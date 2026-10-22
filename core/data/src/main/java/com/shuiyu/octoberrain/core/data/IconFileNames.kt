package com.shuiyu.octoberrain.core.data

import java.security.MessageDigest

/**
 * 图标覆盖文件的命名规则。
 *
 * 文件名由目标标识的 SHA-1 前八位与安全化后的标识拼接而成：同一目标更换图片时
 * 得到一个确定的文件名，不同目标即使安全化后同名也不会互相覆盖。配置文件仍
 * 记录绝对路径，历史版本的纯安全名文件不受影响。
 */
object IconFileNames {
    /** 摘要前缀长度。 */
    private const val HASH_LENGTH = 8

    /** 扩展名。 */
    private const val EXTENSION = ".png"

    /**
     * 生成目标标识对应的文件名。
     *
     * 参数 targetId 为包名或 `special:` 标识；返回值形如 `1a2b3c4d_com.example.app.png`。
     */
    fun fileName(targetId: String): String = hashOf(targetId) + "_" + safeName(targetId) + EXTENSION

    /**
     * 把目标标识转换为只含字母、数字与点划线的片段。
     *
     * 参数 targetId 为包名或 `special:` 标识；冒号与斜线等字符替换为下划线。
     */
    fun safeName(targetId: String): String = targetId.replace(Regex("[^A-Za-z0-9._-]"), "_")

    /** 计算目标标识的 SHA-1 前八位。 */
    private fun hashOf(targetId: String): String {
        val digest = MessageDigest.getInstance("SHA-1").digest(targetId.toByteArray(Charsets.UTF_8))
        return digest.joinToString("") { byte -> "%02x".format(byte) }.take(HASH_LENGTH)
    }
}
