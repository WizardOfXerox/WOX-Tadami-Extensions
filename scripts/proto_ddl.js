const cheerio = require('cheerio');

async function testDDL() {
  const site = 'https://www.divinedaolibrary.com/';
  console.log('1. Testing popularNovels...');
  const res = await fetch(site + 'novels/', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const html = await res.text();
  const $ = cheerio.load(html);
  
  const novels = [];
  $('a[href*="/story/"]').each(function() {
    const href = $(this).attr('href');
    const name = $(this).text().trim();
    if (href && name && !novels.some(n => n.name === name)) {
      const path = href.replace(site, '').replace(/(^\/+|\/+$)/g, '');
      novels.push({ name, path, cover: 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/master/public/static/coverNotAvailable.webp' });
    }
  });

  console.log(`Found ${novels.length} novels on DDL!`);
  console.log('First 3:', novels.slice(0, 3));

  console.log('\n2. Testing parseNovel for:', novels[0].name, novels[0].path);
  const nRes = await fetch(site + novels[0].path + '/', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const nHtml = await nRes.text();
  const $n = cheerio.load(nHtml);

  const title = $n('h1.story__identity-title, h1').first().text().trim();
  const cover = $n('figure.story__thumbnail img, .story__thumbnail img').first().attr('src') || $n('figure.story__thumbnail a').first().attr('href');
  const author = $n('div.story__identity-meta').text().split('|')[0].replace('Author:', '').replace('by', '').trim();
  const summary = $n('section.story__summary').text().trim();

  const chapters = [];
  $n('li.chapter-group__list-item a, ul.chapter-group__list a').each(function(idx) {
    const cHref = $n(this).attr('href');
    const cName = $n(this).text().trim();
    if (cHref && cName) {
      const cPath = cHref.replace(site, '').replace(/(^\/+|\/+$)/g, '');
      chapters.push({ name: cName, path: cPath, chapterNumber: idx + 1 });
    }
  });

  console.log(`Novel details: Title="${title}", Author="${author}", Cover="${cover}", Chapters=${chapters.length}`);
  console.log('First chapter:', chapters[0]);

  console.log('\n3. Testing parseChapter for:', chapters[0].name, chapters[0].path);
  const cRes = await fetch(site + chapters[0].path + '/', { headers: { 'User-Agent': 'Mozilla/5.0' } });
  const cHtml = await cRes.text();
  const $c = cheerio.load(cHtml);

  $c('script, style, .sharedaddy, noscript').remove();
  const content = $c('section#chapter-content > div, section#chapter-content, #chapter-content').html() || '';
  const clean = content.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  console.log(`Chapter content length: ${clean.length} chars`);
  console.log('Sample:', clean.substring(0, 150));
}

testDDL().catch(console.error);
