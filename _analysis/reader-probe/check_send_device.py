"""RD002 外部来源授权矩阵的设备驱动脚本。

在显式指定的设备上安装产品与探针 APK，把合成样本推入探针私有目录，然后逐用例
启动探针构造 Intent，并通过 uiautomator 层级、截图与 logcat 记录可观察结果。
脚本不修改产品代码，也不代替人工验收：退出码 0 表示全部期望命中，2 表示流程
完成但存在未命中（观察值写入 JSON），1 表示环境、安装或部署阶段失败。
"""
from __future__ import annotations

import argparse
import hashlib
import json
import re
import subprocess
import sys
import time
import xml.etree.ElementTree as ET
from pathlib import Path

ROOT = Path(__file__).resolve().parent
WS = ROOT.parents[1]
FIXTURES = ROOT / "fixtures"
MANIFEST = ROOT / "send-fixture-manifest.json"
ADB = Path("D:/DSHStation/.toolchain/android-sdk/platform-tools/adb.exe")
DEFAULT_SERIAL = "SA4B03CN2608081096X"
PRODUCT = "com.shuiyu.octoberrain"
PROBE = "com.shuiyu.octoberrain.readerprobe"
PROBE_ACTIVITY = f"{PROBE}/.ProbeActivity"
DUMP = "/sdcard/reader-send.xml"
LOG_TAGS = ["ReaderProbe:I", "ReaderFixture:I", "AndroidRuntime:E", "ReaderTiming:D"]

EXPECTATIONS = {
    "send_text": "SEND text/plain 且带读授权时进入阅读页，reader_title=plain，reader_status 含百分比",
    "view_epub": "VIEW application/epub+zip 时进入阅读页，reader_title=epubbin，状态显示章节或进度",
    "send_docxzip": "SEND application/zip（DOCX 内容）时出现“暂不支持此文档类型，可使用其他应用打开”类提示",
    "send_multi": "SEND_MULTIPLE 携带两个 URI 时不进入阅读页，并提示一次只能打开一个文件",
    "send_nogrant": "SEND 文本但不授予读权限时不进入阅读页，出现授权或选择提示",
    "missing": "SEND 白名单内但无实体文件的 missing.txt 时，阅读页状态含“源文件不存在”",
    "revoke": "persistable=false 且发送后立即撤销授权时，阅读页显示授权失效类提示",
}
LIMITATIONS = [
    "产品侧当前只在 ACTION_VIEW 接收文档；SEND 与 SEND_MULTIPLE 用例的结果取决于产品对 EXTRA_STREAM 的实现进度，未实现时脚本记录拒绝提示并把对应用例标为未通过。",
    "Toast 由 Toast.makeText 呈现且没有日志输出，采样窗口内可能错过文本；未观察到文本时以 not_entered 与提示集合共同判断，并在 case.notes 标注。",
    "DOCX 以 application/zip 发送时进入压缩包容器，脚本会尝试点入 word/document.xml 观察不支持提示；容器层级变动时该步骤可能停在首屏。",
    "产品当前没有“返回后重开同一 URI”的入口，revoke 用例只记录入口是否存在，auto_covered=false。",
    "持久授权在进程重启后仍可读需要重启产品进程并保留 URI 记录，本脚本未覆盖。",
    "真实第三方应用（文件管理器、微信）的分享路径未覆盖，本脚本只使用探针构造的显式组件 Intent。",
]


class SetupError(RuntimeError):
    """环境、安装或部署阶段失败，携带可诊断的上下文。"""


def sha256(path: Path) -> str:
    """返回文件摘要，用于报告中标识实际安装的 APK 与部署样本。"""
    return hashlib.sha256(path.read_bytes()).hexdigest()


def adb(*args: str, timeout: int = 90, check: bool = True) -> subprocess.CompletedProcess:
    """调用固定 adb 与序列号；check=True 时非零退出转换为带输出的 SetupError。"""
    command = [str(ADB), "-s", SERIAL, *args]
    try:
        result = subprocess.run(command, capture_output=True, check=False, timeout=timeout)
    except FileNotFoundError as error:
        raise SetupError(f"adb 不可执行: {ADB}") from error
    except subprocess.TimeoutExpired as error:
        raise SetupError(f"adb 超时: {' '.join(args)}") from error
    if check and result.returncode != 0:
        detail = (result.stderr or result.stdout).decode("utf-8", "replace").strip()
        raise SetupError(f"adb {' '.join(args)} 退出码 {result.returncode}: {detail[:400]}")
    return result


