package site.srht.taiite.discussions.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowForward
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Eject
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import dev.chrisbanes.accompanist.insets.LocalWindowInsets
import dev.chrisbanes.accompanist.insets.statusBarsPadding
import dev.chrisbanes.accompanist.insets.toPaddingValues
import site.srht.taiite.discussions.irc.IRCChannel

@Composable
fun ChannelSettingsScreen(
    channel: IRCChannel,
    part: () -> Unit,
    detach: () -> Unit,
) {
    var members = channel.members.keys.toList()
    members = members.sortedWith { a, b -> a.name.name.compareTo(b.name.name) }
    Surface {
        LazyColumn(
            contentPadding = LocalWindowInsets.current.systemBars.toPaddingValues(top = false),
            modifier = Modifier.fillMaxSize(),
        ) {
            item {
                Surface(
                    color = MaterialTheme.colors.primarySurface,
                    elevation = 4.dp,
                    modifier = Modifier.fillMaxWidth(),
                ) {
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Text(
                            text = channel.name,
                            style = MaterialTheme.typography.h3,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 8.dp)
                                .statusBarsPadding(),
                        )
                        CompositionLocalProvider(LocalContentAlpha provides ContentAlpha.medium) {
                            Text(
                                text = channel.topic.value,
                                style = MaterialTheme.typography.caption,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 8.dp),
                            )
                        }
                    }
                }
            }
            item {
                Header("Options")
                Divider()
            }
            item {
                CompositionLocalProvider(LocalContentColor provides Color.Red) {
                    TextOption(
                        text = "Part",
                        icon = Icons.Rounded.Delete,
                        isMenu = false,
                        onClick = part,
                    )
                }
                Divider()
            }
            item {
                TextOption(
                    text = "Detach",
                    icon = Icons.Rounded.Eject,
                    isMenu = false,
                    onClick = detach,
                )
                Divider()
            }
            item {
                Header("${channel.members.size} members")
                Divider()
            }
            items(members) {
                Text(
                    text = it.name.name,
                    style = MaterialTheme.typography.body1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier
                        .padding(horizontal = 16.dp)
                        .padding(bottom = 2.dp),
                )
            }
        }
    }
}

@Composable
fun Header(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.h6,
        modifier = Modifier.padding(start = 32.dp, end = 8.dp, top = 16.dp),
    )
}

@Composable
fun TextOption(
    text: String,
    icon: ImageVector,
    isMenu: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .clickable(onClick = onClick)
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 16.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = text,
            modifier = Modifier.align(Alignment.CenterVertically),
        )
        Text(
            text = text,
            modifier = Modifier
                .padding(horizontal = 12.dp)
                .weight(1f)
                .align(Alignment.CenterVertically),
        )
        if (isMenu) {
            Icon(
                imageVector = Icons.Outlined.ArrowForward,
                contentDescription = "",
                modifier = Modifier.align(Alignment.CenterVertically),
            )
        }
    }
}
