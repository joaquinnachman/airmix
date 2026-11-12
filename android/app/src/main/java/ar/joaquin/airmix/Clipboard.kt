package ar.joaquin.airmix

import android.os.Handler
import android.os.Looper

/**
 * COPIAR / PEGAR seteos entre tiras del mismo tipo. Igual en Clipboard.swift (iOS).
 *
 * Las direcciones salen de las mismas tablas que usan las escenas (Scenes.nodeList + Scenes.fieldsFor),
 * así no hay una segunda lista que mantener. Nunca se copian solos: nombre, entrada física, GAIN/trim ni 48V.
 */
object Clipboard {
    enum class What(val label: String) {
        CONFIG("CONFIG"), GATE("GATE"), DYN("COMPRESOR"), EQ("EQ"), SENDS("ENVÍOS"),
        ALL("TODO"), BUS_SENDS("ENVÍOS AL BUS")
    }

    /** Lo copiado: rel = dirección relativa a la tira (o "entrada|sub" para ENVÍOS AL BUS). */
    class Clip(val what: What, val kind: Kind, val from: Strip, val values: List<Pair<String, Any>>) {
        val label: String get() = "${what.label} de ${from.title}"
    }

    var clip: Clip? = null
        private set
    var onChange: (() -> Unit)? = null

    /** Campos que nunca se copian (identidad, ruteo de entrada, ganancia). */
    private val NEVER = setOf("name", "insrc", "rtnsrc", "source", "rtntrim", "rtnsw", "trim", "icon")

    private val main = Handler(Looper.getMainLooper())

    private fun inSection(rel: String, w: What): Boolean = when (w) {
        What.EQ -> rel == "eq" || rel.startsWith("eq/")
        What.GATE -> rel == "gate" || rel == "gate/filter"
        What.DYN -> rel == "dyn" || rel == "dyn/filter"
        What.SENDS -> rel.startsWith("mix/")
        What.CONFIG -> rel == "config" || rel == "preamp" || rel == "grp" || rel == "delay"
        What.ALL -> rel != "insert" && rel != "automix"
        What.BUS_SENDS -> false
    }

    /** Direcciones completas (con "/" inicial) de una sección de una tira. */
    fun leaves(s: Strip, w: What): List<String> {
        val base = s.prefix.trimStart('/')
        val out = ArrayList<String>()
        for (node in Scenes.nodeList()) {
            if (!node.startsWith("$base/") && node != base) continue
            val rel = node.removePrefix(base).trimStart('/')
            if (!inSection(rel, w)) continue
            val fields = Scenes.fieldsFor(Scenes.patternKey(node)) ?: continue
            for (f in fields) if (f.sub !in NEVER) out.add("/$node/${f.sub}")
        }
        return out
    }

    /** Número de bus en las direcciones de envío (mix/NN). */
    private fun busMix(s: Strip): String? = if (s.kind == Kind.BUS) Fmt.two(s.idx) else null

    /** ENVÍOS AL BUS: lo que cada entrada manda a este bus. */
    private fun busSendLeaves(bus: Strip): List<Pair<String, String>> {
        val nn = busMix(bus) ?: return emptyList()
        val out = ArrayList<Pair<String, String>>()
        for (src in stripsOfKind(Kind.CH) + stripsOfKind(Kind.AUX) + stripsOfKind(Kind.RTN)) {
            val node = src.prefix.trimStart('/') + "/mix/$nn"
            val fields = Scenes.fieldsFor(Scenes.patternKey(node)) ?: continue
            for (f in fields) out.add(src.prefix to f.sub)
        }
        return out
    }

    fun options(s: Strip, current: Section?): List<What> {
        val l = ArrayList<What>()
        when (current) {
            Section.CONFIG -> l.add(What.CONFIG)
            Section.GATE -> l.add(What.GATE)
            Section.DYN -> l.add(What.DYN)
            Section.EQ -> l.add(What.EQ)
            Section.SENDS -> l.add(What.SENDS)
            null -> {}
        }
        l.add(What.ALL)
        if (s.kind == Kind.BUS) l.add(What.BUS_SENDS)
        return l.filter { it == What.BUS_SENDS || leaves(s, it).isNotEmpty() }
    }

    /**
     * Copia: pide a la consola lo que falte y, un momento después, guarda los valores.
     * done(n) recibe cuántos valores se copiaron.
     */
    fun copy(s: Strip, w: What, done: (Int) -> Unit) {
        val st = App.store
        val addrs: List<String> =
            if (w == What.BUS_SENDS) busSendLeaves(s).map { (src, sub) -> "$src/mix/${busMix(s)}/$sub" } else leaves(s, w)
        for (a in addrs) if (st.get(a) == null) App.client?.query(a)
        main.postDelayed({
            val vals = ArrayList<Pair<String, Any>>()
            if (w == What.BUS_SENDS) {
                for ((src, sub) in busSendLeaves(s)) st.get("$src/mix/${busMix(s)}/$sub")?.let { vals.add("$src|$sub" to it) }
            } else {
                for (a in addrs) st.get(a)?.let { vals.add(a.removePrefix(s.prefix + "/") to it) }
            }
            clip = if (vals.isEmpty()) null else Clip(w, s.kind, s, vals)
            onChange?.invoke()
            done(vals.size)
        }, 700)
    }

    fun canPaste(target: Strip): Boolean {
        val c = clip ?: return false
        return c.kind == target.kind && c.from.copy(pair = false) != target.copy(pair = false)
    }

    fun targets(): List<Strip> {
        val c = clip ?: return emptyList()
        return stripsOfKind(c.kind).filter { it.idx != c.from.idx }
    }

    /** Pega en una tira. Devuelve cuántos valores escribió. */
    fun paste(target: Strip): Int {
        val c = clip ?: return 0
        if (c.kind != target.kind) return 0
        var n = 0
        if (c.what == What.BUS_SENDS) {
            val nn = busMix(target) ?: return 0
            for ((rel, v) in c.values) {
                val (src, sub) = rel.split('|', limit = 2).let { it[0] to it.getOrElse(1) { "" } }
                App.store.set("$src/mix/$nn/$sub", v)
                n++
            }
        } else {
            for ((rel, v) in c.values) {
                App.store.set("${target.prefix}/$rel", v)
                n++
            }
        }
        return n
    }

    fun clear() {
        clip = null
        onChange?.invoke()
    }
}
