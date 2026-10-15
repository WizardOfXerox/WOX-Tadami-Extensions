const fs = require('fs');
const path = require('path');
const Module = require('module');

const ENGINE_NODE_MODULES = path.resolve('H:/Ideas/Tadami-PC/desktop-novel-engine/node_modules');

const mockModules = {
  '@libs/fetch': {
    fetchApi: async (url, options = {}) => {
      const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,image/apng,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
        ...(options.headers || {})
      };
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15000);
      try {
        const res = await fetch(url, { ...options, headers, signal: controller.signal });
        return res;
      } finally {
        clearTimeout(timeout);
      }
    },
    fetchText: async (url, options = {}) => {
      const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Accept': 'text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8',
        'Accept-Language': 'en-US,en;q=0.9',
        ...(options.headers || {})
      };
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15000);
      try {
        const res = await fetch(url, { ...options, headers, signal: controller.signal });
        return await res.text();
      } finally {
        clearTimeout(timeout);
      }
    },
    fetchFile: async (url, options = {}) => {
      const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        ...(options.headers || {})
      };
      const controller = new AbortController();
      const timeout = setTimeout(() => controller.abort(), 15000);
      try {
        const res = await fetch(url, { ...options, headers, signal: controller.signal });
        const buf = await res.arrayBuffer();
        return Buffer.from(buf).toString('base64');
      } finally {
        clearTimeout(timeout);
      }
    }
  },
  '@libs/storage': {
    storage: { get: () => null, set: () => {}, delete: () => {}, clearAll: () => {}, getAllKeys: () => [] },
    localStorage: { get: () => null, getItem: () => null, set: () => {}, setItem: () => {}, delete: () => {}, remove: () => {}, removeItem: () => {}, clear: () => {}, clearAll: () => {}, keys: () => [], getAllKeys: () => [] },
    sessionStorage: { get: () => null, getItem: () => null, set: () => {}, setItem: () => {}, delete: () => {}, remove: () => {}, removeItem: () => {}, clear: () => {}, clearAll: () => {}, keys: () => [], getAllKeys: () => [] }
  },
  '@libs/filterInputs': {
    FilterTypes: {
      TextInput: 'Text',
      Picker: 'Picker',
      CheckboxGroup: 'Checkbox',
      Switch: 'Switch',
      ExcludableCheckboxGroup: 'XCheckbox'
    }
  },
  '@libs/novelStatus': {
    NovelStatus: {
      Unknown: 'Unknown',
      Ongoing: 'Ongoing',
      Completed: 'Completed',
      Licensed: 'Licensed',
      PublishingFinished: 'Publishing Finished',
      Cancelled: 'Cancelled',
      OnHiatus: 'On Hiatus'
    }
  },
  '@libs/isAbsoluteUrl': {
    isUrlAbsolute: (url) => /^(?:[a-z]+:)?\/\//i.test(url)
  },
  '@libs/defaultCover': {
    defaultCover: 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/master/public/static/coverNotAvailable.webp'
  },
  '@/types/constants': {
    defaultCover: 'https://raw.githubusercontent.com/lnreader/lnreader-plugins/master/public/static/coverNotAvailable.webp'
  },
  '@libs/aes': {
    aesGcmDecrypt: () => ''
  },
  '@libs/utils': {
    sleep: (ms) => new Promise((res) => setTimeout(res, ms))
  }
};

const originalResolveFilename = Module._resolveFilename;
Module._resolveFilename = function (request, parent, isMain, options) {
  if (mockModules[request]) {
    return request;
  }
  if (!request.startsWith('.') && !request.startsWith('/') && !request.includes(':')) {
    try {
      return require.resolve(request, { paths: [ENGINE_NODE_MODULES] });
    } catch (e) {}
  }
  return originalResolveFilename.apply(this, arguments);
};

for (const [k, v] of Object.entries(mockModules)) {
  require.cache[k] = { exports: v, loaded: true };
}

async function testThumbnail(coverUrl, siteUrl) {
  if (!coverUrl) return { ok: false, reason: 'NO_COVER_URL' };
  let fullUrl = coverUrl;
  if (!/^(?:[a-z]+:)?\/\//i.test(coverUrl)) {
    if (coverUrl.startsWith('//')) {
      fullUrl = 'https:' + coverUrl;
    } else if (coverUrl.startsWith('/')) {
      fullUrl = new URL(coverUrl, siteUrl).href;
    } else {
      fullUrl = new URL(coverUrl, siteUrl).href;
    }
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10000);
    const res = await fetch(fullUrl, {
      method: 'GET',
      headers: {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/124.0.0.0 Safari/537.36',
        'Referer': siteUrl || 'https://google.com'
      },
      signal: controller.signal
    });
    clearTimeout(timeout);
    const status = res.status;
    const contentType = res.headers.get('content-type') || '';
    const buf = await res.arrayBuffer();
    const size = buf.byteLength;

    if (status === 200 && size > 500) {
      return { ok: true, status, contentType, size, url: fullUrl };
    } else {
      return { ok: false, status, contentType, size, url: fullUrl, reason: `HTTP_${status}_SIZE_${size}` };
    }
  } catch (err) {
    return { ok: false, error: err.message, url: fullUrl, reason: err.message };
  }
}

