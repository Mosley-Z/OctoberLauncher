import io

p = 'feature/widgets/src/main/java/com/shuiyu/octoberrain/feature/widgets/WereadBoardWidget.kt'
s = io.open(p, encoding='utf-8').read()

old_regions = '''        if (form == WereadBoardForm.WEEK || form == WereadBoardForm.MONTH) {
            val headerBottom = (BOARD_HEADER_H * scale).roundToInt()
            regions +=
                WidgetHitRegion(
                    0,
                    headerBottom,
                    0,
                    0,
                    ACTION_CARD_OPEN,
                )
        }
'''
assert old_regions not in s
start = s.index('        if (form == WereadBoardForm.WEEK || form == WereadBoardForm.MONTH) {')
end = s.index('        return regions', start)
s = s[:start] + s[end:]

s = s.replace('''            action == ACTION_CARD_OPEN -> openBody()
''', '')

start = s.index('''    /**
     * 打开当前形态对应的正文页面。''')
end = s.index('''    /**
     * 跳转目标页，未装配路由时返回失败。''')
s = s[:start] + s[end:]

s = s.replace('''        /** 打开正文页的动作标识。 */
        const val ACTION_CARD_OPEN: String = "card:open"

''', '')
io.open(p, 'w', encoding='utf-8').write(s)
print('ACTION_CARD_OPEN' in s, 'openBody' in s)
