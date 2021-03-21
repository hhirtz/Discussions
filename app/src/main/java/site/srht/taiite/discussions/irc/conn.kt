package site.srht.taiite.discussions.irc

import android.util.Log
import io.ktor.network.sockets.*
import io.ktor.utils.io.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedReceiveChannelException
import kotlinx.coroutines.channels.ReceiveChannel
import kotlinx.coroutines.channels.SendChannel
import kotlinx.coroutines.launch
import java.io.IOException

internal const val CHANNEL_CAPACITY = 64
internal const val READ_LIMIT = 4096

//fun Socket.ircSplit(): Pair<ReceiveChannel<IRCMessage>, SendChannel<IRCMessage>> =
//    readChannel(this) to writeChannel(this)

internal fun readChannel(conn: ReadWriteSocket): ReceiveChannel<IRCMessage> {
    val channelIn = Channel<IRCMessage>(CHANNEL_CAPACITY)
    val reader = conn.openReadChannel()
    CoroutineScope(Dispatchers.IO).launch {
        while (true) {
            val line = try {
                reader.readUTF8Line(READ_LIMIT) ?: break
            } catch (e: IOException) {
                break
            }
            Log.d("IRC_CONN", "Received line: $line")
            val message = IRCMessage.tokenize(line) ?: continue
            if (!message.isValid()) continue
            channelIn.send(message)
        }
        channelIn.close()
        conn.close()
    }
    return channelIn
}

internal fun writeChannel(conn: ReadWriteSocket): SendChannel<IRCMessage> {
    val channelOut = Channel<IRCMessage>(CHANNEL_CAPACITY)
    val writer = conn.openWriteChannel(autoFlush = false)
    CoroutineScope(Dispatchers.IO).launch {
        var awake = false
        try {
            while (true) {
                val message = if (awake) {
                    channelOut.poll()
                } else {
                    channelOut.receive()
                }
                if (message == null) {
                    awake = false
                    writer.flush()
                    continue
                }
                awake = true
                // TODO set write deadline
                val s = "$message\r\n".toByteArray()
                writer.writeFully(s, 0, s.size)
                Log.d("IRC_CONN", "Sent $message")
            }
        } catch (e: ClosedReceiveChannelException) {
            // The user closed "out", stop the loop.
        } catch (e: ClosedWriteChannelException) {
            // The connection has closed, stop the loop.
        } catch (e: IOException) {
            Log.w("IRC_CONN", "Connection closed in conn.kt")
        }
        conn.close()
    }
    return channelOut
}