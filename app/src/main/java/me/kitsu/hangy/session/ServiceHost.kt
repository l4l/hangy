package me.kitsu.hangy.session

import android.content.Context
import android.content.Intent
import androidx.core.content.ContextCompat

/**
 * Starts the measurement foreground service; abstracted so the ViewModel stays unit-testable.
 * No `stop()`: [ScaleService] stops itself when [SessionController.isActive] goes false.
 */
fun interface ServiceHost {
    fun start()
}

class AndroidServiceHost(private val context: Context) : ServiceHost {
    override fun start() {
        ContextCompat.startForegroundService(context, Intent(context, ScaleService::class.java))
    }
}
