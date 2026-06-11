package chat.stoat.screens.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
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
import chat.stoat.api.routes.server.fetchMembers
import chat.stoat.composables.chat.MemberListItem
import chat.stoat.core.model.schemas.Member
import chat.stoat.core.model.schemas.User
import chat.stoat.sheets.UserInfoSheet

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerMembersSettings(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()

    var members by remember { mutableStateOf<List<Member>?>(null) }
    var users by remember { mutableStateOf<Map<String, User>>(emptyMap()) }
    var searchQuery by remember { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var showUserInfoSheet by remember { mutableStateOf(false) }
    var userInfoSheetTarget by remember { mutableStateOf("") }

    LaunchedEffect(serverId) {
        runCatching {
            fetchMembers(serverId = serverId, includeOffline = true)
        }.onSuccess { response ->
            val fetchedUsers = response.users.mapNotNull { user ->
                user.id?.let { id ->
                    StoatAPI.userCache[id] = user
                    id to user
                }
            }.toMap()

            members = response.members
            users = fetchedUsers
            error = null
        }.onFailure {
            error = it.message ?: it::class.simpleName
        }
    }

    val filteredMembers by remember(members, users, searchQuery) {
        derivedStateOf {
            val query = searchQuery.trim().lowercase()
            members.orEmpty()
                .mapNotNull { member ->
                    val userId = member.id?.user ?: return@mapNotNull null
                    val user = users[userId] ?: StoatAPI.userCache[userId] ?: return@mapNotNull null
                    member to user
                }
                .filter { (member, user) ->
                    if (query.isEmpty()) {
                        true
                    } else {
                        val displayName = user.displayName.orEmpty()
                        val username = user.username.orEmpty()
                        val nickname = member.nickname.orEmpty()
                        displayName.contains(query, ignoreCase = true) ||
                                username.contains(query, ignoreCase = true) ||
                                nickname.contains(query, ignoreCase = true)
                    }
                }
                .sortedWith(
                    compareBy<Pair<Member, User>> {
                        it.second.displayName ?: it.second.username ?: ""
                    }.thenBy {
                        it.first.joinedAt ?: ""
                    }
                )
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

    Scaffold(
        modifier = Modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                scrollBehavior = scrollBehavior,
                title = {
                    Text(
                        text = server?.name?.let {
                            stringResource(R.string.server_settings_members_header, it)
                        } ?: stringResource(R.string.server_settings_members),
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
                    Text(stringResource(R.string.server_settings_members_search))
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
                        text = error ?: stringResource(R.string.server_settings_members_error),
                        color = MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(16.dp)
                    )
                }

                members == null -> {
                    Box(modifier = Modifier.fillMaxSize()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    }
                }

                else -> {
                    Text(
                        text = stringResource(
                            R.string.server_settings_members_count,
                            filteredMembers.size
                        ),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    LazyColumn {
                        filteredMembers.forEach { (member, user) ->
                            val userId = member.id?.user ?: return@forEach
                            item(key = userId) {
                                MemberListItem(
                                    user = user,
                                    member = member,
                                    serverId = serverId,
                                    userId = userId,
                                    modifier = Modifier.clickable {
                                        userInfoSheetTarget = userId
                                        showUserInfoSheet = true
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
