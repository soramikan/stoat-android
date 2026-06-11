package chat.stoat.screens.settings.server

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
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
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.channel.CreateInviteResponse
import chat.stoat.api.routes.channel.createInvite
import chat.stoat.api.routes.server.deleteInvite
import chat.stoat.api.routes.server.fetchInvites
import chat.stoat.api.routes.user.getOrFetchUser
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.User
import chat.stoat.internals.Platform
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerInvitesSettings(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val clipboardManager = LocalClipboardManager.current
    val context = LocalContext.current

    val inviteChannels = remember(server?.channels, StoatAPI.channelCache) {
        server?.channels
            ?.mapNotNull { StoatAPI.channelCache[it] }
            ?.filter { it.channelType == ChannelType.TextChannel }
            .orEmpty()
    }

    var invites by remember { mutableStateOf<List<CreateInviteResponse>?>(null) }
    var creators by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var selectedChannelId by remember { mutableStateOf(inviteChannels.firstOrNull()?.id) }
    var error by remember { mutableStateOf<String?>(null) }
    var isCreating by remember { mutableStateOf(false) }
    var showChannelPicker by remember { mutableStateOf(false) }
    var deleteTarget by remember { mutableStateOf<CreateInviteResponse?>(null) }

    fun loadInvites() {
        scope.launch {
            runCatching { fetchInvites(serverId) }
                .onSuccess { response ->
                    invites = response
                    error = null

                    val nextCreators = creators.toMutableMap()
                    response.forEach { invite ->
                        val creator = StoatAPI.userCache[invite.creator]
                            ?: runCatching { getOrFetchUser(invite.creator) }.getOrNull()
                        if (creator != null) {
                            nextCreators[invite.creator] = creator
                        }
                    }
                    creators = nextCreators
                }
                .onFailure {
                    error = it.message ?: it::class.simpleName
                }
        }
    }

    LaunchedEffect(serverId) {
        if (selectedChannelId == null) {
            selectedChannelId = inviteChannels.firstOrNull()?.id
        }
        loadInvites()
    }

    if (showChannelPicker) {
        AlertDialog(
            onDismissRequest = { showChannelPicker = false },
            title = { Text(stringResource(R.string.server_settings_invites_channel)) },
            text = {
                LazyColumn {
                    inviteChannels.forEach { channel ->
                        item(key = channel.id) {
                            ListItem(
                                headlineContent = {
                                    Text(channel.inviteDisplayName())
                                },
                                modifier = Modifier.clickable {
                                    selectedChannelId = channel.id
                                    showChannelPicker = false
                                }
                            )
                        }
                    }
                }
            },
            confirmButton = {},
            dismissButton = {
                TextButton(onClick = { showChannelPicker = false }) {
                    Text(stringResource(R.string.server_settings_invites_channel_cancel))
                }
            }
        )
    }

    deleteTarget?.let { invite ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(stringResource(R.string.server_settings_invites_delete_confirm, invite.id))
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { deleteInvite(invite.id) }
                                .onSuccess {
                                    invites = invites?.filterNot { it.id == invite.id }
                                    deleteTarget = null
                                    error = null
                                }
                                .onFailure {
                                    error = it.message ?: it::class.simpleName
                                    deleteTarget = null
                                }
                        }
                    }
                ) {
                    Text(stringResource(R.string.server_settings_invites_delete_confirm_yes))
                }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.server_settings_invites_delete_confirm_no))
                }
            }
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = server?.name?.let {
                            stringResource(R.string.server_settings_invites_header, it)
                        } ?: stringResource(R.string.server_settings_invites),
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
        Column(
            modifier = Modifier
                .padding(pv)
                .fillMaxSize()
        ) {
            when {
                error != null -> {
                    Text(
                        text = error ?: stringResource(R.string.server_settings_invites_error),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                invites == null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                else -> {
                    LazyColumn {
                        item(key = "create") {
                            Column(Modifier.padding(16.dp)) {
                                Text(
                                    text = stringResource(R.string.server_settings_invites_create),
                                    style = MaterialTheme.typography.titleMedium
                                )

                                if (inviteChannels.isEmpty()) {
                                    Text(
                                        text = stringResource(
                                            R.string.server_settings_invites_create_no_channels
                                        ),
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(top = 8.dp)
                                    )
                                } else {
                                    val selectedChannel = inviteChannels
                                        .firstOrNull { it.id == selectedChannelId }
                                        ?: inviteChannels.first()

                                    OutlinedButton(
                                        onClick = { showChannelPicker = true },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        Text(selectedChannel.inviteDisplayName())
                                    }

                                    Button(
                                        enabled = !isCreating,
                                        onClick = {
                                            scope.launch {
                                                isCreating = true
                                                runCatching { createInvite(selectedChannel.id ?: "") }
                                                    .onSuccess { invite ->
                                                        invites = listOf(invite) + invites.orEmpty()
                                                        StoatAPI.selfId
                                                            ?.let { StoatAPI.userCache[it] }
                                                            ?.let {
                                                                creators = creators + (it.id!! to it)
                                                            }
                                                        error = null
                                                    }
                                                    .onFailure {
                                                        error = it.message ?: it::class.simpleName
                                                    }
                                                isCreating = false
                                            }
                                        },
                                        modifier = Modifier.padding(top = 8.dp)
                                    ) {
                                        if (isCreating) {
                                            Text(stringResource(R.string.server_settings_invites_creating))
                                        } else {
                                            Text(stringResource(R.string.server_settings_invites_create_button))
                                        }
                                    }
                                }
                            }
                        }

                        item(key = "divider") {
                            HorizontalDivider()
                            Text(
                                text = stringResource(
                                    R.string.server_settings_invites_count,
                                    invites.orEmpty().size
                                ),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                            )
                        }

                        invites.orEmpty().forEach { invite ->
                            item(key = invite.id) {
                                val creator = creators[invite.creator]
                                val channel = StoatAPI.channelCache[invite.channel]

                                ListItem(
                                    leadingContent = {
                                        UserAvatar(
                                            username = creator?.displayName
                                                ?: creator?.username
                                                ?: invite.creator,
                                            avatar = creator?.avatar,
                                            userId = creator?.id ?: invite.creator
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = invite.id,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Row {
                                            Text(
                                                text = creator?.displayName
                                                    ?: creator?.username
                                                    ?: stringResource(R.string.unknown),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Text(" - ")
                                            Text(
                                                text = channel?.inviteDisplayName()
                                                    ?: invite.channel,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        Row {
                                            IconButton(
                                                onClick = {
                                                    clipboardManager.setText(AnnotatedString(invite.id))
                                                    if (Platform.needsShowClipboardNotification()) {
                                                        Toast.makeText(
                                                            context,
                                                            context.getString(
                                                                R.string.server_settings_invites_copied
                                                            ),
                                                            Toast.LENGTH_SHORT
                                                        ).show()
                                                    }
                                                }
                                            ) {
                                                Icon(
                                                    painter = painterResource(
                                                        R.drawable.ic_content_copy_24dp
                                                    ),
                                                    contentDescription = stringResource(
                                                        R.string.server_settings_invites_copy
                                                    )
                                                )
                                            }
                                            IconButton(onClick = { deleteTarget = invite }) {
                                                Icon(
                                                    painter = painterResource(R.drawable.ic_delete_24dp),
                                                    contentDescription = stringResource(
                                                        R.string.server_settings_invites_delete
                                                    ),
                                                    tint = MaterialTheme.colorScheme.error
                                                )
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Channel.inviteDisplayName(): String {
    return name ?: id ?: stringResource(R.string.unknown)
}
