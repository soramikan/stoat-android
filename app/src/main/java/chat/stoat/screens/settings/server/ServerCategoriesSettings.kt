package chat.stoat.screens.settings.server

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
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.ULID
import chat.stoat.api.routes.server.patchServer
import chat.stoat.composables.screens.chat.ChannelIcon
import chat.stoat.core.model.schemas.Category
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.screens.settings.SettingsIcon
import kotlinx.coroutines.launch

private data class EditableCategory(
    val id: String,
    val title: String,
    val channels: List<String>
) {
    fun toCategory(): Category {
        return Category(id = id, title = title.trim(), channels = channels)
    }
}

private fun Category.toEditable(): EditableCategory {
    return EditableCategory(
        id = id ?: ULID.makeNext(),
        title = title ?: "",
        channels = channels ?: emptyList()
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerCategoriesSettings(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    var categories by remember(serverId, server?.categories) {
        mutableStateOf<List<EditableCategory>>(
            server?.categories?.map { it.toEditable() } ?: emptyList()
        )
    }
    var newCategoryName by remember { mutableStateOf("") }
    var selectedCategoryId by remember { mutableStateOf<String?>(null) }
    var isSaving by remember { mutableStateOf(false) }
    var hasError by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    val allChannels = remember(server?.channels, StoatAPI.channelCache.size) {
        server
            ?.channels
            .orEmpty()
            .mapNotNull { StoatAPI.channelCache[it] }
            .filter {
                it.channelType == ChannelType.TextChannel ||
                        it.channelType == ChannelType.VoiceChannel
            }
    }
    val categorisedChannelIds = categories.flatMap { it.channels }.toSet()
    val uncategorisedChannels = allChannels.filter { it.id !in categorisedChannelIds }
    val canSave = server != null &&
            categories.all { it.title.trim().isNotEmpty() } &&
            categories.map { it.toCategory() } != server.categories.orEmpty()

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = server?.name?.let {
                            stringResource(R.string.server_settings_categories_header, it)
                        } ?: stringResource(R.string.server_settings_categories),
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
                        enabled = canSave && !isSaving,
                        onClick = {
                            val currentServer = server ?: return@IconButton
                            isSaving = true
                            hasError = false
                            scope.launch {
                                try {
                                    patchServer(
                                        serverId = currentServer.id ?: serverId,
                                        categories = categories.map { it.toCategory() }
                                    )
                                } catch (e: Exception) {
                                    hasError = true
                                } finally {
                                    isSaving = false
                                }
                            }
                        }
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_check_24dp),
                            contentDescription = stringResource(R.string.server_settings_categories_save)
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
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    val unknownChannelName = stringResource(R.string.unknown)

                    CreateCategorySection(
                        name = newCategoryName,
                        onNameChange = {
                            newCategoryName = it
                            hasError = false
                        },
                        onCreate = {
                            val title = newCategoryName.trim()
                            if (title.isNotEmpty()) {
                                categories = categories + EditableCategory(
                                    id = ULID.makeNext(),
                                    title = title,
                                    channels = emptyList()
                                )
                                newCategoryName = ""
                                hasError = false
                            }
                        }
                    )

                    if (hasError) {
                        Text(
                            text = stringResource(R.string.server_settings_categories_update_error),
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodySmall,
                            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                        )
                    }

                    Text(
                        text = stringResource(
                            R.string.server_settings_categories_count,
                            categories.size
                        ),
                        modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    categories.forEach { category ->
                        val channels = category.channels.mapNotNull { StoatAPI.channelCache[it] }
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = category.title,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            },
                            supportingContent = {
                                Text(
                                    if (channels.isEmpty()) {
                                        stringResource(R.string.server_settings_categories_no_channels)
                                    } else {
                                        channels.joinToString { channel ->
                                            channel.name ?: unknownChannelName
                                        }
                                    },
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
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
                                .testTag("server_settings_category_${category.id}")
                                .clickable { selectedCategoryId = category.id }
                        )
                    }

                    if (uncategorisedChannels.isNotEmpty()) {
                        Text(
                            text = stringResource(
                                R.string.server_settings_categories_uncategorised_count,
                                uncategorisedChannels.size
                            ),
                            modifier = Modifier.padding(start = 16.dp, top = 24.dp, bottom = 8.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )

                        uncategorisedChannels.forEach { channel ->
                            ChannelListItem(channel)
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

    selectedCategoryId?.let { categoryId ->
        val categoryIndex = categories.indexOfFirst { it.id == categoryId }
        if (categoryIndex >= 0) {
            CategoryEditorSheet(
                category = categories[categoryIndex],
                allChannels = allChannels,
                canMoveUp = categoryIndex > 0,
                canMoveDown = categoryIndex < categories.lastIndex,
                onDismiss = { selectedCategoryId = null },
                onTitleChange = { title ->
                    categories = categories.updateCategory(categoryId) { it.copy(title = title) }
                    hasError = false
                },
                onChannelCheckedChange = { channelId, checked ->
                    categories = categories.setChannelInCategory(categoryId, channelId, checked)
                    hasError = false
                },
                onMove = { delta ->
                    categories = categories.moveCategory(categoryIndex, delta)
                    hasError = false
                },
                onDelete = {
                    selectedCategoryId = null
                    categories = categories.filterNot { it.id == categoryId }
                    hasError = false
                }
            )
        }
    }
}

@Composable
private fun CreateCategorySection(
    name: String,
    onNameChange: (String) -> Unit,
    onCreate: () -> Unit
) {
    Column(
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        OutlinedTextField(
            value = name,
            onValueChange = onNameChange,
            label = { Text(stringResource(R.string.server_settings_categories_name)) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            enabled = name.trim().isNotEmpty(),
            onClick = onCreate,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_add_24dp),
                contentDescription = null
            )
            Spacer(Modifier.width(8.dp))
            Text(stringResource(R.string.server_settings_categories_create))
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun CategoryEditorSheet(
    category: EditableCategory,
    allChannels: List<Channel>,
    canMoveUp: Boolean,
    canMoveDown: Boolean,
    onDismiss: () -> Unit,
    onTitleChange: (String) -> Unit,
    onChannelCheckedChange: (String, Boolean) -> Unit,
    onMove: (Int) -> Unit,
    onDelete: () -> Unit
) {
    var showDeleteConfirmation by remember(category.id) { mutableStateOf(false) }

    if (showDeleteConfirmation) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirmation = false },
            title = {
                Text(stringResource(R.string.server_settings_categories_delete_confirm, category.title))
            },
            text = { Text(stringResource(R.string.server_settings_categories_delete_confirm_description)) },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirmation = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteConfirmation = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.server_settings_categories_delete_confirm_yes))
                }
            }
        )
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
                text = category.title,
                style = MaterialTheme.typography.headlineSmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )

            OutlinedTextField(
                value = category.title,
                onValueChange = onTitleChange,
                label = { Text(stringResource(R.string.server_settings_categories_name)) },
                isError = category.title.trim().isEmpty(),
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(
                    enabled = canMoveUp,
                    onClick = { onMove(-1) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.server_settings_categories_move_up))
                }
                OutlinedButton(
                    enabled = canMoveDown,
                    onClick = { onMove(1) },
                    modifier = Modifier.weight(1f)
                ) {
                    Text(stringResource(R.string.server_settings_categories_move_down))
                }
            }

            Text(
                text = stringResource(R.string.server_settings_categories_channels),
                style = MaterialTheme.typography.titleMedium
            )

            if (allChannels.isEmpty()) {
                Text(
                    text = stringResource(R.string.server_settings_categories_no_server_channels),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else {
                allChannels.forEach { channel ->
                    val channelId = channel.id ?: return@forEach
                    ListItem(
                        headlineContent = {
                            Text(channel.name ?: stringResource(R.string.unknown))
                        },
                        leadingContent = {
                            ChannelIcon(
                                channelType = channel.channelType ?: ChannelType.TextChannel,
                                modifier = Modifier.size(20.dp)
                            )
                        },
                        trailingContent = {
                            Checkbox(
                                checked = channelId in category.channels,
                                onCheckedChange = {
                                    onChannelCheckedChange(channelId, it)
                                }
                            )
                        },
                        modifier = Modifier.clickable {
                            onChannelCheckedChange(channelId, channelId !in category.channels)
                        }
                    )
                    HorizontalDivider()
                }
            }

            OutlinedButton(
                onClick = { showDeleteConfirmation = true },
                modifier = Modifier.fillMaxWidth()
            ) {
                Icon(
                    painter = painterResource(R.drawable.ic_delete_24dp),
                    contentDescription = null
                )
                Spacer(Modifier.width(8.dp))
                Text(stringResource(R.string.server_settings_categories_delete))
            }

            Spacer(Modifier.height(24.dp))
        }
    }
}

