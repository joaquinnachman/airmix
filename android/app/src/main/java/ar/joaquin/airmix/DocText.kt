package ar.joaquin.airmix

import java.io.ByteArrayInputStream
import java.nio.ByteBuffer
import java.nio.charset.Charset
import java.nio.charset.CodingErrorAction
import java.util.zip.ZipInputStream

/**
 * Saca el texto plano de un archivo para la lista de temas: .txt, .docx (Word / Google Docs),
 * .doc (Word 97-2003) y .rtf. Misma lógica que DocText.swift.
 */
object DocText {
    private val CP1252: Charset = Charset.forName("windows-1252")

    fun extract(b: ByteArray): String? {
        if (b.size >= 4 && b[0] == 'P'.code.toByte() && b[1] == 'K'.code.toByte()) return docx(b)
        if (b.size >= 8 && (b[0].toInt() and 0xff) == 0xD0 && (b[1].toInt() and 0xff) == 0xCF &&
            (b[2].toInt() and 0xff) == 0x11 && (b[3].toInt() and 0xff) == 0xE0
        ) return doc(b)
        val head = String(b, 0, minOf(b.size, 8), Charsets.ISO_8859_1)
        if (head.startsWith("{\\rtf")) return rtf(String(b, Charsets.ISO_8859_1))
        return plain(b)
    }

