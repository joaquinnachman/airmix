package ar.joaquin.airmix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.view.MotionEvent
import android.view.View

/** Coeficientes biquad (RBJ cookbook) solo para dibujar la curva del EQ. */
object Biquad {
    fun design(c: DoubleArray, type: Int, f0: Double, gainDb: Double, q: Double) {
        val w0 = 2.0 * Math.PI * f0 / 48000.0
        val cw = Math.cos(w0)
        val sw = Math.sin(w0)
        val alpha = sw / (2.0 * q)
        val a = Math.pow(10.0, gainDb / 40.0)
        val sa = Math.sqrt(a)
        when (type) {
            0 -> set(c, (1 + cw) / 2, -(1 + cw), (1 + cw) / 2, 1 + alpha, -2 * cw, 1 - alpha) // LCut
            5 -> set(c, (1 - cw) / 2, 1 - cw, (1 - cw) / 2, 1 + alpha, -2 * cw, 1 - alpha) // HCut
            1 -> set( // LShv
                c,
                a * ((a + 1) - (a - 1) * cw + 2 * sa * alpha),
                2 * a * ((a - 1) - (a + 1) * cw),
                a * ((a + 1) - (a - 1) * cw - 2 * sa * alpha),
                (a + 1) + (a - 1) * cw + 2 * sa * alpha,
                -2 * ((a - 1) + (a + 1) * cw),
                (a + 1) + (a - 1) * cw - 2 * sa * alpha
            )
            4 -> set( // HShv
                c,
                a * ((a + 1) + (a - 1) * cw + 2 * sa * alpha),
                -2 * a * ((a - 1) + (a + 1) * cw),
                a * ((a + 1) + (a - 1) * cw - 2 * sa * alpha),
                (a + 1) - (a - 1) * cw + 2 * sa * alpha,
                2 * ((a - 1) - (a + 1) * cw),
                (a + 1) - (a - 1) * cw - 2 * sa * alpha
            )
            else -> set(c, 1 + alpha * a, -2 * cw, 1 - alpha * a, 1 + alpha / a, -2 * cw, 1 - alpha / a) // PEQ/VEQ
        }
    }

    private fun set(c: DoubleArray, b0: Double, b1: Double, b2: Double, a0: Double, a1: Double, a2: Double) {
        c[0] = b0; c[1] = b1; c[2] = b2; c[3] = a0; c[4] = a1; c[5] = a2
    }

    fun magDb(c: DoubleArray, w: Double): Double {
        val cw = Math.cos(w)
        val c2w = Math.cos(2 * w)
        val num = c[0] * c[0] + c[1] * c[1] + c[2] * c[2] + 2 * (c[0] * c[1] + c[1] * c[2]) * cw + 2 * c[0] * c[2] * c2w
        val den = c[3] * c[3] + c[4] * c[4] + c[5] * c[5] + 2 * (c[3] * c[4] + c[4] * c[5]) * cw + 2 * c[3] * c[5] * c2w
        if (num <= 0 || den <= 0) return -60.0
        return 10 * Math.log10(num / den)
    }
}

/**
 * Gráfico del EQ con puntos arrastrables: horizontal = frecuencia, vertical = ganancia.
 * Banda 0 = LOCUT (filtro pasa-altos del preamp), solo en canales.
 */
