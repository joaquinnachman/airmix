package ar.joaquin.airmix

import android.app.AlertDialog
import android.content.Context
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.view.Gravity
import android.view.View
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.Toast

/** Pantalla SALA (igual que RoomView en iOS): micrófonos, salida, ruido rosa, MEDIR y el mapa de riesgo. */
object RoomScreen {
    fun build(ctx: Context): View = with(ctx) {
        val root = row()
        root.gravity = Gravity.TOP
        root.setBackgroundColor(C.BG)
        root.setPadding(dp(sz(14, 8)), dp(sz(10, 6)), dp(sz(14, 8)), dp(sz(10, 6)))

        // ---------------------------------------------------------- izquierda: pasos
        val left = col()
        val warn = label(
            "Usalo a volumen moderado, con los micrófonos en su lugar de show. No hace falta conectar nada. " +
                "Al terminar, todo vuelve como estaba.", sz(13f, 11f), C.YELLOW, Gravity.START
        )
        warn.maxLines = 4
        left.addView(warn, lp(MATCH, WRAP).m(b = dp(sz(10, 6))))

        // 1 · micrófonos
        val c1 = card()
        c1.setPadding(dp(sz(14, 10)), dp(sz(12, 8)), dp(sz(14, 10)), dp(sz(12, 8)))
        c1.addView(sectionTitle("1 · MICRÓFONOS"), lp(MATCH, dp(sz(22, 18))))
        val chips = ArrayList<Btn>()
        val n = App.console.channels
        var r: LinearLayout? = null
        for (ch in 1..n) {
            if ((ch - 1) % 3 == 0) {
                r = row()
                c1.addView(r, lp(MATCH, dp(sz(40, 32))).m(t = dp(4)))
            }
            val s = Strip(Kind.CH, ch)
            val b = Btn(ctx, s.title, BtnStyle.LIGHT, sz(13f, 11f))
            b.activeColor = Sector.of(Kind.CH)
            b.fitOneLine(8)
            b.setPadding(dp(4), 0, dp(4), 0)
            b.active = Room.mics[ch - 1]
            b.bind(s.nameAddr) { v -> b.text = v.toString().trim().ifEmpty { s.title } }
            b.setOnClickListener {
                if (Room.running) return@setOnClickListener
                Room.userPicked = true
                Room.mics[ch - 1] = !Room.mics[ch - 1]
                b.active = Room.mics[ch - 1]
            }
            chips.add(b)
            r!!.addView(b, lp(0, MATCH, 1f).m(r = dp(4)))
        }
        while (r != null && r.childCount < 3) r.addView(View(ctx), lp(0, 1, 1f))
        // Tildados por defecto: los que tienen señal o 48V (hasta que elijas a mano).
        val auto = {
            if (!Room.userPicked && !Room.running) {
                val now = android.os.SystemClock.uptimeMillis()
                for (ch in 1..n) {
                    val s = Strip(Kind.CH, ch)
                    val ha = s.headampFor(App.store.int(s.sourceAddr, ch - 1))
                    val ph = ha != null && App.store.int("$ha/phantom", 0) != 0
                    Room.mics[ch - 1] = Clip.signal(s.meterL, now) || ph
                    chips[ch - 1].active = Room.mics[ch - 1]
                }
            }
            Unit
        }
        for (ch in 1..n) c1.bind(Strip(Kind.CH, ch).sourceAddr) { auto() }
        for (h in 1..minOf(App.console.headamps, 16)) c1.bind("/headamp/${Fmt.two(h)}/phantom") { auto() }
        c1.postDelayed({ auto() }, 1600)
        left.addView(c1, lp(MATCH, WRAP))

        // 2 · salida
        val c2 = card()
        c2.setPadding(dp(sz(14, 10)), dp(sz(12, 8)), dp(sz(14, 10)), dp(sz(12, 8)))
        c2.addView(sectionTitle("2 · SALIDA A PROBAR"), lp(MATCH, dp(sz(22, 18))))
        val outs = ArrayList<Btn>()
        val outRow = row()
        for (bus in 0..App.console.buses) {
            val b = Btn(ctx, if (bus == 0) "MAIN" else "BUS $bus", BtnStyle.LIGHT, sz(13f, 11f))
            b.activeColor = if (bus == 0) Sector.of(Kind.LR) else C.SENDS
            b.fitOneLine(8)
            b.setPadding(dp(3), 0, dp(3), 0)
            b.active = Room.outBus == bus
            if (bus > 0) b.bind(Strip(Kind.BUS, bus).nameAddr) { v -> b.text = SendTarget(bus = bus).display(v.toString()) }
            b.setOnClickListener {
                if (Room.running) return@setOnClickListener
                Room.outBus = bus
                for (k in outs.indices) outs[k].active = k == bus
            }
            outs.add(b)
            outRow.addView(b, lp(0, dp(sz(40, 32)), 1f).m(r = dp(4)))
        }
        c2.addView(outRow, lp(MATCH, WRAP).m(t = dp(4)))
        left.addView(c2, lp(MATCH, WRAP).m(t = dp(sz(8, 6))))

        // 3 · ruido rosa
        val c3 = card()
        c3.setPadding(dp(sz(14, 10)), dp(sz(12, 8)), dp(sz(14, 10)), dp(sz(12, 8)))
        c3.addView(sectionTitle("3 · RUIDO ROSA"), lp(MATCH, dp(sz(22, 18))))
        val nt = label(
            "Sale por el parlante de la tablet: apoyala cerca de los micrófonos y subí el volumen de la tablet " +
                "hasta que se escuche claro, sin exagerar.", sz(12f, 10f), C.TEXT_DIM, Gravity.START
        )
        nt.maxLines = 4
        c3.addView(nt, lp(MATCH, WRAP).m(t = dp(2), b = dp(6)))
        val test = Btn(ctx, "PROBAR RUIDO", BtnStyle.OUTLINE, sz(14f, 12f))
        test.activeColor = C.ACCENT
        test.active = PinkNoise.isOn
        test.setOnClickListener {
            if (Room.running) return@setOnClickListener
            if (PinkNoise.isOn) PinkNoise.stop() else PinkNoise.start()
            test.active = PinkNoise.isOn
        }
        c3.addView(test, lp(MATCH, dp(sz(40, 32))))
        left.addView(c3, lp(MATCH, WRAP).m(t = dp(sz(8, 6))))

        val measure = Btn(ctx, "MEDIR", BtnStyle.LIGHT, sz(18f, 15f))
        measure.activeColor = C.GREEN
        left.addView(measure, lp(MATCH, dp(sz(54, 42))).m(t = dp(sz(10, 6))))

        val leftScroll = ScrollView(ctx)
        leftScroll.isVerticalScrollBarEnabled = false
        leftScroll.addView(left, android.widget.FrameLayout.LayoutParams(MATCH, WRAP))
        root.addView(leftScroll, lp(dp(sz(340, 260)), MATCH))

        // ---------------------------------------------------------- derecha: resultado
        val right = col()
        right.background = rounded(C.SURFACE, 14)
        right.setPadding(dp(sz(14, 8)), dp(sz(10, 6)), dp(sz(14, 8)), dp(sz(10, 6)))
        val status = label("", sz(14f, 11f), C.TEXT, Gravity.START or Gravity.CENTER_VERTICAL)
        status.maxLines = 2
        right.addView(status, lp(MATCH, dp(sz(40, 30))))
        val graph = RiskGraphView(ctx)
        right.addView(graph, lp(MATCH, 0, 1f).m(t = dp(4), b = dp(6)))
        val alerts = col()
        val alertScroll = ScrollView(ctx)
        alertScroll.isVerticalScrollBarEnabled = false
        alertScroll.addView(alerts, android.widget.FrameLayout.LayoutParams(MATCH, WRAP))
        right.addView(alertScroll, lp(MATCH, dp(sz(170, 96))))
        root.addView(right, lp(0, MATCH, 1f).m(l = dp(sz(14, 8))))

        val refresh = {
            measure.text = if (Room.running) "CANCELAR" else "MEDIR"
            measure.active = !Room.running
            test.active = PinkNoise.isOn
            val res = Room.result
            status.text = Room.status.ifEmpty {
                if (res == null) "Elegí micrófonos y salida, poné el ruido rosa y tocá MEDIR (tarda ~5 s por micrófono)." else ""
            }
            status.setTextColor(if (Room.running) C.SENDS else C.TEXT)
            graph.invalidate()
            alerts.removeAllViews()
            if (res != null && !Room.running) {
                if (res.alerts.isEmpty()) {
                    alerts.addView(label("✓ Sin riesgos marcados en ${res.outName}.", sz(14f, 12f), C.GREEN, Gravity.START or Gravity.CENTER_VERTICAL), lp(MATCH, dp(sz(36, 28))))
                }
                for (a in res.alerts) {
                    val line = row()
                    val t = label(a.text, sz(14f, 11f), if (a.high) C.RED else C.YELLOW, Gravity.START or Gravity.CENTER_VERTICAL)
                    t.maxLines = 2
                    line.addView(t, lp(0, MATCH, 1f))
                    val cut = Btn(ctx, "✂ CORTAR", BtnStyle.OUTLINE, sz(13f, 11f))
                    cut.maxLines = 1
                    cut.setOnClickListener { confirmCut(ctx, res, a) }
                    line.addView(cut, lp(dp(sz(110, 84)), dp(sz(38, 30))).m(l = dp(6)))
                    alerts.addView(line, lp(MATCH, dp(sz(44, 34))))
                }
            }
            Unit
        }
        measure.setOnClickListener {
            if (Room.running) Room.cancel("Cancelada: todo volvió como estaba.")
            else {
                val err = Room.start()
                if (err != null) Toast.makeText(ctx, err, Toast.LENGTH_LONG).show()
            }
            refresh()
        }
        root.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(v: View) {
                Room.onChange = refresh
                refresh()
            }

            override fun onViewDetachedFromWindow(v: View) {
                if (Room.onChange === refresh) Room.onChange = null
                // Salir de SALA: nada queda sonando ni cambiado.
                Room.cancel("Cancelada al salir de SALA: todo volvió como estaba.")
                if (PinkNoise.isOn) PinkNoise.stop()
            }
        })
        refresh()
        root
    }

    private fun confirmCut(ctx: Context, res: Room.Result, a: Room.Alert) {
        val out = Room.outStrip(res.outBus)
        AlertDialog.Builder(ctx, android.R.style.Theme_DeviceDefault_Dialog_Alert)
            .setMessage("¿Cortar ${Fmt.hz(a.hz)} en el EQ de ${res.outName}?\nPEQ -6 dB, Q 8 en una banda libre (si ya hay un corte cerca, lo baja 3 dB más).")
            .setPositiveButton("OK") { _, _ ->
                val band = Panels.cutFeedback(out.prefix, 6, a.hz)
                Toast.makeText(
                    ctx,
                    if (band < 0) "No hay una banda libre en el EQ de ${res.outName}: liberá una (ganancia en 0 dB)."
                    else "Corte en ${Fmt.hz(a.hz)} en ${res.outName} (banda $band).",
                    Toast.LENGTH_LONG
                ).show()
            }
            .setNegativeButton("Cancelar", null)
            .show()
    }
}

