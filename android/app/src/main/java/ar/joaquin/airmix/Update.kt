package ar.joaquin.airmix

import android.app.Activity
import android.app.AlertDialog
import android.content.ContentProvider
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.database.MatrixCursor
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.provider.OpenableColumns
import android.widget.Toast
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

/**
 * Aviso de actualización. El repo es público: se consulta el último Release de GitHub sin login.
 * versionCode = número de build del CI (tag build-NN), así la comparación es directa.
 * Solo se baja el APK de los Releases de joaquinnachman/airmix; el instalador del sistema
 * además rechaza cualquier APK firmado con otra clave.
 */
object Updater {
    private const val API = "https://api.github.com/repos/joaquinnachman/airmix/releases/latest"
    private const val ALLOWED = "https://github.com/joaquinnachman/airmix/releases/download/"
    private const val EVERY_MS = 6L * 60 * 60 * 1000

    class Release(val build: Int, val title: String, val notes: String, val apkUrl: String?, val page: String)

    private val main = Handler(Looper.getMainLooper())
    private var lastCheck = 0L
    /** Build que ya se ofreció en esta sesión ("Más tarde" = no volver a molestar hasta reabrir la app). */
    private var offered = -1
    @Volatile private var busy = false

    fun installed(ctx: Context): Int = try {
        val pi = ctx.packageManager.getPackageInfo(ctx.packageName, 0)
        if (Build.VERSION.SDK_INT >= 28) pi.longVersionCode.toInt() else @Suppress("DEPRECATION") pi.versionCode
    } catch (_: Exception) { 0 }

    /** En las pruebas automáticas (Robolectric) no se sale a internet. */
    private fun testing(): Boolean = Build.FINGERPRINT == "robolectric"

    /**
     * Consulta en segundo plano. canShow() se evalúa al llegar la respuesta: nunca se muestra
     * conectado a la consola ni con el candado puesto (no molestar en un show).
     */
    fun check(act: Activity, manual: Boolean = false, canShow: () -> Boolean) {
        if (testing() || busy) return
        val now = System.currentTimeMillis()
        if (!manual && now - lastCheck < EVERY_MS) return
        lastCheck = now
        busy = true
        Thread {
            val r = try { fetch() } catch (_: Exception) { null }
            busy = false
            main.post {
                if (act.isFinishing) return@post
                val mine = installed(act)
                when {
                    r == null -> if (manual) Toast.makeText(act, "No se pudo buscar: ¿hay internet?", Toast.LENGTH_LONG).show()
                    r.build <= mine -> if (manual) Toast.makeText(act, "Estás al día (build $mine)", Toast.LENGTH_SHORT).show()
                    !manual && r.build == offered -> {}
                    // En show (conectado / candado): se vuelve a probar la próxima vez que se abra la app.
                    !manual && !canShow() -> lastCheck = 0
                    else -> {
                        offered = r.build
                        offer(act, r)
                    }
                }
            }
        }.start()
    }

