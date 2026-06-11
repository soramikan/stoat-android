package chat.stoat.core.model.schemas

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ChannelWebhook(
    val id: String? = null,
    val name: String? = null,
    val avatar: AutumnResource? = null,
    @SerialName("creator_id")
    val creatorId: String? = null,
    @SerialName("channel_id")
    val channelId: String? = null,
    val permissions: Long? = null,
    val token: String? = null
)
