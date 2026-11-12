package ar.joaquin.airmix

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.Editable
import android.text.InputType
import android.text.TextWatcher
import android.view.Gravity
import android.view.View
import android.view.WindowManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.PopupWindow
import android.widget.ScrollView
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    enum class Tab(val label: String) { MIX("MEZCLA"), FX("EFECTOS"), METERS("MEDIDORES"), SCENES("ESCENAS") }

    companion object {
        private const val REQ_SAVE = 41
        private const val REQ_OPEN = 42
        private const val REQ_SETLIST = 43
        private const val REQ_RECOVER = 44
    }

    private var tab = Tab.MIX
    private var layers: List<Layer> = emptyList()
    private var layerIdx = 0
    private var sends = SendTarget.MAIN
    private var detail: Strip? = null
    private var section = Section.CONFIG

    private lateinit var content: FrameLayout
    private lateinit var notesPanel: View
    private lateinit var notesEdit: EditText
    private lateinit var timerLabel: TextView
    private var lastTimerText = ""
    private var pendingExport: String? = null
    private lateinit var rail: LinearLayout
    private lateinit var conn: Btn
    private lateinit var soloChip: Btn
    private lateinit var clipChip: Btn
    private var shownLinks: Set<Int> = emptySet()
    private var linkTick = 0
    private val tabBtns = HashMap<Tab, Btn>()
    private val layerBtns = ArrayList<Btn>()

    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private var requeryTick = 0
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            onTick()
            if (ticking) handler.postDelayed(this, 33)
        }
    }

    /** Hubo conexión en esta sesión: recién ahí un corte merece la franja roja. */
    private var everConnected = false
    private lateinit var lostBanner: TextView
    private lateinit var lockBtn: Btn
    private lateinit var bigTimer: FrameLayout
    private lateinit var bigTimerText: TextView
    private var locked = false

    private var dialog: AlertDialog? = null
    private var dialogList: LinearLayout? = null
    private val seen = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // Celular en horizontal: menos de ~520 dp de alto -> interfaz compacta.
        Layout.compact = resources.configuration.screenHeightDp in 1..519
        prefs().getString("model", null)?.let { App.applyModel(it) }
        ShowTimer.load(prefs())
        Setlist.load(prefs())
        if (App.client == null) App.client = MixerClient(applicationContext).also { it.start() }
        App.client?.x32 = App.console.isX32
        setContentView(buildRoot())
        watchLinks()
        rebuildRail()
        render()
        val ip = prefs().getString("ip", null)
        if (ip != null) App.client?.connect(ip, prefs().getInt("port", 0))
        else handler.postDelayed({ showConnect() }, 400)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
        // Aviso de actualización (cada 6 h como mucho), nunca conectado a la consola ni con el candado puesto.
        handler.postDelayed({ Updater.check(this) { App.client?.isConnected() != true && !locked } }, 3000)
        if (!ticking) {
            ticking = true
            handler.post(tick)
        }
    }

    override fun onPause() {
        ticking = false
        handler.removeCallbacks(tick)
        super.onPause()
    }

    override fun onDestroy() {
        if (isFinishing) {
            App.client?.stop()
            App.client = null
        }
        super.onDestroy()
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideBars()
    }

    @Suppress("DEPRECATION")
    override fun onBackPressed() {
        if (locked) return
        if (::bigTimer.isInitialized && bigTimer.visibility == View.VISIBLE) {
            bigTimer.visibility = View.GONE
            return
        }
        if (detail != null) {
            detail = null
            render()
        } else {
            super.onBackPressed()
        }
    }

    @Suppress("DEPRECATION")
    private fun hideBars() {
        window.decorView.systemUiVisibility = (View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_FULLSCREEN
            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
            or View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION
            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN)
    }

    private fun prefs() = getSharedPreferences("airmix", Context.MODE_PRIVATE)

    // ------------------------------------------------------------------ tick (30 fps)

    private fun onTick() {
        val c = App.client ?: return
        var n = 0
        while (n < 500) {
            val m = c.incoming.poll() ?: break
            n++
            if (m.address == "node" || m.address == "/node") {
                Scenes.onNode(m.args.getOrNull(0)?.toString() ?: "")
                continue
            }
            if (m.address == "/xinfo") {
                if (m.args.size >= 4) onXinfo(m.args[1].toString(), m.args[2].toString(), m.args[3].toString())
                continue
            }
            if (m.args.isNotEmpty()) App.store.incoming(m.address, m.args[0])
        }
        while (true) {
            val d = c.discovered.poll() ?: break
            onDiscovered(d)
        }
        val isConn = c.isConnected()
        if (isConn != connected) {
            connected = isConn
            updateConn()
            if (isConn) {
                if (c.port != 0) prefs().edit().putInt("port", c.port).apply()
                App.store.requeryObserved()
            } else {
                java.util.Arrays.fill(c.meters, -100f)
                Clip.reset()
                for (v in App.meterViews) v.invalidate()
            }
            showLostBanner(!isConn && everConnected)
            if (isConn) everConnected = true
        }
        if (connected) {
            Clip.scan(c.meters, android.os.SystemClock.uptimeMillis())
            for (v in App.meterViews) v.invalidate()
        }
        // Links estéreo: si cambian (desde la app, la consola u otra app), se rearman las capas.
        if (++linkTick >= 15) {
            linkTick = 0
            val now = linkedPairs()
            if (now != shownLinks) {
                shownLinks = now
                val keepDetail = detail
                rebuildRail()
                detail = keepDetail
                render()
            }
        }
        if (connected && ++requeryTick >= 45) {
            requeryTick = 0
            App.store.requeryMissing()
        }
        updateTimer()
    }

    private fun updateTimer() {
        val ms = ShowTimer.elapsed()
        val t = ShowTimer.format(ms)
        if (t == lastTimerText) return
        lastTimerText = t
        timerLabel.text = t
        if (::bigTimerText.isInitialized) bigTimerText.text = t
        timerLabel.setTextColor(
            when {
                ShowTimer.isRunning() -> C.GREEN
                ms > 0 -> C.SOLO
                else -> C.TEXT_DIM
            }
        )
    }

    /** Observa (sin vista) el estado de link de todos los pares, para que se pidan y se reintenten. */
    private val watchedLinks = HashSet<String>()
    private fun watchLinks() {
        for (p in chLinkPairs()) {
            val a = chLinkAddr(p)
            if (watchedLinks.add(a)) App.store.observe(a) {}
        }
        // Fuente del RTA (X Air): se conoce de antemano para poder devolverla al salir del EQ.
        if (!App.console.isX32 && watchedLinks.add(RTA_SOURCE)) App.store.observe(RTA_SOURCE) {}
        // Tipos de efecto cargados (para TAP tempo).
        for (n in 1..TapTempo.slots()) {
            val a = "/fx/$n/type"
            if (watchedLinks.add(a)) App.store.observe(a) {}
        }
    }

    /** Botón TAP de la pantalla principal: se mide en el momento del toque (ACTION_DOWN), no al soltar. */
    @android.annotation.SuppressLint("ClickableViewAccessibility")
    private fun tapButton(): Btn {
        val b = Btn(this, if (TapTempo.bpm > 0) "TAP ${TapTempo.bpm}" else "TAP", BtnStyle.OUTLINE, sz(15f, 12f))
        b.activeColor = C.ACCENT
        b.maxLines = 1
        b.setOnTouchListener { v, e ->
            if (e.actionMasked == android.view.MotionEvent.ACTION_DOWN) {
                val n = TapTempo.tap(android.os.SystemClock.uptimeMillis())
                b.active = true
                v.postDelayed({ b.active = false }, 90)
                if (n >= 0) {
                    b.text = "TAP ${TapTempo.bpm}"
                    if (n == 0) Toast.makeText(this, "No hay delays cargados en los efectos", Toast.LENGTH_SHORT).show()
                }
            }
            true
        }
        return b
    }

    private fun onXinfo(name: String, model: String, fw: String) {
        App.mixerName = name
        App.firmware = fw
        prefs().edit().putString("model", model).apply()
        val changed = App.applyModel(model)
        updateConn()
        watchLinks()
        if (changed) {
            sends = SendTarget.MAIN
            detail = null
            rebuildRail()
            render()
        }
    }

    private fun updateConn() {
        val ip = App.client?.host?.hostAddress ?: ""
        if (connected) {
            conn.text = "●  ${App.console.model}  $ip"
            conn.setTextColor(C.GREEN)
        } else {
            conn.text = "●  SIN CONEXIÓN"
            conn.setTextColor(C.RED)
        }
    }

    // ------------------------------------------------------------------ layout base

    private fun buildRoot(): View {
        val root = col()
        root.setBackgroundColor(C.BG)

        val top = row()
        top.setBackgroundColor(C.TOP)
        top.setPadding(dp(sz(16, 10)), 0, dp(sz(12, 8)), 0)
        // Logo 8C (curva EQ + "airmix"), mismo vector que el ícono. Generado por tools/logo/gen.py.
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_lockup)
        logo.scaleType = ImageView.ScaleType.FIT_START
        logo.contentDescription = "AirMix"
        top.addView(logo, lp(dp(sz(118, 88)), dp(sz(28, 21))))
        // Versión instalada (sale del versionName, así siempre coincide).
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        top.addView(label(ver, sz(12f, 10f), C.TEXT_DIM, Gravity.START or Gravity.BOTTOM), lp(dp(sz(48, 38)), dp(sz(26, 20))).m(l = dp(sz(6, 4))))
        top.addView(View(this), lp(dp(sz(12, 4)), 1))
        for (t in Tab.values()) {
            val b = Btn(this, t.label, BtnStyle.TAB, sz(16f, 12f))
            b.setOnClickListener {
                tab = t
                render()
            }
            tabBtns[t] = b
            top.addView(b, lp(dp(sz(124, 78)), dp(sz(40, 34))).m(r = dp(sz(6, 3))))
        }
        top.addView(View(this), lp(0, 1, 1f))
        // Aviso de solo prendido (CLR SOLO ahora vive en MEDIDORES): tocarlo apaga todos los solos.
        soloChip = Btn(this, "● SOLO", BtnStyle.LIGHT, sz(14f, 12f))
        soloChip.activeColor = C.SOLO
        soloChip.active = true
        soloChip.visibility = View.GONE
        soloChip.setOnClickListener { clearSolos() }
        soloChip.bind("/-stat/solo") { v -> soloChip.visibility = if (v.asI() != 0) View.VISIBLE else View.GONE }
        top.addView(soloChip, lp(dp(sz(96, 74)), dp(sz(40, 32))).m(r = dp(sz(8, 4))))
        // Lo copiado (COPIAR / PEGAR): se ve hasta que se borra. En celular, solo el ícono.
        clipChip = Btn(this, "", BtnStyle.LIGHT, sz(13f, 14f))
        clipChip.activeColor = C.SENDS
        clipChip.active = true
        clipChip.maxLines = 1
        clipChip.ellipsize = android.text.TextUtils.TruncateAt.END
        clipChip.visibility = View.GONE
        clipChip.setOnClickListener { clipInfo() }
        top.addView(clipChip, lp(dp(sz(190, 52)), dp(sz(40, 32))).m(r = dp(sz(8, 4))))
        Clipboard.onChange = { updateClipChip() }
        updateClipChip()
        lockBtn = Btn(this, "🔓", BtnStyle.OUTLINE, sz(18f, 15f))
        lockBtn.activeColor = C.ACCENT
        lockBtn.setOnClickListener {
            Toast.makeText(this, if (locked) "Mantené apretado para desbloquear" else "Mantené apretado para bloquear la pantalla", Toast.LENGTH_SHORT).show()
        }
        lockBtn.setOnLongClickListener {
            setLocked(!locked)
            true
        }
        top.addView(lockBtn, lp(dp(sz(52, 42)), dp(sz(40, 34))).m(r = dp(sz(8, 4))))
        conn = Btn(this, "", BtnStyle.OUTLINE, sz(14f, 12f))
        conn.maxLines = 1
        conn.ellipsize = android.text.TextUtils.TruncateAt.END
        conn.setPadding(dp(8), 0, dp(8), 0)
        conn.setOnClickListener { showConnect() }
        top.addView(conn, lp(dp(sz(240, 150)), dp(sz(40, 34))))
        root.addView(top, lp(MATCH, dp(sz(60, 46))))
        updateConn()

        val body = row()
        body.gravity = Gravity.TOP
        rail = col()
        rail.setBackgroundColor(C.NAV_BG)
        body.addView(rail, lp(dp(sz(116, 92)), MATCH))
        val host = FrameLayout(this)
        content = FrameLayout(this)
        host.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        notesPanel = buildNotes()
        notesPanel.visibility = View.GONE
        body.addView(host, lp(0, MATCH, 1f))
        if (Layout.compact) {
            // Celular: no hay lugar para correr las tiras; el panel va encima.
            host.addView(notesPanel, FrameLayout.LayoutParams(dp(372), MATCH, Gravity.END))
        } else {
            // Tablet: el panel ocupa su lugar y las tiras se angostan hacia la izquierda (no quedan tapadas).
            body.addView(notesPanel, lp(dp(420), MATCH))
        }
        root.addView(body, lp(MATCH, 0, 1f))

        // Capa de arriba: franja de corte, reloj gigante y candado (que bloquea todo menos a sí mismo).
        val frame = LockFrame(this)
        frame.addView(root, FrameLayout.LayoutParams(MATCH, MATCH))
        bigTimer = buildBigTimer()
        bigTimer.visibility = View.GONE
        frame.addView(bigTimer, FrameLayout.LayoutParams(MATCH, MATCH))
        lostBanner = label("⚠  SE CORTÓ LA CONEXIÓN CON LA CONSOLA  ·  reconectando…", sz(20f, 15f), Color.WHITE)
        lostBanner.setBackgroundColor(C.RED)
        lostBanner.maxLines = 1
        lostBanner.visibility = View.GONE
        lostBanner.elevation = dp(16).toFloat()
        frame.addView(lostBanner, FrameLayout.LayoutParams(MATCH, dp(sz(56, 42)), Gravity.TOP).also { it.topMargin = dp(sz(60, 46)) })
        return frame
    }

    /** Raíz que, con el candado puesto, se come todos los toques salvo los del candado. */
    private inner class LockFrame(ctx: Context) : FrameLayout(ctx) {
        private val r = android.graphics.Rect()
        private val loc = IntArray(2)

        private fun onLock(e: android.view.MotionEvent): Boolean {
            lockBtn.getLocationOnScreen(loc)
            r.set(loc[0], loc[1], loc[0] + lockBtn.width, loc[1] + lockBtn.height)
            return r.contains(e.rawX.toInt(), e.rawY.toInt())
        }

        override fun onInterceptTouchEvent(e: android.view.MotionEvent): Boolean = locked && !onLock(e)

        @android.annotation.SuppressLint("ClickableViewAccessibility")
        override fun onTouchEvent(e: android.view.MotionEvent): Boolean = locked
    }

    private fun showLostBanner(show: Boolean) {
        if (!::lostBanner.isInitialized) return
        val was = lostBanner.visibility == View.VISIBLE
        lostBanner.visibility = if (show) View.VISIBLE else View.GONE
        if (show && !was) vibrate(longArrayOf(0, 400, 200, 400))
    }

    private fun vibrate(pattern: LongArray) {
        try {
            val v = if (android.os.Build.VERSION.SDK_INT >= 31) {
                (getSystemService(Context.VIBRATOR_MANAGER_SERVICE) as android.os.VibratorManager).defaultVibrator
            } else {
                @Suppress("DEPRECATION")
                getSystemService(Context.VIBRATOR_SERVICE) as android.os.Vibrator
            }
            v.vibrate(android.os.VibrationEffect.createWaveform(pattern, -1))
        } catch (_: Exception) {}
    }

    /** Solo para las capturas automáticas de CI (ScreensTest). */
    internal fun debugScreen(name: String) {
        when (name) {
            "favoritos" -> {
                prefs().edit().putString("favs", "CH:1;CH:2;CH:7;CH:8;BUS:1").apply()
                rebuildRail()
                tab = Tab.MIX
                detail = null
                layerIdx = layers.size - 1
                render()
            }
            "linecheck" -> {
                val now = android.os.SystemClock.uptimeMillis() + 60_000L
                for (i in listOf(0, 1, 2, 6, 7)) Clip.signalAt[i] = now
                Clip.at[2] = now
                tab = Tab.METERS
                lineCheck = true
                render()
            }
            "reloj" -> {
                bigTimer.visibility = View.VISIBLE
                bigTimerText.text = "1:23:45"
            }
            "efectos" -> {
                bigTimer.visibility = View.GONE
                for ((n, t) in listOf(0, 10, 13, 21).withIndex()) App.store.incoming("/fx/${n + 1}/type", t)
                for (n in 1..4) for (k in 1..12) App.store.incoming("/fx/$n/par/${Fmt.two(k)}", (k * 7 % 10) / 10f)
                tab = Tab.FX
                render()
            }
            "corte" -> {
                bigTimer.visibility = View.GONE
                tab = Tab.MIX
                layerIdx = 0
                render()
                lostBanner.visibility = View.VISIBLE
                locked = true
                lockBtn.active = true
                lockBtn.text = "🔒"
            }
        }
    }

    /** CANDADO: mantener apretado para bloquear / desbloquear. La pantalla sigue prendida y mostrando todo. */
    private fun setLocked(on: Boolean) {
        locked = on
        lockBtn.active = on
        lockBtn.text = if (on) "🔒" else "🔓"
        vibrate(longArrayOf(0, 60))
        Toast.makeText(this, if (on) "Pantalla bloqueada. Mantené el candado para desbloquear." else "Pantalla desbloqueada", Toast.LENGTH_SHORT).show()
    }

    /** Reloj del show en pantalla completa: solo el tiempo, gigante. Toque = cerrar; mantener = poner en cero. */
    private fun buildBigTimer(): FrameLayout {
        val f = FrameLayout(this)
        f.setBackgroundColor(Color.BLACK)
        f.isClickable = true
        f.elevation = dp(20).toFloat()
        bigTimerText = TextView(this)
        bigTimerText.typeface = FONT
        bigTimerText.setTextColor(Color.WHITE)
        bigTimerText.gravity = Gravity.CENTER
        bigTimerText.maxLines = 1
        bigTimerText.includeFontPadding = false
        bigTimerText.fontFeatureSettings = "tnum" // números de ancho fijo: el reloj no "baila"
        bigTimerText.setAutoSizeTextTypeUniformWithConfiguration(40, 600, 2, android.util.TypedValue.COMPLEX_UNIT_SP)
        bigTimerText.text = ShowTimer.format(ShowTimer.elapsed())
        f.addView(bigTimerText, FrameLayout.LayoutParams(MATCH, MATCH).also { it.setMargins(dp(24), dp(24), dp(24), dp(24)) })
        f.setOnClickListener { f.visibility = View.GONE }
        f.setOnLongClickListener {
            confirmTimerReset()
            true
        }
        return f
    }

    private fun confirmTimerReset() {
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("¿Poner el cronómetro en cero?")
            .setPositiveButton("Reset") { _, _ ->
                ShowTimer.reset(prefs())
                lastTimerText = ""
                updateTimer()
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    /** Riel izquierdo: capas (con scroll si son muchas, ej. X32) + MUTE FX + CLR SOLO siempre a mano. */
    private fun rebuildRail() {
        layers = buildLayers() + Layer("FAVS ⭐️", favStrips(prefs().getString("favs", "") ?: ""), fav = true)
        if (layerIdx >= layers.size) layerIdx = 0
        rail.removeAllViews()
        layerBtns.clear()

        val timerBox = col()
        timerBox.gravity = Gravity.CENTER
        timerBox.background = rounded(C.SURFACE, 12)
        timerBox.addView(label("SHOW", 11f, C.TEXT_DIM), lp(MATCH, dp(16)))
        timerLabel = label("00:00", sz(24f, 18f), C.TEXT_DIM)
        lastTimerText = ""
        timerBox.addView(timerLabel, lp(MATCH, dp(sz(30, 24))))
        if (!Layout.compact) timerBox.addView(label("tocá: ▶ / ❚❚", 10f, C.TICK), lp(MATCH, dp(14)))
        timerBox.isClickable = true
        timerBox.setOnClickListener {
            ShowTimer.toggle(prefs())
            lastTimerText = ""
            updateTimer()
        }
        // Mantener apretado: reloj gigante para mostrarle al dúo (el reset quedó adentro, manteniendo apretado).
        timerBox.setOnLongClickListener {
            lastTimerText = ""
            bigTimer.visibility = View.VISIBLE
            updateTimer()
            true
        }
        rail.addView(timerBox, lp(MATCH, dp(sz(76, 58))).m(dp(sz(10, 6)), dp(sz(10, 6)), dp(sz(10, 6)), dp(2)))

        val list = col()
        list.setPadding(dp(sz(10, 6)), dp(sz(8, 4)), dp(sz(10, 6)), dp(sz(8, 4)))
        layers.forEachIndexed { i, l ->
            val b = Btn(this, l.label, BtnStyle.NAV, sz(15f, 12f))
            b.activeColor = if (l.fav) C.YELLOW else Sector.of(l.kind)
            b.idleText = if (l.fav) C.YELLOW else Sector.of(l.kind)
            b.refresh()
            b.setOnClickListener {
                layerIdx = i
                tab = Tab.MIX
                detail = null
                render()
            }
            layerBtns.add(b)
            list.addView(b, lp(MATCH, dp(sz(50, 38))).m(b = dp(sz(6, 4))))
        }
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        rail.addView(sv, lp(MATCH, 0, 1f))

        val notesBtn = Btn(this, "✎ NOTAS", BtnStyle.OUTLINE, sz(15f, 12f))
        notesBtn.setOnClickListener { toggleNotes() }
        rail.addView(notesBtn, lp(MATCH, dp(sz(44, 36))).m(dp(sz(10, 6)), dp(sz(8, 4)), dp(sz(10, 6)), 0))

        val fx = Btn(this, "MUTE\nFX", BtnStyle.MUTE, sz(17f, 13f))
        fx.maxLines = 2
        fx.activeColor = C.MUTE
        val refresh: (Any) -> Unit = {
            val all = FxMute.allMuted()
            fx.active = all
            fx.text = if (all) "FX\nMUTEADOS" else "MUTE\nFX"
        }
        for (a in FxMute.targets()) fx.bind(a, refresh)
        fx.setOnClickListener {
            FxMute.toggle()
            refresh(0)
        }
        rail.addView(fx, lp(MATCH, dp(sz(78, 56))).m(dp(sz(10, 6)), dp(sz(8, 4)), dp(sz(10, 6)), dp(sz(14, 6))))
    }

    /** CLR SOLO: apaga todos los solos (vive en MEDIDORES y en el aviso "● SOLO" de arriba). */
    private fun clearSolos() {
        val n = if (App.console.isX32) 80 else 36
        for (i in 1..n) App.store.set("/-stat/solosw/${Fmt.two(i)}", 0)
        App.store.set("/-stat/solo", 0)
    }

    private fun render() {
        content.removeAllViews()
        for ((t, b) in tabBtns) b.active = t == tab
        for (i in layerBtns.indices) layerBtns[i].active = tab == Tab.MIX && i == layerIdx
        val v: View = when (tab) {
            Tab.MIX -> {
                val d = detail
                if (d != null) buildDetail(d) else buildHome()
            }
            Tab.FX -> Rack.build(this)
            Tab.METERS -> buildMeters()
            Tab.SCENES -> buildScenes()
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    /** LINE CHECK: fichas verdes/grises por entrada en vez de las barras. */
    private var lineCheck = false

    /** MEDIDORES + LINE CHECK arriba a la izquierda + CLR SOLO arriba a la derecha. */
    private fun buildMeters(): View {
        val root = col()
        root.setBackgroundColor(C.BG)
        val bar = row()
        bar.setPadding(dp(16), dp(6), dp(16), 0)
        val lc = Btn(this, "LINE CHECK", BtnStyle.OUTLINE, sz(14f, 12f))
        lc.activeColor = C.GREEN
        lc.active = lineCheck
        lc.setOnClickListener {
            lineCheck = !lineCheck
            render()
        }
        bar.addView(lc, lp(dp(sz(140, 116)), dp(sz(40, 32))))
        if (lineCheck) {
            bar.addView(
                label("Verde = llega señal · gris = no llega nada · borde rojo = clip", sz(14f, 11f), C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL).also { it.maxLines = 1 },
                lp(0, dp(sz(40, 32)), 1f).m(l = dp(14))
            )
        } else bar.addView(View(this), lp(0, 1, 1f))
        val clr = Btn(this, "CLR SOLO", BtnStyle.OUTLINE, sz(14f, 12f))
        clr.setOnClickListener { clearSolos() }
        bar.addView(clr, lp(dp(sz(120, 100)), dp(sz(40, 32))))
        root.addView(bar, lp(MATCH, WRAP))
        root.addView(if (lineCheck) LineCheckView(this) else AllMetersView(this), lp(MATCH, 0, 1f))
        return root
    }

    private fun openDetail(s: Strip) {
        detail = s
        render()
    }

    // ------------------------------------------------------------------ MEZCLA

    private fun buildHome(): View {
        val root = col()
        root.setBackgroundColor(C.BG)
        val sendsMode = sends != SendTarget.MAIN
        val layer = layers.getOrNull(layerIdx)

        val header = row()
        header.setPadding(dp(16), 0, dp(10), 0)
        if (sendsMode) {
            val bg = rounded(if (sends.fx > 0) C.ACCENT else C.SENDS, 0)
            header.background = bg
            val title = label("SENDS ON FADER  →  ${sends.display("")}", sz(20f, 14f), C.INK, Gravity.START or Gravity.CENTER_VERTICAL)
            val target = sends
            if (target.bus > 0) {
                title.bind(target.nameAddr!!) { v -> title.text = "SENDS ON FADER  →  ${target.display(v.toString())}" }
                // El encabezado toma el color del bus.
                title.bind(Strip(Kind.BUS, target.bus).colorAddr) { v ->
                    bg.setColor(BusColor.of(v.asI()))
                    title.setTextColor(BusColor.ink(v.asI()))
                }
            } else if (target.fx > 0) {
                title.bind("/fx/${target.fx}/type") { v -> title.text = "SENDS ON FADER  →  ${Fx.label(target.fx, v.asI())}" }
            }
            header.addView(title, lp(0, MATCH, 1f))
            val change = Btn(this, "CAMBIAR ▾", BtnStyle.LIGHT, sz(15f, 12f))
            change.setOnClickListener { showSendsPopup(change) }
            header.addView(change, lp(dp(sz(130, 100)), dp(sz(40, 32))).m(r = dp(8)))
            val exit = Btn(this, "SALIR ✕", BtnStyle.LIGHT, sz(15f, 12f))
            exit.setOnClickListener {
                sends = SendTarget.MAIN
                render()
            }
            header.addView(exit, lp(dp(sz(110, 84)), dp(sz(40, 32))))
        } else {
            header.setBackgroundColor(C.PANEL)
            val sc = if (layer?.fav == true) C.YELLOW else Sector.of(layer?.kind ?: Kind.CH)
            header.addView(View(this).also { it.background = rounded(sc, 3) }, lp(dp(6), dp(28)).m(r = dp(12)))
            header.addView(
                label(layer?.label ?: "", sz(20f, 15f), sc, Gravity.START or Gravity.CENTER_VERTICAL),
                lp(0, MATCH, 1f)
            )
            if (layer?.fav == true) {
                val edit = Btn(this, "ELEGIR TIRAS", BtnStyle.OUTLINE, sz(15f, 12f))
                edit.setOnClickListener { editFavorites() }
                header.addView(edit, lp(dp(sz(150, 116)), dp(sz(40, 32))).m(r = dp(8)))
            }
            header.addView(tapButton(), lp(dp(sz(110, 84)), dp(sz(40, 32))).m(r = dp(8)))
            val sel = Btn(this, "SENDS ON FADER ▾", BtnStyle.OUTLINE, sz(15f, 12f))
            sel.setOnClickListener { showSendsPopup(sel) }
            header.addView(sel, lp(dp(sz(190, 140)), dp(sz(40, 32))))
        }
        root.addView(header, lp(MATCH, dp(sz(56, 40))))

        val strips = row()
        strips.gravity = Gravity.TOP
        strips.setPadding(dp(4), dp(4), dp(4), dp(4))
        val list = layer?.strips ?: emptyList()
        // Capas de canales: tantas tiras como haya (un par linkeado ocupa un lugar). El resto: 8 lugares fijos.
        if (layer?.fav == true && list.isEmpty()) {
            val empty = col()
            empty.gravity = Gravity.CENTER
            empty.addView(label("Todavía no elegiste favoritos.", sz(20f, 15f), C.TEXT), lp(MATCH, dp(40)))
            empty.addView(label("Armá tu propia capa con las tiras que más usás (hasta $MAX_FAVS): por ejemplo las voces, las pistas y un bus.", sz(15f, 12f), C.TEXT_DIM).also { it.maxLines = 2 }, lp(MATCH, WRAP).m(t = dp(6)))
            val pick = Btn(this, "ELEGIR TIRAS", BtnStyle.LIGHT, 17f)
            pick.activeColor = C.YELLOW
            pick.active = true
            pick.setOnClickListener { editFavorites() }
            empty.addView(pick, lp(dp(220), dp(52)).m(t = dp(16)))
            strips.addView(empty, lp(0, MATCH, 8f))
        }
        val slots = if (layer?.kind == Kind.CH || layer?.fav == true) list.size else 8
        for (i in 0 until slots) {
            val v = if (i < list.size) stripView(list[i], sends) { openDetail(it) } else View(this)
            strips.addView(v, lp(0, MATCH, 1f).m(dp(2), 0, dp(2), 0))
        }
        val master = when {
            sends.bus > 0 -> Strip(Kind.BUS, sends.bus)
            sends.fx > 0 -> Strip(Kind.FXSEND, sends.fx)
            else -> Strip(Kind.LR)
        }
        strips.addView(stripView(master, SendTarget.MAIN) { openDetail(it) }, lp(0, MATCH, 1f).m(dp(10), 0, dp(2), 0))
        root.addView(strips, lp(MATCH, 0, 1f))
        return root
    }

    /** Elegir las tiras de FAVORITOS (hasta MAX_FAVS), con su nombre actual. */
    private fun editFavorites() {
        val all = favCandidates()
        val saved = (prefs().getString("favs", "") ?: "").split(';').filter { it.isNotEmpty() }.toMutableList()
        val labels = all.map { s ->
            val n = (App.store.get(s.nameAddr) as? String).orEmpty()
            if (n.isEmpty()) s.title else "${s.title}  ·  $n"
        }.toTypedArray()
        val checked = BooleanArray(all.size) { all[it].favKey() in saved }
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Favoritos (hasta $MAX_FAVS)")
            .setMultiChoiceItems(labels, checked) { d, which, on ->
                if (on && checked.count { it } > MAX_FAVS) {
                    checked[which] = false
                    (d as AlertDialog).listView.setItemChecked(which, false)
                    Toast.makeText(this, "Máximo $MAX_FAVS tiras", Toast.LENGTH_SHORT).show()
                } else checked[which] = on
            }
            .setPositiveButton("Listo") { _, _ ->
                // Mantiene el orden en que estaban; las nuevas van al final.
                val keys = all.indices.filter { checked[it] }.map { all[it].favKey() }
                val ordered = saved.filter { it in keys } + keys.filter { it !in saved }
                prefs().edit().putString("favs", ordered.joinToString(";")).apply()
                rebuildRail()
                render()
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
        // Pide los nombres que falten, para la próxima vez que se abra.
        for (s in all) if (App.store.get(s.nameAddr) == null) App.client?.query(s.nameAddr)
    }

    private fun showSendsPopup(anchor: View) {
        val box = col()
        box.background = rounded(C.SURFACE, 14, C.LINE)
        box.setPadding(dp(8), dp(8), dp(8), dp(8))
        val pw = PopupWindow(box, dp(560), WRAP, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val cell = { t: SendTarget, label: String ->
            val b = Btn(this, label, BtnStyle.LIGHT, 17f)
            b.maxLines = 1
            b.ellipsize = android.text.TextUtils.TruncateAt.END
            b.setPadding(dp(8), 0, dp(8), 0)
            b.activeColor = if (t.fx > 0) C.ACCENT else C.SENDS
            b.active = t == sends
            if (t.bus > 0) {
                b.bind(t.nameAddr!!) { v -> b.text = t.display(v.toString()) }
                // Color del bus: texto de ese color; si está elegido, fondo de ese color.
                b.bind(Strip(Kind.BUS, t.bus).colorAddr) { v ->
                    b.activeColor = BusColor.of(v.asI())
                    b.refresh()
                    if (!b.active) b.setTextColor(BusColor.of(v.asI()))
                    else if (BusColor.ink(v.asI()) != C.INK) b.setTextColor(BusColor.ink(v.asI()))
                }
            } else if (t.fx > 0) {
                b.maxLines = 2
                b.textSize = 14f
                b.bind("/fx/${t.fx}/type") { v -> b.text = Fx.label(t.fx, v.asI()) }
            }
            b.setOnClickListener {
                sends = t
                pw.dismiss()
                render()
            }
            b
        }
        box.addView(cell(SendTarget.MAIN, "MAIN (normal)"), lp(MATCH, dp(52)).m(b = dp(6)))
        val targets = ArrayList<Pair<SendTarget, String>>()
        for (i in 1..App.console.buses) targets.add(SendTarget(bus = i) to "BUS $i")
        for (i in 1..App.console.fxSends) targets.add(SendTarget(fx = i) to "FX $i")
        for (chunk in targets.chunked(4)) {
            val r = row()
            for ((t, l) in chunk) r.addView(cell(t, l), lp(0, dp(52), 1f).m(dp(3), dp(3), dp(3), dp(3)))
            for (k in chunk.size until 4) r.addView(View(this), lp(0, dp(52), 1f).m(dp(3), dp(3), dp(3), dp(3)))
            box.addView(r)
        }
        pw.showAsDropDown(anchor, 0, dp(6), Gravity.END)
    }

    // ------------------------------------------------------------------ DETALLE

    private fun buildDetail(s: Strip): View {
        val secs = sectionsFor(s)
        if (section !in secs) section = secs.first()

        val root = col()
        root.setBackgroundColor(C.BG)

        val bar = row()
        bar.setBackgroundColor(C.PANEL)
        bar.setPadding(dp(10), 0, dp(10), 0)
        val back = Btn(this, "‹  VOLVER", BtnStyle.OUTLINE, 16f)
        back.setOnClickListener {
            detail = null
            render()
        }
        bar.addView(back, lp(dp(120), dp(42)))
        val title = label(s.title, 22f, C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL)
        title.bind(s.nameAddr) { v ->
            val n = v.toString()
            title.text = if (n.isEmpty()) s.title else "${s.title}  ·  $n"
        }
        bar.addView(title, lp(dp(300), MATCH).m(l = dp(16)))
        for (sec in secs) {
            val b = Btn(this, sec.label, BtnStyle.TAB, 16f)
            b.active = sec == section
            val onAddr = when (sec) {
                Section.GATE -> "${s.prefix}/gate/on"
                Section.DYN -> "${s.prefix}/dyn/on"
                Section.EQ -> "${s.prefix}/eq/on"
                else -> null
            }
            if (onAddr != null) b.bind(onAddr) { v -> b.text = (if (v.asI() != 0) "● " else "") + sec.label }
            b.setOnClickListener {
                section = sec
                render()
            }
            bar.addView(b, lp(dp(110), dp(42)).m(r = dp(6)))
        }
        bar.addView(View(this), lp(0, 1, 1f))
        // COPIAR / PEGAR seteos de esta tira.
        val cp = Btn(this, if (Layout.compact) "📋" else "📋 COPIAR / PEGAR", BtnStyle.OUTLINE, sz(15f, 16f))
        cp.setOnClickListener { clipMenu(cp, s) }
        bar.addView(cp, lp(dp(sz(200, 54)), dp(42)).m(r = dp(6)))
        val prev = Btn(this, "‹", BtnStyle.OUTLINE, 24f)
        prev.setOnClickListener { step(s, -1) }
        bar.addView(prev, lp(dp(54), dp(42)).m(r = dp(6)))
        val next = Btn(this, "›", BtnStyle.OUTLINE, 24f)
        next.setOnClickListener { step(s, 1) }
        bar.addView(next, lp(dp(54), dp(42)))
        root.addView(hwrap(bar), lp(MATCH, dp(sz(60, 48))))

        val body = row()
        body.gravity = Gravity.TOP
        body.setPadding(dp(4), dp(4), dp(4), dp(4))
        if (Layout.compact) body.addView(stripView(s, SendTarget.MAIN) {}, lp(dp(96), MATCH).m(r = dp(4)))
        else body.addView(stripView(s, SendTarget.MAIN) {}, lp(0, MATCH, 1f).m(r = dp(4)))
        val panel = FrameLayout(this)
        panel.background = rounded(C.PANEL, 14)
        panel.addView(Panels.build(this, s, section), FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(panel, lp(0, MATCH, if (Layout.compact) 1f else 8f))
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    // ------------------------------------------------------------------ COPIAR / PEGAR

    private fun updateClipChip() {
        if (!::clipChip.isInitialized) return
        val c = Clipboard.clip
        clipChip.visibility = if (c == null) View.GONE else View.VISIBLE
        clipChip.text = if (c == null) "" else if (Layout.compact) "📋" else "📋 ${c.label}"
    }

    private fun clipInfo() {
        val c = Clipboard.clip ?: return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Copiado: ${c.label}")
            .setMessage("Abrí otra tira del mismo tipo y tocá 📋 → Pegar. También podés pegarlo en varias de una vez.")
            .setPositiveButton("Pegar en varios…") { _, _ -> pasteMany() }
            .setNeutralButton("Borrar lo copiado") { _, _ -> Clipboard.clear() }
            .setNegativeButton("Cerrar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    private fun clipMenu(anchor: View, s: Strip) {
        val pm = android.widget.PopupMenu(this, anchor)
        val opts = Clipboard.options(s, if (detail == s) section else null)
        opts.forEachIndexed { i, w ->
            val what = if (w == Clipboard.What.ALL) (if (s.kind == Kind.CH) "CANAL ENTERO" else "${s.title} ENTERO") else w.label
            pm.menu.add(0, i, i, "Copiar $what")
        }
        val c = Clipboard.clip
        if (c != null && Clipboard.canPaste(s)) pm.menu.add(1, 100, 100, "Pegar ${c.label} acá")
        if (c != null) {
            pm.menu.add(1, 101, 101, "Pegar ${c.label} en varios…")
            pm.menu.add(1, 102, 102, "Borrar lo copiado")
        }
        pm.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                100 -> confirmPaste(listOf(s))
                101 -> pasteMany()
                102 -> Clipboard.clear()
                else -> {
                    val w = opts[item.itemId]
                    Clipboard.copy(s, w) { n ->
                        Toast.makeText(this, if (n > 0) "📋 Copiado: ${Clipboard.clip?.label} ($n valores)" else "No se pudo leer de la consola: ¿está conectada?", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            true
        }
        pm.show()
    }

    private fun pasteMany() {
        val c = Clipboard.clip ?: return
        val list = Clipboard.targets()
        if (list.isEmpty()) return
        val labels = list.map { t ->
            val n = (App.store.get(t.nameAddr) as? String).orEmpty()
            if (n.isEmpty()) t.title else "${t.title}  ·  $n"
        }.toTypedArray()
        val checked = BooleanArray(list.size)
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Pegar ${c.label} en…")
            .setMultiChoiceItems(labels, checked) { _, i, on -> checked[i] = on }
            .setPositiveButton("Siguiente") { _, _ ->
                val sel = list.indices.filter { checked[it] }.map { list[it] }
                if (sel.isNotEmpty()) confirmPaste(sel)
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    private fun confirmPaste(targets: List<Strip>) {
        val c = Clipboard.clip ?: return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Pegar ${c.label}")
            .setMessage("En: ${targets.joinToString(", ") { it.title }}.\n\nReemplaza lo que tienen ahora (no se tocan nombre, entrada, GAIN ni 48V).")
            .setPositiveButton("Pegar") { _, _ ->
                var n = 0
                for (t in targets) n += Clipboard.paste(t)
                Toast.makeText(this, "✓ Pegado en ${targets.size} ${if (targets.size == 1) "tira" else "tiras"}", Toast.LENGTH_SHORT).show()
                if (n > 0 && detail != null) render()
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    private fun step(s: Strip, dir: Int) {
        val list = stripsOfKind(s.kind)
        val i = list.indexOf(s.copy(pair = false))
        if (i < 0 || list.isEmpty()) return
        detail = list[(i + dir + list.size) % list.size]
        render()
    }

    // ------------------------------------------------------------------ conexión

    private fun showConnect() {
        if (dialog != null) return
        val box = col()
        box.setPadding(dp(24), dp(12), dp(24), dp(4))
        box.addView(label("Consolas encontradas en la red:", 16f, C.TEXT_DIM, Gravity.START), lp(MATCH, dp(30)))
        val list = col()
        list.addView(label("Buscando…", 16f, C.TEXT_DIM, Gravity.START), lp(MATCH, dp(40)))
        box.addView(list, lp(MATCH, WRAP))
        box.addView(label("O escribí la IP de la consola (XR12 / XR16 / XR18 / X32):", 16f, C.TEXT_DIM, Gravity.START), lp(MATCH, dp(30)).m(t = dp(16)))
        val ip = EditText(this)
        ip.setText(prefs().getString("ip", "192.168.1.1"))
        ip.setTextColor(Color.WHITE)
        ip.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        ip.setSingleLine()
        box.addView(ip, lp(MATCH, WRAP))

        dialogList = list
        seen.clear()
        dialog = AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Conectar a la consola")
            .setView(box)
            .setPositiveButton("Conectar") { _, _ -> connectTo(ip.text.toString(), 0) }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener {
                dialog = null
                dialogList = null
                hideBars()
            }
            .show()
        App.client?.discover()
        handler.postDelayed({ App.client?.discover() }, 1500)
    }

    private fun onDiscovered(d: MixerInfo) {
        val list = dialogList ?: return
        if (!seen.add(d.ip)) return
        if (seen.size == 1) list.removeAllViews()
        val b = Btn(this, "${d.model}  ·  ${d.name}  ·  ${d.ip}", BtnStyle.LIGHT, 18f)
        b.setOnClickListener {
            App.applyModel(d.model)
            prefs().edit().putString("model", d.model).apply()
            connectTo(d.ip, d.port)
            dialog?.dismiss()
            rebuildRail()
            render()
        }
        list.addView(b, lp(MATCH, dp(54)).m(t = dp(6)))
    }

    private fun connectTo(ip: String, port: Int) {
        val c = App.client ?: return
        if (c.connect(ip, port)) {
            prefs().edit().putString("ip", ip.trim()).putInt("port", port).apply()
            App.store.resetValues()
            connected = false
            updateConn()
            Toast.makeText(this, "Conectando a ${ip.trim()}…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "IP inválida", Toast.LENGTH_SHORT).show()
        }
    }

    // ------------------------------------------------------------------ NOTAS

    private val saveNotes = Runnable { prefs().edit().putString("notes", notesEdit.text.toString()).apply() }

    private var notesTab = 0 // 0 = NOTAS, 1 = TEMAS
    private lateinit var notesBody: FrameLayout
    private lateinit var notesBox: View
    private lateinit var songsBox: View
    private lateinit var songsList: LinearLayout
    private lateinit var songsSource: TextView
    private val notesActions = ArrayList<View>()
    private val notesTabs = ArrayList<Btn>()

    private fun buildNotes(): View {
        val panel = col()
        panel.background = rounded(C.SURFACE, 16, C.LINE)
        panel.setPadding(dp(16), dp(12), dp(16), dp(16))
        panel.isClickable = true
        panel.elevation = dp(12).toFloat()

        val head = row()
        for ((i, t) in listOf("NOTAS", "TEMAS").withIndex()) {
            val b = Btn(this, t, BtnStyle.TAB, sz(16f, 14f))
            b.setOnClickListener { showNotesTab(i) }
            notesTabs.add(b)
            head.addView(b, lp(dp(sz(80, 62)), dp(40)).m(r = dp(6)))
        }
        head.addView(View(this), lp(0, 1, 1f))
        val mark = Btn(this, "+ MARCA", BtnStyle.LIGHT, sz(14f, 12f))
        mark.setOnClickListener { insertMark() }
        head.addView(mark, lp(dp(sz(84, 72)), dp(40)).m(r = dp(6)))
        val clear = Btn(this, "BORRAR", BtnStyle.OUTLINE, sz(13f, 11f))
        clear.setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("¿Borrar todas las notas?")
                .setPositiveButton("Borrar") { _, _ -> notesEdit.setText("") }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { hideBars() }
                .show()
        }
        head.addView(clear, lp(dp(sz(72, 60)), dp(40)).m(r = dp(6)))
        notesActions.add(mark)
        notesActions.add(clear)
        val close = Btn(this, "✕", BtnStyle.OUTLINE, 16f)
        close.setOnClickListener { toggleNotes() }
        head.addView(close, lp(dp(sz(42, 36)), dp(40)))
        panel.addView(head)

        notesEdit = EditText(this)
        notesEdit.setTextColor(C.TEXT)
        notesEdit.setHintTextColor(C.TICK)
        notesEdit.hint = "Anotá lo que necesites: cambios de set, problemas de monitor, pedidos…"
        notesEdit.textSize = 17f
        notesEdit.gravity = Gravity.TOP or Gravity.START
        notesEdit.background = rounded(C.BG, 12)
        notesEdit.setPadding(dp(14), dp(12), dp(14), dp(12))
        notesEdit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_MULTI_LINE or InputType.TYPE_TEXT_FLAG_CAP_SENTENCES
        notesEdit.setText(prefs().getString("notes", ""))
        notesEdit.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                handler.removeCallbacks(saveNotes)
                handler.postDelayed(saveNotes, 700)
            }
        })
        notesBox = notesEdit
        songsBox = buildSongs()
        notesBody = FrameLayout(this)
        notesBody.addView(notesBox, FrameLayout.LayoutParams(MATCH, MATCH))
        notesBody.addView(songsBox, FrameLayout.LayoutParams(MATCH, MATCH))
        panel.addView(notesBody, lp(MATCH, 0, 1f).m(t = dp(8)))
        showNotesTab(notesTab)
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(8), dp(8), dp(8), dp(8))
        wrap.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))
        return wrap
    }

    private fun showNotesTab(i: Int) {
        notesTab = i
        for (k in notesTabs.indices) notesTabs[k].active = k == i
        notesBox.visibility = if (i == 0) View.VISIBLE else View.GONE
        songsBox.visibility = if (i == 1) View.VISIBLE else View.GONE
        for (a in notesActions) a.visibility = if (i == 0) View.VISIBLE else View.GONE
        if (i == 1) {
            notesEdit.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(notesEdit.windowToken, 0)
        }
    }

    // ------------------------------------------------------------------ TEMAS (lista de temas)

    private fun buildSongs(): View {
        val box = col()
        val tools = row()
        val load = Btn(this, "CARGAR ARCHIVO…", BtnStyle.LIGHT, 14f)
        load.setOnClickListener { openSetlistFile() }
        tools.addView(load, lp(0, dp(42), 1.4f).m(r = dp(6)))
        val add = Btn(this, "+ TEMA", BtnStyle.LIGHT, 14f)
        add.setOnClickListener { editSong(-1) }
        tools.addView(add, lp(0, dp(42), 1f).m(r = dp(6)))
        val empty = Btn(this, "VACIAR", BtnStyle.OUTLINE, 13f)
        empty.setOnClickListener {
            if (Setlist.songs.isEmpty()) return@setOnClickListener
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("¿Vaciar la lista de temas?")
                .setPositiveButton("Vaciar") { _, _ ->
                    Setlist.songs.clear()
                    Setlist.current = -1
                    Setlist.source = ""
                    songsChanged()
                }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { hideBars() }
                .show()
        }
        tools.addView(empty, lp(0, dp(42), 0.8f))
        box.addView(tools)
        songsSource = label("", 12f, C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL)
        box.addView(songsSource, lp(MATCH, dp(22)).m(t = dp(4)))
        songsList = col()
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(songsList, FrameLayout.LayoutParams(MATCH, WRAP))
        box.addView(sv, lp(MATCH, 0, 1f))
        val nav = row()
        val prev = Btn(this, "◀  ANTERIOR", BtnStyle.OUTLINE, 15f)
        prev.setOnClickListener { stepSong(-1) }
        nav.addView(prev, lp(0, dp(48), 1f).m(r = dp(6)))
        val next = Btn(this, "SIGUIENTE  ▶", BtnStyle.LIGHT, 15f)
        next.activeColor = C.ACCENT
        next.active = true
        next.setOnClickListener { stepSong(1) }
        nav.addView(next, lp(0, dp(48), 1f))
        box.addView(nav, lp(MATCH, WRAP).m(t = dp(8)))
        renderSongs()
        return box
    }

    private fun songsChanged() {
        Setlist.save(prefs())
        renderSongs()
    }

    private fun stepSong(dir: Int) {
        val n = Setlist.songs.size
        if (n == 0) return
        Setlist.current = when {
            Setlist.current < 0 -> if (dir > 0) 0 else n - 1
            else -> (Setlist.current + dir).coerceIn(0, n - 1)
        }
        songsChanged()
    }

    private fun renderSongs() {
        songsList.removeAllViews()
        val n = Setlist.songs.size
        songsSource.text = when {
            n == 0 -> "Sin temas. Cargá un .txt / .doc / .docx (un tema por línea) o agregalos a mano."
            Setlist.source.isNotEmpty() -> "$n temas · ${Setlist.source}"
            else -> "$n temas"
        }
        songsSource.maxLines = 2
        Setlist.songs.forEachIndexed { i, s ->
            val cur = i == Setlist.current
            val r = row()
            r.background = rounded(if (cur) C.SURFACE2 else C.BG, 10, if (cur) C.ACCENT else 0)
            r.setPadding(dp(8), dp(4), dp(4), dp(4))
            r.addView(label("${i + 1}", 15f, if (cur) C.ACCENT else C.TEXT_DIM), lp(dp(30), MATCH))
            val txt = col()
            val t = label(s.title, 16f, if (cur) C.TEXT else C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL)
            t.maxLines = 2
            txt.addView(t, lp(MATCH, WRAP))
            if (s.note.isNotEmpty()) {
                val nt = label(s.note, 13f, C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL)
                nt.typeface = FONT_MEDIUM
                nt.maxLines = 3
                txt.addView(nt, lp(MATCH, WRAP).m(t = dp(2)))
            }
            txt.isClickable = true
            txt.setOnClickListener {
                Setlist.current = if (cur) -1 else i
                songsChanged()
            }
            r.addView(txt, lp(0, WRAP, 1f).m(l = dp(4), r = dp(4)))
            for ((sym, act) in listOf<Pair<String, () -> Unit>>(
                "▲" to { Setlist.move(i, -1); songsChanged() },
                "▼" to { Setlist.move(i, 1); songsChanged() },
                "✎" to { editSong(i) },
                "✕" to { Setlist.remove(i); songsChanged() }
            )) {
                val b = Btn(this, sym, BtnStyle.SMALL, 14f)
                b.setOnClickListener { act() }
                r.addView(b, lp(dp(34), dp(36)).m(l = dp(3)))
            }
            songsList.addView(r, lp(MATCH, WRAP).m(t = dp(6)))
        }
    }

    /** Editar (i >= 0) o agregar (i = -1) un tema. */
    private fun editSong(i: Int) {
        val s = Setlist.songs.getOrNull(i)
        val box = col()
        box.setPadding(dp(24), dp(8), dp(24), 0)
        val title = EditText(this)
        title.hint = "Tema"
        title.setSingleLine()
        title.setTextColor(Color.WHITE)
        title.setText(s?.title ?: "")
        box.addView(title, lp(MATCH, WRAP))
        val note = EditText(this)
        note.hint = "Nota (tono, quién arranca, cambios…)"
        note.setTextColor(Color.WHITE)
        note.setText(s?.note ?: "")
        box.addView(note, lp(MATCH, WRAP).m(t = dp(8)))
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle(if (s == null) "Agregar tema" else "Editar tema")
            .setView(box)
            .setPositiveButton("Guardar") { _, _ ->
                val t = title.text.toString().trim()
                if (t.isEmpty()) return@setPositiveButton
                if (s == null) Setlist.songs.add(Song(t, note.text.toString().trim()))
                else {
                    s.title = t
                    s.note = note.text.toString().trim()
                }
                songsChanged()
            }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun openSetlistFile() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(
                Intent.EXTRA_MIME_TYPES, arrayOf(
                    "text/plain", "text/rtf", "application/rtf", "application/msword",
                    "application/vnd.openxmlformats-officedocument.wordprocessingml.document", "application/octet-stream"
                )
            )
        try { startActivityForResult(i, REQ_SETLIST) } catch (_: Exception) {}
    }

    private fun onSetlistFile(bytes: ByteArray, display: String) {
        val text = DocText.extract(bytes)
        val list = text?.let { Setlist.parse(it) } ?: emptyList()
        if (list.isEmpty()) {
            Toast.makeText(this, "No encontré temas en ese archivo. Probá con un .txt o .docx, un tema por línea.", Toast.LENGTH_LONG).show()
            return
        }
        val apply = {
            Setlist.songs.clear()
            Setlist.songs.addAll(list)
            Setlist.current = -1
            Setlist.source = display
            songsChanged()
            Toast.makeText(this, "${list.size} temas cargados", Toast.LENGTH_SHORT).show()
        }
        if (Setlist.songs.isEmpty()) apply()
        else AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("¿Reemplazar la lista actual (${Setlist.songs.size} temas) por los ${list.size} de \"$display\"?")
            .setPositiveButton("Reemplazar") { _, _ -> apply() }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    private fun toggleNotes() {
        val show = notesPanel.visibility != View.VISIBLE
        notesPanel.visibility = if (show) View.VISIBLE else View.GONE
        if (!show) {
            notesEdit.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as android.view.inputmethod.InputMethodManager
            imm.hideSoftInputFromWindow(notesEdit.windowToken, 0)
            saveNotes.run()
            hideBars()
        }
    }

    /** Inserta "[12:34] " con el tiempo del show (o la hora, si el cronómetro está en cero). */
    private fun insertMark() {
        val ms = ShowTimer.elapsed()
        val stamp = if (ms > 0) ShowTimer.format(ms)
        else java.text.SimpleDateFormat("HH:mm", java.util.Locale.US).format(java.util.Date())
        val ed = notesEdit.text
        var pos = notesEdit.selectionStart
        if (pos < 0) pos = ed.length
        val prefix = if (pos > 0 && ed[pos - 1] != '\n') "\n" else ""
        ed.insert(pos, "$prefix[$stamp] ")
        notesEdit.requestFocus()
    }

    // ------------------------------------------------------------------ ESCENAS

    private fun scenesDir(): java.io.File = java.io.File(filesDir, "scenes").also { it.mkdirs() }

    private fun safeName(n: String): String =
        n.trim().ifEmpty { "escena" }.replace(Regex("[^A-Za-z0-9 _\\-áéíóúñÁÉÍÓÚÑ]"), "_").take(40)

    private fun buildScenes(): View {
        val root = row()
        root.gravity = Gravity.TOP
        root.setBackgroundColor(C.BG)
        root.setPadding(dp(14), dp(14), dp(14), dp(14))

        // --- guardar
        val left = col()
        val saveCard = card()
        saveCard.addView(sectionTitle("GUARDAR LA MEZCLA ACTUAL"), lp(MATCH, dp(24)))
        val name = EditText(this)
        name.setTextColor(C.TEXT)
        name.setHintTextColor(C.TICK)
        name.hint = "Nombre de la escena"
        name.setText("GYP " + java.text.SimpleDateFormat("dd-MM", java.util.Locale.US).format(java.util.Date()))
        name.setSingleLine()
        name.background = rounded(C.BG, 10, C.LINE)
        name.setPadding(dp(14), 0, dp(14), 0)
        saveCard.addView(name, lp(MATCH, dp(50)).m(t = dp(8)))
        val notes = EditText(this)
        notes.setTextColor(C.TEXT)
        notes.setHintTextColor(C.TICK)
        notes.hint = "Nota (opcional): lugar, fecha…"
        notes.setSingleLine()
        notes.background = rounded(C.BG, 10, C.LINE)
        notes.setPadding(dp(14), 0, dp(14), 0)
        saveCard.addView(notes, lp(MATCH, dp(50)).m(t = dp(8)))
        val save = Btn(this, "LEER CONSOLA Y GUARDAR", BtnStyle.LIGHT, 17f)
        save.activeColor = C.ACCENT
        save.active = true
        save.setOnClickListener { Scenes.save(name.text.toString(), notes.text.toString()) }
        saveCard.addView(save, lp(MATCH, dp(54)).m(t = dp(12)))
        saveCard.addView(
            label("Se guarda en la tablet, sin internet: en la app y en Descargas/AirMix (se ve en Mis archivos). Se abre en X-Air Edit / X32-Edit.", 13f, C.TEXT_DIM, Gravity.START).also { it.maxLines = 3 },
            lp(MATCH, WRAP).m(t = dp(10))
        )
        left.addView(saveCard, lp(MATCH, WRAP))

        val statusCard = card()
        statusCard.addView(sectionTitle("ESTADO"), lp(MATCH, dp(24)))
        val status = label("Listo.", 15f, C.TEXT, Gravity.START or Gravity.TOP)
        status.maxLines = 8
        statusCard.addView(status, lp(MATCH, WRAP).m(t = dp(6)))
        left.addView(statusCard, lp(MATCH, WRAP).m(t = dp(14)))
        left.addView(View(this), lp(MATCH, 0, 1f))
        val about = Btn(this, "ACERCA DE", BtnStyle.OUTLINE, 13f)
        about.setTextColor(C.TEXT_DIM)
        about.setOnClickListener { showAbout() }
        left.addView(about, lp(dp(140), dp(38)).m(t = dp(10)))
        Scenes.onStatus = { msg -> status.text = msg }
        Scenes.onSaveReady = { n, text -> onSceneRead(n, text) }
        root.addView(left, lp(0, MATCH, 1f).m(r = dp(14)))

        // --- cargar
        val loadCard = card()
        val head = row()
        head.addView(sectionTitle("ESCENAS EN LA TABLET"), lp(0, dp(40), 1f))
        val open = Btn(this, "ABRIR .scn…", BtnStyle.OUTLINE, 15f)
        open.setOnClickListener { openDocument() }
        head.addView(open, lp(dp(140), dp(40)))
        loadCard.addView(head)
        val list = col()
        val files = scenesDir().listFiles()?.filter { it.name.endsWith(".scn") }?.sortedByDescending { it.lastModified() } ?: emptyList()
        if (files.isEmpty()) {
            list.addView(label("Todavía no hay escenas guardadas.", 15f, C.TEXT_DIM, Gravity.START), lp(MATCH, dp(40)))
            // Si se reinstaló la app, las copias siguen en Descargas/AirMix: se recuperan de una.
            val rec = Btn(this, "RECUPERAR ESCENAS DE DESCARGAS/AIRMIX", BtnStyle.LIGHT, 14f)
            rec.activeColor = C.SENDS
            rec.active = true
            rec.setOnClickListener { recoverScenes() }
            list.addView(rec, lp(MATCH, dp(50)).m(t = dp(8)))
            list.addView(
                label("Abre Descargas/AirMix: seleccioná todas las escenas (mantené apretada una y tocá las demás) y quedan otra vez en la app.", 13f, C.TEXT_DIM, Gravity.START).also { it.maxLines = 3 },
                lp(MATCH, WRAP).m(t = dp(6))
            )
        }
        val df = java.text.SimpleDateFormat("dd/MM HH:mm", java.util.Locale.US)
        for (file in files) {
            val r = row()
            r.background = rounded(C.SURFACE2, 10)
            r.setPadding(dp(14), 0, dp(8), 0)
            val t = label(file.name.removeSuffix(".scn"), 17f, C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL)
            r.addView(t, lp(0, MATCH, 1f))
            r.addView(label(df.format(java.util.Date(file.lastModified())), 13f, C.TEXT_DIM), lp(dp(100), MATCH))
            val load = Btn(this, "CARGAR", BtnStyle.LIGHT, 15f)
            load.activeColor = C.SENDS
            load.active = true
            load.setOnClickListener {
                val text = file.readText(Charsets.ISO_8859_1)
                confirmLoad(Scenes.parse(text, file.name.removeSuffix(".scn")))
            }
            r.addView(load, lp(dp(100), dp(40)).m(r = dp(6)))
            val share = Btn(this, "EXPORTAR", BtnStyle.OUTLINE, 13f)
            share.setOnClickListener { exportMenu(share, file.name.removeSuffix(".scn"), file.readText(Charsets.ISO_8859_1)) }
            r.addView(share, lp(dp(90), dp(40)).m(r = dp(6)))
            val del = Btn(this, "✕", BtnStyle.OUTLINE, 15f)
            del.setOnClickListener {
                AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                    .setMessage("¿Borrar \"${file.name}\" de la tablet?")
                    .setPositiveButton("Borrar") { _, _ ->
                        file.delete()
                        render()
                    }
                    .setNegativeButton("Cancelar", null)
                    .setOnDismissListener { hideBars() }
                    .show()
            }
            r.addView(del, lp(dp(44), dp(40)))
            list.addView(r, lp(MATCH, dp(56)).m(t = dp(8)))
        }
        loadCard.addView(scroll(list), lp(MATCH, 0, 1f).m(t = dp(6)))
        root.addView(loadCard, lp(0, MATCH, 1.3f))
        return root
    }

    /** ACERCA DE: autoría, contacto y créditos. */
    private fun showAbout() {
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        val msg = "AirMix $ver\n\n" +
            "Creada por Joaquín Nachman · 2026\n" +
            "Instagram: @joaquinnachman\n\n" +
            "Errores y sugerencias: joaquinnachman@gmail.com\n\n" +
            "App independiente, compatible con consolas Behringer X Air y X32. No está afiliada a Behringer ni a Music Tribe.\n\n" +
            "Tablas de efectos y formato de escenas basados en el trabajo de Patrick-Gilles Maillot. Tipografía del logo: Unbounded (SIL OFL)."
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Acerca de")
            .setMessage(msg)
            // Se cierra tocando afuera; los tres botones son acciones.
            .setPositiveButton("Buscar actualización") { _, _ -> Updater.check(this, manual = true) { true } }
            .setNeutralButton("Reportar error") { _, _ ->
                val i = Intent(Intent.ACTION_SENDTO, android.net.Uri.parse("mailto:joaquinnachman@gmail.com"))
                    .putExtra(Intent.EXTRA_SUBJECT, "AirMix $ver: error / sugerencia")
                try { startActivity(i) } catch (_: Exception) {
                    Toast.makeText(this, "joaquinnachman@gmail.com", Toast.LENGTH_LONG).show()
                }
            }
            .setNegativeButton("Instagram") { _, _ ->
                try { startActivity(Intent(Intent.ACTION_VIEW, android.net.Uri.parse("https://instagram.com/joaquinnachman"))) } catch (_: Exception) {}
            }
            .setOnDismissListener { hideBars() }
            .show()
    }

    private fun onSceneRead(name: String, text: String) {
        val f = java.io.File(scenesDir(), safeName(name) + ".scn")
        f.writeText(text, Charsets.ISO_8859_1)
        if (tab == Tab.SCENES) render()
        // Copia local en Descargas/AirMix: no necesita internet (la tablet suele estar en el WiFi de la consola).
        val where = saveToDownloads(name, text)
        Scenes.onStatus?.invoke(
            if (where != null) "✓ Guardada en la tablet: \"${f.name}\" (en la app y en $where)."
            else "✓ Guardada en la app como \"${f.name}\". No se pudo copiar a Descargas: usá EXPORTAR."
        )
    }

    /** EXPORTAR: a Descargas de la tablet (local) o a otra carpeta / app con el selector del sistema. */
    private fun exportMenu(anchor: View, name: String, text: String) {
        val pm = android.widget.PopupMenu(this, anchor)
        pm.menu.add(0, 1, 0, "Guardar en Descargas/AirMix (tablet)")
        pm.menu.add(0, 2, 1, "Elegir carpeta o app…")
        pm.setOnMenuItemClickListener { item ->
            if (item.itemId == 1) {
                val where = saveToDownloads(name, text)
                Toast.makeText(this, if (where != null) "Guardada en $where" else "No se pudo guardar en Descargas", Toast.LENGTH_LONG).show()
            } else {
                exportDocument(name, text)
            }
            true
        }
        pm.show()
    }

    /**
     * Guarda el .scn en Descargas/AirMix de la tablet (MediaStore, Android 10+, sin permisos).
     * Devuelve la ruta para mostrar, o null si no se pudo.
     */
    private fun saveToDownloads(name: String, text: String): String? {
        val file = safeName(name) + ".scn"
        val bytes = text.toByteArray(Charsets.ISO_8859_1)
        return try {
            if (android.os.Build.VERSION.SDK_INT >= 29) {
                val cv = android.content.ContentValues().apply {
                    put(android.provider.MediaStore.MediaColumns.DISPLAY_NAME, file)
                    put(android.provider.MediaStore.MediaColumns.MIME_TYPE, "application/octet-stream")
                    put(android.provider.MediaStore.MediaColumns.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS + "/AirMix")
                }
                val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv) ?: return null
                contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: return null
                "Descargas/AirMix/$file"
            } else {
                @Suppress("DEPRECATION")
                val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AirMix")
                dir.mkdirs()
                java.io.File(dir, file).writeBytes(bytes)
                "Descargas/AirMix/$file"
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun confirmLoad(scene: Scenes.Scene) {
        if (Scenes.busy()) return
        val ph = Scenes.phantomOn(scene)
        val warn = if (ph.isEmpty()) "\n\n48V: apagado en todas las entradas."
        else "\n\n⚠ ESTA ESCENA PRENDE 48V EN: entrada ${ph.joinToString(", ")}. El resto queda apagado."
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Cargar \"${scene.name}\"")
            .setMessage("Esto reemplaza la mezcla actual de la consola (${scene.lines.size} bloques). Los links stereo y el 48V quedan como dice la escena; lo que no trae, apagado.$warn\n\nAntes de cargar se verifica el formato con la consola; lo que no se pueda verificar queda como está.")
            .setPositiveButton("Cargar") { _, _ -> Scenes.load(scene) }
            .setNegativeButton("Cancelar", null)
            .setOnDismissListener { hideBars() }
            .show()
    }

    @Suppress("DEPRECATION")
    private fun exportDocument(name: String, text: String) {
        pendingExport = text
        val i = Intent(Intent.ACTION_CREATE_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("application/octet-stream")
            .putExtra(Intent.EXTRA_TITLE, safeName(name) + ".scn")
        try { startActivityForResult(i, REQ_SAVE) } catch (_: Exception) {}
    }

    /** Selector del sistema abierto en Descargas/AirMix, con selección múltiple. */
    @Suppress("DEPRECATION")
    private fun recoverScenes() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
            .putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
        if (android.os.Build.VERSION.SDK_INT >= 26) {
            i.putExtra(
                android.provider.DocumentsContract.EXTRA_INITIAL_URI,
                android.net.Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2FAirMix")
            )
        }
        try { startActivityForResult(i, REQ_RECOVER) } catch (_: Exception) {}
    }

    /** Ya hay una copia con ese nombre en Descargas/AirMix (para no duplicarla). */
    private fun inDownloads(name: String): Boolean {
        if (android.os.Build.VERSION.SDK_INT < 29) {
            @Suppress("DEPRECATION")
            val dir = java.io.File(android.os.Environment.getExternalStoragePublicDirectory(android.os.Environment.DIRECTORY_DOWNLOADS), "AirMix")
            return java.io.File(dir, safeName(name) + ".scn").exists()
        }
        return try {
            contentResolver.query(
                android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                arrayOf(android.provider.MediaStore.MediaColumns._ID),
                "${android.provider.MediaStore.MediaColumns.DISPLAY_NAME}=? AND ${android.provider.MediaStore.MediaColumns.RELATIVE_PATH} LIKE ?",
                arrayOf(safeName(name) + ".scn", "%AirMix%"), null
            )?.use { it.count > 0 } ?: false
        } catch (_: Exception) { false }
    }

    @Suppress("DEPRECATION")
    private fun openDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try { startActivityForResult(i, REQ_OPEN) } catch (_: Exception) {}
    }

    private fun displayName(uri: android.net.Uri): String {
        try {
            contentResolver.query(uri, arrayOf(android.provider.OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) return c.getString(0) ?: ""
            }
        } catch (_: Exception) {}
        return (uri.lastPathSegment ?: "").substringAfterLast('/').substringAfterLast(':')
    }

    @Deprecated("Activity API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        hideBars()
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            if (requestCode == REQ_RECOVER) {
                val uris = ArrayList<android.net.Uri>()
                val clip = data.clipData
                if (clip != null) for (k in 0 until clip.itemCount) uris.add(clip.getItemAt(k).uri) else uris.add(uri)
                var n = 0
                for (u in uris) {
                    val text = contentResolver.openInputStream(u)?.use { it.readBytes().toString(Charsets.ISO_8859_1) } ?: continue
                    val sc = Scenes.parse(text, displayName(u).removeSuffix(".scn"))
                    if (sc.lines.isEmpty()) continue
                    java.io.File(scenesDir(), safeName(displayName(u).removeSuffix(".scn").ifEmpty { sc.name }) + ".scn").writeText(text, Charsets.ISO_8859_1)
                    n++
                }
                if (tab == Tab.SCENES) render()
                Toast.makeText(this, if (n > 0) "$n escenas recuperadas" else "No se encontraron escenas .scn", Toast.LENGTH_LONG).show()
            } else if (requestCode == REQ_SETLIST) {
                val bytes = contentResolver.openInputStream(uri)?.use { it.readBytes() } ?: return
                onSetlistFile(bytes, displayName(uri))
            } else if (requestCode == REQ_SAVE) {
                val text = pendingExport ?: return
                contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.ISO_8859_1)) }
                pendingExport = null
                Toast.makeText(this, "Escena exportada", Toast.LENGTH_SHORT).show()
            } else if (requestCode == REQ_OPEN) {
                val text = contentResolver.openInputStream(uri)?.use { it.readBytes().toString(Charsets.ISO_8859_1) } ?: return
                var display = uri.lastPathSegment ?: "escena"
                display = display.substringAfterLast('/').substringAfterLast(':').removeSuffix(".scn")
                val scene = Scenes.parse(text, display)
                if (scene.lines.isEmpty()) {
                    Toast.makeText(this, "Ese archivo no parece una escena .scn", Toast.LENGTH_LONG).show()
                    return
                }
                java.io.File(scenesDir(), safeName(scene.name) + ".scn").writeText(text, Charsets.ISO_8859_1)
                // Copia de respaldo en Descargas/AirMix, que sobrevive aunque se desinstale la app.
                if (!inDownloads(scene.name)) saveToDownloads(scene.name, text)
                if (tab == Tab.SCENES) render()
                confirmLoad(scene)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo leer/escribir el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
