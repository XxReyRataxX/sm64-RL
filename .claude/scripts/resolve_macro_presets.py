#!/usr/bin/env python3
"""Resuelve los MACRO_OBJECT de un nivel a (behavior, model, pos).

Uso:  python3 -I .claude/scripts/resolve_macro_presets.py bob [area]

Mapea el nombre del preset (enum MacroPresets en include/macro_preset_names.h)
al indice del array MacroObjectPresets (include/macro_presets.h), que contiene
{behavior, model, param}. Salida TSV: preset  behavior  model  yaw  x  y  z  param
"""
import os
import re
import sys

ROOT = os.path.dirname(os.path.dirname(os.path.dirname(os.path.abspath(__file__))))


def preset_names():
    src = open(os.path.join(ROOT, "include/macro_preset_names.h")).read()
    body = src.split("enum MacroPresets", 1)[1]
    body = body.split("};", 1)[0]
    return re.findall(r"^\s*(macro_\w+)\s*,", body, re.M)


def preset_table():
    src = open(os.path.join(ROOT, "include/macro_presets.h")).read()
    body = src.split("MacroObjectPresets[] =", 1)[1].split("};", 1)[0]
    rows = []
    for m in re.finditer(r"\{\s*(\w+)\s*,\s*(\w+)\s*,\s*([^}]*?)\}", body):
        rows.append((m.group(1), m.group(2), m.group(3).strip()))
    return rows


def main():
    level = sys.argv[1] if len(sys.argv) > 1 else "bob"
    area = sys.argv[2] if len(sys.argv) > 2 else "1"
    names = preset_names()
    table = preset_table()
    idx = {n: i for i, n in enumerate(names)}

    path = os.path.join(ROOT, "levels", level, "areas", area, "macro.inc.c")
    print("preset\tbehavior\tmodel\tyaw\tx\ty\tz\tbehParam")
    for line in open(path):
        m = re.search(
            r"MACRO_OBJECT(?:_WITH_BEH_PARAM)?\(\s*/\*preset\*/\s*(\w+)\s*,"
            r"\s*/\*yaw\*/\s*(-?\d+)\s*,\s*/\*pos\*/\s*(-?\d+)\s*,\s*(-?\d+)\s*,\s*(-?\d+)\s*"
            r"(?:,\s*/\*behParam\*/\s*([\w()x]+))?",
            line,
        )
        if not m:
            continue
        preset, yaw, x, y, z, param = m.groups()
        i = idx.get(preset)
        beh, model, defparam = table[i] if i is not None and i < len(table) else ("?", "?", "?")
        print("\t".join([preset, beh, model, yaw, x, y, z, param or defparam or "0"]))


if __name__ == "__main__":
    main()
