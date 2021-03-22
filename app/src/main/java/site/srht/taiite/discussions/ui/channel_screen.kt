package site.srht.taiite.discussions.ui

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Search
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.layout.LastBaseline
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.accompanist.insets.navigationBarsWithImePadding
import kotlinx.coroutines.launch
import site.srht.taiite.discussions.irc.IMMessage
import site.srht.taiite.discussions.irc.IRCChannel
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.time.format.FormatStyle

@Composable
fun ChannelScreen(
    channel: IRCChannel,
    onMessageSent: (String) -> Unit
) {
    val messageListState = rememberLazyListState()
    val scope = rememberCoroutineScope()
    val scrollToBottom: () -> Unit = { scope.launch { messageListState.scrollToItem(0) } }
    Scaffold(
        topBar = { ChannelTopBar(channel) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize(),
        ) {
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxSize(),
            ) {
                Messages(
                    messages = channel.messages,
                    listState = messageListState,
                )
                val firstVisibleIndex =
                    messageListState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: 0
                if (firstVisibleIndex != 0) {
                    FloatingActionButton(
                        onClick = scrollToBottom,
                        modifier = Modifier
                            .padding(16.dp)
                            .align(Alignment.BottomEnd),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.KeyboardArrowDown,
                            contentDescription = "Scroll to bottom",
                        )
                    }
                }
            }
            UserInput(
                onMessageSent = {
                    onMessageSent(it)
                    scrollToBottom()
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .navigationBarsWithImePadding(),
            )
        }
    }
}

@Composable
fun ChannelTopBar(channel: IRCChannel) {
    InsetAwareTopAppBar(
        title = {
            Column(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(channel.name)
                if (channel.topic.value.isNotBlank()) {
                    CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                        Text(
                            text = channel.topic.value,
                            style = MaterialTheme.typography.caption,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        },
        actions = {
            Icon(
                imageVector = Icons.Outlined.Search,
                contentDescription = "Search",
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp), // TODO show unimplemented dialog
            )
            Icon(
                imageVector = Icons.Outlined.Info,
                contentDescription = "Channel info",
                modifier = Modifier
                    .padding(horizontal = 12.dp, vertical = 16.dp)
                    .height(24.dp), // TODO show unimplemented dialog
            )
        },
    )
}

@Composable
fun Messages(
    messages: List<IMMessage>,
    listState: LazyListState,
    modifier: Modifier = Modifier,
) {
    val messagesR = messages.asReversed()
    LazyColumn(
        state = listState,
        modifier = modifier,
        reverseLayout = true,
    ) {
        itemsIndexed(messagesR) { index, message ->
            val prevMsg = messagesR.getOrNull(index + 1)
            val isNewDay = prevMsg?.localDate() != message.localDate()
            val isFirstMessageByAuthor = prevMsg?.author != message.author || isNewDay
            Message(message, isFirstMessageByAuthor)
            if (isNewDay) {
                DayHeader(
                    userFriendlyDate(message.localDateTime())
                )
            }
        }
    }
}

fun userFriendlyDate(date: LocalDateTime): String {
    val now = LocalDate.now()
    val then = date.toLocalDate()
    if (then == now) {
        return "Today"
    }
    if (then.plusDays(1) == now) {
        return "Yesterday"
    }
    val elapsed = then.until(now)
    if (elapsed.days < 8) {
        return "${elapsed.days} days ago"
    }
    val formatter = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL)
    return then.format(formatter)
}

@Composable
fun DayHeader(dayString: String) {
    Row(
        modifier = Modifier
            .padding(horizontal = 16.dp)
            .padding(top = 8.dp)
            .height(16.dp)
    ) {
        DayHeaderLine()
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                text = dayString,
                modifier = Modifier.padding(horizontal = 16.dp),
                style = MaterialTheme.typography.overline,
            )
        }
        DayHeaderLine()
    }
}

@Composable
fun RowScope.DayHeaderLine() {
    Divider(
        modifier = Modifier
            .weight(1f)
            .align(Alignment.CenterVertically),
        color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f),
    )
}

