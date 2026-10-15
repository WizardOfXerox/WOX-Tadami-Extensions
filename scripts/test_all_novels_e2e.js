const fs = require('fs');
const path = require('path');
const Module = require('module');

const ENGINE_NODE_MODULES = path.resolve('H:/Ideas/Tadami-PC/desktop-novel-engine/node_modules');

let protobuf = null;
try {
  protobuf = require(path.join(ENGINE_NODE_MODULES, 'protobufjs'));
} catch (e) {}

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
      const timeout = setTimeout(() => controller.abort(), 12000);
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
      const timeout = setTimeout(() => controller.abort(), 12000);
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
      const timeout = setTimeout(() => controller.abort(), 12000);
      try {
        const res = await fetch(url, { ...options, headers, signal: controller.signal });
        const buf = await res.arrayBuffer();
        return Buffer.from(buf).toString('base64');
      } finally {
        clearTimeout(timeout);
      }
    },
    fetchProto: async (config, url, options = {}) => {
      if (!protobuf) throw new Error('protobufjs not available');
      const root = protobuf.parse(config.proto).root;
      const RequestType = root.lookupType(config.requestType);
      const ResponseType = root.lookupType(config.responseType);
      const errMsg = RequestType.verify(config.requestData);
      if (errMsg) throw new Error(errMsg);
      const message = RequestType.create(config.requestData);
      const buffer = RequestType.encode(message).finish();
      
      // gRPC-web framing: 1 byte flag + 4 bytes length
      const framed = Buffer.alloc(5 + buffer.length);
      framed.writeUInt8(0, 0);
      framed.writeUInt32BE(buffer.length, 1);
      buffer.copy(framed, 5);

      const headers = {
        'User-Agent': 'Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36',
        'Content-Type': 'application/grpc-web+proto',
        'X-Grpc-Web': '1',
        ...(options.headers || {})
      };
      const res = await fetch(url, { method: 'POST', headers, body: framed });
      const respBuf = Buffer.from(await res.arrayBuffer());
      if (respBuf.length < 5) throw new Error('Invalid gRPC-web response');
      const msgLen = respBuf.readUInt32BE(1);
      const msgBuf = respBuf.subarray(5, 5 + msgLen);
      return ResponseType.toObject(ResponseType.decode(msgBuf), { defaults: true });
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
  if (!coverUrl) return { ok: false, reason: 'NO_COVER' };
  let fullUrl = coverUrl;
  if (!/^(?:[a-z]+:)?\/\//i.test(coverUrl)) {
    if (coverUrl.startsWith('//')) {
      fullUrl = 'https:' + coverUrl;
    } else {
      try {
        fullUrl = new URL(coverUrl, siteUrl).href;
      } catch (e) {
        return { ok: false, reason: 'INVALID_URL' };
      }
    }
  }

  try {
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 6000);
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

    if (status === 200 && size > 200) {
      return { ok: true, status, contentType, size, url: fullUrl };
    } else {
      return { ok: false, status, contentType, size, url: fullUrl, reason: `HTTP_${status}_SIZE_${size}` };
    }
  } catch (err) {
    return { ok: false, error: err.message, url: fullUrl, reason: err.message };
  }
}

