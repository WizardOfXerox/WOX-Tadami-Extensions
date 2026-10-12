package eu.kanade.tachiyomi.animeextension.en.reanime

import eu.kanade.tachiyomi.animesource.model.AnimeFilterList
import eu.kanade.tachiyomi.animesource.model.AnimesPage
import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.SEpisode
import eu.kanade.tachiyomi.animesource.model.Track
import eu.kanade.tachiyomi.animesource.model.Video
import eu.kanade.tachiyomi.animesource.online.AnimeHttpSource
import eu.kanade.tachiyomi.lib.playlistutils.PlaylistUtils
import eu.kanade.tachiyomi.network.GET
import eu.kanade.tachiyomi.util.asJsoup
import okhttp3.Headers
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Request
import okhttp3.Response
import org.json.JSONArray
import org.json.JSONObject
import java.io.ByteArrayOutputStream
import java.net.URLEncoder
import java.nio.ByteBuffer
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Base64
import java.util.Locale
import javax.crypto.Cipher
import javax.crypto.Mac
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.SecretKeySpec

class ReAnime : AnimeHttpSource() {

    override val name = "Re:Anime"

    override val baseUrl = "https://reanime.to"

    private val apiUrl = "https://reanime.to/api"

    override val lang = "en"

    override val supportsLatest = true

    private val playlistUtils by lazy { PlaylistUtils(client, headers) }

    override fun headersBuilder(): Headers.Builder = Headers.Builder()
        .add("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36")
        .add("Referer", "$baseUrl/")

    // ============================== Popular ===============================

    override fun popularAnimeRequest(page: Int): Request {
        return GET("$baseUrl/search?page=$page", headers)
    }

    override fun popularAnimeParse(response: Response): AnimesPage {
        val document = response.asJsoup()
        val animeElements = document.select("a[href^=/anime/]")
        val animeList = animeElements.mapNotNull { element ->
            val url = element.attr("href")
            val title = element.select("h3[data-anime-title]").text().ifBlank {
                element.select("img").attr("alt")
            }.trim()
            if (title.isBlank()) return@mapNotNull null
            val thumb = element.select("img").attr("abs:src")
            SAnime.create().apply {
                this.title = title
                this.url = url
                this.thumbnail_url = thumb
            }
        }.distinctBy { it.url }

        val hasNextPage = animeList.size >= 24
        return AnimesPage(animeList, hasNextPage)
    }

    // =============================== Latest ===============================

    override fun latestUpdatesRequest(page: Int): Request {
        return GET("$baseUrl/home", headers)
    }

    override fun latestUpdatesParse(response: Response): AnimesPage {
        return popularAnimeParse(response)
    }

    // =============================== Search ===============================

    override fun searchAnimeRequest(page: Int, query: String, filters: AnimeFilterList): Request {
        return if (query.isNotBlank()) {
            val encodedQuery = URLEncoder.encode(query.trim(), "UTF-8")
            GET("$baseUrl/search?q=$encodedQuery&page=$page", headers)
        } else {
            GET("$baseUrl/search?page=$page", headers)
        }
    }

    override fun searchAnimeParse(response: Response): AnimesPage {
        return popularAnimeParse(response)
    }

    // =========================== Anime Details ============================

    override fun animeDetailsRequest(anime: SAnime): Request {
        val slug = anime.url.removePrefix("/anime/").removePrefix("/watch/").removePrefix("/")
        return GET("$apiUrl/v1/anime/$slug", headers)
    }

