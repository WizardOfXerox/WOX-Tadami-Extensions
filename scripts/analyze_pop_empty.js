const fs = require('fs');

const data = JSON.parse(fs.readFileSync('H:/Ideas/Tadami-Extensions/novel_e2e_report.json', 'utf8'));
const popEmpty = data.filter(x => x.status === 'POPULAR_EMPTY');
console.log(`Analyzing ${popEmpty.length} POPULAR_EMPTY plugins concurrently...`);

async function probeItem(item) {
  if (!item.site) return { id: item.id, site: '', error: 'NO_SITE' };
  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);
    const res = await fetch(item.site, {
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36'
      },
      signal: controller.signal
    });
    clearTimeout(timeout);
    const html = await res.text();
    const isCf = res.status === 403 || res.status === 503 || html.includes('Just a moment...') || html.includes('Cloudflare') || html.includes('challenge-running');
    return { id: item.id, name: item.name, lang: item.lang, site: item.site, status: res.status, cf: isCf, htmlLen: html.length };
  } catch (err) {
    return { id: item.id, name: item.name, lang: item.lang, site: item.site, error: err.message };
  }
}

async function run() {
  const details = await Promise.all(popEmpty.map(probeItem));
  
  const cfList = details.filter(x => x.cf);
  const ok200List = details.filter(x => !x.cf && x.status === 200);
  const redirectList = details.filter(x => !x.cf && x.status >= 300 && x.status < 400);
  const errList = details.filter(x => x.error || (!x.cf && x.status >= 400));

  console.log('Result of probing sites for POPULAR_EMPTY:');
  console.log(`  Cloudflare / 403 blocked: ${cfList.length}`);
  console.log(`  Site returned 200 OK (Selector / HTML changed): ${ok200List.length}`);
  console.log(`  Redirects: ${redirectList.length}`);
  console.log(`  Network / 404 / Error: ${errList.length}`);

  console.log('\n--- PLUGINS RETURNING 200 (FIXABLE SELECTORS / ENDPOINTS) ---');
  for (const d of ok200List) {
    console.log(`  * ${d.id} [${d.lang}]: ${d.site} (html: ${d.htmlLen}b)`);
  }

  console.log('\n--- CLOUDFLARE SITES ---');
  for (const d of cfList) {
    console.log(`  * [CF ${d.status}] ${d.id} [${d.lang}]: ${d.site}`);
  }
}

run().catch(console.error);
