"""Generate a reviewable static site without changing the legacy deployment."""
from pathlib import Path
import json, html, re, hashlib

ROOT = Path(__file__).resolve().parent
OUT = ROOT / 'dist'
LEGACY = Path('D:/WorkBuddyStation/2026-10-03-03-42-56/wereadmoji-site')
BASE = 'https://wereadmoji.app.workbuddy.host/'
REPO = 'https://github.com/Mosley-Z/OctoberLauncher'
stable = json.loads((LEGACY / 'update-stable.json').read_text('utf-8'))
beta = json.loads((LEGACY / 'update-beta.json').read_text('utf-8'))
mod = json.loads((LEGACY / 'mod/update.json').read_text('utf-8'))
e = html.escape

def header(current='index.html'):
    links = [('index.html', '十雨桌面'), ('wereadmoji.html', '微读墨记'), ('weread-eink.html', '微信读书自用版'), ('updates.html', '更新日志')]
    nav = ''.join(f'<a href="{url}"'+(' aria-current="page"' if current == url else '')+f'>{label}</a>' for url,label in links)
    return f'''<a class="skip" href="#main">跳到正文</a><header class="header"><div class="wrap header-inner"><a class="brand" href="index.html"><img src="assets/october-icon.png" alt="" width="37" height="37"><span>十雨桌面<small>OctoberLauncher</small></span></a><button class="menu" aria-controls="navigation" aria-expanded="false">菜单</button><nav id="navigation" class="nav" aria-label="主导航">{nav}<a href="{REPO}">GitHub ↗</a><a class="nav-download" href="index.html#download">下载十雨</a></nav></div></header>'''

def footer():
    return f'''<footer class="footer"><div class="wrap"><div class="footer-top"><a class="brand" href="index.html">十雨桌面</a><div class="footer-links"><a href="wereadmoji.html">微读墨记</a><a href="weread-eink.html">微信读书自用版</a><a href="guide.html">安装与使用</a><a href="privacy.html">隐私与许可</a><a href="{REPO}">GitHub ↗</a></div></div><div class="footer-bottom"><span>十月的雨，落在你的桌面。</span><span>独立开发 · 面向 Android 墨水屏设备</span></div></div></footer>'''

def page(name, title, body, desc, alpine=False):
    scripts = '<script defer src="vendor/alpine.min.js"></script>' if alpine else ''
    (OUT / name).write_text(f'''<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width, initial-scale=1"><title>{e(title)} · 十雨桌面</title><meta name="description" content="{e(desc)}"><meta name="theme-color" content="#f7fafd"><link rel="icon" href="assets/october-icon.png"><link rel="stylesheet" href="site.css">{scripts}</head><body>{header(name)}<main id="main">{body}</main>{footer()}<script src="vendor/gsap.min.js"></script><script src="vendor/ScrollTrigger.min.js"></script><script src="site.js"></script></body></html>''', 'utf-8')

def intro(title, desc, actions='', badge=''):
    return f'''<section class="subhero"><div class="wrap"><p class="breadcrumb"><a href="index.html">十雨桌面</a> / {title}</p><h1>{title}{f'<span class="pill">{badge}</span>' if badge else ''}</h1><p class="lead">{desc}</p>{actions}</div></section>'''

entries = '''<div class="product-entries"><a class="entry" href="wereadmoji.html"><span class="entry-icon" aria-hidden="true">墨</span><div><h2 class="entry-title">微读墨记</h2><p>独立版持续维护 · 阅读数据与笔记</p></div><span class="arrow" aria-hidden="true">↗</span></a><a class="entry" href="weread-eink.html"><span class="entry-icon" aria-hidden="true">读</span><div><h2 class="entry-title">微信读书自用版</h2><p>墨水屏自用改版 · 安装包与更新说明</p></div><span class="arrow" aria-hidden="true">↗</span></a></div>'''

