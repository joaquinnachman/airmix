package ar.joaquin.airmix

import android.content.Context
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.ScrollView
import android.widget.TextView

/** Paleta AirMix: grafito azulado + coral como acento. */
object C {
    val BG = Color.parseColor("#0F1216")
    val TOP = Color.parseColor("#0B0E11")
    val NAV_BG = Color.parseColor("#0B0E11")
    val PANEL = Color.parseColor("#161B21")
    val SURFACE = Color.parseColor("#1C222A")
    val SURFACE2 = Color.parseColor("#252D37")
    val LINE = Color.parseColor("#2E3743")
    val GRAPH = Color.parseColor("#0C0F13")
    val STRIP = Color.parseColor("#141920")
    val ACCENT = Color.parseColor("#FF7A45")
    val SENDS = Color.parseColor("#4CC9F0")
    val MUTE = Color.parseColor("#E5484D")
    val SOLO = Color.parseColor("#F5C542")
    val TEXT = Color.parseColor("#E8EDF2")
    val TEXT_DIM = Color.parseColor("#8A96A3")
    val INK = Color.parseColor("#0B0E11")
    val GRID = Color.parseColor("#1F262F")
    val DIVIDER = Color.parseColor("#2E3743")
    val GREEN = Color.parseColor("#3DD68C")
    val YELLOW = Color.parseColor("#F5C542")
    val RED = Color.parseColor("#E5484D")
    val METER_BG = Color.parseColor("#0A0D10")
    val TICK = Color.parseColor("#4A5562")
    val FILL = Color.parseColor("#5B6775")
    val CAP = Color.parseColor("#E8EDF2")
    val CAP_SHADE = Color.parseColor("#B9C2CC")
    val KNOB_BG = Color.parseColor("#2A323C")
    val KNOB_FACE = Color.parseColor("#1C222A")
}

val FONT: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)
val FONT_MEDIUM: Typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)

const val MATCH = -1
const val WRAP = -2

fun Context.dp(v: Number): Int = (v.toFloat() * resources.displayMetrics.density + 0.5f).toInt()
fun View.dpf(v: Float): Float = v * resources.displayMetrics.density

fun lp(w: Int, h: Int, weight: Float = 0f) = LinearLayout.LayoutParams(w, h, weight)
fun LinearLayout.LayoutParams.m(l: Int = 0, t: Int = 0, r: Int = 0, b: Int = 0): LinearLayout.LayoutParams {
    setMargins(l, t, r, b)
    return this
}

fun Context.label(s: String, sp: Float, color: Int = C.TEXT, gravity: Int = Gravity.CENTER): TextView =
    TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        setTextColor(color)
        typeface = FONT
        this.gravity = gravity
        includeFontPadding = false
        maxLines = 1
    }

fun Context.row(): LinearLayout = LinearLayout(this).apply {
    orientation = LinearLayout.HORIZONTAL
    gravity = Gravity.CENTER_VERTICAL
}

fun Context.col(): LinearLayout = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }

fun Context.divider(): View = View(this).apply {
    setBackgroundColor(C.DIVIDER)
    layoutParams = lp(MATCH, dp(1)).m(0, dp(16), 0, dp(16))
}

fun Context.scroll(v: View): ScrollView = ScrollView(this).apply {
    isFillViewport = true
    isVerticalScrollBarEnabled = false
    addView(v, FrameLayout.LayoutParams(MATCH, WRAP))
}

fun Context.rounded(color: Int, radiusDp: Int = 10, strokeColor: Int = 0): GradientDrawable =
    GradientDrawable().apply {
        setColor(color)
        cornerRadius = dp(radiusDp).toFloat()
        if (strokeColor != 0) setStroke(dp(1), strokeColor)
    }

/** Card: superficie redondeada para agrupar controles. */
fun Context.card(): LinearLayout = col().apply {
    background = rounded(C.SURFACE, 14)
    setPadding(dp(18), dp(14), dp(18), dp(14))
}

fun Context.sectionTitle(s: String): TextView = label(s, 13f, C.TEXT_DIM, Gravity.START or Gravity.CENTER_VERTICAL).apply {
    letterSpacing = 0.12f
}

enum class BtnStyle { NAV, TAB, LIGHT, MUTE, SMALL, OUTLINE }

class Btn(ctx: Context, label: String, private val style: BtnStyle = BtnStyle.LIGHT, sp: Float = 16f) : TextView(ctx) {
    var activeColor = C.ACCENT
    var active = false
        set(v) {
            field = v
            refresh()
        }
    private val bg = GradientDrawable()
    private val strokeW = ctx.dp(1)

    init {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = FONT
        gravity = Gravity.CENTER
        includeFontPadding = false
        bg.cornerRadius = ctx.dp(if (style == BtnStyle.TAB) 8 else 10).toFloat()
        background = bg
        isClickable = true
        refresh()
    }

