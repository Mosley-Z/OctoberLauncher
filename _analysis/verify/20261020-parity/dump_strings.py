import xml.etree.ElementTree as ET
import os, io

base = r"D:\WorkBuddyStation\WereadMoji\weread-widget\app\src\main"
out = []
for root, _, files in os.walk(base + r"\res"):
    for f in files:
        if f == "strings.xml":
            for el in ET.parse(os.path.join(root, f)).getroot():
                name = el.get("name", "")
                if name.startswith("lab_") or name in ("btn_open_a11y","lock_intro","lock_enable","lock_pin_label","lock_btn_save","lock_bg_label","lock_btn_bg_load","lock_btn_bg_clear","lock_bg_hint","lock_tip_a11y","power_intro","power_perm_cmd_label","power_btn_copy","power_perm_steps","power_item_wifi_notif","power_tip_wifi_notif","power_item_anim","power_tip_anim","power_item_screensaver","power_tip_screensaver","power_btn_battery_opt","power_battery_opt_tip","power_shizuku_hint"):
                    out.append(name + " = " + (el.text or "").strip())
p = r"D:\DSHStation\OctoberLauncher\_analysis\verify\20261020-parity\lab_strings.txt"
io.open(p, "w", encoding="utf-8").write("\n".join(sorted(out)))
print("written", len(out))