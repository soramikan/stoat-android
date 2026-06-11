package chat.stoat.screens.settings.server

import android.content.Context
import android.net.Uri
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.LinearProgressIndicator
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
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.custom.deleteEmoji
import chat.stoat.api.routes.custom.fetchServerEmojis
import chat.stoat.api.routes.custom.uploadEmoji
import chat.stoat.api.routes.microservices.autumn.uploadToAutumn
import chat.stoat.api.routes.user.getOrFetchUser
import chat.stoat.composables.generic.InlineMediaPicker
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.data.STOAT_FILES
import chat.stoat.core.model.schemas.Emoji
import chat.stoat.core.model.schemas.User
import com.bumptech.glide.integration.compose.ExperimentalGlideComposeApi
import com.bumptech.glide.integration.compose.GlideImage
import io.ktor.http.ContentType
import kotlinx.coroutines.launch
import java.io.File

@OptIn(ExperimentalMaterial3Api::class, ExperimentalGlideComposeApi::class)
@Composable
fun ServerEmojisSettings(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()

    var selectedUri by remember { mutableStateOf<Uri?>(null) }
    var emojiName by remember { mutableStateOf("") }
    var creators by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var isLoading by remember { mutableStateOf(false) }
    var isUploading by remember { mutableStateOf(false) }
    var uploadProgress by remember { mutableFloatStateOf(0f) }
    var error by remember { mutableStateOf<String?>(null) }
    var deleteTarget by remember { mutableStateOf<Emoji?>(null) }

    val emojis = StoatAPI.emojiCache.values
        .filter {
            val parent = it.parent
            parent?.type == "Server" && parent.id == serverId
        }
        .sortedBy { it.name ?: "" }

    suspend fun loadCreators(items: List<Emoji>) {
        val nextCreators = creators.toMutableMap()
        items.forEach { emoji ->
            val creatorId = emoji.creatorID ?: return@forEach
            val creator = StoatAPI.userCache[creatorId]
                ?: runCatching { getOrFetchUser(creatorId) }.getOrNull()
            if (creator != null) {
                nextCreators[creatorId] = creator
            }
        }
        creators = nextCreators
    }

    fun refreshEmojis() {
        scope.launch {
            isLoading = true
            runCatching { fetchServerEmojis(serverId) }
                .onSuccess {
                    error = null
                    loadCreators(it)
                }
                .onFailure {
                    error = it.message ?: it::class.simpleName
                }
            isLoading = false
        }
    }

    LaunchedEffect(serverId) {
        refreshEmojis()
    }

    deleteTarget?.let { emoji ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = {
                Text(
                    stringResource(
                        R.string.server_settings_emojis_delete_confirm,
                        emoji.name ?: stringResource(R.string.unknown)
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
                        val emojiId = emoji.id ?: return@Button
                        scope.launch {
                            runCatching { deleteEmoji(emojiId) }
                                .onSuccess {
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
                    Text(stringResource(R.string.server_settings_emojis_delete_confirm_yes))
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
                            stringResource(R.string.server_settings_emojis_header, it)
                        } ?: stringResource(R.string.server_settings_emojis),
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
                    IconButton(
                        enabled = !isLoading,
                        onClick = { refreshEmojis() }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_download_24dp),
                            contentDescription = stringResource(R.string.server_settings_emojis_reload)
                        )
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
            server?.let {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    item {
                        EmojiUploadSection(
                            selectedUri = selectedUri,
                            emojiName = emojiName,
                            isUploading = isUploading,
                            uploadProgress = uploadProgress,
                            onPick = {
                                selectedUri = it
                                error = null
                            },
                            onRemove = { selectedUri = null },
                            onNameChange = {
                                emojiName = normaliseEmojiName(it)
                                error = null
                            },
                            onUpload = {
                                val uri = selectedUri ?: return@EmojiUploadSection
                                val name = emojiName.trim()
                                if (name.isEmpty()) return@EmojiUploadSection

                                scope.launch {
                                    isUploading = true
                                    uploadProgress = 0f
                                    var copiedFile: File? = null
                                    try {
                                        copiedFile = copyUriToCache(context, uri, "emoji")
                                        val mime = context.contentResolver.getType(uri) ?: "image/*"
                                        val autumnId = uploadToAutumn(
                                            file = copiedFile,
                                            name = copiedFile.name,
                                            tag = "emojis",
                                            contentType = ContentType.parse(mime),
                                            onProgress = { soFar, outOf ->
                                                uploadProgress = if (outOf == 0L) {
                                                    0f
                                                } else {
                                                    soFar.toFloat() / outOf.toFloat()
                                                }
                                            }
                                        )
                                        val emoji = uploadEmoji(
                                            autumnId = autumnId,
                                            name = name,
                                            serverId = serverId,
                                            nsfw = false
                                        )
                                        loadCreators(listOf(emoji))
                                        selectedUri = null
                                        emojiName = ""
                                        error = null
                                    } catch (e: Exception) {
                                        error = e.message ?: e::class.simpleName
                                    } finally {
                                        copiedFile?.delete()
                                        uploadProgress = 0f
                                        isUploading = false
                                    }
                                }
                            }
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
                            text = stringResource(R.string.server_settings_emojis_count, emojis.size),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }

                    if (isLoading && emojis.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(24.dp)
                            ) {
                                CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                            }
                        }
                    }

                    emojis.forEach { emoji ->
                        item(key = emoji.id) {
                            EmojiListItem(
                                emoji = emoji,
                                creator = emoji.creatorID?.let { creators[it] },
                                onDelete = { deleteTarget = emoji }
                            )
                            HorizontalDivider()
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
}

@Composable
private fun EmojiUploadSection(
    selectedUri: Uri?,
    emojiName: String,
    isUploading: Boolean,
    uploadProgress: Float,
    onPick: (Uri) -> Unit,
    onRemove: () -> Unit,
    onNameChange: (String) -> Unit,
    onUpload: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            InlineMediaPicker(
                currentModel = selectedUri,
                mimeType = "image/*",
                circular = true,
                canRemove = true,
                enabled = !isUploading,
                onPick = onPick,
                onRemove = onRemove
            )

            Column(modifier = Modifier.weight(1f)) {
                OutlinedTextField(
                    value = emojiName,
                    onValueChange = onNameChange,
                    enabled = !isUploading,
                    label = { Text(stringResource(R.string.server_settings_emojis_name)) },
                    prefix = { Text(":") },
                    suffix = { Text(":") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Text(
                    text = stringResource(R.string.server_settings_emojis_name_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        if (isUploading) {
            LinearProgressIndicator(
                progress = { uploadProgress },
                modifier = Modifier.fillMaxWidth()
            )
        }

        Button(
            enabled = !isUploading && selectedUri != null && emojiName.isNotBlank(),
            onClick = onUpload,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_reaction_24dp),
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(
                if (isUploading) {
                    stringResource(R.string.server_settings_emojis_uploading)
                } else {
                    stringResource(R.string.server_settings_emojis_upload)
                }
            )
        }
    }
}

@OptIn(ExperimentalGlideComposeApi::class)
@Composable
private fun EmojiListItem(
    emoji: Emoji,
    creator: User?,
    onDelete: () -> Unit
) {
    ListItem(
        headlineContent = {
            Text(
                text = ":${emoji.name ?: stringResource(R.string.unknown)}:",
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        supportingContent = {
            if (creator != null) {
                Text(
                    text = creator.displayName ?: creator.username ?: stringResource(R.string.unknown),
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        leadingContent = {
            GlideImage(
                model = "$STOAT_FILES/emojis/${emoji.id}",
                contentDescription = emoji.name,
                contentScale = ContentScale.Crop,
                modifier = Modifier
                    .clip(MaterialTheme.shapes.small)
                    .size(40.dp)
            )
        },
        trailingContent = {
            if (creator != null) {
                UserAvatar(
                    username = User.resolveDefaultName(creator),
                    userId = creator.id ?: "",
                    avatar = creator.avatar,
                    size = 28.dp
                )
            }
        },
        modifier = Modifier
            .testTag("server_settings_emoji_${emoji.id}")
            .clickable { onDelete() }
    )
}

private fun normaliseEmojiName(input: String): String {
    return input
        .lowercase()
        .filter { it in 'a'..'z' || it in '0'..'9' || it == '_' || it == '-' }
}

private fun copyUriToCache(context: Context, uri: Uri, prefix: String): File {
    val extension = context.contentResolver.getType(uri)
        ?.substringAfterLast("/")
        ?.takeIf { it.isNotBlank() }
        ?: "img"
    val file = File(context.cacheDir, "$prefix-${System.currentTimeMillis()}.$extension")

    context.contentResolver.openInputStream(uri)?.use { input ->
        file.outputStream().use { output ->
            input.copyTo(output)
        }
    } ?: throw Exception("Could not open file")

    return file
}
