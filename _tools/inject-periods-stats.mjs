// 向统计缓存文件注入若干历史周期快照，供真机验证周期选择器使用。
// 输入参数：源 stats.json 路径、输出路径。
// 统计缓存是扁平对象，键为 d_<mode>:<baseTimeSec>，值为该期原始统计 JSON 字符串。
import fs from "node:fs";

const [, , source, target] = process.argv;
if (!source || !target) {
    console.error("用法：node inject-periods-stats.mjs <源文件> <输出文件>");
    process.exit(1);
}

const document = JSON.parse(fs.readFileSync(source, "utf8"));
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

for (const [base, total, days, dayCount] of weeks) {
    document[`d_weekly:${base}`] = JSON.stringify(periodJson(base, total, days, dayCount));
}
for (const [base, total, days, dayCount] of months) {
    document[`d_monthly:${base}`] = JSON.stringify(periodJson(base, total, days, dayCount));
}

fs.writeFileSync(target, JSON.stringify(document), "utf8");
console.log(`已写入 ${target}`);
