#!/usr/bin/env node
// 统计 PNG 指定区域内每行/每列的暗像素数，用于量测弹窗面板边界与行位置。
// 用法：node measure-lines.mjs <png> <top> <bottom> [minCount]
import { execFileSync } from 'node:child_process';

const [file, topArg, bottomArg, minArg] = process.argv.slice(2);
const top = Number(topArg);
const bottom = Number(bottomArg);
const minCount = Number(minArg ?? 200);
const raw = execFileSync(
  'ffmpeg',
  ['-loglevel', 'error', '-i', file, '-f', 'rawvideo', '-pix_fmt', 'gray', '-'],
  { maxBuffer: 64 * 1024 * 1024 },
);
const width = 480;
const stride = raw.length / 800;
const rowCounts = [];
for (let y = top; y < bottom; y += 1) {
  let dark = 0;
  for (let x = 0; x < width; x += 1) {
    if (raw[y * stride + x] < 140) dark += 1;
  }
  if (dark >= minCount) rowCounts.push(`row y=${y} dark=${dark}`);
}
console.log(rowCounts.join('\n'));
