#!/usr/bin/env python3
"""Combine src/ into one SecondLoop.java that can be uploaded through OnBot Java.

Every top-level type in src/secondloop/*.java (the interpreter) and
src/ftc/*.java (modules that use the FTC SDK) becomes a static nested class of
SecondLoop, so nothing clashes with other files in the teamcode package. Their
imports are merged into the single import list at the top of the output.
"""

import pathlib
import re
import sys

ROOT = pathlib.Path(__file__).resolve().parent.parent
TEMPLATE = ROOT / "src" / "SecondLoop.java"
SOURCE_DIRS = [ROOT / "src" / "secondloop", ROOT / "src" / "ftc"]
MARKER = "// @@SECONDLOOP_SOURCES@@"
INTERNAL_IMPORT = "import secondloop."

TOP_LEVEL_TYPE = re.compile(r"^(?:public\s+)?((?:final\s+|abstract\s+)?(?:class|interface|enum)\b)")


def split_source(text):
    """Return (imports, body). The package line and secondloop.* imports are
    dropped, since every class ends up nested in SecondLoop."""
    imports, body = [], []
    for line in text.splitlines():
        if line.startswith("package "):
            continue
        if line.startswith("import "):
            if not line.startswith(INTERNAL_IMPORT):
                imports.append(line)
            continue
        body.append(line)
    while body and not body[0].strip():
        body.pop(0)
    while body and not body[-1].strip():
        body.pop()
    return imports, body


def nest(path, body, indent):
    out = [indent + "// ---- " + path.relative_to(ROOT / "src").as_posix() + " ----", ""]
    for line in body:
        line = TOP_LEVEL_TYPE.sub(r"static \1", line)
        out.append(indent + line if line.strip() else "")
    out.append("")
    return out


def amalgamate():
    imports = set()
    nested = []
    for path in [p for d in SOURCE_DIRS for p in sorted(d.glob("*.java"))]:
        file_imports, body = split_source(path.read_text())
        imports.update(file_imports)
        nested.extend(nest(path, body, "    "))

    template_imports, template_body = split_source(TEMPLATE.read_text())
    imports.update(template_imports)

    marker_lines = [i for i, line in enumerate(template_body) if line.strip() == MARKER]
    if len(marker_lines) != 1:
        sys.exit("expected exactly one %s line in %s" % (MARKER, TEMPLATE))
    at = marker_lines[0]
    while nested and not nested[-1]:
        nested.pop()
    template_body[at:at + 1] = nested

    header = [
        "// GENERATED FILE - DO NOT EDIT.",
        "// Built from src/ by tools/amalgamate.py. Upload this file through OnBot Java.",
        "",
        "package org.firstinspires.ftc.teamcode;",
        "",
    ]
    return "\n".join(header + sorted(imports) + [""] + template_body) + "\n"


def main():
    output = pathlib.Path(sys.argv[1]) if len(sys.argv) > 1 else ROOT / "build" / "SecondLoop.java"
    output.parent.mkdir(parents=True, exist_ok=True)
    output.write_text(amalgamate())
    print("wrote", output)


if __name__ == "__main__":
    main()
