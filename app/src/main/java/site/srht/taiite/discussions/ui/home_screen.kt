package site.srht.taiite.discussions.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.accompanist.insets.LocalWindowInsets
import dev.chrisbanes.accompanist.insets.navigationBarsPadding
import dev.chrisbanes.accompanist.insets.toPaddingValues
import kotlinx.coroutines.delay
import site.srht.taiite.discussions.irc.IRCChannel

@Composable
fun HomeScreen(
    nickname: String,
    channelList: List<IRCChannel>,
    onChannelClicked: (String) -> Unit,
    onChannelJoined: (String) -> Unit,
) {
    val joinChannelFormShown = remember { mutableStateOf(false) }
    Scaffold(
        topBar = { HomeTopBar(nickname, channelList) },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { joinChannelFormShown.value = true },
                modifier = Modifier.navigationBarsPadding(),
            ) {
                Icon(
                    imageVector = Icons.Outlined.Add,
                    contentDescription = "Join a channel"
                )
            }
        }
    ) { innerPadding ->
        ChannelList(
            channels = channelList,
            onClick = onChannelClicked,
            modifier = Modifier.padding(innerPadding),
        )
        if (joinChannelFormShown.value) {
            JoinChannelForm(
                dismiss = { joinChannelFormShown.value = false },
                onSubmit = {
                    joinChannelFormShown.value = false
                    onChannelJoined(it)
                },
            )
        }
    }
}

@Composable
fun HomeTopBar(nickname: String, channels: List<IRCChannel>) {
    InsetAwareTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    text = nickname,
                    style = MaterialTheme.typography.subtitle1,
                )
                CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                    Text(
                        text = "${channels.size} channels",
                        style = MaterialTheme.typography.caption,
                    )
                }
            }
        },
        actions = {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = "Settings",
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp), // TODO show unimplemented dialog
            )
        },
    )
}

@Composable
fun ChannelList(
    channels: List<IRCChannel>,
    onClick: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    LazyColumn(
        contentPadding = LocalWindowInsets.current.systemBars.toPaddingValues(top = false),
        modifier = modifier,
    ) {
        items(channels) {
            ChannelListItem(
                channel = it,
                onClick = onClick,
            )
        }
    }
}

@Composable
fun ChannelListItem(channel: IRCChannel, onClick: (String) -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = { onClick(channel.name) })
    ) {
        Text(
            text = channel.name,
            style = MaterialTheme.typography.h6,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Text(
            text = channel.topic.value,
            style = MaterialTheme.typography.subtitle1,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Divider()
    }
}

@Composable
fun JoinChannelForm(
    dismiss: () -> Unit,
    onSubmit: (String) -> Unit,
) {
    val name = remember { mutableStateOf("") }
    val textFieldFocus = FocusRequester()
    AlertDialog(
        onDismissRequest = dismiss,
        title = { Text("") }, // hack to add a margin above the outlined button.
        confirmButton = {
            OutlinedButton(
                onClick = { onSubmit(name.value) },
                enabled = name.value.isNotBlank(),
            ) {
                Text("Join")
            }
        },
        text = {
            OutlinedTextField(
                value = name.value,
                onValueChange = { name.value = it },
                label = { Text("Channel name") },
                isError = name.value.isBlank(),
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    keyboardType = KeyboardType.Email,
                    imeAction = ImeAction.Done,
                ),
                modifier = Modifier.focusRequester(textFieldFocus),
            )
        },
        modifier = Modifier.fillMaxWidth(0.8f),
    )
    LaunchedEffect(null) { // Focus the text field when the popup is drawn.
        // It seems the coroutine must be suspended (exited) once in order to show the keyboard,
        // otherwise the text field receives the focus but does not show it.  The delay is set to 3
        // frames (48ms) so that the async runtime has time to run the coroutine responsible for the
        // composition of "OutlinedTextField" before this (looks pretty consistent so far).
        delay(48)
        textFieldFocus.requestFocus()
    }
}
