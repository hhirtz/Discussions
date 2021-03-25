package site.srht.taiite.discussions.irc

import android.util.Base64
import io.ktor.network.sockets.*
import io.ktor.util.cio.*
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.consumeEach
import site.srht.taiite.discussions.irc.*
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.*

interface SASLClient {
    fun mechanism(): String
    fun respond(challenge: String): String?
}

class SASLPlain(private val username: String, private val password: String) : SASLClient {
    override fun mechanism(): String = "PLAIN"

    override fun respond(challenge: String): String? {
        if (challenge != "+") {
            return null
        }
        val username = this.username.toByteArray()
        val password = this.password.toByteArray()
        val payload = username + 0 + username + 0 + password
        return Base64.encodeToString(payload, Base64.DEFAULT).trim()
    }
}

private val supportedCapabilities = setOf(
    "draft/chathistory",

    //"account-notify",
    //"account-tag",
    //"away-notify",
    "batch",
    "cap-notify",
    "echo-message",
    "extended-join",
    "invite-notify",
    //"labeled-response",
    "message-tags",
    //"multi-prefix",
    "server-time",
    "sasl",
    //"setname",
    //"userhost-in-names",
)

data class IRCSessionParams(
    val nickname: String,
    val username: String,
    val realName: String,
    val auth: SASLClient?,
)

private class ChBatch(val target: String, val messages: MutableList<IMMessage> = mutableListOf())

class IRCSession(private val conn: ReadWriteSocket, params: IRCSessionParams) {
    private val out = writeChannel(conn)

    private val _state = MutableIRCState(params.nickname, params.username, params.realName)
    val state: IRCState = _state

    private val typingStamps = mutableMapOf<String, LocalDateTime>()

    private val _events = Channel<IRCEvent>(64)
    val events: ReceiveChannel<IRCEvent> = _events

    private val auth = params.auth
    private val chathistoryBatches = mutableMapOf<String, ChBatch>()

    init {
        val connIn = readChannel(conn)
        val session = this@IRCSession
        CoroutineScope(Dispatchers.Main).launch {
            session.send("CAP", "LS", "302")
            session.send("NICK", params.nickname)
            session.send(
                "USER",
                params.username,
                "0",
                "*",
                params.realName
            )
            connIn.consumeEach {
                session.handleMessage(it)
            }
            session.close()
        }
    }

    fun close() {
        this.out.close()
        this.conn.close()
    }

    suspend fun join(channel: String) {
        for (prefix in this.state.featureCHANTYPES) {
            if (channel.startsWith(prefix)) {
                this.send("JOIN", channel)
            }
        }
        val prefix = this.state.featureCHANTYPES.firstOrNull() ?: '#'
        this.send("JOIN", "$prefix$channel")
    }

    suspend fun privmsg(target: String, content: String) {
        for (line in content.splitToSequence('\n')) {
            if (line.isBlank()) {
                continue
            }
            this.send("PRIVMSG", target, line)
        }
    }

    suspend fun typing(target: String) {
        if (!this.state.enabledCapabilities.contains("message-tags")) {
            return
        }
        val targetCM = this.state.casemap(target)
        val now = LocalDateTime.now()
        val tooSoon =
            this.typingStamps[targetCM]?.let { it.until(now, ChronoUnit.SECONDS) < 3 } ?: false
        if (tooSoon) {
            return
        }
        this.typingStamps[targetCM] = now
        this.send("@+typing=active", "TAGMSG", target)
    }

    // `before` must be in the UTC zone.
    private suspend fun requestHistoryBefore(target: String, before: LocalDateTime) {
        if (!this.state.enabledCapabilities.contains("draft/chathistory")) {
            return
        }
        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'")
        val criterion = formatter.format(before)
        this.send("CHATHISTORY", "BEFORE", target, "timestamp=$criterion", "100")
    }

    private suspend fun handleMessage(msg: IRCMessage) {
        if (this._state.registered.value) {
            this.handleMessageRegistered(msg)
        } else {
            this.handleMessageUnregistered(msg)
        }
    }