class EqGraphView(
    ctx: Context, private val p: String, private val bands: Int, private val hpf: Boolean
) : View(ctx) {
    var selected = 1
    var onSelect: ((Int) -> Unit)? = null

    /** RTA de fondo (lo redibuja el tick de 30 fps mientras está prendido). */
    var rtaOn = false
        set(v) {
            field = v
            if (v && isAttachedToWindow) App.meterViews.add(this) else App.meterViews.remove(this)
            if (!v && fbBin >= 0) {
                fbBin = -1
                java.util.Arrays.fill(fbHits, 0)
                onFeedback?.invoke(0f)
            }
            invalidate()
        }
    private val rtaPath = Path()

    /**
     * Detector de acople: una banda del RTA que sobresale mucho de sus vecinas y se sostiene ~0,7 s.
     * onFeedback recibe la frecuencia (Hz) o 0 cuando ya no hay.
     */
    var onFeedback: ((Float) -> Unit)? = null
    private val fbHits = IntArray(MixerClient.RTA_BINS)
    private var fbBin = -1
    private var fbLabel = ""

    private fun detectFeedback(m: FloatArray) {
        val n = minOf(m.size, fbHits.size)
        var best = -1
        var bestLevel = -200f
        for (i in 0 until n) {
            if (i < 3 || i > n - 4) { fbHits[i] = 0; continue }
            val around = (m[i - 3] + m[i - 2] + m[i + 2] + m[i + 3]) / 4f
            val peak = m[i] > -45f && m[i] >= m[i - 1] && m[i] >= m[i + 1] && m[i] - around >= 12f
            fbHits[i] = if (peak) minOf(fbHits[i] + 1, 90) else maxOf(fbHits[i] - 2, 0)
            if (fbHits[i] >= 20 && m[i] > bestLevel) {
                bestLevel = m[i]
                best = i
            }
        }
        if (best != fbBin) {
            fbBin = best
            val hz = if (best >= 0) binHz(best) else 0f
            fbLabel = if (best >= 0) "ACOPLE " + shortHz(hz) else ""
            onFeedback?.invoke(hz)
        }
    }

    private fun binHz(i: Int): Float = (20.0 * Math.pow(1000.0, i / (MixerClient.RTA_BINS - 1.0))).toFloat()

    private fun shortHz(hz: Float): String =
        if (hz >= 1000f) String.format(java.util.Locale.US, "%.1fk", hz / 1000f) else String.format(java.util.Locale.US, "%.0f", hz)

    private val n = 160
    private val curve = FloatArray(n)
    private var dirty = true
    private val coef = Array(bands + 1) { DoubleArray(6) }
    private val active = BooleanArray(bands + 1)
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()
    private var dragBand = -1

    private val onA = "$p/eq/on"
    private val typeA = Array(bands + 1) { "$p/eq/$it/type" }
    private val fA = Array(bands + 1) { "$p/eq/$it/f" }
    private val gA = Array(bands + 1) { "$p/eq/$it/g" }
    private val qA = Array(bands + 1) { "$p/eq/$it/q" }
    private val hpOnA = "$p/preamp/hpon"
    private val hpFA = "$p/preamp/hpf"

    private val dotColors = intArrayOf(
        Color.parseColor("#2F6FD6"), Color.parseColor("#1AA6A6"), Color.parseColor("#C9B81E"),
        Color.parseColor("#B01EB5"), Color.parseColor("#D6731E"), Color.parseColor("#3FB64A"),
        Color.parseColor("#D63A3A")
    )
    private val gridHz = floatArrayOf(
        20f, 40f, 60f, 80f, 100f, 200f, 300f, 400f, 500f, 600f, 800f, 1000f,
        2000f, 3000f, 4000f, 5000f, 6000f, 8000f, 10000f, 20000f
    )
    private val gridLbl = arrayOf(
        "20", "40", "60", "80", "100", "200", "300", "400", "500", "600", "800", "1k",
        "2k", "3k", "4k", "5k", "6k", "8k", "10k", "20k"
    )

    private var gl = 0f
    private var gt = 0f
    private var gw = 1f
    private var gh = 1f
    private var dx = 0f
    private var dy = 0f

    init {
        val inv: (Any) -> Unit = {
            dirty = true
            invalidate()
        }
        bind(onA, inv)
        for (b in 1..bands) {
            bind(typeA[b], inv); bind(fA[b], inv); bind(gA[b], inv); bind(qA[b], inv)
        }
        if (hpf) {
            bind(hpOnA, inv); bind(hpFA, inv)
        }
    }

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        if (rtaOn) App.meterViews.add(this)
    }

    override fun onDetachedFromWindow() {
        App.meterViews.remove(this)
        super.onDetachedFromWindow()
    }

    /** Espectro: 100 bandas log de 20 Hz a 20 kHz, -80..0 dB de abajo hacia arriba. */
    private fun drawRta(c: Canvas) {
        val m = App.client?.rta ?: return
        val n = m.size
        rtaPath.reset()
        rtaPath.moveTo(gl, gt + gh)
        for (i in 0 until n) {
            val x = gl + gw * i / (n - 1)
            val y = gt + gh * (1f - ((m[i] + 80f) / 80f).coerceIn(0f, 1f))
            rtaPath.lineTo(x, y)
        }
        rtaPath.lineTo(gl + gw, gt + gh)
        rtaPath.close()
        paint.style = Paint.Style.FILL
        paint.color = C.SENDS
        paint.alpha = 60
        c.drawPath(rtaPath, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1f)
        paint.alpha = 150
        c.drawPath(rtaPath, paint)
        paint.alpha = 255
        detectFeedback(m)
        if (fbBin >= 0) {
            val x = gl + gw * fbBin / (n - 1)
            paint.color = C.RED
            paint.strokeWidth = dpf(2f)
            c.drawLine(x, gt, x, gt + gh, paint)
            paint.style = Paint.Style.FILL
            paint.textSize = dpf(13f)
            paint.typeface = FONT
            paint.textAlign = if (x > gl + gw * 0.8f) Paint.Align.RIGHT else Paint.Align.LEFT
            c.drawText(fbLabel, x + if (paint.textAlign == Paint.Align.LEFT) dpf(5f) else -dpf(5f), gt + dpf(16f), paint)
        }
    }

    override fun onSizeChanged(w: Int, h: Int, ow: Int, oh: Int) {
        gl = dpf(30f)
        gt = dpf(10f)
        gw = maxOf(w - gl - dpf(10f), 1f)
        gh = maxOf(h - gt - dpf(26f), 1f)
    }

    private fun xHz(hz: Float) = gl + Fmt.logInv(20f, 20000f, hz) * gw
    private fun yDb(db: Float) = gt + (1f - (db + 15f) / 30f) * gh

    private fun recompute() {
        val st = App.store
        val eqOn = st.int(onA, 0) != 0
        for (b in 1..bands) {
            val type = st.int(typeA[b], 2)
            val f = Fmt.log(20f, 20000f, st.float(fA[b], 0.5f)).toDouble()
            val g = (-15f + 30f * st.float(gA[b], 0.5f)).toDouble()
            val q = Fmt.log(10f, 0.3f, st.float(qA[b], 0.5f)).toDouble()
            active[b] = eqOn
            Biquad.design(coef[b], type, f, g, q)
        }
        active[0] = hpf && st.int(hpOnA, 0) != 0
        if (hpf) Biquad.design(coef[0], 0, Fmt.log(20f, 400f, st.float(hpFA, 0f)).toDouble(), 0.0, 0.707)
        for (i in 0 until n) {
            val fr = 20.0 * Math.pow(1000.0, i / (n - 1.0))
            val w = 2 * Math.PI * fr / 48000.0
            var sum = 0.0
            for (b in 0..bands) if (active[b]) sum += Biquad.magDb(coef[b], w)
            curve[i] = sum.toFloat()
        }
        dirty = false
    }

    private fun dotPos(b: Int) {
        val st = App.store
        if (b == 0) {
            dx = xHz(Fmt.log(20f, 400f, st.float(hpFA, 0f)))
            dy = yDb(0f)
            return
        }
        dx = gl + st.float(fA[b], 0.5f) * gw
        val type = st.int(typeA[b], 2)
        dy = if (type == 0 || type == 5) yDb(0f) else yDb(-15f + 30f * st.float(gA[b], 0.5f))
    }

    override fun onDraw(c: Canvas) {
        if (dirty) recompute()
        paint.style = Paint.Style.FILL
        paint.color = C.GRAPH
        c.drawRect(gl, gt, gl + gw, gt + gh, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1f)
        paint.color = C.GRID
        for (hz in gridHz) {
            val x = xHz(hz)
            c.drawLine(x, gt, x, gt + gh, paint)
        }
        var db = -15
        while (db <= 15) {
            val y = yDb(db.toFloat())
            c.drawLine(gl, y, gl + gw, y, paint)
            db += 5
        }
        paint.style = Paint.Style.FILL
        paint.typeface = FONT
        paint.textSize = dpf(11f)
        paint.color = C.TEXT_DIM
        paint.textAlign = Paint.Align.CENTER
        for (i in gridHz.indices) c.drawText(gridLbl[i], xHz(gridHz[i]), gt + gh + dpf(17f), paint)
        paint.textAlign = Paint.Align.RIGHT
        db = -15
        while (db <= 15) {
            c.drawText(db.toString(), gl - dpf(5f), yDb(db.toFloat()) + dpf(4f), paint)
            db += 5
        }

        if (rtaOn) {
            c.save()
            c.clipRect(gl, gt, gl + gw, gt + gh)
            drawRta(c)
            c.restore()
        }

        path.reset()
        for (i in 0 until n) {
            val x = gl + gw * i / (n - 1)
            val y = yDb(curve[i].coerceIn(-18f, 18f))
            if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        c.save()
        c.clipRect(gl, gt, gl + gw, gt + gh)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(2.5f)
        paint.color = if (App.store.int(onA, 0) != 0) Color.WHITE else C.TICK
        c.drawPath(path, paint)
        c.restore()

        val first = if (hpf) 0 else 1
        for (b in first..bands) {
            dotPos(b)
            paint.style = Paint.Style.FILL
            paint.color = dotColors[b % dotColors.size]
            paint.alpha = if (b == selected) 235 else 170
            c.drawCircle(dx, dy, dpf(if (b == selected) 19f else 15f), paint)
            paint.alpha = 255
            if (b == selected) {
                paint.style = Paint.Style.STROKE
                paint.strokeWidth = dpf(2f)
                paint.color = Color.WHITE
                c.drawCircle(dx, dy, dpf(19f), paint)
            }
        }
    }

    private fun nearest(x: Float, y: Float): Int {
        var best = -1
        var bestD = dpf(44f) * dpf(44f)
        val first = if (hpf) 0 else 1
        for (b in first..bands) {
            dotPos(b)
            val d = (dx - x) * (dx - x) + (dy - y) * (dy - y)
            if (d < bestD) {
                bestD = d
                best = b
            }
        }
        return best
    }

    override fun onTouchEvent(e: MotionEvent): Boolean {
        when (e.actionMasked) {
            MotionEvent.ACTION_DOWN -> {
                dragBand = nearest(e.x, e.y)
                if (dragBand >= 0) {
                    parent?.requestDisallowInterceptTouchEvent(true)
                    if (selected != dragBand) {
                        selected = dragBand
                        onSelect?.invoke(dragBand)
                    }
                    invalidate()
                }
            }
            MotionEvent.ACTION_MOVE -> if (dragBand >= 0) {
                val fx = ((e.x - gl) / gw).coerceIn(0f, 1f)
                if (dragBand == 0) {
                    val hz = Fmt.log(20f, 20000f, fx).coerceIn(20f, 400f)
                    App.store.set(hpFA, Fmt.logInv(20f, 400f, hz))
                } else {
                    App.store.set(fA[dragBand], fx)
                    val type = App.store.int(typeA[dragBand], 2)
                    if (type in 1..4) App.store.set(gA[dragBand], (1f - (e.y - gt) / gh).coerceIn(0f, 1f))
                }
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> dragBand = -1
        }
        return true
    }
}

val RATIO_VALUES = floatArrayOf(1.1f, 1.3f, 1.5f, 2f, 2.5f, 3f, 4f, 5f, 7f, 10f, 20f, 100f)

/** Curva de transferencia de gate o compresor. */
class DynGraphView(ctx: Context, p: String, private val gate: Boolean) : View(ctx) {
    private val base = if (gate) "$p/gate" else "$p/dyn"
    private val onA = "$base/on"
    private val thrA = "$base/thr"
    private val modeA = "$base/mode"
    private val ratioA = "$base/ratio"
    private val rangeA = "$base/range"
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val path = Path()

    init {
        val inv: (Any) -> Unit = { invalidate() }
        bind(onA, inv); bind(thrA, inv); bind(modeA, inv)
        if (gate) bind(rangeA, inv) else bind(ratioA, inv)
    }

    private fun transfer(x: Float): Float {
        val st = App.store
        val mode = st.int(modeA, 0)
        return if (gate) {
            val thr = Fmt.lin(-80f, 0f, st.float(thrA, 0f))
            val range = Fmt.lin(3f, 60f, st.float(rangeA, 1f))
            val name = App.console.gateModes.getOrElse(mode) { "GATE" }
            when {
                x >= thr -> x
                name.startsWith("EXP") -> maxOf(thr + (x - thr) * (name.last() - '0'), x - range)
                name == "GATE" -> x - range
                else -> x
            }
        } else {
            val thr = Fmt.lin(-60f, 0f, st.float(thrA, 1f))
            val ratio = RATIO_VALUES[st.int(ratioA, 0).coerceIn(0, RATIO_VALUES.size - 1)]
            if (mode == 0) {
                if (x > thr) thr + (x - thr) / ratio else x
            } else {
                if (x < thr) thr + (x - thr) * ratio else x
            }
        }
    }

    override fun onDraw(c: Canvas) {
        val pad = dpf(4f)
        val l = pad
        val t = pad
        val w = width - 2 * pad
        val h = height - 2 * pad
        paint.style = Paint.Style.FILL
        paint.color = C.GRAPH
        c.drawRect(l, t, l + w, t + h, paint)
        paint.style = Paint.Style.STROKE
        paint.strokeWidth = dpf(1f)
        paint.color = C.GRID
        for (i in 1..7) {
            c.drawLine(l + w * i / 8f, t, l + w * i / 8f, t + h, paint)
            c.drawLine(l, t + h * i / 8f, l + w, t + h * i / 8f, paint)
        }
        val minDb = if (gate) -80f else -60f
        path.reset()
        for (i in 0..80) {
            val x = minDb + (-minDb) * i / 80f
            val y = transfer(x)
            val px = l + (x - minDb) / (-minDb) * w
            val py = (t + (1f - (y - minDb) / (-minDb)) * h).coerceIn(t, t + h)
            if (i == 0) path.moveTo(px, py) else path.lineTo(px, py)
        }
        paint.strokeWidth = dpf(3f)
        paint.color = if (App.store.int(onA, 0) != 0) Color.WHITE else C.TICK
        c.drawPath(path, paint)
    }
}
