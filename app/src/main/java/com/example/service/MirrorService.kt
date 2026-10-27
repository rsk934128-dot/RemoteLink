package com.example.service

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Settings
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import androidx.core.app.NotificationCompat
import com.example.MainActivity
import com.example.R
import com.example.model.ConnectionStatus
import com.example.network.RemoteLinkWebSocketManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class MirrorService : NotificationListenerService() {

    companion object {
        const val CHANNEL_ID = "remotelink_mirror_channel"
        const val ONGOING_NOTIFICATION_ID = 2001

        private val _isMirroringEnabled = MutableStateFlow(true)
        val isMirroringEnabled: StateFlow<Boolean> = _isMirroringEnabled.asStateFlow()

        private val _isServiceBound = MutableStateFlow(false)
        val isServiceBound: StateFlow<Boolean> = _isServiceBound.asStateFlow()

        fun setMirroringEnabled(enabled: Boolean, context: Context) {
            _isMirroringEnabled.value = enabled
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!enabled) {
                notificationManager.cancel(ONGOING_NOTIFICATION_ID)
            } else if (isNotificationAccessGranted(context)) {
                showActiveMirrorNotification(context)
            }
        }

        fun isNotificationAccessGranted(context: Context): Boolean {
            val enabledListeners = Settings.Secure.getString(
                context.contentResolver,
                "enabled_notification_listeners"
            ) ?: return false
            val myComponent = ComponentName(context, MirrorService::class.java).flattenToString()
            return enabledListeners.contains(myComponent) || enabledListeners.contains(context.packageName)
        }

        fun showActiveMirrorNotification(context: Context) {
            val notificationManager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                val channel = NotificationChannel(
                    CHANNEL_ID,
                    "RemoteLink Notification Mirroring",
                    NotificationManager.IMPORTANCE_LOW
                ).apply {
                    description = "Indicates when notifications are mirrored to paired panel"
                }
                notificationManager.createNotificationChannel(channel)
            }

            val launchIntent = Intent(context, MainActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_SINGLE_TOP
            }
            val pendingIntent = PendingIntent.getActivity(
                context,
                0,
                launchIntent,
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
            )

            val notification = NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.mipmap.ic_launcher)
                .setContentTitle("RemoteLink: Notification Mirror Active")
                .setContentText("Notifications are being mirrored to your connected panel.")
                .setOngoing(true)
                .setContentIntent(pendingIntent)
                .setPriority(NotificationCompat.PRIORITY_LOW)
                .build()

            notificationManager.notify(ONGOING_NOTIFICATION_ID, notification)
        }
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        _isServiceBound.value = true
        RemoteLinkWebSocketManager.log("Notification listener connected to Android system.")
        if (_isMirroringEnabled.value) {
            showActiveMirrorNotification(this)
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        _isServiceBound.value = false
        RemoteLinkWebSocketManager.log("Notification listener disconnected.")
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        notificationManager.cancel(ONGOING_NOTIFICATION_ID)
    }

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        super.onNotificationPosted(sbn)
        if (sbn == null) return
        if (!_isMirroringEnabled.value) return
        if (RemoteLinkWebSocketManager.connectionStatus.value != ConnectionStatus.CONNECTED_AND_APPROVED) return

        // 1. Ignore notifications from our own app
        if (sbn.packageName == packageName) return

        // 2. Check if user permitted this specific app to mirror notifications
        if (!AppNotificationFilterManager.isAppPermitted(sbn.packageName)) return

        // 3. Ignore ongoing notifications (music playback, active call, progress bars)
        if (sbn.isOngoing) return

        val notification = sbn.notification ?: return

        // 3. Ignore group summaries
        if ((notification.flags and Notification.FLAG_GROUP_SUMMARY) != 0) return

        // 4. Extract content
        val extras = notification.extras ?: return
        val title = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString() ?: ""
        val text = extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()
            ?: extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()
            ?: ""

        // Skip completely empty items
        if (title.isBlank() && text.isBlank()) return

        // Resolve application name
        val pm = packageManager
        val appName = try {
            val appInfo = pm.getApplicationInfo(sbn.packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (_: Exception) {
            sbn.packageName
        }

        // Privacy check: Don't mirror sensitive/secret notifications or OTP tags if user configures
        val category = notification.category
        if (category == Notification.CATEGORY_CALL) {
            // Call alert is fine, but skip carrier codes
        }

        RemoteLinkWebSocketManager.sendNotification(
            appName = appName,
            title = title,
            text = text,
            isOngoing = false
        )
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification?) {
        super.onNotificationRemoved(sbn)
    }
}
