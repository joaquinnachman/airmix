package ar.joaquin.airmix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Shader
import android.graphics.Typeface
import android.view.GestureDetector
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.widget.HorizontalScrollView
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView

/**
 * EFECTOS como un rack de equipos: cada FX es un frente de 19" con orejas y tornillos,
 * un display con el nombre del efecto (tocarlo cambia el tipo) y todas sus perillas para editar ahí mismo.
 * Igual en Rack.swift (iOS).
 */
object RackColors {
    val FACE_TOP = Color.parseColor("#3A4049")
    val FACE_BOTTOM = Color.parseColor("#20252B")
    val EAR = Color.parseColor("#2B3037")
    val EDGE = Color.parseColor("#4B525C")
    val SCREW = Color.parseColor("#14171B")
    val SCREW_RING = Color.parseColor("#5A626D")
    val LCD_BG = Color.parseColor("#0B1410")
    val LCD_EDGE = Color.parseColor("#22382A")
    val LCD_TEXT = Color.parseColor("#FFB04A")
    val KNOB_SKIRT = Color.parseColor("#15181C")
    val KNOB_CAP = Color.parseColor("#3C434C")
    val KNOB_CAP_HI = Color.parseColor("#5B636E")
    val TICK = Color.parseColor("#8A929C")
    val LCD_FONT: Typeface = Typeface.create(Typeface.MONOSPACE, Typeface.BOLD)
}

/** Frente de rack: degradé metálico, orejas a los costados con dos tornillos cada una. */
class RackFace(ctx: Context) : LinearLayout(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var shader: LinearGradient? = null
    val ear = ctx.dp(26)

    init {
        orientation = HORIZONTAL
        gravity = Gravity.CENTER_VERTICAL
        setWillNotDraw(false)
        setPadding(ear + ctx.dp(12), ctx.dp(8), ear + ctx.dp(12), ctx.dp(8))
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        shader = LinearGradient(0f, 0f, 0f, h.toFloat(), RackColors.FACE_TOP, RackColors.FACE_BOTTOM, Shader.TileMode.CLAMP)
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val rad = dpf(6f)
        p.style = Paint.Style.FILL
        p.shader = shader
        r.set(0f, 0f, w, h)
        c.drawRoundRect(r, rad, rad, p)
        p.shader = null
        p.color = RackColors.EAR
        r.set(0f, 0f, ear.toFloat(), h)
        c.drawRoundRect(r, rad, rad, p)
        r.set(w - ear, 0f, w, h)
        c.drawRoundRect(r, rad, rad, p)
        // tornillos
        for (x in floatArrayOf(ear / 2f, w - ear / 2f)) {
            for (y in floatArrayOf(dpf(18f), h - dpf(18f))) {
                p.color = RackColors.SCREW_RING
                c.drawCircle(x, y, dpf(6.5f), p)
                p.color = RackColors.SCREW
                c.drawCircle(x, y, dpf(5f), p)
                p.color = RackColors.SCREW_RING
                p.strokeWidth = dpf(1.5f)
                c.drawLine(x - dpf(3f), y, x + dpf(3f), y, p)
            }
        }
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1f)
        p.color = RackColors.EDGE
        r.set(0.5f, 0.5f, w - 0.5f, h - 0.5f)
        c.drawRoundRect(r, rad, rad, p)
        p.style = Paint.Style.FILL
    }
}

