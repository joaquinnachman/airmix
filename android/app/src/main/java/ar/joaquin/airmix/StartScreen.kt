package ar.joaquin.airmix

import android.content.Context
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.text.InputType
import android.view.Gravity
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout

/**
 * Modo de uso elegido en la pantalla de inicio. Igual en Start.swift.
 * 0 = CONTROL TOTAL (la app de siempre); n ≥ 1 = MÚSICO en el monitor (bus) n.
 */
object Mode {
    var musicianBus = 0
    val musician: Boolean get() = musicianBus > 0
}

/**
 * Pantallas de inicio (diseño aprobado por Joaquín el 26/11/2026):
 *  1) CONEXIÓN: radar buscando consolas, las encontradas en una lista, conectar a mano por IP y DEMO.
 *  2) ¿CÓMO VAS A USAR AIRMIX HOY?: CONTROL TOTAL o MÚSICO (con la lista de monitores de la consola conectada).
 * Va encima de todo; la actividad decide cuándo mostrarla y qué hacer con cada elección.
 */
class StartScreen(ctx: Context) : FrameLayout(ctx) {
    var onConnect: ((ip: String, port: Int, model: String?) -> Unit)? = null
    var onDemo: (() -> Unit)? = null
    var onFull: (() -> Unit)? = null
    var onMusician: ((bus: Int) -> Unit)? = null
    var onRescan: (() -> Unit)? = null
    /** Última consola usada (IP) y último monitor elegido en MÚSICO. */
    var lastIp: String? = null
    var lastBus = 0

    var page = 1
        private set
    /** Se tocó CONECTAR y se espera la respuesta de la consola para pasar a la pantalla 2. */
    var waiting = false
        private set
    private var waitingSince = 0L

    private val seen = HashSet<String>()
    private var foundList: LinearLayout? = null
    private var searching: android.widget.TextView? = null

    init {
        setBackgroundColor(C.BG)
        isClickable = true
        elevation = context.dp(30).toFloat()
    }

    // ------------------------------------------------------------------ navegación

    fun showConnection() {
        page = 1
        waiting = false
        seen.clear()
        removeAllViews()
        addView(buildConnection(), LayoutParams(MATCH, MATCH))
        visibility = View.VISIBLE
        onRescan?.invoke()
    }

    fun showModes() {
        page = 2
        waiting = false
        removeAllViews()
        addView(buildModes(), LayoutParams(MATCH, MATCH))
        visibility = View.VISIBLE
    }

    fun hide() {
        visibility = View.GONE
        removeAllViews()
        waiting = false
    }

    val shown: Boolean get() = visibility == View.VISIBLE

    /** Se llama desde el tick: si se estaba esperando y la consola respondió, pasa a la pantalla 2. */
    fun onConnected() {
        if (shown && page == 1 && waiting) showModes()
    }

    /** Sin respuesta en 6 s: se avisa y se sigue en la pantalla de conexión. */
    fun waitTimedOut(now: Long): Boolean {
        if (!waiting || now - waitingSince < 6000) return false
        waiting = false
        searching?.text = "No responde. ¿La tablet está en el wifi de la consola?"
        searching?.setTextColor(C.RED)
        return true
    }

    fun onDiscovered(d: MixerInfo) {
        val list = foundList ?: return
        if (!shown || page != 1 || !seen.add(d.ip)) return
        list.addView(consoleCard(d.model, d.name, d.ip, d.ip == lastIp) { connect(d.ip, d.port, d.model) }, 0, lp(MATCH, context.dp(sz(92, 62))).m(b = context.dp(sz(10, 6))))
    }

    private fun connect(ip: String, port: Int, model: String?) {
        waiting = true
        waitingSince = android.os.SystemClock.uptimeMillis()
        searching?.text = "Conectando a $ip…"
        searching?.setTextColor(C.SENDS)
        onConnect?.invoke(ip, port, model)
    }

    // ------------------------------------------------------------------ pantalla 1: conexión

