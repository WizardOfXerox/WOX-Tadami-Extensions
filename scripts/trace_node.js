const fs = require('fs');
let code = fs.readFileSync('scripts/common.js', 'utf8');

code = code.replace('_0x5989e7[_0x5acc2f(0x110)](0x1,0x0,_0x70dbf4(_0x5989e7[_0x5acc2f(0x124)](\'|\')));', 
'console.log("Before splice string:", JSON.stringify(_0x5989e7.join("|"))); _0x5989e7.splice(1, 0, _0x70dbf4(_0x5989e7.join("|")));');

eval(code);

const epId = '181765';
const appVer = '2.8.10';
const viGuid = '62f176f3bb1b5b8e70e39932ad34a0c7';
const platformVer = 4830201;
const appName = 'kisskh';

_0x54b991(epId, null, appVer, viGuid, platformVer, appName, appName, appName, appName, appName, appName);