def dump(attempts: int = 3) -> ET.Element:
    """删除旧层级后重新导出并解析；失败时抛出带最后诊断的 RuntimeError。"""
    last = "no attempt"
    for _ in range(attempts):
        adb("shell", "rm", "-f", DUMP, check=False)
        result = adb("shell", "uiautomator", "dump", DUMP, timeout=60, check=False)
        if b"dumped to:" in result.stdout:
            raw = adb("exec-out", "cat", DUMP, check=False)
            try:
                return ET.fromstring(raw.stdout)
            except ET.ParseError as error:
                last = f"层级解析失败: {error}"
        else:
            text = (result.stdout + result.stderr).decode("utf-8", "replace").strip()
            last = text[:200] or f"退出码 {result.returncode}"
        time.sleep(0.25)
    raise RuntimeError(f"uiautomator 层级不可用: {last}")


def texts(root: ET.Element) -> set[str]:
    """收集层级中所有非空文本节点，供提示与失败文案匹配。"""
    return {node.attrib["text"] for node in root.iter("node") if node.attrib.get("text")}


def field(root: ET.Element, name: str) -> str:
    """取最后一个 reader_<name> 控件的文本，缺失时返回空串。"""
    values = [
        node.attrib.get("text", "")
        for node in root.iter("node")
        if node.attrib.get("resource-id", "").endswith(":id/reader_" + name)
    ]
    return values[-1] if values else ""


def enabled(root: ET.Element, name: str) -> bool:
    """判断 reader_<name> 控件是否处于可点击状态。"""
    values = [
        node.attrib.get("enabled")
        for node in root.iter("node")
        if node.attrib.get("resource-id", "").endswith(":id/reader_" + name)
    ]
    return values[-1] == "true" if values else False


def reset() -> None:
    """每个用例前停止产品与探针、清理旧层级并清空 logcat，避免跨用例残留。"""
    adb("shell", "am", "force-stop", PRODUCT, check=False)
    adb("shell", "am", "force-stop", PROBE, check=False)
    adb("shell", "rm", "-f", DUMP, check=False)
    adb("logcat", "-c", check=False)


def launch(**extras: object) -> None:
    """以显式组件启动探针；布尔参数转为 --ez，其余转为 --es。"""
    args = ["shell", "am", "start", "-n", PROBE_ACTIVITY]
    for key, value in extras.items():
        if isinstance(value, bool):
            args += ["--ez", key, "true" if value else "false"]
        else:
            args += ["--es", key, str(value)]
    adb(*args, timeout=60)


def probe_log() -> list[str]:
    """读取探针 JSON 日志行，供报告核对实际构造的 Intent 参数。"""
    result = adb("logcat", "-d", "-s", "ReaderProbe:I", check=False)
    lines = []
    for raw in result.stdout.decode("utf-8", "replace").splitlines():
        start = raw.find("{")
        if start >= 0 and '"fixture"' in raw and '"action"' in raw:
            lines.append(raw[start:])
    return lines


def observe(seconds: float, evidence: dict) -> dict:
    """在时间窗口内反复采样层级，记录是否进入阅读页、状态、提示文本与截图。"""
    started = time.time()
    state = {"entered": False, "title": "", "status": "", "next_enabled": False, "texts": set()}
    shot = False
    while time.time() - started < seconds:
        try:
            root = dump()
        except SetupError:
            raise
        except RuntimeError:
            time.sleep(0.3)
            continue
        state["texts"] |= texts(root)
        if not shot:
            try:
                target = OUT / f"s4-{evidence['case'].replace('_', '-')}.png"
                target.write_bytes(adb("exec-out", "screencap", "-p", check=False).stdout)
            except OSError:
                pass
            shot = True
        title = field(root, "title")
        if title:
            state["entered"] = True
            state["title"] = title
            state["status"] = field(root, "status") or state["status"]
            state["next_enabled"] = state["next_enabled"] or enabled(root, "next")
        time.sleep(0.25)
    state["texts"] = sorted(state["texts"])
    # 墨水屏刷新与产品初始化较慢：窗口结束后再取一次终态，避免把加载中的标题当作结果。
    time.sleep(1.0)
    try:
        final = dump()
    except RuntimeError:
        return state
    if field(final, "title"):
        state["entered"] = True
        state["title"] = field(final, "title")
        state["status"] = field(final, "status") or state["status"]
        state["next_enabled"] = state["next_enabled"] or enabled(final, "next")
        state["texts"] = sorted(set(state["texts"]) | texts(final))
    return state


