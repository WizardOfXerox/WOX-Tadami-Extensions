package eu.kanade.tachiyomi.animeextension.all.stremio

import eu.kanade.tachiyomi.animesource.model.SAnime
import eu.kanade.tachiyomi.animesource.model.Video
import okhttp3.Headers
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

fun String.urlEncode(): String {
    return URLEncoder.encode(this, "UTF-8")
}

fun String.takeNotBlank(): String? {
    return this.takeIf { it.isNotBlank() }
}

enum class FetchType {
    Episodes,
    Seasons,
}

data class Hoster(
    val hosterUrl: String,
    val hosterName: String,
    val internalData: String = "",
)

private val videoInternalDataMap = ConcurrentHashMap<Video, String>()

var Video.internalData: String
    get() = videoInternalDataMap[this] ?: ""
    set(value) {
        videoInternalDataMap[this] = value
    }

fun Video(
    videoTitle: String,
    videoUrl: String,
    headers: Headers? = null,
    internalData: String? = null,
): Video {
    val video = Video(url = videoUrl, quality = videoTitle, videoUrl = videoUrl, headers = headers)
    if (internalData != null) {
        video.internalData = internalData
    }
    return video
}

private val seasonNumberMap = ConcurrentHashMap<SAnime, Double>()

var SAnime.season_number: Double
    get() = try {
        javaClass.getField("season_number").getDouble(this)
    } catch (_: Throwable) {
        seasonNumberMap[this] ?: 0.0
    }
    set(value) {
        try {
            javaClass.getField("season_number").setDouble(this, value)
        } catch (_: Throwable) {
            seasonNumberMap[this] = value
        }
    }

private val fetchTypeMap = ConcurrentHashMap<SAnime, FetchType>()

var SAnime.fetch_type: FetchType
    get() = try {
        FetchType.valueOf(javaClass.getField("fetch_type").get(this).toString())
    } catch (_: Throwable) {
        fetchTypeMap[this] ?: FetchType.Episodes
    }
    set(value) {
        try {
            val field = javaClass.getField("fetch_type")
            @Suppress("UNCHECKED_CAST")
            val targetEnum = java.lang.Enum.valueOf(field.type as Class<out Enum<*>>, value.name)
            field.set(this, targetEnum)
        } catch (_: Throwable) {
            fetchTypeMap[this] = value
        }
    }

var SAnime.background_url: String?
    get() = try {
        javaClass.getField("background_url").get(this) as? String
    } catch (_: Throwable) { null }
    set(value) {
        try {
            javaClass.getField("background_url").set(this, value)
        } catch (_: Throwable) {}
    }

private val episodeSummaryMap = ConcurrentHashMap<eu.kanade.tachiyomi.animesource.model.SEpisode, String>()

var eu.kanade.tachiyomi.animesource.model.SEpisode.summary: String?
    get() = try {
        javaClass.getField("summary").get(this) as? String
    } catch (_: Throwable) {
        episodeSummaryMap[this]
    }
    set(value) {
        try {
            javaClass.getField("summary").set(this, value)
        } catch (_: Throwable) {
            if (value != null) episodeSummaryMap[this] = value else episodeSummaryMap.remove(this)
        }
    }

private val episodePreviewUrlMap = ConcurrentHashMap<eu.kanade.tachiyomi.animesource.model.SEpisode, String>()

var eu.kanade.tachiyomi.animesource.model.SEpisode.preview_url: String?
    get() = try {
        javaClass.getField("preview_url").get(this) as? String
    } catch (_: Throwable) {
        episodePreviewUrlMap[this]
    }
    set(value) {
        try {
            javaClass.getField("preview_url").set(this, value)
        } catch (_: Throwable) {
            if (value != null) episodePreviewUrlMap[this] = value else episodePreviewUrlMap.remove(this)
        }
    }
