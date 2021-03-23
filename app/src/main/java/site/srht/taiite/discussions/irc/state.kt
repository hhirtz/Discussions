package site.srht.taiite.discussions.irc

import android.util.Patterns
import androidx.compose.runtime.State
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.snapshots.SnapshotStateMap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.style.TextDecoration
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.util.*

class IRCUser(var name: IRCPrefix) {
    var isAway: Boolean = false

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (javaClass != other?.javaClass) return false
        other as IRCUser
        if (name != other.name) return false
        if (isAway != other.isAway) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + isAway.hashCode()
        return result
    }
}

private fun annotateURLs(s: AnnotatedString) = buildAnnotatedString {
    this.append(s)
    val urlStyle = SpanStyle(
        color = Color(0xFF9999FF),
        textDecoration = TextDecoration.Underline,
    )
    val urls = Patterns.WEB_URL.matcher(s.text)
    while (urls.find()) {
        val urlStart = urls.start()
        val urlEnd = urls.end()
        val urlString = s.text.substring(urlStart, urlEnd)
        this.addStringAnnotation("URL", urlString, urlStart, urlEnd)
        this.addStyle(urlStyle, urlStart, urlEnd)
    }
}

class IMMessage(val author: String, val date: Date, val content: AnnotatedString) {
    constructor(author: String, date: Date, rawContent: String)
            : this(author, date, annotateURLs(ircFormat(rawContent)))

    fun localDateTime(): LocalDateTime =
        this.date.toInstant().atZone(ZoneId.systemDefault()).toLocalDateTime()

    fun localDate(): LocalDate =
        this.localDateTime().toLocalDate()

    companion object {
        fun fromIRCMessage(msg: IRCMessage): IMMessage? {
            if (msg.command != "PRIVMSG" || msg.prefix == null || msg.params.size < 2) {
                return null
            }
            return IMMessage(msg.prefix.name, msg.dateOrNow(), msg.params[1])
        }
    }
}

class IRCChannel(var name: String) {
    var members = mutableStateMapOf<IRCUser, String>()
    var topic = mutableStateOf("")
    var topicWho: IRCPrefix? = null
    var topicTime: Date? = null
    var secret = false
    var complete = false
    var messages = mutableStateListOf<IMMessage>()

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IRCChannel) return false
        if (name != other.name) return false
        if (members != other.members) return false
        if (topic != other.topic) return false
        if (topicWho != other.topicWho) return false
        if (topicTime != other.topicTime) return false
        if (secret != other.secret) return false
        if (complete != other.complete) return false
        // Only check for size: content is left untouched, if the sizes match then content matches.
        if (messages.size != other.messages.size) return false
        return true
    }

    override fun hashCode(): Int {
        var result = name.hashCode()
        result = 31 * result + members.hashCode()
        result = 31 * result + topic.hashCode()
        result = 31 * result + (topicWho?.hashCode() ?: 0)
        result = 31 * result + (topicTime?.hashCode() ?: 0)
        result = 31 * result + secret.hashCode()
        result = 31 * result + complete.hashCode()
        result =
            31 * result + messages.size.hashCode() // only check for size, see "equals()" comment
        return result
    }
}

abstract class IRCState internal constructor() {
    abstract val nickname: State<String>
    abstract val username: State<String>
    abstract val realName: State<String>
    abstract val registered: State<Boolean>
    abstract val hostname: State<String>
    abstract val account: State<String>
    abstract val users: SnapshotStateMap<String, IRCUser>
    abstract val channels: SnapshotStateMap<String, IRCChannel>
    abstract val typings: SnapshotStateMap<Pair<String, String>, Date>
    abstract val availableCapabilities: Map<String, String>
    abstract val enabledCapabilities: Set<String>
    abstract val featureCASEMAPPING: String
    abstract val featureCHANTYPES: CharArray
    abstract val featurePREFIX: Pair<CharArray, CharArray>

    private fun isChannel(name: String): Boolean =
        name.indexOfAny(this.featureCHANTYPES) == 0

    fun casemap(name: String): String = when (this.featureCASEMAPPING) {
        "ascii" -> casemapASCII(name)
        "rfc1459-strict" -> casemapRFC1459Strict(name)
        else -> casemapRFC1459(name)
    }