    /** UTF-8 (con o sin BOM); si no es UTF-8 válido, Windows-1252. UTF-16 con BOM también. */
    private fun plain(b: ByteArray): String {
        if (b.size >= 2 && (b[0].toInt() and 0xff) == 0xFF && (b[1].toInt() and 0xff) == 0xFE) return String(b, 2, b.size - 2, Charsets.UTF_16LE)
        if (b.size >= 2 && (b[0].toInt() and 0xff) == 0xFE && (b[1].toInt() and 0xff) == 0xFF) return String(b, 2, b.size - 2, Charsets.UTF_16BE)
        val start = if (b.size >= 3 && (b[0].toInt() and 0xff) == 0xEF && (b[1].toInt() and 0xff) == 0xBB && (b[2].toInt() and 0xff) == 0xBF) 3 else 0
        return try {
            Charsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(b, start, b.size - start)).toString()
        } catch (_: Exception) {
            String(b, CP1252)
        }
    }

    // ---------------------------------------------------------------- .docx

    private fun docx(b: ByteArray): String? {
        val xml = try {
            ZipInputStream(ByteArrayInputStream(b)).use { z ->
                var e = z.nextEntry
                var found: String? = null
                while (e != null) {
                    if (e.name == "word/document.xml") {
                        found = z.readBytes().toString(Charsets.UTF_8)
                        break
                    }
                    e = z.nextEntry
                }
                found
            }
        } catch (_: Exception) {
            null
        } ?: return null
        return docxXmlToText(xml)
    }

    private val TOKEN = Regex("<w:t(?:\\s[^>]*)?>([^<]*)</w:t>|<w:tab/>|<w:br/>|<w:cr/>")

    fun docxXmlToText(xml: String): String {
        val sb = StringBuilder()
        for (para in xml.split("</w:p>")) {
            for (m in TOKEN.findAll(para)) {
                val t = m.groupValues[1]
                when {
                    m.value.startsWith("<w:tab") -> sb.append('\t')
                    m.value.startsWith("<w:br") || m.value.startsWith("<w:cr") -> sb.append('\n')
                    else -> sb.append(unescape(t))
                }
            }
            sb.append('\n')
        }
        return sb.toString()
    }

    private fun unescape(s: String): String {
        if (!s.contains('&')) return s
        var r = s.replace("&lt;", "<").replace("&gt;", ">").replace("&quot;", "\"").replace("&apos;", "'")
        r = Regex("&#(x?)([0-9a-fA-F]+);").replace(r) { m ->
            val code = if (m.groupValues[1].isNotEmpty()) m.groupValues[2].toInt(16) else m.groupValues[2].toInt()
            String(Character.toChars(code))
        }
        return r.replace("&amp;", "&")
    }

    // ---------------------------------------------------------------- .doc (Word 97-2003, archivo OLE)

    private fun u16(b: ByteArray, o: Int): Int = (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8)
    private fun u32(b: ByteArray, o: Int): Int =
        (b[o].toInt() and 0xff) or ((b[o + 1].toInt() and 0xff) shl 8) or
            ((b[o + 2].toInt() and 0xff) shl 16) or ((b[o + 3].toInt() and 0xff) shl 24)

    private fun doc(f: ByteArray): String? = try {
        docParse(f)
    } catch (_: Exception) {
        null
    }

    private fun docParse(f: ByteArray): String? {
        val ss = 1 shl u16(f, 0x1E)
        val mss = 1 shl u16(f, 0x20)
        val nFat = u32(f, 0x2C)
        val dirStart = u32(f, 0x30)
        val miniCutoff = u32(f, 0x38)
        val miniFatStart = u32(f, 0x3C)
        val difatStart = u32(f, 0x44)
        val sec = { s: Int -> (s + 1) * ss }

        // Sectores de la FAT (DIFAT: 109 en el encabezado + cadena de sectores DIFAT).
        val fatSecs = ArrayList<Int>()
        for (i in 0 until minOf(nFat, 109)) fatSecs.add(u32(f, 0x4C + 4 * i))
        var d = difatStart
        var guard = 0
        while (fatSecs.size < nFat && d >= 0 && sec(d) + ss <= f.size && guard++ < 1000) {
            for (i in 0 until ss / 4 - 1) if (fatSecs.size < nFat) fatSecs.add(u32(f, sec(d) + 4 * i))
            d = u32(f, sec(d) + ss - 4)
        }
        val fat = IntArray(fatSecs.size * (ss / 4))
        fatSecs.forEachIndexed { k, s -> for (i in 0 until ss / 4) if (sec(s) + 4 * i + 4 <= f.size) fat[k * (ss / 4) + i] = u32(f, sec(s) + 4 * i) }

        fun chain(start: Int): ByteArray {
            val out = java.io.ByteArrayOutputStream()
            var s = start
            var n = 0
            while (s >= 0 && s < fat.size && n++ < 100000) {
                val o = sec(s)
                if (o + ss > f.size) break
                out.write(f, o, ss)
                s = fat[s]
            }
            return out.toByteArray()
        }

        val dir = chain(dirStart)
        val names = HashMap<String, Pair<Int, Int>>()
        for (i in 0 until dir.size / 128) {
            val o = i * 128
            val len = u16(dir, o + 0x40)
            if (len < 2) continue
            val name = String(dir, o, len - 2, Charsets.UTF_16LE)
            names[name] = u32(dir, o + 0x74) to u32(dir, o + 0x78)
        }
        // Mini stream (para streams chicos, como la tabla de un .doc corto).
        val rootStart = u32(dir, 0x74)
        val mini = chain(rootStart)
        val miniFatBytes = chain(miniFatStart)
        val miniFat = IntArray(miniFatBytes.size / 4) { u32(miniFatBytes, it * 4) }

        fun stream(name: String): ByteArray? {
            val (start, size) = names[name] ?: return null
            if (size < miniCutoff) {
                val out = java.io.ByteArrayOutputStream()
                var s = start
                var n = 0
                while (s >= 0 && s < miniFat.size + 1 && out.size() < size && n++ < 100000) {
                    val o = s * mss
                    if (o + mss > mini.size) break
                    out.write(mini, o, mss)
                    s = if (s < miniFat.size) miniFat[s] else -2
                }
                return out.toByteArray().copyOf(minOf(size, out.size()))
            }
            val all = chain(start)
            return all.copyOf(minOf(size, all.size))
        }

        val wd = stream("WordDocument") ?: return null
        if (u16(wd, 0) != 0xA5EC) return null
        val table = stream(if ((u16(wd, 0x0A) and 0x0200) != 0) "1Table" else "0Table") ?: return null
        val ccpText = u32(wd, 0x4C)
        val fcClx = u32(wd, 0x1A2)

        // Clx: saltear Prc (0x01) y leer la tabla de piezas (0x02).
        var p = fcClx
        while (p < table.size && table[p].toInt() == 1) p += 3 + u16(table, p + 1)
        if (p >= table.size || table[p].toInt() != 2) return null
        val lcb = u32(table, p + 1)
        p += 5
        val n = (lcb - 4) / 12
        val sb = StringBuilder()
        for (i in 0 until n) {
            val cpStart = u32(table, p + 4 * i)
            val cpEnd = u32(table, p + 4 * (i + 1))
            if (cpStart >= ccpText) break
            val count = minOf(cpEnd, ccpText) - cpStart
            val fc = u32(table, p + 4 * (n + 1) + 8 * i + 2)
            if ((fc and 0x40000000) != 0) {
                val off = (fc and 0x3FFFFFFF) / 2
                if (off + count <= wd.size) sb.append(String(wd, off, count, CP1252))
            } else {
                if (fc + 2 * count <= wd.size) sb.append(String(wd, fc, 2 * count, Charsets.UTF_16LE))
            }
        }
        return cleanWord(sb.toString())
    }

    /** Caracteres de control de Word: párrafo/celda -> salto; campos: se deja solo el resultado. */
    private fun cleanWord(s: String): String {
        val sb = StringBuilder()
        var depth = 0
        var inCode = false
        for (ch in s) {
            when (ch.code) {
                0x13 -> { depth++; inCode = true }
                0x14 -> inCode = false
                0x15 -> { if (depth > 0) depth--; inCode = false }
                0x0D, 0x07, 0x0B, 0x0C -> if (!inCode) sb.append('\n')
                0x09 -> if (!inCode) sb.append('\t')
                else -> if (!inCode && ch.code >= 0x20) sb.append(ch)
            }
        }
        return sb.toString()
    }

    // ---------------------------------------------------------------- .rtf

    private val RTF_SKIP = setOf("fonttbl", "colortbl", "stylesheet", "info", "pict", "header", "footer", "listtable", "listoverridetable", "rsidtbl", "generator", "xmlnstbl")

    fun rtf(s: String): String {
        val sb = StringBuilder()
        val skipStack = ArrayList<Boolean>()
        var skip = false
        var i = 0
        var groupStart = false
        var uc = 1 // cuántos caracteres de reemplazo siguen a cada \uN
        var pend = 0
        while (i < s.length) {
            val c = s[i]
            when (c) {
                '{' -> { skipStack.add(skip); groupStart = true; i++; continue }
                '}' -> { skip = if (skipStack.isEmpty()) false else skipStack.removeAt(skipStack.size - 1); i++ }
                '\\' -> {
                    if (i + 1 >= s.length) break
                    val n = s[i + 1]
                    when {
                        n == '*' -> { skip = true; i += 2 }
                        n == '\'' && i + 3 < s.length -> {
                            val code = s.substring(i + 2, i + 4).toIntOrNull(16)
                            if (pend > 0) pend--
                            else if (!skip && code != null) sb.append(String(byteArrayOf(code.toByte()), CP1252))
                            i += 4
                        }
                        n.isLetter() -> {
                            var j = i + 1
                            while (j < s.length && s[j].isLetter()) j++
                            val word = s.substring(i + 1, j)
                            var k = j
                            if (k < s.length && (s[k] == '-' || s[k].isDigit())) {
                                k++
                                while (k < s.length && s[k].isDigit()) k++
                            }
                            val param = s.substring(j, k)
                            if (k < s.length && s[k] == ' ') k++
                            if (groupStart && word in RTF_SKIP) skip = true
                            if (word == "uc") uc = param.toIntOrNull() ?: 1
                            if (!skip) when (word) {
                                "par", "line", "row", "cell" -> sb.append('\n')
                                "tab" -> sb.append('\t')
                                "u" -> param.toIntOrNull()?.let {
                                    sb.append((if (it < 0) it + 65536 else it).toChar())
                                    pend = uc
                                }
                            }
                            i = k
                        }
                        else -> { if (!skip && n != '\n' && n != '\r') sb.append(n); i += 2 }
                    }
                }
                '\r', '\n' -> i++
                else -> {
                    if (pend > 0) pend-- else if (!skip) sb.append(c)
                    i++
                }
            }
            groupStart = false
        }
        return sb.toString()
    }
}
