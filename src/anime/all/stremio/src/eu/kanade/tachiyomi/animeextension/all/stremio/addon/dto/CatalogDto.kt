package eu.kanade.tachiyomi.animeextension.all.stremio.addon.dto

import kotlinx.serialization.KSerializer
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.Transient
import kotlinx.serialization.descriptors.PrimitiveKind
import kotlinx.serialization.descriptors.PrimitiveSerialDescriptor
import kotlinx.serialization.descriptors.SerialDescriptor
import kotlinx.serialization.encoding.Decoder
import kotlinx.serialization.encoding.Encoder
import kotlinx.serialization.json.JsonDecoder
import kotlinx.serialization.json.JsonPrimitive

object AnyStringSerializer : KSerializer<String> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("AnyString", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: String) {
        encoder.encodeString(value)
    }

    override fun deserialize(decoder: Decoder): String {
        val input = decoder as? JsonDecoder
        if (input != null) {
            val element = input.decodeJsonElement()
            if (element is JsonPrimitive) {
                return element.content
            }
            return element.toString()
        }
        return decoder.decodeString()
    }
}

@Serializable(with = ExtraTypeSerializer::class)
enum class ExtraType {
    GENRE,
    SEARCH,
    SKIP,
    UNKNOWN,

    ;

    companion object {
        fun fromString(value: String): ExtraType {
            return ExtraType.values().find { it.name.equals(value, ignoreCase = true) } ?: UNKNOWN
        }
    }
}

object ExtraTypeSerializer : KSerializer<ExtraType> {
    override val descriptor: SerialDescriptor = PrimitiveSerialDescriptor("ExtraType", PrimitiveKind.STRING)

    override fun serialize(encoder: Encoder, value: ExtraType) {
        encoder.encodeString(value.name)
    }

    override fun deserialize(decoder: Decoder): ExtraType {
        return ExtraType.fromString(decoder.decodeString())
    }
}

@Serializable
data class CatalogDto(
    val id: String,
    val type: String,
    val name: String? = null,
    val extra: List<ExtraDto>? = null,

    @Transient
    var transportUrl: String = "",
) {
    @Serializable
    data class ExtraDto(
        @SerialName("name")
        val type: ExtraType,
        val isRequired: Boolean? = null,
        val options: List<@Serializable(with = AnyStringSerializer::class) String>? = null,
    )

    fun hasRequired(type: ExtraType): Boolean {
        return extra.orEmpty().any {
            it.type == type && it.isRequired == true
        }
    }
}