@Composable
fun Message(
    message: IMMessage,
    isFirstMessageByAuthor: Boolean,
) {
    val spaceBetweenAuthors = if (isFirstMessageByAuthor) {
        Modifier.padding(top = 8.dp)
    } else {
        Modifier
    }
    Column(
        modifier = spaceBetweenAuthors.padding(start = 8.dp, end = 8.dp),
    ) {
        if (isFirstMessageByAuthor) {
            AuthorAndTimestamp(message)
        }
        Text(
            text = message.content,
            style = MaterialTheme.typography.body1.copy(
                color = LocalContentColor.current
            ),
            modifier = Modifier.padding(start = 4.dp, end = 4.dp),
        )
    }
}

@Composable
fun AuthorAndTimestamp(message: IMMessage) {
    val timeFormatter = DateTimeFormatter.ofLocalizedTime(FormatStyle.SHORT)
    Row(modifier = Modifier.semantics(mergeDescendants = true) {}) {
        Text(
            text = message.author,
            modifier = Modifier
                .alignBy(LastBaseline)
                .paddingFrom(LastBaseline, after = 4.dp), // Space to 1st bubble
            style = MaterialTheme.typography.subtitle1
        )
        Spacer(modifier = Modifier.width(8.dp))
        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
            Text(
                text = message.localDateTime().format(timeFormatter),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.alignBy(LastBaseline)
            )
        }
    }
}

@Composable
fun UserInput(
    onMessageSent: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    var textState by remember { mutableStateOf(TextFieldValue()) }

    Column(modifier) {
        Divider()
        Row(
            modifier = Modifier.fillMaxWidth()
        ) {
            UserInputText(
                textFieldValue = textState,
                onTextChanged = { textState = it },
                modifier = Modifier
                    .weight(1f)
                    .heightIn(min = 48.dp, max = 144.dp)
                    .align(Alignment.Bottom),
            )

            // Send button
            val border = if (textState.text.isBlank()) {
                BorderStroke(
                    width = 1.dp,
                    color = MaterialTheme.colors.onSurface.copy(alpha = 0.12f)
                )
            } else {
                null
            }
            val disabledContentColor =
                MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
            val buttonColors = ButtonDefaults.buttonColors(
                disabledBackgroundColor = MaterialTheme.colors.surface,
                disabledContentColor = disabledContentColor
            )
            Button(
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .height(36.dp)
                    .align(Alignment.CenterVertically),
                enabled = textState.text.isNotBlank(),
                onClick = {
                    if (textState.text.isNotBlank()) {
                        onMessageSent(textState.text)
                        textState = TextFieldValue() // empty text field
                    }
                },
                colors = buttonColors,
                border = border,
                // TODO: Workaround for https://issuetracker.google.com/158830170
                contentPadding = PaddingValues(0.dp)
            ) {
                Text(
                    "Send",
                    modifier = Modifier.padding(horizontal = 16.dp)
                )
            }
        }
    }
}

@Composable
private fun UserInputText(
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onTextChanged: (TextFieldValue) -> Unit,
    textFieldValue: TextFieldValue,
) {
    Box(
        modifier = modifier,
    ) {
        BasicTextField(
            value = textFieldValue,
            onValueChange = { onTextChanged(it) },
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp)
                .align(Alignment.CenterStart),
            keyboardOptions = KeyboardOptions(
                keyboardType = keyboardType,
                imeAction = ImeAction.Send,
            ),
            cursorBrush = SolidColor(LocalContentColor.current),
            textStyle = LocalTextStyle.current.copy(color = LocalContentColor.current)
        )

        val disableContentColor =
            MaterialTheme.colors.onSurface.copy(alpha = ContentAlpha.disabled)
        if (textFieldValue.text.isEmpty()) {
            Text(
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 16.dp),
                text = "Send a message...",
                style = MaterialTheme.typography.body1.copy(color = disableContentColor)
            )
        }
    }
}