    fun refresh() {
        val b: Int
        val t: Int
        var stroke = 0
        if (active) {
            b = if (style == BtnStyle.TAB) Color.TRANSPARENT else activeColor
            t = when {
                style == BtnStyle.TAB -> activeColor
                activeColor == C.MUTE -> Color.WHITE
                else -> C.INK
            }
            if (style == BtnStyle.TAB) stroke = activeColor
        } else {
            when (style) {
                BtnStyle.NAV -> { b = Color.TRANSPARENT; t = C.TEXT_DIM }
                BtnStyle.TAB -> { b = Color.TRANSPARENT; t = C.TEXT_DIM }
                BtnStyle.LIGHT -> { b = C.SURFACE2; t = C.TEXT }
                BtnStyle.MUTE -> { b = C.SURFACE2; t = C.TEXT; stroke = C.LINE }
                BtnStyle.SMALL -> { b = C.SURFACE2; t = C.TEXT_DIM }
                BtnStyle.OUTLINE -> { b = Color.TRANSPARENT; t = C.TEXT; stroke = C.LINE }
            }
        }
        bg.setColor(b)
        bg.setStroke(if (stroke != 0) strokeW else 0, stroke)
        setTextColor(t)
    }
}

/** Botón on/off ligado a un int. invert = true para MUTE (activo cuando mix/on == 0). */
fun Context.toggle(
    text: String, addr: String, style: BtnStyle = BtnStyle.LIGHT, invert: Boolean = false,
    color: Int = C.ACCENT, sp: Float = 16f
): Btn {
    val b = Btn(this, text, style, sp)
    b.activeColor = color
    b.bind(addr) { v ->
        val x = v.asI()
        b.active = if (invert) x == 0 else x != 0
    }
    b.setOnClickListener {
        val cur = App.store.int(addr, if (invert) 1 else 0)
        App.store.set(addr, if (cur != 0) 0 else 1)
    }
    return b
}

/** Interruptor ON/OFF para gate/dyn/eq. */
fun Context.onOff(addr: String): Btn {
    val b = Btn(this, "OFF", BtnStyle.LIGHT, 18f)
    b.activeColor = C.ACCENT
    b.bind(addr) { v ->
        val on = v.asI() != 0
        b.active = on
        b.text = if (on) "● ON" else "○ OFF"
    }
    b.setOnClickListener { App.store.set(addr, if (App.store.int(addr) != 0) 0 else 1) }
    return b
}

/** Selector segmentado (MANUAL/AUTO, PEAK/RMS...). */
fun Context.choice(addr: String, labels: List<String>): LinearLayout {
    val r = row()
    r.background = rounded(C.SURFACE2, 10)
    r.setPadding(dp(3), dp(3), dp(3), dp(3))
    val btns = ArrayList<Btn>()
    labels.forEachIndexed { i, s ->
        val b = Btn(this, s, BtnStyle.NAV, 16f)
        b.setOnClickListener { App.store.set(addr, i) }
        btns.add(b)
        r.addView(b, lp(dp(96), dp(42)))
    }
    r.bind(addr) { v ->
        val x = v.asI()
        for (i in btns.indices) btns[i].active = i == x
    }
    return r
}

/** Selector de lista. */
fun Context.selector(title: String, addr: String, options: List<String>): Btn {
    val b = Btn(this, "$title\n—", BtnStyle.LIGHT, 16f)
    b.maxLines = 2
    b.gravity = Gravity.CENTER_VERTICAL or Gravity.START
    b.setPadding(dp(14), 0, dp(14), 0)
    b.bind(addr) { v -> b.text = "$title  ▾\n${options.getOrElse(v.asI()) { "?" }}" }
    b.setOnClickListener {
        val pm = PopupMenu(this, b)
        options.forEachIndexed { i, s -> pm.menu.add(0, i, i, s) }
        pm.setOnMenuItemClickListener { item ->
            App.store.set(addr, item.itemId)
            true
        }
        pm.show()
    }
    return b
}

/** Un bit de una máscara (asignación a DCA / mute groups). */
fun Context.bitToggle(text: String, addr: String, bit: Int, color: Int = C.ACCENT): Btn {
    val b = Btn(this, text, BtnStyle.LIGHT, 17f)
    b.activeColor = color
    b.bind(addr) { v -> b.active = ((v.asI() shr bit) and 1) == 1 }
    b.setOnClickListener { App.store.set(addr, App.store.int(addr) xor (1 shl bit)) }
    return b
}

val SCRIBBLE_COLORS = intArrayOf(
    Color.parseColor("#2E3743"), Color.parseColor("#E5484D"), Color.parseColor("#3DD68C"),
    Color.parseColor("#F5C542"), Color.parseColor("#4C8DF6"), Color.parseColor("#C764E8"),
    Color.parseColor("#4CC9F0"), Color.parseColor("#E8EDF2")
)

val COLOR_NAMES = listOf(
    "Apagado", "Rojo", "Verde", "Amarillo", "Azul", "Magenta", "Cian", "Blanco",
    "Apagado inv.", "Rojo inv.", "Verde inv.", "Amarillo inv.", "Azul inv.", "Magenta inv.", "Cian inv.", "Blanco inv."
)

/** Chip de nombre con el color de scribble de la consola (0-7 lleno, 8-15 contorno). */
fun applyScribble(v: TextView, color: Int) {
    val c = color.coerceIn(0, 15)
    val base = SCRIBBLE_COLORS[c % 8]
    val d = GradientDrawable()
    d.cornerRadius = v.dpf(8f)
    if (c < 8) {
        d.setColor(base)
        v.setTextColor(if (c == 0 || c == 1 || c == 4 || c == 5) Color.WHITE else C.INK)
    } else {
        d.setColor(Color.TRANSPARENT)
        d.setStroke(v.dpf(1.5f).toInt(), if (c == 8) C.TEXT_DIM else base)
        v.setTextColor(if (c == 8) C.TEXT else base)
    }
    v.background = d
}