async function testPluginE2E(item, pluginBaseDir) {
  const match = item.url.match(/src\/plugins\/([^/]+)\/(.+)$/);
  if (!match) {
    return { id: item.id, name: item.name, lang: item.lang, status: 'URL_PARSE_ERROR' };
  }

  const langDir = match[1];
  const fileName = decodeURIComponent(match[2]);
  const localFilePath = path.join(pluginBaseDir, langDir, fileName);

  if (!fs.existsSync(localFilePath)) {
    return { id: item.id, name: item.name, lang: item.lang, status: 'FILE_NOT_FOUND', localFilePath };
  }

  let mod;
  try {
    delete require.cache[require.resolve(localFilePath)];
    mod = require(localFilePath);
  } catch (err) {
    return { id: item.id, name: item.name, lang: item.lang, status: 'SYNTAX_OR_REQUIRE_ERROR', error: err.message };
  }

  const plugin = mod.default || mod;
  if (!plugin || typeof plugin.popularNovels !== 'function') {
    return { id: item.id, name: item.name, lang: item.lang, status: 'INTERFACE_MISMATCH' };
  }

  // 1. Popular Novels Listing
  let novels = null;
  let popularError = null;
  try {
    novels = await plugin.popularNovels(1, { filters: plugin.filters || {}, showLatestNovels: false });
  } catch (e1) {
    try {
      novels = await plugin.popularNovels(1, { filters: plugin.filters || {}, showLatestNovels: true });
    } catch (e2) {
      popularError = e1.message || e2.message;
    }
  }

  if (popularError) {
    const isCf = popularError.includes('403') || popularError.includes('503') || popularError.includes('Cloudflare') || popularError.includes('webview');
    const isDns = popularError.includes('ENOTFOUND') || popularError.includes('EAI_AGAIN') || popularError.includes('getaddrinfo');
    const isTimeout = popularError.includes('abort') || popularError.includes('timeout') || popularError.includes('UND_ERR_CONNECT_TIMEOUT');
    return {
      id: item.id,
      name: item.name,
      lang: item.lang,
      site: plugin.site,
      status: isCf ? 'CLOUDFLARE_BLOCKED' : (isDns ? 'DEAD_DOMAIN' : (isTimeout ? 'CONNECT_TIMEOUT' : 'POPULAR_FETCH_ERROR')),
      error: popularError
    };
  }

  if (!Array.isArray(novels) || novels.length === 0) {
    return { id: item.id, name: item.name, lang: item.lang, site: plugin.site, status: 'POPULAR_EMPTY' };
  }

  const firstNovel = novels[0];

  // 2. Thumbnail Check
  const thumbResult = await testThumbnail(firstNovel.cover, plugin.site);

  // 3. parseNovel (Details & Chapters)
  let novelDetails = null;
  try {
    novelDetails = await plugin.parseNovel(firstNovel.path);
  } catch (err) {
    return {
      id: item.id,
      name: item.name,
      lang: item.lang,
      site: plugin.site,
      status: 'PARSE_NOVEL_ERROR',
      error: err.message,
      novelCount: novels.length,
      thumbnail: thumbResult
    };
  }

  if (!novelDetails || !Array.isArray(novelDetails.chapters) || novelDetails.chapters.length === 0) {
    return {
      id: item.id,
      name: item.name,
      lang: item.lang,
      site: plugin.site,
      status: 'NO_CHAPTERS_FOUND',
      novelCount: novels.length,
      thumbnail: thumbResult
    };
  }

  const firstChapter = novelDetails.chapters[0];

  // 4. parseChapter (Content Extraction)
  let chapterResult = null;
  try {
    chapterResult = await plugin.parseChapter(firstChapter.path);
  } catch (err) {
    return {
      id: item.id,
      name: item.name,
      lang: item.lang,
      site: plugin.site,
      status: 'PARSE_CHAPTER_ERROR',
      error: err.message,
      novelCount: novels.length,
      chapterCount: novelDetails.chapters.length,
      thumbnail: thumbResult
    };
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

  const cleanText = textContent.replace(/<[^>]+>/g, ' ').replace(/\s+/g, ' ').trim();
  if (cleanText.length < 50) {
    return {
      id: item.id,
      name: item.name,
      lang: item.lang,
      site: plugin.site,
      status: 'CHAPTER_CONTENT_EMPTY',
      novelCount: novels.length,
      chapterCount: novelDetails.chapters.length,
      contentLength: cleanText.length,
      thumbnail: thumbResult
    };
  }

  return {
    id: item.id,
    name: item.name,
    lang: item.lang,
    site: plugin.site,
    status: 'PASS',
    novelCount: novels.length,
    chapterCount: novelDetails.chapters.length,
    contentLength: cleanText.length,
    sampleSnippet: cleanText.substring(0, 120),
    thumbnail: thumbResult
  };
}

async function runAll() {
  const indexJsonPath = 'H:/Ideas/Tadami-Extensions/apks/novel/plugins.json';
  const pluginBaseDir = 'H:/Ideas/Tadami-Extensions/apks/novel/.js/src/plugins';
  const plugins = JSON.parse(fs.readFileSync(indexJsonPath, 'utf8'));

  console.log(`======================================================================`);
  console.log(`  Tadami Full-Stack Novel Plugin E2E Verification Suite`);
  console.log(`  Total Plugins in Manifest: ${plugins.length}`);
  console.log(`  Tests: popularNovels -> thumbnail -> parseNovel -> parseChapter`);
  console.log(`======================================================================\n`);

  const results = [];
  const CONCURRENCY = 6;
  let cursor = 0;
  let passedCount = 0;
  let cfCount = 0;
  let deadDomainCount = 0;
  let failedCount = 0;

  async function worker(workerId) {
    while (cursor < plugins.length) {
      const idx = cursor++;
      const item = plugins[idx];
      const prefix = `[${idx + 1}/${plugins.length}]`;
      
      let res;
      try {
        res = await Promise.race([
          testPluginE2E(item, pluginBaseDir),
          new Promise((_, reject) => setTimeout(() => reject(new Error('TIMEOUT_15S')), 15000))
        ]);
      } catch (err) {
        res = {
          id: item.id,
          name: item.name,
          lang: item.lang,
          status: 'TIMEOUT',
          error: err.message
        };
      }
      results.push(res);

      if (res.status === 'PASS') {
        passedCount++;
        const thumbOk = res.thumbnail && res.thumbnail.ok ? 'Thumb:OK' : 'Thumb:FAIL';
        console.log(`${prefix} PASS [${res.lang}] ${res.name} (Novels: ${res.novelCount}, Chs: ${res.chapterCount}, ChLen: ${res.contentLength}, ${thumbOk})`);
      } else if (res.status === 'CLOUDFLARE_BLOCKED') {
        cfCount++;
        console.log(`${prefix} CF_BLOCK [${res.lang}] ${res.name} -> ${res.error}`);
      } else if (res.status === 'DEAD_DOMAIN') {
        deadDomainCount++;
        console.log(`${prefix} DEAD_DOMAIN [${res.lang}] ${res.name} -> ${res.site || res.error}`);
      } else {
        failedCount++;
        console.log(`${prefix} FAIL (${res.status}) [${res.lang}] ${res.name} -> ${res.error || ''}`);
      }
    }
  }

  const workers = [];
  for (let w = 0; w < CONCURRENCY; w++) {
    workers.push(worker(w));
  }
  await Promise.all(workers);

  console.log(`\n======================================================================`);
  console.log(`  Novel E2E Verification Summary:`);
  console.log(`    Total Tested:        ${plugins.length}`);
  console.log(`    100% Fully Working:  ${passedCount} (${((passedCount / plugins.length) * 100).toFixed(1)}%)`);
  console.log(`    Cloudflare Guarded:  ${cfCount}`);
  console.log(`    Dead Domains:        ${deadDomainCount}`);
  console.log(`    Other Failures:      ${failedCount}`);
  console.log(`======================================================================\n`);

  const reportPath = 'H:/Ideas/Tadami-Extensions/novel_e2e_report.json';
  fs.writeFileSync(reportPath, JSON.stringify(results, null, 2), 'utf8');
  console.log(`Detailed report written to: ${reportPath}`);
}

runAll().catch(console.error);
