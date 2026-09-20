package xyz.saltedchips.bookyplayer.player

import android.content.Context
import android.content.Intent
import java.security.MessageDigest
import java.util.UUID

internal object TrustedMediaClients {
    internal val packages = setOf(
        "android",
        "com.android.bluetooth",
        "com.android.car",
        "com.android.car.media",
        "com.android.systemui",
        "com.google.android.apps.automotive.templates.host",
        "com.google.android.apps.googleassistant",
        "com.google.android.autosimulator",
        "com.google.android.carassistant",
        "com.google.android.embedded.projection",
        "com.google.android.gms",
        "com.google.android.gms.car",
        "com.google.android.googlequicksearchbox",
        "com.google.android.projection.gearhead",
        "com.google.android.wearable.app",
    )

    fun allows(packageName: String, appPackage: String): Boolean {
        if (packageName == appPackage) return true
        return packageName in packages
    }
}

internal object WidgetCommandAuth {
    const val EXTRA_TOKEN = "xyz.saltedchips.bookyplayer.extra.WIDGET_TOKEN"
    private const val KEY_TOKEN = "widget_command_token"

    fun token(context: Context): String {
        val prefs = context.applicationContext.getSharedPreferences(AutoLibrary.PREFS, Context.MODE_PRIVATE)
        prefs.getString(KEY_TOKEN, null)?.takeIf { it.isNotBlank() }?.let { return it }
        val created = UUID.randomUUID().toString()
        prefs.edit().putString(KEY_TOKEN, created).apply()
        return created
    }

    fun matches(context: Context, intent: Intent?): Boolean {
        val expected = context.applicationContext
            .getSharedPreferences(AutoLibrary.PREFS, Context.MODE_PRIVATE)
            .getString(KEY_TOKEN, null)
            ?: return false
        val provided = intent?.getStringExtra(EXTRA_TOKEN) ?: return false
        val expectedBytes = expected.toByteArray(Charsets.UTF_8)
        val providedBytes = provided.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(expectedBytes, providedBytes)
    }
}
