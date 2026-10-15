import urllib.request

headers = {"User-Agent": "Mozilla/5.0"}
url = "https://kisskh.co/scripts.075605d76f7d5f68.js?v=1"
js = urllib.request.urlopen(urllib.request.Request(url, headers=headers)).read().decode('utf-8', errors='ignore')

print("Size of scripts.075605d76f7d5f68.js:", len(js))
print(js[:2000])
