import urllib.request
import urllib.parse
import base64
import re
from bs4 import BeautifulSoup

base = 'https://vww.monoschinos2.net'
headers = {
    'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/120.0.0.0 Safari/537.36',
    'Referer': f'{base}/'
}

def test():
    # 1. Popular
    pop_url = f'{base}/animes?p=1'
    req = urllib.request.Request(pop_url, headers=headers)
    soup = BeautifulSoup(urllib.request.urlopen(req, timeout=10).read().decode('utf-8', 'ignore'), 'html.parser')
    items = soup.select('li.ficha_efecto a')
    print(f'1. Popular: found {len(items)} items')
    assert len(items) > 0

    # 2. Latest
    latest_req = urllib.request.Request(base, headers=headers)
    soup_latest = BeautifulSoup(urllib.request.urlopen(latest_req, timeout=10).read().decode('utf-8', 'ignore'), 'html.parser')
    latest_items = soup_latest.select("li.col.mb-4:has(a[href*='/ver/'])")
    print(f'2. Latest: found {len(latest_items)} items')
    assert len(latest_items) > 0

    # 3. Search
    search_url = f'{base}/animes?buscar=bleach&p=1'
    soup_search = BeautifulSoup(urllib.request.urlopen(urllib.request.Request(search_url, headers=headers), timeout=10).read().decode('utf-8', 'ignore'), 'html.parser')
    search_items = soup_search.select('li.ficha_efecto a')
    print(f'3. Search: found {len(search_items)} items for bleach')
    assert len(search_items) > 0
    target_anime_path = search_items[0].get('href').replace('./', '/')
    print(f'   Target anime: {target_anime_path}')

    # 4. Details & Episodes
    anime_url = f'{base}{target_anime_path}'
    soup_anime = BeautifulSoup(urllib.request.urlopen(urllib.request.Request(anime_url, headers=headers), timeout=10).read().decode('utf-8', 'ignore'), 'html.parser')
    h1 = soup_anime.select_one('h1')
    print(f'4. Details Title: {h1.text.strip() if h1 else None}')
    caplist = soup_anime.select_one('section.caplist')
    assert caplist is not None
    data_i = caplist.get('data-i')
    data_u = caplist.get('data-u')
    data_e = caplist.get('data-e')
    print(f'   Caplist: id={data_i}, slug={data_u}, count={data_e}')

    # 5. Fetch episodes via ajax
    post_data = urllib.parse.urlencode({
        'acc': 'episodes',
        'i': data_i,
        'u': data_u,
        'p': '1'
    }).encode('utf-8')
    ajax_req = urllib.request.Request(f'{base}/ajax_pagination', data=post_data, headers={
        'User-Agent': headers['User-Agent'],
        'Referer': anime_url,
        'X-Requested-With': 'XMLHttpRequest'
    })
    ep_html = urllib.request.urlopen(ajax_req, timeout=10).read().decode('utf-8', 'ignore')
    ep_soup = BeautifulSoup(ep_html, 'html.parser')
    ep_links = ep_soup.find_all('a', href=lambda h: h and '/ver/' in h)
    print(f'5. Episode list: found {len(ep_links)} episodes on page 1')
    assert len(ep_links) > 0
    first_ep_url = ep_links[0].get('href')
    print(f'   First episode url: {first_ep_url}')

    # 6. Episode video servers
    ep_req = urllib.request.Request(first_ep_url, headers=headers)
    soup_ep = BeautifulSoup(urllib.request.urlopen(ep_req, timeout=10).read().decode('utf-8', 'ignore'), 'html.parser')
    opt = soup_ep.select_one('.opt[data-encrypt]')
    assert opt is not None
    enc = opt.get('data-encrypt')
    post_opt = urllib.parse.urlencode({'acc': 'opt', 'i': enc}).encode('utf-8')
    opt_req = urllib.request.Request(f'{base}/ajax_pagination', data=post_opt, headers={
        'User-Agent': headers['User-Agent'],
        'Referer': first_ep_url,
        'X-Requested-With': 'XMLHttpRequest'
    })
    opt_html = urllib.request.urlopen(opt_req, timeout=10).read().decode('utf-8', 'ignore')
    opt_soup = BeautifulSoup(opt_html, 'html.parser')
    buttons = opt_soup.select('button[data-player]')
    print(f'6. Video servers: found {len(buttons)} servers')
    assert len(buttons) > 0
    for btn in buttons:
        dec = base64.b64decode(btn.get('data-player')).decode('utf-8', 'ignore')
        print(f'   - {btn.text.strip()}: {dec}')

    print('\nALL MONOSCHINOS TESTS PASSED!')

if __name__ == '__main__':
    test()