    private fun topBar(right: View?): View = with(context) {
        val top = row()
        val logo = ImageView(this)
        logo.setImageResource(R.drawable.logo_lockup)
        logo.scaleType = ImageView.ScaleType.FIT_START
        logo.contentDescription = "AirMix"
        top.addView(logo, lp(dp(sz(150, 96)), dp(sz(34, 22))))
        val ver = try { packageManager.getPackageInfo(packageName, 0).versionName ?: "" } catch (_: Exception) { "" }
        top.addView(label(ver, sz(14f, 11f), C.TEXT_DIM, Gravity.START or Gravity.BOTTOM), lp(dp(60), dp(sz(30, 20))).m(l = dp(6)))
        top.addView(View(this), lp(0, 1, 1f))
        if (right != null) top.addView(right, lp(WRAP, dp(sz(44, 32))))
        top
    }

    private fun buildConnection(): View = with(context) {
        val root = col()
        root.setPadding(dp(sz(40, 20)), dp(sz(24, 10)), dp(sz(40, 20)), dp(sz(24, 10)))
        root.addView(topBar(null), lp(MATCH, WRAP))

        val body = row()
        body.gravity = Gravity.CENTER_VERTICAL
        // Izquierda: radar.
        val left = col()
        left.gravity = Gravity.CENTER
        left.addView(RadarView(this), lp(dp(sz(300, 140)), dp(sz(300, 140))))
        val st = label("BUSCANDO CONSOLAS EN LA RED…", sz(22f, 14f), C.ACCENT)
        st.maxLines = 2
        searching = st
        left.addView(st, lp(MATCH, WRAP).m(t = dp(sz(20, 8))))
        if (!Layout.compact) left.addView(label("Conectá la tablet al wifi de la consola\no al mismo router que la consola.", 15f, C.TEXT_DIM).also { it.maxLines = 2 }, lp(MATCH, WRAP).m(t = dp(6)))
        body.addView(left, lp(0, MATCH, if (Layout.compact) 0.8f else 1f))

        // Derecha: encontradas + a mano.
        val right = col()
        right.gravity = Gravity.CENTER_VERTICAL
        right.addView(sectionTitle("CONSOLAS ENCONTRADAS"), lp(MATCH, dp(sz(26, 18))))
        val list = col()
        foundList = list
        val ip = lastIp
        if (!ip.isNullOrEmpty()) {
            seen.add(ip)
            list.addView(consoleCard(App.console.model, "Última usada", ip, true) { connect(ip, 0, null) }, lp(MATCH, dp(sz(92, 62))).m(b = dp(sz(10, 6))))
        }
        val lsv = android.widget.ScrollView(this)
        lsv.isVerticalScrollBarEnabled = false
        lsv.addView(list, FrameLayout.LayoutParams(MATCH, WRAP))
        right.addView(lsv, lp(MATCH, 0, 1f).m(t = dp(6)))
        right.addView(divider(), lp(MATCH, 1).m(t = dp(sz(14, 6)), b = dp(sz(14, 6))))
        right.addView(sectionTitle("¿NO APARECE? CONECTÁ A MANO"), lp(MATCH, dp(sz(26, 18))))
        if (!Layout.compact) right.addView(label("Escribí la IP de la consola (en la XR12, de fábrica es 192.168.1.1).", 14f, C.TEXT_DIM, Gravity.START), lp(MATCH, dp(24)))
        val manual = row()
        val edit = EditText(this)
        edit.hint = "192.168.1.1"
        edit.setText(lastIp ?: "")
        edit.setTextColor(C.TEXT)
        edit.setHintTextColor(C.TICK)
        edit.textSize = sz(20f, 15f)
        edit.typeface = FONT
        edit.background = rounded(C.STRIP, 12, C.LINE)
        edit.setPadding(dp(14), 0, dp(14), 0)
        edit.setSingleLine()
        edit.inputType = InputType.TYPE_CLASS_TEXT or InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS
        edit.imeOptions = EditorInfo.IME_ACTION_GO
        val go = Btn(this, "CONECTAR", BtnStyle.OUTLINE, sz(17f, 13f))
        val doManual = {
            val t = edit.text.toString().trim()
            if (t.isNotEmpty()) connect(t, 0, null)
        }
        go.setOnClickListener { doManual() }
        edit.setOnEditorActionListener { _, _, _ -> doManual(); true }
        manual.addView(edit, lp(0, dp(sz(54, 40)), 1f))
        manual.addView(go, lp(dp(sz(150, 104)), dp(sz(54, 40))).m(l = dp(10)))
        right.addView(manual, lp(MATCH, WRAP).m(t = dp(sz(8, 4))))
        body.addView(right, lp(0, MATCH, 1f).m(l = dp(sz(48, 18))))
        root.addView(body, lp(MATCH, 0, 1f).m(t = dp(sz(10, 4)), b = dp(sz(10, 4))))

        val bottom = row()
        bottom.gravity = Gravity.CENTER_VERTICAL
        val again = Btn(this, "↻  BUSCAR DE NUEVO", BtnStyle.OUTLINE, sz(16f, 12f))
        again.setOnClickListener {
            seen.clear()
            list.removeAllViews()
            if (!lastIp.isNullOrEmpty()) {
                val l = lastIp!!
                seen.add(l)
                list.addView(consoleCard(App.console.model, "Última usada", l, true) { connect(l, 0, null) }, lp(MATCH, dp(sz(92, 62))).m(b = dp(sz(10, 6))))
            }
            searching?.text = "BUSCANDO CONSOLAS EN LA RED…"
            searching?.setTextColor(C.ACCENT)
            onRescan?.invoke()
        }
        bottom.addView(again, lp(dp(sz(220, 150)), dp(sz(52, 38))))
        bottom.addView(label("X32 · M32 · XR12 · XR16 · XR18 · X18 · MR12 · MR18", sz(15f, 11f), C.TICK).also { it.maxLines = 1 }, lp(0, WRAP, 1f))
        val demo = Btn(this, "▶  PROBAR SIN CONSOLA (DEMO)", BtnStyle.OUTLINE, sz(16f, 12f))
        demo.setTextColor(C.ACCENT)
        demo.setOnClickListener { onDemo?.invoke() }
        bottom.addView(demo, lp(dp(sz(320, 210)), dp(sz(52, 38))))
        root.addView(bottom, lp(MATCH, WRAP))
        root
    }

