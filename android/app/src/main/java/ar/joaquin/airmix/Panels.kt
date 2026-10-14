package ar.joaquin.airmix

import android.content.Context
import android.text.InputFilter
import android.text.InputType
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputMethodManager
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu

/** Tira de canal (igual en HOME y en el detalle). */
fun Context.stripView(s: Strip, target: SendTarget, onOpen: (Strip) -> Unit): View {
    val col = col()
    col.setBackgroundColor(C.STRIP)

    col.addView(label(s.title, 12f, C.TEXT_DIM), lp(MATCH, dp(24)).m(t = dp(6)))

    val name = label(s.title, 15f)
    name.setPadding(dp(4), 0, dp(4), 0)
    applyScribble(name, 0)
    name.setOnClickListener { onOpen(s) }
    name.bind(s.nameAddr) { v -> name.text = v.toString().ifEmpty { s.title } }
    name.bind(s.colorAddr) { v -> applyScribble(name, v.asI()) }
    col.addView(name, lp(MATCH, dp(36)).m(dp(8), 0, dp(8), dp(6)))

    val pan = s.panAddr
    if (pan != null) col.addView(PanView(this, pan), lp(MATCH, dp(30)))
    else col.addView(View(this), lp(MATCH, dp(30)))

    val sendAddr = s.sendAddr(target)
    val faderAddr = sendAddr ?: s.faderAddr
    val info = row()
    info.setPadding(dp(8), 0, dp(8), 0)
    info.addView(toggle("S", s.soloAddr, BtnStyle.SMALL, color = C.SOLO, sp = 15f), lp(dp(38), dp(32)))
    val dbText = label("", 14f, if (sendAddr != null) C.SENDS else C.TEXT, Gravity.END or Gravity.CENTER_VERTICAL)
    info.addView(dbText, lp(0, dp(32), 1f))
    col.addView(info, lp(MATCH, dp(40)))

    val faderRow = row()
    val fader = FaderView(this)
    fader.fillColor = if (sendAddr != null) C.SENDS else C.FILL
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
            lp(dp(if (s.meterR >= 0) 12 else 7), MATCH).m(t = dp(18), r = dp(10), b = dp(18))
        )
    }
    col.addView(faderRow, lp(MATCH, 0, 1f))
    col.addView(
        toggle("MUTE", s.onAddr, BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 18f),
        lp(MATCH, dp(48)).m(dp(8), dp(6), dp(8), dp(10))
    )
    return col
}

