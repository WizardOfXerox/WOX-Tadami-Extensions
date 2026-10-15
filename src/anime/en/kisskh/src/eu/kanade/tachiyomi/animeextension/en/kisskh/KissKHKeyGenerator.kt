package eu.kanade.tachiyomi.animeextension.en.kisskh

object KissKHKeyGenerator {
    private val ROUND_KEYS = intArrayOf(
        0x4f6bdaa3, -0x61d07350, 0x7f5e722d, -0x61210cec,
        0x536620a8, -0x32b653e8, -0x4de821cb, 0x2cc92d21,
        -0x73412227, 0x41f771c1, -0xc1f500c, -0x20d67d2b,
        0x2dadde47, 0x6c5aaf86, -0x6045ff8e, 0x409382a7,
        -0x6417db2, -0x6a1bd238, 0xa5e2dba, 0x4acdaf1d,
        0x54c72698, -0x3edcf4b0, -0x3482d916, -0x7e4f7609,
        -0x6c9fb16c, 0x524345c4, -0x66c19cd2, 0x188eead9,
        -0x351884c7, -0x675bc103, 0x19a5dd3, 0x1914b70a,
        -0x4fb1e313, 0x28ea2210, 0x29707fc3, 0x3064c8c9,
        -0x17593e17, -0x3fb31c07, -0x16c363c6, -0x26a7ab0d,
        -0x4b793324, 0x74ca2f25, -0x62094ce1, 0x44aee7ec,
    )

    private val t0 = IntArray(256)
    private val t1 = IntArray(256)
    private val t2 = IntArray(256)
    private val t3 = IntArray(256)
    private val sbox = IntArray(256)

    init {
        val d = IntArray(256) { i ->
            if (i < 128) (i shl 1) and 0xFF else ((i shl 1) xor 0x11b) and 0xFF
        }
        var y = 0
        var x = 0
        for (i in 0 until 256) {
            var s = x xor (x shl 1) xor (x shl 2) xor (x shl 3) xor (x shl 4)
            s = (s ushr 8) xor (s and 0xFF) xor 0x63
            sbox[y] = s and 0xFF
            val xVal = d[y]
            val xVal2 = d[d[xVal]]
            val t = (0x101 * d[s]) xor (0x1010100 * s)
            t0[y] = (t shl 24) or (t ushr 8)
            t1[y] = (t shl 16) or (t ushr 16)
            t2[y] = (t shl 8) or (t ushr 24)
            t3[y] = t
            if (y != 0) {
                y = xVal xor d[d[d[xVal2 xor xVal]]]
                x = x xor d[d[x]]
            } else {
                y = 1
                x = 1
            }
        }
    }

