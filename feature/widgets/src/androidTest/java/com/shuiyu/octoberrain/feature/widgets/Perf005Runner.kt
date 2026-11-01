package com.shuiyu.octoberrain.feature.widgets

import android.app.Activity
import android.app.Application
import android.app.Instrumentation
import android.os.Bundle
import com.shuiyu.octoberrain.core.common.AppServices

class Perf005Runner : Instrumentation() {
    override fun onCreate(arguments: Bundle?) {
        super.onCreate(arguments)
        start()
    }

    override fun onStart() {
        val result = Bundle()
        var suite: Perf005Tests? = null
        var success = false
        try {
            val target = targetContext
            check(target.packageName == TEST_PACKAGE)
            check(target.applicationContext.packageName == TEST_PACKAGE)
            check(target.applicationContext.javaClass == Application::class.java)
            check(!AppServices.isReady())
            val root =
                target.filesDir
                    .canonicalFile
                    .parentFile!!
            check(root.name == TEST_PACKAGE)
            check(target.filesDir.canonicalPath.startsWith(root.canonicalPath + "/"))
            check(target.cacheDir.canonicalPath.startsWith(root.canonicalPath + "/"))
            suite = Perf005Tests(this, target)
            suite.run()
            success = true
        } catch (error: Throwable) {
            result.putString("errorclass", error.javaClass.simpleName)
        } finally {
            result.putInt("tests", suite?.passed ?: 0)
            result.putString("stage", suite?.stage ?: "isolation")
            finish(if (success) Activity.RESULT_OK else Activity.RESULT_CANCELED, result)
        }
    }

    companion object {
        const val TEST_PACKAGE = "com.shuiyu.octoberrain.perf005.test"
    }
}