/** Perilla de equipo: nombre arriba, perilla con marcas y puntero, valor abajo en "display". */
class RackKnobView(ctx: Context, private val spec: Spec, private val accent: Int = C.ACCENT) : View(ctx) {
    private var pos = 0f
    private var acc = 0f
    private var lastY = 0f
    private var lastSent = -1
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val oval = RectF()
    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            lastSent = -1
            sendNorm(spec.def)
            return true
        }
    })

    init {
        bind(spec.addr) { v ->
            val n = v.asF()
            pos = if (spec.steps > 1) (n / (spec.steps - 1)).coerceIn(0f, 1f) else n.coerceIn(0f, 1f)
            invalidate()
        }
    }

    private fun sendNorm(v: Float) {
        if (spec.steps > 1) {
            val i = Math.round(v * (spec.steps - 1))
            if (i != lastSent) {
                lastSent = i
                App.store.set(spec.addr, i)
            }
        } else App.store.set(spec.addr, v)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.y
                acc = pos
                lastSent = -1
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dy = lastY - e.y
                lastY = e.y
                acc = (acc + dy / dpf(220f)).coerceIn(0f, 1f)
                sendNorm(acc)
            }
        }
        return true
    }

    private fun valueText(): String =
        if (spec.steps > 1) spec.fmt(Math.round(pos * (spec.steps - 1)).toFloat()) else spec.fmt(pos)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.typeface = FONT
        p.textAlign = Paint.Align.CENTER
        p.textSize = dpf(10.5f)
        p.color = C.TEXT_DIM
        var lbl = spec.label
        while (lbl.length > 3 && p.measureText(lbl) > w - dpf(4f)) lbl = lbl.dropLast(1)
        c.drawText(lbl, w / 2f, dpf(12f), p)

        val top = dpf(18f)
        val bottom = h - dpf(22f)
        val cy = (top + bottom) / 2f
        val cx = w / 2f
        val rOuter = minOf((bottom - top) / 2f, w / 2f - dpf(4f))
        // marcas alrededor
        p.strokeWidth = dpf(1.5f)
        p.strokeCap = Paint.Cap.ROUND
        for (k in 0..10) {
            val a = Math.toRadians((135f + k * 27f).toDouble())
            val on = k / 10f <= pos + 0.001f
            p.color = if (on) accent else RackColors.TICK
            val r1 = rOuter * 0.86f
            val r2 = rOuter
            c.drawLine(
                cx + (Math.cos(a) * r1).toFloat(), cy + (Math.sin(a) * r1).toFloat(),
                cx + (Math.cos(a) * r2).toFloat(), cy + (Math.sin(a) * r2).toFloat(), p
            )
        }
        // pollera y tapa
        p.style = Paint.Style.FILL
        p.color = RackColors.KNOB_SKIRT
        c.drawCircle(cx, cy, rOuter * 0.74f, p)
        p.color = RackColors.KNOB_CAP
        c.drawCircle(cx, cy, rOuter * 0.6f, p)
        p.color = RackColors.KNOB_CAP_HI
        c.drawCircle(cx - rOuter * 0.12f, cy - rOuter * 0.14f, rOuter * 0.3f, p)
        p.color = RackColors.KNOB_CAP
        c.drawCircle(cx, cy, rOuter * 0.42f, p)
        // puntero
        val a = Math.toRadians((135f + pos * 270f).toDouble())
        p.color = Color.WHITE
        p.strokeWidth = dpf(3f)
        c.drawLine(
            cx + (Math.cos(a) * rOuter * 0.2f).toFloat(), cy + (Math.sin(a) * rOuter * 0.2f).toFloat(),
            cx + (Math.cos(a) * rOuter * 0.66f).toFloat(), cy + (Math.sin(a) * rOuter * 0.66f).toFloat(), p
        )
        // valor
        p.typeface = RackColors.LCD_FONT
        p.textSize = dpf(11.5f)
        p.color = RackColors.LCD_TEXT
        var v = valueText()
        while (v.length > 2 && p.measureText(v) > w - dpf(2f)) v = v.dropLast(1)
        c.drawText(v, cx, h - dpf(6f), p)
    }
}

/** Display del equipo: nombre del efecto en ámbar sobre verde oscuro. */
fun Context.rackLcd(text: String, sp: Float): TextView {
    val t = TextView(this)
    t.text = text
    t.typeface = RackColors.LCD_FONT
    t.setTextColor(RackColors.LCD_TEXT)
    t.textSize = sp
    t.gravity = Gravity.CENTER_VERTICAL or Gravity.START
    t.maxLines = 2
    t.setPadding(dp(12), 0, dp(10), 0)
    t.background = rounded(RackColors.LCD_BG, 6, RackColors.LCD_EDGE)
    return t
}

