#!/usr/bin/env python3
"""Turn the app's SVG icons (app/design/icons/*.svg) into Android vector drawables (res/drawable/ic_*.xml)
and into family-server/public/icons.js, so the car app and the family phone page share one icon set.

Usage: python3 tools/svg2vector.py
The SVGs are the source; re-run after adding or editing one. Only the subset the icon set uses is supported:
a 24x24 viewBox and <path> elements, stroked with currentColor (root defaults) or filled with currentColor.
Colour is not stored: the app tints every icon with the theme colour, so paths are drawn in plain black.
"""
import json
import pathlib
import sys
import xml.etree.ElementTree as ET

ROOT = pathlib.Path(__file__).resolve().parent.parent
SRC = ROOT / "app/design/icons"
OUT = ROOT / "app/src/main/res/drawable"
WEB = ROOT / "family-server/public/icons.js"
NS = "{http://www.w3.org/2000/svg}"
INK = "#FF000000"


def convert(svg: pathlib.Path) -> str:
    root = ET.parse(svg).getroot()
    if root.get("viewBox") != "0 0 24 24":
        sys.exit(f"{svg.name}: viewBox must be 0 0 24 24")
    defaults = {k: root.get(k) for k in ("fill", "stroke", "stroke-width", "stroke-linecap", "stroke-linejoin")}
    paths = []
    for el in root.iter():
        if el.tag == NS + "svg":
            continue
        if el.tag != NS + "path":
            sys.exit(f"{svg.name}: only <path> is supported, found {el.tag.removeprefix(NS)}")
        a = {**defaults, **{k: v for k, v in el.attrib.items() if k != "d"}}
        attrs = [f'android:pathData="{el.get("d")}"']
        attrs.append(f'android:fillColor="{INK if a["fill"] == "currentColor" else "#00000000"}"')
        if a["stroke"] == "currentColor":
            attrs += [f'android:strokeColor="{INK}"', f'android:strokeWidth="{a["stroke-width"] or 1}"',
                      f'android:strokeLineCap="{a["stroke-linecap"] or "butt"}"', f'android:strokeLineJoin="{a["stroke-linejoin"] or "miter"}"']
        paths.append("    <path\n        " + "\n        ".join(attrs) + " />")
    return (
        f"<!-- Generated from app/design/icons/{svg.name} by tools/svg2vector.py. Edit the SVG, not this file. -->\n"
        '<vector xmlns:android="http://schemas.android.com/apk/res/android"\n'
        '    android:width="24dp" android:height="24dp" android:viewportWidth="24" android:viewportHeight="24">\n'
        + "\n".join(paths) + "\n</vector>\n"
    )


def web_icons(svgs) -> str:
    """The same icons for the family phone page: path data only, drawn there with currentColor."""
    table = {}
    for svg in svgs:
        root = ET.parse(svg).getroot()
        table[svg.stem] = [[p.get("d"), p.get("fill") == "currentColor"] for p in root.iter(NS + "path")]
    return (
        "/* Generated from app/design/icons by tools/svg2vector.py: the car app's icon set. Edit the SVGs, not this file. */\n"
        "window.DW_ICONS = " + json.dumps(table, separators=(",", ":")) + ";\n"
    )


def main():
    OUT.mkdir(parents=True, exist_ok=True)
    svgs = sorted(SRC.glob("*.svg"))
    for svg in svgs:
        (OUT / f"ic_{svg.stem}.xml").write_text(convert(svg))
    WEB.write_text(web_icons(svgs))
    print(f"{len(svgs)} icons -> {OUT.relative_to(ROOT)} and {WEB.relative_to(ROOT)}")


if __name__ == "__main__":
    main()
