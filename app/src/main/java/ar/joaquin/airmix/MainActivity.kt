package ar.joaquin.airmix

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.text.InputType
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
    enum class Tab(val label: String) { MIX("MEZCLA"), FX("EFECTOS"), METERS("MEDIDORES") }

    private var tab = Tab.MIX
    private var layers: List<Layer> = emptyList()
    private var layerIdx = 0
    private var sends = SendTarget.MAIN
    private var detail: Strip? = null
    private var section = Section.CONFIG

    private lateinit var content: FrameLayout
    private lateinit var rail: LinearLayout
    private lateinit var modelLabel: TextView
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
    }

    private fun onXinfo(name: String, model: String, fw: String) {
        App.mixerName = name
        App.firmware = fw
        prefs().edit().putString("model", model).apply()
        val changed = App.applyModel(model)
        modelLabel.text = "$model  ·  $fw"
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
        modelLabel = label(App.console.model, 13f, C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL)
        top.addView(modelLabel, lp(dp(150), MATCH).m(l = dp(14)))
        for (t in Tab.values()) {
            val b = Btn(this, t.label, BtnStyle.TAB, 16f)
            b.setOnClickListener {
                tab = t
                render()
            }
            tabBtns[t] = b
            top.addView(b, lp(dp(130), dp(40)).m(r = dp(8)))
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
        content = FrameLayout(this)
        body.addView(content, lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    /** Riel izquierdo: capas (con scroll si son muchas, ej. X32) + MUTE FX + CLR SOLO siempre a mano. */
    private fun rebuildRail() {
        layers = buildLayers()
        if (layerIdx >= layers.size) layerIdx = 0
        rail.removeAllViews()
        layerBtns.clear()

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
            header.addView(
                label("SENDS ON FADER  →  ${sends.label}", 20f, C.INK, Gravity.START or Gravity.CENTER_VERTICAL),
                lp(0, MATCH, 1f)
            )
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
        val pw = PopupWindow(box, dp(400), WRAP, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        val cell = { t: SendTarget, label: String ->
            val b = Btn(this, label, BtnStyle.LIGHT, 18f)
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
}
