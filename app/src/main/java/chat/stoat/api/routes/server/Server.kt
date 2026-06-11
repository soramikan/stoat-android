package chat.stoat.api.routes.server

import chat.stoat.api.StoatAPI
import chat.stoat.api.StoatAPIError
import chat.stoat.api.StoatHttp
import chat.stoat.api.StoatJson
import chat.stoat.api.api
import chat.stoat.api.routes.channel.CreateInviteResponse
import chat.stoat.core.model.schemas.Category
import chat.stoat.core.model.schemas.Member
import chat.stoat.core.model.schemas.PermissionDescription
import chat.stoat.core.model.schemas.Role
import chat.stoat.core.model.schemas.Server
import chat.stoat.core.model.schemas.ServerUserChoice
import chat.stoat.core.model.schemas.ServerWithChannelObjects
import chat.stoat.core.model.schemas.User
import io.ktor.client.request.delete
import io.ktor.client.request.get
import io.ktor.client.request.parameter
import io.ktor.client.request.patch
import io.ktor.client.request.post
import io.ktor.client.request.put
import io.ktor.client.request.setBody
import io.ktor.client.statement.bodyAsText
import io.ktor.http.ContentType
import io.ktor.http.contentType
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.SerializationException
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.JsonElement

@Serializable
data class FetchMembersResponse(
    val members: List<Member>,
    val users: List<User>
)

@Serializable
data class Ban(
    @SerialName("_id")
    val id: ServerUserChoice,
    val reason: String? = null
)

@Serializable
data class BansResponse(
    val users: List<User>,
    val bans: List<Ban>
)

@Serializable
data class RoleWithId(
    val id: String,
    val role: Role
)

@Serializable
data class RoleEditBody(
    val name: String? = null,
    val colour: String? = null,
    val hoist: Boolean? = null,
    val rank: Double? = null,
    val remove: List<String>? = null
)

@Serializable
private data class CreateRoleBody(
    val name: String
)

@Serializable
private data class ServerPermissionsBody(
    val permissions: Long
)

@Serializable
private data class RolePermissionsBody(
    val permissions: PermissionDescription
)

suspend fun ackServer(serverId: String) {
    StoatHttp.put("/servers/$serverId/ack".api())
}

suspend fun fetchMembers(
    serverId: String,
    includeOffline: Boolean = false,
    pure: Boolean = false
): FetchMembersResponse {
    val response = StoatHttp.get("/servers/$serverId/members".api()) {
        parameter("exclude_offline", !includeOffline)
    }

    val responseContent = response.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), responseContent)
        throw Error(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val membersResponse =
        StoatJson.decodeFromString(FetchMembersResponse.serializer(), responseContent)

    if (pure) {
        return membersResponse
    }

    membersResponse.members.forEach { member ->
        if (!StoatAPI.members.hasMember(serverId, member.id!!.user)) {
            StoatAPI.members.setMember(serverId, member)
        }
    }

    membersResponse.users.forEach { user ->
        user.id?.let { StoatAPI.userCache.putIfAbsent(it, user) }
    }

    return membersResponse
}

suspend fun fetchMember(serverId: String, userId: String, pure: Boolean = false): Member {
    val response = StoatHttp.get("/servers/$serverId/members/$userId".api())

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response.bodyAsText())
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val member = StoatJson.decodeFromString(Member.serializer(), response.bodyAsText())

    if (!pure) {
        member.id?.let {
            if (!StoatAPI.members.hasMember(serverId, it.user)) {
                StoatAPI.members.setMember(serverId, member)
            }
        }
    }

    return member
}

suspend fun fetchBans(serverId: String): BansResponse {
    val response = StoatHttp.get("/servers/$serverId/bans".api())
        .bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val bansResponse = StoatJson.decodeFromString(BansResponse.serializer(), response)
    bansResponse.users.forEach { user ->
        user.id?.let { StoatAPI.userCache.putIfAbsent(it, user) }
    }
    return bansResponse
}

suspend fun unbanUser(serverId: String, userId: String) {
    StoatHttp.delete("/servers/$serverId/bans/$userId".api())
}

suspend fun fetchInvites(serverId: String): List<CreateInviteResponse> {
    val response = StoatHttp.get("/servers/$serverId/invites".api())
        .bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    return StoatJson.decodeFromString(
        ListSerializer(CreateInviteResponse.serializer()),
        response
    )
}

suspend fun deleteInvite(code: String) {
    StoatHttp.delete("/invites/$code".api())
}

