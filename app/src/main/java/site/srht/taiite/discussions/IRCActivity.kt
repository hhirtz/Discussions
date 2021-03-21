package site.srht.taiite.discussions

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.ServiceConnection
import android.os.Build
import android.os.Bundle
import android.os.IBinder
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
            val ircState = this.model.ircState.observeAsState().value
            IRCTheme {
                if (onboarded == null) {
                    LoadingScreen("Loading settings...")
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
                } else if (ircState == null || !ircState.registered.value) {
                    LoadingScreen("Connecting...")
                } else {
                    val screen =
                        this.model.currentScreen.observeAsState(initial = Screen.Home).value
                    val openedChannel =
                        (screen as? Screen.Channel)?.name?.let { ircState.getChannel(it) }
                    if (openedChannel == null) {
                        HomeScreen(
                            nickname = ircState.nickname.value,
                            channelList = ircState.channels.toSortedMap().values.toList(),
                            onChannelClicked = { this.model.openChannel(it) },
                            onChannelJoined = { this.service?.join(it) },
                        )
                    } else {
                        ChannelScreen(
                            channel = openedChannel,
                            onMessageSent = { this.service?.privmsg(screen.name, it) }
                        )
                    }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        this.service?.model = null
    }

    override fun onBackPressed() {
        when (this.model.currentScreen.value) {
            is Screen.Home -> super.onBackPressed()
            is Screen.Channel -> this.model.closeChannel()
        }
    }
}
