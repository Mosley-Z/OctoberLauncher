package com.shuiyu.octoberrain.feature.tray

import android.content.Context
import android.view.View
import com.shuiyu.octoberrain.core.common.AppServices
import com.shuiyu.octoberrain.core.common.TrayController
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.lang.ref.WeakReference

/** Existing status-bar factory; the retired pull-down panel has no controller or windows. */
class TrayControllerImpl : TrayController {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val statusBars = mutableListOf<WeakReference<StatusBarView>>()

    init {
        scope.launch { AppServices.system.battery.collect { refreshStatusBars() } }
        scope.launch { AppServices.config.state.collect { refreshStatusBars() } }
    }

    override fun createStatusBar(context: Context): View =
        StatusBarView(context).also {
            statusBars.add(WeakReference(it))
        }

    private fun refreshStatusBars() {
        statusBars.removeAll { it.get() == null }
        statusBars.mapNotNull { it.get() }.forEach { it.refresh() }
    }
}
