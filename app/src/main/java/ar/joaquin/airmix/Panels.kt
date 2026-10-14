package ar.joaquin.airmix

import android.content.Context
import android.graphics.Color
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu

/** Tira de canal (igual en HOME y en el detalle). */
fun Context.stripView(s: Strip, target: SendTarget, bg: Int, onOpen: (Strip) -> Unit): View {
    val col = col()
    col.setBackgroundColor(bg)

    val title = label(s.title, 22f)
    title.setBackgroundColor(Color.BLACK)
    title.setOnClickListener { onOpen(s) }
    col.addView(title, lp(MATCH, dp(58)))

    val name = label(s.title.uppercase(), 14f, Color.BLACK)
    name.setBackgroundColor(Color.WHITE)
    name.setOnClickListener { onOpen(s) }
    name.bind(s.nameAddr) { v -> name.text = v.toString().ifEmpty { s.title.uppercase() } }
    name.bind(s.colorAddr) { v -> applyScribble(name, v.asI()) }
    col.addView(name, lp(MATCH, dp(30)))

    val panBox = FrameLayout(this)
    panBox.setBackgroundColor(Color.BLACK)
    s.panAddr?.let {
        val lpp = FrameLayout.LayoutParams(MATCH, dp(34), Gravity.CENTER)
        lpp.setMargins(dp(12), 0, dp(12), 0)
        panBox.addView(PanView(this, it), lpp)
    }
    col.addView(panBox, lp(MATCH, dp(54)))

    val ms = row()
    ms.gravity = Gravity.CENTER
    ms.addView(toggle("M", s.onAddr, BtnStyle.SMALL, invert = true, color = C.MUTE, sp = 18f), lp(dp(40), dp(40)).m(r = dp(8)))
    ms.addView(toggle("S", s.soloAddr, BtnStyle.SMALL, color = C.ACCENT, sp = 18f), lp(dp(40), dp(40)))
    col.addView(ms, lp(MATCH, dp(58)))

    val faderRow = row()
    val sendAddr = s.sendAddr(target)
    val faderAddr = sendAddr ?: s.faderAddr
    val fader = FaderView(this)
    fader.trackColor = if (sendAddr != null) C.CYAN else Color.WHITE
    val dbText = label("", 13f, C.TEXT_DIM)
    fader.bind(faderAddr) { v ->
        val f = v.asF()
        fader.setValue(f)
        dbText.text = Fmt.faderDb(f)
    }
    fader.onUserChange = { f ->
        App.store.set(faderAddr, f)
        dbText.text = Fmt.faderDb(f)
    }
    faderRow.addView(fader, lp(0, MATCH, 1f))
    if (s.meterL >= 0) {
        faderRow.addView(
            MeterView(this, s.meterL, s.meterR),
            lp(dp(if (s.meterR >= 0) 12 else 8), MATCH).m(t = dp(24), r = dp(10), b = dp(24))
        )
    }
    col.addView(faderRow, lp(MATCH, 0, 1f))
    col.addView(dbText, lp(MATCH, dp(20)))
    col.addView(
        toggle("MUTE", s.onAddr, BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 20f),
        lp(MATCH, dp(46)).m(dp(10), dp(4), dp(10), dp(10))
    )
    return col
}

object Panels {
    private val GATE_MODES = listOf("EXP 2", "EXP 3", "EXP 4", "GATE", "DUCK")
    private val DYN_MODES = listOf("COMP", "EXP")
    private val EQ_TYPES = listOf("LCut", "LShv", "PEQ", "VEQ", "HShv", "HCut")
    private val RATIOS = listOf("1.1", "1.3", "1.5", "2.0", "2.5", "3.0", "4.0", "5.0", "7.0", "10", "20", "100")

    fun build(ctx: Context, s: Strip, sec: Section): View = when (sec) {
        Section.CONFIG -> ctx.scroll(config(ctx, s))
        Section.GATE -> ctx.scroll(gate(ctx, s.prefix))
        Section.DYN -> ctx.scroll(dyn(ctx, s.prefix))
        Section.EQ -> eq(ctx, s.prefix, if (s.kind == Kind.CH) 4 else 6, s.kind == Kind.CH)
        Section.SENDS -> sends(ctx, s)
    }

