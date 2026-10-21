"""Build the synthetic real-Android notes-cache probe; does not access devices."""

import argparse
import os
from pathlib import Path
import subprocess
import tempfile
import zipfile


ROOT = Path(__file__).resolve().parents[2]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--jdk", type=Path, default=os.environ.get("JAVA_HOME"))
    parser.add_argument("--sdk", type=Path, required=True)
    args = parser.parse_args()
    if not args.jdk:
        parser.error("--jdk or JAVA_HOME is required")
    extension = ".exe" if os.name == "nt" else ""
    java = args.jdk / "bin" / ("java" + extension)
    javac = args.jdk / "bin" / ("javac" + extension)
    android = args.sdk / "platforms/android-35/android.jar"
    d8 = args.sdk / "build-tools/35.0.0/lib/d8.jar"
    kernel = ROOT / "libs/weread-kernel-1.3.2-k6-host1.jar"
    work = ROOT / "_analysis"
    work.mkdir(exist_ok=True)
    source = Path(__file__).parent / "probe/KernelNotesCacheProbe.java"
    with tempfile.TemporaryDirectory(prefix="weread-probe-", dir=work) as temporary:
        classes = Path(temporary) / "classes"
        dex = Path(temporary) / "dex"
        classes.mkdir()
        dex.mkdir()
        subprocess.run([
            str(javac), "--release", "8", "-encoding", "UTF-8", "-g:none",
            "-classpath", os.pathsep.join([str(kernel), str(android)]),
            "-d", str(classes), str(source),
        ], check=True)
        subprocess.run([
            str(java), "-cp", str(d8), "com.android.tools.r8.D8",
            "--lib", str(android), "--min-api", "30", "--output", str(dex),
            str(kernel), *[str(compiled) for compiled in sorted(classes.glob("*.class"))],
        ], check=True)
        target = work / "weread-host1-probe.jar"
        with zipfile.ZipFile(target, "w", compression=zipfile.ZIP_DEFLATED) as archive:
            for compiled in sorted(dex.glob("*.dex")):
                archive.write(compiled, compiled.name)
    print(target)


if __name__ == "__main__":
    main()