    override fun animeDetailsParse(response: Response): SAnime {
        val json = JSONObject(response.body.string())
        val titleObj = json.optJSONObject("title")
        val titleStr = titleObj?.optString("english")?.ifBlank { null }
            ?: titleObj?.optString("user_preferred")?.ifBlank { null }
            ?: titleObj?.optString("romaji")?.ifBlank { null }
            ?: "Anime"

        return SAnime.create().apply {
            title = titleStr
            description = json.optString("description", "")
                .replace("<br>", "\n")
                .replace("<br/>", "\n")
                .replace("<br />", "\n")

            val coverObj = json.optJSONObject("cover_image")
            thumbnail_url = coverObj?.optString("extra_large")?.ifBlank { null }
                ?: coverObj?.optString("large")

            val genresArr = json.optJSONArray("genres")
            if (genresArr != null && genresArr.length() > 0) {
                val list = mutableListOf<String>()
                for (i in 0 until genresArr.length()) list.add(genresArr.getString(i))
                genre = list.joinToString(", ")
            }

            val studiosArr = json.optJSONArray("studios")
            if (studiosArr != null && studiosArr.length() > 0) {
                author = studiosArr.getJSONObject(0).optString("name")
                artist = author
            }

            val statusStr = json.optString("status", "")
            status = when {
                statusStr.contains("Finished", true) -> SAnime.COMPLETED
                statusStr.contains("Releasing", true) -> SAnime.ONGOING
                else -> SAnime.UNKNOWN
            }
        }
    }

    // ============================== Episodes ==============================

    override fun episodeListRequest(anime: SAnime): Request {
        val slug = anime.url.removePrefix("/anime/").removePrefix("/watch/").removePrefix("/")
        return GET("$baseUrl/watch/$slug", headers)
    }

