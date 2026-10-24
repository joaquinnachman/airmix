package ar.joaquin.airmix

import android.app.AlertDialog
import android.content.Context
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
import android.widget.TextView
import android.widget.Toast

/** Tira de canal (igual en HOME y en el detalle). */
fun Context.stripView(s: Strip, target: SendTarget, onOpen: (Strip) -> Unit): View {
    val col = col()
    col.setBackgroundColor(C.STRIP)

    col.addView(label(s.title, 12f, Sector.of(s.kind)), lp(MATCH, dp(24)).m(t = dp(6)))

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
    fader.fillColor = if (sendAddr != null) C.SENDS else Sector.of(s.kind)
    fader.bind(faderAddr) { v ->
        val f = v.asF()
        fader.setValue(f)
        dbText.text = Fmt.faderDb(f)
    }
    fader.onUserChange = { f ->
        App.store.set(faderAddr, f)
        dbText.text = Fmt.faderDb(f)
    }
    // En "sends on fader" a un bus, el fader y los dB toman el color del bus.
    if (sendAddr != null && target.bus > 0) {
        fader.bind(Strip(Kind.BUS, target.bus).colorAddr) { v ->
            val c = BusColor.of(v.asI())
            fader.fillColor = c
            fader.invalidate()
            dbText.setTextColor(c)
        }
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
        Section.EQ -> eq(ctx, s.prefix, if (s.kind == Kind.CH) 4 else 6, s.kind == Kind.CH, s.selIdx())
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
        val ok = Btn(ctx, "OK", BtnStyle.LIGHT, 18f)
        ok.activeColor = C.GREEN
        val commit = {
            name.clearFocus()
            val imm = getSystemService(Context.INPUT_METHOD_SERVICE) as InputMethodManager
            imm.hideSoftInputFromWindow(name.windowToken, 0)
            NameCommit.send(ctx, s.nameAddr, name.text.toString(), ok)
        }
        name.setOnEditorActionListener { _, id, _ ->
            if (id == EditorInfo.IME_ACTION_DONE) {
                commit()
                true
            } else false
        }
        ok.setOnClickListener { commit() }
        r1.addView(name, lp(dp(260), dp(52)))
        r1.addView(ok, lp(dp(84), dp(52)).m(l = dp(8)))
        r1.addView(View(ctx), lp(0, 1, 1f))
        r1.addView(toggle("MUTE", s.onAddr, BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 18f), lp(dp(120), dp(52)))
        r1.addView(toggle("SOLO", s.soloAddr, color = C.SOLO, sp = 18f), lp(dp(120), dp(52)).m(l = dp(12)))
        idCard.addView(r1)

        // Paleta de color (la misma de la consola): fila de llenos y fila de contorno.
        idCard.addView(sectionTitle("COLOR"), lp(MATCH, dp(20)).m(t = dp(14), b = dp(6)))
        val swatches = ArrayList<TextView>()
        for (rowIdx in 0..1) {
            val r = row()
            for (k in 0 until 8) {
                val ci = rowIdx * 8 + k
                val sw = label("", 15f)
                applyScribble(sw, ci)
                sw.contentDescription = COLOR_NAMES[ci]
                sw.isClickable = true
                sw.setOnClickListener { App.store.set(s.colorAddr, ci) }
                swatches.add(sw)
                r.addView(sw, lp(dp(54), dp(40)).m(r = dp(8), b = dp(8)))
            }
            idCard.addView(r)
        }
        idCard.bind(s.colorAddr) { v ->
            val cur = v.asI()
            for (i in swatches.indices) swatches[i].text = if (i == cur) "✓" else ""
        }

        if (s.kind == Kind.CH) {
            val p = s.prefix
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
            // GAIN y 48V dependen de la fuente del canal: se rearman cuando cambia.
            val gainSlot = FrameLayout(ctx)
            val phantomSlot = FrameLayout(ctx)
            val gainInfo = label("", 12f, C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL)
            var shownHa: String? = "?"
            gainSlot.bind(s.sourceAddr) { v ->
                val ha = s.headampFor(v.asI())
                if (ha == shownHa) return@bind
                shownHa = ha
                gainSlot.removeAllViews()
                phantomSlot.removeAllViews()
                if (ha == null) {
                    gainSlot.addView(label("SIN PREAMP\n(fuente sin ganancia)", 13f, C.TEXT_DIM).also { it.maxLines = 2 }, FrameLayout.LayoutParams(MATCH, MATCH))
                    gainInfo.text = ""
                    return@bind
                }
                gainSlot.addView(knob(Spec("GAIN", "$ha/gain", def = 12f / 72f) { Fmt.db(Fmt.lin(-12f, 60f, it)) }), FrameLayout.LayoutParams(MATCH, MATCH))
                phantomSlot.addView(toggle("48V", "$ha/phantom", color = C.MUTE, sp = 17f), FrameLayout.LayoutParams(MATCH, MATCH))
                // Diagnóstico: si la consola no contesta por esa entrada, se avisa (y se ve la dirección usada).
                gainInfo.text = "Preamp: $ha"
                gainInfo.setTextColor(C.TEXT_DIM)
                gainInfo.postDelayed({
                    if (shownHa == ha && App.store.get("$ha/gain") == null) {
                        gainInfo.text = "Preamp: $ha · la consola no responde"
                        gainInfo.setTextColor(C.SOLO)
                    }
                }, 2500)
            }
            r3.addView(gainSlot, lp(dp(190), dp(80)))
            // X32 tiene TRIM digital por canal; en X Air la ganancia de entrada es solo el headamp.
            if (isX32) r3.addView(knob(Spec("TRIM", "$p/preamp/trim", def = 0.5f, bipolar = true) { Fmt.db(Fmt.lin(-18f, 18f, it)) }))
            r3.addView(knob(Spec("LOW CUT", "$p/preamp/hpf", def = 0f) { Fmt.hz(Fmt.log(20f, 400f, it)) }))
            pre.addView(r3)
            pre.addView(gainInfo, lp(MATCH, dp(18)))
            val r4 = row()
            r4.addView(toggle("LOW CUT", "$p/preamp/hpon", sp = 17f), lp(dp(120), dp(50)).m(r = dp(12)))
            r4.addView(phantomSlot, lp(dp(100), dp(50)).m(r = dp(12)))
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

    private fun eq(ctx: Context, p: String, bands: Int, hpf: Boolean, sel: Int): View = with(ctx) {
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
        val rta = Btn(ctx, "RTA", BtnStyle.OUTLINE, 15f)
        rta.activeColor = C.SENDS
        rta.active = rtaOn
        graph.rtaOn = rtaOn
        rta.setOnClickListener {
            rtaOn = !rtaOn
            rta.active = rtaOn
            graph.rtaOn = rtaOn
            App.client?.setRta(rtaOn)
        }
        top.addView(rta, lp(dp(72), dp(42)).m(r = dp(6)))
        val rst = Btn(ctx, "RESET", BtnStyle.OUTLINE, 15f)
        rst.setOnClickListener {
            AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
                .setMessage("¿Poner el EQ plano? Todas las bandas vuelven a 0 dB con su tipo, frecuencia y Q de fábrica.")
                .setPositiveButton("Resetear") { _, _ -> EqDefaults.reset(p, bands) }
                .setNegativeButton("Cancelar", null)
                .show()
        }
        top.addView(rst, lp(dp(90), dp(42)))
        root.addView(top)
        root.addView(graph, lp(MATCH, 0, 1f).m(t = dp(10), b = dp(10)))
        root.addView(bottom, lp(MATCH, dp(96)))
        select(1)
        // El RTA de la consola muestra el canal seleccionado: lo seleccionamos y pedimos el RTA mientras el EQ esté a la vista.
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                if (sel >= 0) App.store.set("/-stat/selidx", sel)
                App.client?.setRta(rtaOn)
            }

            override fun onViewDetachedFromWindow(v: View) {
                App.client?.setRta(false)
            }
        })
        root
    }

    /** RTA prendido/apagado (se recuerda mientras la app está abierta). */
    var rtaOn = true

    // ------------------------------------------------------------------ ENVÍOS

    private fun sends(ctx: Context, s: Strip): View = with(ctx) {
        val root = row()
        root.gravity = Gravity.TOP
        root.setPadding(dp(12), dp(10), dp(12), dp(10))
        val targets = ArrayList<SendTarget>()
        for (i in 1..App.console.buses) targets.add(SendTarget(bus = i))
        for (i in 1..App.console.fxSends) targets.add(SendTarget(fx = i))
        for (t in targets) {
            val mixIdx = if (t.bus > 0) t.bus else 6 + t.fx
            val addr = "${s.prefix}/mix/${Fmt.two(mixIdx)}/level"
            val c = col()
            c.gravity = Gravity.CENTER_HORIZONTAL
            c.background = rounded(C.SURFACE, 12)
            c.setPadding(dp(4), 0, dp(4), 0)
            // Nombre del destino: "1-JUANCITO" (con el color del bus) o "FX1 · Hall Reverb".
            val title = label(t.label, 14f, if (t.fx > 0) C.ACCENT else C.SENDS)
            title.maxLines = 2
            title.ellipsize = android.text.TextUtils.TruncateAt.END
            c.addView(title, lp(MATCH, dp(40)).m(t = dp(6)))
            val vtxt = label("", 13f, C.TEXT)
            c.addView(vtxt, lp(MATCH, dp(20)))
            val f = FaderView(ctx)
            f.fillColor = if (t.fx > 0) C.ACCENT else C.SENDS
            if (t.bus > 0) {
                title.bind(t.nameAddr!!) { v -> title.text = t.display(v.toString()) }
                title.bind(Strip(Kind.BUS, t.bus).colorAddr) { v ->
                    val col = BusColor.of(v.asI())
                    title.setTextColor(col)
                    f.fillColor = col
                    f.invalidate()
                }
            } else {
                title.bind("/fx/${t.fx}/type") { v -> title.text = Fx.label(t.fx, v.asI()) }
            }
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

    /** FX que se está editando en EFECTOS (se recuerda al cambiar de pestaña). */
    private var fxSel = 1

    fun effects(ctx: Context): View = with(ctx) {
        val root = row()
        root.gravity = Gravity.TOP
        root.setBackgroundColor(C.BG)
        root.setPadding(dp(10), dp(10), dp(10), dp(10))
        val n = App.console.fxUnits
        if (fxSel > n) fxSel = 1

        val editor = FrameLayout(ctx)
        val list = col()
        val tiles = ArrayList<Btn>()
        for (i in 1..n) {
            val b = Btn(ctx, "FX $i", BtnStyle.LIGHT, 16f)
            b.maxLines = 2
            b.gravity = Gravity.CENTER_VERTICAL or Gravity.START
            b.setPadding(dp(14), 0, dp(10), 0)
            b.ellipsize = android.text.TextUtils.TruncateAt.END
            b.bind("/fx/$i/type") { v -> b.text = "FX $i\n" + Fx.typeName(i, v.asI()) }
            b.active = i == fxSel
            b.setOnClickListener {
                fxSel = i
                for (k in tiles.indices) tiles[k].active = k + 1 == i
                editor.removeAllViews()
                editor.addView(fxEditor(ctx, i), FrameLayout.LayoutParams(MATCH, MATCH))
            }
            tiles.add(b)
            list.addView(b, lp(MATCH, dp(if (n > 4) 60 else 72)).m(b = dp(8)))
        }
        root.addView(scroll(list), lp(dp(240), MATCH).m(r = dp(10)))
        editor.addView(fxEditor(ctx, fxSel), FrameLayout.LayoutParams(MATCH, MATCH))
        root.addView(editor, lp(0, MATCH, 1f))
        root
    }

    private fun fxEditor(ctx: Context, n: Int): View = with(ctx) {
        val root = panelRoot()
        root.setPadding(0, 0, 0, 0)
        val typeAddr = "/fx/$n/type"

        // --- tipo de efecto + retorno
        val head = addCard(root, "FX $n")
        val r = row()
        val typeBtn = Btn(ctx, "—", BtnStyle.LIGHT, 18f)
        typeBtn.maxLines = 2
        typeBtn.gravity = Gravity.CENTER_VERTICAL or Gravity.START
        typeBtn.setPadding(dp(16), 0, dp(16), 0)
        typeBtn.bind(typeAddr) { v -> typeBtn.text = "EFECTO  ▾\n" + Fx.typeName(n, v.asI()) }
        typeBtn.setOnClickListener {
            val pm = PopupMenu(ctx, typeBtn)
            Fx.typeOptions(n).forEachIndexed { i, name -> pm.menu.add(0, i, i, name) }
            pm.setOnMenuItemClickListener { item ->
                App.store.set(typeAddr, item.itemId)
                // Los parámetros cambian con el tipo: se vuelven a pedir.
                typeBtn.postDelayed({
                    for (k in 1..64) App.store.forget("/fx/$n/par/${Fmt.two(k)}")
                    for (k in 1..16) App.client?.query("/fx/$n/par/${Fmt.two(k)}")
                }, 400)
                true
            }
            pm.show()
        }
        r.addView(typeBtn, lp(dp(260), dp(60)))
        val rtn = Fx.returnPrefix(n)
        if (rtn != null) {
            r.addView(knob(Spec("RETORNO", "$rtn/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }), lp(dp(190), dp(80)).m(l = dp(16)))
            if (!App.console.isX32) r.addView(knob(Spec("SEND MASTER", "/fxsend/$n/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }))
            r.addView(View(ctx), lp(0, 1, 1f))
            r.addView(toggle("MUTE", "$rtn/mix/on", BtnStyle.MUTE, invert = true, color = C.MUTE, sp = 17f), lp(dp(100), dp(52)))
        } else {
            r.addView(label("insert", 15f, C.TEXT_DIM), lp(dp(100), dp(52)).m(l = dp(16)))
        }
        head.addView(r)

        // --- parámetros (se rearman cuando cambia el tipo)
        val parCard = addCard(root, "PARÁMETROS")
        val grid = col()
        parCard.addView(grid)
        var shown: String? = "?"
        grid.bind(typeAddr) { v ->
            val abbr = Fx.abbr(n, v.asI())
            if (abbr == shown) return@bind
            shown = abbr
            grid.removeAllViews()
            val pars = Fx.params(abbr)
            for (chunk in pars.indices.chunked(4)) {
                val kr = row()
                for (i in chunk) kr.addView(knob(Fx.spec(n, i, pars[i])))
                grid.addView(kr)
            }
        }
        root.addView(
            label("Doble toque en una perilla = valor inicial. Los cambios van directo a la consola.", 13f, C.TEXT_DIM, Gravity.START),
            lp(MATCH, dp(24))
        )
        scroll(root)
    }

    fun placeholder(ctx: Context, what: String): View = with(ctx) {
        val v = label("$what — próxima versión", 20f, C.TEXT_DIM)
        v.setBackgroundColor(C.BG)
        v
    }
}

/**
 * Cambio de nombre con confirmación: manda el nombre, se lo vuelve a pedir a la consola
 * y, si no coincide, reintenta (hasta 3 veces). El botón muestra … / ✓.
 */
object NameCommit {
    fun send(ctx: Context, addr: String, raw: String, ok: Btn, attempt: Int = 1) {
        val txt = raw.trim().take(12)
        ok.text = "…"
        ok.active = false
        App.store.set(addr, txt)
        ok.postDelayed({
            App.store.forget(addr)
            App.client?.query(addr)
        }, 700)
        ok.postDelayed({
            val v = App.store.get(addr)?.toString()
            when {
                v == txt -> {
                    ok.text = "✓"
                    ok.active = true
                    ok.postDelayed({
                        ok.text = "OK"
                        ok.active = false
                    }, 2000)
                }
                attempt < 3 -> send(ctx, addr, txt, ok, attempt + 1)
                else -> {
                    ok.text = "OK"
                    Toast.makeText(ctx, "La consola no confirmó el nombre. Revisá la conexión y tocá OK de nuevo.", Toast.LENGTH_LONG).show()
                }
            }
        }, 1700)
    }
}
