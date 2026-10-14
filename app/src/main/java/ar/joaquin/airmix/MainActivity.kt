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
import android.widget.TextView
import android.widget.Toast

class MainActivity : Activity() {
    enum class Tab(val label: String) {
        HOME("HOME"), EFFECTS("EFFECTS"), METERS("METERS"), RECORDER("RECORDER"), ROUTING("ROUTING")
    }

    enum class Layer { CH1, CH2, AUX, RTN, BUS, DCA }

    private var tab = Tab.HOME
    private var layer = Layer.CH1
    private var sends = SendTarget.MAIN
    private var detail: Strip? = null
    private var section = Section.CONFIG

    private lateinit var content: FrameLayout
    private lateinit var nav: LinearLayout
    private lateinit var modelLabel: TextView
    private lateinit var fwLabel: TextView
    private lateinit var wifi: WifiIcon
    private val tabBtns = HashMap<Tab, Btn>()
    private val layerBtns = HashMap<Layer, Btn>()

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
        if (App.client == null) App.client = MixerClient(applicationContext).also { it.start() }
        setContentView(buildRoot())
        render()
        val ip = prefs().getString("ip", null)
        if (ip != null) App.client?.connect(ip) else handler.postDelayed({ showConnect() }, 400)
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
            wifi.ok = isConn
            if (isConn) {
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
        val changed = App.applyModel(model)
        modelLabel.text = model
        fwLabel.text = fw
        if (changed) render()
    }

    // ------------------------------------------------------------------ layout

    private fun buildRoot(): View {
        val root = col()
        root.setBackgroundColor(C.BG)

        val top = row()
        top.setBackgroundColor(C.TOP)
        val logo = col()
        logo.gravity = Gravity.CENTER
        modelLabel = label(App.model, 26f)
        fwLabel = label("", 12f, C.TEXT_DIM)
        logo.addView(modelLabel)
        logo.addView(fwLabel)
        top.addView(logo, lp(dp(110), MATCH))
        for (t in Tab.values()) {
            val b = Btn(this, t.label, BtnStyle.TAB, 15f)
            b.setOnClickListener {
                tab = t
                render()
            }
            tabBtns[t] = b
            top.addView(b, lp(0, dp(46), 1f).m(dp(8), 0, dp(8), 0))
        }
        wifi = WifiIcon(this)
        wifi.setOnClickListener { showConnect() }
        top.addView(wifi, lp(dp(64), MATCH))
        root.addView(top, lp(MATCH, dp(64)))

        val body = row()
        body.gravity = Gravity.TOP
        nav = col()
        nav.setBackgroundColor(C.NAV_BG)
        for (l in Layer.values()) {
            val b = Btn(this, "", BtnStyle.NAV, 15f)
            b.setOnClickListener {
                layer = l
                detail = null
                render()
            }
            layerBtns[l] = b
            nav.addView(b, lp(MATCH, dp(54)).m(dp(10), dp(9), dp(10), dp(9)))
        }
        nav.addView(View(this), lp(MATCH, 0, 1f))
        val clr = Btn(this, "CLR SOLO", BtnStyle.NAV, 14f)
        clr.setOnClickListener { for (i in 1..36) App.store.set("/-stat/solosw/${Fmt.two(i)}", 0) }
        nav.addView(clr, lp(MATCH, dp(48)).m(dp(10), dp(8), dp(10), dp(16)))
        body.addView(nav, lp(dp(104), MATCH))

        content = FrameLayout(this)
        body.addView(content, lp(0, MATCH, 1f))
        root.addView(body, lp(MATCH, 0, 1f))
        return root
    }

