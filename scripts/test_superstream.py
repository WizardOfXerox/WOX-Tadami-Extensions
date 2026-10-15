import urllib.request, urllib.parse, base64, hashlib
from cryptography.hazmat.primitives.ciphers import Cipher, algorithms, modes

iv = base64.b64decode('d0VpcGhUbiE=')
key = base64.b64decode('MTIzZDZjZWRmNjI2ZHk1NDIzM2FhMXc2')[:24]
app_key = base64.b64decode('bW92aWVib3g=').decode()

query = '{"childmode":0,"app_version":"11.5","appid":"com.tdo.showbox","module":"Home_index","channel":"Website","page":1,"lang":"en","type":"all"}'

pad_len = 8 - (len(query.encode()) % 8)
padded = query.encode() + bytes([pad_len] * pad_len)

cipher = Cipher(algorithms.TripleDES(key), modes.CBC(iv))
enc = cipher.encryptor()
ciphertext = enc.update(padded) + enc.finalize()
enc_b64 = base64.b64encode(ciphertext).decode()

def md5(s):
    return hashlib.md5(s.encode() if isinstance(s, str) else s).hexdigest()

app_key_hash = md5(app_key)
verify = md5(md5(app_key) + key.decode('latin1') + enc_b64)

new_body = f'{{"app_key":"{app_key_hash}","verify":"{verify}","encrypt_data":"{enc_b64}"}}'
base64_body = base64.b64encode(new_body.encode()).decode()

data = urllib.parse.urlencode({
    'data': base64_body,
    'appid': '27',
    'platform': 'android',
    'version': '129',
    'medium': 'Website&token1234567890abcdef1234567890abcdef'
}).encode()

req = urllib.request.Request(
    'https://showbox.shegu.net/api/api_client/index/',
    data=data,
    headers={
        'Platform': 'android',
        'Accept': 'charset=utf-8',
        'User-Agent': 'okhttp/3.12.1'
    }
)
try:
    with urllib.request.urlopen(req, timeout=10) as resp:
        res = resp.read().decode()
        print('SuperStream API Status:', resp.status)
        print('SuperStream API Body (first 300):', res[:300])
except Exception as e:
    print('SuperStream API Failed:', e)
