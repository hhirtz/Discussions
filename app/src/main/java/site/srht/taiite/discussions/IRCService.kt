package site.srht.taiite.discussions

import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.os.Binder
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import io.ktor.network.selector.*
import io.ktor.network.sockets.*
import io.ktor.network.tls.*
import io.ktor.util.network.*
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
            if (model == null) {
                this.idle()
            } else {
                this.resume()
            }
            this.s?.let {
                model?.ircState?.value = it.state
            }
            field = model
        }
    private var s: IRCSession? = null
    private val started = AtomicBoolean(false)
    private val idle = AtomicBoolean(false)
    private var connectJob: CompletableJob? = null
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
            service.stayUpToDateWithParams(service.preferences.clientParams)
        }
        return START_STICKY
    }

    // Must be launched from the main thread.
    private suspend fun stayUpToDateWithParams(paramFlow: Flow<IRCClientParams?>) {
        paramFlow.collectLatest { params ->
            if (params == null) {
                return@collectLatest
            }
            this.stayConnected(params)
        }
    }

    private suspend fun stayConnected(params: IRCClientParams) {
        while (true) {
            this.s?.close()
            val session = withContext(Dispatchers.IO) {
                val job = Job()
                this@IRCService.connectJob = job
                connect(params, job)
            }
            this@IRCService.connectJob = null
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

    // Send IDLE to decrease the number of message we receive.
    fun idle() {
        if (!this.idle.getAndSet(true)) {
            CoroutineScope(Dispatchers.Main).launch {
                this@IRCService.s?.idle()
            }
        }
    }

    // Remove IDLE status and, if we're not connected, try to reconnect now.
    fun resume() {
        if (this.idle.getAndSet(false)) {
            CoroutineScope(Dispatchers.Main).launch {
                this@IRCService.connectJob?.complete()
                this@IRCService.s?.resume()
            }
        }
    }
}

// Keep connecting until it succeeds.  `resetBackoff.complete()` may be called to retry immediately,
// and reset the exponential backoff.
suspend fun connect(params: IRCClientParams, resetBackoff: CompletableJob): IRCSession {
    var backoff = 1.4f // seconds
    while (true) {
        try {
            return tryConnect(params)
        } catch (e: UnresolvedAddressException) {
            // Connection failed, retry.
        } catch (e: IOException) {
            // Connection failed, retry.
        }
        if (backoff < 10 * 60) {
            backoff *= backoff
        }
        withTimeoutOrNull((backoff * 1000f).toLong()) {
            // Wait for either `backoff` seconds, or for `resetBackoff` to be completed.
            resetBackoff.join()
            backoff = 1.4f
        }
    }
}

// Attempts to connect to the IRC server.  Throws an exception on failure.
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