    private fun Context.knob(spec: Spec): KnobView {
        val k = KnobView(this, spec)
        k.layoutParams = lp(dp(200), dp(84))
        return k
    }

    private fun Context.knobRow(vararg specs: Spec): LinearLayout {
        val r = row()
        for (sp in specs) r.addView(knob(sp))
        return r
    }

    private fun Context.panelRoot(): LinearLayout {
        val root = col()
        root.setPadding(dp(28), dp(18), dp(28), dp(18))
        return root
    }

    // ------------------------------------------------------------------ CONFIG

    private fun config(ctx: Context, s: Strip): View = with(ctx) {
        val root = panelRoot()
        val r1 = row()

        val colorBtn = Btn(ctx, "", BtnStyle.LIGHT)
        colorBtn.bind(s.colorAddr) { v -> applyScribble(colorBtn, v.asI()) }
        colorBtn.setOnClickListener {
            val pm = PopupMenu(ctx, colorBtn)
            COLOR_NAMES.forEachIndexed { i, n -> pm.menu.add(0, i, i, n) }
            pm.setOnMenuItemClickListener { item ->
                App.store.set(s.colorAddr, item.itemId)
                true
            }
            pm.show()
        }
        r1.addView(colorBtn, lp(dp(64), dp(56)))

        val name = EditText(ctx)
        name.setTextColor(Color.WHITE)
        name.setBackgroundColor(Color.BLACK)
        name.typeface = FONT
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        name.setSingleLine()
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        name.imeOptions = EditorInfo.IME_ACTION_DONE
        name.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(12))
        name.setPadding(dp(16), 0, dp(16), 0)
        name.bind(s.nameAddr) { v -> if (!name.hasFocus()) name.setText(v.toString()) }
        name.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) {
                App.store.set(s.nameAddr, name.text.toString())
                name.clearFocus()
                val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
                imm.hideSoftInputFromWindow(name.windowToken, 0)
                true
            } else false
        }
        r1.addView(name, lp(dp(260), dp(56)).m(l = dp(20)))
        r1.addView(View(ctx), lp(0, 1, 1f))
        r1.addView(toggle("MUTE", s.onAddr, BtnStyle.LIGHT, invert = true, color = C.MUTE, sp = 20f), lp(dp(130), dp(56)))
        r1.addView(toggle("SOLO", s.soloAddr, BtnStyle.LIGHT, sp = 20f), lp(dp(130), dp(56)).m(l = dp(20)))
        root.addView(r1)

        if (s.kind == Kind.CH) {
            val p = s.prefix
            val n = s.idx
            val hasHeadamp = n <= App.headamps
            val ha = "/headamp/${Fmt.two(n)}"
            root.addView(divider())

            val src = label("", 18f)
            src.setBackgroundColor(Color.BLACK)
            src.bind("$p/config/insrc") { v -> src.text = "SOURCE   INP ${Fmt.two(v.asI() + 1)}" }
            root.addView(src, lp(dp(240), dp(48)))

            val r3 = row()
            if (hasHeadamp) r3.addView(knob(Spec("GAIN", "$ha/gain", def = 12f / 72f) { Fmt.db(Fmt.lin(-12f, 60f, it)) }))
            r3.addView(knob(Spec("TRIM", "$p/preamp/trim", def = 0.5f, bipolar = true) { Fmt.db(Fmt.lin(-18f, 18f, it)) }))
            r3.addView(knob(Spec("LOCUT", "$p/preamp/hpf", def = 0f) { Fmt.hz(Fmt.log(20f, 400f, it)) }))
            r3.addView(toggle("LO CUT", "$p/preamp/hpon", sp = 18f), lp(dp(120), dp(52)))
            root.addView(r3, lp(MATCH, WRAP).m(t = dp(12)))

            val r4 = row()
            if (hasHeadamp) r4.addView(toggle("48V", "$ha/phantom", color = C.MUTE, sp = 20f), lp(dp(110), dp(56)).m(r = dp(16)))
            r4.addView(toggle("PHASE", "$p/preamp/invert", sp = 20f), lp(dp(130), dp(56)).m(r = dp(16)))
            r4.addView(toggle("STEREO LR", "$p/mix/lr", sp = 20f), lp(dp(160), dp(56)))
            root.addView(r4, lp(MATCH, WRAP).m(t = dp(12)))

            root.addView(divider())
            val r5 = row()
            r5.addView(label("DCA", 18f), lp(dp(60), dp(48)))
            for (i in 0..3) r5.addView(bitToggle("${i + 1}", "$p/grp/dca", i), lp(dp(54), dp(48)).m(r = dp(8)))
            r5.addView(View(ctx), lp(dp(40), 1))
            r5.addView(label("MUTE GROUPS", 18f), lp(WRAP, dp(48)).m(r = dp(14)))
            for (i in 0..3) r5.addView(bitToggle("${i + 1}", "$p/grp/mute", i), lp(dp(54), dp(48)).m(r = dp(8)))
            root.addView(r5)
        }
        root
    }

    // ------------------------------------------------------------------ GATE

    private fun keySource(ctx: Context, addr: String): View = with(ctx) {
        val key = label("", 17f)
        key.maxLines = 2
        key.setBackgroundColor(Color.BLACK)
        key.bind(addr) { v ->
            val x = v.asI()
            key.text = "KEY SOURCE\n" + (if (x == 0) "SELF" else "#$x")
        }
        key.layoutParams = lp(dp(160), dp(64)).m(l = dp(24))
        key
    }

    private fun gate(ctx: Context, p: String): View = with(ctx) {
        val b = "$p/gate"
        val root = panelRoot()
        val top = row()
        top.addView(onOff("$b/on"), lp(dp(130), dp(54)))
        top.addView(selector("TYPE", "$b/mode", GATE_MODES), lp(dp(200), dp(54)).m(l = dp(24)))
        root.addView(top)
        root.addView(DynGraphView(ctx, p, true), lp(dp(240), dp(200)).m(t = dp(16)))
        root.addView(divider())
        root.addView(
            knobRow(
                Spec("THRESH", "$b/thr", def = 0f) { Fmt.db(Fmt.lin(-80f, 0f, it)) },
                Spec("RANGE", "$b/range", def = 1f) { Fmt.db(Fmt.lin(3f, 60f, it)) }
            )
        )
        root.addView(divider())
        root.addView(
            knobRow(
                Spec("ATTACK", "$b/attack", def = 0.2f) { Fmt.ms(Fmt.lin(0f, 120f, it)) },
                Spec("HOLD", "$b/hold", def = 0.5f) { Fmt.ms(Fmt.log(0.02f, 2000f, it)) },
                Spec("RELEASE", "$b/release", def = 0.5f) { Fmt.ms(Fmt.log(5f, 4000f, it)) }
            )
        )
        root.addView(divider())
        val r = row()
        r.addView(knob(Spec("FREQ", "$b/filter/f", def = 0.5f) { Fmt.hz(Fmt.log(20f, 20000f, it)) }))
        r.addView(toggle("FILTER", "$b/filter/on", sp = 18f), lp(dp(120), dp(56)))
        r.addView(keySource(ctx, "$b/keysrc"))
        root.addView(r)
        root
    }

    // ------------------------------------------------------------------ DYN

    private fun dyn(ctx: Context, p: String): View = with(ctx) {
        val b = "$p/dyn"
        val root = panelRoot()
        val top = row()
        top.addView(onOff("$b/on"), lp(dp(130), dp(54)))
        top.addView(selector("TYPE", "$b/mode", DYN_MODES), lp(dp(200), dp(54)).m(l = dp(24)))
        root.addView(top)

        val mid = row()
        mid.addView(DynGraphView(ctx, p, false), lp(dp(240), dp(200)))
        val opts = col()
        opts.gravity = Gravity.CENTER_HORIZONTAL
        opts.addView(label("ENVELOPE", 18f), lp(WRAP, dp(28)))
        opts.addView(choice("$b/auto", listOf("MANUAL", "AUTO")))
        opts.addView(choice("$b/env", listOf("LIN", "LOG")), lp(WRAP, WRAP).m(t = dp(10)))
        opts.addView(label("DETECTION", 18f), lp(WRAP, dp(28)).m(t = dp(14)))
        opts.addView(choice("$b/det", listOf("PEAK", "RMS")))
        mid.addView(opts, lp(WRAP, WRAP).m(l = dp(120)))
        root.addView(mid, lp(MATCH, WRAP).m(t = dp(16)))

        root.addView(divider())
        root.addView(
            knobRow(
                Spec("THRESH", "$b/thr", def = 1f) { Fmt.db(Fmt.lin(-60f, 0f, it)) },
                Spec("RATIO", "$b/ratio", steps = 12, def = 3f / 11f) { RATIOS[it.toInt().coerceIn(0, 11)] },
                Spec("KNEE", "$b/knee", def = 0f) { Fmt.num(Fmt.lin(0f, 5f, it)) },
                Spec("GAIN", "$b/mgain", def = 0f) { Fmt.db(Fmt.lin(0f, 24f, it)) }
            )
        )
        root.addView(divider())
        root.addView(
            knobRow(
                Spec("ATTACK", "$b/attack", def = 0.2f) { Fmt.ms(Fmt.lin(0f, 120f, it)) },
                Spec("HOLD", "$b/hold", def = 0.5f) { Fmt.ms(Fmt.log(0.02f, 2000f, it)) },
                Spec("RELEASE", "$b/release", def = 0.5f) { Fmt.ms(Fmt.log(5f, 4000f, it)) },
                Spec("MIX", "$b/mix", def = 1f) { Fmt.pct(Fmt.lin(0f, 100f, it)) }
            )
        )
        root.addView(divider())
        val r = row()
        r.addView(knob(Spec("FREQ", "$b/filter/f", def = 0.5f) { Fmt.hz(Fmt.log(20f, 20000f, it)) }))
        r.addView(toggle("FILTER", "$b/filter/on", sp = 18f), lp(dp(120), dp(56)))
        r.addView(keySource(ctx, "$b/keysrc"))
        root.addView(r)
        root
    }

    // ------------------------------------------------------------------ EQ

    private fun eq(ctx: Context, p: String, bands: Int, hpf: Boolean): View = with(ctx) {
        val root = col()
        root.setPadding(dp(24), dp(14), dp(24), dp(10))
        val top = row()
        top.addView(onOff("$p/eq/on"), lp(dp(130), dp(50)))
        root.addView(top)

        val graph = EqGraphView(ctx, p, bands, hpf)
        root.addView(graph, lp(MATCH, 0, 1f).m(t = dp(10)))

        val names = if (bands == 4) listOf("LOW", "LOW MID", "HI MID", "HIGH")
        else listOf("LOW", "LOW 2", "LOW MID", "HI MID", "HIGH 2", "HIGH")
        val bandBtns = HashMap<Int, Btn>()
        val bandRow = row()
        bandRow.gravity = Gravity.CENTER
        val bottom = row()
        bottom.setBackgroundColor(C.BG)
        bottom.setPadding(dp(16), 0, dp(16), 0)

        val select: (Int) -> Unit = { band ->
            graph.selected = band
            graph.invalidate()
            for ((k, btn) in bandBtns) btn.active = k == band
            bottom.removeAllViews()
            if (band == 0) {
                bottom.addView(toggle("LO CUT", "$p/preamp/hpon", sp = 18f), lp(dp(130), dp(52)))
                bottom.addView(knob(Spec("FREQ", "$p/preamp/hpf", def = 0f) { Fmt.hz(Fmt.log(20f, 400f, it)) }), lp(dp(220), dp(84)).m(l = dp(24)))
            } else {
                val e = "$p/eq/$band"
                bottom.addView(selector("TYPE", "$e/type", EQ_TYPES), lp(dp(150), dp(56)))
                bottom.addView(knob(Spec("FREQ", "$e/f", def = 0.5f) { Fmt.hz(Fmt.log(20f, 20000f, it)) }), lp(dp(210), dp(84)).m(l = dp(20)))
                bottom.addView(knob(Spec("GAIN", "$e/g", def = 0.5f, bipolar = true) { Fmt.db(Fmt.lin(-15f, 15f, it)) }))
                bottom.addView(knob(Spec("Q", "$e/q", def = 0.5f) { Fmt.num(Fmt.log(10f, 0.3f, it)) }))
            }
        }
        graph.onSelect = { select(it) }

        if (hpf) {
            val b = Btn(ctx, "LOCUT", BtnStyle.LIGHT, 18f)
            b.setOnClickListener { select(0) }
            bandBtns[0] = b
            bandRow.addView(b, lp(dp(120), dp(50)).m(r = dp(14)))
        }
        for (i in 1..bands) {
            val b = Btn(ctx, names[i - 1], BtnStyle.LIGHT, 18f)
            b.setOnClickListener { select(i) }
            bandBtns[i] = b
            bandRow.addView(b, lp(dp(120), dp(50)).m(r = dp(14)))
        }
        val rst = Btn(ctx, "RST ALL", BtnStyle.NAV, 16f)
        rst.setOnClickListener { for (i in 1..bands) App.store.set("$p/eq/$i/g", 0.5f) }
        bandRow.addView(rst, lp(dp(100), dp(50)).m(l = dp(20)))
        root.addView(bandRow, lp(MATCH, dp(66)))
        root.addView(bottom, lp(MATCH, dp(96)))
        select(1)
        root
    }

    // ------------------------------------------------------------------ SENDS

    private fun sends(ctx: Context, s: Strip): View = with(ctx) {
        val root = row()
        root.gravity = Gravity.TOP
        root.setPadding(dp(16), dp(10), dp(16), dp(10))
        val targets = ArrayList<Triple<String, Int, Int>>()
        for (i in 1..App.buses) targets.add(Triple("Bus", i, i))
        for (i in 1..4) targets.add(Triple("FX", i, 6 + i))
        for ((kind, num, mixIdx) in targets) {
            val addr = "${s.prefix}/mix/${Fmt.two(mixIdx)}/level"
            val c = col()
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.addView(label(kind, 20f, C.CYAN), lp(MATCH, dp(28)))
            val numLbl = label("$num", 18f)
            numLbl.setBackgroundColor(C.BTN_GREY)
            c.addView(numLbl, lp(dp(42), dp(40)).m(t = dp(4)))
            val vtxt = label("", 13f, C.TEXT_DIM)
            c.addView(vtxt, lp(MATCH, dp(22)).m(t = dp(4)))
            val f = FaderView(ctx)
            f.trackColor = C.CYAN
            f.bind(addr) { x ->
                val fl = x.asF()
                f.setValue(fl)
                vtxt.text = Fmt.faderDb(fl)
            }
            f.onUserChange = {
                App.store.set(addr, it)
                vtxt.text = Fmt.faderDb(it)
            }
            c.addView(f, lp(MATCH, 0, 1f))
            root.addView(c, lp(0, MATCH, 1f))
        }
        root
    }

    // ------------------------------------------------------------------ EFFECTS / otros

    fun effects(ctx: Context): View = with(ctx) {
        val root = col()
        root.setBackgroundColor(Color.BLACK)
        root.setPadding(dp(6), dp(6), dp(6), dp(6))
        for (r in 0..1) {
            val rowV = row()
            for (cIdx in 0..1) rowV.addView(fxTile(ctx, r * 2 + cIdx + 1), lp(0, MATCH, 1f).m(dp(4), dp(4), dp(4), dp(4)))
            root.addView(rowV, lp(MATCH, 0, 1f))
        }
        root.addView(
            label("Editar parámetros y cambiar el tipo de efecto: próxima versión.", 14f, C.TEXT_DIM),
            lp(MATCH, dp(36))
        )
        root
    }

    private fun fxTile(ctx: Context, n: Int): View = with(ctx) {
        val t = row()
        t.setBackgroundColor(C.PANEL)
        t.setPadding(dp(18), dp(10), dp(18), dp(10))
        val num = label("$n", 22f, C.TEXT_DIM)
        num.setBackgroundColor(Color.BLACK)
        t.addView(num, lp(dp(48), dp(48)))
        val name = label("—", 26f)
        name.setBackgroundColor(Color.BLACK)
        name.bind("/fx/$n/type") { v -> name.text = FX_TYPES.getOrElse(v.asI()) { "Efecto #${v.asI()}" } }
        t.addView(name, lp(0, dp(90), 1f).m(l = dp(18), r = dp(18)))
        val knobs = col()
        knobs.addView(knob(Spec("RETURN", "/rtn/$n/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }))
        knobs.addView(knob(Spec("SEND MASTER", "/fxsend/$n/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }))
        t.addView(knobs)
        t.addView(toggle("MUTE", "/rtn/$n/mix/on", BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 18f), lp(dp(100), dp(52)))
        t
    }

    fun placeholder(ctx: Context, what: String): View = with(ctx) {
        val v = label("$what — próxima versión", 22f, C.TEXT_DIM)
        v.setBackgroundColor(C.BG)
        v
    }
}
