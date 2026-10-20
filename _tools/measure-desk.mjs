// 墨台像素量测脚本：读取 ffmpeg 生成的灰度原始像素（480x800，每字节一个像素），
// 统计水平分隔线行、文本行范围与左侧内边距，用于与微读内核渲染基准做像素级对比。
// 参数：<gray文件> [起始行] [结束行]
import fs from 'node:fs';

const W = 480;
const H = 800;
const path = process.argv[2];
const from = Number(process.argv[3] ?? 0);
const to = Number(process.argv[4] ?? H);
const buf = fs.readFileSync(path);
if (buf.length !== W * H) {
  console.error('unexpected size', buf.length);
  process.exit(1);
}

const isDark = (p) => p < 128;

// 每行暗像素数量，用于定位横线与文本行。
const rowDark = new Array(H).fill(0);
for (let y = 0; y < H; y++) {
  let n = 0;
  for (let x = 0; x < W; x++) if (isDark(buf[y * W + x])) n++;
  rowDark[y] = n;
}

// 连续浅灰像素超过 60% 宽度的行视为横线（内核分隔线为浅灰 #D8D8D8）。
const lightRow = new Array(H).fill(0);
for (let y = 0; y < H; y++) {
  let n = 0;
  for (let x = 0; x < W; x++) if (buf[y * W + x] < 240) n++;
  lightRow[y] = n;
}
const lines = [];
for (let y = 0; y < H; y++) if (lightRow[y] > W * 0.6) lines.push(y);
const lineBands = [];
for (const y of lines) {
  const last = lineBands[lineBands.length - 1];
  if (last && y === last.end + 1) last.end = y;
  else lineBands.push({ start: y, end: y });
}

// 文本行：暗像素在 3..40% 宽之间，按连续行分组。
const textRows = [];
for (let y = from; y < to; y++) {
  const n = rowDark[y];
  if (n > 2 && n < W * 0.4) textRows.push(y);
}
const textBands = [];
for (const y of textRows) {
  const last = textBands[textBands.length - 1];
  if (last && y === last.end + 1) last.end = y;
  else textBands.push({ start: y, end: y });
}

// 每个文本带的左右极值与高度。
const bands = textBands.map((b) => {
  let minX = W;
  let maxX = -1;
  for (let y = b.start; y <= b.end; y++) {
    for (let x = 0; x < W; x++) {
      if (isDark(buf[y * W + x])) {
        if (x < minX) minX = x;
        if (x > maxX) maxX = x;
      }
    }
  }
  return { y0: b.start, y1: b.end, h: b.end - b.start + 1, x0: minX, x1: maxX };
});

console.log(JSON.stringify({ path, lines: lineBands, bands: bands.slice(0, 40) }, null, 1));