object Panels {
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
        k.layoutParams = lp(dp(190), dp(80))
        return k
    }

    private fun Context.knobRow(vararg specs: Spec): LinearLayout {
        val r = row()
        for (sp in specs) r.addView(knob(sp))
        return r
    }

    private fun Context.panelRoot(): LinearLayout {
        val root = col()
        root.setPadding(dp(20), dp(16), dp(20), dp(16))
        return root
    }

    private fun Context.addCard(root: LinearLayout, title: String): LinearLayout {
        val c = card()
        c.addView(sectionTitle(title), lp(MATCH, dp(22)).m(b = dp(8)))
        root.addView(c, lp(MATCH, WRAP).m(b = dp(14)))
        return c
    }

    // ------------------------------------------------------------------ CONFIG

    private fun config(ctx: Context, s: Strip): View = with(ctx) {
        val root = panelRoot()

        val idCard = addCard(root, "IDENTIDAD")
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
        r1.addView(colorBtn, lp(dp(56), dp(52)))

        val name = EditText(ctx)
        name.setTextColor(C.TEXT)
        name.background = rounded(C.BG, 10, C.LINE)
        name.typeface = FONT
        name.setTextSize(TypedValue.COMPLEX_UNIT_SP, 20f)
        name.setSingleLine()
        name.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        name.imeOptions = EditorInfo.IME_ACTION_DONE
        name.filters = arrayOf<InputFilter>(InputFilter.LengthFilter(12))
        name.setPadding(dp(16), 0, dp(16), 0)
        name.hint = "Nombre"
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
        r1.addView(name, lp(dp(260), dp(52)).m(l = dp(14)))
        r1.addView(View(ctx), lp(0, 1, 1f))
        r1.addView(toggle("MUTE", s.onAddr, BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 18f), lp(dp(120), dp(52)))
        r1.addView(toggle("SOLO", s.soloAddr, color = C.SOLO, sp = 18f), lp(dp(120), dp(52)).m(l = dp(12)))
        idCard.addView(r1)

        if (s.kind == Kind.CH) {
            val p = s.prefix
            val ha = s.headampAddr
            val isX32 = App.console.isX32

            val pre = addCard(root, "PREAMP")
            val src = label("", 15f, C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL)
            src.bind(s.sourceAddr) { v ->
                val x = v.asI()
                src.text = if (isX32) {
                    when {
                        x == 0 -> "Fuente: OFF"
                        x <= 32 -> "Fuente: IN ${Fmt.two(x)}"
                        else -> "Fuente: #$x"
                    }
                } else "Fuente: INP ${Fmt.two(x + 1)}"
            }
            pre.addView(src, lp(MATCH, dp(26)))
            val r3 = row()
            if (ha != null) r3.addView(knob(Spec("GAIN", "$ha/gain", def = 12f / 72f) { Fmt.db(Fmt.lin(-12f, 60f, it)) }))
            // X32 tiene TRIM digital por canal; en X Air la ganancia de entrada es solo el headamp.
            if (isX32) r3.addView(knob(Spec("TRIM", "$p/preamp/trim", def = 0.5f, bipolar = true) { Fmt.db(Fmt.lin(-18f, 18f, it)) }))
            r3.addView(knob(Spec("LOW CUT", "$p/preamp/hpf", def = 0f) { Fmt.hz(Fmt.log(20f, 400f, it)) }))
            pre.addView(r3)
            val r4 = row()
            r4.addView(toggle("LOW CUT", "$p/preamp/hpon", sp = 17f), lp(dp(120), dp(50)).m(r = dp(12)))
            if (ha != null) r4.addView(toggle("48V", "$ha/phantom", color = C.MUTE, sp = 17f), lp(dp(100), dp(50)).m(r = dp(12)))
            r4.addView(toggle("Ø PHASE", "$p/preamp/invert", sp = 17f), lp(dp(120), dp(50)).m(r = dp(12)))
            r4.addView(toggle("MAIN LR", s.lrAssignAddr, sp = 17f), lp(dp(130), dp(50)))
            pre.addView(r4, lp(MATCH, WRAP).m(t = dp(8)))

            val grp = addCard(root, "GRUPOS")
            val r5 = row()
            r5.addView(label("DCA", 15f, C.TEXT_DIM), lp(dp(50), dp(46)))
            for (i in 0 until App.console.dcas) r5.addView(bitToggle("${i + 1}", "$p/grp/dca", i), lp(dp(46), dp(46)).m(r = dp(6)))
            grp.addView(r5)
            val r6 = row()
            r6.addView(label("MUTE", 15f, C.TEXT_DIM), lp(dp(50), dp(46)))
            for (i in 0 until App.console.muteGroups) r6.addView(bitToggle("${i + 1}", "$p/grp/mute", i, C.MUTE), lp(dp(46), dp(46)).m(r = dp(6)))
            grp.addView(r6, lp(MATCH, WRAP).m(t = dp(8)))
        }
        root
    }

    // ------------------------------------------------------------------ GATE / DYN

    private fun keySource(ctx: Context, addr: String): View = with(ctx) {
        val key = label("", 15f, C.TEXT_DIM)
        key.maxLines = 2
        key.background = rounded(C.SURFACE2, 10)
        key.bind(addr) { v ->
            val x = v.asI()
            key.text = "KEY SOURCE\n" + App.console.keySourceName(x)
        }
        key.layoutParams = lp(dp(150), dp(56)).m(l = dp(16))
        key
    }

    private fun gate(ctx: Context, p: String): View = with(ctx) {
        val b = "$p/gate"
        val root = panelRoot()
        val main = addCard(root, "GATE")
        val top = row()
        top.addView(onOff("$b/on"), lp(dp(120), dp(52)))
        top.addView(selector("TIPO", "$b/mode", App.console.gateModes), lp(dp(180), dp(52)).m(l = dp(14)))
        main.addView(top)
        val mid = row()
        mid.addView(DynGraphView(ctx, p, true), lp(dp(220), dp(190)))
        val knobs = col()
        knobs.addView(knobRow(
            Spec("THRESHOLD", "$b/thr", def = 0f) { Fmt.db(Fmt.lin(-80f, 0f, it)) },
            Spec("RANGE", "$b/range", def = 1f) { Fmt.db(Fmt.lin(3f, 60f, it)) }
        ))
        knobs.addView(knobRow(
            Spec("ATTACK", "$b/attack", def = 0.2f) { Fmt.ms(Fmt.lin(0f, 120f, it)) },
            Spec("HOLD", "$b/hold", def = 0.5f) { Fmt.ms(Fmt.log(0.02f, 2000f, it)) },
            Spec("RELEASE", "$b/release", def = 0.5f) { Fmt.ms(Fmt.log(5f, 4000f, it)) }
        ))
        mid.addView(knobs, lp(WRAP, WRAP).m(l = dp(20)))
        main.addView(mid, lp(MATCH, WRAP).m(t = dp(12)))

        val key = addCard(root, "SIDECHAIN")
        val r = row()
        r.addView(knob(Spec("FILTRO", "$b/filter/f", def = 0.5f) { Fmt.hz(Fmt.log(20f, 20000f, it)) }))
        r.addView(toggle("FILTRO", "$b/filter/on", sp = 17f), lp(dp(110), dp(52)))
        r.addView(keySource(ctx, "$b/keysrc"))
        key.addView(r)
        root
    }

    private fun dyn(ctx: Context, p: String): View = with(ctx) {
        val b = "$p/dyn"
        val root = panelRoot()
        val main = addCard(root, "DINÁMICA")
        val top = row()
        top.addView(onOff("$b/on"), lp(dp(120), dp(52)))
        top.addView(selector("TIPO", "$b/mode", DYN_MODES), lp(dp(160), dp(52)).m(l = dp(14)))
        top.addView(View(ctx), lp(0, 1, 1f))
        top.addView(choice("$b/auto", listOf("MANUAL", "AUTO")))
        top.addView(choice("$b/env", listOf("LIN", "LOG")), lp(WRAP, WRAP).m(l = dp(10)))
        top.addView(choice("$b/det", listOf("PEAK", "RMS")), lp(WRAP, WRAP).m(l = dp(10)))
        main.addView(top)
        val mid = row()
        mid.addView(DynGraphView(ctx, p, false), lp(dp(220), dp(190)))
        val knobs = col()
        knobs.addView(knobRow(
            Spec("THRESHOLD", "$b/thr", def = 1f) { Fmt.db(Fmt.lin(-60f, 0f, it)) },
            Spec("RATIO", "$b/ratio", steps = 12, def = 3f / 11f) { RATIOS[it.toInt().coerceIn(0, 11)] + " : 1" },
            Spec("KNEE", "$b/knee", def = 0f) { Fmt.num(Fmt.lin(0f, 5f, it)) },
            Spec("GAIN", "$b/mgain", def = 0f) { Fmt.db(Fmt.lin(0f, 24f, it)) }
        ))
        knobs.addView(knobRow(
            Spec("ATTACK", "$b/attack", def = 0.2f) { Fmt.ms(Fmt.lin(0f, 120f, it)) },
            Spec("HOLD", "$b/hold", def = 0.5f) { Fmt.ms(Fmt.log(0.02f, 2000f, it)) },
            Spec("RELEASE", "$b/release", def = 0.5f) { Fmt.ms(Fmt.log(5f, 4000f, it)) },
            Spec("MIX", "$b/mix", def = 1f) { Fmt.pct(Fmt.lin(0f, 100f, it)) }
        ))
        mid.addView(knobs, lp(WRAP, WRAP).m(l = dp(20)))
        main.addView(mid, lp(MATCH, WRAP).m(t = dp(12)))

        val key = addCard(root, "SIDECHAIN")
        val r = row()
        r.addView(knob(Spec("FILTRO", "$b/filter/f", def = 0.5f) { Fmt.hz(Fmt.log(20f, 20000f, it)) }))
        r.addView(toggle("FILTRO", "$b/filter/on", sp = 17f), lp(dp(110), dp(52)))
        r.addView(keySource(ctx, "$b/keysrc"))
        key.addView(r)
        root
    }

    // ------------------------------------------------------------------ EQ

    private fun eq(ctx: Context, p: String, bands: Int, hpf: Boolean): View = with(ctx) {
        val root = col()
        root.setPadding(dp(20), dp(14), dp(20), dp(12))

        val graph = EqGraphView(ctx, p, bands, hpf)
        val names = if (bands == 4) listOf("LOW", "LO MID", "HI MID", "HIGH")
        else listOf("LOW", "LOW 2", "LO MID", "HI MID", "HIGH 2", "HIGH")
        val bandBtns = HashMap<Int, Btn>()
        val bottom = row()
        bottom.background = rounded(C.SURFACE, 14)
        bottom.setPadding(dp(16), 0, dp(16), 0)

        val select: (Int) -> Unit = { band ->
            graph.selected = band
            graph.invalidate()
            for ((k, btn) in bandBtns) btn.active = k == band
            bottom.removeAllViews()
            if (band == 0) {
                bottom.addView(toggle("LOW CUT", "$p/preamp/hpon", sp = 17f), lp(dp(130), dp(50)))
                bottom.addView(knob(Spec("FREQ", "$p/preamp/hpf", def = 0f) { Fmt.hz(Fmt.log(20f, 400f, it)) }), lp(dp(220), dp(80)).m(l = dp(24)))
            } else {
                val e = "$p/eq/$band"
                bottom.addView(selector("TIPO", "$e/type", EQ_TYPES), lp(dp(140), dp(54)))
                bottom.addView(knob(Spec("FREQ", "$e/f", def = 0.5f) { Fmt.hz(Fmt.log(20f, 20000f, it)) }), lp(dp(210), dp(80)).m(l = dp(20)))
                bottom.addView(knob(Spec("GAIN", "$e/g", def = 0.5f, bipolar = true) { Fmt.db(Fmt.lin(-15f, 15f, it)) }))
                bottom.addView(knob(Spec("Q", "$e/q", def = 0.5f) { Fmt.num(Fmt.log(10f, 0.3f, it)) }))
            }
        }
        graph.onSelect = { select(it) }

        val top = row()
        top.addView(onOff("$p/eq/on"), lp(dp(120), dp(46)))
        top.addView(View(ctx), lp(dp(20), 1))
        if (hpf) {
            val b = Btn(ctx, "LOW CUT", BtnStyle.TAB, 16f)
            b.setOnClickListener { select(0) }
            bandBtns[0] = b
            top.addView(b, lp(dp(100), dp(42)).m(r = dp(6)))
        }
        for (i in 1..bands) {
            val b = Btn(ctx, names[i - 1], BtnStyle.TAB, 16f)
            b.setOnClickListener { select(i) }
            bandBtns[i] = b
            top.addView(b, lp(dp(92), dp(42)).m(r = dp(6)))
        }
        top.addView(View(ctx), lp(0, 1, 1f))
        val rst = Btn(ctx, "RESET", BtnStyle.OUTLINE, 15f)
        rst.setOnClickListener { for (i in 1..bands) App.store.set("$p/eq/$i/g", 0.5f) }
        top.addView(rst, lp(dp(90), dp(42)))
        root.addView(top)
        root.addView(graph, lp(MATCH, 0, 1f).m(t = dp(10), b = dp(10)))
        root.addView(bottom, lp(MATCH, dp(96)))
        select(1)
        root
    }

    // ------------------------------------------------------------------ ENVÍOS

    private fun sends(ctx: Context, s: Strip): View = with(ctx) {
        val root = row()
        root.gravity = Gravity.TOP
        root.setPadding(dp(12), dp(10), dp(12), dp(10))
        val targets = ArrayList<Triple<String, Int, Int>>()
        for (i in 1..App.console.buses) targets.add(Triple("BUS", i, i))
        for (i in 1..App.console.fxSends) targets.add(Triple("FX", i, 6 + i))
        for ((kind, num, mixIdx) in targets) {
            val addr = "${s.prefix}/mix/${Fmt.two(mixIdx)}/level"
            val c = col()
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.background = rounded(C.SURFACE, 12)
            c.addView(label("$kind $num", 15f, if (kind == "FX") C.ACCENT else C.SENDS), lp(MATCH, dp(30)).m(t = dp(6)))
            val vtxt = label("", 13f, C.TEXT)
            c.addView(vtxt, lp(MATCH, dp(20)))
            val f = FaderView(ctx)
            f.fillColor = if (kind == "FX") C.ACCENT else C.SENDS
            f.bind(addr) { x ->
                val fl = x.asF()
                f.setValue(fl)
                vtxt.text = Fmt.faderDb(fl)
            }
            f.onUserChange = {
                App.store.set(addr, it)
                vtxt.text = Fmt.faderDb(it)
            }
            c.addView(f, lp(MATCH, 0, 1f).m(b = dp(8)))
            root.addView(c, lp(0, MATCH, 1f).m(dp(3), 0, dp(3), 0))
        }
        root
    }

    // ------------------------------------------------------------------ EFFECTS / otros

    fun effects(ctx: Context): View = with(ctx) {
        val root = col()
        root.setBackgroundColor(C.BG)
        root.setPadding(dp(10), dp(10), dp(10), dp(4))
        val n = App.console.fxUnits
        for (r in 0 until (n + 1) / 2) {
            val rowV = row()
            for (cIdx in 0..1) {
                val i = r * 2 + cIdx + 1
                if (i <= n) rowV.addView(fxTile(ctx, i), lp(0, MATCH, 1f).m(dp(5), dp(5), dp(5), dp(5)))
            }
            root.addView(rowV, lp(MATCH, 0, 1f))
        }
        root.addView(
            label("Editar parámetros y cambiar el tipo de efecto: próxima versión.", 13f, C.TEXT_DIM),
            lp(MATCH, dp(30))
        )
        root
    }

    private fun fxTile(ctx: Context, n: Int): View = with(ctx) {
        val c = App.console
        val t = row()
        t.background = rounded(C.SURFACE, 14)
        t.setPadding(dp(18), dp(8), dp(18), dp(8))
        val num = label("FX $n", 16f, C.ACCENT)
        t.addView(num, lp(dp(56), MATCH))
        val name = label("—", 22f, C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL)
        name.bind("/fx/$n/type") { v -> name.text = FX_TYPES.getOrElse(v.asI()) { "Efecto #${v.asI()}" } }
        t.addView(name, lp(0, MATCH, 1f).m(l = dp(8), r = dp(8)))
        // Retorno de cada FX: X Air /rtn/N; X32 FX 1-4 vuelven por /fxrtn (pares L/R: 1-2, 3-4...).
        val rtn: String? = if (c.isX32) (if (n <= 4) "/fxrtn/${Fmt.two(2 * n - 1)}" else null) else "/rtn/$n"
        if (rtn != null) {
            val knobs = col()
            knobs.addView(knob(Spec("RETORNO", "$rtn/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }))
            if (!c.isX32) knobs.addView(knob(Spec("SEND MASTER", "/fxsend/$n/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }))
            t.addView(knobs)
            t.addView(toggle("MUTE", "$rtn/mix/on", BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 16f), lp(dp(90), dp(48)))
        } else {
            t.addView(label("insert", 14f, C.TEXT_DIM), lp(dp(90), MATCH))
        }
        t
    }

    fun placeholder(ctx: Context, what: String): View = with(ctx) {
        val v = label("$what — próxima versión", 20f, C.TEXT_DIM)
        v.setBackgroundColor(C.BG)
        v
    }
}
