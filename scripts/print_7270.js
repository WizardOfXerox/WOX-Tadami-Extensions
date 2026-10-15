const fs = require('fs');
let code = fs.readFileSync('scripts/decrypted_subs_code.js', 'utf8');
code += '\nconsole.log(JSON.stringify(_$_7270, null, 2));';
eval(code);
