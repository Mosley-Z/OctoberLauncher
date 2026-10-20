package com.shuiyu.octoberrain.feature.wereadapp.ui

import android.content.Context
import android.os.Environment
import android.text.InputType
import com.shuiyu.octoberrain.core.design.InkWidgets
import com.shuiyu.octoberrain.feature.wereadapp.WereadAppPrefs
import java.io.File
import java.security.MessageDigest
import java.security.SecureRandom

/**
 * 锁屏密码段。
 *
 * 补齐启用锁屏、四位数字密码、锁屏背景三组控件与状态行；
 * 偏好与微读墨记一致写入 `kernel_prefs.json`，密码只保存随机盐与摘要，不存明文。
 *
 * @param context 创建视图的上下文
 */
@Suppress("TooManyFunctions")
internal class WereadLabLockPage(
    context: Context,
) : LabPageView(context) {
    override fun render() {
        addIntro()
        addEnableRow()
        addPinSection()
        addBackgroundSection()
        addAccessibilityTip()
    }

    /** 简介文案。 */
    private fun addIntro() {
        content.addView(labBody(context, "锁屏为应用级软锁：开启后进入锁屏密码界面，需输入四位数字解锁。"))
        content.addView(labSpacer(context, 0.5f))
        content.addView(labCaption(context, "忘记密码时可清除应用数据恢复，桌面与阅读数据会一并清除。"))
    }

    /** 启用锁屏复选行。 */
    private fun addEnableRow() {
        val store = WereadAppPrefs.store(context)
        val enabled = store.getBool(KEY_LOCK_ENABLED, false)
        content.addView(labSpacer(context))
        content.addView(
            labCheckRow(context, "启用锁屏（默认关）", enabled) { checked ->
                store.putBool(KEY_LOCK_ENABLED, checked)
                store.flush()
                refresh()
            },
        )
    }

    /** 密码分组：输入、保存与状态行。 */
    private fun addPinSection() {
        val store = WereadAppPrefs.store(context)
        val hasPin = store.getString(KEY_LOCK_PIN_HASH, "").isNotEmpty()
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "锁屏密码"))
        val (row, field) =
            labFieldRow(
                context,
                "四位数字密码",
                "",
                "输入四位数字",
                InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_VARIATION_PASSWORD,
            )
        content.addView(row)
        content.addView(labSpacer(context, 0.5f))
        content.addView(
            labButtonRow(
                context,
                InkWidgets.button(context, "保存密码") { savePin(field.text.toString()) },
                InkWidgets.button(context, "清除密码") { clearPin() },
            ),
        )
        content.addView(labSpacer(context, 0.5f))
        content.addView(InkWidgets.labelRow(context, "状态", if (hasPin) "已设置密码" else "未设置密码"))
    }

    /** 背景分组：路径输入、载入与恢复默认。 */
    private fun addBackgroundSection() {
        val store = WereadAppPrefs.store(context)
        val current = store.getString(KEY_LOCK_BG_PATH, "")
        content.addView(labSpacer(context, 2f))
        content.addView(labSectionTitle(context, "锁屏背景"))
        val (row, field) =
            labFieldRow(
                context,
                "背景图路径",
                current,
                DEFAULT_BG_REL,
                InputType.TYPE_CLASS_TEXT,
            )
        content.addView(row)
        content.addView(labSpacer(context, 0.5f))
        content.addView(
            labButtonRow(
                context,
                InkWidgets.button(context, "载入背景图") { loadBackground(field.text.toString()) },
                InkWidgets.button(context, "恢复默认") {
                    store.putString(KEY_LOCK_BG_PATH, "")
                    store.flush()
                    labToast(context, "已恢复默认背景")
                    refresh()
                },
            ),
        )
        content.addView(labSpacer(context, 0.5f))
        content.addView(
            InkWidgets.labelRow(context, "背景状态", if (current.isEmpty()) "默认浅色网点" else current),
        )
        content.addView(labCaption(context, "图片放在 /sdcard/Pictures/ 下可直接填写文件名，也可填写完整路径。"))
    }

    /** 无障碍依赖提示。 */
    private fun addAccessibilityTip() {
        content.addView(labSpacer(context, 2f))
        content.addView(labCaption(context, "锁屏界面依赖系统覆盖窗口能力；部分机型需在权限页允许显示在其他应用上层。"))
    }

    /** 保存四位数字密码，仅写入随机盐与摘要。 */
    private fun savePin(pin: String) {
        if (!PIN_PATTERN.matches(pin)) {
            labToast(context, "请输入四位数字密码")
            return
        }
        val store = WereadAppPrefs.store(context)
        val salt = ByteArray(SALT_LENGTH).also { SecureRandom().nextBytes(it) }
        store.putString(KEY_LOCK_PIN_SALT, salt.toHex())
        store.putString(KEY_LOCK_PIN_HASH, digest(salt, pin))
        store.flush()
        labToast(context, "密码已保存")
        refresh()
    }

    /** 清除已保存的密码。 */
    private fun clearPin() {
        val store = WereadAppPrefs.store(context)
        store.putString(KEY_LOCK_PIN_SALT, "")
        store.putString(KEY_LOCK_PIN_HASH, "")
        store.flush()
        labToast(context, "密码已清除")
        refresh()
    }

    /** 校验背景图存在后写入路径。 */
    private fun loadBackground(raw: String) {
        val text = raw.trim()
        if (text.isEmpty()) {
            labToast(context, "请先填写背景图路径")
            return
        }
        val file = resolveBackground(text)
        if (!file.isFile) {
            labToast(context, "未找到背景图：${file.absolutePath}")
            return
        }
        val store = WereadAppPrefs.store(context)
        store.putString(KEY_LOCK_BG_PATH, file.absolutePath)
        store.flush()
        labToast(context, "背景图已载入")
        refresh()
    }

    /** 把相对路径解析到外部存储根目录下的 Pictures 目录。 */
    private fun resolveBackground(path: String): File {
        val file = File(path)
        if (file.isAbsolute) return file
        return File(Environment.getExternalStorageDirectory(), "Pictures/$path")
    }

    /** 计算 SHA-256(盐 ‖ 密码) 的十六进制摘要。 */
    private fun digest(
        salt: ByteArray,
        pin: String,
    ): String {
        val md = MessageDigest.getInstance("SHA-256")
        md.update(salt)
        md.update(pin.toByteArray(Charsets.UTF_8))
        return md.digest().toHex()
    }

    /** 字节数组转十六进制字符串。 */
    private fun ByteArray.toHex(): String = joinToString("") { "%02x".format(it) }

    private companion object {
        /** 锁屏启用偏好键。 */
        const val KEY_LOCK_ENABLED: String = "lock_enabled"

        /** 密码盐偏好键。 */
        const val KEY_LOCK_PIN_SALT: String = "lock_pin_salt"

        /** 密码摘要偏好键。 */
        const val KEY_LOCK_PIN_HASH: String = "lock_pin_hash"

        /** 锁屏背景路径偏好键。 */
        const val KEY_LOCK_BG_PATH: String = "lock_bg_path"

        /** 四位数字密码的匹配规则。 */
        val PIN_PATTERN: Regex = Regex("^\\d{4}$")

        /** 随机盐长度，单位字节。 */
        const val SALT_LENGTH: Int = 8

        /** 默认背景图相对路径提示。 */
        const val DEFAULT_BG_REL: String = "背景.jpg"
    }
}
