package chat.stoat.screens.settings.server

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.api.internals.PermissionBit
import chat.stoat.api.internals.hasPermission
import chat.stoat.api.routes.server.leaveOrDeleteServer
import chat.stoat.internals.extensions.rememberServerPermissions
import chat.stoat.screens.settings.SettingsIcon
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ServerSettingsHome(navController: NavController, serverId: String) {
    val server = StoatAPI.serverCache[serverId]
    val scrollBehavior = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    val permissions by rememberServerPermissions(serverId)
    var showDeletionConfirmation by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (showDeletionConfirmation) {
        AlertDialog(
            onDismissRequest = {
                showDeletionConfirmation = false
            },
            title = {
                Text(
                    stringResource(
                        R.string.server_settings_delete_confirm,
                        server?.name ?: stringResource(R.string.unknown)
                    )
                )
            },
            text = { Text(stringResource(R.string.server_settings_delete_confirm_description)) },
            dismissButton = {
                TextButton(onClick = {
                    showDeletionConfirmation = false
                }) {
                    Text(stringResource(R.string.server_settings_delete_confirm_no))
                }
            },
            confirmButton = {
                Button(onClick = {
                    showDeletionConfirmation = false
                    scope.launch {
                        leaveOrDeleteServer(serverId)
                        StoatAPI.serverCache.remove(serverId)
                        if (!navController.popBackStack("chat", inclusive = false)) {
                            navController.popBackStack()
                        }
                    }
                }) {
                    Text(stringResource(R.string.server_settings_delete_confirm_yes))
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
                            stringResource(R.string.server_settings_header, it)
                        } ?: stringResource(R.string.server_settings),
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
        Box(Modifier.padding(pv)) {
            server?.let {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .verticalScroll(rememberScrollState())
                ) {
                    if (permissions.hasPermission(PermissionBit.ManageServer)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(id = R.string.server_settings_overview)
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_info_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("server_settings_view_overview")
                                .clickable {
                                    navController.navigate("settings/server/$serverId/overview")
                                }
                        )
                    }

                    if (permissions.hasPermission(PermissionBit.ManageChannel)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(id = R.string.server_settings_categories)
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
                                .testTag("server_settings_view_categories")
                                .clickable {
                                    navController.navigate("settings/server/$serverId/categories")
                                }
                        )
                    }

                    ListItem(
                        headlineContent = {
                            Text(
                                text = stringResource(id = R.string.server_settings_members)
                            )
                        },
                        leadingContent = {
                            SettingsIcon {
                                Icon(
                                    painter = painterResource(R.drawable.ic_group_24dp),
                                    contentDescription = null,
                                )
                            }
                        },
                        modifier = Modifier
                            .testTag("server_settings_view_members")
                            .clickable {
                                navController.navigate("settings/server/$serverId/members")
                            }
                    )

                    if (permissions.hasPermission(PermissionBit.ManageServer)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(id = R.string.server_settings_invites)
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_ios_share_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("server_settings_view_invites")
                                .clickable {
                                    navController.navigate("settings/server/$serverId/invites")
                                }
                        )
                    }

                    if (permissions.hasPermission(PermissionBit.BanMembers)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(id = R.string.server_settings_bans)
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_gavel_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("server_settings_view_bans")
                                .clickable {
                                    navController.navigate("settings/server/$serverId/bans")
                                }
                        )
                    }

                    if (permissions.hasPermission(PermissionBit.ManageRole)) {
                        ListItem(
                            headlineContent = {
                                Text(
                                    text = stringResource(id = R.string.server_settings_roles)
                                )
                            },
                            leadingContent = {
                                SettingsIcon {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_badge_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("server_settings_view_roles")
                                .clickable {
                                    navController.navigate("settings/server/$serverId/roles")
                                }
                        )
                    }

                    if (server.owner == StoatAPI.selfId) {
                        ListItem(
                            headlineContent = {
                                CompositionLocalProvider(LocalContentColor provides MaterialTheme.colorScheme.error) {
                                    Text(
                                        text = stringResource(id = R.string.server_settings_delete)
                                    )
                                }
                            },
                            leadingContent = {
                                SettingsIcon(danger = true) {
                                    Icon(
                                        painter = painterResource(R.drawable.ic_delete_24dp),
                                        contentDescription = null,
                                    )
                                }
                            },
                            modifier = Modifier
                                .testTag("server_settings_click_delete")
                                .clickable {
                                    showDeletionConfirmation = true
                                }
                        )
                    }
                }
            } ?: run {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                ) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                }
            }
        }
    }
}
