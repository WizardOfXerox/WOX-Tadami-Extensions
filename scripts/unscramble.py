import urllib.request
import json

url = "https://kisskh.co/scripts.075605d76f7d5f68.js?v=1"
js = urllib.request.urlopen(urllib.request.Request(url, headers={"User-Agent": "Mozilla/5.0"})).read().decode('utf-8', errors='ignore')

def t(l):
    n = 3822606
    i = len(l)
    o = list(l)
    for H in range(i):
        d = (n * (H + 418) + (n % 29269))
        h = (n * (H + 202) + (n % 27203))
        v = d % i
        u = h % i
        o[v], o[u] = o[u], o[v]
        n = (d + h) % 4745886
    return "".join(o)

p1 = js.find("t('vfv0") + 2
p1_end = js.find("'))(t('", p1)
str1 = js[p1+1: p1_end]

p2 = p1_end + 7
p2_end = js.rfind("'))")
str2 = js[p2: p2_end].replace("\\'", "'")

u1 = t(str1)
u2 = t(str2)

with open("scripts/run_unscramble.js", "w", encoding="utf-8") as f:
    f.write(f"""const fs = require('fs');
const decode = function() {{
{u1}
}};
const payload = {json.dumps(u2)};
const res = decode(payload);
fs.writeFileSync('scripts/decrypted_subs_code.js', res);
console.log('Successfully saved to scripts/decrypted_subs_code.js, length:', res.length);
""")
