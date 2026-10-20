import xml.etree.ElementTree as ET
p = r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\ui-desktop.xml"
root = ET.parse(p).getroot()
for n in root.iter("node"):
    t = n.get("text", "").strip()
    d = n.get("content-desc", "").strip()
    cls = n.get("class", "").split(".")[-1]
    b = n.get("bounds", "")
    if t or d:
        print(cls, "|", t or ("desc:" + d), "|", b, "| click=", n.get("clickable"))