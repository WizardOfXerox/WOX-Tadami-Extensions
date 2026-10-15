const fs = require('fs');
const path = require('path');

function walk(dir) {
  let results = [];
  const list = fs.readdirSync(dir);
  list.forEach(file => {
    const full = path.join(dir, file);
    const stat = fs.statSync(full);
    if (stat && stat.isDirectory()) {
      results = results.concat(walk(full));
    } else if (file.endsWith('.js')) {
      results.push(full);
    }
  });
  return results;
}

const files = walk('H:/Ideas/Tadami-Extensions/apks/novel/.js/src/plugins');
const reqs = new Set();
const regex = /require\(['"]([^'"]+)['"]\)/g;

for (const file of files) {
  const content = fs.readFileSync(file, 'utf8');
  let match;
  while ((match = regex.exec(content)) !== null) {
    reqs.add(match[1]);
  }
}

console.log('Total files scanned:', files.length);
console.log('Total unique require targets:', reqs.size);
for (const r of Array.from(reqs).sort()) {
  console.log(' -', r);
}