    private fun consoleCard(model: String, name: String, ip: String, last: Boolean, onTap: () -> Unit): View = with(context) {
        val card = row()
        card.gravity = Gravity.CENTER_VERTICAL
        card.background = rounded(C.PANEL, 16, if (last) C.SENDS else C.LINE)
        card.setPadding(dp(sz(16, 10)), 0, dp(sz(14, 8)), 0)
        card.isClickable = true
        card.setOnClickListener { onTap() }
        val badge = label(model.uppercase(), sz(20f, 14f), C.SENDS)
        badge.background = rounded(Color.parseColor("#0C2A36"), 10)
        card.addView(badge, lp(dp(sz(74, 52)), dp(sz(54, 38))))
        val txt = col()
        txt.gravity = Gravity.CENTER_VERTICAL
        txt.addView(label(name.ifEmpty { model }, sz(22f, 16f), C.TEXT, Gravity.START).also { it.maxLines = 1 }, lp(MATCH, WRAP))
        txt.addView(label(if (last) "$ip  ·  última usada" else ip, sz(14f, 11f), C.TEXT_DIM, Gravity.START).also { it.maxLines = 1 }, lp(MATCH, WRAP))
        card.addView(txt, lp(0, WRAP, 1f).m(l = dp(sz(16, 10))))
        val b = Btn(this, "CONECTAR", BtnStyle.LIGHT, sz(17f, 13f))
        b.activeColor = C.SENDS
        b.active = true
        b.setOnClickListener { onTap() }
        card.addView(b, lp(dp(sz(140, 100)), dp(sz(52, 40))))
        card
    }

    // ------------------------------------------------------------------ pantalla 2: modo

