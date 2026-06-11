package chat.stoat.screens.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.routes.server.Ban
import chat.stoat.api.routes.server.fetchBans
import chat.stoat.api.routes.server.unbanUser
import chat.stoat.composables.generic.UserAvatar
import chat.stoat.core.model.schemas.User
import chat.stoat.sheets.UserInfoSheet
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerBansSettings(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val scope = rememberCoroutineScope()

    var bans by remember { mutableStateOf<List<Ban>?>(null) }
    var users by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showUserInfoSheet by remember { mutableStateOf(false) }
    var userInfoSheetTarget by remember { mutableStateOf("") }
    var unbanTarget by remember { mutableStateOf<Pair<User, Ban>?>(null) }

    fun loadBans() {
        scope.launch {
            runCatching {
                fetchBans(serverId)
            }.onSuccess { response ->
                val fetchedUsers = response.users.mapNotNull { user ->
                    user.id?.let { id ->
                        StoatAPI.userCache[id] = user
                        id to user
                    }
                }.toMap()

                users = fetchedUsers
                bans = response.bans
                error = null
            }.onFailure {
                error = it.message ?: it::class.simpleName
            }
        }
    }

    LaunchedEffect(serverId) {
        loadBans()
    }

    val filteredBans by remember(bans, users, searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            bans.orEmpty()
                .mapNotNull { ban ->
                    val userId = ban.id.user
                    val user = users[userId] ?: StoatAPI.userCache[userId] ?: return@mapNotNull null
                    user to ban
                }
                .filter { (user, ban) ->
                    if (query.isEmpty()) {
                        true
                    } else {
                        user.displayName.orEmpty().contains(query, ignoreCase = true) ||
                                user.username.orEmpty().contains(query, ignoreCase = true) ||
                                ban.reason.orEmpty().contains(query, ignoreCase = true)
                    }
                }
                .sortedBy { (user, _) -> user.displayName ?: user.username ?: "" }
        }
    }

    if (showUserInfoSheet) {
        val userContextSheetState = rememberModalBottomSheetState()

        ModalBottomSheet(
            sheetState = userContextSheetState,
            onDismissRequest = {
                showUserInfoSheet = false
            }
        ) {
            UserInfoSheet(
                userId = userInfoSheetTarget,
                serverId = serverId,
                dismissSheet = {
                    userContextSheetState.hide()
                    showUserInfoSheet = false
                }
            )
        }
    }

    unbanTarget?.let { (user, ban) ->
        AlertDialog(
            onDismissRequest = {
                unbanTarget = null
            },
            title = {
                Text(
                    stringResource(
                        R.string.server_settings_bans_unban_confirm,
                        user.username ?: user.id ?: ban.id.user
                    )
                )
            },
            text = {
                Text(ban.reason ?: stringResource(R.string.server_settings_bans_no_reason))
            },
            dismissButton = {
                TextButton(onClick = { unbanTarget = null }) {
                    Text(stringResource(R.string.server_settings_bans_unban_confirm_no))
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        scope.launch {
                            runCatching { unbanUser(serverId, ban.id.user) }
                                .onSuccess {
                                    bans = bans?.filterNot { it.id.user == ban.id.user }
                                    users = users - ban.id.user
                                    unbanTarget = null
                                }
                                .onFailure {
                                    error = it.message ?: it::class.simpleName
                                    unbanTarget = null
                                }
                        }
                    }
                ) {
                    Text(stringResource(R.string.server_settings_bans_unban_confirm_yes))
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
                            stringResource(R.string.server_settings_bans_header, it)
                        } ?: stringResource(R.string.server_settings_bans),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                },
                navigationIcon = {
                    IconButton(onClick = {
                        navController.popBackStack()
                    }) {
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
            TextField(
                label = {
                    Text(stringResource(R.string.server_settings_bans_search))
                },
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp),
                singleLine = true
            )

            when {
                error != null -> {
                    Text(
                        text = error ?: stringResource(R.string.server_settings_bans_error),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                bans == null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                else -> {
                    Text(
                        text = stringResource(
                            R.string.server_settings_bans_count,
                            filteredBans.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn {
                        filteredBans.forEach { (user, ban) ->
                            val userId = user.id ?: ban.id.user
                            item(key = userId) {
                                ListItem(
                                    modifier = Modifier.clickable {
                                        userInfoSheetTarget = userId
                                        showUserInfoSheet = true
                                    },
                                    leadingContent = {
                                        UserAvatar(
                                            username = user.displayName ?: user.username ?: userId,
                                            avatar = user.avatar,
                                            userId = userId
                                        )
                                    },
                                    headlineContent = {
                                        Text(
                                            text = user.displayName ?: user.username ?: userId,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    },
                                    supportingContent = {
                                        Row {
                                            Text(
                                                text = "@${user.username ?: userId}",
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                            Spacer(Modifier.padding(horizontal = 4.dp))
                                            Text(
                                                text = ban.reason
                                                    ?: stringResource(R.string.server_settings_bans_no_reason),
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    },
                                    trailingContent = {
                                        IconButton(onClick = { unbanTarget = user to ban }) {
                                            Icon(
                                                painter = painterResource(R.drawable.ic_delete_24dp),
                                                contentDescription = stringResource(
                                                    R.string.server_settings_bans_unban
                                                ),
                                                tint = MaterialTheme.colorScheme.error
                                            )
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