object Rack {
    /** Pantalla EFECTOS: un frente de rack por FX, uno abajo del otro. */
    fun build(ctx: Context): View = with(ctx) {
        val list = col()
        list.setBackgroundColor(C.BG)
        list.setPadding(dp(10), dp(10), dp(10), dp(10))
        for (n in 1..App.console.fxUnits) {
            list.addView(unit(ctx, n), lp(MATCH, dp(sz(150, 128))).m(b = dp(10)))
        }
        list.addView(
            label("Tocá el display para cambiar el efecto. Arrastrá una perilla hacia arriba o abajo; doble toque = valor inicial.", sz(13f, 11f), C.TEXT_DIM, Gravity.START),
            lp(MATCH, dp(24))
        )
        scroll(list)
    }

    private fun unit(ctx: Context, n: Int): View = with(ctx) {
        val face = RackFace(ctx)
        val typeAddr = "/fx/$n/type"

        // --- izquierda: número + display (tocar = cambiar tipo)
        val left = col()
        left.addView(label("FX $n", sz(13f, 11f), C.ACCENT, Gravity.START or Gravity.CENTER_VERTICAL), lp(MATCH, dp(18)))
        val lcd = rackLcd("—", sz(16f, 13f))
        lcd.bind(typeAddr) { v -> lcd.text = Fx.typeName(n, v.asI()).uppercase() + "  ▾" }
        lcd.setOnClickListener {
            val pm = PopupMenu(ctx, lcd)
            Fx.typeOptions(n).forEachIndexed { i, name -> pm.menu.add(0, i, i, name) }
            pm.setOnMenuItemClickListener { item ->
                App.store.set(typeAddr, item.itemId)
                // Los parámetros cambian con el tipo: se vuelven a pedir.
                lcd.postDelayed({
                    for (k in 1..64) App.store.forget("/fx/$n/par/${Fmt.two(k)}")
                    for (k in 1..16) App.client?.query("/fx/$n/par/${Fmt.two(k)}")
                }, 400)
                true
            }
            pm.show()
        }
        left.addView(lcd, lp(MATCH, 0, 1f).m(t = dp(4)))
        face.addView(left, lp(dp(sz(200, 150)), MATCH))

        // --- centro: todas las perillas del efecto (scroll horizontal)
        val knobs = LinearLayout(ctx)
        knobs.orientation = LinearLayout.HORIZONTAL
        val hs = HorizontalScrollView(ctx)
        hs.isHorizontalScrollBarEnabled = false
        hs.addView(knobs, android.widget.FrameLayout.LayoutParams(WRAP, MATCH))
        var shown: String? = "?"
        knobs.bind(typeAddr) { v ->
            val abbr = Fx.abbr(n, v.asI())
            if (abbr == shown) return@bind
            shown = abbr
            knobs.removeAllViews()
            val pars = Fx.params(abbr)
            for (i in pars.indices) knobs.addView(RackKnobView(ctx, Fx.spec(n, i, pars[i])), lp(dp(sz(78, 66)), MATCH))
        }
        face.addView(hs, lp(0, MATCH, 1f).m(l = dp(12), r = dp(12)))

        // --- derecha: retorno + mute (o "INSERT" en X32 FX 5-8)
        val rtn = Fx.returnPrefix(n)
        val right = row()
        if (rtn != null) {
            right.addView(
                RackKnobView(ctx, Spec("RETORNO", "$rtn/mix/fader", def = 0.75f) { Fmt.faderDb(it) + " dB" }, C.SENDS),
                lp(dp(sz(78, 66)), MATCH)
            )
            val mute = toggle("MUTE", "$rtn/mix/on", BtnStyle.MUTE, invert = true, color = C.MUTE, sp = sz(15f, 12f))
            right.addView(mute, lp(dp(sz(78, 64)), dp(sz(46, 38))).m(l = dp(6)))
        } else {
            right.addView(label("INSERT", 14f, C.TEXT_DIM), lp(dp(78), MATCH))
        }
        face.addView(right, lp(WRAP, MATCH))
        face
    }
}
