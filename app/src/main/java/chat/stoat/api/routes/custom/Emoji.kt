package chat.stoat.api.routes.custom

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.core.model.schemas.Emoji
import chat.stoat.core.model.schemas.EmojiParent
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.HttpResponse
import io.ktor.client.statement.bodyAsText
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer

@Serializable
private data class CreateEmojiBody(
    val name: String,
    val parent: EmojiParent,
    val nsfw: Boolean
)

private suspend fun bodyOrThrow(response: HttpResponse): String {
    val body = response.bodyAsText()
    if (response.status.value !in 200..299) {
        val error = runCatching {
            StoatJson.decodeFromString(StoatAPIError.serializer(), body)
        }.getOrNull()

        throw Exception(error?.type ?: response.status.description)
    }
    return body
}

suspend fun fetchEmoji(id: String): Emoji {
    val response = bodyOrThrow(StoatHttp.get("/custom/emoji/$id".api()))
    val emoji = StoatJson.decodeFromString(
        Emoji.serializer(),
        response
    )
    emoji.id?.let { StoatAPI.emojiCache[it] = emoji }
    return emoji
}

suspend fun fetchServerEmojis(serverId: String): List<Emoji> {
    val response = bodyOrThrow(StoatHttp.get("/servers/$serverId/emojis".api()))

    val emojis = StoatJson.decodeFromString(
        ListSerializer(Emoji.serializer()),
        response
    )
    emojis.forEach { emoji ->
        emoji.id?.let { StoatAPI.emojiCache[it] = emoji }
    }
    return emojis
}

suspend fun uploadEmoji(
    autumnId: String,
    name: String,
    serverId: String,
    nsfw: Boolean = false
): Emoji {
    val response = bodyOrThrow(StoatHttp.put("/custom/emoji/$autumnId".api()) {
        setBody(
            CreateEmojiBody(
                name = name,
                parent = EmojiParent(type = "Server", id = serverId),
                nsfw = nsfw
            )
        )
    })

    val emoji = StoatJson.decodeFromString(Emoji.serializer(), response)
    emoji.id?.let { StoatAPI.emojiCache[it] = emoji }
    return emoji
}

suspend fun deleteEmoji(id: String) {
    bodyOrThrow(StoatHttp.delete("/custom/emoji/$id".api()))
    StoatAPI.emojiCache.remove(id)
}
