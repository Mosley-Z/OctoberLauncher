import xml.etree.ElementTree as ET

NS = "{http://schemas.android.com/apk/res/android}"
PATH = r"D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main\res\layout\settings_layout.xml"

tree = ET.parse(PATH)
root = tree.getroot()


def attr(el, name, default=""):
    return el.get(NS + name, default)


def text_of(el):
    return attr(el, "text", "") or attr(el, "hint", "")


def walk(el, depth, out):
    tag = el.tag.split("}")[-1]
    vid = attr(el, "id", "").split("/")[-1]
    if tag in ("TextView", "EditText", "CheckBox", "RadioButton", "Button", "SeekBar", "Spinner"):
        label = text_of(el)
        if label or vid:
            out.append("  " * depth + f"{tag} [{vid}] {label[:70]}")
        return
    if tag in ("LinearLayout", "ScrollView", "RadioGroup", "HorizontalScrollView", "FrameLayout", "GridLayout", "View"):
        if tag == "View":
            return
        out.append("  " * depth + f"<{tag} id={vid}>")
        for c in el:
            walk(c, depth + 1, out)
        return
    out.append("  " * depth + f"<{tag} id={vid}>")
    for c in el:
        walk(c, depth + 1, out)


out = []
walk(root, 0, out)
print("\n".join(out))
print("---total lines---", len(out))
