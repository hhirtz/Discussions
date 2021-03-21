package site.srht.taiite.discussions.irc

import androidx.core.text.isDigitsOnly
import java.text.SimpleDateFormat
import java.util.*

fun casemapASCII(name: String): String {
    val sb = StringBuilder(name.length)
    for (r in name) when (r) {
        in 'A'..'Z' -> sb.append(r.toLowerCase())
        else -> sb.append(r)
    }
    return sb.toString()
}

fun casemapRFC1459(name: String): String {
    val sb = StringBuilder(name.length)
    for (r in name) when (r) {
        in 'A'..'Z' -> sb.append(r.toLowerCase())
        '[' -> sb.append('{')
        ']' -> sb.append('{')
        '\\' -> sb.append('|')
        '~' -> sb.append('^')
        else -> sb.append(r)
    }
    return sb.toString()
}

fun casemapRFC1459Strict(name: String): String {
    val sb = StringBuilder(name.length)
    for (r in name) when (r) {
        in 'A'..'Z' -> sb.append(r.toLowerCase())
        '[' -> sb.append('{')
        ']' -> sb.append('{')
        '\\' -> sb.append('|')
        else -> sb.append(r)
    }
    return sb.toString()
}

fun unescapeTagValue(escaped: String): String {
    val sb = StringBuilder(escaped.length)
    var escape = false
    for (c in escaped) {
        if (c == '\\' && !escape) {
            escape = true
        } else {
            if (escape) {
                sb.append(
                    when (c) {
                        ':' -> ';'
                        's' -> ' '
                        'r' -> '\r'
                        'n' -> '\n'
                        else -> c
                    }
                )
            } else {
                sb.append(c)
            }
            escape = false
        }
    }
    return sb.toString()
}

fun escapeTagValue(unescaped: String): String {
    val sb = StringBuilder(unescaped.length)
    for (c in unescaped) when (c) {
        ';' -> sb.append("\\:")
        ' ' -> sb.append("\\s")
        '\r' -> sb.append("\\r")
        '\n' -> sb.append("\\n")
        '\\' -> sb.append("\\\\")
        else -> sb.append(c)
    }
    return sb.toString()
}

data class IRCPrefix(var name: String, var user: String, var host: String) {
    override fun toString(): String =
        if (this.user != "" && this.host != "") {
            this.name + "!" + this.user + "@" + this.host
        } else if (this.user != "") {
            this.name + "!" + this.user
        } else if (this.host != "") {
            this.name + "@" + this.host
        } else {
            this.name
        }

    companion object {
        fun tokenize(s: String): IRCPrefix? {
            if (s == "") {
                return null
            }
            var user = ""
            var host = ""
            val spl0 = s.split('@', limit = 2)
            if (1 < spl0.size) {
                host = spl0[1]
            }
            val spl1 = spl0[0].split('!', limit = 2)
            if (1 < spl1.size) {
                user = spl1[1]
            }
            return IRCPrefix(spl1[0], user, host)
        }
    }
}