def entry_log() -> dict:
    """读取产品外部入口日志，返回最近一次 forward 与 reject 记录。

    Toast 在 uiautomator 层级中通常不可见，拒绝结果以 DocumentEntry 日志补充观察。
    """
    raw = adb("logcat", "-d", "-s", "DocumentEntry:I", check=False).stdout.decode("utf-8", "replace")
    result = {"forward": None, "reject": None}
    for line in raw.splitlines():
        if "forward" in line:
            result["forward"] = line.strip()
        if "reject=" in line:
            result["reject"] = line.split("reject=", 1)[1].strip()
    return result


def hints(state: dict, *needles: str) -> list[str]:
    """从提示集合中挑选包含任一关键词的文本。"""
    return [text for text in state["texts"] if any(needle in text for needle in needles)]


def run_case(name: str, seconds: float, check, **extras: object) -> dict:
    """执行单个用例：复位置、启动探针、采样、截图与日志留存，返回结构化结果。"""
    result = {"case": name, "expected": EXPECTATIONS[name], "launch": extras}
    try:
        reset()
        launch(**extras)
        state = observe(seconds, result)
        state["entry"] = entry_log()
        result.update({key: value for key, value in state.items() if key not in ("texts", "entry")})
        result["entry"] = state["entry"]
        result["texts"] = state["texts"][:12]
        passed, note = check(state)
        result.update({"passed": passed, "notes": note, "auto_covered": name != "revoke"})
        result["probe_log"] = probe_log()
        try:
            (OUT / f"case-{name.replace('_', '-')}.xml").write_bytes(adb("exec-out", "cat", DUMP, check=False).stdout)
        except OSError:
            pass
    except Exception as error:  # 单用例失败不阻断其余用例，保留诊断信息
        result.update({"passed": False, "notes": f"{type(error).__name__}: {error}", "auto_covered": name != "revoke"})
    try:
        log = adb("logcat", "-d", "-s", *LOG_TAGS, check=False).stdout
    except SetupError:
        log = b""
    try:
        (OUT / f"case-{name.replace('_', '-')}-logcat.txt").write_bytes(log)
    except OSError:
        pass
    return result


def check_send_text(state: dict) -> tuple[bool, str]:
    if state["entered"] and state["title"] == "plain" and "%" in state["status"]:
        return True, "阅读页标题为 plain 且出现进度百分比"
    return False, f"未出现 plain 阅读页（进入={state['entered']}，标题={state['title']!r}，状态={state['status']!r}，提示={state['texts'][:4]}）"


def check_view_epub(state: dict) -> tuple[bool, str]:
    if state["entered"] and state["title"] == "epubbin" and re.search(r"第\d+/\d+章|%", state["status"]):
        return True, f"阅读页标题为 epubbin，状态={state['status']!r}"
    return False, f"未出现 epubbin 阅读页（进入={state['entered']}，标题={state['title']!r}，状态={state['status']!r}）"


def check_docxzip(state: dict) -> tuple[bool, str]:
    found = hints(state, "不支持", "其他应用")
    if "不支持" in state["status"] or "其他应用" in state["status"] or found:
        return True, f"观察到不支持提示: {found[:3] or [state['status']]}"
    return False, f"未观察到不支持提示（进入={state['entered']}，标题={state['title']!r}，状态={state['status']!r}，提示={state['texts'][:4]}）"


