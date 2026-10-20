package app.htmllens.desktop

import java.io.File
import java.net.InetAddress
import java.net.ServerSocket
import java.net.Socket
import kotlin.concurrent.thread

/**
 * Keeps one running instance: a second launch (e.g. double-clicking another .html file on
 * Windows) forwards its file arguments to the first instance over a loopback socket and exits.
 */
object SingleInstance {
    private val portFile = File(AppDirs.data, "instance.port")

    /** Returns true when this process should continue as the primary instance. */
    fun acquire(args: List<String>, onOpen: (List<String>) -> Unit): Boolean {
        runCatching {
            val port = portFile.readText().trim().toInt()
            Socket(InetAddress.getLoopbackAddress(), port).use { s ->
                s.soTimeout = 1500
                val w = s.getOutputStream().bufferedWriter()
                w.write("HTMLLENS\n")
                args.forEach { w.write(File(it).absolutePath + "\n") }
                w.write("\n")
                w.flush()
                if (s.getInputStream().bufferedReader().readLine() == "OK") return false
            }
        }
        runCatching {
            val server = ServerSocket(0, 10, InetAddress.getLoopbackAddress())
            portFile.writeText(server.localPort.toString())
            thread(isDaemon = true, name = "htmllens-instance") {
                while (true) {
                    val socket = runCatching { server.accept() }.getOrNull() ?: break
                    runCatching {
                        socket.use { s ->
                            val r = s.getInputStream().bufferedReader()
                            if (r.readLine() != "HTMLLENS") return@use
                            val paths = generateSequence { r.readLine() }.takeWhile { it.isNotEmpty() }.toList()
                            s.getOutputStream().bufferedWriter().apply { write("OK\n"); flush() }
                            onOpen(paths)
                        }
                    }
                }
            }
            Runtime.getRuntime().addShutdownHook(Thread { runCatching { portFile.delete() } })
        }
        return true
    }
}
