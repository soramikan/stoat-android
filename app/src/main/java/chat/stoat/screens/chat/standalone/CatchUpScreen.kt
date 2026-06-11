package chat.stoat.screens.chat.standalone

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.State
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavController
import chat.stoat.R
import chat.stoat.api.StoatAPI
import chat.stoat.callbacks.Action
import chat.stoat.callbacks.ActionChannel
import chat.stoat.core.model.schemas.Channel
import chat.stoat.core.model.schemas.ChannelType
import chat.stoat.core.model.schemas.ChannelUnread
import kotlinx.coroutines.launch
import logcat.LogPriority
import logcat.logcat

sealed class CatchUpCard {
    data class UnreadMessageInChannel(
        val channelId: String,
        val lastReadMessageId: String,
        val newestMessageId: String,
        val mentionCount: Int
    ) : CatchUpCard()
}

class CatchUpScreenViewModel : ViewModel() {
    private val deck = ArrayDeque<CatchUpCard>(initialCapacity = 3)
    private var dataSource: Iterator<ChannelUnread> = emptyList<ChannelUnread>().iterator()
    var initComplete = false
        private set

    private val _cards = mutableStateOf<List<CatchUpCard>>(emptyList())
    val cards: State<List<CatchUpCard>> = _cards

    fun initWith(unreads: List<ChannelUnread>) {
        deck.clear()
        dataSource = unreads.iterator()

        repeat(3) {
            dealNewCard()
        }

        initComplete = true
    }

    fun dealNewCard() {
        while (dataSource.hasNext()) {
            val unread = dataSource.next()
            val channel = StoatAPI.channelCache[unread.id] ?: continue
            val lastReadMessageId = unread.last_id ?: continue
            val newestMessageId = channel.lastMessageID ?: continue

            if (!StoatAPI.unreads.hasUnread(channel.id ?: unread.id, newestMessageId, channel.server)) {
                continue
            }

            deck.addLast(
                CatchUpCard.UnreadMessageInChannel(
                    channelId = unread.id,
                    lastReadMessageId = lastReadMessageId,
                    newestMessageId = newestMessageId,
                    mentionCount = unread.mentions?.size ?: 0
                )
            )
            deckUpdated()
            return
        }

        deckUpdated()
        logcat(LogPriority.WARN) { "No more unreads to deal!" }
    }

    fun swipedCard() {
        if (deck.isNotEmpty()) {
            deck.removeFirst()
            deckUpdated()
        }
        dealNewCard()
    }

    fun deckUpdated() {
        _cards.value = deck.toList()
    }

