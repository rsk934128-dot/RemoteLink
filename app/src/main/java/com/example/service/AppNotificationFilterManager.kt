package com.example.service

import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.graphics.drawable.Drawable
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

data class AppNotificationItem(
    val packageName: String,
    val appName: String,
    val icon: Drawable? = null,
    val isPermitted: Boolean = true
)

object AppNotificationFilterManager {
    private const val PREFS_NAME = "notification_filter_prefs"
    private const val KEY_BLOCKED_PACKAGES = "blocked_packages"

    private var prefs: SharedPreferences? = null

    // Set of package names that are explicitly blocked by the user
    private val _blockedPackages = MutableStateFlow<Set<String>>(emptySet())
    val blockedPackages: StateFlow<Set<String>> = _blockedPackages.asStateFlow()

    fun init(context: Context) {
        if (prefs == null) {
            val sp = context.applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            prefs = sp
            _blockedPackages.value = sp.getStringSet(KEY_BLOCKED_PACKAGES, emptySet()) ?: emptySet()
        }
    }

    fun isAppPermitted(packageName: String): Boolean {
        return !_blockedPackages.value.contains(packageName)
    }

    fun setAppPermitted(packageName: String, permitted: Boolean) {
        val current = _blockedPackages.value.toMutableSet()
        if (permitted) {
            current.remove(packageName)
        } else {
            current.add(packageName)
        }
        _blockedPackages.value = current
        prefs?.edit()?.putStringSet(KEY_BLOCKED_PACKAGES, current)?.apply()
    }

    fun setAllPermitted(packageNames: List<String>, permitted: Boolean) {
        val current = _blockedPackages.value.toMutableSet()
        if (permitted) {
            current.removeAll(packageNames.toSet())
        } else {
            current.addAll(packageNames)
        }
        _blockedPackages.value = current
        prefs?.edit()?.putStringSet(KEY_BLOCKED_PACKAGES, current)?.apply()
    }

    fun getInstalledApps(context: Context): List<AppNotificationItem> {
        val pm = context.packageManager
        val mainIntent = Intent(Intent.ACTION_MAIN, null).apply {
            addCategory(Intent.CATEGORY_LAUNCHER)
        }
        val resolveInfos = pm.queryIntentActivities(mainIntent, 0)
        val list = mutableListOf<AppNotificationItem>()
        val seen = mutableSetOf<String>()

        for (info in resolveInfos) {
            val pkg = info.activityInfo.packageName
            if (pkg == context.packageName || seen.contains(pkg)) continue
            seen.add(pkg)
            val name = info.loadLabel(pm).toString()
            val icon = try {
                info.loadIcon(pm)
            } catch (_: Exception) {
                null
            }
            val permitted = isAppPermitted(pkg)
            list.add(AppNotificationItem(pkg, name, icon, permitted))
        }

        // Add standard messaging & productivity apps if not already discovered
        val defaults = listOf(
            Pair("com.whatsapp", "WhatsApp"),
            Pair("org.telegram.messenger", "Telegram"),
            Pair("com.google.android.gm", "Gmail"),
            Pair("com.google.android.apps.messaging", "Messages"),
            Pair("com.slack", "Slack"),
            Pair("com.discord", "Discord"),
            Pair("com.instagram.android", "Instagram"),
            Pair("com.facebook.katana", "Facebook"),
            Pair("com.microsoft.office.outlook", "Outlook"),
            Pair("org.thoughtcrime.securesms", "Signal"),
            Pair("com.google.android.dialer", "Phone & Calls"),
            Pair("com.google.android.calendar", "Google Calendar")
        )

        for ((pkg, name) in defaults) {
            if (!seen.contains(pkg)) {
                list.add(AppNotificationItem(pkg, name, null, isAppPermitted(pkg)))
                seen.add(pkg)
            }
        }

        return list.sortedBy { it.appName.lowercase() }
    }
}
