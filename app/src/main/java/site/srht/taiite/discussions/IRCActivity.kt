package site.srht.taiite.discussions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
import androidx.activity.compose.BackHandler
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.runtime.*
import androidx.compose.runtime.livedata.observeAsState
import androidx.core.view.WindowCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch
import site.srht.taiite.discussions.ui.*

class IRCActivity : AppCompatActivity() {
    private var service: IRCService? = null
    private val model: IRCViewModel by viewModels()

    // "by lazy" is needed here because "this.applicationContext" is null when the activity object
    // is allocated.
    private val preferences by lazy { PreferencesRepository(this.applicationContext) }

    // This activity is coupled with the connection manager service (called "IRCService"). When the
    // activity is created, it starts the service and bind its view-model.
    private val serviceConnection = object : ServiceConnection {
        override fun onServiceConnected(name: ComponentName?, service: IBinder?) {
            val activity = this@IRCActivity
            activity.service = (service as IRCServiceBinder).service
            activity.service?.model = activity.model
        }

        override fun onServiceDisconnected(name: ComponentName?) {
            val activity = this@IRCActivity
            activity.service = null
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        // Bind the service.
        val serviceIntent = Intent(this, IRCService::class.java)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            this.startService(serviceIntent)
        } else {
            this.startForegroundService(serviceIntent)
        }
        this.bindService(serviceIntent, this.serviceConnection, Context.BIND_IMPORTANT)

        WindowCompat.setDecorFitsSystemWindows(this.window, false)
        this.setContent {
            val onboarded = this.preferences.onboarded.collectAsState(initial = null).value
            IRCTheme {
                if (onboarded == null) {
                    LoadingScreen("Loading settings…")
                } else if (!onboarded) {
                    OnboardingScreen(
                        onboard = { serverAddress, serverPort, nickname, password ->
                            this@IRCActivity.lifecycleScope.launch {
                                this@IRCActivity.preferences.onboard(
                                    serverAddress,
                                    serverPort,
                                    nickname,
                                    password,
                                )
                            }
                        },
                    )
                } else {
                    App(this.model, this.service)
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        this.service?.model = null
    }
}

@Composable
fun App(
    model: IRCViewModel,
    service: IRCService?,
) {
    val ircState = model.ircState.observeAsState().value

    if (ircState == null || !ircState.registered.value) {
        LoadingScreen(reason = "Connecting…")
        return
    }

    when (val screen = model.currentScreen.observeAsState(initial = Screen.Home).value) {
        Screen.Home -> {
            HomeScreen(
                nickname = ircState.nickname.value,
                channelList = ircState.channels.toSortedMap().values.toList(),
                onChannelClicked = { model.openChannel(it) },
                onChannelJoined = { service?.join(it) },
            )
        }
        is Screen.Channel -> {
            ChannelScreen(
                channel = ircState.getChannel(screen.name)!!,
                typings = ircState.typings(screen.name),
                onMessageSent = { service?.privmsg(screen.name, it) },
                onTyping = { service?.typing(screen.name) },
                goBack = { model.closeChannel() },
                goToSettings = { model.goToChannelSettings() },
            )
            BackHandler(enabled = true) { model.closeChannel() }
        }
        is Screen.ChannelSettings -> {
            ChannelSettingsScreen(
                channel = ircState.getChannel(screen.name)!!,
                part = {
                    model.closeChannel()
                    service?.part(screen.name)
                },
                detach = {
                    model.closeChannel()
                    service?.part(screen.name, "detach")
                },
            )
            BackHandler(enabled = true) { model.exitChannelSettings() }
        }
    }
}