    private fun render() {
        content.removeAllViews()
        if (layer == Layer.CH2 && App.channels <= 8) layer = Layer.CH1
        nav.visibility = if (tab == Tab.HOME) View.VISIBLE else View.GONE
        for ((t, b) in tabBtns) b.active = t == tab
        for ((l, b) in layerBtns) {
            b.active = l == layer
            b.text = when (l) {
                Layer.CH1 -> "CH 1-8"
                Layer.CH2 -> "CH 9-${App.channels}"
                Layer.AUX -> "AUX"
                Layer.RTN -> "FX RETURN"
                Layer.BUS -> "BUS"
                Layer.DCA -> "DCA"
            }
            b.visibility = if (l == Layer.CH2 && App.channels <= 8) View.GONE else View.VISIBLE
        }
        val v: View = when (tab) {
            Tab.HOME -> {
                val d = detail
                if (d != null) buildDetail(d) else buildHome()
            }
            Tab.EFFECTS -> Panels.effects(this)
            Tab.METERS -> AllMetersView(this).also { it.setBackgroundColor(C.BG) }
            Tab.RECORDER -> Panels.placeholder(this, "RECORDER")
            Tab.ROUTING -> Panels.placeholder(this, "ROUTING")
        }
        content.addView(v, FrameLayout.LayoutParams(MATCH, MATCH))
    }

    private fun stripsFor(l: Layer): List<Strip> = when (l) {
        Layer.CH1 -> (1..minOf(8, App.channels)).map { Strip(Kind.CH, it) }
        Layer.CH2 -> (9..App.channels).map { Strip(Kind.CH, it) }
        Layer.AUX -> listOf(Strip(Kind.AUX))
        Layer.RTN -> stripsOfKind(Kind.RTN)
        Layer.BUS -> stripsOfKind(Kind.BUS)
        Layer.DCA -> stripsOfKind(Kind.DCA)
    }

    private fun openDetail(s: Strip) {
        detail = s
        render()
    }

    // ------------------------------------------------------------------ HOME

    private fun buildHome(): View {
        val root = col()
        root.setBackgroundColor(C.BG)
        val sendsMode = sends != SendTarget.MAIN

        val header = row()
        header.setBackgroundColor(if (sendsMode) C.HEADER_SENDS else C.HEADER)
        header.addView(label(if (sendsMode) sends.title else "", 38f, Color.BLACK), lp(0, MATCH, 1f))
        val sel = Btn(this, "SENDS TO  ⋮\n${sends.label}", BtnStyle.NAV, 16f)
        sel.maxLines = 2
        sel.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        sel.setPadding(dp(12), 0, dp(12), 0)
        sel.setTextColor(Color.WHITE)
        sel.setOnClickListener { showSendsPopup(sel) }
        header.addView(sel, lp(dp(180), MATCH).m(dp(6), dp(6), dp(6), dp(6)))
        root.addView(header, lp(MATCH, dp(72)))

        val strips = row()
        strips.gravity = Gravity.TOP
        val list = stripsFor(layer)
        for (i in 0 until 8) {
            val bg = if (i % 2 == 0) C.STRIP_A else C.STRIP_B
            val v = if (i < list.size) stripView(list[i], sends, bg) { openDetail(it) }
            else View(this).also { it.setBackgroundColor(C.BG) }
            strips.addView(v, lp(0, MATCH, 1f))
        }
        val master = when {
            sends.bus > 0 -> Strip(Kind.BUS, sends.bus)
            sends.fx > 0 -> Strip(Kind.FXSEND, sends.fx)
            else -> Strip(Kind.LR)
        }
        strips.addView(stripView(master, SendTarget.MAIN, C.STRIP_B) { openDetail(it) }, lp(0, MATCH, 1f))
        root.addView(strips, lp(MATCH, 0, 1f))
        return root
    }

    private fun showSendsPopup(anchor: View) {
        val box = col()
        box.setBackgroundColor(Color.BLACK)
        box.setPadding(dp(2), dp(2), dp(2), dp(2))
        val pw = PopupWindow(box, dp(380), WRAP, true)
        pw.setBackgroundDrawable(ColorDrawable(Color.BLACK))
        val cell = { t: SendTarget ->
            val b = Btn(this, t.title, BtnStyle.NAV, 22f)
            b.active = t == sends
            b.setOnClickListener {
                sends = t
                pw.dismiss()
                render()
            }
            b
        }
        box.addView(cell(SendTarget.MAIN), lp(MATCH, dp(58)).m(dp(2), dp(2), dp(2), dp(2)))
        val targets = (1..App.buses).map { SendTarget(bus = it) } + (1..4).map { SendTarget(fx = it) }
        for (pair in targets.chunked(2)) {
            val r = row()
            for (t in pair) r.addView(cell(t), lp(0, dp(58), 1f).m(dp(2), dp(2), dp(2), dp(2)))
            box.addView(r)
        }
        pw.showAsDropDown(anchor, 0, 0, Gravity.END)
    }