page('index.html','十月的雨，落在你的桌面',f'''
<section class="hero"><div class="wrap hero-inner"><div class="hero-copy"><p class="hero-name">十雨桌面 · OctoberLauncher</p><h1>十月的雨，<br>落在你的<span>桌面。</span></h1><p class="lead">为 Android 墨水屏设备打造的桌面。<br>让应用、书籍与笔记，各得其所。</p><div class="actions"><a class="btn" href="#download">下载十雨桌面 <span aria-hidden="true">↓</span></a><a class="btn secondary" href="#features">了解功能 <span aria-hidden="true">↗</span></a></div><p class="hero-note">Android 8.0 及以上 · 墨水屏桌面启动器</p><div class="mobile-related"><a href="wereadmoji.html">微读墨记 ↗</a><a href="weread-eink.html">微信读书自用版 ↗</a></div></div><div class="scene"><div class="window" aria-hidden="true"><div class="rain"></div></div><div class="ripple" aria-hidden="true"></div><div class="device"><img src="assets/october-home.png" width="480" height="800" alt="十雨桌面实际界面：时钟组件与应用宫格"></div><span class="scene-caption">十雨桌面 · 早期界面截图</span></div></div></section>
<div class="wrap">{entries}</div>
<section id="features" class="section"><div class="wrap"><div class="section-head"><h2>从桌面出发，<br>把时间留给阅读。</h2><p>常用应用放在顺手的位置，书架与笔记留在身边。按自己的使用习惯，安排每一屏。</p></div><div class="feature-layout"><div class="screen-mat"><img src="assets/october-home.png" width="480" height="800" loading="lazy" alt="十雨桌面时钟与应用布局"></div><dl class="feature-list"><div class="feature"><dt><span>01</span>桌面，按你的习惯</dt><dd>宫格与列表、分页与整理，搭配时钟、图片等组件，安排常用应用。</dd></div><div class="feature"><dt><span>02</span>书籍与笔记，近一点</dt><dd>书架、微信读书数据与桌面卡片，让阅读内容自然融入日常桌面。</dd></div><div class="feature"><dt><span>03</span>文件，也有自己的位置</dt><dd>浏览本地文件与相册，查看和编辑图片，把常用内容收在一起。</dd></div><div class="feature"><dt><span>04</span>留出自己的样子</dt><dd>调整布局、壁纸、图标与主题，保留墨水屏清晰、安静的阅读感。</dd></div></dl></div></div></section>
<section class="section blue-section"><div class="wrap statement"><h2>墨记来到十雨，<br>也继续独立生长。</h2><p>十雨融合了微读墨记的部分能力。喜欢独立使用的你，仍然可以选择微读墨记；独立版将继续维护。</p><a class="text-link" href="wereadmoji.html">了解微读墨记独立版 ↗</a></div></section>
<section class="section"><div class="wrap"><div class="section-head"><h2>每一次更新，<br>都在各自的路上。</h2><a class="text-link" href="updates.html">查看各产品更新 ↗</a></div><div class="updates-list"><article class="update-row"><span class="tag">十雨桌面</span><div><h3>桌面、书架与微读墨记融合</h3><p>发布版本与完整变更记录，前往十雨桌面仓库查看。</p></div><a href="{REPO}/releases" aria-label="查看十雨桌面发布记录">↗</a></article><article class="update-row"><span class="tag">微读墨记 · Beta</span><div><h3>阅读指纹与独立墨台</h3><p>阅读划线生成指纹图，墨台拥有自己的页面。</p></div><a href="wereadmoji.html" aria-label="了解微读墨记 Beta">↗</a></article><article class="update-row"><span class="tag">微信读书自用版</span><div><h3>自定义字体，直接从目录选择</h3><p>支持从字体目录选取字体，也可通过系统选择器导入。</p></div><a href="weread-eink.html" aria-label="了解微信读书自用版更新">↗</a></article></div></div></section>
<section id="download" class="download"><div class="wrap download-inner"><div><h2>给桌面，换一场十月的雨。</h2><p>前往 GitHub 发布页获取安装包与版本说明。<br>首次安装后，按系统提示设置为默认桌面。</p></div><div class="actions"><a class="btn" href="{REPO}/releases">获取十雨桌面 <span aria-hidden="true">↗</span></a><a class="btn secondary" href="guide.html">安装指引</a></div></div></section>
''','十雨桌面，面向 Android 墨水屏设备的桌面启动器。下载十雨桌面，访问持续维护的微读墨记独立版与微信读书自用版。')