@Composable
private fun ChannelListItem(channel: Channel) {
    ListItem(
        headlineContent = {
            Text(channel.name ?: stringResource(R.string.unknown))
        },
        leadingContent = {
            ChannelIcon(
                channelType = channel.channelType ?: ChannelType.TextChannel,
                modifier = Modifier.size(20.dp)
            )
        }
    )
}

private fun List<EditableCategory>.updateCategory(
    categoryId: String,
    transform: (EditableCategory) -> EditableCategory
): List<EditableCategory> {
    return map { category ->
        if (category.id == categoryId) transform(category) else category
    }
}

private fun List<EditableCategory>.setChannelInCategory(
    categoryId: String,
    channelId: String,
    checked: Boolean
): List<EditableCategory> {
    return map { category ->
        when {
            category.id == categoryId -> category.copy(
                channels = if (checked) {
                    (category.channels + channelId).distinct()
                } else {
                    category.channels - channelId
                }
            )

            checked -> category.copy(channels = category.channels - channelId)
            else -> category
        }
    }
}

private fun List<EditableCategory>.moveCategory(index: Int, delta: Int): List<EditableCategory> {
    val target = index + delta
    if (index !in indices || target !in indices) return this

    val mutable = toMutableList()
    val item = mutable.removeAt(index)
    mutable.add(target, item)
    return mutable
}