    // ------------------------------------------------------------------ DETALLE

    private fun buildDetail(s: Strip): View {
        val secs = sectionsFor(s)
        if (section !in secs) section = secs.first()

        val root = row()
        root.gravity = Gravity.TOP
        root.setBackgroundColor(C.BG)
        root.addView(stripView(s, SendTarget.MAIN, C.STRIP_A) {}, lp(0, MATCH, 1f))

        val right = col()
        val bar = row()
        bar.setBackgroundColor(Color.BLACK)
        bar.addView(View(this), lp(0, 1, 1f))
        val prev = label("‹", 46f)
        prev.setOnClickListener { step(s, -1) }
        bar.addView(prev, lp(dp(70), MATCH))
        bar.addView(label("${s.title.uppercase()} - ${section.label}", 30f), lp(WRAP, MATCH))
        val next = label("›", 46f)
        next.setOnClickListener { step(s, 1) }
        bar.addView(next, lp(dp(70), MATCH))
        bar.addView(View(this), lp(0, 1, 1f))
        val done = label("DONE  ✓", 28f)
        done.setOnClickListener {
            detail = null
            render()
        }
        bar.addView(done, lp(dp(170), MATCH))
        right.addView(bar, lp(MATCH, dp(64)))

        val body = row()
        body.gravity = Gravity.TOP
        val panel = FrameLayout(this)
        panel.setBackgroundColor(C.PANEL)
        panel.addView(Panels.build(this, s, section), FrameLayout.LayoutParams(MATCH, MATCH))
        body.addView(panel, lp(0, MATCH, 1f))

        val side = col()
        side.setBackgroundColor(Color.BLACK)
        side.setPadding(dp(8), dp(8), dp(8), dp(8))
        for (sec in secs) {
            val b = Btn(this, sec.label, BtnStyle.LIGHT, 20f)
            b.maxLines = 2
            b.active = sec == section
            val onAddr = when (sec) {
                Section.GATE -> "${s.prefix}/gate/on"
                Section.DYN -> "${s.prefix}/dyn/on"
                Section.EQ -> "${s.prefix}/eq/on"
                else -> null
            }
            if (onAddr != null) b.bind(onAddr) { v -> b.text = sec.label + "\n" + (if (v.asI() != 0) "ON" else "OFF") }
            b.setOnClickListener {
                section = sec
                render()
            }
            side.addView(b, lp(MATCH, dp(96)).m(0, dp(6), 0, dp(6)))
        }
        body.addView(side, lp(dp(132), MATCH))
        right.addView(body, lp(MATCH, 0, 1f))
        root.addView(right, lp(0, MATCH, 8f))
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
        box.addView(label("O escribí la IP de la consola:", 16f, C.TEXT_DIM, Gravity.START), lp(MATCH, dp(30)).m(t = dp(16)))
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
            .setPositiveButton("Conectar") { _, _ -> connectTo(ip.text.toString()) }
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
        val b = Btn(this, "${d.model}  ·  ${d.name}  ·  ${d.ip}", BtnStyle.NAV, 18f)
        b.setTextColor(Color.WHITE)
        b.setOnClickListener {
            connectTo(d.ip)
            dialog?.dismiss()
        }
        list.addView(b, lp(MATCH, dp(54)).m(t = dp(6)))
    }

    private fun connectTo(ip: String) {
        val c = App.client ?: return
        if (c.connect(ip)) {
            prefs().edit().putString("ip", ip.trim()).apply()
            App.store.resetValues()
            connected = false
            wifi.ok = false
            Toast.makeText(this, "Conectando a ${ip.trim()}…", Toast.LENGTH_SHORT).show()
        } else {
            Toast.makeText(this, "IP inválida", Toast.LENGTH_SHORT).show()
        }
    }
}