    private fun encryptBlock(words: IntArray, offset: Int) {
        val iv0: Int
        val iv1: Int
        val iv2: Int
        val iv3: Int
        if (offset == 0) {
            iv0 = 0x1504af3
            iv1 = 0x56e619cf
            iv2 = 0x2e42bba6
            iv3 = -0x73c08f07
        } else {
            iv0 = words[offset - 4]
            iv1 = words[offset - 3]
            iv2 = words[offset - 2]
            iv3 = words[offset - 1]
        }

        words[offset] = words[offset] xor iv0
        words[offset + 1] = words[offset + 1] xor iv1
        words[offset + 2] = words[offset + 2] xor iv2
        words[offset + 3] = words[offset + 3] xor iv3

        var s0 = words[offset] xor ROUND_KEYS[0]
        var s1 = words[offset + 1] xor ROUND_KEYS[1]
        var s2 = words[offset + 2] xor ROUND_KEYS[2]
        var s3 = words[offset + 3] xor ROUND_KEYS[3]
        var kIdx = 4

        for (r in 1 until 10) {
            val n0 = t0[(s0 ushr 24) and 0xFF] xor t1[(s1 ushr 16) and 0xFF] xor t2[(s2 ushr 8) and 0xFF] xor t3[s3 and 0xFF] xor ROUND_KEYS[kIdx++]
            val n1 = t0[(s1 ushr 24) and 0xFF] xor t1[(s2 ushr 16) and 0xFF] xor t2[(s3 ushr 8) and 0xFF] xor t3[s0 and 0xFF] xor ROUND_KEYS[kIdx++]
            val n2 = t0[(s2 ushr 24) and 0xFF] xor t1[(s3 ushr 16) and 0xFF] xor t2[(s0 ushr 8) and 0xFF] xor t3[s1 and 0xFF] xor ROUND_KEYS[kIdx++]
            val n3 = t0[(s3 ushr 24) and 0xFF] xor t1[(s0 ushr 16) and 0xFF] xor t2[(s1 ushr 8) and 0xFF] xor t3[s2 and 0xFF] xor ROUND_KEYS[kIdx++]
            s0 = n0
            s1 = n1
            s2 = n2
            s3 = n3
        }

        words[offset] = ((sbox[(s0 ushr 24) and 0xFF] shl 24) or (sbox[(s1 ushr 16) and 0xFF] shl 16) or (sbox[(s2 ushr 8) and 0xFF] shl 8) or sbox[s3 and 0xFF]) xor ROUND_KEYS[kIdx++]
        words[offset + 1] = ((sbox[(s1 ushr 24) and 0xFF] shl 24) or (sbox[(s2 ushr 16) and 0xFF] shl 16) or (sbox[(s3 ushr 8) and 0xFF] shl 8) or sbox[s0 and 0xFF]) xor ROUND_KEYS[kIdx++]
        words[offset + 2] = ((sbox[(s2 ushr 24) and 0xFF] shl 24) or (sbox[(s3 ushr 16) and 0xFF] shl 16) or (sbox[(s0 ushr 8) and 0xFF] shl 8) or sbox[s1 and 0xFF]) xor ROUND_KEYS[kIdx++]
        words[offset + 3] = ((sbox[(s3 ushr 24) and 0xFF] shl 24) or (sbox[(s0 ushr 16) and 0xFF] shl 16) or (sbox[(s1 ushr 8) and 0xFF] shl 8) or sbox[s2 and 0xFF]) xor ROUND_KEYS[kIdx++]
    }

    private fun jsHash(s: String): Long {
        var h = 0L
        for (i in 0 until s.length) {
            val shifted = (h.toInt() shl 5).toLong()
            h = shifted - h + s[i].code.toLong()
        }
        return h
    }

    fun generateKey(epId: String, isSub: Boolean = false): String {
        val appVer = "2.8.10"
        val guid = if (isSub) "VgV52sWhwvBSf8BsM3BRY9weWiiCbtGp" else "62f176f3bb1b5b8e70e39932ad34a0c7"
        val platformVer = "4830201"
        val appName = "kisskh"

        val beforeStr = "|$epId||mg3c3b04ba|$appVer|$guid|$platformVer|$appName|$appName|$appName|$appName|$appName|$appName|00|"
        val h = jsHash(beforeStr)
        val finalStr = "|$h|$epId||mg3c3b04ba|$appVer|$guid|$platformVer|$appName|$appName|$appName|$appName|$appName|$appName|00|"

        val pad = 16 - (finalStr.length % 16)
        val paddedStr = finalStr + pad.toChar().toString().repeat(pad)

        val words = IntArray(paddedStr.length / 4)
        for (i in 0 until paddedStr.length) {
            words[i ushr 2] = words[i ushr 2] or ((paddedStr[i].code and 0xFF) shl (24 - (i % 4) * 8))
        }

        for (offset in 0 until words.size step 4) {
            encryptBlock(words, offset)
        }

        val sb = java.lang.StringBuilder(paddedStr.length * 2)
        for (i in 0 until paddedStr.length) {
            val b = (words[i ushr 2] ushr (24 - (i % 4) * 8)) and 0xFF
            sb.append(String.format("%02X", b))
        }
        return sb.toString()
    }
}
