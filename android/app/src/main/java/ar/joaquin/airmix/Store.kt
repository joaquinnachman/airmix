package ar.joaquin.airmix

import android.os.SystemClock
import android.view.View

/** Estado global de la app (una sola instancia, vive mientras viva el proceso). */
object App {
    val store = Store()
    var client: MixerClient? = null

    /** Perfil de la consola. Arranca con el último modelo usado (o XR12) hasta que la consola responde /xinfo. */
    var console: Console = Console.fromModel("XR12")
        private set
    var firmware = ""
    var mixerName = ""

    /** Vistas de medidores visibles: se redibujan desde un único tick. */
    val meterViews = HashSet<View>()

    /** Devuelve true si cambió la forma de la consola (canales, buses, familia). */
    fun applyModel(m: String): Boolean {
        val n = Console.fromModel(m)
        val changed = !n.sameShape(console)
        console = n
        client?.x32 = n.isX32
        return changed
    }
}

/**
 * Valores de la consola por dirección OSC. Solo se usa desde el hilo principal.
 * Las vistas se suscriben al adjuntarse y se desuscriben al desadjuntarse
 * (ver View.bind), así no queda nada colgado cuando cambiás de pantalla.
 */
class Store {
    private val values = HashMap<String, Any>()
    private val listeners = HashMap<String, ArrayList<(Any) -> Unit>>()
    private val touched = HashMap<String, Long>()

    fun get(addr: String): Any? = values[addr]
    fun float(addr: String, def: Float = 0f): Float = (values[addr] as? Number)?.toFloat() ?: def
    fun int(addr: String, def: Int = 0): Int = (values[addr] as? Number)?.toInt() ?: def

    /** Cambio hecho por el usuario: actualiza, manda a la consola y avisa a las vistas. */
    fun set(addr: String, v: Any) {
        values[addr] = v
        touched[addr] = SystemClock.uptimeMillis()
        App.client?.send(addr, v)
        notify(addr, v)
    }

    /** Valor que llegó de la consola. Se ignora si el usuario lo está tocando ahora mismo. */
    fun incoming(addr: String, v: Any) {
        val t = touched[addr]
        if (t != null && SystemClock.uptimeMillis() - t < 500) return
        values[addr] = v
        notify(addr, v)
    }

    fun observe(addr: String, cb: (Any) -> Unit): () -> Unit {
        val list = listeners.getOrPut(addr) { ArrayList() }
        list.add(cb)
        val v = values[addr]
        if (v != null) cb(v) else App.client?.query(addr)
        return {
            list.remove(cb)
            if (list.isEmpty() && listeners[addr] === list) listeners.remove(addr)
        }
    }

    /** Al (re)conectar: pide de nuevo todo lo que está en pantalla. */
    fun requeryObserved() {
        val c = App.client ?: return
        for (k in listeners.keys) c.query(k)
    }

    /** Olvida el valor local (para pedirlo de nuevo y verificar qué tiene la consola). */
    fun forget(addr: String) {
        values.remove(addr)
        touched.remove(addr)
    }

    fun resetValues() {
        values.clear()
        touched.clear()
    }

    private fun notify(addr: String, v: Any) {
        val list = listeners[addr] ?: return
        if (list.size == 1) {
            list[0](v)
            return
        }
        for (cb in list.toTypedArray()) cb(v)
    }
}

fun Any.asF(): Float = (this as? Number)?.toFloat() ?: 0f
fun Any.asI(): Int = (this as? Number)?.toInt() ?: 0

/** Suscribe la vista a una dirección mientras esté en pantalla. */
fun View.bind(addr: String, cb: (Any) -> Unit) {
    val holder = arrayOfNulls<() -> Unit>(1)
    addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
        override fun onViewAttachedToWindow(v: View) {
            holder[0]?.invoke()
            holder[0] = App.store.observe(addr, cb)
        }

        override fun onViewDetachedFromWindow(v: View) {
            holder[0]?.invoke()
            holder[0] = null
        }
    })
    if (isAttachedToWindow) holder[0] = App.store.observe(addr, cb)
}
