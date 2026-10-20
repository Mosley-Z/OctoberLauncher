// 生成关闭负一屏的 shared_prefs 配置：对所有 launcher_config 字符串修改 desktop.negativePage.enabled。
import fs from 'node:fs'

const src = process.argv[2]
const dst = process.argv[3]
const text = fs.readFileSync(src, 'utf8')

/** 还原 XML 实体。 */
function unescapeXml(value) {
  return value
    .replaceAll('&quot;', '"')
    .replaceAll('&lt;', '<')
    .replaceAll('&gt;', '>')
    .replaceAll('&#39;', "'")
    .replaceAll('&amp;', '&')
}

/** 转义为 XML 文本内容。 */
function escapeXml(value) {
  return value
    .replaceAll('&', '&amp;')
    .replaceAll('<', '&lt;')
    .replaceAll('>', '&gt;')
    .replaceAll("'", '&#39;')
    .replaceAll('"', '&quot;')
}

let count = 0
const result = text.replace(
  /<string name="launcher_config">([\s\S]*?)<\/string>/g,
  (whole, body) => {
    const data = JSON.parse(unescapeXml(body))
    if (data?.desktop?.negativePage) {
      data.desktop.negativePage.enabled = false
      count += 1
    }
    return `<string name="launcher_config">${escapeXml(JSON.stringify(data))}</string>`
  },
)

fs.writeFileSync(dst, result)
console.log(`修改 launcher_config 条目 ${count} 处`)
