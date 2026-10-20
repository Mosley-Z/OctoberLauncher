import xml.etree.ElementTree as ET
import re, os

ns = "{http://schemas.android.com/apk/res/android}"
base = r"D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main"

def show(el, depth):
    tag = el.tag.split("}")[-1]
    attrs = []
    for key in ("id", "text", "layout_width", "layout_height", "visibility", "orientation"):
        v = el.get(ns + key)
        if v:
            if key == "id":
                v = v.replace("@+id/", "")
            attrs.append(f"{key}={v}")
    for k, v in el.attrib.items():
        if k.startswith("{") and "schemas.android" not in k:
            attrs.append(f"{k.split('}')[-1]}={v}")
    print("  " * depth + tag + (" [" + ", ".join(attrs) + "]" if attrs else ""))
    for c in el:
        show(c, depth + 1)

for name in ("inc_lab_lockscreen", "inc_lab_power"):
    p = base + rf"\res\layout\{name}.xml"
    print("=== " + name)
    show(ET.parse(p).getroot(), 0)

# 收集 lab_ 开头的字符串
strings = {}
for root, _, files in os.walk(base + r"\res"):
    for f in files:
        if f == "strings.xml":
            for el in ET.parse(os.path.join(root, f)).getroot():
                if el.get("name", "").startswith("lab_") or el.get("name", "").startswith("btn_open_a11y"):
                    strings[el.get("name")] = (el.text or "").strip()
print("=== strings")
for k in sorted(strings):
    print(k, "=", strings[k])