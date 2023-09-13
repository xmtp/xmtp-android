package org.xmtp.android.library.codecs

import com.google.gson.GsonBuilder
import com.google.protobuf.kotlin.toByteStringUtf8
import java.util.Locale

val ContentTypeReaction = ContentTypeIdBuilder.builderFromAuthorityId(
    "xmtp.org",
    "reaction",
    versionMajor = 1,
    versionMinor = 0
)

data class Reaction(
    val reference: String,
    val action: ReactionAction,
    val content: String,
    val schema: ReactionSchema,
)

sealed class ReactionAction {
    object Removed : ReactionAction()
    object Added : ReactionAction()
    object Unknown : ReactionAction()
}

sealed class ReactionSchema {
    object Unicode : ReactionSchema()
    object Shortcode : ReactionSchema()
    object Custom : ReactionSchema()
    object Unknown : ReactionSchema()
}

private fun getReactionSchema(schema: String): ReactionSchema {
    return when (schema) {
        "unicode" -> ReactionSchema.Unicode
        "shortcode" -> ReactionSchema.Shortcode
        "custom" -> ReactionSchema.Custom
        else -> ReactionSchema.Unknown
    }
}

private fun getReactionAction(action: String): ReactionAction {
    return when (action) {
        "removed" -> ReactionAction.Removed
        "added" -> ReactionAction.Added
        else -> ReactionAction.Unknown
    }
}

data class ReactionCodec(override var contentType: ContentTypeId = ContentTypeReaction) :
    ContentCodec<Reaction> {

    override fun encode(content: Reaction): EncodedContent {
        val gson = GsonBuilder().create()
        return EncodedContent.newBuilder().also {
            it.type = ContentTypeReaction
            it.content = gson.toJson(content).toByteStringUtf8()
        }.build()
    }

    override fun decode(content: EncodedContent): Reaction {
        val text = content.content.toStringUtf8()

        // First try to decode it in the canonical form.
        try {
            return GsonBuilder().create().fromJson(text, Reaction::class.java)
        } catch (ignore: Exception) {
        }

        // If that fails, try to decode it in the legacy form.
        return Reaction(
            reference = content.parametersMap["reference"] ?: "",
            action = getReactionAction(content.parametersMap["action"]?.lowercase(Locale.ROOT) ?: ""),
            schema = getReactionSchema(content.parametersMap["schema"]?.lowercase(Locale.ROOT) ?: ""),
            content = text,
        )
    }

    override fun fallback(content: Reaction): String? {
        return when (content.action) {
            ReactionAction.Added -> "Reacted “${content.content}” to an earlier message"
            ReactionAction.Removed -> "Removed “${content.content}” from an earlier message"
            else -> null
        }
    }
}
