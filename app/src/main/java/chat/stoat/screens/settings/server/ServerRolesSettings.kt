package chat.stoat.screens.settings.server

import android.graphics.Color as AndroidColor
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
import androidx.compose.material3.AlertDialog
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
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb
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
import chat.stoat.api.routes.server.RoleEditBody
import chat.stoat.api.routes.server.createRole
import chat.stoat.api.routes.server.deleteRole
import chat.stoat.api.routes.server.editRole
import chat.stoat.api.routes.server.setDefaultRolePermissions
import chat.stoat.api.routes.server.setRolePermissions
import chat.stoat.core.model.schemas.PermissionDescription
import chat.stoat.core.model.schemas.Role
import chat.stoat.screens.settings.SettingsIcon
import chat.stoat.sheets.ColourPickerSheet
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

private val rolePermissionOptions = listOf(
    PermissionOption(PermissionBit.ManageChannel, R.string.permission_manage_channel),
    PermissionOption(PermissionBit.ManageServer, R.string.permission_manage_server),
    PermissionOption(PermissionBit.ManagePermissions, R.string.permission_manage_permissions),
    PermissionOption(PermissionBit.ManageRole, R.string.permission_manage_role),
    PermissionOption(PermissionBit.ManageCustomisation, R.string.permission_manage_customisation),
    PermissionOption(PermissionBit.KickMembers, R.string.permission_kick_members),
    PermissionOption(PermissionBit.BanMembers, R.string.permission_ban_members),
    PermissionOption(PermissionBit.TimeoutMembers, R.string.permission_timeout_members),
    PermissionOption(PermissionBit.AssignRoles, R.string.permission_assign_roles),
    PermissionOption(PermissionBit.ChangeNickname, R.string.permission_change_nickname),
    PermissionOption(PermissionBit.ManageNicknames, R.string.permission_manage_nicknames),
    PermissionOption(PermissionBit.ChangeAvatar, R.string.permission_change_avatar),
    PermissionOption(PermissionBit.RemoveAvatars, R.string.permission_remove_avatars),
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
    PermissionOption(PermissionBit.MentionRoles, R.string.permission_mention_roles)
)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerRolesSettings(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var showCreateRole by remember { mutableStateOf(false) }
    var selectedRoleId by remember { mutableStateOf<String?>(null) }
    var showDefaultPermissions by remember { mutableStateOf(false) }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = server?.name?.let {
                            stringResource(R.string.server_settings_roles_header, it)
                        } ?: stringResource(R.string.server_settings_roles),
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
            )
        },
    ) { pv ->
        Box(
            Modifier
                .padding(pv)
                .imePadding()
        ) {
            server?.let {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.server_settings_roles_default))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.server_settings_roles_default_description))
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_list_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("server_settings_roles_default")
                            .clickable { showDefaultPermissions = true }
                    )

                    ListItem(
                        headlineContent = {
                            Text(stringResource(R.string.server_settings_roles_create))
                        },
                        supportingContent = {
                            Text(stringResource(R.string.server_settings_roles_create_description))
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_group_add_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("server_settings_roles_create")
                            .clickable { showCreateRole = true }
                    )

                    Text(
                        text = stringResource(
                            R.string.server_settings_roles_count,
                            server.roles?.size ?: 0
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    server.roles
                        .orEmpty()
                        .entries
                        .sortedBy { it.value.rank ?: 0.0 }
                        .forEach { (roleId, role) ->
                            ListItem(
                                headlineContent = {
                                    Text(
                                        text = role.name ?: stringResource(R.string.unknown),
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                },
                                supportingContent = {
                                    Text(
                                        stringResource(
                                            R.string.server_settings_roles_rank,
                                            role.rank?.toString() ?: "0"
                                        )
                                    )
                                },
                                leadingContent = {
                                    Box(
                                        modifier = Modifier
                                            .clip(CircleShape)
                                            .size(18.dp)
                                            .background(
                                                brush = role.colour?.let { BrushCompat.parseColour(it) }
                                                    ?: Brush.solidColor(
                                                        MaterialTheme.colorScheme.outlineVariant
                                                    )
                                            )
                                    )
                                },
                                modifier = Modifier
                                    .testTag("server_settings_role_$roleId")
                                    .clickable { selectedRoleId = roleId }
                            )
                        }
                }
            } ?: run {
                Box(modifier = Modifier.fillMaxSize()) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }

    if (showCreateRole) {
        CreateRoleDialog(
            serverId = serverId,
            onDismiss = { showCreateRole = false },
            onCreated = { roleId ->
                showCreateRole = false
                selectedRoleId = roleId
            }
        )
    }

    selectedRoleId?.let { roleId ->
        val role = server?.roles?.get(roleId)
        if (role != null) {
            RoleEditorSheet(
                serverId = serverId,
                roleId = roleId,
                role = role,
                onDismiss = { selectedRoleId = null }
            )
        }
    }

    if (showDefaultPermissions && server != null) {
        DefaultPermissionsSheet(
            serverId = serverId,
            initialPermissions = server.defaultPermissions ?: BitDefaults.Server,
            onDismiss = { showDefaultPermissions = false }
        )
    }
}

@Composable
private fun CreateRoleDialog(
    serverId: String,
    onDismiss: () -> Unit,
    onCreated: (String) -> Unit
) {
    val scope = rememberCoroutineScope()
    var name by remember { mutableStateOf("") }
    var isCreating by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.server_settings_roles_create)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = {
                        name = it
                        hasError = false
                    },
                    label = { Text(stringResource(R.string.server_settings_roles_name)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (hasError) {
                    Text(
                        text = stringResource(R.string.server_settings_roles_create_error),
                        color = MaterialTheme.colorScheme.error,
                        style = MaterialTheme.typography.bodySmall
                    )
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(
                enabled = !isCreating && name.trim().isNotEmpty(),
                onClick = {
                    isCreating = true
                    scope.launch {
                        try {
                            val role = createRole(serverId, name.trim())
                            onCreated(role.id)
                        } catch (e: Exception) {
                            hasError = true
                        } finally {
                            isCreating = false
                        }
                    }
                }
            ) {
                Text(
                    if (isCreating) {
                        stringResource(R.string.server_settings_roles_creating)
                    } else {
                        stringResource(R.string.server_settings_roles_create_button)
                    }
                )
            }
        }
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RoleEditorSheet(
    serverId: String,
    roleId: String,
    role: Role,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val initialPermissions = role.permissions ?: PermissionDescription(0, 0)
    var name by remember(roleId) { mutableStateOf(role.name ?: "") }
    var colour by remember(roleId) { mutableStateOf(role.colour ?: "") }
    var hoist by remember(roleId) { mutableStateOf(role.hoist == true) }
    var rank by remember(roleId) { mutableStateOf(role.rank?.toString() ?: "0") }
    var allow by remember(roleId) { mutableStateOf(initialPermissions.a) }
    var deny by remember(roleId) { mutableStateOf(initialPermissions.d) }
    var isSaving by remember(roleId) { mutableStateOf(false) }
    var hasError by remember(roleId) { mutableStateOf(false) }
    var showDeleteConfirmation by remember(roleId) { mutableStateOf(false) }
    var showColourPicker by remember(roleId) { mutableStateOf(false) }
    val rankValue = rank.toDoubleOrNull()

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(
                    stringResource(
                        R.string.server_settings_roles_delete_confirm,
                        role.name ?: stringResource(R.string.unknown)
                    )
                )
            },
            text = { Text(stringResource(R.string.server_settings_roles_delete_confirm_description)) },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        isSaving = true
                        scope.launch {
                            try {
                                deleteRole(serverId, roleId)
                                onDismiss()
                            } catch (e: Exception) {
                                hasError = true
                            } finally {
                                isSaving = false
                            }
                        }
                    }
                ) {
                    Text(stringResource(R.string.server_settings_roles_delete_confirm_yes))
                }
            }
        )
    }

    ModalBottomSheet(
        sheetState = sheetState,
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
                text = role.name ?: stringResource(R.string.unknown),
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedTextField(
                value = name,
                onValueChange = {
                    name = it
                    hasError = false
                },
                label = { Text(stringResource(R.string.server_settings_roles_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            OutlinedTextField(
                value = colour,
                onValueChange = {
                    colour = it
                    hasError = false
                },
                label = { Text(stringResource(R.string.server_settings_roles_colour)) },
                singleLine = true,
                leadingIcon = {
                    Box(
                        modifier = Modifier
                            .clip(CircleShape)
                            .size(18.dp)
                            .background(
                                brush = colour.takeIf { it.isNotBlank() }
                                    ?.let { BrushCompat.parseColour(it) }
                                    ?: Brush.solidColor(MaterialTheme.colorScheme.outlineVariant)
                            )
                    )
                },
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    onClick = { showColourPicker = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_palette_24dp),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.server_settings_roles_pick_colour))
                }
                OutlinedButton(
                    onClick = { colour = "" },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_undo_24dp),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.server_settings_roles_clear_colour))
                }
            }

            OutlinedTextField(
                value = rank,
                onValueChange = {
                    rank = it
                    hasError = false
                },
                label = { Text(stringResource(R.string.server_settings_roles_rank_label)) },
                singleLine = true,
                isError = rankValue == null,
                modifier = Modifier.fillMaxWidth()
            )

            ListItem(
                headlineContent = { Text(stringResource(R.string.server_settings_roles_hoist)) },
                supportingContent = {
                    Text(stringResource(R.string.server_settings_roles_hoist_description))
                },
                trailingContent = {
                    Switch(
                        checked = hoist,
                        onCheckedChange = {
                            hoist = it
                            hasError = false
                        }
                    )
                }
            )

            Text(
                text = stringResource(R.string.server_settings_roles_permissions),
                style = MaterialTheme.typography.titleMedium
            )

            rolePermissionOptions.forEach { option ->
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
                    text = stringResource(R.string.server_settings_roles_update_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedButton(
                    enabled = !isSaving,
                    onClick = { showDeleteConfirmation = true },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(stringResource(R.string.server_settings_roles_delete))
                }

                Button(
                    enabled = !isSaving && name.trim().isNotEmpty() && rankValue != null,
                    onClick = {
                        val trimmedColour = colour.trim().ifBlank { null }
                        val remove = if (trimmedColour == null && role.colour != null) {
                            listOf("Colour")
                        } else {
                            null
                        }
                        val roleBody = RoleEditBody(
                            name = name.trim().takeIf { it != role.name },
                            colour = trimmedColour.takeIf { it != role.colour },
                            hoist = hoist.takeIf { it != (role.hoist == true) },
                            rank = rankValue.takeIf { it != (role.rank ?: 0.0) },
                            remove = remove
                        )
                        val changedRole = roleBody.name != null ||
                                roleBody.colour != null ||
                                roleBody.hoist != null ||
                                roleBody.rank != null ||
                                roleBody.remove != null
                        val permissions = PermissionDescription(allow, deny)
                        val changedPermissions = permissions != initialPermissions

                        isSaving = true
                        scope.launch {
                            try {
                                if (changedRole) {
                                    editRole(serverId, roleId, roleBody)
                                }
                                if (changedPermissions) {
                                    setRolePermissions(serverId, roleId, permissions)
                                }
                                onDismiss()
                            } catch (e: Exception) {
                                hasError = true
                            } finally {
                                isSaving = false
                            }
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_check_24dp),
                        contentDescription = null
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        if (isSaving) {
                            stringResource(R.string.server_settings_roles_saving)
                        } else {
                            stringResource(R.string.server_settings_roles_save)
                        }
                    )
                }
            }

            Spacer(Modifier.height(24.dp))
        }
    }

    if (showColourPicker) {
        ModalBottomSheet(
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            onDismissRequest = { showColourPicker = false }
        ) {
            ColourPickerSheet(
                initialValue = parseCssHexColour(colour) ?: MaterialTheme.colorScheme.primary.toArgb(),
                onColourSelected = {
                    colour = it.toCssHexColour()
                    showColourPicker = false
                },
                onUseDefaultColour = {
                    colour = ""
                    showColourPicker = false
                },
                onDismiss = { showColourPicker = false }
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DefaultPermissionsSheet(
    serverId: String,
    initialPermissions: Long,
    onDismiss: () -> Unit
) {
    val scope = rememberCoroutineScope()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var permissions by remember(serverId) { mutableStateOf(initialPermissions) }
    var isSaving by remember(serverId) { mutableStateOf(false) }
    var hasError by remember(serverId) { mutableStateOf(false) }

    ModalBottomSheet(
        sheetState = sheetState,
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
                text = stringResource(R.string.server_settings_roles_default),
                style = MaterialTheme.typography.headlineSmall
            )

            rolePermissionOptions.forEach { option ->
                DefaultPermissionRow(
                    option = option,
                    checked = permissions.hasPermission(option.bit),
                    onCheckedChange = { checked ->
                        permissions = if (checked) {
                            permissions or option.bit.value
                        } else {
                            permissions and option.bit.value.inv()
                        }
                        hasError = false
                    }
                )
                HorizontalDivider()
            }

            if (hasError) {
                Text(
                    text = stringResource(R.string.server_settings_roles_update_error),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall
                )
            }

            Button(
                enabled = !isSaving,
                onClick = {
                    isSaving = true
                    scope.launch {
                        try {
                            setDefaultRolePermissions(serverId, permissions)
                            onDismiss()
                        } catch (e: Exception) {
                            hasError = true
                        } finally {
                            isSaving = false
                        }
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
                        stringResource(R.string.server_settings_roles_saving)
                    } else {
                        stringResource(R.string.server_settings_roles_save)
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

@Composable
private fun DefaultPermissionRow(
    option: PermissionOption,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    ListItem(
        headlineContent = {
            Text(stringResource(option.title))
        },
        trailingContent = {
            Switch(
                checked = checked,
                onCheckedChange = onCheckedChange
            )
        }
    )
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

private fun parseCssHexColour(colour: String): Int? {
    return try {
        colour.takeIf { it.startsWith("#") }?.let { AndroidColor.parseColor(it) }
    } catch (e: IllegalArgumentException) {
        null
    }
}

private fun Int.toCssHexColour(): String = String.format("#%08X", this)
