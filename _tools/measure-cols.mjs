// 行带内列聚类：统计指定纵范围内的暗像素列分布，用于定位顶栏文本块的起止列。
// 参数：<gray文件> <y0> <y1> [阈值]
import fs from 'node:fs';

const W = 480;
const path = process.argv[2];
const y0 = Number(process.argv[3]);
const y1 = Number(process.argv[4]);
const thr = Number(process.argv[5] ?? 128);
const buf = fs.readFileSync(path);

const colDark = new Array(W).fill(0);
for (let y = y0; y <= y1; y++) {
  for (let x = 0; x < W; x++) if (buf[y * W + x] < thr) colDark[x]++;
}
const clusters = [];
let start = -1;
for (let x = 0; x < W; x++) {
  if (colDark[x] > 0 && start < 0) start = x;
  if ((colDark[x] === 0 || x === W - 1) && start >= 0) {
    clusters.push({ x0: start, x1: colDark[x] === 0 ? x - 1 : x });
    start = -1;
  }
}
// 合并间距小于 8px 的簇，视为同一文本块。
const merged = [];
for (const c of clusters) {
  const last = merged[merged.length - 1];
  if (last && c.x0 - last.x1 <= 8) last.x1 = c.x1;
  else merged.push({ ...c });
}
console.log(JSON.stringify({ path, y0, y1, merged }, null, 1));
