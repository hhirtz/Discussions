package site.srht.taiite.discussions

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.consumeEach
import kotlinx.coroutines.flow.*
import site.srht.taiite.discussions.irc.IRCSession
import site.srht.taiite.discussions.irc.IRCSessionParams
import java.io.IOException
import java.time.LocalDateTime
import java.util.concurrent.atomic.AtomicBoolean

data class IRCClientParams(
    val serverAddress: String,
    val serverPort: Int,
    val serverInsecure: Boolean,
    val sessionParams: IRCSessionParams,
)

class IRCServiceBinder(val service: IRCService) : Binder()

class IRCService : Service() {
    internal var model: IRCViewModel? = null
        set(model) {
            this.s?.let {
                model?.ircState?.value = it.state
            }
            field = model
        }
    private var s: IRCSession? = null
    private var started = AtomicBoolean(false)
    private val preferences by lazy { PreferencesRepository(this.applicationContext) }

    override fun onBind(intent: Intent?): IBinder {
        return IRCServiceBinder(this)
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (this.started.getAndSet(true)) {
            return START_STICKY
        }
        val pendingIntent: PendingIntent =
            Intent(this, IRCActivity::class.java).let { notificationIntent ->
                var flag = PendingIntent.FLAG_UPDATE_CURRENT
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                    flag = flag or PendingIntent.FLAG_IMMUTABLE
                }
                PendingIntent.getActivity(this, 0, notificationIntent, flag)
            }
        val notification = NotificationCompat.Builder(this, IRC_SERVICE_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle("Discussions is connected")
            .setContentText("You may turn off this notification with a long press.")
            .setContentIntent(pendingIntent)
            .setSilent(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            // Hide the notification (not allowed on Oreo and higher).
            notification.priority = NotificationCompat.PRIORITY_MIN
        }
        this.startForeground(1, notification.build())
        CoroutineScope(Dispatchers.Main).launch {
            val service = this@IRCService
            service.stayConnected(service.preferences.clientParams)
        }
        return START_STICKY
    }

    // Must be launched from the main thread.
    private suspend fun stayConnected(paramFlow: Flow<IRCClientParams?>) {
        Log.w("IRC_SERVICE", "Launching connection loop")
        paramFlow.collectLatest { params ->
            if (params == null) {
                Log.w("IRC_SERVICE", "Parameter changed, they were null")
                return@collectLatest
            }
            Log.w("IRC_SERVICE", "Parameter changed, connecting to IRC server")
            while (true) {
                this.s?.close()
                val session = withContext(Dispatchers.IO) { connect(params) }
                Log.w("IRC_SERVICE", "Connected to server, starting event loop")
                this.s = session
                this.model?.ircState?.value = session.state
                session.events.consumeEach {
                    // TODO find a better way to wait for connection to be closed.
                }
                session.close()
                this.s = null
                this.model?.ircState?.value = null
            }
        }
    }

    fun privmsg(target: String, content: String) {
        CoroutineScope(Dispatchers.Main).launch {
            this@IRCService.s?.privmsg(target, content)
        }
    }

    fun join(channel: String) {
        CoroutineScope(Dispatchers.Main).launch {
            this@IRCService.s?.join(channel)
        }
    }

    fun part(channel: String, reason: String = "") {
        CoroutineScope(Dispatchers.Main).launch {
            this@IRCService.s?.part(channel, reason)
        }
    }

    fun typing(target: String) {
        CoroutineScope(Dispatchers.Main).launch {
            this@IRCService.s?.typing(target)
        }
    }

    // `before` must be in the UTC zone.
    fun requestHistoryBefore(target: String, before: LocalDateTime) {
        CoroutineScope(Dispatchers.Main).launch {
            this@IRCService.s?.requestHistoryBefore(target, before)
        }
    }
}

suspend fun connect(params: IRCClientParams): IRCSession {
    var backoff = 1000L // milliseconds
    while (true) {
        try {
            return tryConnect(params)
        } catch (e: IOException) {
            Log.i("IRC_SERVICE", "Connection failed")
        }
        if (backoff < 10 * 60 * 1000) {
            backoff *= 3
        }
        delay(backoff)
    }
}

suspend fun tryConnect(params: IRCClientParams): IRCSession {
    var conn = aSocket(ActorSelectorManager(Dispatchers.IO))
        .tcp()
        .connect(params.serverAddress, params.serverPort) {
            this.keepAlive = false
        }
    if (!params.serverInsecure) {
        conn = conn.tls(Dispatchers.IO) {
            this.serverName = params.serverAddress
        }
    }
    return IRCSession(conn, params.sessionParams)
}
