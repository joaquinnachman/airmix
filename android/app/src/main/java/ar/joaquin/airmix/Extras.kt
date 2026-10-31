package ar.joaquin.airmix

import android.content.SharedPreferences
import java.util.Locale

/** Cronómetro del show. Usa el reloj real, así sigue contando aunque se cierre la app. */
object ShowTimer {
    private var running = false
    private var startedAt = 0L
    private var accumulated = 0L

    fun isRunning() = running

    fun elapsed(): Long = if (running) accumulated + (System.currentTimeMillis() - startedAt) else accumulated

    fun toggle(p: SharedPreferences) {
        if (running) {
            accumulated += System.currentTimeMillis() - startedAt
            running = false
        } else {
            startedAt = System.currentTimeMillis()
            running = true
        }
        save(p)
    }

    fun reset(p: SharedPreferences) {
        running = false
        accumulated = 0L
        startedAt = 0L
        save(p)
    }

    fun load(p: SharedPreferences) {
        running = p.getBoolean("t_run", false)
        startedAt = p.getLong("t_start", 0L)
        accumulated = p.getLong("t_acc", 0L)
    }

    private fun save(p: SharedPreferences) {
        p.edit().putBoolean("t_run", running).putLong("t_start", startedAt).putLong("t_acc", accumulated).apply()
    }

    fun format(ms: Long): String {
        val s = ms / 1000
        val h = s / 3600
        val m = (s % 3600) / 60
        val sec = s % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, sec)
        else String.format(Locale.US, "%02d:%02d", m, sec)
    }
}

/**
 * TAP tempo: se toca al ritmo del tema (negras) y el tiempo pasa a TODOS los efectos cargados
 * que tengan parámetro TIME en ms (Stereo Delay, 3/4-Tap, Delay/Reverb, Delay/Chorus, Delay/Flanger, Mod Delay).
 */
object TapTempo {
    private val taps = LongArray(5)
    private var count = 0
    /** Último tempo aplicado (0 = ninguno). */
    var bpm = 0

    /** Slots de efectos que pueden tener delay (en X32 los FX 5-8 no tienen delays). */
    fun slots(): Int = if (App.console.isX32) 4 else App.console.fxUnits

    /** Registra un toque. Devuelve cuántos efectos se actualizaron (-1 = todavía falta otro toque). */
    fun tap(now: Long): Int {
        if (count > 0 && now - taps[(count - 1) % taps.size] > 2000L) count = 0
        taps[count % taps.size] = now
        count++
        if (count < 2) return -1
        val n = minOf(count, taps.size)
        val first = taps[(count - n) % taps.size]
        val last = taps[(count - 1) % taps.size]
        val ms = (last - first).toFloat() / (n - 1)
        if (ms < 100f) return -1
        bpm = Math.round(60000f / ms)
        return apply(ms)
    }

    fun apply(ms: Float): Int {
        var applied = 0
        for (n in 1..slots()) {
            val raw = App.store.int("/fx/$n/type", -1)
            if (raw < 0) continue
            val abbr = Fx.abbr(n, raw) ?: continue
            val pars = Fx.params(abbr)
            val i = pars.indexOfFirst { it.label == "TIME" && it.unit == "ms" && it.kind == 'A' }
            if (i < 0) continue
            val p = pars[i]
            val v = ((ms.coerceIn(p.a, p.b) - p.a) / (p.b - p.a)).coerceIn(0f, 1f)
            App.store.set("/fx/$n/par/${Fmt.two(i + 1)}", v)
            applied++
        }
        return applied
    }
}