    suspend fun markAsRead(card: CatchUpCard.UnreadMessageInChannel) {
        StoatAPI.unreads.markAsRead(card.channelId, card.newestMessageId)
        swipedCard()
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CatchUpScreen(navController: NavController, viewModel: CatchUpScreenViewModel = viewModel()) {
    LaunchedEffect(Unit) {
        if (!viewModel.initComplete) {
            viewModel.initWith(StoatAPI.unreads.getAllUnreads())
        }
    }

    val scope = rememberCoroutineScope()
    val primaryContainer = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.1f)
    val colourKeep = remember { Color(0xFFF84848).copy(alpha = 0.5f) }
    val colourRead = remember { Color(0xFF3ABF7E).copy(alpha = 0.5f) }

    var currentColour by remember { mutableStateOf(primaryContainer) }
    val gradientColour by animateColorAsState(
        targetValue = currentColour,
        animationSpec = tween(durationMillis = 500)
    )

    val cards by viewModel.cards

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = stringResource(R.string.catch_up),
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
                }
            )
        }
    ) { pv ->
        Box(Modifier.fillMaxSize()) {
            Box(
                Modifier
                    .background(
                        Brush.linearGradient(
                            0.2f to MaterialTheme.colorScheme.background,
                            1.0f to gradientColour,
                            end = Offset.Infinite.copy(x = 0f)
                        )
                    )
                    .fillMaxSize()
            )

            Box(
                Modifier
                    .padding(pv)
                    .imePadding()
            ) {
                if (cards.isEmpty() && viewModel.initComplete) {
                    Column(
                        verticalArrangement = Arrangement.Center,
                        horizontalAlignment = Alignment.CenterHorizontally,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(24.dp)
                    ) {
                        Icon(
                            painter = painterResource(R.drawable.ic_inbox_24dp),
                            contentDescription = null
                        )
                        Spacer(Modifier.height(16.dp))
                        Text(
                            text = stringResource(R.string.catch_up_empty_title),
                            style = MaterialTheme.typography.titleLarge,
                            textAlign = TextAlign.Center
                        )
                        Spacer(Modifier.height(8.dp))
                        Text(
                            text = stringResource(R.string.catch_up_empty_description),
                            style = MaterialTheme.typography.bodyMedium,
                            textAlign = TextAlign.Center
                        )
                    }
                }

                for (card in cards.reversed()) {
                    when (card) {
                        is CatchUpCard.UnreadMessageInChannel -> {
                            val state = rememberSwipeToDismissBoxState()
                            val channel = StoatAPI.channelCache[card.channelId]
                            val channelName = channel?.catchUpDisplayName()
                                ?: stringResource(R.string.unknown)
                            val serverName = channel?.server?.let {
                                StoatAPI.serverCache[it]?.name
                            }

                            LaunchedEffect(state.currentValue) {
                                if (state.currentValue == SwipeToDismissBoxValue.StartToEnd) {
                                    // Start to end is mark read
                                    viewModel.markAsRead(card)
                                    state.reset()
                                } else if (state.currentValue == SwipeToDismissBoxValue.EndToStart) {
                                    // End to start is skip
                                    viewModel.swipedCard()
                                    state.reset()
                                }
                            }

                            LaunchedEffect(state.dismissDirection) {
                                when (state.dismissDirection) {
                                    SwipeToDismissBoxValue.StartToEnd -> {
                                        currentColour = colourRead
                                    }

                                    SwipeToDismissBoxValue.EndToStart -> {
                                        currentColour = colourKeep
                                    }

                                    SwipeToDismissBoxValue.Settled -> {
                                        currentColour = primaryContainer
                                    }
                                }
                            }

                            SwipeToDismissBox(
                                state = state,
                                modifier = Modifier
                                    .padding(16.dp)
                                    .fillMaxSize(),
                                backgroundContent = {
                                    if (state.dismissDirection == SwipeToDismissBoxValue.StartToEnd) {
                                        Text(
                                            stringResource(R.string.channel_context_sheet_actions_mark_read),
                                            Modifier
                                                .fillMaxWidth()
                                                .background(Color.Green)
                                        )
                                    } else if (state.dismissDirection == SwipeToDismissBoxValue.EndToStart) {
                                        Text(
                                            stringResource(R.string.catch_up_keep_unread),
                                            textAlign = TextAlign.Right,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(Color.Red)
                                        )
                                    }
                                }
                            ) {
                                Card(
                                    modifier = Modifier.fillMaxSize()
                                ) {
                                    Column(
                                        verticalArrangement = Arrangement.spacedBy(12.dp),
                                        modifier = Modifier
                                            .fillMaxSize()
                                            .padding(24.dp)
                                    ) {
                                        Text(
                                            text = channelName,
                                            style = MaterialTheme.typography.headlineSmall,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        serverName?.let {
                                            Text(
                                                text = it,
                                                style = MaterialTheme.typography.labelLarge,
                                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }

                                        if (card.mentionCount > 0) {
                                            Text(
                                                text = stringResource(
                                                    R.string.catch_up_mentions_count,
                                                    card.mentionCount
                                                ),
                                                style = MaterialTheme.typography.bodyMedium
                                            )
                                        }

                                        Text(
                                            text = stringResource(R.string.overview_screen_catch_up_description),
                                            style = MaterialTheme.typography.bodyMedium
                                        )

                                        Spacer(Modifier.weight(1f))
                                        HorizontalDivider()
                                        Column(
                                            verticalArrangement = Arrangement.spacedBy(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            OutlinedButton(
                                                onClick = { viewModel.swipedCard() },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(R.string.catch_up_keep_unread))
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    scope.launch { viewModel.markAsRead(card) }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(R.string.channel_context_sheet_actions_mark_read))
                                            }
                                            Button(
                                                onClick = {
                                                    scope.launch {
                                                        ActionChannel.send(Action.SwitchChannel(card.channelId))
                                                        navController.popBackStack()
                                                    }
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(stringResource(R.string.link_open))
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Channel.catchUpDisplayName(): String {
    return when (channelType) {
        ChannelType.SavedMessages -> stringResource(R.string.channel_notes)
        ChannelType.DirectMessage -> recipients
            ?.firstOrNull { it != StoatAPI.selfId }
            ?.let { StoatAPI.userCache[it]?.displayName ?: StoatAPI.userCache[it]?.username }
            ?: name
            ?: stringResource(R.string.unknown)

        else -> name ?: stringResource(R.string.unknown)
    }
}
