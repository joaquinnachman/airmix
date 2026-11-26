package ar.joaquin.airmix

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/**
 * PRESETS guardados en el dispositivo (sin internet). Igual en Presets.swift.
 *  - De canal: la configuración de una tira (EQ, compresor, gate, filtro…) sin nombre, entrada, GAIN, 48V ni envíos.
 *    Se cargan en cualquier tira del mismo tipo.
 *  - De efecto: tipo de efecto + todos sus parámetros. Se cargan en cualquier FX.
 * Los valores se guardan con su tipo ("i:3", "f:0.5", "s:texto") para escribirlos igual que vinieron.
 */
object Presets {
    const val MAX = 60
    private const val KEY_STRIP = "presets_strip"
    private const val KEY_FX = "presets_fx"

    class Preset(val name: String, val kind: String, val type: Int, val values: List<Pair<String, Any>>)

    private fun prefs(ctx: Context) = ctx.getSharedPreferences("airmix", Context.MODE_PRIVATE)

    private fun enc(v: Any): String = when (v) {
        is Int -> "i:$v"
        is Float -> "f:$v"
        is Double -> "f:$v"
        else -> "s:$v"
    }

    private fun dec(s: String): Any? = when {
        s.startsWith("i:") -> s.substring(2).toIntOrNull()
        s.startsWith("f:") -> s.substring(2).toFloatOrNull()
        s.startsWith("s:") -> s.substring(2)
        else -> null
    }

    private fun load(ctx: Context, key: String): MutableList<Preset> {
        val out = ArrayList<Preset>()
        try {
            val arr = JSONArray(prefs(ctx).getString(key, "[]"))
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                val vals = ArrayList<Pair<String, Any>>()
                val vo = o.getJSONObject("v")
                for (k in vo.keys()) dec(vo.getString(k))?.let { vals.add(k to it) }
                out.add(Preset(o.getString("n"), o.optString("k"), o.optInt("t", -1), vals))
            }
        } catch (_: Exception) {}
        return out
    }

    private fun save(ctx: Context, key: String, list: List<Preset>) {
        val arr = JSONArray()
        for (p in list) {
            val vo = JSONObject()
            for ((k, v) in p.values) vo.put(k, enc(v))
            arr.put(JSONObject().put("n", p.name).put("k", p.kind).put("t", p.type).put("v", vo))
        }
        prefs(ctx).edit().putString(key, arr.toString()).apply()
    }

    // ------------------------------------------------------------------ canal

    /** Tipo de tira para presets (un canal se carga en canales, un bus en buses…). */
    fun kindKey(s: Strip): String = s.kind.name

    fun stripPresets(ctx: Context, s: Strip): List<Preset> = load(ctx, KEY_STRIP).filter { it.kind == kindKey(s) }

    /** Lo que guarda un preset de canal: todo menos envíos (y lo que el portapapeles nunca copia). */
    private fun stripLeaves(s: Strip): List<String> = Clipboard.leaves(s, Clipboard.What.ALL).filter { !it.removePrefix(s.prefix + "/").startsWith("mix/") }

    /** Pide a la consola lo que falte y, un momento después, guarda. done(n) = cuántos valores. */
    fun saveStrip(ctx: Context, s: Strip, name: String, done: (Int) -> Unit) {
        val st = App.store
        val addrs = stripLeaves(s)
        for (a in addrs) if (st.get(a) == null) App.client?.query(a)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val vals = ArrayList<Pair<String, Any>>()
            for (a in addrs) st.get(a)?.let { vals.add(a.removePrefix(s.prefix + "/") to it) }
            if (vals.isNotEmpty()) {
                val list = load(ctx, KEY_STRIP)
                list.removeAll { it.kind == kindKey(s) && it.name.equals(name, true) }
                list.add(0, Preset(name, kindKey(s), -1, vals))
                save(ctx, KEY_STRIP, list.take(MAX))
            }
            done(vals.size)
        }, 700)
    }

    fun applyStrip(p: Preset, s: Strip): Int {
        var n = 0
        for ((rel, v) in p.values) {
            App.store.set("${s.prefix}/$rel", v)
            n++
        }
        return n
    }

    fun deleteStrip(ctx: Context, p: Preset) {
        val list = load(ctx, KEY_STRIP)
        list.removeAll { it.kind == p.kind && it.name == p.name }
        save(ctx, KEY_STRIP, list)
    }

    // ------------------------------------------------------------------ efectos

    fun fxPresets(ctx: Context): List<Preset> = load(ctx, KEY_FX)

    fun saveFx(ctx: Context, n: Int, name: String, done: (Int) -> Unit) {
        val st = App.store
        val type = st.int("/fx/$n/type", -1)
        val count = Fx.params(Fx.abbr(n, type)).size
        val addrs = (1..count).map { "/fx/$n/par/${Fmt.two(it)}" }
        for (a in addrs) if (st.get(a) == null) App.client?.query(a)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            val vals = ArrayList<Pair<String, Any>>()
            for (i in addrs.indices) st.get(addrs[i])?.let { vals.add(Fmt.two(i + 1) to it) }
            if (type >= 0 && vals.isNotEmpty()) {
                val list = load(ctx, KEY_FX)
                list.removeAll { it.name.equals(name, true) }
                list.add(0, Preset(name, Fx.typeName(n, type), type, vals))
                save(ctx, KEY_FX, list.take(MAX))
            }
            done(if (type < 0) 0 else vals.size)
        }, 700)
    }

    /** Carga: primero el tipo de efecto y, cuando la consola lo cambió, los parámetros. */
    fun applyFx(p: Preset, n: Int) {
        App.store.set("/fx/$n/type", p.type)
        android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
            for ((k, v) in p.values) App.store.set("/fx/$n/par/$k", v)
        }, 500)
    }

    fun deleteFx(ctx: Context, p: Preset) {
        val list = load(ctx, KEY_FX)
        list.removeAll { it.name == p.name }
        save(ctx, KEY_FX, list)
    }
}