/** Mapa de riesgo: barras por banda (rojo = alto, ámbar = medio) sobre el espectro promedio de los micrófonos. */
class RiskGraphView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gridHz = floatArrayOf(100f, 1000f, 10000f)
    private val gridLbl = arrayOf("100", "1k", "10k")

    private fun x(i: Int, l: Float, w: Float) = l + w * i / (Room.BINS - 1f)

    override fun onDraw(c: Canvas) {
        val l = dpf(4f)
        val t = dpf(4f)
        val w = width - dpf(8f)
        val h = height - dpf(22f)
        p.typeface = FONT
        p.style = Paint.Style.FILL
        p.color = C.SURFACE2
        c.drawRect(l, t, l + w, t + h, p)
        p.textSize = dpf(11f)
        p.textAlign = Paint.Align.CENTER
        for (k in gridHz.indices) {
            val gx = l + Fmt.logInv(20f, 20000f, gridHz[k]) * w
            p.color = C.LINE
            c.drawRect(gx - dpf(0.5f), t, gx + dpf(0.5f), t + h, p)
            p.color = C.TEXT_DIM
            c.drawText(gridLbl[k], gx, t + h + dpf(15f), p)
        }
        val res = Room.result
        if (res == null) {
            p.color = C.TEXT_DIM
            p.textSize = dpf(14f)
            c.drawText(if (Room.running) "Midiendo…" else "Todavía no mediste esta sala", l + w / 2f, t + h / 2f, p)
            return
        }
        // Espectro promedio (gris), -90..-10 dB.
        path.reset()
        for (i in 0 until Room.BINS) {
            val y = t + h * (1f - ((res.spectrum[i] + 90f) / 80f).coerceIn(0f, 1f))
            if (i == 0) path.moveTo(x(i, l, w), y) else path.lineTo(x(i, l, w), y)
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1.5f)
        p.color = C.TEXT_DIM
        c.drawPath(path, p)
        // Riesgo por banda: 0..8 dB de crecimiento sobre la mediana.
        p.style = Paint.Style.FILL
        val bw = maxOf(w / Room.BINS - dpf(1f), dpf(1f))
        for (i in Room.LO_BIN..Room.HI_BIN) {
            val s = res.score[i]
            if (s <= 0.3f) continue
            val bh = h * (s / 8f).coerceIn(0f, 1f)
            p.color = if (s >= Room.HIGH_DB) C.RED else if (s >= Room.MID_DB) C.YELLOW else C.LINE
            val bx = x(i, l, w)
            c.drawRect(bx - bw / 2f, t + h - bh, bx + bw / 2f, t + h, p)
        }
        // Marcas de las alertas.
        p.textSize = dpf(12f)
        for (a in res.alerts) {
            val ax = l + Fmt.logInv(20f, 20000f, a.hz) * w
            p.color = if (a.high) C.RED else C.YELLOW
            c.drawRect(ax - dpf(1f), t, ax + dpf(1f), t + h, p)
            p.textAlign = if (ax > l + w * 0.85f) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(Room.shortHz(a.hz), ax + if (p.textAlign == Paint.Align.LEFT) dpf(4f) else -dpf(4f), t + dpf(14f), p)
        }
        p.textAlign = Paint.Align.CENTER
    }
}
