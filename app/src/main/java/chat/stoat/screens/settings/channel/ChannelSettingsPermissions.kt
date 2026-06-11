package chat.stoat.screens.settings.channel

import androidx.annotation.StringRes
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.BitDefaults
import chat.stoat.api.internals.BrushCompat
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.hasPermission
import chat.stoat.api.internals.solidColor
import chat.stoat.api.routes.channel.setDefaultChannelPermissions
import chat.stoat.api.routes.channel.setRoleChannelPermissions
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.PermissionDescription
import chat.stoat.core.model.schemas.Role
import chat.stoat.screens.settings.SettingsIcon
import kotlinx.coroutines.launch

private enum class PermissionState {
    Unset,
    Allow,
    Deny
}

private data class PermissionOption(
    val bit: PermissionBit,
    @param:StringRes val title: Int
)

private sealed class PermissionTarget {
    data object Default : PermissionTarget()
    data class RoleTarget(val id: String, val role: Role) : PermissionTarget()
}

private val channelPermissionOptions = listOf(
    PermissionOption(PermissionBit.ViewChannel, R.string.permission_view_channel),
    PermissionOption(PermissionBit.ReadMessageHistory, R.string.permission_read_message_history),
    PermissionOption(PermissionBit.SendMessage, R.string.permission_send_message),
    PermissionOption(PermissionBit.ManageMessages, R.string.permission_manage_messages),
    PermissionOption(PermissionBit.ManageWebhooks, R.string.permission_manage_webhooks),
    PermissionOption(PermissionBit.InviteOthers, R.string.permission_invite_others),
    PermissionOption(PermissionBit.SendEmbeds, R.string.permission_send_embeds),
    PermissionOption(PermissionBit.UploadFiles, R.string.permission_upload_files),
    PermissionOption(PermissionBit.Masquerade, R.string.permission_masquerade),
    PermissionOption(PermissionBit.React, R.string.permission_react),
    PermissionOption(PermissionBit.BypassSlowmode, R.string.permission_bypass_slowmode),
    PermissionOption(PermissionBit.Connect, R.string.permission_connect),
    PermissionOption(PermissionBit.Speak, R.string.permission_speak),
    PermissionOption(PermissionBit.Video, R.string.permission_video),
    PermissionOption(PermissionBit.MuteMembers, R.string.permission_mute_members),
    PermissionOption(PermissionBit.DeafenMembers, R.string.permission_deafen_members),
    PermissionOption(PermissionBit.MoveMembers, R.string.permission_move_members),
    PermissionOption(PermissionBit.Listen, R.string.permission_listen),
    PermissionOption(PermissionBit.MentionEveryone, R.string.permission_mention_everyone),
    PermissionOption(PermissionBit.MentionRoles, R.string.permission_mention_roles),
    PermissionOption(PermissionBit.ManageChannel, R.string.permission_manage_channel),
    PermissionOption(PermissionBit.ManagePermissions, R.string.permission_manage_permissions)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChannelSettingsPermissions(navController: NavController, channelId: String) {
    val channel = StoatAPI.channelCache[channelId]
    val server = channel?.server?.let { StoatAPI.serverCache[it] }
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var selectedTarget by remember(channelId) { mutableStateOf<PermissionTarget?>(null) }
    var groupPermissions by remember(channelId, channel?.permissions) {
        mutableStateOf(channel?.permissions ?: BitDefaults.DirectMessages)
    }
    var groupError by remember(channelId) { mutableStateOf(false) }
    var isSavingGroup by remember(channelId) { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = stringResource(R.string.channel_settings_permissions),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = { navController.popBackStack() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_arrow_back_24dp),
                            contentDescription = stringResource(id = R.string.back)
                        )
                    }
                },
                actions = {
                    if (channel?.channelType == ChannelType.Group) {
                        IconButton(
                            enabled = !isSavingGroup,
                            onClick = {
                                isSavingGroup = true
                                groupError = false
                                scope.launch {
                                    runCatching {
                                        setDefaultChannelPermissions(channelId, groupPermissions)
                                    }.onFailure {
                                        groupError = true
                                    }
                                    isSavingGroup = false
                                }
                            }
                        ) {
                            Icon(
                                painter = painterResource(R.drawable.ic_check_24dp),
                                contentDescription = stringResource(
                                    R.string.channel_settings_permissions_save
                                )
                            )
                        }
                    }
                }
            )
        },
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            channel?.let { currentChannel ->
                when (currentChannel.channelType) {
                    ChannelType.Group -> {
                        GroupChannelPermissionsEditor(
                            permissions = groupPermissions,
                            hasError = groupError,
                            onPermissionsChange = {
                                groupPermissions = it
                                groupError = false
                            }
                        )
                    }

                    ChannelType.TextChannel,
                    ChannelType.VoiceChannel -> {
                        ServerChannelPermissionsList(
                            channel = currentChannel,
                            roles = server?.roles.orEmpty(),
                            onSelect = { selectedTarget = it }
                        )
                    }

                    else -> {
                        Box(modifier = Modifier.fillMaxSize()) {
                            Text(
                                text = stringResource(
                                    R.string.channel_settings_permissions_unavailable
                                ),
                                modifier = Modifier.align(Alignment.Center),
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    selectedTarget?.let { target ->
        val currentChannel = channel ?: return@let
        val initialPermissions = when (target) {
            PermissionTarget.Default -> currentChannel.defaultPermissions ?: PermissionDescription(0, 0)
            is PermissionTarget.RoleTarget -> currentChannel.rolePermissions?.get(target.id)
                ?: PermissionDescription(0, 0)
        }

        PermissionOverwriteSheet(
            channelId = channelId,
            target = target,
            initialPermissions = initialPermissions,
            onDismiss = { selectedTarget = null }
        )
    }
}

@Composable
private fun ServerChannelPermissionsList(
    channel: Channel,
    roles: Map<String, Role>,
    onSelect: (PermissionTarget) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        ListItem(
            headlineContent = {
                Text(stringResource(R.string.channel_settings_permissions_default))
            },
            supportingContent = {
                Text(stringResource(R.string.channel_settings_permissions_default_description))
            },
            leadingContent = {
                SettingsIcon {
                    Icon(
                        painter = painterResource(R.drawable.ic_list_24dp),
                        contentDescription = null
                    )
                }
            },
            modifier = Modifier
                .testTag("channel_settings_permissions_default")
                .clickable { onSelect(PermissionTarget.Default) }
        )

        Text(
            text = stringResource(R.string.channel_settings_permissions_roles, roles.size),
            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
            style = MaterialTheme.typography.labelLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        roles.entries.sortedBy { it.value.rank ?: 0.0 }.forEach { (roleId, role) ->
            ListItem(
                headlineContent = {
                    Text(
                        text = role.name ?: stringResource(R.string.unknown),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                },
                supportingContent = {
                    val overwrite = channel.rolePermissions?.get(roleId)
                    Text(
                        if (overwrite == null || (overwrite.a == 0L && overwrite.d == 0L)) {
                            stringResource(R.string.channel_settings_permissions_no_overwrite)
                        } else {
                            stringResource(R.string.channel_settings_permissions_has_overwrite)
                        }
                    )
                },
                leadingContent = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(18.dp)
                            .background(
                                brush = role.colour?.let { BrushCompat.parseColour(it) }
                                    ?: Brush.solidColor(MaterialTheme.colorScheme.outlineVariant)
                            )
                    )
                },
                modifier = Modifier
                    .testTag("channel_settings_permissions_role_$roleId")
                    .clickable { onSelect(PermissionTarget.RoleTarget(roleId, role)) }
            )
        }
    }
}

@Composable
private fun GroupChannelPermissionsEditor(
    permissions: Long,
    hasError: Boolean,
    onPermissionsChange: (Long) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
    ) {
        if (hasError) {
            Text(
                text = stringResource(R.string.channel_settings_permissions_update_error),
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )
        }

        channelPermissionOptions.forEach { option ->
            ListItem(
                headlineContent = {
                    Text(stringResource(option.title))
                },
                trailingContent = {
                    Switch(
                        checked = permissions.hasPermission(option.bit),
                        onCheckedChange = { checked ->
                            onPermissionsChange(
                                if (checked) {
                                    permissions or option.bit.value
                                } else {
                                    permissions and option.bit.value.inv()
                                }
                            )
                        }
                    )
                }
            )
            HorizontalDivider()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun PermissionOverwriteSheet(
    channelId: String,
    target: PermissionTarget,
    initialPermissions: PermissionDescription,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    var allow by remember(channelId, target) { mutableStateOf(initialPermissions.a) }
    var deny by remember(channelId, target) { mutableStateOf(initialPermissions.d) }
    var isSaving by remember(channelId, target) { mutableStateOf(false) }
    var hasError by remember(channelId, target) { mutableStateOf(false) }
    val title = when (target) {
        PermissionTarget.Default -> stringResource(R.string.channel_settings_permissions_default)
        is PermissionTarget.RoleTarget -> target.role.name ?: stringResource(R.string.unknown)
    }

    ModalBottomSheet(
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        onDismissRequest = onDismiss
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            channelPermissionOptions.forEach { option ->
                PermissionOverwriteRow(
                    option = option,
                    state = permissionState(allow, deny, option.bit),
                    onStateChange = { state ->
                        val updated = setPermissionState(allow, deny, option.bit, state)
                        allow = updated.first
                        deny = updated.second
                        hasError = false
                    }
                )
                HorizontalDivider()
            }

            if (hasError) {
                Text(
                    text = stringResource(R.string.channel_settings_permissions_update_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                enabled = !isSaving,
                onClick = {
                    val permissions = PermissionDescription(allow, deny)
                    isSaving = true
                    hasError = false
                    scope.launch {
                        runCatching {
                            when (target) {
                                PermissionTarget.Default -> setDefaultChannelPermissions(
                                    channelId,
                                    permissions
                                )

                                is PermissionTarget.RoleTarget -> setRoleChannelPermissions(
                                    channelId,
                                    target.id,
                                    permissions
                                )
                            }
                        }.onSuccess {
                            onDismiss()
                        }.onFailure {
                            hasError = true
                        }
                        isSaving = false
                    }
                },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_check_24dp),
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    if (isSaving) {
                        stringResource(R.string.channel_settings_permissions_saving)
                    } else {
                        stringResource(R.string.channel_settings_permissions_save)
                    }
                )
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun PermissionOverwriteRow(
    option: PermissionOption,
    state: PermissionState,
    onStateChange: (PermissionState) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        Text(
            text = stringResource(option.title),
            style = MaterialTheme.typography.bodyLarge
        )
        Spacer(Modifier.height(8.dp))
        Row(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            PermissionStateButton(
                selected = state == PermissionState.Unset,
                text = stringResource(R.string.server_settings_roles_permission_unset),
                onClick = { onStateChange(PermissionState.Unset) },
                modifier = Modifier.weight(1f)
            )
            PermissionStateButton(
                selected = state == PermissionState.Allow,
                text = stringResource(R.string.server_settings_roles_permission_allow),
                onClick = { onStateChange(PermissionState.Allow) },
                modifier = Modifier.weight(1f)
            )
            PermissionStateButton(
                selected = state == PermissionState.Deny,
                text = stringResource(R.string.server_settings_roles_permission_deny),
                onClick = { onStateChange(PermissionState.Deny) },
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
private fun PermissionStateButton(
    selected: Boolean,
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    if (selected) {
        Button(onClick = onClick, modifier = modifier) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    } else {
        OutlinedButton(onClick = onClick, modifier = modifier) {
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private fun permissionState(allow: Long, deny: Long, bit: PermissionBit): PermissionState {
    return when {
        allow.hasPermission(bit) -> PermissionState.Allow
        deny.hasPermission(bit) -> PermissionState.Deny
        else -> PermissionState.Unset
    }
}

private fun setPermissionState(
    allow: Long,
    deny: Long,
    bit: PermissionBit,
    state: PermissionState
): Pair<Long, Long> {
    return when (state) {
        PermissionState.Allow -> (allow or bit.value) to (deny and bit.value.inv())
        PermissionState.Deny -> (allow and bit.value.inv()) to (deny or bit.value)
        PermissionState.Unset -> (allow and bit.value.inv()) to (deny and bit.value.inv())
    }
}
