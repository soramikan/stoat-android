package chat.stoat.internals.extensions

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableLongState
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.Roles
import chat.stoat.api.routes.server.fetchMember

@Composable
fun rememberChannelPermissions(channelId: String, key1: Any = Unit): MutableLongState {
    val permissions = rememberSaveable { mutableLongStateOf(0L) }
    val selfId = StoatAPI.selfId
    val channel = StoatAPI.channelCache[channelId]

    LaunchedEffect(channelId, key1, selfId, channel) {
        if (StoatAPI.selfId == null) return@LaunchedEffect
        if (StoatAPI.userCache[StoatAPI.selfId] == null) return@LaunchedEffect
        if (StoatAPI.channelCache[channelId] == null) return@LaunchedEffect

        val channel = StoatAPI.channelCache[channelId]
        val selfUser = StoatAPI.userCache[StoatAPI.selfId]
        val member = channel?.let {
            it.server?.let { server ->
                StoatAPI.selfId?.let { selfId ->
                    StoatAPI.members.getMember(server, selfId)
                }
            }
        }
        channel?.let { permissions.longValue = Roles.permissionFor(it, selfUser, member) }
    }

    return permissions
}

@Composable
fun rememberServerPermissions(serverId: String, key1: Any = Unit): MutableLongState {
    val permissions = rememberSaveable { mutableLongStateOf(0L) }
    val selfId = StoatAPI.selfId
    val server = StoatAPI.serverCache[serverId]

    LaunchedEffect(serverId, key1, selfId, server) {
        if (selfId == null) return@LaunchedEffect
        if (StoatAPI.userCache[selfId] == null) return@LaunchedEffect

        val currentServer = StoatAPI.serverCache[serverId] ?: return@LaunchedEffect
        if (currentServer.owner == selfId) {
            permissions.longValue = PermissionBit.GrantAllSafe.value
            return@LaunchedEffect
        }

        val member = StoatAPI.members.getMember(serverId, selfId)
            ?: runCatching { fetchMember(serverId, selfId) }.getOrNull()
            ?: return@LaunchedEffect

        permissions.longValue = Roles.permissionFor(currentServer, member)
    }

    return permissions
}