data class IRCMessage(
    val tags: MutableMap<String, String> = mutableMapOf(),
    val prefix: IRCPrefix? = null,
    val command: String,
    val params: ArrayList<String>
) {
    fun isValid(): Boolean = when (this.command) {
        "AUTHENTICATE", "PING" ->
            1 <= this.params.size
        "PONG", ERR_NICKNAMEINUSE, RPL_ENDOFNAMES, RPL_LOGGEDOUT, RPL_MOTD, RPL_NOTOPIC, RPL_WELCOME, RPL_YOURHOST ->
            2 <= this.params.size
        "FAIL", "WARN", "NOTE", RPL_ISUPPORT, RPL_LOGGEDIN, RPL_TOPIC ->
            3 <= this.params.size
        RPL_NAMREPLY ->
            4 <= this.params.size
        RPL_WHOREPLY ->
            8 <= this.params.size
        "QUIT" ->
            this.prefix != null
        "JOIN", "NICK", "PART", "TAGMSG" ->
            this.prefix != null && 1 <= this.params.size
        "KICK", "PRIVMSG", "NOTICE", "TOPIC" ->
            this.prefix != null && 2 <= this.params.size
        "CAP" ->
            3 <= this.params.size && when (this.params[1]) {
                "LS", "LIST", "ACK", "NAK", "NEW", "DEL" -> true
                else -> false
            }
        RPL_TOPICWHOTIME ->
            4 <= this.params.size && this.params[3].isDigitsOnly()
        "BATCH" ->
            1 <= this.params.size && 2 <= this.params[0].length &&
                    (
                            this.params[0][0] == '-' ||
                                    (this.params[0][0] == '+' && 2 <= this.params.size && when (this.params[1]) {
                                        "chathistory" -> 3 <= this.params.size
                                        else -> false
                                    })
                            )
        else ->
            3 <= this.params.size && this.command.length == 3 && this.command.isDigitsOnly()
    }

    fun date(): Date? =
        this.tags["time"]?.let {
            SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'", Locale.ENGLISH)
                .parse(it)
        }

    fun dateOrNow(): Date = this.date() ?: Date()

    override fun toString(): String {
        val sb = StringBuilder()
        if (this.tags.isNotEmpty()) {
            sb.append('@')
            for ((k, v) in this.tags) {
                sb.append(k)
                if (v != "") {
                    sb.append('=')
                    sb.append(escapeTagValue(v))
                }
                sb.append(';')
            }
            sb.append(' ')
        }
        if (this.prefix != null) {
            sb.append(':')
            sb.append(this.prefix.toString())
            sb.append(' ')
        }
        sb.append(this.command)
        if (this.params.isNotEmpty()) {
            for (i in 0 until this.params.size - 1) {
                sb.append(' ')
                sb.append(this.params[i])
            }
            sb.append(' ')
            if (this.params.last().contains(' ')) {
                sb.append(':')
            }
            sb.append(this.params.last())
        }
        return sb.toString()
    }

    companion object {
        fun tokenize(line: String): IRCMessage? = Tokenizer(line).message()

        fun simple(command: String, vararg params: String) =
            IRCMessage(command = command, params = params.toCollection(ArrayList()))
    }
}

private class Tokenizer(var s: String) {
    private fun word(): String {
        var end = this.s.indexOf(' ')
        if (end < 0) {
            end = this.s.length
        }
        var start = end
        if (start + 1 < this.s.length) {
            start += 1
        }
        val word = this.s.substring(0, end)
        this.s = this.s.substring(start, this.s.length)
        return word
    }

    private fun tags(): MutableMap<String, String> {
        val res = mutableMapOf<String, String>()
        if (this.s == "") {
            return res
        }
        if (this.s[0] != '@') {
            return res
        }
        val w = this.word().substring(1)
        for (item in w.splitToSequence(';')) {
            if (item == "" || item == "=" || item == "+" || item == "+=") {
                continue
            }
            val kv = item.split('=', limit = 2)
            if (kv.size == 1) {
                res[kv[0]] = ""
            } else {
                res[kv[0]] = unescapeTagValue(kv[1])
            }
        }
        return res
    }

    private fun prefix(): IRCPrefix? {
        if (this.s == "") {
            return null
        }
        if (this.s[0] != ':') {
            return null
        }
        val w = this.word().substring(1)
        return IRCPrefix.tokenize(w)
    }

    private fun param(): String {
        if (this.s == "") {
            return ""
        }
        if (this.s[0] != ':') {
            return this.word()
        }
        val res = this.s.substring(1)
        this.s = ""
        return res
    }

    fun message(): IRCMessage? {
        this.s = this.s.trimStart().trimEnd('\r', '\n')
        if (this.s == "") {
            return null
        }
        val tags = this.tags()
        val prefix = this.prefix()
        val command = this.word()
        if (command == "") {
            return null
        }
        val params = arrayListOf<String>()
        while (this.s != "") {
            params.add(this.param())
        }
        return IRCMessage(tags, prefix, command, params)
    }
}

data class CapToken(val name: String, val value: String, val enable: Boolean) {
    companion object {
        fun tokenize(item: String): CapToken? {
            if (item == "" || item == "-" || item == "=" || item == "-=") {
                return null
            }
            var s = item
            var enable = true
            if (s[0] == '-') {
                enable = false
                s = s.substring(1)
            }
            val kv = s.split('=', limit = 2)
            return if (kv.size == 2) {
                CapToken(casemapASCII(kv[0]), kv[1], enable)
            } else {
                CapToken(casemapASCII(kv[0]), "", enable)
            }
        }
    }
}

data class IRCMember(val powerLevel: String, val name: IRCPrefix) {
    companion object {
        fun tokenizeWithPrefixes(item: String, prefixes: CharArray): IRCMember? {
            val nameString = item.trimStart { prefixes.contains(it) }
            val name = IRCPrefix.tokenize(nameString) ?: return null
            val powerLevel = item.substring(0, item.length - nameString.length)
            return IRCMember(powerLevel, name)
        }
    }
}
