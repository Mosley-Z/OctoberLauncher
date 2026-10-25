"""Bounded static checks: files, anchors, external download contracts."""
from pathlib import Path
from html.parser import HTMLParser
from urllib.parse import urlsplit, unquote
import json

root = Path(__file__).resolve().parent / 'dist'
class Links(HTMLParser):
    def __init__(self):
        super().__init__(); self.refs=[]; self.ids=set(); self.titles=0
    def handle_starttag(self, tag, attrs):
        attrs=dict(attrs)
        if attrs.get('id'): self.ids.add(attrs['id'])
        if tag=='h1': self.titles+=1
        for key in ('href','src'):
            if attrs.get(key): self.refs.append(attrs[key])

pages={}
for file in root.glob('*.html'):
    parser=Links(); parser.feed(file.read_text('utf-8')); pages[file.name]=parser
errors=[]; checked=0
for name,parser in pages.items():
    if name!='legacy.html' and parser.titles!=1: errors.append(f'{name}: h1 count {parser.titles}')
    for ref in parser.refs:
        url=urlsplit(ref)
        if url.scheme or url.netloc: continue
        dest=unquote(url.path) or name
        if not (root / dest).is_file(): errors.append(f'{name}: missing {ref}')
        elif url.fragment and dest in pages and url.fragment not in pages[dest].ids: errors.append(f'{name}: missing anchor {ref}')
        checked+=1
required={
    'wereadmoji.html':['https://wereadmoji.app.workbuddy.host/apk/weread-stats-1.0.1.apk','https://wereadmoji.app.workbuddy.host/apk/weread-stats-1.3.1-beta.apk'],
    'weread-eink.html':['https://wereadmoji.app.workbuddy.host/apk/weread-eink-3.1.apk'],
}
for name,refs in required.items():
    for ref in refs:
        if ref not in pages[name].refs: errors.append(f'{name}: changed download {ref}')
result={'pages':len(pages),'local_references_checked':checked,'errors':errors,'scope':'static local references and legacy download contracts, not remote availability'}
out=root.parent / 'evidence/static-check.json';out.write_text(json.dumps(result,ensure_ascii=False,indent=2),'utf-8')
print(json.dumps(result,ensure_ascii=False))
raise SystemExit(bool(errors))