    fun unCasemap(name: String): String {
        val nameCM = this.casemap(name)
        if (this.isChannel(name)) {
            val ch = this.channels[nameCM] ?: return name
            return ch.name
        } else {
            val u = this.users[nameCM] ?: return name
            return u.name.name
        }
    }

    fun getChannel(name: String): IRCChannel? = this.channels[this.casemap(name)]

    fun typings(target: String): Set<String> {
        val targetCM = this.casemap(target)
        val names = HashSet<String>()
        synchronized(this.typings) {
            for ((t, name) in this.typings.keys) {
                if (t == targetCM) {
                    names.add(name)
                }
            }
        }
        return names
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is IRCState) return false
        if (nickname != other.nickname) return false
        if (username != other.username) return false
        if (realName != other.realName) return false
        if (registered != other.registered) return false
        if (hostname != other.hostname) return false
        if (account != other.account) return false
        if (users != other.users) return false
        if (channels != other.channels) return false
        if (typings != other.typings) return false
        if (availableCapabilities != other.availableCapabilities) return false
        if (enabledCapabilities != other.enabledCapabilities) return false
        if (featureCASEMAPPING != other.featureCASEMAPPING) return false
        if (!featureCHANTYPES.contentEquals(other.featureCHANTYPES)) return false
        if (featurePREFIX != other.featurePREFIX) return false
        return true
    }

    override fun hashCode(): Int {
        var result = nickname.hashCode()
        result = 31 * result + username.hashCode()
        result = 31 * result + realName.hashCode()
        result = 31 * result + registered.hashCode()
        result = 31 * result + hostname.hashCode()
        result = 31 * result + account.hashCode()
        result = 31 * result + users.hashCode()
        result = 31 * result + channels.hashCode()
        result = 31 * result + typings.hashCode()
        result = 31 * result + availableCapabilities.hashCode()
        result = 31 * result + enabledCapabilities.hashCode()
        result = 31 * result + featureCASEMAPPING.hashCode()
        result = 31 * result + featureCHANTYPES.contentHashCode()
        result = 31 * result + featurePREFIX.hashCode()
        return result
    }
}

internal class MutableIRCState(
    nickname: String,
    username: String,
    realName: String,
) : IRCState() {
    override var nickname = mutableStateOf(nickname)
    override var username = mutableStateOf(username)
    override var realName = mutableStateOf(realName)
    override var registered = mutableStateOf(false)
    var nicknameCM = casemapASCII(nickname)
    override var hostname = mutableStateOf("")
    override var account = mutableStateOf("")
    override var users = mutableStateMapOf<String, IRCUser>()
    override var channels = mutableStateMapOf<String, IRCChannel>()
    override var typings = mutableStateMapOf<Pair<String, String>, Date>()
    override var availableCapabilities = mutableMapOf<String, String>()
    override var enabledCapabilities = mutableSetOf<String>()
    override var featureCASEMAPPING = "rfc1459"
    override var featureCHANTYPES = charArrayOf('#', '&', '+', '!')
    override var featurePREFIX = charArrayOf('o', 'v') to charArrayOf('@', '+')

    fun cleanUser(user: IRCUser) {
        for ((_, channel) in this.channels) {
            if (channel.members.containsKey(user)) {
                return
            }
        }
        this.users.remove(this.casemap(user.name.name))
    }

    fun typingActive(target: String, name: String) {
        val typing = Pair(target, name)
        val now = Date()
        synchronized(this.typings) {
            this.typings[typing] = now
        }
        val state = this@MutableIRCState
        CoroutineScope(Dispatchers.Main).launch {
            delay(6000)
            val newNow = state.typings[typing]
            if (newNow == now) {
                state.typings.remove(typing)
            }
            // TODO send notification to user of Session
        }
    }

    fun typingDone(target: String, name: String) {
        this.typings.remove(Pair(target, name))
    }

    fun addMessages(target: String, messages: List<IMMessage>) {
        val channel = this.channels[this.casemap(target)] ?: return
        channel.messages.addAll(0, messages) // TODO add to the correct location
    }
}
