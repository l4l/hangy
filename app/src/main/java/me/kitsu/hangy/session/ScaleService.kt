package me.kitsu.hangy.session

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.getSystemService
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import me.kitsu.hangy.HangyApplication
import me.kitsu.hangy.MainActivity
import me.kitsu.hangy.R

/**
 * Holds the process at foreground importance while measuring: the Bluetooth stack clamps scan
 * clients below `IMPORTANCE_FOREGROUND_SERVICE` to `SCAN_MODE_LOW_POWER`, collapsing the reading
 * rate. The session itself lives in [SessionController], so this service needs no binder — it only
 * owns the notification and the foreground lifetime.
 */
class ScaleService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val controller: SessionController
        get() = (application as HangyApplication).container.sessionController

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            scope.launch {
                controller.finishForShutdown()
                stopSelf()
            }
            return START_NOT_STICKY
        }
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            buildNotification(),
            ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
        )
        // connect() flips isActive first, so the collector below cannot see an initial false.
        controller.connect()
        scope.launch {
            controller.isActive.first { !it }
            stopSelf()
        }
        // Resurrecting after process death would bring back a service with no session behind it.
        return START_NOT_STICKY
    }

    /** App swiped from Recents mid-session: save the partial session before the process goes away. */
    override fun onTaskRemoved(rootIntent: Intent?) {
        scope.launch {
            controller.finishForShutdown()
            stopSelf()
        }
    }

    override fun onDestroy() {
        controller.disconnect()
        scope.cancel()
        super.onDestroy()
    }

    private fun buildNotification(): Notification {
        createChannel()
        val openApp = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE,
        )
        val stop = PendingIntent.getService(
            this,
            1,
            Intent(this, ScaleService::class.java).setAction(ACTION_STOP),
            PendingIntent.FLAG_IMMUTABLE,
        )
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentTitle(getString(R.string.notification_measuring_title))
            .setContentText(getString(R.string.notification_measuring_text))
            .setContentIntent(openApp)
            .addAction(0, getString(R.string.notification_stop), stop)
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .build()
    }

    private fun createChannel() {
        // IMPORTANCE_LOW: silent. The app plays its own tone cues and must not beep twice.
        val channel = NotificationChannel(
            CHANNEL_ID,
            getString(R.string.notification_channel_measuring),
            NotificationManager.IMPORTANCE_LOW,
        )
        getSystemService<NotificationManager>()?.createNotificationChannel(channel)
    }

    private companion object {
        const val CHANNEL_ID = "measuring"
        const val NOTIFICATION_ID = 1
        const val ACTION_STOP = "me.kitsu.hangy.session.STOP"
    }
}
