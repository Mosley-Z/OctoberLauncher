package com.shuiyu.octoberrain.feature.files.data

import android.content.Context
import android.content.SharedPreferences
import android.graphics.Bitmap
import android.graphics.Matrix
import com.shuiyu.octoberrain.feature.files.ui.Gray
import com.shuiyu.octoberrain.feature.files.ui.GrayParams

/** 撤销栈的默认深度，与规划文档一致。 */
const val EDIT_STACK_DEPTH = 20

/** 旋转计数偏好的键前缀，路径直接拼接在后。 */
private const val ROTATION_PREFIX = "edited_rotations:"

/**
 * 一次编辑操作。
 *
 * 操作按用户执行顺序保存，重放时先做几何变换，滤镜与对比度在末尾统一应用。
 * 裁剪矩形使用归一化坐标，旋转与镜像后仍可复用同一份操作序列。
 */
sealed interface EditOp {
    /**
     * 顺时针旋转。
     *
     * 参数 quarters 为旋转次数，负数按对 4 取模归一。
     */
    data class Rotate(
        val quarters: Int,
    ) : EditOp

    /** 水平镜像。 */
    data object Mirror : EditOp

    /**
     * 裁剪。
     *
     * 参数 rect 为相对当前方向的归一化矩形。
     */
    data class Crop(
        val rect: CropRect,
    ) : EditOp

    /**
     * 选择黑白滤镜档位。
     *
     * 参数 level 取 0 至 4，语义见 `GrayParams`。
     */
    data class Filter(
        val level: Int,
    ) : EditOp

    /**
     * 选择对比度档位。
     *
     * 参数 level 取 0 至 2，语义见 `GrayParams`。
     */
    data class Contrast(
        val level: Int,
    ) : EditOp
}

/**
 * 旋转计数的持久化接口。
 *
 * 编辑页在进入时读取原始旋转次数，退出时保存累计次数，使查看器与再次进入的编辑页
 * 看到同一方向。接口与 Android 解耦，本地 JVM 测试使用内存实现。
 */
interface RotationStore {
    /** 读取路径对应的累计旋转次数，缺失时返回 0。 */
    fun count(path: String): Int

    /** 保存路径对应的累计旋转次数。 */
    fun save(
        path: String,
        count: Int,
    )
}

/** 内存实现，供单元测试与无需持久化的场景使用。 */
class InMemoryRotationStore : RotationStore {
    private val counts = mutableMapOf<String, Int>()

    override fun count(path: String): Int = counts[path] ?: 0

    override fun save(
        path: String,
        count: Int,
    ) {
        counts[path] = normalizeTurns(count)
    }
}

/**
 * 基于模块偏好文件的旋转计数实现。
 *
 * 偏好文件名复用 `FilePrefs.FILE_NAME`，键为固定前缀加图片绝对路径。
 * 参数 context 为任意上下文，内部使用应用上下文访问偏好。
 */
class PrefsRotationStore(
    context: Context,
) : RotationStore {
    private val preferences: SharedPreferences =
        context.applicationContext.getSharedPreferences(FilePrefs.FILE_NAME, Context.MODE_PRIVATE)

    override fun count(path: String): Int =
        if (path.isEmpty()) 0 else normalizeTurns(preferences.getInt(ROTATION_PREFIX + path, 0))

    override fun save(
        path: String,
        count: Int,
    ) {
        if (path.isEmpty()) return
        preferences.edit().putInt(ROTATION_PREFIX + path, normalizeTurns(count)).apply()
    }
}

/**
 * 编辑会话：维护操作栈、撤销重做与旋转计数。
 *
 * 参数 sourcePath 为源图片绝对路径；rotationStore 为旋转计数存储；maxDepth 为操作栈深度，
 * 默认 20。新增操作清空重做栈；操作数达到深度上限后拒绝继续新增并返回 false。
 * 旋转计数在每次旋转、撤销与重做后写回存储，来源为进入会话时读取的值。
 */
