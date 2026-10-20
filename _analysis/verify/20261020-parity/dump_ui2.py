import xml.etree.ElementTree as ET
p = r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\ui-settings.xml"
for n in ET.parse(p).getroot().iter("node"):
    t = n.get("text", "").strip()
    if t:
        print(t, "|", n.get("bounds"), "| click=", n.get("clickable"))