    private suspend fun handleMessageUnregistered(msg: IRCMessage) {
        when (msg.command) {
            "AUTHENTICATE" -> {
                val auth = this.auth ?: return
                val res = auth.respond(msg.params[0])
                if (res == null) {
                    this.send("AUTHENTICATE", "*")
                } else {
                    this.send("AUTHENTICATE", res)
                }
            }
            RPL_LOGGEDIN -> {
                this._state.account.value = msg.params[2]
                this._state.hostname.value = IRCPrefix.tokenize(msg.params[1])?.host ?: ""
                this.endRegistration()
            }
            ERR_NICKLOCKED, ERR_SASLFAIL, ERR_SASLTOOLONG, ERR_SASLABORTED, ERR_SASLALREADY, RPL_SASLMECHS -> {
                this.endRegistration()
            }
            "CAP" -> when (msg.params[1]) {
                "LS" -> {
                    val lastCapLS: Boolean
                    val ls: String
                    if (msg.params[2] == "*") {
                        lastCapLS = false
                        ls = msg.params[3]
                    } else {
                        lastCapLS = true
                        ls = msg.params[2]
                    }
                    for (item in ls.splitToSequence(' ')) {
                        val cap = CapToken.tokenize(item) ?: continue
                        this._state.availableCapabilities[cap.name] = cap.value
                        if (supportedCapabilities.contains(cap.name)) {
                            this.send("CAP", "REQ", cap.name)
                        }
                    }
                    val canRegister = this._state.availableCapabilities.contains("sasl")
                    if (lastCapLS && (this.auth == null || !canRegister)) {
                        this.endRegistration()
                    }
                }
                else -> this.handleMessageRegistered(msg)
            }
            ERR_NICKNAMEINUSE -> {
                this._state.nickname.value = "${this._state.nickname}_"
                this._state.nicknameCM = "${this._state.nicknameCM}_"
                this.send("NICK", this._state.nickname.value)
            }
            else -> this.handleMessageRegistered(msg)
        }
    }

