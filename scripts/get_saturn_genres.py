from bs4 import BeautifulSoup
import urllib.request, ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36'}
req = urllib.request.Request('https://www.animesaturn.net/filter', headers=headers)
html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8', errors='ignore')
soup = BeautifulSoup(html, 'html.parser')

print("Categories:")
for inp in soup.select('input[name="categories[]"]'):
    parent = inp.parent
    print(f'Genre("{inp.get("value")}", "{parent.get_text(strip=True)}"),')
