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
    private val tabBtns = HashMap<Tab, Btn>()
    private val layerBtns = ArrayList<Btn>()

    private val handler = Handler(Looper.getMainLooper())
    private var connected = false
    private var ticking = false
    private val tick = object : Runnable {
        override fun run() {
            onTick()
            if (ticking) handler.postDelayed(this, 33)
        }
    }

    private var dialog: AlertDialog? = null
    private var dialogList: LinearLayout? = null
    private val seen = HashSet<String>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        prefs().getString("model", null)?.let { App.applyModel(it) }
        ShowTimer.load(prefs())
        if (App.client == null) App.client = MixerClient(applicationContext).also { it.start() }
        App.client?.x32 = App.console.isX32
        setContentView(buildRoot())
        rebuildRail()
        render()
        val ip = prefs().getString("ip", null)
        if (ip != null) App.client?.connect(ip, prefs().getInt("port", 0))
        else handler.postDelayed({ showConnect() }, 400)
    }

    override fun onResume() {
        super.onResume()
        hideBars()
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
                for (v in App.meterViews) v.invalidate()
            }
        }
        if (connected) for (v in App.meterViews) v.invalidate()
        updateTimer()
    }

    private fun updateTimer() {
        val ms = ShowTimer.elapsed()
        val t = ShowTimer.format(ms)
        if (t == lastTimerText) return
        lastTimerText = t
        timerLabel.text = t
        timerLabel.setTextColor(
            when {
                ShowTimer.isRunning() -> C.GREEN
                ms > 0 -> C.SOLO
                else -> C.TEXT_DIM
            }
        )
    }

    private fun onXinfo(name: String, model: String, fw: String) {
        App.mixerName = name
        App.firmware = fw
        prefs().edit().putString("model", model).apply()
        val changed = App.applyModel(model)
        updateConn()
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
        top.setPadding(dp(16), 0, dp(12), 0)
        val logo = label("AIRMIX", 22f, C.ACCENT, Gravity.START or Gravity.CENTER_VERTICAL)
        logo.letterSpacing = 0.15f
        top.addView(logo, lp(WRAP, MATCH))
        top.addView(View(this), lp(dp(40), 1))
        for (t in Tab.values()) {
            val b = Btn(this, t.label, BtnStyle.TAB, 16f)
            b.setOnClickListener {
                tab = t
                render()
            }
            tabBtns[t] = b
            top.addView(b, lp(dp(124), dp(40)).m(r = dp(6)))
        }
        top.addView(View(this), lp(0, 1, 1f))
        conn = Btn(this, "", BtnStyle.OUTLINE, 14f)
        conn.setOnClickListener { showConnect() }
        top.addView(conn, lp(dp(240), dp(40)))
        root.addView(top, lp(MATCH, dp(60)))
        updateConn()

        val body = row()
        body.gravity = Gravity.TOP
        rail = col()
        rail.setBackgroundColor(C.NAV_BG)
        body.addView(rail, lp(dp(116), MATCH))
        val host = FrameLayout(this)
        content = FrameLayout(this)
        host.addView(content, FrameLayout.LayoutParams(MATCH, MATCH))
        notesPanel = buildNotes()
        notesPanel.visibility = View.GONE
        host.addView(notesPanel, FrameLayout.LayoutParams(dp(420), MATCH, Gravity.END))
        body.addView(host, lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    /** Riel izquierdo: capas (con scroll si son muchas, ej. X32) + MUTE FX + CLR SOLO siempre a mano. */
    private fun rebuildRail() {
        layers = buildLayers()
        if (layerIdx >= layers.size) layerIdx = 0
        rail.removeAllViews()
        layerBtns.clear()

        val timerBox = col()
        timerBox.gravity = Gravity.CENTER
        timerBox.background = rounded(C.SURFACE, 12)
        timerBox.addView(label("SHOW", 11f, C.TEXT_DIM), lp(MATCH, dp(16)))
        timerLabel = label("00:00", 24f, C.TEXT_DIM)
        lastTimerText = ""
        timerBox.addView(timerLabel, lp(MATCH, dp(30)))
        timerBox.addView(label("tocá: ▶ / ❚❚", 10f, C.TICK), lp(MATCH, dp(14)))
        timerBox.isClickable = true
        timerBox.setOnClickListener {
            ShowTimer.toggle(prefs())
            lastTimerText = ""
            updateTimer()
        }
        timerBox.setOnLongClickListener {
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
            true
        }
        rail.addView(timerBox, lp(MATCH, dp(76)).m(dp(10), dp(10), dp(10), dp(2)))

        val list = col()
        list.setPadding(dp(10), dp(8), dp(10), dp(8))
        layers.forEachIndexed { i, l ->
            val b = Btn(this, l.label, BtnStyle.NAV, 15f)
            b.setOnClickListener {
                layerIdx = i
                tab = Tab.MIX
                detail = null
                render()
            }
            layerBtns.add(b)
            list.addView(b, lp(MATCH, dp(50)).m(b = dp(6)))
        }
        val sv = ScrollView(this)
        sv.isVerticalScrollBarEnabled = false
        sv.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        rail.addView(sv, lp(MATCH, 0, 1f))

        val notesBtn = Btn(this, "✎ NOTAS", BtnStyle.OUTLINE, 15f)
        notesBtn.setOnClickListener { toggleNotes() }
        rail.addView(notesBtn, lp(MATCH, dp(44)).m(dp(10), dp(8), dp(10), 0))

        val fx = Btn(this, "MUTE\nFX", BtnStyle.MUTE, 17f)
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
        rail.addView(fx, lp(MATCH, dp(78)).m(dp(10), dp(8), dp(10), dp(8)))

        val clr = Btn(this, "CLR SOLO", BtnStyle.OUTLINE, 14f)
        clr.setOnClickListener {
            val n = if (App.console.isX32) 80 else 36
            for (i in 1..n) App.store.set("/-stat/solosw/${Fmt.two(i)}", 0)
        }
        rail.addView(clr, lp(MATCH, dp(44)).m(dp(10), 0, dp(10), dp(14)))
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
            Tab.FX -> Panels.effects(this)
            Tab.METERS -> AllMetersView(this).also { it.setBackgroundColor(C.BG) }
            Tab.SCENES -> buildScenes()
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
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
            header.background = rounded(C.SENDS, 0)
            val title = label("SENDS ON FADER  →  ${sends.display("")}", 20f, C.INK, Gravity.START or Gravity.CENTER_VERTICAL)
            val target = sends
            target.nameAddr?.let { a -> title.bind(a) { v -> title.text = "SENDS ON FADER  →  ${target.display(v.toString())}" } }
            header.addView(title, lp(0, MATCH, 1f))
            val change = Btn(this, "CAMBIAR ▾", BtnStyle.LIGHT, 15f)
            change.setOnClickListener { showSendsPopup(change) }
            header.addView(change, lp(dp(130), dp(40)).m(r = dp(8)))
            val exit = Btn(this, "SALIR ✕", BtnStyle.LIGHT, 15f)
            exit.setOnClickListener {
                sends = SendTarget.MAIN
                render()
            }
            header.addView(exit, lp(dp(110), dp(40)))
        } else {
            header.setBackgroundColor(C.PANEL)
            header.addView(
                label(layer?.label ?: "", 20f, C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL),
                lp(0, MATCH, 1f)
            )
            val sel = Btn(this, "SENDS ON FADER ▾", BtnStyle.OUTLINE, 15f)
            sel.setOnClickListener { showSendsPopup(sel) }
            header.addView(sel, lp(dp(190), dp(40)))
        }
        root.addView(header, lp(MATCH, dp(56)))

        val strips = row()
        strips.gravity = Gravity.TOP
        strips.setPadding(dp(4), dp(4), dp(4), dp(4))
        val list = layer?.strips ?: emptyList()
        for (i in 0 until 8) {
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
            t.nameAddr?.let { a -> b.bind(a) { v -> b.text = t.display(v.toString()) } }
            b.activeColor = if (t.fx > 0) C.ACCENT else C.SENDS
            b.active = t == sends
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
        val prev = Btn(this, "‹", BtnStyle.OUTLINE, 24f)
        prev.setOnClickListener { step(s, -1) }
        bar.addView(prev, lp(dp(54), dp(42)).m(r = dp(6)))
        val next = Btn(this, "›", BtnStyle.OUTLINE, 24f)
        next.setOnClickListener { step(s, 1) }
        bar.addView(next, lp(dp(54), dp(42)))
        root.addView(bar, lp(MATCH, dp(60)))

        val body = row()
        body.gravity = Gravity.TOP
        body.setPadding(dp(4), dp(4), dp(4), dp(4))
        body.addView(stripView(s, SendTarget.MAIN) {}, lp(0, MATCH, 1f).m(r = dp(4)))
        val panel = FrameLayout(this)
        panel.background = rounded(C.PANEL, 14)
        panel.addView(Panels.build(this, s, section), FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(panel, lp(0, MATCH, 8f))
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    private fun step(s: Strip, dir: Int) {
        val list = stripsOfKind(s.kind)
        val i = list.indexOf(s)
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

    private fun buildNotes(): View {
        val panel = col()
        panel.background = rounded(C.SURFACE, 16, C.LINE)
        panel.setPadding(dp(16), dp(12), dp(16), dp(16))
        panel.isClickable = true
        panel.elevation = dp(12).toFloat()

        val head = row()
        head.addView(label("NOTAS", 18f, C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL), lp(0, dp(44), 1f))
        val mark = Btn(this, "+ MARCA", BtnStyle.LIGHT, 15f)
        mark.setOnClickListener { insertMark() }
        head.addView(mark, lp(dp(100), dp(40)).m(r = dp(8)))
        val clear = Btn(this, "BORRAR", BtnStyle.OUTLINE, 14f)
        clear.setOnClickListener {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("¿Borrar todas las notas?")
                .setPositiveButton("Borrar") { _, _ -> notesEdit.setText("") }
                .setNegativeButton("Cancelar", null)
                .setOnDismissListener { hideBars() }
                .show()
        }
        head.addView(clear, lp(dp(84), dp(40)).m(r = dp(8)))
        val close = Btn(this, "✕", BtnStyle.OUTLINE, 16f)
        close.setOnClickListener { toggleNotes() }
        head.addView(close, lp(dp(44), dp(40)))
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
        panel.addView(notesEdit, lp(MATCH, 0, 1f).m(t = dp(8)))
        val wrap = FrameLayout(this)
        wrap.setPadding(dp(8), dp(8), dp(8), dp(8))
        wrap.addView(panel, FrameLayout.LayoutParams(MATCH, MATCH))
        return wrap
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
            label("Se guarda en la tablet y te ofrece exportar el .scn (Drive, Descargas…). Se abre en X-Air Edit / X32-Edit.", 13f, C.TEXT_DIM, Gravity.START).also { it.maxLines = 3 },
            lp(MATCH, WRAP).m(t = dp(10))
        )
        left.addView(saveCard, lp(MATCH, WRAP))

        val statusCard = card()
        statusCard.addView(sectionTitle("ESTADO"), lp(MATCH, dp(24)))
        val status = label("Listo.", 15f, C.TEXT, Gravity.START or Gravity.TOP)
        status.maxLines = 8
        statusCard.addView(status, lp(MATCH, WRAP).m(t = dp(6)))
        left.addView(statusCard, lp(MATCH, WRAP).m(t = dp(14)))
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
            share.setOnClickListener { exportDocument(file.name.removeSuffix(".scn"), file.readText(Charsets.ISO_8859_1)) }
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

    private fun onSceneRead(name: String, text: String) {
        val f = java.io.File(scenesDir(), safeName(name) + ".scn")
        f.writeText(text, Charsets.ISO_8859_1)
        if (tab == Tab.SCENES) render()
        Scenes.onStatus?.invoke("✓ Guardada en la tablet como \"${f.name}\".")
        exportDocument(name, text)
    }

    private fun confirmLoad(scene: Scenes.Scene) {
        if (Scenes.busy()) return
        AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setTitle("Cargar \"${scene.name}\"")
            .setMessage("Esto reemplaza la mezcla actual de la consola (${scene.lines.size} bloques).\n\nAntes de cargar se verifica el formato con la consola; lo que no se pueda verificar queda como está.")
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

    @Suppress("DEPRECATION")
    private fun openDocument() {
        val i = Intent(Intent.ACTION_OPEN_DOCUMENT)
            .addCategory(Intent.CATEGORY_OPENABLE)
            .setType("*/*")
        try { startActivityForResult(i, REQ_OPEN) } catch (_: Exception) {}
    }

    @Deprecated("Activity API")
    @Suppress("DEPRECATION")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        hideBars()
        val uri = data?.data
        if (resultCode != RESULT_OK || uri == null) return
        try {
            if (requestCode == REQ_SAVE) {
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
                if (tab == Tab.SCENES) render()
                confirmLoad(scene)
            }
        } catch (e: Exception) {
            Toast.makeText(this, "No se pudo leer/escribir el archivo: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