    private fun buildModes(): View = with(context) {
        val root = col()
        root.setPadding(dp(sz(40, 16)), dp(sz(24, 8)), dp(sz(40, 16)), dp(sz(20, 8)))

        val chip = row()
        chip.gravity = Gravity.CENTER_VERTICAL
        val info = if (Demo.on) "▶ MODO DEMO · ${App.console.model}"
        else listOf(App.console.model, App.mixerName, App.client?.host?.hostAddress ?: "").filter { it.isNotEmpty() }.joinToString("  ·  ")
        val dot = label("●", sz(16f, 12f), if (Demo.on) C.YELLOW else C.GREEN)
        chip.addView(dot, lp(dp(sz(26, 18)), WRAP))
        chip.addView(label(info, sz(17f, 13f), C.TEXT, Gravity.START).also { it.maxLines = 1 }, lp(WRAP, WRAP))
        chip.background = rounded(Color.parseColor("#0F1F17"), 12, Color.parseColor("#1F5A3D"))
        chip.setPadding(dp(12), 0, dp(14), 0)
        val right = row()
        right.gravity = Gravity.CENTER_VERTICAL
        right.addView(chip, lp(WRAP, MATCH))
        val change = Btn(this, if (Demo.on) "SALIR DEL DEMO" else "CAMBIAR CONSOLA", BtnStyle.OUTLINE, sz(15f, 12f))
        change.setOnClickListener {
            if (Demo.on) onDemo?.invoke() else showConnection()
        }
        right.addView(change, lp(dp(sz(190, 130)), MATCH).m(l = dp(10)))
        root.addView(topBar(right), lp(MATCH, WRAP))

        root.addView(label("¿CÓMO VAS A USAR AIRMIX HOY?", sz(32f, 18f), C.TEXT), lp(MATCH, WRAP).m(t = dp(sz(18, 4)), b = dp(sz(18, 6))))

        val cards = row()
        // CONTROL TOTAL
        val full = modeCard(C.ACCENT, Color.parseColor("#17130F"))
        full.addView(ConsoleArt(this), lp(dp(sz(190, 92)), dp(sz(100, 48))))
        modeTexts(full, "CONTROL TOTAL", "OPERADOR · SONIDISTA · TÉCNICO", "Toda la consola: la sala, los monitores, efectos, escenas y SALA. La app de siempre.", C.ACCENT, Color.parseColor("#D9B9A6"), Color.parseColor("#B9A99D"))
        full.addView(View(this), lp(1, 0, 1f))
        val enter = Btn(this, "ENTRAR  →", BtnStyle.LIGHT, sz(26f, 19f))
        enter.activeColor = C.ACCENT
        enter.active = true
        enter.setOnClickListener { onFull?.invoke() }
        full.addView(enter, lp(MATCH, dp(sz(76, 50))))
        cards.addView(full, lp(0, MATCH, 1f).m(r = dp(sz(12, 6))))

        // MÚSICO
        val mus = modeCard(C.SENDS, Color.parseColor("#0F171D"))
        mus.addView(HeadphonesArt(this), lp(dp(sz(130, 62)), dp(sz(100, 48))))
        modeTexts(mus, "MÚSICO", "MONITORES · ASISTENTE · SOLO MEZCLAS", "Tocá tu monitor y entrás directo. La sala no se toca.", C.SENDS, Color.parseColor("#A9D4E6"), Color.parseColor("#8FA6B2"))
        mus.addView(View(this), lp(1, 0, 1f))
        val n = App.console.buses
        val cols = when {
            n <= 2 -> 1
            n <= 6 -> 2
            else -> 4
        }
        val grid = col()
        var row: LinearLayout? = null
        for (b in 1..n) {
            if ((b - 1) % cols == 0) {
                row = row()
                grid.addView(row, lp(MATCH, 0, 1f).m(t = if (b == 1) 0 else dp(sz(8, 4))))
            }
            row!!.addView(monitorTile(b, n), lp(0, MATCH, 1f).m(l = if ((b - 1) % cols == 0) 0 else dp(sz(8, 4))))
        }
        val rows = (n + cols - 1) / cols
        val tileH = when {
            n <= 2 -> sz(96, 54)
            n <= 6 -> sz(72, 40)
            else -> sz(56, 30)
        }
        mus.addView(grid, lp(MATCH, dp(rows * tileH + (rows - 1) * sz(8, 4))))
        cards.addView(mus, lp(0, MATCH, 1f).m(l = dp(sz(12, 6))))
        root.addView(cards, lp(MATCH, 0, 1f))

        if (!Layout.compact) root.addView(
            label("Aparecen los envíos que tiene la consola conectada, con el nombre y el color que tienen en la consola.", 13f, C.TICK),
            lp(MATCH, WRAP).m(t = dp(12))
        )
        root
    }

