package com.tim.futureai

import android.app.Application
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** Process-lifetime scope lets persisted task steps finish or recover independently of a Compose screen. */
class FutureAiApplication : Application() {
    val taskScope: CoroutineScope by lazy { CoroutineScope(SupervisorJob() + Dispatchers.IO) }
}
