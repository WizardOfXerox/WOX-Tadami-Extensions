from bs4 import BeautifulSoup
import urllib.request
import ssl

ctx = ssl.create_default_context()
ctx.check_hostname = False
ctx.verify_mode = ssl.CERT_NONE
headers = {'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36'}
url = 'https://www.animesaturn.net/anime/i-m-looking-for-a-zombie-EZBpn'
req = urllib.request.Request(url, headers=headers)
html = urllib.request.urlopen(req, context=ctx).read().decode('utf-8', errors='ignore')
soup = BeautifulSoup(html, 'html.parser')

print("Synopsis / Paragraphs:")
for p in soup.find_all('p'):
    print('P:', p.get('class'), '-->', p.get_text(strip=True)[:100])

print("\nEpisode container classes:")
for ep in soup.select('a[href*="/episode/"]'):
    parent = ep.parent
    print('EP a class:', ep.get('class'), '| parent:', parent.name, parent.get('class'), '| href:', ep.get('href'), '| text:', ep.get_text(strip=True))

status_elements = soup.find_all(string=lambda t: t and any(s in t.lower() for s in ['stato', 'status', 'in corso', 'finito', 'completo']))
for se in status_elements:
    print('Status context:', se.parent)