    private fun fetch(): Release? {
        val c = URL(API).openConnection() as HttpURLConnection
        c.connectTimeout = 5000
        c.readTimeout = 8000
        c.setRequestProperty("Accept", "application/vnd.github+json")
        c.setRequestProperty("User-Agent", "AirMix")
        try {
            if (c.responseCode != 200) return null
            val j = org.json.JSONObject(c.inputStream.bufferedReader().readText())
            val build = j.optString("tag_name").removePrefix("build-").toIntOrNull() ?: return null
            var apk: String? = null
            val assets = j.optJSONArray("assets")
            if (assets != null) for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                if (a.optString("name") == "AirMix.apk") apk = a.optString("browser_download_url")
            }
            if (apk != null && !apk.startsWith(ALLOWED)) apk = null
            return Release(build, j.optString("name"), j.optString("body"), apk, j.optString("html_url"))
        } finally {
            c.disconnect()
        }
    }

    private fun offer(act: Activity, r: Release) {
        val ver = Regex("""AirMix\s+([0-9.]+)""").find(r.title)?.groupValues?.get(1) ?: "build ${r.build}"
        val notes = r.notes.lines().firstOrNull { it.isNotBlank() }?.take(300).orEmpty()
        AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Hay una versión nueva de AirMix ($ver)")
            .setMessage((if (notes.isNotEmpty()) "$notes\n\n" else "") + "Se baja de GitHub y se instala encima: no se pierde nada.")
            .setPositiveButton("ACTUALIZAR") { _, _ -> if (r.apkUrl != null) download(act, r) else openPage(act, r.page) }
            .setNegativeButton("Más tarde", null)
            .show()
    }

    private fun openPage(act: Activity, url: String) {
        try { act.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) } catch (_: Exception) {}
    }

    fun apkFile(ctx: Context): File = File(File(ctx.cacheDir, "updates").also { it.mkdirs() }, "AirMix.apk")

    private fun download(act: Activity, r: Release) {
        val dlg = AlertDialog.Builder(act, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Bajando AirMix…")
            .setMessage("0 %")
            .setCancelable(false)
            .show()
        Thread {
            val f = apkFile(act)
            var ok = false
            try {
                var url = URL(r.apkUrl)
                var c = url.openConnection() as HttpURLConnection
                c.setRequestProperty("User-Agent", "AirMix")
                c.connectTimeout = 8000
                c.readTimeout = 15000
                c.instanceFollowRedirects = true
                // GitHub redirige a su CDN; HttpURLConnection sigue redirecciones https -> https.
                val total = c.contentLengthLong
                c.inputStream.use { inp ->
                    f.outputStream().use { out ->
                        val buf = ByteArray(64 * 1024)
                        var done = 0L
                        var lastPct = -1
                        while (true) {
                            val n = inp.read(buf)
                            if (n < 0) break
                            out.write(buf, 0, n)
                            done += n
                            if (total > 0) {
                                val pct = (done * 100 / total).toInt()
                                if (pct != lastPct) {
                                    lastPct = pct
                                    main.post { dlg.setMessage("$pct %") }
                                }
                            }
                        }
                    }
                }
                c.disconnect()
                ok = f.length() > 100_000
            } catch (_: Exception) {}
            main.post {
                dlg.dismiss()
                if (ok) install(act) else Toast.makeText(act, "No se pudo bajar la actualización. Probá de nuevo con internet.", Toast.LENGTH_LONG).show()
            }
        }.start()
    }

    private fun install(act: Activity) {
        if (Build.VERSION.SDK_INT >= 26 && !act.packageManager.canRequestPackageInstalls()) {
            // Primera vez: Android pide permitir "instalar apps de esta fuente".
            Toast.makeText(act, "Habilitá \"Permitir de esta fuente\" para AirMix y tocá ACTUALIZAR de nuevo.", Toast.LENGTH_LONG).show()
            offered = -1
            try {
                act.startActivity(Intent(android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + act.packageName)))
            } catch (_: Exception) {}
            return
        }
        val uri = Uri.parse("content://${act.packageName}.updates/AirMix.apk")
        val i = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK)
        try { act.startActivity(i) } catch (_: Exception) {
            Toast.makeText(act, "No se pudo abrir el instalador.", Toast.LENGTH_LONG).show()
        }
    }
}

/** Entrega el APK bajado (solo ese archivo, solo lectura) al instalador del sistema. */
class UpdateProvider : ContentProvider() {
    override fun onCreate(): Boolean = true

    private fun file(): File? = context?.let { Updater.apkFile(it) }

    override fun openFile(uri: Uri, mode: String): ParcelFileDescriptor? {
        val f = file() ?: return null
        if (uri.lastPathSegment != "AirMix.apk" || !f.exists()) return null
        return ParcelFileDescriptor.open(f, ParcelFileDescriptor.MODE_READ_ONLY)
    }

    override fun getType(uri: Uri): String = "application/vnd.android.package-archive"

    override fun query(uri: Uri, projection: Array<out String>?, selection: String?, selectionArgs: Array<out String>?, sortOrder: String?): Cursor {
        val c = MatrixCursor(arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE))
        c.addRow(arrayOf<Any>("AirMix.apk", file()?.length() ?: 0L))
        return c
    }

    override fun insert(uri: Uri, values: ContentValues?): Uri? = null
    override fun delete(uri: Uri, selection: String?, selectionArgs: Array<out String>?): Int = 0
    override fun update(uri: Uri, values: ContentValues?, selection: String?, selectionArgs: Array<out String>?): Int = 0
}
