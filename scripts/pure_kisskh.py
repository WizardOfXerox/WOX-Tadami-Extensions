ROUND_KEYS = [
    0x4f6bdaa3, -0x61d07350, 0x7f5e722d, -0x61210cec,
    0x536620a8, -0x32b653e8, -0x4de821cb, 0x2cc92d21,
    -0x73412227, 0x41f771c1, -0xc1f500c, -0x20d67d2b,
    0x2dadde47, 0x6c5aaf86, -0x6045ff8e, 0x409382a7,
    -0x6417db2, -0x6a1bd238, 0xa5e2dba, 0x4acdaf1d,
    0x54c72698, -0x3edcf4b0, -0x3482d916, -0x7e4f7609,
    -0x6c9fb16c, 0x524345c4, -0x66c19cd2, 0x188eead9,
    -0x351884c7, -0x675bc103, 0x19a5dd3, 0x1914b70a,
    -0x4fb1e313, 0x28ea2210, 0x29707fc3, 0x3064c8c9,
    -0x17593e17, -0x3fb31c07, -0x16c363c6, -0x26a7ab0d,
    -0x4b793324, 0x74ca2f25, -0x62094ce1, 0x44aee7ec
]
ROUND_KEYS = [(k & 0xFFFFFFFF) for k in ROUND_KEYS]

t0, t1, t2, t3, sbox = [0]*256, [0]*256, [0]*256, [0]*256, [0]*256
d = [0]*256
for i in range(256):
    d[i] = ((i << 1) & 0xFF) if i < 128 else (((i << 1) ^ 0x11b) & 0xFF)

y = 0
x = 0
for i in range(256):
    s = x ^ (x << 1) ^ (x << 2) ^ (x << 3) ^ (x << 4)
    s = ((s >> 8) ^ (s & 0xFF) ^ 0x63) & 0xFF
    sbox[y] = s
    x_val = d[y]
    x_val_2 = d[d[x_val]]
    t = (0x101 * d[s] ^ 0x1010100 * s) & 0xFFFFFFFF
    t0[y] = ((t << 24) | (t >> 8)) & 0xFFFFFFFF
    t1[y] = ((t << 16) | (t >> 16)) & 0xFFFFFFFF
    t2[y] = ((t << 8) | (t >> 24)) & 0xFFFFFFFF
    t3[y] = t & 0xFFFFFFFF
    if y != 0:
        y = x_val ^ d[d[d[x_val_2 ^ x_val]]]
        x ^= d[d[x]]
    else:
        y = 1
        x = 1

