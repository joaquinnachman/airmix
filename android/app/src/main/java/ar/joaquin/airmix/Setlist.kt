package ar.joaquin.airmix

import android.content.SharedPreferences
import org.json.JSONArray
import org.json.JSONObject

/** Un tema de la lista: título + nota libre (tono, quién arranca, cambios de monitor…). */
class Song(var title: String, var note: String = "")

/**
 * Lista de temas de la banda que se está operando (pestaña TEMAS del panel de notas).
 * Se guarda en la tablet y se puede cargar desde .txt / .docx / .doc / .rtf (un tema por línea).
 */
object Setlist {
    val songs = ArrayList<Song>()
    /** Tema que está sonando (-1 = ninguno). */
    var current = -1
    /** Nombre del archivo del que salió la lista. */
    var source = ""

    fun load(p: SharedPreferences) {
        songs.clear()
        try {
            val o = JSONObject(p.getString("setlist", null) ?: return)
            source = o.optString("source", "")
            current = o.optInt("current", -1)
            val a = o.optJSONArray("songs") ?: JSONArray()
            for (i in 0 until a.length()) {
                val s = a.getJSONObject(i)
                songs.add(Song(s.optString("t", ""), s.optString("n", "")))
            }
        } catch (_: Exception) {}
        if (current >= songs.size) current = -1
    }

    fun save(p: SharedPreferences) {
        val a = JSONArray()
        for (s in songs) a.put(JSONObject().put("t", s.title).put("n", s.note))
        val o = JSONObject().put("source", source).put("current", current).put("songs", a)
        p.edit().putString("setlist", o.toString()).apply()
    }

    fun move(i: Int, dir: Int) {
        val j = i + dir
        if (i !in songs.indices || j !in songs.indices) return
        val t = songs[i]
        songs[i] = songs[j]
        songs[j] = t
        if (current == i) current = j else if (current == j) current = i
    }

    fun remove(i: Int) {
        if (i !in songs.indices) return
        songs.removeAt(i)
        if (current == i) current = -1 else if (current > i) current--
    }

    /**
     * Texto -> temas. Un tema por línea; se ignoran líneas vacías y la numeración ("1.", "02 -", "3)", viñetas).
     * Si la línea tiene " // " o una tabulación, lo que sigue es la nota.
     */
    fun parse(text: String): List<Song> {
        val out = ArrayList<Song>()
        val num = Regex("^\\s*(?:\\d{1,3}\\s*[.)\\-–:]\\s*|[•·*\\-–]\\s+)")
        for (raw in text.replace("\r\n", "\n").replace('\r', '\n').split('\n')) {
            var line = raw.trim()
            if (line.isEmpty()) continue
            line = line.replace(num, "").trim()
            if (line.isEmpty()) continue
            val sep = listOf(" // ", "\t").map { line.indexOf(it) }.filter { it > 0 }.minOrNull()
            if (sep != null) {
                val title = line.substring(0, sep).trim()
                val note = line.substring(sep).trimStart('/', '\t', ' ').trim()
                out.add(Song(title, note))
            } else {
                out.add(Song(line))
            }
        }
        return out
    }
}
