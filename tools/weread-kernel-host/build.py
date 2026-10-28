"""Compile the reviewed host classes and verify untouched kernel entries."""

import argparse
import copy
import hashlib
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[2]
BASE = ROOT / "libs/weread-kernel-1.3.2-k6.jar"
OUTPUT = ROOT / "libs/weread-kernel-1.3.2-k6-host1.jar"
CLASS = "com/inkread/weekread/kernel/desk/DeskData.class"
TYPE = "com.inkread.weekread.kernel.desk.DeskData"
CLASSES = [CLASS, "com/inkread/weekread/kernel/desk/DeskPage.class", "com/inkread/weekread/kernel/desk/BillSection.class"]
BASE_SHA256 = "0e8d30a95aa06039152d297879253ae1872ae9ac88beb1cc24553fba48c4ec3a"


def public_api(javap, archive, type_name=TYPE):
    result = subprocess.run(
        [str(javap), "-public", "-classpath", str(archive), type_name],
        check=True, capture_output=True, text=True,
    )
    return [line.strip() for line in result.stdout.splitlines()
            if line.strip().startswith("public ")]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--android-jar", type=Path, required=True)
    args = parser.parse_args()
    if not args.jdk:
        parser.error("--jdk or JAVA_HOME is required")
    javac = args.jdk / "bin" / ("javac.exe" if os.name == "nt" else "javac")
    javap = args.jdk / "bin" / ("javap.exe" if os.name == "nt" else "javap")
    assert hashlib.sha256(BASE.read_bytes()).hexdigest() == BASE_SHA256, "Unexpected k6 input"
    source = Path(__file__).parent / "src" / CLASS.replace(".class", ".java")
    work = ROOT / "_analysis"
    work.mkdir(exist_ok=True)
    with tempfile.TemporaryDirectory(prefix="weread-kernel-host-", dir=work) as temporary:
        classes = Path(temporary) / "classes"
        classes.mkdir()
        subprocess.run([
            str(javac), "--release", "8", "-encoding", "UTF-8", "-g:none",
            "-classpath", os.pathsep.join([str(BASE), str(args.android_jar)]),
            "-d", str(classes), *[str(Path(__file__).parent / "src" / name.replace(".class", ".java")) for name in CLASSES],
        ], check=True)
        compiled_names = sorted(p.relative_to(classes).as_posix() for p in classes.rglob("*.class"))
        assert all(name in CLASSES or name.startswith("com/inkread/weekread/kernel/desk/BillSection$") for name in compiled_names)
        patched = {name: (classes / name).read_bytes() for name in compiled_names}
        with zipfile.ZipFile(BASE) as original, zipfile.ZipFile(OUTPUT, "w") as result:
            for entry in original.infolist():
                result.writestr(copy.copy(entry), patched[entry.filename] if entry.filename in patched else original.read(entry.filename))
        with zipfile.ZipFile(BASE) as original, zipfile.ZipFile(OUTPUT) as result:
            assert original.namelist() == result.namelist(), "Entry list changed"
            changed = [name for name in original.namelist() if original.read(name) != result.read(name)]
            assert all(name in compiled_names for name in changed)
            assert all(name in changed for name in CLASSES), "Unexpected changed entries: " + repr(changed)
        for name in CLASSES:
            type_name = name.removesuffix(".class").replace("/", ".")
            assert public_api(javap, BASE, type_name) == public_api(javap, OUTPUT, type_name), "Public API changed: " + type_name
    digest = hashlib.sha256(OUTPUT.read_bytes()).hexdigest()
    OUTPUT.with_suffix(".sha256").write_text(f"{digest}  {OUTPUT.name}\n", encoding="ascii")
    print(f"Verified: only {CLASSES} differ; public APIs unchanged")
    print(f"{digest}  {OUTPUT.name}")


if __name__ == "__main__":
    main()
