#!/usr/bin/env node
// 修改 kernel_prefs.json 的偏好值，供真机走查时快速切换导出样式。
// 参数：<输入文件> <输出文件> key=value [key=value ...]
// value 为 true/false 写入 bool，整数写入 int，其余写入 string；文件不存在时按空结构创建。
import fs from 'node:fs';

const [input, output, ...assignments] = process.argv.slice(2);
if (!input || !output) {
  console.error('用法: node kp-set.mjs <input> <output> key=value ...');
  process.exit(1);
}
const data = fs.existsSync(input)
  ? JSON.parse(fs.readFileSync(input, 'utf8'))
  : { string: {}, long: {}, int: {}, bool: {} };
for (const section of ['string', 'long', 'int', 'bool']) {
  if (!data[section]) data[section] = {};
}
for (const item of assignments) {
  const eq = item.indexOf('=');
  if (eq < 0) continue;
  const key = item.slice(0, eq);
  const raw = item.slice(eq + 1);
  for (const section of ['string', 'long', 'int', 'bool']) {
    delete data[section][key];
  }
  if (raw === 'true' || raw === 'false') {
    data.bool[key] = raw === 'true';
  } else if (/^-?\d+$/.test(raw)) {
    data.int[key] = Number.parseInt(raw, 10);
  } else {
    data.string[key] = raw;
  }
}
fs.writeFileSync(output, JSON.stringify(data));
console.log(`已更新 ${assignments.length} 项 → ${output}`);
