package ar.joaquin.airmix

import java.nio.ByteBuffer
import java.nio.ByteOrder

class OscMessage(val address: String, val args: List<Any>)

/** Codec OSC mínimo (int, float, string, blob). Suficiente para X Air. */
object Osc {
    private val CS = Charsets.ISO_8859_1

    private fun padded(n: Int): Int = (n + 3) and 3.inv()

    fun encode(address: String, vararg args: Any): ByteArray {
        val tags = StringBuilder(",")
        val addrBytes = address.toByteArray(CS)
        var size = padded(addrBytes.size + 1)
        for (a in args) {
            when (a) {
                is Int -> { tags.append('i'); size += 4 }
                is Float -> { tags.append('f'); size += 4 }
                is String -> { tags.append('s'); size += padded(a.toByteArray(CS).size + 1) }
                else -> throw IllegalArgumentException("Tipo OSC no soportado: $a")
            }
        }
        size += padded(tags.length + 1)
        val buf = ByteBuffer.allocate(size).order(ByteOrder.BIG_ENDIAN)
        putString(buf, addrBytes)
        putString(buf, tags.toString().toByteArray(CS))
        for (a in args) {
            when (a) {
                is Int -> buf.putInt(a)
                is Float -> buf.putFloat(a)
                is String -> putString(buf, a.toByteArray(CS))
            }
        }
        return buf.array()
    }

    private fun putString(buf: ByteBuffer, b: ByteArray) {
        buf.put(b)
        val pad = padded(b.size + 1) - b.size
        for (i in 0 until pad) buf.put(0.toByte())
    }

    private fun indexOfZero(d: ByteArray, from: Int, len: Int): Int {
        var i = from
        while (i < len) {
            if (d[i] == 0.toByte()) return i
            i++
        }
        return -1
    }

    fun decode(data: ByteArray, len: Int): OscMessage? {
        try {
            val addrEnd = indexOfZero(data, 0, len)
            if (addrEnd <= 0) return null
            val address = String(data, 0, addrEnd, CS)
            var p = padded(addrEnd + 1)
            if (p >= len || data[p] != ','.code.toByte()) return OscMessage(address, emptyList())
            val tagEnd = indexOfZero(data, p, len)
            if (tagEnd < 0) return null
            val tags = String(data, p + 1, tagEnd - p - 1, CS)
            p = padded(tagEnd + 1)
            val bb = ByteBuffer.wrap(data, 0, len).order(ByteOrder.BIG_ENDIAN)
            val args = ArrayList<Any>(tags.length)
            for (t in tags) {
                when (t) {
                    'i' -> { args.add(bb.getInt(p)); p += 4 }
                    'f' -> { args.add(bb.getFloat(p)); p += 4 }
                    's' -> {
                        val e = indexOfZero(data, p, len)
                        if (e < 0) return OscMessage(address, args)
                        args.add(String(data, p, e - p, CS))
                        p = padded(e + 1)
                    }
                    'b' -> {
                        val n = bb.getInt(p)
                        p += 4
                        args.add(data.copyOfRange(p, p + n))
                        p += padded(n)
                    }
                    else -> return OscMessage(address, args)
                }
            }
            return OscMessage(address, args)
        } catch (e: Exception) {
            return null
        }
    }
}
