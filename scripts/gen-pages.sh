#!/bin/sh
# 把 docs/ 下的隐私政策和用户协议生成静态网页，供 GitHub Pages（main 分支 /docs 目录）托管
# 每次修改 docs/*.md 后运行一次：sh scripts/gen-pages.sh
set -e
cd "$(dirname "$0")/../docs"
CSS='<style>body{max-width:760px;margin:0 auto;padding:24px 18px 60px;font:16px/1.75 -apple-system,"PingFang SC","Roboto","Microsoft YaHei",sans-serif;color:#222;background:#fff}h1{font-size:1.6em}h2{font-size:1.25em;margin-top:1.8em}a{color:#0a59f7}table{border-collapse:collapse}td,th{border:1px solid #ddd;padding:4px 8px}@media(prefers-color-scheme:dark){body{background:#121212;color:#ddd}a{color:#6ea8ff}}</style>'
for f in privacy privacy_en agreement agreement_en; do
  case $f in *_en) L=en;; *) L=zh-CN;; esac
  T=$(head -1 $f.md | sed 's/^# *//')
  pandoc $f.md -f gfm -t html5 -s --metadata title="$T" --metadata lang=$L \
    -V header-includes="$CSS" -V pagetitle="$T" -o $f.html
  python3 - $f.html <<'P'
import re,sys;p=sys.argv[1];s=open(p).read();s=re.sub(r'<header id="title-block-header">.*?</header>','',s,flags=re.S);open(p,'w').write(s)
P
done
cat > index.html <<'H'
<!doctype html><html lang="zh-CN"><head><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1"><title>咪咪面板（Android 版）</title>
<style>body{max-width:760px;margin:0 auto;padding:40px 18px;font:17px/2 -apple-system,"PingFang SC","Microsoft YaHei",sans-serif}a{color:#0a59f7}</style></head><body>
<h1>咪咪面板（Android 版）</h1>
<p><a href="privacy.html">隐私政策</a> · <a href="agreement.html">用户协议</a></p>
<p><a href="privacy_en.html">Privacy Policy</a> · <a href="agreement_en.html">User Agreement</a></p>
</body></html>
H
touch .nojekyll
