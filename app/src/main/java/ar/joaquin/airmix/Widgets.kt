package ar.joaquin.airmix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

// Todas las vistas dibujan con objetos preasignados (nada de "new" dentro de onDraw en lo caliente).

/** Fader vertical. Arrastre relativo (no salta al tocar); doble tap = 0 dB. */
class FaderView(ctx: Context) : View(ctx) {
    var value = 0f
        private set
    var trackColor = Color.WHITE
    var onUserChange: ((Float) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var lastY = 0f
    private val marks = floatArrayOf(10f, 5f, 0f, -5f, -10f, -20f, -30f, -40f, -50f, -60f)
    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            setValue(0.75f)
            onUserChange?.invoke(value)
            return true
        }
    })

    fun setValue(v: Float) {
        val n = v.coerceIn(0f, 1f)
        if (n != value) {
            value = n
            invalidate()
        }
    }

    private fun capH() = dpf(64f)
    private fun top() = capH() / 2f + dpf(4f)
    private fun travel() = height - capH() - dpf(8f)

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val top = top()
        val travel = travel()
        val bottom = top + travel
        val span = minOf(dpf(30f), width / 2f - dpf(4f))
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1f)
        p.color = C.TICK
        for (i in 0..40) {
            val y = top + travel * i / 40f
            c.drawLine(cx - span, y, cx - dpf(7f), y, p)
            c.drawLine(cx + dpf(7f), y, cx + span, y, p)
        }
        p.color = Color.WHITE
        p.strokeWidth = dpf(2.5f)
        for (db in marks) {
            val y = bottom - Fmt.dbToFader(db) * travel
            c.drawLine(cx - span, y, cx - dpf(7f), y, p)
            c.drawLine(cx + dpf(7f), y, cx + span, y, p)
        }
        p.color = trackColor
        p.strokeWidth = dpf(3f)
        c.drawLine(cx, top - dpf(12f), cx, bottom + dpf(12f), p)

        val y = bottom - value * travel
        val ch = capH()
        val hw = dpf(17f)
        // marco del cap
        p.strokeWidth = dpf(1.5f)
        p.color = C.CAP_EDGE
        r.set(cx - hw - dpf(10f), y - ch / 2f, cx + hw + dpf(10f), y - dpf(2f))
        c.drawRoundRect(r, dpf(6f), dpf(6f), p)
        r.set(cx - hw - dpf(10f), y + dpf(2f), cx + hw + dpf(10f), y + ch / 2f)
        c.drawRoundRect(r, dpf(6f), dpf(6f), p)
        // cap en dos tonos
        p.style = Paint.Style.FILL
        p.color = C.CAP_TOP
        r.set(cx - hw, y - ch / 2f + dpf(4f), cx + hw, y)
        c.drawRect(r, p)
        p.color = Color.WHITE
        r.set(cx - hw, y, cx + hw, y + ch / 2f - dpf(4f))
        c.drawRect(r, p)
        p.color = C.CAP_EDGE
        c.drawRect(cx - hw, y - dpf(1f), cx + hw, y + dpf(1f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.y
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val dv = (lastY - e.y) / maxOf(travel(), 1f)
                lastY = e.y
                val nv = (value + dv).coerceIn(0f, 1f)
                if (nv != value) {
                    value = nv
                    invalidate()
                    onUserChange?.invoke(value)
                }
            }
        }
        return true
    }
}

/** Definición de una perilla. Para parámetros enumerados, steps > 0 y el valor es el índice. */
class Spec(
    val label: String,
    val addr: String,
    val steps: Int = 0,
    val def: Float = 0f,
    val bipolar: Boolean = false,
    val fmt: (Float) -> String
)

/** Perilla: arrastre vertical, doble tap = valor por defecto. */
class KnobView(ctx: Context, private val spec: Spec) : View(ctx) {
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
        } else {
            App.store.set(spec.addr, v)
        }
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
                acc = (acc + dy / dpf(240f)).coerceIn(0f, 1f)
                sendNorm(acc)
            }
        }
        return true
    }

    private fun valueText(): String =
        if (spec.steps > 1) spec.fmt(Math.round(pos * (spec.steps - 1)).toFloat()) else spec.fmt(pos)

    override fun onDraw(c: Canvas) {
        val h = height.toFloat()
        val r = h * 0.36f
        val cx = r + dpf(10f)
        val cy = h / 2f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.BUTT
        p.strokeWidth = r * 0.45f
        p.color = C.KNOB_BG
        c.drawArc(oval, 135f, 270f, false, p)
        p.color = C.KNOB_ARC
        if (spec.bipolar) c.drawArc(oval, 270f, (pos - 0.5f) * 270f, false, p)
        else c.drawArc(oval, 135f, pos * 270f, false, p)
        val ang = Math.toRadians((135f + pos * 270f).toDouble())
        p.strokeWidth = dpf(2f)
        p.color = Color.WHITE
        val ri = r * 0.55f
        val ro = r * 1.2f
        c.drawLine(
            cx + (Math.cos(ang) * ri).toFloat(), cy + (Math.sin(ang) * ri).toFloat(),
            cx + (Math.cos(ang) * ro).toFloat(), cy + (Math.sin(ang) * ro).toFloat(), p
        )
        p.style = Paint.Style.FILL
        p.typeface = FONT
        p.textAlign = Paint.Align.LEFT
        val tx = cx + r + dpf(16f)
        p.textSize = dpf(12f)
        p.color = C.TEXT_DIM
        c.drawText(spec.label, tx, cy - dpf(5f), p)
        p.textSize = dpf(16f)
        p.color = Color.WHITE
        c.drawText(valueText(), tx, cy + dpf(14f), p)
    }
}

