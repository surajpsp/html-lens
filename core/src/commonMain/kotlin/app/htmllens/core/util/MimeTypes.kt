package app.htmllens.core.util

object MimeTypes {
    private val byExt = mapOf(
        "html" to "text/html", "htm" to "text/html", "xhtml" to "application/xhtml+xml", "shtml" to "text/html",
        "css" to "text/css", "js" to "text/javascript", "mjs" to "text/javascript", "cjs" to "text/javascript",
        "json" to "application/json", "map" to "application/json", "webmanifest" to "application/manifest+json",
        "xml" to "application/xml", "txt" to "text/plain", "md" to "text/markdown", "csv" to "text/csv",
        "svg" to "image/svg+xml", "png" to "image/png", "jpg" to "image/jpeg", "jpeg" to "image/jpeg",
        "gif" to "image/gif", "webp" to "image/webp", "avif" to "image/avif", "ico" to "image/x-icon",
        "bmp" to "image/bmp", "tif" to "image/tiff", "tiff" to "image/tiff", "heic" to "image/heic",
        "woff" to "font/woff", "woff2" to "font/woff2", "ttf" to "font/ttf", "otf" to "font/otf", "eot" to "application/vnd.ms-fontobject",
        "mp4" to "video/mp4", "webm" to "video/webm", "ogv" to "video/ogg", "mov" to "video/quicktime", "m4v" to "video/mp4",
        "mp3" to "audio/mpeg", "ogg" to "audio/ogg", "oga" to "audio/ogg", "wav" to "audio/wav", "m4a" to "audio/mp4",
        "opus" to "audio/ogg", "flac" to "audio/flac", "aac" to "audio/aac",
        "pdf" to "application/pdf", "wasm" to "application/wasm", "zip" to "application/zip",
        "mht" to "multipart/related", "mhtml" to "multipart/related", "eml" to "message/rfc822",
        "webarchive" to "application/x-webarchive", "vtt" to "text/vtt", "srt" to "text/plain",
    )

    private val extByMime = mapOf(
        "text/html" to "html", "text/css" to "css", "text/javascript" to "js", "application/javascript" to "js",
        "application/x-javascript" to "js", "application/json" to "json", "image/svg+xml" to "svg", "image/png" to "png",
        "image/jpeg" to "jpg", "image/jpg" to "jpg", "image/gif" to "gif", "image/webp" to "webp", "image/avif" to "avif",
        "image/x-icon" to "ico", "image/vnd.microsoft.icon" to "ico", "font/woff" to "woff", "font/woff2" to "woff2",
        "application/font-woff" to "woff", "application/font-woff2" to "woff2", "font/ttf" to "ttf", "font/otf" to "otf",
        "video/mp4" to "mp4", "video/webm" to "webm", "audio/mpeg" to "mp3", "audio/ogg" to "ogg", "text/plain" to "txt",
        "application/pdf" to "pdf", "application/xml" to "xml", "text/xml" to "xml",
    )

    fun forPath(path: String): String = byExt[Paths.extension(path)] ?: "application/octet-stream"

    fun extensionFor(mime: String): String? = extByMime[mime.substringBefore(';').trim().lowercase()]

    fun isHtml(mime: String) = mime.startsWith("text/html") || mime.startsWith("application/xhtml")
    fun isCss(mime: String) = mime.startsWith("text/css")
    fun isJs(mime: String) = mime.contains("javascript")
    fun isImage(mime: String) = mime.startsWith("image/")
    fun isText(mime: String) = mime.startsWith("text/") || mime.contains("json") || mime.contains("xml") ||
        mime.contains("javascript") || mime == "image/svg+xml"

    fun isHtmlPath(path: String) = Paths.extension(path) in setOf("html", "htm", "xhtml", "shtml")
}
