package site.srht.taiite.discussions.irc

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.core.text.isDigitsOnly
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
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

    fun date(): LocalDateTime? =
        this.tags["time"]?.let {
            val parsed = DateTimeFormatter.ISO_OFFSET_DATE_TIME.parse(it)
            LocalDateTime.from(parsed)
        }

    fun dateOrNow(): LocalDateTime = this.date() ?: LocalDateTime.now(ZoneOffset.UTC)

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

fun ircFormat(raw: String): AnnotatedString {
    val formatted = AnnotatedString.Builder(capacity = raw.length)

    // Return true iff the given style is starting.
    val addStyle: (OptInt, SpanStyle) -> Boolean = { start, style ->
        start.value?.let { formatted.addStyle(style, it, formatted.length) }
        val res = start.value == null
        start.value = null
        res
    }

    // Every time a formatting code is encountered:
    // For the matching formatting style, the position of the last encountered code (if any) is used
    // to call `addStyle`, which will call `Annotated.addStyle` from said last position to the end
    // of the annotated string.
    val boldStart = OptInt()
    val addBold = { addStyle(boldStart, SpanStyle(fontWeight = FontWeight.Bold)) }

    var color = ColorCode(0, Color.Unspecified, Color.Unspecified)
    val colorStart = OptInt()
    val addColor = { /* TODO: need to add color depending on user theme */ }

    val monospaceStart = OptInt()
    val addMonospace =
        { addStyle(monospaceStart, SpanStyle(fontFamily = FontFamily.Monospace)) }

    val italicStart = OptInt()
    val addItalic = { addStyle(italicStart, SpanStyle(fontStyle = FontStyle.Italic)) }

    val strikethroughStart = OptInt()
    val addStrikethrough =
        { addStyle(strikethroughStart, SpanStyle(textDecoration = TextDecoration.LineThrough)) }

    val underlineStart = OptInt()
    val addUnderline =
        { addStyle(underlineStart, SpanStyle(textDecoration = TextDecoration.Underline)) }

    val resetFormatting: () -> Unit = {
        addBold()
        addColor()
        addMonospace()
        addItalic()
        addStrikethrough()
        addUnderline()
    }

    var i = 0
    while (i < raw.length) {
        when (raw[i]) {
            '\u0002' -> if (addBold()) boldStart.value = formatted.length
            '\u0003' -> { // ansi color code
                addColor()
                val code = ColorCodeTokenizer(raw.substring(i + 1)).tokenize()
                i += code.size
                if (code.fgColor != Color.Unspecified && code.bgColor != Color.Unspecified) {
                    color = code
                    colorStart.value = formatted.length
                }
            }
            '\u0004' -> { // hex color code
                // unimplemented
            }
            '\u000F' -> resetFormatting()
            '\u0011' -> if (addMonospace()) monospaceStart.value = formatted.length
            '\u0016' -> { // reverse colors
                addColor()
                color = ColorCode(size = 0, fgColor = color.bgColor, bgColor = color.fgColor)
                colorStart.value = formatted.length
            }
            '\u001D' -> if (addItalic()) italicStart.value = formatted.length
            '\u001E' -> if (addStrikethrough()) strikethroughStart.value = formatted.length
            '\u001F' -> if (addUnderline()) underlineStart.value = formatted.length
            else -> formatted.append(raw[i])
        }
        i++
    }

    resetFormatting()
    return formatted.toAnnotatedString()
}

// Mutable, optional integer.
class OptInt(var value: Int? = null)

private class ColorCode(val size: Int, val fgColor: Color, val bgColor: Color)

private class ColorCodeTokenizer(var s: String) {
    private var parsed: Int = 0

    private fun colorString(): String = when (this.s.length) {
        0 -> ""
        1 -> if (this.s[0].isAsciiDigit()) this.s.substring(0, 1) else ""
        else -> if (this.s[0].isAsciiDigit()) {
            if (this.s[1].isAsciiDigit()) {
                this.s.substring(0, 2)
            } else {
                this.s.substring(0, 1)
            }
        } else {
            ""
        }
    }

    private fun color(): Color {
        val ircCode = this.colorString()
        if (ircCode == "") {
            return Color.Unspecified
        }
        this.s = this.s.substring(ircCode.length)
        this.parsed += ircCode.length
        val rgbCode = ircColorToRGB[ircCode.toInt()]
        return Color(rgbCode)
    }

    fun tokenize(): ColorCode {
        val fgColor = this.color()
        if (this.s == "" || this.s[0] != ',') {
            return ColorCode(this.parsed, fgColor, Color.Unspecified)
        }
        this.s = this.s.substring(1)
        val bgColor = this.color()
        if (fgColor == Color.Unspecified && bgColor == Color.Unspecified) {
            // Don't take the ',' into account (it's a literal ',').
            return ColorCode(0, Color.Unspecified, Color.Unspecified)
        }
        // "+ 1" for the separator ','
        return ColorCode(this.parsed + 1, fgColor, bgColor)
    }
}

fun Char.isAsciiDigit(): Boolean = this in '0'..'9'

private val ircColorToRGB = intArrayOf( // cc delthas ^^
    0xffffff,
    0x000000,
    0x00007f,
    0x009300,
    0xff0000,
    0x7f0000,
    0x9c009c,
    0xfc7f00,
    0xffff00,
    0x00fc00,
    0x009393,
    0x00ffff,
    0x0000fc,
    0xff00ff,
    0x7f7f7f,
    0x2d2d2d,
    0x470000,
    0x472100,
    0x474700,
    0x324700,
    0x004700,
    0x00472c,
    0x004747,
    0x002747,
    0x000047,
    0x2e0047,
    0x470047,
    0x47002a,
    0x740000,
    0x743a00,
    0x747400,
    0x517400,
    0x007400,
    0x007449,
    0x007474,
    0x004074,
    0x000074,
    0x4b0074,
    0x740074,
    0x740045,
    0xb50000,
    0xb56300,
    0xb5b500,
    0x7db500,
    0x00b500,
    0x00b571,
    0x00b5b5,
    0x0063b5,
    0x0000b5,
    0x7500b5,
    0xb500b5,
    0xb5006b,
    0xff0000,
    0xff8c00,
    0xffff00,
    0xb2ff00,
    0x00ff00,
    0x00ffa0,
    0x00ffff,
    0x008cff,
    0x0000ff,
    0xa500ff,
    0xff00ff,
    0xff0098,
    0xff5959,
    0xffb459,
    0xffff71,
    0xcfff60,
    0x6fff6f,
    0x65ffc9,
    0x6dffff,
    0x59b4ff,
    0x5959ff,
    0xc459ff,
    0xff66ff,
    0xff59bc,
    0xff9c9c,
    0xffd39c,
    0xffff9c,
    0xe2ff9c,
    0x9cff9c,
    0x9cffdb,
    0x9cffff,
    0x9cd3ff,
    0x9c9cff,
    0xdc9cff,
    0xff9cff,
    0xff94d3,
    0x000000,
    0x131313,
    0x282828,
    0x363636,
    0x4d4d4d,
    0x656565,
    0x818181,
    0x9f9f9f,
    0xbcbcbc,
    0xe2e2e2,
    0xffffff
)
