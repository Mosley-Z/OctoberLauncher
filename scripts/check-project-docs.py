"""检查仓库管理文档的本地链接、迁移映射与阅读器任务依赖；不调用构建/网络。"""

from pathlib import Path
import json
import re
import sys
from urllib.parse import unquote


ROOT = Path(__file__).resolve().parent.parent
# 历史评审针对当时源码，保留原报告；这里只豁免这两个已删除文件的历史引用。
HISTORICAL_MISSING = {
    (
        "docs/acceptance/202610210141来自CODEX的CODE REVIEW.md",
        "app/src/main/java/com/shuiyu/octoberrain/WereadGatewayProvider.kt",
    ),
    (
        "docs/acceptance/202610210141来自CODEX的CODE REVIEW.md",
        "feature/settings/src/main/java/com/shuiyu/octoberrain/feature/settings/qr/KeyEntryServer.kt",
    ),
}
STATES = {
    "TODO", "IN_PROGRESS", "READY_FOR_REVIEW", "VERIFIED", "DONE",
    "BLOCKED", "DEFERRED", "CANCELLED",
}


def main() -> int:
    errors: list[str] = []
    warnings: list[str] = []
    files = [ROOT / name for name in ("README.md", "PROJECT.md", "AGENTS.md")]
    for directory in ("docs", "task", "memory"):
        files.extend(sorted((ROOT / directory).rglob("*.md")))
    for path in files:
        relative = path.relative_to(ROOT).as_posix()
        text = path.read_text(encoding="utf-8-sig")
        # 当前管理文档的链接为普通链接或用<>包裹的含空格路径。
        for match in re.finditer(r"\]\((<[^>]+>|[^)\n]+)\)", text):
            target = unquote(match.group(1).strip("<>"))
            target = target.split("#", 1)[0]
            if not target or "://" in target or target.startswith("mailto:"):
                continue
            target = re.sub(r":\d+$", "", target)
            if re.match(r"^/[A-Za-z]:/", target):
                target = target[1:]
            # 历史评审的本仓库绝对路径，在不同checkout仍按仓库相对路径校验。
            original_prefix = "D:/DSHStation/OctoberLauncher/"
            if target.startswith(original_prefix):
                resolved = ROOT / target[len(original_prefix):]
            else:
                resolved = (path.parent / target).resolve()
            if resolved.exists():
                continue
            try:
                target_relative = resolved.relative_to(ROOT).as_posix()
            except ValueError:
                target_relative = str(resolved)
            if (relative, target_relative) in HISTORICAL_MISSING:
                warnings.append(f"historical source removed: {relative} -> {target_relative}")
            else:
                errors.append(f"missing link: {relative} -> {target}")

    manifest = json.loads(
        (ROOT / "docs/migration-2026-10-22.json").read_text(encoding="utf-8-sig")
    )
    destinations: set[str] = set()
    for entry in manifest:
        old, new = entry["Old"], entry["New"]
        if new in destinations or not (ROOT / new).is_file() or (ROOT / old).exists():
            errors.append(f"invalid migration: {old} -> {new}")
        destinations.add(new)

    cards = sorted((ROOT / "task").glob("TASK-RD-*.md"))
    graph: dict[str, set[str]] = {}
    index = (ROOT / "task/README.md").read_text(encoding="utf-8")
    for path in cards:
        text = path.read_text(encoding="utf-8")
        task_id = re.match(r"TASK-RD-(\d{3})-", path.name)
        if not task_id:
            errors.append(f"invalid task name: {path.name}")
            continue
        key = task_id.group(1)
        if key in graph:
            errors.append(f"duplicate task id: {key}")
        if path.name not in index:
            errors.append(f"task absent from index: {path.name}")
        state = re.search(r"^- 状态：([A-Z_]+)", text, re.MULTILINE)
        if not state or state.group(1) not in STATES:
            errors.append(f"invalid task state: {path.name}")
        dependency = re.search(r"^- 依赖：(.*)$", text, re.MULTILINE)
        if not dependency:
            errors.append(f"missing dependency field: {path.name}")
        graph[key] = set(re.findall(r"TASK-RD-(\d{3})-", dependency.group(1) if dependency else ""))
        for field in ("执行者/集成者", "允许修改", "READY条件", "验证命令及结果/证据", "下一步"):
            if f"- {field}：" not in text:
                errors.append(f"missing handoff field {field}: {path.name}")

    visited: set[str] = set()
    visiting: set[str] = set()

    def visit(key: str) -> None:
        if key in visiting:
            errors.append(f"dependency cycle at {key}")
            return
        if key in visited:
            return
        visiting.add(key)
        for dependency in graph[key]:
            if dependency not in graph:
                errors.append(f"unknown dependency: {key} -> {dependency}")
            else:
                visit(dependency)
        visiting.remove(key)
        visited.add(key)

    for key in graph:
        visit(key)
    for warning in warnings:
        print("WARN", warning)
    for error in errors:
        print("ERROR", error)
    print(f"documents={len(files)} migrations={len(manifest)} tasks={len(cards)} warnings={len(warnings)} errors={len(errors)}")
    return 1 if errors else 0


if __name__ == "__main__":
    sys.exit(main())