class EditSession(
    val sourcePath: String,
    private val rotationStore: RotationStore = InMemoryRotationStore(),
    private val maxDepth: Int = EDIT_STACK_DEPTH,
) {
    private val operations = mutableListOf<EditOp>()
    private val undone = mutableListOf<EditOp>()
    private val initialRotation: Int = normalizeTurns(rotationStore.count(sourcePath))

    /** 当前累计旋转次数，范围 0 至 3。 */
    var rotationCount: Int = initialRotation
        private set

    /** 当前操作序列的只读快照。 */
    val operationList: List<EditOp> get() = operations.toList()

    /** 是否还有可撤销的操作。 */
    val canUndo: Boolean get() = operations.isNotEmpty()

    /** 是否还有可重做的操作。 */
    val canRedo: Boolean get() = undone.isNotEmpty()

    /** 当前操作数量。 */
    val size: Int get() = operations.size

    /**
     * 追回一个操作。
     *
     * 参数 op 为待追加的操作；裁剪矩形在入栈前夹取到 0 与 1 之间。
     * 栈已满时返回 false，其余情况返回 true。
     */
    fun push(op: EditOp): Boolean {
        if (operations.size >= maxDepth) return false
        val applied = if (op is EditOp.Crop) op.copy(rect = CropMath.clamp(op.rect)) else op
        operations.add(applied)
        undone.clear()
        adjustRotation(applied, forward = true)
        return true
    }

    /**
     * 撤销最后一个操作。
     *
     * 无操作可撤销时返回 null；成功时把操作移入重做栈并返回该操作。
     */
    fun undo(): EditOp? {
        val op = operations.removeLastOrNull() ?: return null
        undone.add(op)
        adjustRotation(op, forward = false)
        return op
    }

    /**
     * 重做最近撤销的操作。
     *
     * 无操作可重做时返回 null；成功时把操作移回操作栈并返回该操作。
     */
    fun redo(): EditOp? {
        val op = undone.removeLastOrNull() ?: return null
        operations.add(op)
        adjustRotation(op, forward = true)
        return op
    }

    /** 清空两个栈并恢复到进入会话时的旋转次数。 */
    fun reset() {
        operations.clear()
        undone.clear()
        rotationCount = initialRotation
        syncRotation()
    }

    /** 返回供导出使用的操作快照，调用方不得修改。 */
    fun exportOps(): List<EditOp> = operations.toList()

    /**
     * 在源位图上重放全部操作。
     *
     * 参数 source 为已按导出预算解码的位图，函数不回收源位图；
     * 返回新位图，中间位图在结束时回收。滤镜档位与对比度取最后选择的值。
     */
    fun applyTo(source: Bitmap): Bitmap = applyOps(source, operations)

    /** 按旋转操作调整累计次数并写回存储。 */
    private fun adjustRotation(
        op: EditOp,
        forward: Boolean,
    ) {
        if (op !is EditOp.Rotate) return
        val delta = normalizeTurns(op.quarters)
        rotationCount = normalizeTurns(rotationCount + if (forward) delta else -delta)
        syncRotation()
    }

    /** 保存当前旋转次数。 */
    private fun syncRotation() {
        rotationStore.save(sourcePath, rotationCount)
    }
}

/**
 * 重放操作序列。
 *
 * 参数 source 为源位图；ops 为操作列表。几何操作按顺序执行，滤镜与对比度
 * 取最后选择的值并在末尾统一应用。返回新位图，源位图与中间位图之外的结果由调用方负责回收。
 */
internal fun applyOps(
    source: Bitmap,
    ops: List<EditOp>,
): Bitmap {
    val created = mutableListOf<Bitmap>()
    var current = source
    var filterLevel = -1
    var contrast = 1
    for (op in ops) {
        when (op) {
            is EditOp.Rotate -> current = addTransform(created, transformBitmap(current, op.quarters))
            EditOp.Mirror -> current = addTransform(created, mirrorBitmap(current))
            is EditOp.Crop -> current = addTransform(created, cropBitmap(current, op.rect))
            is EditOp.Filter -> filterLevel = op.level
            is EditOp.Contrast -> contrast = op.level
        }
    }
    val result =
        if (filterLevel >= 0) {
            addTransform(created, filterBitmap(current, filterLevel, contrast))
        } else {
            current
        }
    created.filter { it !== result && it !== source }.forEach { it.recycle() }
    return result
}

/** 把新位图加入中间结果列表并返回该位图。 */
private fun addTransform(
    created: MutableList<Bitmap>,
    bitmap: Bitmap,
): Bitmap {
    if (bitmap !== created.lastOrNull() && created.none { it === bitmap }) created.add(bitmap)
    return bitmap
}

/** 按顺时针次数旋转位图，次数为 0 时返回原位图。 */
private fun transformBitmap(
    source: Bitmap,
    quarters: Int,
): Bitmap {
    val turns = normalizeTurns(quarters)
    if (turns == 0) return source
    val matrix = Matrix().apply { setRotate(90f * turns) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/** 水平镜像位图。 */
private fun mirrorBitmap(source: Bitmap): Bitmap {
    val matrix = Matrix().apply { setScale(-1f, 1f) }
    return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
}

/** 按归一化矩形裁剪位图；矩形覆盖整幅图片时返回原位图。 */
private fun cropBitmap(
    source: Bitmap,
    rect: CropRect,
): Bitmap {
    val safe = CropMath.clamp(rect)
    val coversAll = safe.left <= 0f && safe.top <= 0f && safe.right >= 1f && safe.bottom >= 1f
    val bounds = CropMath.pixels(safe, source.width, source.height)
    return when {
        coversAll -> source
        bounds.width <= 0 || bounds.height <= 0 -> source
        else -> Bitmap.createBitmap(source, bounds.left, bounds.top, bounds.width, bounds.height)
    }
}

/** 对位图像素执行黑白滤镜并生成新位图。 */
private fun filterBitmap(
    source: Bitmap,
    level: Int,
    contrast: Int,
): Bitmap {
    val pixels = IntArray(source.width * source.height)
    source.getPixels(pixels, 0, source.width, 0, 0, source.width, source.height)
    val filtered = Gray.apply(pixels, source.width, source.height, GrayParams(level, contrast, false))
    return Bitmap.createBitmap(filtered, source.width, source.height, Bitmap.Config.ARGB_8888)
}

/** 把任意整数归一为 0 至 3 的旋转次数。 */
internal fun normalizeTurns(value: Int): Int = ((value % 4) + 4) % 4