def release_card(data, label):
    local = LEGACY / data['apk']
    assert local.stat().st_size == data['size']
    assert hashlib.sha256(local.read_bytes()).hexdigest() == data['sha256']
    return f'''<article class="release-card"><h2>{label}</h2><div class="version">{e(data['versionName'])}</div><p class="meta">Android 安装包 · {data['size']/1024:.0f} KB</p><a class="btn" href="{BASE}{data['apk']}">下载{label} ↓</a><p><a class="text-link" href="{e(data['urls'][1])}">备用下载 ↗</a></p><details><summary>本版更新说明</summary><p>{e(data['notes'])}</p></details><details><summary>校验信息</summary><pre>SHA-256\n{data['sha256']}\n文件大小：{data['size']} 字节</pre></details></article>'''

page('wereadmoji.html','微读墨记',intro('微读墨记','把微信读书里的阅读数据与笔记，带到你的桌面。独立使用，继续更新。',f'<div class="actions"><a class="btn" href="#downloads">下载独立版 ↓</a><a class="btn secondary" href="https://github.com/Mosley-Z/WereadMoji">GitHub ↗</a></div>','独立版持续维护')+f'''<div class="wrap content"><div class="notice"><p><strong>与十雨融合，也保留独立选择。</strong>十雨桌面已融合微读墨记的部分能力；微读墨记独立版继续维护，可按自己的使用习惯选择。</p></div><div id="downloads" class="content-grid">{release_card(stable,'正式版')}{release_card(beta,'Beta 版')}</div><h2>在桌面上，回到阅读</h2><div class="content-grid"><div><h3>本书与本记</h3><p>查看最近在读的书籍，让划线与笔记回到眼前。正式版支持手动选择最近在读的书。</p></div><div><h3>墨台与阅读指纹</h3><p>Beta 版提供独立墨台页面，将阅读划线排成指纹图，并可生成壁纸。</p></div></div><h2>开始使用</h2><ol><li>选择正式版或 Beta 版，下载安装包并安装。</li><li>打开应用，按初始化指引配置微信读书 API Key 与相关权限。</li><li>选择需要展示的内容，再调整桌面卡片。</li></ol><p>Beta 版包含实验性功能，可能调整或撤回；重要使用场景建议选择正式版。</p><p><a class="text-link" href="legacy.html">查看原站完整功能、安装说明与版本历史 ↗</a></p><h2>权限与数据</h2><p>读取微信读书数据需要自行配置 API Key。具体权限用途与使用限制请查看原站说明及应用内指引。</p><p>微读墨记是第三方工具，与腾讯及微信读书无关联。</p></div>''','微读墨记独立版持续维护。正式版、Beta 版下载，阅读笔记与指纹功能介绍。')

apk = LEGACY / 'apk/weread-eink-3.1.apk'
assert apk.stat().st_size == mod['apkSize']
assert hashlib.sha256(apk.read_bytes()).hexdigest() == mod['apkSha256']
page('weread-eink.html','微信读书自用版',intro('微信读书自用版','面向墨水屏的个人自用改版。这里保留完整安装包、热更新说明与历史记录。','<a class="btn" href="#downloads">查看安装与下载 ↓</a>','非官方 · 个人自用')+f'''<div class="wrap content"><div class="notice"><p><strong>非官方、与腾讯无关。</strong>自签名包，<strong>装它必须先卸载官方墨水屏版（会清掉本地数据）</strong>；仅供个人自用备份，<strong>请勿再分发</strong>。已有自用版用户请先查看下方升级说明。</p></div><div id="downloads" class="content-grid"><article class="release-card"><h2>完整安装包</h2><div class="version">自用 V3.1</div><p class="meta">基于微信读书墨水屏版 2.1.2 · Android 8.0 及以上<br>{mod['apkSize']/1024/1024:.1f} MiB</p><a class="btn" href="{e(mod['apkUrl'])}">下载完整安装包 ↓</a><details><summary>安装包校验信息</summary><pre>SHA-256\n{mod['apkSha256']}\n文件大小：{mod['apkSize']} 字节</pre></details></article><article class="release-card"><h2>应用内热更新</h2><div class="version">{e(mod['versionName'])}</div><p>已安装自用版后，通过应用内「检查更新」获取适配的插件。</p><p class="meta">完整安装包版本、官方内核版本与插件版本分别编号，不可直接比较大小。</p><details><summary>本次插件更新</summary><p>{e(mod['notes'])}</p></details></article></div><h2>安装与升级</h2><ol><li>首次从官方墨水屏版切换：先备份需要保留的数据，再卸载官方版并安装自用版。</li><li>已经使用自用版：优先在应用内检查更新；需要全量升级时，按应用提示安装完整包。</li><li>较早底包可能先收到过渡插件，再通过应用内提示下载完整包。</li></ol><p><a class="text-link" href="legacy.html">查看原站完整安装说明与版本历史 ↗</a></p><h2>使用边界</h2><p>这是对微信读书官方墨水屏版的个人改版，与腾讯及微信读书无关联。仅作个人自用留档与说明，不提供担保、不承诺可用性，请勿再分发或用于商业用途。</p></div>''','微信读书墨水屏自用版的完整安装包、热更新记录与安装说明。非官方个人改版。')

