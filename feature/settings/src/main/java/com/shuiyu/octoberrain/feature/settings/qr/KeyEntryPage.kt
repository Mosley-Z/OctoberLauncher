package com.shuiyu.octoberrain.feature.settings.qr

/**
 * 录入页面的 HTML 模板。
 *
 * 页面针对手机浏览器编写，采用深色样式与单列布局，
 * 表单通过页面地址中原样携带的一次性令牌提交回同一服务。
 */
internal object KeyEntryPage {
    /**
     * 密钥录入表单页。
     *
     * @param path 表单提交路径
     * @param token 一次性令牌
     * @param error 校验失败的说明，为空时展示正常提示
     * @param value 回填的密钥文本
     */
    fun form(
        path: String,
        token: String,
        error: String?,
        value: String,
    ): String {
        val tip =
            if (error == null) {
                "密钥仅在本次提交中经由局域网传输，不会上传到其它位置。"
            } else {
                "提交未通过：${escape(error)}"
            }
        val body =
            buildString {
                append("<p class=\"tip\">$tip</p>")
                append("<form method=\"post\" action=\"$path?t=$token\">")
                append("<label for=\"key\">接口密钥</label>")
                append("<input id=\"key\" name=\"key\" type=\"text\" value=\"${escape(value)}\"")
                append(" placeholder=\"wrk-…\" autocomplete=\"off\" autocapitalize=\"off\" spellcheck=\"false\">")
                append("<button type=\"submit\">提交密钥</button>")
                append("</form>")
            }
        return message("录入微信读书密钥", body)
    }

    /**
     * 通用提示页。
     *
     * @param title 页面标题，同时作为正文标题
     * @param body 正文 HTML 片段
     */
    fun message(
        title: String,
        body: String,
    ): String =
        buildString {
            append("<!DOCTYPE html><html lang=\"zh-CN\"><head><meta charset=\"utf-8\">")
            append("<meta name=\"viewport\" content=\"width=device-width,initial-scale=1\">")
            append("<title>${escape(title)}</title><style>")
            append("body{margin:0;padding:24px;background:#111;color:#eee;")
            append("font-family:system-ui,-apple-system,'Noto Sans SC',sans-serif;line-height:1.6}")
            append("h1{font-size:20px;font-weight:600;margin:0 0 12px}")
            append(".tip{color:#aaa;font-size:14px}")
            append("label{display:block;margin:20px 0 8px;font-size:14px;color:#bbb}")
            append("input{width:100%;box-sizing:border-box;padding:12px;border-radius:8px;")
            append("border:1px solid #333;background:#1c1c1c;color:#eee;font-size:16px}")
            append("button{margin-top:16px;width:100%;padding:12px;border:0;border-radius:8px;")
            append("background:#3a7afe;color:#fff;font-size:16px}")
            append("</style></head><body><h1>${escape(title)}</h1>$body</body></html>")
        }

    /** 转义 HTML 特殊字符，避免密钥文本破坏页面结构。 */
    private fun escape(text: String): String =
        text
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
}
