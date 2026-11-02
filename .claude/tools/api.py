"""Looks up the exact Minecraft, Fabric and mod versions this build compiles against.

  python .claude/tools/api.py classes RenderTypes
  python .claude/tools/api.py grep "submitCustomGeometry\\(" --only minecraft
  python .claude/tools/api.py show net.minecraft.client.renderer.rendertype.RenderTypes --lines 1-80
  python .claude/tools/api.py sig net.minecraft.client.Options --match hud
  python .claude/tools/api.py status

--only filters jars by name. Search hits are source lines, or class names for jars without sources.
"""
import argparse
import os
import pathlib
import re
import subprocess
import sys
import zipfile

ROOT = pathlib.Path(__file__).resolve().parents[2]
CLASSPATH_FILE = ROOT / "build" / "api-classpath.txt"
JAVAP = pathlib.Path.home() / ".jdks" / "openjdk-25.0.2" / "bin" / "javap"


def classpath():
    stale = not CLASSPATH_FILE.exists() or any(
        (ROOT / name).stat().st_mtime > CLASSPATH_FILE.stat().st_mtime
        for name in ("build.gradle", "gradle.properties", "src/main/resources/voidheart.accesswidener"))
    if stale:
        gradlew = ROOT / ("gradlew.bat" if os.name == "nt" else "gradlew")
        subprocess.run([str(gradlew), "writeApiClasspath", "-q"], cwd=ROOT, check=True, stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL)
    jars = [pathlib.Path(line) for line in CLASSPATH_FILE.read_text().splitlines()]
    return [jar for jar in jars if jar.suffix == ".jar" and jar.exists()]


def sources_of(jar):
    """The sources jar beside this one, or in a sibling hash folder of the Gradle cache."""
    name = jar.stem + "-sources.jar"
    for candidate in [jar.with_name(name), *jar.parent.parent.glob("*/" + name)]:
        if candidate.exists():
            return candidate
    return None


def selected(only):
    jars = classpath()
    return [jar for jar in jars if not only or only.lower() in jar.name.lower()]


def class_entries(archive):
    for name in archive.namelist():
        if (name.endswith(".java") or name.endswith(".class")) and "$" not in name:
            yield name


def to_class_name(entry):
    return entry.rsplit(".", 1)[0].replace("/", ".")


def cmd_classes(args):
    pattern = re.compile(args.pattern, re.IGNORECASE)
    found = 0
    for jar in selected(args.only):
        with zipfile.ZipFile(jar) as archive:
            for entry in class_entries(archive):
                if pattern.search(entry.rsplit("/", 1)[-1].rsplit(".", 1)[0]):
                    print(f"{to_class_name(entry)}  [{jar.stem}]")
                    found += 1
                    if found >= args.max:
                        print(f"... stopped at {args.max}")
                        return


def cmd_grep(args):
    pattern = re.compile(args.pattern)
    found = 0
    unsearched = []
    for jar in selected(args.only):
        source = sources_of(jar)
        if not source:
            unsearched.append(jar.stem)
            continue
        with zipfile.ZipFile(source) as archive:
            for entry in archive.namelist():
                if not entry.endswith(".java"):
                    continue
                text = archive.read(entry).decode("utf-8", "replace")
                if not pattern.search(text):
                    continue
                for number, line in enumerate(text.splitlines(), 1):
                    if pattern.search(line):
                        print(f"{entry}:{number}: {line.strip()[:160]}")
                        found += 1
                        if found >= args.max:
                            print(f"... stopped at {args.max}")
                            return
    if unsearched:
        print(f"No sources, so not searched ({len(unsearched)} jars). Use classes and sig for: " + ", ".join(unsearched[:12]))


def locate(name, only):
    """Every jar holding the class, given a full name or a simple one."""
    path = name.replace(".", "/")
    for jar in selected(only):
        with zipfile.ZipFile(jar) as archive:
            for entry in class_entries(archive):
                base = entry.rsplit(".", 1)[0]
                if base == path or base.endswith("/" + path):
                    yield jar, to_class_name(entry)


def cmd_show(args):
    hits = list(locate(args.name, args.only))
    if not hits:
        sys.exit(f"No class named {args.name}")
    if len(hits) > 1:
        print("Several matches, showing the first: " + ", ".join(hit[1] for hit in hits[:8]))
    jar, name = hits[0]
    source = sources_of(jar)
    if not source:
        sys.exit(f"{jar.stem} has no sources. Use: api.py sig {name}")
    with zipfile.ZipFile(source) as archive:
        lines = archive.read(name.replace(".", "/") + ".java").decode("utf-8", "replace").splitlines()
    start, end = 1, len(lines)
    if args.lines:
        start, end = (int(part) for part in args.lines.split("-"))
    print(f"{name} [{source.stem}], {len(lines)} lines")
    for number in range(start, min(end, len(lines)) + 1):
        print(f"{number}: {lines[number - 1]}")


def cmd_sig(args):
    hits = list(locate(args.name, args.only))
    if not hits:
        sys.exit(f"No class named {args.name}")
    jar, name = hits[0]
    if len(hits) > 1:
        print("Several matches, showing the first: " + ", ".join(hit[1] for hit in hits[:8]))
    flags = ["-protected"] + (["-constants"] if args.constants else [])
    output = subprocess.run([str(JAVAP), *flags, "-cp", str(jar), name], capture_output=True, text=True, check=True).stdout
    pattern = re.compile(args.match, re.IGNORECASE) if args.match else None
    for line in output.splitlines():
        if not pattern or pattern.search(line):
            print(line)


def cmd_status(args):
    jars = classpath()
    with_sources = [jar for jar in jars if sources_of(jar)]
    print(f"{len(jars)} jars on the classpath, {len(with_sources)} with sources")
    minecraft = [jar for jar in jars if jar.name.startswith("minecraft-")]
    for jar in minecraft:
        print(f"{jar.name}: " + ("sources found" if sources_of(jar) else "no sources, run: ./gradlew genSources --console=plain"))
    print("No sources: " + ", ".join(jar.stem for jar in jars if jar not in with_sources and jar not in minecraft))
    found = JAVAP.exists() or JAVAP.with_suffix(".exe").exists()
    print("javap: " + ("found" if found else f"missing at {JAVAP}, so sig won't work"))


def main():
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    commands = parser.add_subparsers(dest="command", required=True)

    classes = commands.add_parser("classes", help="find classes by name")
    classes.add_argument("pattern")
    grep = commands.add_parser("grep", help="search source lines")
    grep.add_argument("pattern")
    show = commands.add_parser("show", help="print a class's source")
    show.add_argument("name")
    show.add_argument("--lines", help="such as 1-80")
    sig = commands.add_parser("sig", help="print signatures with javap, which works without sources")
    sig.add_argument("name")
    sig.add_argument("--match", help="keep only matching lines")
    sig.add_argument("--constants", action="store_true", help="include constant values")
    status = commands.add_parser("status", help="show what is searchable")

    for command in (classes, grep, show, sig, status):
        command.add_argument("--only", help="only jars whose name contains this")
    for command in (classes, grep):
        command.add_argument("--max", type=int, default=40)

    args = parser.parse_args()
    {"classes": cmd_classes, "grep": cmd_grep, "show": cmd_show, "sig": cmd_sig, "status": cmd_status}[args.command](args)


if __name__ == "__main__":
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    main()