def encrypt_block(words, offset):
    if offset == 0:
        iv = [0x1504af3, 0x56e619cf, 0x2e42bba6, 0x8c3f70f9]
    else:
        iv = words[offset - 4 : offset]
    
    for i in range(4):
        words[offset + i] ^= iv[i]
        
    s0 = words[offset] ^ ROUND_KEYS[0]
    s1 = words[offset + 1] ^ ROUND_KEYS[1]
    s2 = words[offset + 2] ^ ROUND_KEYS[2]
    s3 = words[offset + 3] ^ ROUND_KEYS[3]
    k_idx = 4
    
    for r in range(1, 10):
        n0 = (t0[(s0 >> 24) & 0xFF] ^ t1[(s1 >> 16) & 0xFF] ^ t2[(s2 >> 8) & 0xFF] ^ t3[s3 & 0xFF] ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
        k_idx += 1
        n1 = (t0[(s1 >> 24) & 0xFF] ^ t1[(s2 >> 16) & 0xFF] ^ t2[(s3 >> 8) & 0xFF] ^ t3[s0 & 0xFF] ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
        k_idx += 1
        n2 = (t0[(s2 >> 24) & 0xFF] ^ t1[(s3 >> 16) & 0xFF] ^ t2[(s0 >> 8) & 0xFF] ^ t3[s1 & 0xFF] ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
        k_idx += 1
        n3 = (t0[(s3 >> 24) & 0xFF] ^ t1[(s0 >> 16) & 0xFF] ^ t2[(s1 >> 8) & 0xFF] ^ t3[s2 & 0xFF] ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
        k_idx += 1
        s0, s1, s2, s3 = n0, n1, n2, n3
        
    words[offset] = (((sbox[(s0 >> 24) & 0xFF] << 24) | (sbox[(s1 >> 16) & 0xFF] << 16) | (sbox[(s2 >> 8) & 0xFF] << 8) | sbox[s3 & 0xFF]) ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
    k_idx += 1
    words[offset + 1] = (((sbox[(s1 >> 24) & 0xFF] << 24) | (sbox[(s2 >> 16) & 0xFF] << 16) | (sbox[(s3 >> 8) & 0xFF] << 8) | sbox[s0 & 0xFF]) ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
    k_idx += 1
    words[offset + 2] = (((sbox[(s2 >> 24) & 0xFF] << 24) | (sbox[(s3 >> 16) & 0xFF] << 16) | (sbox[(s0 >> 8) & 0xFF] << 8) | sbox[s1 & 0xFF]) ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF
    k_idx += 1
    words[offset + 3] = (((sbox[(s3 >> 24) & 0xFF] << 24) | (sbox[(s0 >> 16) & 0xFF] << 16) | (sbox[(s1 >> 8) & 0xFF] << 8) | sbox[s2 & 0xFF]) ^ ROUND_KEYS[k_idx]) & 0xFFFFFFFF

def to_int32(x):
    x = int(x) & 0xFFFFFFFF
    if x >= 0x80000000:
        x -= 0x100000000
    return x

def js_hash(s):
    h = 0
    for ch in s:
        # in JS: (h << 5) - h + charCode
        shifted = to_int32(to_int32(h) << 5)
        h = shifted - h + ord(ch)
    return h

def generate_kisskh_key(ep_id, is_sub=False):
    app_ver = "2.8.10"
    guid = "VgV52sWhwvBSf8BsM3BRY9weWiiCbtGp" if is_sub else "62f176f3bb1b5b8e70e39932ad34a0c7"
    platform_ver = 4830201
    app_name = "kisskh"
    
    arr = ['', str(ep_id), None, "mg3c3b04ba", app_ver, guid, str(platform_ver), app_name[:48], app_name.lower()[:48], app_name[:48], app_name, app_name, app_name, '00', '']
    before_str = '|'.join([str(x) if x is not None else '' for x in arr])
    h = js_hash(before_str)
    arr.insert(1, str(h))
    final_str = '|'.join([str(x) if x is not None else '' for x in arr])
    
    pad = 16 - (len(final_str) % 16)
    final_str += chr(pad) * pad
    
    words = [0] * (len(final_str) // 4)
    for i in range(len(final_str)):
        words[i >> 2] |= (ord(final_str[i]) & 0xFF) << (24 - (i % 4) * 8)
        
    for offset in range(0, len(words), 4):
        encrypt_block(words, offset)
        
    hex_parts = []
    for i in range(len(final_str)):
        b = (words[i >> 2] >> (24 - (i % 4) * 8)) & 0xFF
        hex_parts.append(f"{b:02X}")
    return ''.join(hex_parts)

key = generate_kisskh_key(181765, is_sub=False)
sub_key = generate_kisskh_key(181765, is_sub=True)
expected_key = "22CB74C6605F33F0F8DCE693D9BD86DFA3A208137D382A9A1563653BE69FC54C3BD1AF5EB891DCBFC72BA05FC7EC5AE0390094FB9E996335E4E699ABD650DECBBC912F23E171D8DB55AF14920F078C43F3A1923F44AD3120C600BA1AD4FA4EA7CFD2CFEB20E78802A86D9F76DEA24340AD6B4602384B1C215A715971912CAC12"
print("Computed Key: ", key)
print("Expected Key: ", expected_key)
print("Key match?    ", key == expected_key)