logs = [('moji', '微读墨记正式版 '+stable['versionName'], stable['notes']), ('moji', '微读墨记 Beta '+beta['versionName'], beta['notes']), ('eink','微信读书自用版插件 '+mod['versionName'],mod['notes'])]
log_html = ''.join(f'''<article class="log-item" x-show="filter === 'all' || filter === '{kind}'" x-transition.opacity.duration.180ms><h2>{e(title)}</h2><p>{e(notes)}</p><a class="text-link" href="{'wereadmoji.html' if kind=='moji' else 'weread-eink.html'}">前往产品下载 ↗</a></article>''' for kind,title,notes in logs)
page('updates.html','更新日志',intro('各自更新，一起生长。','按产品查看更新与下载。十雨桌面的发布记录以 GitHub 仓库为准。')+f'''<div class="wrap content" x-data="{{filter: 'all'}}"><div class="tabs" aria-label="筛选产品"><button @click="filter='all'" :aria-pressed="filter==='all'">全部产品</button><button @click="filter='october'" :aria-pressed="filter==='october'">十雨桌面</button><button @click="filter='moji'" :aria-pressed="filter==='moji'">微读墨记</button><button @click="filter='eink'" :aria-pressed="filter==='eink'">微信读书自用版</button></div><article class="log-item" x-show="filter==='all' || filter==='october'" x-transition.opacity.duration.180ms><h2>十雨桌面发布记录</h2><p>安装包、正式版本与完整变更记录统一在仓库发布页查看。</p><a class="text-link" href="{REPO}/releases">前往 GitHub Releases ↗</a></article>{log_html}<p><a class="text-link" href="legacy.html">查看微读墨记与自用版完整历史 ↗</a></p><noscript><p>启用 JavaScript 可按产品筛选；所有记录已完整显示在本页。</p></noscript></div>''','十雨桌面、微读墨记和微信读书自用版的产品更新与历史记录。',True)

page('guide.html','安装与使用',intro('从这里，开始使用十雨。','适用于 Android 8.0 及以上。不同墨水屏设备的系统菜单与权限入口可能有所不同。')+f'''<div class="wrap content faq"><h2>安装十雨桌面</h2><ol><li>前往 <a class="text-link" href="{REPO}/releases">GitHub 发布页</a>，选择已发布的安装包。</li><li>在设备上打开 APK，按系统提示允许当前浏览器或文件管理器安装应用。</li><li>安装后打开十雨，按系统提示将其设置为默认桌面。</li><li>按自己的需要设置布局、组件与书架。</li></ol><h2>常见问题</h2><details><summary>需要一次性授予所有权限吗？</summary><p>通知、无障碍、悬浮窗等权限按所需功能申请。可以跳过可选权限，先使用基本桌面功能。</p></details><details><summary>不想继续用十雨作为默认桌面，怎样切换？</summary><p>在系统设置中找到默认应用或主屏幕应用，选择原来的桌面。具体菜单名称因设备而异。</p></details><details><summary>微读墨记还可以单独使用吗？</summary><p>可以。微读墨记独立版继续维护，下载入口保留在本站。</p><a class="text-link" href="wereadmoji.html">下载微读墨记 ↗</a></details><details><summary>十雨必须搭配微信读书自用版吗？</summary><p>十雨桌面可以独立使用。微信读书自用版是单独的产品入口，不是安装十雨的前提。</p></details><details><summary>微信读书自用版能直接覆盖官方版安装吗？</summary><p>不能。自签名包与官方包签名不同，首次切换需卸载官方墨水屏版，这会清掉本地数据，请先查看产品页的完整提示。</p><a class="text-link" href="weread-eink.html">查看自用版安装说明 ↗</a></details></div>''','十雨桌面安装指引、默认桌面设置与关联产品使用问题。')