    private fun modeCard(border: Int, bg: Int): LinearLayout = with(context) {
        val c = col()
        c.gravity = Gravity.CENTER_HORIZONTAL
        c.background = rounded(bg, 20, border).also { it.setStroke(dp(2), border) }
        c.setPadding(dp(sz(26, 12)), dp(sz(22, 8)), dp(sz(26, 12)), dp(sz(22, 10)))
        c
    }

    private fun modeTexts(c: LinearLayout, title: String, sub: String, text: String, tc: Int, sc: Int, xc: Int) = with(context) {
        c.addView(label(title, sz(40f, 22f), tc).also { it.letterSpacing = 0.04f }, lp(MATCH, WRAP).m(t = dp(sz(8, 2))))
        c.addView(label(sub, sz(17f, 11f), sc).also { it.letterSpacing = 0.08f; it.maxLines = 1 }, lp(MATCH, WRAP).m(t = dp(2)))
        val t = label(text, sz(15f, 11f), xc)
        t.typeface = FONT_MEDIUM
        t.maxLines = 2
        c.addView(t, lp(MATCH, WRAP).m(t = dp(sz(8, 2))))
    }

    /** "MONITOR n" (color del bus) arriba y el nombre que tiene en la consola abajo, en grande. */
    private fun monitorTile(bus: Int, total: Int): View = with(context) {
        val s = Strip(Kind.BUS, bus)
        val t = col()
        t.gravity = Gravity.CENTER
        t.isClickable = true
        val isLast = bus == lastBus
        val bg = rounded(if (isLast) Color.parseColor("#12303D") else Color.parseColor("#141C23"), 14, if (isLast) C.SENDS else Color.parseColor("#24313A"))
        t.background = bg
        val big = total <= 6
        val top = label(if (isLast) "MONITOR $bus  ·  ÚLTIMO" else "MONITOR $bus", if (big) sz(14f, 10f) else sz(11f, 9f), C.SENDS)
        top.letterSpacing = 0.12f
        top.maxLines = 1
        t.addView(top, lp(MATCH, WRAP))
        val name = label("SIN NOMBRE", if (total <= 2) sz(32f, 18f) else if (big) sz(26f, 15f) else sz(17f, 11f), C.TICK)
        name.fitOneLine(9)
        name.bind(s.nameAddr) { v ->
            val n = v.toString().trim()
            name.text = if (n.isEmpty()) "SIN NOMBRE" else n.uppercase()
            name.setTextColor(if (n.isEmpty()) C.TICK else C.TEXT)
        }
        t.addView(name, lp(MATCH, WRAP))
        top.bind(s.colorAddr) { v -> top.setTextColor(BusColor.of(v.asI())) }
        t.setOnClickListener { onMusician?.invoke(bus) }
        t
    }
}

/** Radar de búsqueda: un barrido que gira. Sin asignaciones en onDraw. */
class RadarView(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val arc = RectF()
    private var t0 = 0L

    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        t0 = android.os.SystemClock.uptimeMillis()
    }

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val cx = w / 2f
        val cy = h / 2f
        val r = minOf(w, h) / 2f - dpf(3f)
        val now = android.os.SystemClock.uptimeMillis() - t0
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#121519")
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1f)
        p.color = Color.parseColor("#23272F")
        c.drawCircle(cx, cy, r * 0.66f, p)
        c.drawCircle(cx, cy, r * 0.33f, p)
        c.drawLine(cx - r, cy, cx + r, cy, p)
        c.drawLine(cx, cy - r, cx, cy + r, p)
        // Barrido: abanico que se desvanece detrás de la línea.
        val ang = (now % 3200L) / 3200f * 360f
        arc.set(cx - r, cy - r, cx + r, cy + r)
        p.style = Paint.Style.FILL
        for (k in 0 until 12) {
            p.color = C.ACCENT
            p.alpha = (90 - k * 7).coerceAtLeast(0)
            c.drawArc(arc, ang - 90f - (k + 1) * 6f, 6f, true, p)
        }
        p.alpha = 255
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(2f)
        p.color = C.ACCENT
        val rad = Math.toRadians((ang - 90f).toDouble())
        c.drawLine(cx, cy, cx + r * Math.cos(rad).toFloat(), cy + r * Math.sin(rad).toFloat(), p)
        // Onda que se expande.
        val pr = (now % 2400L) / 2400f
        p.alpha = ((1f - pr) * 200).toInt()
        c.drawCircle(cx, cy, r * (0.35f + 0.65f * pr), p)
        p.alpha = 255
        p.strokeWidth = dpf(2f)
        p.color = Color.parseColor("#2A2F38")
        c.drawCircle(cx, cy, r, p)
        p.style = Paint.Style.FILL
        p.color = C.TEXT
        c.drawCircle(cx, cy, dpf(5f), p)
        if (isShown) postInvalidateOnAnimation()
    }
}

