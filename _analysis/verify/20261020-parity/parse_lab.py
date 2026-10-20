import xml.etree.ElementTree as ET
import sys

path = r"D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main\res\layout\settings_layout.xml"
ns = "{http://schemas.android.com/apk/res/android}"
tree = ET.parse(path)
root = tree.getroot()

def show(el, depth):
    tag = el.tag.split("}")[-1]
    attrs = []
    for key in ("id", "layout_width", "layout_height", "text", "visibility", "orientation", "layout_weight"):
        value = el.get(ns + key)
        if value:
            if key == "id":
                value = value.replace("@+id/", "")
            attrs.append(f"{key}={value}")
    for key, value in el.attrib.items():
        if key.startswith("{") and "schemas.android.com/apk/res" not in key:
            short = key.split("}")[-1]
            if "id" not in short and "layout_width" not in short:
                attrs.append(f"{short}={value}")
    print("  " * depth + tag + (" [" + ", ".join(attrs) + "]" if attrs else ""))
    for child in el:
        show(child, depth + 1)

target = None
for el in root.iter():
    if el.get(ns + "id") == "@+id/page_lab":
        target = el
        break
if target is None:
    print("page_lab not found")
    sys.exit(1)
show(target, 0)