def check_multi(state: dict) -> tuple[bool, str]:
    reject = (state.get("entry") or {}).get("reject")
    found = hints(state, "只能打开一个", "一个文件")
    if not state["entered"] and (reject == "UNSUPPORTED" or found):
        return True, f"入口拒绝多文件（reject={reject}，提示={found[:3]}）"
    return False, f"多文件集合未被拒绝（进入={state['entered']}，reject={reject}，标题={state['title']!r}，提示={state['texts'][:4]}）"


def check_nogrant(state: dict) -> tuple[bool, str]:
    reject = (state.get("entry") or {}).get("reject")
    found = hints(state, "授权", "请选择", "无法读取")
    if not state["entered"] and (reject == "PERMISSION" or found):
        return True, f"入口拒绝缺少读取授权的投递（reject={reject}，提示={found[:3]}）"
    if state["entered"] and ("授权" in state["status"] or "无法读取" in state["status"]):
        return True, f"阅读页报告授权问题: {state['status']!r}"
    return False, f"未观察到授权拒绝（进入={state['entered']}，reject={reject}，状态={state['status']!r}，提示={state['texts'][:4]}）"


def check_missing(state: dict) -> tuple[bool, str]:
    if state["entered"] and state["title"] == "missing.txt" and "不存在" in state["status"]:
        return True, f"阅读页报告源文件不存在: {state['status']!r}"
    return False, f"未出现缺失提示（进入={state['entered']}，标题={state['title']!r}，状态={state['status']!r}）"


def check_revoke(state: dict) -> tuple[bool, str]:
    reject = (state.get("entry") or {}).get("reject")
    found = hints(state, "授权", "失效", "重新")
    if state["entered"] and ("授权" in state["status"] or "重新" in state["status"]):
        return True, f"阅读页报告授权失效: {state['status']!r}"
    if not state["entered"] and (reject == "PERMISSION" or found):
        return True, f"产品在读取前拒绝并提示: {found[:3]}（reject={reject}）"
    return False, f"撤销后仍完成读取或未出现授权提示（进入={state['entered']}，reject={reject}，状态={state['status']!r}）；系统在投递时已记录授权，撤销时机晚于记录"


