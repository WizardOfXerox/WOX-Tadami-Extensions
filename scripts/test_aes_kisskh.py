import struct
from Crypto.Cipher import AES

# Key from _0x458390[0..3]:
words = [0x4f6bdaa3, 0x9e2f8cb0, 0x7f5e722d, 0x9edf0314]
key = struct.pack(">4I", *words)
print("AES Key hex:", key.hex())

# IV:
iv_words = [0x01504af3, 0x56e619cf, 0x2e42bba6, 0x8c3f70f9]
iv = struct.pack(">4I", *iv_words)
print("AES IV hex:", iv.hex())

# Let's check what input string was constructed in common.js:
# const _0x5989e7=['', _0x3be93d, _0x3e48ea, "mg3c3b04ba", _0xbf2c16, _0x576527, _0x454b79, _0x12af79(_0x361753), _0x12af79(_0x3cdffa.toLowerCase()), _0x12af79(_0x2fa7de), _0xdaf7bd, _0x59e242, _0x5674ce, '00', ''];
# _0x5989e7.splice(0x1, 0x0, _0x70dbf4(_0x5989e7.join('|')));
# const _0x278f64 = _0x29e11d(_0x5989e7.join('|'))
# PKCS7 padding of that string!

def hash_code(s):
    h = 0
    for ch in s:
        h = (((h << 5) - h) + ord(ch)) & 0xFFFFFFFF
        if h >= 0x80000000:
            h -= 0x100000000
    return h

epId = '181765'
appVer = '2.8.10'
viGuid = '62f176f3bb1b5b8e70e39932ad34a0c7'
platformVer = 4830201
appName = 'kisskh'

arr = ['', epId, None, "mg3c3b04ba", appVer, viGuid, platformVer, appName[:0x30], appName.lower()[:0x30], appName[:0x30], appName, appName, appName, '00', '']
# join with '|' (None in JS array becomes '')
arr_str = '|'.join([str(x) if x is not None else '' for x in arr])
h = hash_code(arr_str)
print("Hash code:", h)
arr.insert(1, h)
final_str = '|'.join([str(x) if x is not None else '' for x in arr])
print("Final string to encrypt:", repr(final_str))

# PKCS7 pad to 16 bytes:
pad_len = 16 - (len(final_str) % 16)
padded = final_str.encode('latin1') + bytes([pad_len] * pad_len)

cipher = AES.new(key, AES.MODE_CBC, iv)
ciphertext = cipher.encrypt(padded)
print("Encrypted ciphertext hex:", ciphertext.hex().upper())