    private suspend fun handleMessageRegistered(msg: IRCMessage) {
        val batchID = msg.tags["batch"]
        if (batchID != null) {
            val batch = this.chathistoryBatches[batchID]
            if (batch != null) {
                IMMessage.fromIRCMessage(msg)?.let { batch.messages.add(it) }
                return
            }
        }
        when (msg.command) {
            RPL_WELCOME -> {
                if (this._state.registered.value) {
                    return
                }
                this._state.registered.value = true
                this._state.nickname.value = msg.params[0]
                this._state.nicknameCM = this._state.casemap(msg.params[0])
                this._state.users[this._state.nicknameCM] =
                    IRCUser(IRCPrefix(msg.params[0], "", ""))
                if (this._state.hostname.value == "") {
                    this.send("WHO", msg.params[0])
                }
            }
            RPL_ISUPPORT -> {
                for (i in 1 until msg.params.size - 1) {
                    val item = msg.params[i]
                    if (item == "" || item == "-" || item == "=" || item == "-=") {
                        continue
                    }
                    if (item[0] == '-') {
                        // TODO support ISUPPORT negations
                        continue
                    }
                    val kv = item.split('=', limit = 2)
                    val key = kv[0]
                    var value = ""
                    if (kv.size == 2) {
                        value = kv[1]
                    }
                    when (key) {
                        "CASEMAPPING" -> if (value != "") {
                            this._state.featureCASEMAPPING = value
                        }
                        "CHANTYPES" -> if (value != "") {
                            this._state.featureCHANTYPES = value.toCharArray()
                        }
                        "PREFIX" -> {
                            if (value == "") {
                                this._state.featurePREFIX = charArrayOf() to charArrayOf()
                            } else if (value.length % 2 == 0 && 2 < value.length) {
                                val numPrefixes = value.length / 2 - 1
                                val modes = value.substring(1, 1 + numPrefixes)
                                    .toCharArray()
                                val symbols = value.substring(2 + numPrefixes)
                                    .toCharArray()
                                this._state.featurePREFIX = modes to symbols
                            }
                        }
                    }
                }
            }
            RPL_WHOREPLY -> {
                val nicknameCM = this._state.casemap(msg.params[5])
                if (this._state.nicknameCM == nicknameCM) {
                    this._state.username.value = msg.params[2]
                    this._state.hostname.value = msg.params[3]
                }
                val u = this._state.users[nicknameCM]
                if (u != null) {
                    // TODO
                }
            }
            "CAP" -> when (msg.params[1]) {
                "ACK" -> {
                    for (item in msg.params[2].splitToSequence(' ')) {
                        val cap = CapToken.tokenize(item) ?: continue
                        if (!cap.enable) {
                            this._state.enabledCapabilities.remove(cap.name)
                            continue
                        }
                        this._state.enabledCapabilities.add(cap.name)
                        if (cap.name == "sasl") {
                            this.auth?.mechanism()?.let { this.send("AUTHENTICATE", it) }
                        } else if (cap.name == "multi-prefix") {
                            for ((_, channel) in this._state.channels) {
                                this.send("NAMES", channel.name)
                            }
                        }
                    }
                }
                "NEW" -> {
                    for (item in msg.params[2].splitToSequence(' ')) {
                        val cap = CapToken.tokenize(item) ?: continue
                        this._state.availableCapabilities[cap.name] = cap.value
                        if (supportedCapabilities.contains(cap.name)) {
                            this.send("CAP", "REQ", cap.name)
                        }
                    }
                }
                "DEL" -> {
                    for (item in msg.params[2].splitToSequence(' ')) {
                        val cap = CapToken.tokenize(item) ?: continue
                        this._state.availableCapabilities.remove(cap.name)
                        this._state.enabledCapabilities.remove(cap.name)
                    }
                }
            }
            "JOIN" -> {
                val nickCM = this._state.casemap(msg.prefix!!.name)
                val channelCM = this._state.casemap(msg.params[0])
                if (nickCM == this._state.nicknameCM) {
                    this._state.channels[channelCM] = IRCChannel(msg.params[0])
                    // JOIN event is on RPL_ENDOFNAMES
                } else {
                    val channel = this._state.channels[channelCM] ?: return
                    val user =
                        this._state.users.computeIfAbsent(nickCM) { IRCUser(msg.prefix.copy()) }
                    channel.members[user] = ""
                }
            }
            "PART" -> {
                val nickCM = this._state.casemap(msg.prefix!!.name)
                val channelCM = this._state.casemap(msg.params[0])
                val channel = this._state.channels[channelCM] ?: return
                if (nickCM == this._state.nicknameCM) {
                    this._state.channels.remove(channelCM)
                    for ((member, _) in channel.members) {
                        this._state.cleanUser(member)
                    }
                } else {
                    val user = this._state.users[nickCM] ?: return
                    channel.members.remove(user)
                    this._state.cleanUser(user)
                }
            }
            "KICK" -> {
                val nickCM = this._state.casemap(msg.params[1])
                val channelCM = this._state.casemap(msg.params[0])
                val channel = this._state.channels[channelCM] ?: return
                if (nickCM == this._state.nicknameCM) {
                    this._state.channels.remove(channelCM)
                    for ((member, _) in channel.members) {
                        this._state.cleanUser(member)
                    }
                } else {
                    val user = this._state.users[nickCM] ?: return
                    channel.members.remove(user)
                    this._state.cleanUser(user)
                }
            }
            "QUIT" -> {
                val nickCM = this._state.casemap(msg.prefix!!.name)
                this._state.users[nickCM]?.let { user ->
                    this._state.channels
                        .asSequence()
                        .filter { (_, channel) -> channel.members.contains(user) }
                        .forEach { (_, channel) ->
                            channel.members.remove(user)
                            this._state.cleanUser(user)
                        }
                }
            }
            RPL_NAMREPLY -> {
                val channelCM = this._state.casemap(msg.params[2])
                val channel = this._state.channels[channelCM] ?: return
                for (item in msg.params[3].splitToSequence(' ')) {
                    val member = IRCMember.tokenizeWithPrefixes(
                        item,
                        this._state.featurePREFIX.second
                    ) ?: continue
                    val nickCM = this._state.casemap(member.name.name)
                    val user = this._state.users.computeIfAbsent(nickCM) { IRCUser(member.name) }
                    channel.members[user] = member.powerLevel
                }
            }
            RPL_ENDOFNAMES -> {
                val channelCM = this._state.casemap(msg.params[1])
                val channel = this._state.channels[channelCM] ?: return
                if (channel.complete) {
                    return
                }
                channel.complete = true
                this._state.channels[channelCM] = channel
                this.requestHistoryBefore(channel.name, LocalDateTime.now(ZoneOffset.UTC))
            }
            RPL_TOPIC -> {
                val channelCM = this._state.casemap(msg.params[1])
                val channel = this._state.channels[channelCM] ?: return
                channel.topic.value = msg.params[2]
            }
            RPL_TOPICWHOTIME -> {
                val channelCM = this._state.casemap(msg.params[1])
                val channel = this._state.channels[channelCM] ?: return
                channel.topicWho = IRCPrefix.tokenize(msg.params[2])
                channel.topicTime =
                    LocalDateTime.ofEpochSecond(msg.params[3].toLong(), 0, ZoneOffset.UTC)
            }
            RPL_NOTOPIC -> {
                val channelCM = this._state.casemap(msg.params[1])
                val channel = this._state.channels[channelCM] ?: return
                channel.topic.value = ""
            }
            "TOPIC" -> {
                val channelCM = this._state.casemap(msg.params[0])
                val channel = this._state.channels[channelCM] ?: return
                val at = msg.dateOrNow()
                channel.topic.value = msg.params[1]
                channel.topicWho = msg.prefix!!.copy()
                channel.topicTime = at
            }
            "PRIVMSG", "NOTICE" -> {
                val target = this._state.unCasemap(msg.params[0])
                val name = this._state.unCasemap(msg.prefix!!.name)
                this._state.typingDone(target, name)
                val targetCM = this._state.casemap(msg.params[0])
                val nameCM = this._state.casemap(msg.prefix.name)
                val channel = this._state.channels[targetCM]
                if (channel != null) {
                    channel.messages.add(IMMessage(name, msg.dateOrNow(), msg.params[1]))
                } else {
                    // TODO
                }
            }
            "TAGMSG" -> {
                val typingState = msg.tags["+typing"] ?: return
                val target = this._state.unCasemap(msg.params[0])
                val name = this._state.unCasemap(msg.prefix!!.name)
                val nameCM = this._state.casemap(msg.prefix.name)
                if (nameCM == this._state.nicknameCM) {
                    return
                }
                when (typingState) {
                    "active" -> {
                        this._state.typingActive(target, name)
                    }
                    "paused", "done" -> {
                        this._state.typingDone(target, name)
                    }
                }
            }
            "BATCH" -> {
                val batchStart = msg.params[0][0] == '+'
                val id = msg.params[0].substring(1)
                if (batchStart) {
                    val batchName = msg.params[1]
                    if (batchName == "chathistory") {
                        val target = msg.params[2]
                        this.chathistoryBatches[id] = ChBatch(target)
                    }
                } else {
                    this.chathistoryBatches[id]?.let {
                        this._state.addMessages(it.target, it.messages)
                        this.chathistoryBatches.remove(id)
                    }
                }
            }
            "NICK" -> {
                val nickCM = this._state.casemap(msg.prefix!!.name)
                val newNick = msg.params[0]
                val newNickCM = this._state.casemap(newNick)
                this._state.users[nickCM]
                    ?.also {
                        it.name.name = newNick
                        this._state.users.remove(nickCM)
                        this._state.users[newNickCM] = it
                    } ?: return

                if (nickCM == this._state.nicknameCM) {
                    this._state.nickname.value = newNick
                    this._state.nicknameCM = newNickCM
                }
            }
            "PING" -> {
                this.send("PONG", msg.params[0])
            }
        }
    }

    private suspend fun endRegistration() {
        this.send("CAP", "END")
    }

    private suspend fun send(command: String, vararg params: String) {
        this.out.send(IRCMessage.simple(command, *params))
    }
}
