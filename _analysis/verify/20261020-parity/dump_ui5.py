import xml.etree.ElementTree as ET
import io
p = r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\ui5.xml"
out = []
for n in ET.parse(p).getroot().iter("node"):
    t = n.get("text", "").strip()
    if t:
        out.append(t + " | " + n.get("bounds") + " | click=" + n.get("clickable"))
io.open(r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\ui5.txt", "w", encoding="utf-8").write("\n".join(out))
print(len(out))