async function testSinglePlugin(filePath) {
  console.log(`\n======================================================`);
  console.log(`Testing Novel Plugin: ${filePath}`);
  console.log(`======================================================`);

  const mod = require(path.resolve(filePath));
  const plugin = mod.default || mod;

  console.log(`[PLUGIN] ID: ${plugin.id} | Name: ${plugin.name} | Site: ${plugin.site} | Version: ${plugin.version}`);

  // Step 1: popularNovels
  console.log(`[TEST 1] Calling popularNovels(1)...`);
  let novels = [];
  try {
    novels = await plugin.popularNovels(1, { filters: plugin.filters || {}, showLatestNovels: false });
  } catch (err1) {
    try {
      novels = await plugin.popularNovels(1, { filters: plugin.filters || {}, showLatestNovels: true });
    } catch (err2) {
      console.log(`[TEST 1 FAIL] popularNovels threw error: ${err1.message}`);
      return { success: false, step: 'popularNovels', error: err1.message };
    }
  }

  if (!Array.isArray(novels) || novels.length === 0) {
    console.log(`[TEST 1 FAIL] popularNovels returned empty list`);
    return { success: false, step: 'popularNovels', error: 'EMPTY_LIST' };
  }
  console.log(`[TEST 1 PASS] Retrieved ${novels.length} novels. First: "${novels[0].name}" (path: ${novels[0].path})`);

  // Step 2: Thumbnail Loading
  const firstNovel = novels[0];
  console.log(`[TEST 2] Testing Thumbnail image loading: ${firstNovel.cover}`);
  const thumbResult = await testThumbnail(firstNovel.cover, plugin.site);
  if (thumbResult.ok) {
    console.log(`[TEST 2 PASS] Thumbnail loaded: status=${thumbResult.status}, type=${thumbResult.contentType}, bytes=${thumbResult.size}`);
  } else {
    console.log(`[TEST 2 WARN/FAIL] Thumbnail failed: ${thumbResult.reason} (${thumbResult.error || ''})`);
  }

  // Step 3: parseNovel (Details & Chapters)
  console.log(`[TEST 3] Calling parseNovel("${firstNovel.path}")...`);
  let novelDetails = null;
  try {
    novelDetails = await plugin.parseNovel(firstNovel.path);
  } catch (err) {
    console.log(`[TEST 3 FAIL] parseNovel threw error: ${err.message}`);
    return { success: false, step: 'parseNovel', error: err.message, thumbnail: thumbResult };
  }

  if (!novelDetails || !Array.isArray(novelDetails.chapters) || novelDetails.chapters.length === 0) {
    console.log(`[TEST 3 FAIL] parseNovel returned no chapters! (chapters: ${novelDetails ? (novelDetails.chapters || []).length : 0})`);
    return { success: false, step: 'parseNovel_no_chapters', details: novelDetails, thumbnail: thumbResult };
  }
  console.log(`[TEST 3 PASS] Retrieved novel details: "${novelDetails.name}" with ${novelDetails.chapters.length} chapters.`);
  const firstChapter = novelDetails.chapters[0];
  console.log(`   First Chapter: "${firstChapter.name}" (path: ${firstChapter.path})`);

  // Step 4: parseChapter (Content Loading)
  console.log(`[TEST 4] Calling parseChapter("${firstChapter.path}")...`);
  let chapterResult = null;
  try {
    chapterResult = await plugin.parseChapter(firstChapter.path);
  } catch (err) {
    console.log(`[TEST 4 FAIL] parseChapter threw error: ${err.message}`);
    return { success: false, step: 'parseChapter', error: err.message, thumbnail: thumbResult };
  }

  let textContent = '';
  if (typeof chapterResult === 'string') {
    textContent = chapterResult;
  } else if (chapterResult && typeof chapterResult.chapterText === 'string') {
    textContent = chapterResult.chapterText;
  } else if (chapterResult && typeof chapterResult.content === 'string') {
    textContent = chapterResult.content;
  } else if (chapterResult && typeof chapterResult.text === 'string') {
    textContent = chapterResult.text;
  }

  let cleanText = textContent.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  const hasImages = /<img[^>]+src=/i.test(textContent);

  if (cleanText.length < 50 && !hasImages && novelDetails.chapters.length > 1) {
    console.log(`[TEST 4 NOTICE] Chapter 1 was short (${cleanText.length} chars). Testing Chapter 2: "${novelDetails.chapters[1].name}"...`);
    const ch2 = novelDetails.chapters[1];
    try {
      const ch2Result = await plugin.parseChapter(ch2.path);
      let t2 = typeof ch2Result === 'string' ? ch2Result : (ch2Result?.chapterText || ch2Result?.content || ch2Result?.text || '');
      let c2 = t2.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
      if (c2.length >= 50 || /<img[^>]+src=/i.test(t2)) {
        textContent = t2;
        cleanText = c2;
      }
    } catch (e2) {}
  }

  if (cleanText.length < 50 && !/<img[^>]+src=/i.test(textContent)) {
    console.log(`[TEST 4 FAIL] Chapter content is too short or empty! Length: ${cleanText.length} chars`);
    return { success: false, step: 'parseChapter_content_empty', length: cleanText.length, thumbnail: thumbResult };
  }

  console.log(`[TEST 4 PASS] Chapter content loaded successfully! Raw length: ${textContent.length}, Text length: ${cleanText.length} chars.`);
  console.log(`   Sample text: "${cleanText.substring(0, 150)}..."`);
  console.log(`\n>>> [FULL E2E SUCCESS] Plugin ${plugin.id} is 100% WORKING! <<<\n`);
  return { success: true, novelCount: novels.length, chapterCount: novelDetails.chapters.length, contentLength: cleanText.length, thumbnail: thumbResult };
}

const target = process.argv[2] || 'H:/Ideas/Tadami-Extensions/apks/novel/.js/src/plugins/english/royalroad.js';
testSinglePlugin(target).catch(console.error);
