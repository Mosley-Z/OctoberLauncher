package com.shuiyu.octoberrain

import com.shuiyu.octoberrain.core.common.WereadTodo
import com.shuiyu.octoberrain.core.common.WereadTodoHost
import com.shuiyu.octoberrain.core.wereadsync.WereadTodoStore
import java.io.File

/**
 * 待办宿主，把界面请求转给微读数据目录下的待办存储。
 *
 * 装配由应用启动时完成，未装配时所有操作返回空清单，界面据此显示空态。
 * 存储实现读写 `filesDir/weread/kernel_prefs.json`，与内核读取位置一致。
 */
object HostWereadTodos : WereadTodoHost {
    /** 微读数据目录名。 */
    private const val DIRECTORY = "weread"

    /** 待办存储，未装配时为空。 */
    private var store: WereadTodoStore? = null

    /**
     * 绑定数据目录，创建待办存储。
     *
     * @param filesDir 应用私有文件目录
     */
    fun attach(filesDir: File) {
        store = WereadTodoStore(File(filesDir, DIRECTORY))
    }

    override fun list(): List<WereadTodo> = store?.list().orEmpty()

    override fun add(
        content: String,
        dateSec: Long,
        minuteOfDay: Int,
    ): List<WereadTodo> = store?.add(content, dateSec, minuteOfDay).orEmpty()

    override fun toggle(id: Long): List<WereadTodo> = store?.toggle(id).orEmpty()

    override fun remove(id: Long): List<WereadTodo> = store?.remove(id).orEmpty()

    override fun move(
        id: Long,
        delta: Int,
    ): List<WereadTodo> = store?.move(id, delta).orEmpty()

    override fun update(
        id: Long,
        content: String,
        dateSec: Long,
        minuteOfDay: Int,
    ): List<WereadTodo> = store?.update(id, content, dateSec, minuteOfDay).orEmpty()
}