/** Dibujo de CONTROL TOTAL: una consola con faders. */
class ConsoleArt(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()
    private val pos = floatArrayOf(0.35f, 0.2f, 0.55f, 0.28f, 0.45f, 0.12f)

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        p.style = Paint.Style.FILL
        p.color = Color.parseColor("#211C17")
        r.set(dpf(2f), dpf(2f), w - dpf(2f), h - dpf(2f))
        c.drawRoundRect(r, dpf(10f), dpf(10f), p)
        p.style = Paint.Style.STROKE
        p.strokeWidth = dpf(1.5f)
        p.color = Color.parseColor("#3A332B")
        c.drawRoundRect(r, dpf(10f), dpf(10f), p)
        val n = pos.size
        val top = h * 0.14f
        val bot = h * 0.86f
        val capW = w / n * 0.5f
        val capH = h * 0.12f
        for (i in 0 until n) {
            val x = w * (i + 0.5f) / n
            p.style = Paint.Style.STROKE
            p.strokeWidth = dpf(3f)
            p.strokeCap = Paint.Cap.ROUND
            p.color = Color.parseColor("#463D33")
            c.drawLine(x, top, x, bot, p)
            val y = top + (bot - top) * pos[i]
            p.style = Paint.Style.FILL
            p.color = if (i == n - 1) Color.WHITE else C.CAP
            r.set(x - capW / 2f, y, x + capW / 2f, y + capH)
            c.drawRoundRect(r, dpf(3f), dpf(3f), p)
            if (i != n - 1) {
                p.color = C.ACCENT
                c.drawRect(x - capW / 2f + dpf(3f), y + capH / 2f - dpf(1f), x + capW / 2f - dpf(3f), y + capH / 2f + dpf(1f), p)
            }
        }
    }
}

/** Dibujo de MÚSICO: auriculares in-ear y una nota. */
class HeadphonesArt(ctx: Context) : View(ctx) {
    private val p = Paint(Paint.ANTI_ALIAS_FLAG)
    private val r = RectF()

    override fun onDraw(c: Canvas) {
        val w = width.toFloat()
        val h = height.toFloat()
        val s = minOf(w / 104f, h / 80f)
        val ox = (w - 104f * s) / 2f
        val oy = (h - 80f * s) / 2f
        p.style = Paint.Style.STROKE
        p.strokeWidth = 5f * s
        p.strokeCap = Paint.Cap.ROUND
        p.color = C.TEXT
        r.set(ox + 12f * s, oy + 10f * s, ox + 80f * s, oy + 82f * s)
        c.drawArc(r, 180f, 180f, false, p)
        p.style = Paint.Style.FILL
        p.color = C.SENDS
        r.set(ox + 4f * s, oy + 40f * s, ox + 22f * s, oy + 70f * s)
        c.drawRoundRect(r, 8f * s, 8f * s, p)
        r.set(ox + 70f * s, oy + 40f * s, ox + 88f * s, oy + 70f * s)
        c.drawRoundRect(r, 8f * s, 8f * s, p)
        p.color = Color.parseColor("#8FD3EF")
        c.drawCircle(ox + 92f * s, oy + 34f * s, 4f * s, p)
        c.drawRect(ox + 94.5f * s, oy + 16f * s, ox + 96.5f * s, oy + 34f * s, p)
        c.drawRect(ox + 94.5f * s, oy + 16f * s, ox + 103f * s, oy + 19f * s, p)
    }
}
