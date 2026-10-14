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
