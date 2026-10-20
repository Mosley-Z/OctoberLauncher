#!/usr/bin/env node
// 从 PNM 灰度像素中找出暗色矩形边框的连通域边界，用于量测微读弹窗按钮几何。
// 用法：node measure-rects.mjs <png> <left> <top> <right> <bottom>；输出每个连通域的外接框与面积。
import { execFileSync } from 'node:child_process';

const [file, l, t, r, b] = process.argv.slice(2);
const left = Number(l);
const top = Number(t);
const right = Number(r);
const bottom = Number(b);
const width = right - left;
const height = bottom - top;
const raw = execFileSync(
  'ffmpeg',
  ['-loglevel', 'error', '-i', file, '-f', 'rawvideo', '-pix_fmt', 'gray', '-'],
  { maxBuffer: 64 * 1024 * 1024 },
);
const stride = raw.length / 800;
const dark = new Uint8Array(width * height);
for (let y = 0; y < height; y += 1) {
  for (let x = 0; x < width; x += 1) {
    const value = raw[(y + top) * stride + (x + left)];
    dark[y * width + x] = value < 140 ? 1 : 0;
  }
}
const seen = new Uint8Array(width * height);
const rects = [];
const dirs = [
  [1, 0],
  [-1, 0],
  [0, 1],
  [0, -1],
];
for (let y = 0; y < height; y += 1) {
  for (let x = 0; x < width; x += 1) {
    const start = y * width + x;
    if (!dark[start] || seen[start]) continue;
    const stack = [start];
    seen[start] = 1;
    let minX = x;
    let maxX = x;
    let minY = y;
    let maxY = y;
    let count = 0;
    while (stack.length) {
      const idx = stack.pop();
      const cy = Math.floor(idx / width);
      const cx = idx % width;
      count += 1;
      if (cx < minX) minX = cx;
      if (cx > maxX) maxX = cx;
      if (cy < minY) minY = cy;
      if (cy > maxY) maxY = cy;
      for (const [dx, dy] of dirs) {
        const nx = cx + dx;
        const ny = cy + dy;
        if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue;
        const nidx = ny * width + nx;
        if (dark[nidx] && !seen[nidx]) {
          seen[nidx] = 1;
          stack.push(nidx);
        }
      }
    }
    const w = maxX - minX + 1;
    const h = maxY - minY + 1;
    if (w >= 24 && h >= 16 && w <= 320 && h <= 120) {
      rects.push({ x: minX + left, y: minY + top, w, h, count });
    }
  }
}
rects.sort((a, b2) => a.y - b2.y || a.x - b2.x);
for (const item of rects) {
  console.log(`x=${item.x}..${item.x + item.w} y=${item.y}..${item.y + item.h} w=${item.w} h=${item.h}`);
}
