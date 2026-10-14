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

object C {
    val BG = Color.parseColor("#1B1B1B")
    val TOP = Color.parseColor("#141414")
    val NAV_BG = Color.parseColor("#1A1A1A")
    val PANEL = Color.parseColor("#2E2E2E")
    val GRAPH = Color.parseColor("#161616")
    val STRIP_A = Color.parseColor("#232323")
    val STRIP_B = Color.parseColor("#2B2B2B")
    val HEADER = Color.parseColor("#3A3A3A")
    val HEADER_SENDS = Color.parseColor("#BDBDBD")
    val ACCENT = Color.parseColor("#FDB71A")
    val CYAN = Color.parseColor("#1ED5E3")
    val MUTE = Color.parseColor("#D0101A")
    val BTN = Color.parseColor("#111111")
    val BTN_LIGHT = Color.parseColor("#D9D9D9")
    val BTN_GREY = Color.parseColor("#6E6E6E")
    val TEXT = Color.WHITE
    val TEXT_DIM = Color.parseColor("#BDBDBD")
    val GRID = Color.parseColor("#333333")
    val DIVIDER = Color.parseColor("#555555")
    val GREEN = Color.parseColor("#2FCB3A")
    val YELLOW = Color.parseColor("#E8C51A")
    val RED = Color.parseColor("#E53935")
    val METER_BG = Color.parseColor("#0B0B0B")
    val TICK = Color.parseColor("#8A8A8A")
    val CAP_EDGE = Color.parseColor("#9E9E9E")
    val CAP_TOP = Color.parseColor("#E4E4E4")
    val KNOB_BG = Color.parseColor("#0E0E0E")
    val KNOB_ARC = Color.parseColor("#9C9C9C")
    val PAN_BG = Color.parseColor("#3A3A3A")
}

val FONT: Typeface = Typeface.create("sans-serif-condensed", Typeface.BOLD)

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
    layoutParams = lp(MATCH, dp(2)).m(0, dp(14), 0, dp(14))
}

fun Context.scroll(v: View): ScrollView = ScrollView(this).apply {
    isFillViewport = true
    addView(v, FrameLayout.LayoutParams(MATCH, WRAP))
}

enum class BtnStyle { NAV, TAB, LIGHT, MUTE, SMALL }

class Btn(ctx: Context, label: String, private val style: BtnStyle = BtnStyle.NAV, sp: Float = 16f) : TextView(ctx) {
    var activeColor = C.ACCENT
    var active = false
        set(v) {
            field = v
            refresh()
        }
    private val bg = GradientDrawable()

    init {
        text = label
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        typeface = FONT
        gravity = Gravity.CENTER
        includeFontPadding = false
        bg.cornerRadius = ctx.dp(4).toFloat()
        background = bg
        isClickable = true
        refresh()
    }

    fun refresh() {
        val b: Int
        val t: Int
        if (active) {
            b = activeColor
            t = if (activeColor == C.MUTE) C.TEXT else Color.BLACK
        } else {
            when (style) {
                BtnStyle.NAV -> { b = C.BTN; t = C.TEXT_DIM }
                BtnStyle.TAB -> { b = C.TOP; t = C.TEXT_DIM }
                BtnStyle.LIGHT -> { b = C.BTN_LIGHT; t = Color.BLACK }
                BtnStyle.MUTE -> { b = C.BTN_GREY; t = C.TEXT }
                BtnStyle.SMALL -> { b = C.BTN_GREY; t = C.TEXT }
            }
        }
        bg.setColor(b)
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

/** Botón "ON/OFF" estilo MX-MIX para gate/dyn/eq. */
fun Context.onOff(addr: String): Btn {
    val b = Btn(this, "OFF", BtnStyle.NAV, 20f)
    b.bind(addr) { v ->
        val on = v.asI() != 0
        b.active = on
        b.text = if (on) "ON" else "OFF"
    }
    b.setOnClickListener { App.store.set(addr, if (App.store.int(addr) != 0) 0 else 1) }
    return b
}

/** Selector segmentado (MANUAL/AUTO, PEAK/RMS...). */
fun Context.choice(addr: String, labels: List<String>): LinearLayout {
    val r = row()
    val btns = ArrayList<Btn>()
    labels.forEachIndexed { i, s ->
        val b = Btn(this, s, BtnStyle.NAV, 18f)
        b.setOnClickListener { App.store.set(addr, i) }
        btns.add(b)
        r.addView(b, lp(dp(110), dp(50)))
    }
    r.bind(addr) { v ->
        val x = v.asI()
        for (i in btns.indices) btns[i].active = i == x
    }
    return r
}

/** Selector de lista (TYPE ⋮). */
fun Context.selector(title: String, addr: String, options: List<String>): Btn {
    val b = Btn(this, "$title\n—", BtnStyle.NAV, 17f)
    b.maxLines = 2
    b.gravity = Gravity.CENTER_VERTICAL or Gravity.START
    b.setPadding(dp(14), 0, dp(14), 0)
    b.bind(addr) { v -> b.text = "$title  ⋮\n${options.getOrElse(v.asI()) { "?" }}" }
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
fun Context.bitToggle(text: String, addr: String, bit: Int): Btn {
    val b = Btn(this, text, BtnStyle.LIGHT, 18f)
    b.bind(addr) { v -> b.active = ((v.asI() shr bit) and 1) == 1 }
    b.setOnClickListener { App.store.set(addr, App.store.int(addr) xor (1 shl bit)) }
    return b
}

private val SCRIBBLE = intArrayOf(
    Color.parseColor("#444444"), Color.parseColor("#E53935"), Color.parseColor("#43A047"),
    Color.parseColor("#FDD835"), Color.parseColor("#1E88E5"), Color.parseColor("#D81B60"),
    Color.parseColor("#00ACC1"), Color.WHITE
)

val COLOR_NAMES = listOf(
    "Apagado", "Rojo", "Verde", "Amarillo", "Azul", "Magenta", "Cian", "Blanco",
    "Apagado inv.", "Rojo inv.", "Verde inv.", "Amarillo inv.", "Azul inv.", "Magenta inv.", "Cian inv.", "Blanco inv."
)

/** Colores de scribble strip X Air: 0-7 normales, 8-15 invertidos. */
fun applyScribble(v: TextView, color: Int) {
    val c = color.coerceIn(0, 15)
    val base = SCRIBBLE[c % 8]
    if (c < 8) {
        v.setBackgroundColor(base)
        v.setTextColor(if (c == 0 || c == 1 || c == 4 || c == 5) Color.WHITE else Color.BLACK)
    } else {
        v.setBackgroundColor(Color.BLACK)
        v.setTextColor(if (c == 8) Color.WHITE else base)
    }
}
