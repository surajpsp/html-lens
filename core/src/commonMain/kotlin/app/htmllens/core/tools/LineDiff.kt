package app.htmllens.core.tools

/** Line-based Myers diff used for comparing versions of an HTML app. */
object LineDiff {
    enum class Op { EQUAL, INSERT, DELETE }
    data class Line(val op: Op, val text: String, val oldNo: Int?, val newNo: Int?)
    data class Stats(val added: Int, val removed: Int)

    fun stats(lines: List<Line>) = Stats(lines.count { it.op == Op.INSERT }, lines.count { it.op == Op.DELETE })

    fun diff(a: String, b: String, maxEdits: Int = 4000): List<Line> {
        val x = a.split('\n')
        val y = b.split('\n')
        val n = x.size
        val m = y.size
        val max = n + m
        val offset = max + 1
        val v = IntArray(2 * max + 3)
        val trace = ArrayList<IntArray>()
        var found = false
        loop@ for (d in 0..minOf(max, maxEdits)) {
            trace += v.copyOf()
            var k = -d
            while (k <= d) {
                var xi = if (k == -d || (k != d && v[offset + k - 1] < v[offset + k + 1])) v[offset + k + 1] else v[offset + k - 1] + 1
                var yi = xi - k
                while (xi < n && yi < m && x[xi] == y[yi]) { xi++; yi++ }
                v[offset + k] = xi
                if (xi >= n && yi >= m) { found = true; break@loop }
                k += 2
            }
        }
        if (!found) {
            return x.mapIndexed { i, t -> Line(Op.DELETE, t, i + 1, null) } + y.mapIndexed { i, t -> Line(Op.INSERT, t, null, i + 1) }
        }
        // Backtrack
        val out = ArrayList<Line>()
        var xi = n
        var yi = m
        for (d in trace.size - 1 downTo 0) {
            val vd = trace[d]
            val k = xi - yi
            val prevK = if (k == -d || (k != d && vd[offset + k - 1] < vd[offset + k + 1])) k + 1 else k - 1
            val prevX = vd[offset + prevK]
            val prevY = prevX - prevK
            while (xi > prevX && yi > prevY) {
                out += Line(Op.EQUAL, x[xi - 1], xi, yi); xi--; yi--
            }
            if (d > 0) {
                if (xi == prevX) out += Line(Op.INSERT, y[yi - 1], null, yi).also { yi-- }
                else out += Line(Op.DELETE, x[xi - 1], xi, null).also { xi-- }
            }
        }
        out.reverse()
        return out
    }
}
