// 向内核偏好文件注入若干历史周期缓存，供真机验证周期选择器使用。
// 输入参数：源文件路径、输出文件路径。
import fs from "node:fs";

const [, , source, target] = process.argv;
if (!source || !target) {
    console.error("用法：node inject-periods.mjs <源文件> <输出文件>");
    process.exit(1);
}

const document = JSON.parse(fs.readFileSync(source, "utf8"));
document.string = document.string ?? {};
document.long = document.long ?? {};

const DAY = 86400;

// 构造一个最小周期快照：只有基础字段、阅读天数与逐日阅读时长。
function periodJson(base, total, days, dayCount) {
    const readTimes = {};
    for (let i = 0; i < dayCount; i += 1) {
        readTimes[String(base + i * DAY)] = Math.round(total / dayCount);
    }
    return {
        baseTime: base,
        totalReadTime: total,
        readDays: days,
        dayAverageReadTime: Math.round(total / days),
        readTimes,
    };
}

const weeks = [
    [1791734400, 3600, 2, 7],
    [1791129600, 7200, 3, 7],
    [1760889600, 5400, 2, 7],
];
const months = [
    [1788192000, 9000, 4, 30],
    [1785513600, 12000, 6, 31],
    [1759248000, 15000, 8, 31],
];

const now = Date.now();
for (const [base, total, days, dayCount] of weeks) {
    document.string[`d_weekly:${base}`] = JSON.stringify(periodJson(base, total, days, dayCount));
    document.long[`t_weekly:${base}`] = now;
}
for (const [base, total, days, dayCount] of months) {
    document.string[`d_monthly:${base}`] = JSON.stringify(periodJson(base, total, days, dayCount));
    document.long[`t_monthly:${base}`] = now;
}

fs.writeFileSync(target, JSON.stringify(document), "utf8");
console.log(`已写入 ${target}`);
