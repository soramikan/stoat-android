package chat.stoat.screens.settings.channel

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.OutlinedTextField
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.routes.channel.createWebhook
import chat.stoat.api.routes.channel.deleteWebhook
import chat.stoat.api.routes.channel.fetchWebhooks
import chat.stoat.core.model.data.STOAT_BASE
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.AutumnResource
import chat.stoat.core.model.schemas.ChannelWebhook
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ChannelSettingsWebhooks(navController: NavController, channelId: String) {
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()
    val clipboard = LocalClipboardManager.current
    val context = LocalContext.current
    var webhooks by remember(channelId) { mutableStateOf<List<ChannelWebhook>?>(null) }
    var error by remember(channelId) { mutableStateOf<String?>(null) }
    var showCreateDialog by remember(channelId) { mutableStateOf(false) }
    var deleteTarget by remember(channelId) { mutableStateOf<ChannelWebhook?>(null) }

    fun loadWebhooks() {
        scope.launch {
            runCatching { fetchWebhooks(channelId) }
                .onSuccess {
                    webhooks = it
                    error = null
                }
                .onFailure {
                    error = it.message ?: it::class.simpleName
                }
        }
    }

    LaunchedEffect(channelId) {
        loadWebhooks()
    }

    if (showCreateDialog) {
        CreateWebhookDialog(
            onDismiss = { showCreateDialog = false },
            onCreate = { name ->
                scope.launch {
                    runCatching { createWebhook(channelId, name) }
                        .onSuccess { webhook ->
                            webhooks = webhooks.orEmpty() + webhook
                            showCreateDialog = false
                            error = null
                        }
                        .onFailure {
                            showCreateDialog = false
                            error = it.message ?: it::class.simpleName
                        }
                }
            }
        )
    }

    deleteTarget?.let { webhook ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    stringResource(
                        R.string.channel_settings_webhooks_delete_confirm,
                        webhook.name ?: stringResource(R.string.unknown)
                    )
                )
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val webhookId = webhook.id ?: return@Button
                        scope.launch {
                            runCatching { deleteWebhook(webhookId) }
                                .onSuccess {
                                    webhooks = webhooks.orEmpty().filterNot { it.id == webhookId }
                                    deleteTarget = null
                                    error = null
                                }
                                .onFailure {
                                    deleteTarget = null
                                    error = it.message ?: it::class.simpleName
                                }
                        }
                    }
                ) {
                    Text(stringResource(R.string.channel_settings_webhooks_delete_confirm_yes))
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
                        text = stringResource(R.string.channel_settings_webhooks),
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
                    IconButton(onClick = { loadWebhooks() }) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download_24dp),
                            contentDescription = stringResource(R.string.channel_settings_webhooks_reload)
                        )
                    }
                }
            )
        },
    ) { pv ->
        Box(Modifier.padding(pv)) {
            when {
                webhooks == null && error == null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                error != null && webhooks == null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        Text(
                            text = error ?: "",
                            modifier = Modifier.align(Alignment.Center),
                            color = MaterialTheme.colorScheme.error
                        )
                    }
                }

                else -> {
                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                        item {
                            ListItem(
                                headlineContent = {
                                    Text(stringResource(R.string.channel_settings_webhooks_create))
                                },
                                leadingContent = {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_add_24dp),
                                        contentDescription = null
                                    )
                                },
                                modifier = Modifier
                                    .testTag("channel_settings_webhooks_create")
                                    .clickable { showCreateDialog = true }
                            )
                        }

                        if (error != null) {
                            item {
                                Text(
                                    text = error ?: "",
                                    color = MaterialTheme.colorScheme.error,
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                                )
                            }
                        }

                        item {
                            Text(
                                text = stringResource(
                                    R.string.channel_settings_webhooks_count,
                                    webhooks.orEmpty().size
                                ),
                                modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }

                        webhooks.orEmpty().forEach { webhook ->
                            item(key = webhook.id) {
                                WebhookListItem(
                                    webhook = webhook,
                                    onCopyUrl = {
                                        val id = webhook.id
                                        val token = webhook.token
                                        if (id != null && token != null) {
                                            clipboard.setText(
                                                AnnotatedString("$STOAT_BASE/webhooks/$id/$token")
                                            )
                                            Toast.makeText(
                                                context,
                                                context.getString(
                                                    R.string.channel_settings_webhooks_copied
                                                ),
                                                Toast.LENGTH_SHORT
                                            ).show()
                                        }
                                    },
                                    onDelete = { deleteTarget = webhook }
                                )
                                HorizontalDivider()
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CreateWebhookDialog(
    onDismiss: () -> Unit,
    onCreate: (String) -> Unit
) {
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.channel_settings_webhooks_create)) },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                label = { Text(stringResource(R.string.channel_settings_webhooks_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        },
        confirmButton = {
            Button(
                enabled = name.trim().isNotEmpty(),
                onClick = { onCreate(name.trim()) }
            ) {
                Text(stringResource(R.string.channel_settings_webhooks_create_button))
            }
        }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun WebhookListItem(
    webhook: ChannelWebhook,
    onCopyUrl: () -> Unit,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = webhook.name ?: stringResource(R.string.unknown),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            Text(
                text = if (webhook.token == null) {
                    stringResource(R.string.channel_settings_webhooks_no_token)
                } else {
                    stringResource(R.string.channel_settings_webhooks_has_token)
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        leadingContent = {
            WebhookAvatar(webhook.avatar)
        },
        trailingContent = {
            Row {
                IconButton(
                    enabled = webhook.id != null && webhook.token != null,
                    onClick = onCopyUrl
                ) {
                    Icon(
                        painter = painterResource(R.drawable.ic_content_copy_24dp),
                        contentDescription = stringResource(
                            R.string.channel_settings_webhooks_copy_url
                        )
                    )
                }
                IconButton(onClick = onDelete) {
                    Icon(
                        painter = painterResource(R.drawable.ic_delete_24dp),
                        contentDescription = stringResource(
                            R.string.channel_settings_webhooks_delete
                        ),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    )
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun WebhookAvatar(avatar: AutumnResource?) {
    val url = avatar?.id?.let { id ->
        "$STOAT_FILES/${avatar.tag ?: "avatars"}/$id"
    }

    if (url == null) {
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .size(40.dp)
                .background(MaterialTheme.colorScheme.outlineVariant)
        )
    } else {
        GlideImage(
            model = url,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .clip(CircleShape)
                .size(40.dp)
        )
    }
}
