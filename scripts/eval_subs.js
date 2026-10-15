const https = require('https');

https.get('https://kisskh.co/scripts.075605d76f7d5f68.js?v=1', (res) => {
    let js = '';
    res.on('data', c => js += c);
    res.on('end', () => {
        global.CryptoJS = {
            enc: { Utf8: { parse: (s) => Buffer.from(s, 'utf8') } },
            AES: {
                decrypt: (ciphertext, key, cfg) => {
                    console.log('CryptoJS.AES.decrypt called! key:', key.toString(), 'iv:', cfg.iv.toString());
                    return { toString: (enc) => 'mock_decrypted_text' };
                }
            }
        };
        try {
            eval(js);
            console.log('typeof _a2:', typeof _a2);
            console.log('typeof _a3:', typeof _a3);
            if (typeof _a2 === 'function') console.log('_a2 code:', _a2.toString());
            if (typeof _a3 === 'function') console.log('_a3 code:', _a3.toString());
            // Test call _a2
            _a2('some_ciphertext', global.CryptoJS);
        } catch (e) {
            console.log('Eval error:', e);
        }
    });
});
