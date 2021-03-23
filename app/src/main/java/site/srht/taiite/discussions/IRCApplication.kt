package site.srht.taiite.discussions

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.os.Build

const val IRC_SERVICE_CHANNEL = "irc-service"

class IRCApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        this.createNotificationChannels()
    }

    private fun createNotificationChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) {
            return
        }
        val notificationManager =
            this.getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        val ircServiceChannel = NotificationChannel(
            IRC_SERVICE_CHANNEL,
            "Connected status (you may disable this)",
            NotificationManager.IMPORTANCE_LOW
        )
        notificationManager.createNotificationChannel(ircServiceChannel)
    }
}
