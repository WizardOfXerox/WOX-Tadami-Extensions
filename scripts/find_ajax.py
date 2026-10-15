import urllib.request, ssl, re

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
req = urllib.request.Request('https://animesogo.to/assets/js/main.js?1.2', headers=headers)
code = urllib.request.urlopen(req, context=ctx).read().decode('utf-8')

matches = set(re.findall(r'[\'"](ajax/[^\'"]+)[\'"]', code))
print("Found direct ajax endpoints:", len(matches))
for m in sorted(matches):
    print(" ", m)

# Also check for concatenation like 'ajax/' + ...
cat_matches = re.findall(r'[\'"]ajax/([^\'"]+)[\'"]\s*\+\s*([^\,;\)]+)', code)
print("\nConcatenated ajax endpoints:", len(cat_matches))
for prefix, rest in cat_matches[:20]:
    print(f"  ajax/{prefix} + {rest[:30]}")
