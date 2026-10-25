package ar.joaquin.airmix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.view.GestureDetector
import android.view.MotionEvent
import android.view.View

// Todas las vistas dibujan con objetos preasignados (nada de "new" dentro de onDraw).

/**
 * Fader vertical con escala en dB a la izquierda y relleno hasta el cap.
 * Arrastre relativo (no salta al tocar); doble tap = 0 dB.
 */
class FaderView(ctx: Context) : View(ctx) {
    var value = 0f
        private set
    var fillColor = C.FILL
    var onUserChange: ((Float) -> Unit)? = null

    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private var lastY = 0f
    private var dragging = false
    private val marks = floatArrayOf(10f, 5f, 0f, -5f, -10f, -20f, -30f, -40f, -60f)
    private val markLbl = arrayOf("+10", "+5", "0", "-5", "-10", "-20", "-30", "-40", "-60")
    /** Orden de prioridad de las etiquetas: si el fader es corto, solo entran las primeras (no se enciman). */
    private val labelOrder = intArrayOf(2, 8, 0, 5, 4, 7, 1, 6, 3)
    private val drawnY = FloatArray(9)
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

    private fun capH() = dpf(30f)
    private fun top() = capH() / 2f + dpf(6f)
    private fun travel() = height - capH() - dpf(12f)

    override fun onDraw(c: Canvas) {
        val top = top()
        val travel = travel()
        val bottom = top + travel
        val cx = width * 0.62f
        val tw = dpf(6f)

        // escala
        p.style = Paint.Style.FILL
        p.typeface = FONT
        p.textSize = dpf(10f)
        p.textAlign = Paint.Align.RIGHT
        val lx = cx - dpf(18f)
        val minGap = p.textSize * 1.25f
        var drawn = 0
        for (oi in labelOrder.indices) {
            val i = labelOrder[oi]
            val y = bottom - Fmt.dbToFader(marks[i]) * travel
            p.color = if (marks[i] == 0f) C.TEXT else C.TICK
            c.drawRect(lx + dpf(3f), y - dpf(0.5f), cx - tw / 2f - dpf(3f), y + dpf(0.5f), p)
            var free = true
            for (d in 0 until drawn) if (Math.abs(drawnY[d] - y) < minGap) { free = false; break }
            // Si la tira es muy angosta no hay lugar para los números: solo las rayitas.
            if (free && lx >= dpf(22f)) {
                c.drawText(markLbl[i], lx, y + dpf(3.5f), p)
                drawnY[drawn++] = y
            }
        }

        // riel
        p.color = C.METER_BG
        r.set(cx - tw / 2f, top - dpf(4f), cx + tw / 2f, bottom + dpf(4f))
        c.drawRoundRect(r, tw / 2f, tw / 2f, p)
        // relleno hasta la posición
        val y = bottom - value * travel
        if (value > 0f) {
            p.color = fillColor
            r.set(cx - tw / 2f, y, cx + tw / 2f, bottom + dpf(4f))
            c.drawRoundRect(r, tw / 2f, tw / 2f, p)
        }

        // cap
        val hw = minOf(dpf(24f), width * 0.3f)
        val ch = capH()
        p.color = Color.BLACK
        p.alpha = 90
        r.set(cx - hw, y - ch / 2f + dpf(3f), cx + hw, y + ch / 2f + dpf(3f))
        c.drawRoundRect(r, dpf(7f), dpf(7f), p)
        p.alpha = 255
        p.color = if (dragging) Color.WHITE else C.CAP
        r.set(cx - hw, y - ch / 2f, cx + hw, y + ch / 2f)
        c.drawRoundRect(r, dpf(7f), dpf(7f), p)
        p.color = C.CAP_SHADE
        r.set(cx - hw, y, cx + hw, y + ch / 2f)
        c.drawRoundRect(r, dpf(7f), dpf(7f), p)
        p.color = fillColor
        c.drawRect(cx - hw + dpf(6f), y - dpf(1.5f), cx + hw - dpf(6f), y + dpf(1.5f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastY = e.y
                dragging = true
                invalidate()
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
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                dragging = false
                invalidate()
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

/** Perilla tipo anillo: arrastre vertical, doble tap = valor por defecto. */
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
        val r = h * 0.34f
        val cx = r + dpf(8f)
        val cy = h / 2f
        oval.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.STROKE
        p.strokeCap = Paint.Cap.ROUND
        p.strokeWidth = dpf(5f)
        p.color = C.KNOB_BG
        c.drawArc(oval, 135f, 270f, false, p)
        p.color = C.ACCENT
        if (spec.bipolar) {
            val sweep = (pos - 0.5f) * 270f
            if (sweep != 0f) c.drawArc(oval, 270f, sweep, false, p)
        } else if (pos > 0f) {
            c.drawArc(oval, 135f, pos * 270f, false, p)
        }
        p.style = Paint.Style.FILL
        p.color = C.KNOB_FACE
        c.drawCircle(cx, cy, r * 0.7f, p)
        val ang = Math.toRadians((135f + pos * 270f).toDouble())
        p.color = C.TEXT
        c.drawCircle(
            cx + (Math.cos(ang) * r * 0.45f).toFloat(),
            cy + (Math.sin(ang) * r * 0.45f).toFloat(), dpf(3f), p
        )
        p.typeface = FONT
        p.textAlign = Paint.Align.LEFT
        val tx = cx + r + dpf(16f)
        p.textSize = dpf(12f)
        p.color = C.TEXT_DIM
        c.drawText(spec.label, tx, cy - dpf(6f), p)
        p.textSize = dpf(17f)
        p.color = C.TEXT
        c.drawText(valueText(), tx, cy + dpf(15f), p)
    }
}

/** Paneo: riel con punto; arrastre horizontal, doble tap = centro. */
class PanView(ctx: Context, private val addr: String) : View(ctx) {
    private var pos = 0.5f
    private var lastX = 0f
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
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

    override fun onDraw(c: Canvas) {
        val pad = dpf(10f)
        val w = width - 2 * pad
        val cy = height / 2f
        val cx = pad + w / 2f
        p.style = Paint.Style.FILL
        p.color = C.METER_BG
        c.drawRect(pad, cy - dpf(2f), pad + w, cy + dpf(2f), p)
        val x = pad + pos * w
        p.color = C.ACCENT
        c.drawRect(minOf(cx, x), cy - dpf(2f), maxOf(cx, x), cy + dpf(2f), p)
        p.color = C.TICK
        c.drawRect(cx - dpf(1f), cy - dpf(6f), cx + dpf(1f), cy + dpf(6f), p)
        p.color = C.TEXT
        c.drawCircle(x, cy, dpf(7f), p)
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        gd.onTouchEvent(e)
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                lastX = e.x
                parent?.requestDisallowInterceptTouchEvent(true)
            }
            MotionEvent.ACTION_MOVE -> {
                val nv = (pos + (e.x - lastX) / maxOf(width - dpf(20f), 1f)).coerceIn(0f, 1f)
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

/** Pantalla METERS: todos los medidores de la consola en una sola vista. */
class AllMetersView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val labels: List<String> = meterLabels()

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
        val n = minOf(labels.size, m.size)
        if (n == 0) return
        val pad = dpf(16f)
        val labelH = dpf(26f)
        val slot = (width - 2 * pad) / n
        val bw = slot * 0.6f
        val top = pad
        val bottom = height - pad - labelH
        p.textSize = dpf(if (n > 50) 9f else 11f)
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