def tap(node: ET.Element) -> None:
    """点击控件中心点；bounds 缺失时静默跳过，等待后续采样。"""
    bounds = list(map(int, re.findall(r"\d+", node.attrib.get("bounds", ""))))
    if len(bounds) == 4:
        adb("shell", "input", "tap", str((bounds[0] + bounds[2]) // 2), str((bounds[1] + bounds[3]) // 2), check=False)
        time.sleep(0.6)


def drill_docx() -> dict:
    """在压缩包容器内逐层点入 word/document.xml，观察是否出现不支持提示。"""
    steps: list[str] = []
    found: list[str] = []
    for _ in range(4):
        try:
            root = dump()
        except RuntimeError:
            break
        found = hints({"texts": sorted(texts(root))}, "不支持", "其他应用")
        if found:
            break
        target = None
        for node in root.iter("node"):
            label = node.attrib.get("text", "")
            if label in ("word", "word/", "document.xml") or label.endswith("/document.xml"):
                target = node
                break
        if target is None or target.attrib.get("text", "") in steps:
            break
        steps.append(target.attrib.get("text", ""))
        tap(target)
    try:
        (OUT / "s4-send-docxzip-inner.png").write_bytes(adb("exec-out", "screencap", "-p", check=False).stdout)
    except (OSError, SetupError):
        pass
    return {"steps": steps, "hints": found, "unsupported": bool(found)}


def reopen_entry(state: dict) -> list[str]:
    """在产品内返回一层后查找重开入口；不存在时返回空列表。"""
    if not state["entered"]:
        return []
    adb("shell", "input", "keyevent", "4", check=False)
    time.sleep(0.8)
    try:
        return hints({"texts": sorted(texts(dump()))}, "重新打开", "重试", "重新授权", "打开方式")
    except RuntimeError:
        return []


def deploy() -> tuple[list[dict], str]:
    """安装探针与产品、推送样本并核对清单；返回部署记录与设备目录列表。"""
    if not MANIFEST.is_file():
        raise SetupError(f"缺少清单 {MANIFEST}，先运行 generate_send_fixtures.py")
    manifest = json.loads(MANIFEST.read_text(encoding="utf-8"))
    names = manifest.get("deploy") or []
    for name in names:
        if not (FIXTURES / name).is_file():
            raise SetupError(f"样本缺失: {FIXTURES / name}")
    for apk, label in ((PROBE_APK, "探针"), (PRODUCT_APK, "产品")):
        if not apk.is_file():
            raise SetupError(f"{label} APK 不存在: {apk}")
    result = {}
    labels = {"probe": "探针", "product": "产品"}
    for apk, label in ((PROBE_APK, "probe"), (PRODUCT_APK, "product")):
        installed = adb("install", "-r", str(apk), timeout=180)
        output = (installed.stdout + installed.stderr).decode("utf-8", "replace").strip()
        if "Success" not in output:
            hint = ""
            if "INSTALL_FAILED_UPDATE_INCOMPATIBLE" in output:
                hint = "；签名冲突，需要人工确认后卸载旧包"
            raise SetupError(f"{labels[label]}安装失败: {output}{hint}")
        result[label] = "Success"
    records = []
    # 首次安装的探针尚未运行，files 目录可能不存在；先创建再复制样本。
    adb("shell", "run-as", PROBE, "mkdir", "-p", "files")
    for name in names:
        local = FIXTURES / name
        remote = f"/data/local/tmp/rd002-{name}"
        adb("push", str(local), remote, timeout=180)
        adb("shell", "run-as", PROBE, "cp", remote, f"files/{name}")
        adb("shell", "rm", "-f", remote, check=False)
        records.append({"name": name, "bytes": local.stat().st_size, "sha256": sha256(local)})
    adb("shell", "run-as", PROBE, "rm", "-f", "files/missing.txt", check=False)
    listing = adb("shell", "run-as", PROBE, "ls", "-l", "files").stdout.decode("utf-8", "replace")
    missing = [record["name"] for record in records if record["name"] not in listing]
    if missing:
        raise SetupError(f"部署后目录缺少: {', '.join(missing)}；目录内容: {listing.strip()[:400]}")
    if "missing.txt" in listing:
        raise SetupError("设备上的 missing.txt 仍然存在，缺失用例不成立")
    return records, listing


def main() -> int:
    """按顺序执行安装、部署与七个用例，写出 JSON 报告并返回验收退出码。"""
    global SERIAL, PRODUCT_APK, PROBE_APK, OUT
    parser = argparse.ArgumentParser(description="RD002 外部来源授权矩阵设备驱动")
    parser.add_argument("product_apk", nargs="?", default=str(WS / "app/build/outputs/apk/debug/app-debug.apk"))
    parser.add_argument("probe_apk", nargs="?", default=str(ROOT / "out/reader-probe.apk"))
    parser.add_argument("--serial", default=DEFAULT_SERIAL)
    parser.add_argument("--out", default=str(WS / "_analysis/verify/sources-002"))
    options = parser.parse_args()
    SERIAL = options.serial
    PRODUCT_APK = Path(options.product_apk)
    PROBE_APK = Path(options.probe_apk)
    OUT = Path(options.out)
    OUT.mkdir(parents=True, exist_ok=True)

    report: dict = {
        "script": "check_send_device.py",
        "serial": SERIAL,
        "adb": str(ADB),
        "expectations": EXPECTATIONS,
        "limitations": LIMITATIONS,
        "started_at": time.strftime("%Y-%m-%dT%H:%M:%S%z"),
    }
    try:
        if not ADB.is_file():
            raise SetupError(f"adb 不存在: {ADB}")
        for apk, label in ((PRODUCT_APK, "产品"), (PROBE_APK, "探针")):
            if not apk.is_file():
                raise SetupError(f"{label} APK 不存在: {apk}（产品包由集成者构建，探针包由 build.py 生成）")
        names = json.loads(MANIFEST.read_text(encoding="utf-8")).get("deploy", []) if MANIFEST.is_file() else []
        if not (names and all((FIXTURES / name).is_file() for name in names)):
            generated = subprocess.run(
                [sys.executable, str(ROOT / "generate_send_fixtures.py")],
                capture_output=True,
                timeout=120,
                check=False,
            )
            report["generator"] = {
                "exit": generated.returncode,
                "stdout": generated.stdout.decode("utf-8", "replace").strip()[-800:],
            }
            if generated.returncode != 0:
                detail = generated.stderr.decode("utf-8", "replace").strip()[:400]
                raise SetupError(f"样本生成失败，退出码 {generated.returncode}: {detail}")
        adb("get-state")
        report["adb_version"] = adb("version").stdout.decode("utf-8", "replace").strip()
        report["apks"] = {
            "product": {"path": str(PRODUCT_APK), "bytes": PRODUCT_APK.stat().st_size, "sha256": sha256(PRODUCT_APK)},
            "probe": {"path": str(PROBE_APK), "bytes": PROBE_APK.stat().st_size, "sha256": sha256(PROBE_APK)},
        }
        deployed, listing = deploy()
        report["installs"] = {"product": "Success", "probe": "Success"}
        report["deployed"] = deployed
        report["device_files"] = listing.strip().splitlines()
        cases = []
        cases.append(run_case("send_text", 10.0, check_send_text, sendFixture="plain", sendAction="SEND", sendMime="text/plain", uriPrefix="fixture", readGrant=True))
        cases.append(run_case("view_epub", 14.0, check_view_epub, sendFixture="epubbin", sendAction="VIEW", sendMime="application/epub+zip", uriPrefix="fixture", readGrant=True))
        docx_case = run_case("send_docxzip", 8.0, check_docxzip, sendFixture="docxzip", sendAction="SEND", sendMime="application/zip", uriPrefix="fixture", readGrant=True)
        if not docx_case.get("passed") and docx_case.get("entered"):
            inner = drill_docx()
            docx_case["archive_drill"] = inner
            docx_case["passed"] = inner["unsupported"]
            docx_case["notes"] += "；压缩包内层" + ("出现不支持提示" if inner["unsupported"] else f"未出现不支持提示（步骤={inner['steps']}）")
        cases.append(docx_case)
        cases.append(run_case("send_multi", 10.0, check_multi, sendFixture="multi-a.txt", sendAction="SEND_MULTIPLE", sendMime="text/plain", uriPrefix="fixture", readGrant=True))
        cases.append(run_case("send_nogrant", 10.0, check_nogrant, sendFixture="plain", sendAction="SEND", sendMime="text/plain", uriPrefix="fixture", readGrant=False))
        cases.append(run_case("missing", 10.0, check_missing, sendFixture="missing.txt", sendAction="SEND", sendMime="text/plain", uriPrefix="fixture", readGrant=True))
        revoke = run_case("revoke", 12.0, check_revoke, sendFixture="plain", sendAction="SEND", sendMime="text/plain", uriPrefix="fixture", readGrant=True, persistable=False, revokeAfter=True)
        revoke["reopen_entry"] = reopen_entry(revoke)
        revoke["notes"] += f"；产品内重开入口: {revoke['reopen_entry'] or '未找到'}"
        cases.append(revoke)
        adb("shell", "am", "force-stop", PRODUCT, check=False)
        report["cases"] = cases
        report["passed"] = sum(1 for case in cases if case.get("passed"))
        report["total"] = len(cases)
        report["not_covered"] = LIMITATIONS
        report["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        (OUT / "s4-send-flow.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({key: report[key] for key in ("serial", "passed", "total", "cases")}, ensure_ascii=False))
        return 0 if report["passed"] == report["total"] else 2
    except Exception as error:  # 环境或部署失败：保留诊断 JSON 后以非零退出
        report["error"] = f"{type(error).__name__}: {error}"
        report["finished_at"] = time.strftime("%Y-%m-%dT%H:%M:%S%z")
        (OUT / "s4-send-flow.json").write_text(json.dumps(report, ensure_ascii=False, indent=2) + "\n", encoding="utf-8")
        print(json.dumps({"error": report["error"]}, ensure_ascii=False))
        return 1


SERIAL = DEFAULT_SERIAL
PRODUCT_APK = Path()
PROBE_APK = Path()
OUT = Path()

if __name__ == "__main__":
    raise SystemExit(main())