/** Paneo tipo "moño" con marcador; arrastre horizontal, doble tap = centro. */
class PanView(ctx: Context, private val addr: String) : View(ctx) {
    private var pos = 0.5f
    private var lastX = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private val gd = GestureDetector(ctx, object : GestureDetector.SimpleOnGestureListener() {
        override fun onDoubleTap(e: MotionEvent): Boolean {
            App.store.set(addr, 0.5f)
            return true
        }
    })

    init {
        bind(addr) { v ->
            pos = v.asF().coerceIn(0f, 1f)
            invalidate()
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        val cx = w / 2f
        val cy = h / 2f
        path.reset()
        path.moveTo(0f, cy - h * 0.4f)
        path.lineTo(cx, cy - h * 0.08f)
        path.lineTo(w.toFloat(), cy - h * 0.4f)
        path.lineTo(w.toFloat(), cy + h * 0.4f)
        path.lineTo(cx, cy + h * 0.08f)
        path.lineTo(0f, cy + h * 0.4f)
        path.close()
    }

    override fun onDraw(c: Canvas) {
        p.style = Paint.Style.FILL
        p.color = C.PAN_BG
        c.drawPath(path, p)
        p.color = Color.WHITE
        val x = pos * width
        c.drawRect(x - dpf(1.5f), 0f, x + dpf(1.5f), height.toFloat(), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val nv = (pos + (e.x - lastX) / maxOf(width.toFloat(), 1f)).coerceIn(0f, 1f)
                lastX = e.x
                if (nv != pos) App.store.set(addr, nv)
            }
        }
        return true
    }
}

object MeterDraw {
    /** Barra vertical de -60 a 0 dB: verde, amarillo desde -12, rojo desde -3. */
    fun bar(c: Canvas, p: Paint, l: Float, t: Float, r: Float, b: Float, db: Float) {
        if (db <= -60f) return
        val h = b - t
        val yv = b - ((db + 60f) / 60f).coerceIn(0f, 1f) * h
        val y12 = b - (48f / 60f) * h
        val y3 = b - (57f / 60f) * h
        p.color = C.GREEN
        c.drawRect(l, maxOf(yv, y12), r, b, p)
        if (yv < y12) {
            p.color = C.YELLOW
            c.drawRect(l, maxOf(yv, y3), r, y12, p)
        }
        if (yv < y3) {
            p.color = C.RED
            c.drawRect(l, yv, r, y3, p)
        }
    }
}

/** Medidor de canal (mono o L/R). Lo redibuja el tick central de MainActivity. */
class MeterView(ctx: Context, private val l: Int, private val r: Int = -1) : View(ctx) {
    private val p = Paint()

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        App.meterViews.add(this)
    }

    override fun onDetachedFromWindow() {
        App.meterViews.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.color = C.METER_BG
        c.drawRect(0f, 0f, w, h, p)
        val m = App.client?.meters ?: return
        if (l !in m.indices) return
        if (r < 0 || r !in m.indices) {
            MeterDraw.bar(c, p, 0f, 0f, w, h, m[l])
        } else {
            val half = w / 2f
            MeterDraw.bar(c, p, 0f, 0f, half - 1f, h, m[l])
            MeterDraw.bar(c, p, half + 1f, 0f, w, h, m[r])
        }
    }
}

/** Pantalla METERS: los 40 medidores en una sola vista. */
class AllMetersView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels: Array<String> = Array(MixerClient.METER_COUNT) { i ->
        when {
            i < 16 -> "${i + 1}"
            i == 16 -> "AUX L"
            i == 17 -> "AUX R"
            i < 26 -> "FX${(i - 18) / 2 + 1}${if ((i - 18) % 2 == 0) "L" else "R"}"
            i < 32 -> "B${i - 25}"
            i < 36 -> "FXS${i - 31}"
            i == 36 -> "LR L"
            i == 37 -> "LR R"
            i == 38 -> "MON L"
            else -> "MON R"
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        App.meterViews.add(this)
    }

    override fun onDetachedFromWindow() {
        App.meterViews.remove(this)
        super.onDetachedFromWindow()
    }

    override fun onDraw(c: Canvas) {
        val m = App.client?.meters ?: return
        val n = m.size
        val pad = dpf(16f)
        val labelH = dpf(26f)
        val slot = (width - 2 * pad) / n
        val bw = slot * 0.55f
        val top = pad
        val bottom = height - pad - labelH
        p.textSize = dpf(11f)
        p.typeface = FONT
        p.textAlign = Paint.Align.CENTER
        for (i in 0 until n) {
            val x = pad + slot * i + (slot - bw) / 2f
            p.color = C.METER_BG
            c.drawRect(x, top, x + bw, bottom, p)
            MeterDraw.bar(c, p, x, top, x + bw, bottom, m[i])
            p.color = C.TEXT_DIM
            c.drawText(labels[i], x + bw / 2f, bottom + dpf(18f), p)
        }
    }
}

/** Ícono de WiFi: verde conectado, rojo sin consola. */
class WifiIcon(ctx: Context) : View(ctx) {
    var ok = false
        set(v) {
            if (field != v) {
                field = v
                invalidate()
            }
        }
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val cx = width / 2f
        val cy = height * 0.68f
        p.color = if (ok) C.GREEN else C.RED
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = dpf(3f)
        for (i in 1..3) {
            val rad = dpf(6.5f) * i
            r.set(cx - rad, cy - rad, cx + rad, cy + rad)
            c.drawArc(r, 225f, 90f, false, p)
        }
        p.style = Paint.Style.FILL
        c.drawCircle(cx, cy, dpf(2.5f), p)
    }
}
