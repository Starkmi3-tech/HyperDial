"""On-device checks for HyperDial: screenshots, view bounds and pixel colors over adb.

Usage:
  devicecheck.py screenshot <serial> <out.png>
  devicecheck.py ui <serial> <resource_id>      prints the node as JSON, exit 1 if missing
  devicecheck.py pixel <png> <x> <y> <#RRGGBB>  exit 0 if the pixel matches
  devicecheck.py density <serial>               prints the screen density in dpi
"""

import json
import re
import subprocess
import sys
import xml.etree.ElementTree as ET

from PIL import Image

_BOUNDS = re.compile(r"\[(-?\d+),(-?\d+)\]\[(-?\d+),(-?\d+)\]")


def pixel_matches(png_path: str, x: int, y: int, hex_color: str, tol: int = 8) -> bool:
    expected = tuple(int(hex_color.lstrip("#")[i:i + 2], 16) for i in (0, 2, 4))
    with Image.open(png_path) as img:
        actual = img.convert("RGB").getpixel((x, y))
    return all(abs(a - e) <= tol for a, e in zip(actual, expected))


def parse_node(ui_xml: str, resource_id: str) -> dict | None:
    suffix = f":id/{resource_id}"
    for node in ET.fromstring(ui_xml).iter("node"):
        if node.get("resource-id", "").endswith(suffix):
            match = _BOUNDS.fullmatch(node.get("bounds", ""))
            bounds = tuple(int(v) for v in match.groups()) if match else None
            return {"text": node.get("text", ""), "bounds": bounds}
    return None


def dp_to_px(dp: float, density_dpi: int) -> int:
    return round(dp * density_dpi / 160)


def _adb(serial: str, *args: str) -> bytes:
    return subprocess.run(["adb", "-s", serial, *args], check=True, capture_output=True).stdout


def _density(serial: str) -> int:
    out = _adb(serial, "shell", "wm", "density").decode()
    override = re.search(r"Override density:\s*(\d+)", out)
    physical = re.search(r"Physical density:\s*(\d+)", out)
    return int((override or physical).group(1))


def main(argv: list[str]) -> int:
    cmd, args = argv[0], argv[1:]
    if cmd == "screenshot":
        serial, out = args
        with open(out, "wb") as f:
            f.write(_adb(serial, "exec-out", "screencap", "-p"))
        return 0
    if cmd == "ui":
        serial, resource_id = args
        _adb(serial, "shell", "uiautomator", "dump", "/sdcard/hd.xml")
        node = parse_node(_adb(serial, "exec-out", "cat", "/sdcard/hd.xml").decode("utf-8"), resource_id)
        if node is None:
            print(f"missing: {resource_id}")
            return 1
        print(json.dumps(node))
        return 0
    if cmd == "pixel":
        png, x, y, hex_color = args
        ok = pixel_matches(png, int(x), int(y), hex_color)
        print("match" if ok else "mismatch")
        return 0 if ok else 1
    if cmd == "density":
        print(_density(args[0]))
        return 0
    print(__doc__)
    return 2


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
