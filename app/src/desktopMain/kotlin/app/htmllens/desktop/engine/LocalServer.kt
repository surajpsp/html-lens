package app.htmllens.desktop.engine

import app.htmllens.core.serve.Serve
import app.htmllens.core.serve.ServeConfig
import app.htmllens.core.util.MimeTypes
import app.htmllens.core.util.Paths
import app.htmllens.viewer.ViewSession
import com.sun.net.httpserver.HttpExchange
import com.sun.net.httpserver.HttpServer
import java.io.File
import java.net.InetAddress
import java.net.InetSocketAddress
import java.net.URI
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.Executors

/**
 * A private HTTP server bound to 127.0.0.1 that serves open bundles to the embedded WebView.
 * Every view gets an unguessable channel token; nothing is reachable without it. A stable port
 * keeps the origin (and therefore saved app storage) the same across launches.
 */
object LocalServer {
    class Channel(val session: ViewSession, val onMessage: (String) -> Unit) {
        @Volatile var config: ServeConfig = session.serveConfig("http")
    }

    private var server: HttpServer? = null
    var port: Int = 0
        private set
    val origin: String get() = "http://127.0.0.1:$port"

    private val channels = ConcurrentHashMap<String, Channel>()
    private val editors = ConcurrentHashMap<String, (String) -> Unit>()
    private val contents = ConcurrentHashMap<String, ByteArray>()

    @Synchronized
    fun start(dataDir: File) {
        if (server != null) return
        val portFile = File(dataDir, "server.port")
        val preferred = portFile.takeIf { it.isFile }?.readText()?.trim()?.toIntOrNull()
        val candidates = listOfNotNull(preferred) + (0 until 20).map { 47_200 + (Math.random() * 700).toInt() } + listOf(0)
        for (p in candidates) {
            val s = runCatching { HttpServer.create(InetSocketAddress(InetAddress.getLoopbackAddress(), p), 64) }.getOrNull() ?: continue
            s.createContext("/") { ex -> runCatching { handle(ex) }.onFailure { runCatching { respond(ex, 500, "text/plain", emptyMap(), (it.message ?: "error").toByteArray()) } } }
            s.executor = Executors.newFixedThreadPool(8) { r -> Thread(r, "htmllens-server").apply { isDaemon = true } }
            s.start()
            server = s
            port = s.address.port
            runCatching { portFile.writeText(port.toString()) }
            return
        }
        error("Could not start the local preview server")
    }

    fun register(token: String, channel: Channel) { channels[token] = channel }
    fun unregister(token: String) { channels.remove(token) }
    fun registerEditor(token: String, onMessage: (String) -> Unit) { editors[token] = onMessage }
    fun unregisterEditor(token: String) { editors.remove(token) }
    fun putContent(token: String, bytes: ByteArray) { contents[token] = bytes }

    private fun handle(ex: HttpExchange) {
        val rawPath = ex.requestURI.rawPath ?: "/"
        when {
            rawPath.startsWith("/s/") -> {
                val rest = rawPath.removePrefix("/s/")
                val token = rest.substringBefore('/')
                val channel = channels[token] ?: return notFound(ex, rawPath)
                serveBundle(ex, channel, rest.substringAfter('/', ""))
            }
            rawPath.startsWith("/__hv/msg/") && ex.requestMethod == "POST" -> {
                val body = ex.requestBody.readBytes().decodeToString()
                channels[rawPath.removePrefix("/__hv/msg/")]?.onMessage?.invoke(body)
                respond(ex, 204, "text/plain", emptyMap(), ByteArray(0))
            }
            rawPath.startsWith("/__hv/ed/") && ex.requestMethod == "POST" -> {
                val body = ex.requestBody.readBytes().decodeToString()
                editors[rawPath.removePrefix("/__hv/ed/")]?.invoke(body)
                respond(ex, 204, "text/plain", emptyMap(), ByteArray(0))
            }
            rawPath.startsWith("/__hv/content/") -> {
                val bytes = contents.remove(rawPath.removePrefix("/__hv/content/")) ?: ByteArray(0)
                respond(ex, 200, "text/plain; charset=utf-8", mapOf("Cache-Control" to "no-store"), bytes)
            }
            rawPath.startsWith("/__hv/") || rawPath.startsWith("/editor/") -> {
                val res = rawPath.removePrefix("/").let { if (it.endsWith("/")) it + "index.html" else it }
                val bytes = javaClass.getResourceAsStream("/web/$res")?.use { it.readBytes() } ?: return notFound(ex, rawPath)
                val mime = MimeTypes.forPath(res) + if (MimeTypes.isText(MimeTypes.forPath(res))) "; charset=utf-8" else ""
                respond(ex, 200, mime, mapOf("Cache-Control" to "no-cache"), bytes)
            }
            else -> {
                // Root-absolute reference ("/img/logo.png") from a bundle page: resolve against the referring session.
                val referer = ex.requestHeaders.getFirst("Referer") ?: return notFound(ex, rawPath)
                val refPath = runCatching { URI(referer).rawPath }.getOrNull() ?: return notFound(ex, rawPath)
                if (!refPath.startsWith("/s/")) return notFound(ex, rawPath)
                val token = refPath.removePrefix("/s/").substringBefore('/')
                val channel = channels[token] ?: return notFound(ex, rawPath)
                serveBundle(ex, channel, rawPath.removePrefix("/"))
            }
        }
    }

    private fun serveBundle(ex: HttpExchange, channel: Channel, path: String) {
        val file = channel.session.bundle.resolve(path)
            ?: return respond(ex, 404, "text/html; charset=utf-8", emptyMap(), Serve.notFoundPage(Paths.percentDecode(path)))
        val cfg = channel.config
        respond(ex, 200, file.mime, Serve.headers(file, cfg), Serve.body(file, cfg))
    }

    private fun notFound(ex: HttpExchange, path: String) =
        respond(ex, 404, "text/html; charset=utf-8", emptyMap(), Serve.notFoundPage(path))

    private fun respond(ex: HttpExchange, status: Int, mime: String, headers: Map<String, String>, body: ByteArray) {
        val h = ex.responseHeaders
        headers.forEach { (k, v) -> if (!k.equals("Content-Type", true)) h.set(k, v) }
        h.set("Content-Type", mime)
        h.set("X-Content-Type-Options", "nosniff".takeIf { status == 404 } ?: "")
        h.remove("X-Content-Type-Options")
        val head = ex.requestMethod == "HEAD"
        ex.sendResponseHeaders(status, if (head || body.isEmpty()) -1 else body.size.toLong())
        if (!head && body.isNotEmpty()) ex.responseBody.use { it.write(body) } else ex.close()
    }
}
