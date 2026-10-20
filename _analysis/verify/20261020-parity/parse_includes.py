import xml.etree.ElementTree as ET
ns = "{http://schemas.android.com/apk/res/android}"
base = r"D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main"
tree = ET.parse(base + r"\res\layout\settings_layout.xml")
for el in tree.getroot().iter():
    if el.tag.split("}")[-1] == "include":
        layout = None
        for k, v in el.attrib.items():
            if k.endswith("}layout") or k == "layout":
                layout = v
        print("include ->", layout, "id:", el.get(ns + "id"))