page('privacy.html','隐私与许可',intro('了解权限，也了解来源。','各产品的权限与使用方式有所不同，请结合产品说明及应用内提示阅读。')+f'''<div class="wrap content legal"><h2>十雨桌面</h2><p>通知读取用于通知功能，无障碍与悬浮窗用于对应系统交互，使用情况访问用于相关统计。权限按功能申请，文件管理需要相应存储访问权限。</p><p>微信读书、天气和在线更新等联网功能会连接对应服务。具体实现与第三方组件许可可在应用内及 <a href="{REPO}">项目仓库</a>查看。</p><h2>微读墨记</h2><p>配置微信读书 API Key 后读取自己的阅读数据。原站保留了该独立版的权限用途、已知限制与使用说明。</p><p><a href="legacy.html">查看原站权限与隐私说明</a></p><h2>微信读书自用版</h2><p>非官方个人改版，与腾讯及微信读书无关联。原产品的名称与商标归原权利人所有。仅作个人自用留档，请勿再分发或用于商业用途。</p><h2>本站</h2><p>本页面不需要注册或登录。下载链接可能指向旧资源站、GitHub 或 jsDelivr，相应访问由目标服务处理。</p><h2>许可与来源</h2><p>十雨桌面及微读墨记的源码和许可信息以各自仓库及应用内说明为准。微信读书自用版不属于腾讯官方发布。</p></div>''','十雨桌面、微读墨记与微信读书自用版的权限、隐私与来源说明。')

# Preserve the public historical page only; strip its cloud counting scripts.
legacy = (LEGACY / 'index.html').read_text('utf-8')
legacy = re.sub(r'<script\b[^>]*>.*?</script>', '', legacy, flags=re.S|re.I)
legacy = re.sub(r'(href|src)="(apk/|mod/|_assets/)([^\"]+)"', lambda m:f'{m[1]}="{BASE}{m[2]}{m[3]}"', legacy)
legacy = legacy.replace('<body>', '<body><aside style="padding:18px;background:#eaf2f8;color:#183449;text-align:center;font:14px/1.8 sans-serif">原站历史资料 · 保留原文中的版本与功能说明，不代表当前最新状态。<br><a href="index.html">返回十雨桌面新站</a> · <a href="wereadmoji.html">微读墨记独立版</a> · <a href="weread-eink.html">微信读书自用版</a></aside>')
(OUT / 'legacy.html').write_text(legacy,'utf-8')
(OUT / 'robots.txt').write_text('User-agent: *\nDisallow: /\n','utf-8')
# No anonymous public release is available at the configured GitHub URL.
# Present a truthful download state instead of sending visitors to a 404.
for file in OUT.glob('*.html'):
    if file.name == 'legacy.html':
        continue
    text = file.read_text('utf-8')
    text = text.replace(f'href="{REPO}/releases"', 'href="guide.html#october-download"')
    text = text.replace('前往 GitHub 发布页获取安装包与版本说明。<br>首次安装后，按系统提示设置为默认桌面。', '十雨桌面的本站安装包暂未开放下载。<br>微读墨记与微信读书自用版可在各自页面下载。')
    text = text.replace('获取十雨桌面 <span', '查看下载信息 <span')
    text = text.replace('发布版本与完整变更记录，前往十雨桌面仓库查看。', '查看安装与使用说明，了解十雨桌面的使用方式。')
    text = text.replace('安装包、正式版本与完整变更记录统一在仓库发布页查看。', '十雨桌面的本站安装包暂未开放下载，可先查看安装与使用说明。')
    text = text.replace('前往 GitHub Releases ↗', '查看安装与下载说明 ↗')
    text = text.replace('十雨桌面的发布记录以 GitHub 仓库为准。', '各产品的版本与下载渠道分别维护。')
    text = text.replace('<h2>安装十雨桌面</h2>', '<h2 id="october-download">十雨桌面下载</h2><div class="notice"><p>本站安装包暂未开放下载。微读墨记独立版与微信读书自用版的下载入口正常保留。</p></div><h2>安装十雨桌面</h2>')
    text = text.replace('前往 <a class="text-link" href="guide.html#october-download">GitHub 发布页</a>，选择已发布的安装包。', '取得十雨桌面正式发布的 APK 安装包。')
    file.write_text(text,'utf-8')
print('Generated 7 HTML pages. Verified stable, beta and full APK local hashes. Legacy update files untouched.')