    override fun episodeListParse(response: Response): List<SEpisode> {
        val html = response.body.string()
        val anilistIdMatch = Regex("""anilist\s*:\s*(\d+)""").find(html)
        val anilistId = anilistIdMatch?.groupValues?.get(1) ?: ""
        val slug = response.request.url.pathSegments.lastOrNull() ?: ""

        val epList = mutableListOf<SEpisode>()
        val epRegex = Regex("""\{[^}]*?episode_number\s*:\s*(\d+)[^}]*?title\s*:\s*"([^"]+)"[^}]*?\}""")
        for (match in epRegex.findAll(html)) {
            val num = match.groupValues[1].toFloatOrNull() ?: continue
            val name = match.groupValues[2]
            epList.add(SEpisode.create().apply {
                this.name = name
                this.episode_number = num
                this.url = "/watch/$slug?ep=${num.toInt()}&aid=$anilistId"
            })
        }

        if (epList.isEmpty()) {
            val hrefMatches = Regex("""href="/watch/[^"]*\?ep=(\d+)"""").findAll(html)
            val nums = hrefMatches.mapNotNull { it.groupValues[1].toIntOrNull() }.distinct().sortedDescending()
            for (num in nums) {
                epList.add(SEpisode.create().apply {
                    this.name = "Episode $num"
                    this.episode_number = num.toFloat()
                    this.url = "/watch/$slug?ep=$num&aid=$anilistId"
                })
            }
        }

        return epList.distinctBy { it.episode_number }.sortedByDescending { it.episode_number }
    }

    // ============================ Video Links =============================

    override fun videoListRequest(episode: SEpisode): Request {
        val uri = episode.url.toHttpUrlOrNull() ?: "$baseUrl${episode.url}".toHttpUrl()
        val aid = uri.queryParameter("aid") ?: ""
        val ep = uri.queryParameter("ep") ?: "1"
        return GET(
            "$baseUrl/api/flix/$aid/$ep",
            headers = Headers.Builder()
                .add("Referer", "$baseUrl/")
                .add("Accept", "application/json")
                .build()
        )
    }

    override fun videoListParse(response: Response): List<Video> {
        val json = JSONObject(response.body.string())
        val servers = json.optJSONArray("servers") ?: return emptyList()
        val videoList = mutableListOf<Video>()

        for (i in 0 until servers.length()) {
            val server = servers.getJSONObject(i)
            val serverName = server.optString("serverName", "Server")
            val dataType = server.optString("dataType", "sub").uppercase(Locale.ROOT)
            val dataLink = server.optString("dataLink")
            if (dataLink.isBlank()) continue

            try {
                extractFlixCloud(dataLink, "$serverName $dataType", videoList)
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }

        return videoList
    }

    override fun videoUrlParse(response: Response): String = throw UnsupportedOperationException()

    // ======================== FlixCloud Extraction ========================

    private fun extractFlixCloud(embedUrl: String, prefix: String, videoList: MutableList<Video>) {
        val embedReq = GET(embedUrl, Headers.Builder().add("Referer", "$baseUrl/").build())
        val embedResp = client.newCall(embedReq).execute()
        val html = embedResp.body.string()

        // Extract SSR JSON
        val m = Regex("""\{type:"data",data:(\{)""").find(html) ?: return
        val start = html.indexOf('{', m.range.last)
        var depth = 0
        var end = start
        for (i in start until html.length) {
            if (html[i] == '{') depth++
            else if (html[i] == '}') {
                depth--
                if (depth == 0) {
                    end = i + 1
                    break
                }
            }
        }
        val ssrJson = html.substring(start, end)
        // Clean JS identifiers to standard JSON
        val cleanJson = convertJsObjectToJson(ssrJson)
        val data = JSONObject(cleanJson)

        val seed = data.getString("obfuscation_seed")
        val fields = deriveFields(seed)
        val ocd = data.getJSONObject("obfuscated_crypto_data")
        val container = ocd.getJSONObject(fields.containerName)
        val arr = container.getJSONArray(fields.arrayName)
        val obj = arr.getJSONObject(0).getJSONObject(fields.objectName)

        val frag1 = Base64.getDecoder().decode(obj.getString(fields.keyField))
        val iv = Base64.getDecoder().decode(obj.getString(fields.ivField))
        val kf2 = Base64.getDecoder().decode(data.getString(fields.keyFrag2Field))
        val token = data.getString(fields.tokenField)
        val wPayload = data.getString("w_payload")

        // Fetch token data
        val m3u8Req = GET(
            "https://flixcloud.cc/api/m3u8/$token",
            Headers.Builder().add("Referer", "$baseUrl/").build()
        )
        val tokResp = client.newCall(m3u8Req).execute()
        val tokData = JSONObject(tokResp.body.string())

        val vidKey = sha256Hex(token + "vid").substring(0, 10)
        val keyKey = sha256Hex(token + "key").substring(0, 10)
        val vBytes = Base64.getDecoder().decode(tokData.getString(vidKey))
        val tBytes = Base64.getDecoder().decode(tokData.getString(keyKey))

        val seedInt = seed.substring(0, 8).toLong(16).toInt()
        val wasmOut = runWasmVm(wPayload, frag1, kf2, tBytes, seedInt)

        val pbk = pbkdf2HmacSha256(wasmOut, seed.toByteArray(Charsets.UTF_8), 1000, 32)
        val r = pbk.clone()
        val seedBytes = seed.toByteArray(Charsets.UTF_8)
        for (j in 0 until 32) {
            r[j] = (r[j].toInt() xor seedBytes[j % seedBytes.size].toInt()).toByte()
        }
        val aesKey = sha256Bytes(r)

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        cipher.init(Cipher.DECRYPT_MODE, SecretKeySpec(aesKey, "AES"), IvParameterSpec(iv))
        val masterUrl = String(cipher.doFinal(vBytes), Charsets.UTF_8).trim()

        val subtitleList = mutableListOf<Track>()
        val subArr = data.optJSONArray("subtitles")
        if (subArr != null) {
            for (s in 0 until subArr.length()) {
                val sub = subArr.getJSONObject(s)
                val subUrl = sub.optString("url")
                val subLang = sub.optString("language", "English")
                if (subUrl.isNotBlank()) {
                    subtitleList.add(Track(subUrl, subLang))
                }
            }
        }

        if (masterUrl.startsWith("http")) {
            val videos = playlistUtils.extractFromHls(
                masterUrl,
                referer = "https://flixcloud.cc/",
                videoNameGen = { quality -> "$prefix - $quality" },
                subtitleList = subtitleList
            )
            if (videos.isNotEmpty()) {
                videoList.addAll(videos)
            } else {
                videoList.add(
                    Video(
                        masterUrl,
                        prefix,
                        masterUrl,
                        headers = Headers.Builder().add("Referer", "https://flixcloud.cc/").build(),
                        subtitleTracks = subtitleList
                    )
                )
            }
        }
    }

    // Convert JS object literals (keys without quotes) to JSON
    private fun convertJsObjectToJson(js: String): String {
        return js.replace(Regex("""([{,])\s*([a-zA-Z0-9_\-]+)\s*:""")) { match ->
            "${match.groupValues[1]}\"${match.groupValues[2]}\":"
        }
    }

    private data class Fields(
        val keyField: String,
        val ivField: String,
        val containerName: String,
        val arrayName: String,
        val objectName: String,
        val tokenField: String,
        val keyFrag2Field: String
    )

    private fun deriveFields(seed: String): Fields {
        var e = seed
        for (i in 0 until 3) e = sha256Hex("$e$i")
        var l = e
        for (i in 0 until 3) l = sha256Hex("$l$i")
        return Fields(
            keyField = "kf_" + e.substring(8, 16),
            ivField = "ivf_" + e.substring(16, 24),
            containerName = "cd_" + e.substring(24, 32),
            arrayName = "ad_" + e.substring(32, 40),
            objectName = "od_" + e.substring(40, 48),
            tokenField = e.substring(48, 64) + "_" + e.substring(56, 64),
            keyFrag2Field = l.substring(0, 16) + "_" + l.substring(16, 24)
        )
    }

    private fun runWasmVm(wasmB64: String, frag1: ByteArray, kf2: ByteArray, tBytes: ByteArray, seedInt: Int): ByteArray {
        val wasm = Base64.getDecoder().decode(wasmB64)
        var pos = 8
        var codeBytes: ByteArray? = null
        while (pos < wasm.size) {
            val secId = wasm[pos++].toInt() and 0xff
            var length = 0; var shift = 0
            while (true) {
                val b = wasm[pos++].toInt() and 0xff
                length = length or ((b and 0x7f) shl shift)
                if ((b and 0x80) == 0) break
                shift += 7
            }
            if (secId == 10) {
                codeBytes = wasm.copyOfRange(pos, pos + length)
                break
            }
            pos += length
        }

        if (codeBytes == null) return ByteArray(0)

        var p = 1
        var fsize0 = 0; var shift0 = 0
        while (true) {
            val b = codeBytes[p++].toInt() and 0xff
            fsize0 = fsize0 or ((b and 0x7f) shl shift0)
            if ((b and 0x80) == 0) break
            shift0 += 7
        }
        p += fsize0

        var fsize1 = 0; var shift1 = 0
        while (true) {
            val b = codeBytes[p++].toInt() and 0xff
            fsize1 = fsize1 or ((b and 0x7f) shl shift1)
            if ((b and 0x80) == 0) break
            shift1 += 7
        }
        val func1Start = p
        val numLocals = codeBytes[p++].toInt() and 0xff
        for (i in 0 until numLocals) p += 2
        val instrs = codeBytes.copyOfRange(p, func1Start + fsize1)

        val mem = ByteArray(65536)
        val len = frag1.size
        val y = 1000; val v = 1000 + len; val t = 1000 + 2 * len; val out = 1000 + 3 * len
        System.arraycopy(frag1, 0, mem, y, len)
        System.arraycopy(kf2, 0, mem, v, len)
        System.arraycopy(tBytes, 0, mem, t, len)

        val locals = intArrayOf(y, v, t, out, len, 0, 0, 0, 0, 0)
        val stack = IntArray(128)
        var sp = 0
        var ip = 0
        var loopIp = 0

        while (ip < instrs.size) {
            val op = instrs[ip++].toInt() and 0xff
            when (op) {
                0x0b -> break
                0x03 -> {
                    ip++
                    loopIp = ip
                }
                0x02 -> ip++
                0x20 -> {
                    val idx = instrs[ip++].toInt() and 0xff
                    stack[sp++] = locals[idx]
                }
                0x21 -> {
                    val idx = instrs[ip++].toInt() and 0xff
                    locals[idx] = stack[--sp]
                }
                0x23 -> {
                    ip++
                    stack[sp++] = seedInt
                }
                0x28, 0x2d -> {
                    ip += 2
                    val addr = stack[--sp]
                    stack[sp++] = mem[addr].toInt() and 0xff
                }
                0x36, 0x3a -> {
                    ip += 2
                    val value = stack[--sp]
                    val addr = stack[--sp]
                    mem[addr] = value.toByte()
                }
                0x41 -> {
                    var vConst = 0; var s = 0; var b: Int
                    while (true) {
                        b = instrs[ip++].toInt() and 0xff
                        vConst = vConst or ((b and 0x7f) shl s)
                        if ((b and 0x80) == 0) break
                        s += 7
                    }
                    if ((b and 0x40) != 0) vConst = vConst or (-1 shl s)
                    stack[sp++] = vConst
                }
                0x4f -> {
                    val bVal = stack[--sp].toLong() and 0xffffffffL
                    val aVal = stack[--sp].toLong() and 0xffffffffL
                    stack[sp++] = if (aVal >= bVal) 1 else 0
                }
                0x0d -> {
                    ip++
                    val cond = stack[--sp]
                    if (cond != 0) break
                }
                0x0c -> {
                    ip++
                    ip = loopIp
                }
                0x6a -> { val bVal = stack[--sp]; stack[sp - 1] += bVal }
                0x6b -> { val bVal = stack[--sp]; stack[sp - 1] -= bVal }
                0x6c -> { val bVal = stack[--sp]; stack[sp - 1] *= bVal }
                0x71 -> { val bVal = stack[--sp]; stack[sp - 1] = stack[sp - 1] and bVal }
                0x72 -> { val bVal = stack[--sp]; stack[sp - 1] = stack[sp - 1] or bVal }
                0x73 -> { val bVal = stack[--sp]; stack[sp - 1] = stack[sp - 1] xor bVal }
                0x74 -> { val bVal = stack[--sp]; stack[sp - 1] = stack[sp - 1] shl (bVal and 31) }
                0x76 -> { val bVal = stack[--sp]; stack[sp - 1] = stack[sp - 1] ushr (bVal and 31) }
            }
        }

        val res = ByteArray(len)
        System.arraycopy(mem, out, res, 0, len)
        return res
    }

    private fun pbkdf2HmacSha256(password: ByteArray, salt: ByteArray, iterations: Int, keyLength: Int): ByteArray {
        val mac = Mac.getInstance("HmacSHA256")
        mac.init(SecretKeySpec(password, "HmacSHA256"))
        val result = ByteArray(keyLength)
        val buf = ByteBuffer.allocate(salt.size + 4).put(salt).putInt(1).array()
        var u = mac.doFinal(buf)
        val t = u.clone()
        for (i in 2..iterations) {
            u = mac.doFinal(u)
            for (j in t.indices) {
                t[j] = (t[j].toInt() xor u[j].toInt()).toByte()
            }
        }
        System.arraycopy(t, 0, result, 0, minOf(keyLength, t.size))
        return result
    }

    private fun sha256Hex(str: String): String {
        val md = MessageDigest.getInstance("SHA-256")
        val bytes = md.digest(str.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }

    private fun sha256Bytes(bytes: ByteArray): ByteArray {
        return MessageDigest.getInstance("SHA-256").digest(bytes)
    }
}