suspend fun createRole(serverId: String, name: String): RoleWithId {
    val response = StoatHttp.post("/servers/$serverId/roles".api()) {
        setBody(CreateRoleBody(name))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val roleWithId = StoatJson.decodeFromString(RoleWithId.serializer(), response)
    val server = StoatAPI.serverCache[serverId]
    if (server != null) {
        StoatAPI.serverCache[serverId] = server.copy(
            roles = server.roles?.plus(roleWithId.id to roleWithId.role)
                ?: mapOf(roleWithId.id to roleWithId.role)
        )
    }

    return roleWithId
}

suspend fun editRole(
    serverId: String,
    roleId: String,
    body: RoleEditBody
): Role {
    val response = StoatHttp.patch("/servers/$serverId/roles/$roleId".api()) {
        setBody(body)
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val role = StoatJson.decodeFromString(Role.serializer(), response)
    val server = StoatAPI.serverCache[serverId]
    if (server != null) {
        StoatAPI.serverCache[serverId] = server.copy(
            roles = server.roles?.plus(roleId to role) ?: mapOf(roleId to role)
        )
    }

    return role
}

suspend fun setRolePermissions(
    serverId: String,
    roleId: String,
    permissions: PermissionDescription
): Server {
    val response = StoatHttp.put("/servers/$serverId/permissions/$roleId".api()) {
        setBody(RolePermissionsBody(permissions))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val server = StoatJson.decodeFromString(Server.serializer(), response)
    StoatAPI.serverCache[serverId] = server
    return server
}

suspend fun setDefaultRolePermissions(serverId: String, permissions: Long): Server {
    val response = StoatHttp.put("/servers/$serverId/permissions/default".api()) {
        setBody(ServerPermissionsBody(permissions))
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val server = StoatJson.decodeFromString(Server.serializer(), response)
    StoatAPI.serverCache[serverId] = server
    return server
}

suspend fun deleteRole(serverId: String, roleId: String) {
    val response = StoatHttp.delete("/servers/$serverId/roles/$roleId".api())
        .bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    val server = StoatAPI.serverCache[serverId]
    if (server != null) {
        val roles = server.roles?.toMutableMap() ?: mutableMapOf()
        roles.remove(roleId)
        StoatAPI.serverCache[serverId] = server.copy(roles = roles)
    }
}

suspend fun leaveOrDeleteServer(serverId: String, leaveSilently: Boolean = false) {
    StoatHttp.delete("/servers/$serverId".api()) {
        parameter("leave_silently", leaveSilently)
    }
}

suspend fun patchServer(
    serverId: String,
    name: String? = null,
    description: String? = null,
    icon: String? = null,
    banner: String? = null,
    categories: List<Category>? = null,
    remove: List<String>? = null,
    pure: Boolean = false
) {
    val body = mutableMapOf<String, JsonElement>()

    if (name != null) {
        body["name"] = StoatJson.encodeToJsonElement(String.serializer(), name)
    }

    if (description != null) {
        body["description"] = StoatJson.encodeToJsonElement(String.serializer(), description)
    }

    if (icon != null) {
        body["icon"] = StoatJson.encodeToJsonElement(String.serializer(), icon)
    }

    if (banner != null) {
        body["banner"] = StoatJson.encodeToJsonElement(String.serializer(), banner)
    }

    if (categories != null) {
        body["categories"] = StoatJson.encodeToJsonElement(
            ListSerializer(Category.serializer()),
            categories
        )
    }

    if (remove != null) {
        body["remove"] = StoatJson.encodeToJsonElement(ListSerializer(String.serializer()), remove)
    }

    val response = StoatHttp.patch("/servers/$serverId".api()) {
        contentType(ContentType.Application.Json)
        setBody(
            StoatJson.encodeToString(
                MapSerializer(
                    String.serializer(),
                    JsonElement.serializer()
                ),
                body
            )
        )
    }.bodyAsText()

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response)
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    if (!pure) {
        val server = StoatJson.decodeFromString(Server.serializer(), response)
        StoatAPI.serverCache[serverId] = server
    }
}

@Serializable
data class ServerCreationBody(
    val name: String,
    val description: String? = null,
    val nsfw: Boolean = false
)

suspend fun createServer(
    name: String,
    description: String = "",
    nsfw: Boolean = false
): ServerWithChannelObjects {
    val body = ServerCreationBody(name, description, nsfw)

    val response = StoatHttp.post("/servers/create".api()) {
        setBody(StoatJson.encodeToString(ServerCreationBody.serializer(), body))
    }

    try {
        val error = StoatJson.decodeFromString(StoatAPIError.serializer(), response.bodyAsText())
        throw Exception(error.type)
    } catch (e: SerializationException) {
        // Not an error
    }

    return StoatJson.decodeFromString(ServerWithChannelObjects.serializer(), response.bodyAsText())
}
