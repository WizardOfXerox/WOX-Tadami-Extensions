import re

with open("scripts/common.js", "r", encoding="utf-8") as f:
    code = f.read()

mapping = {
    '0x10a': '"39aXORCP"',
    '0x10b': '"toUpperCase"',
    '0x10c': '"87280owbbkp"',
    '0x10d': '"6AzawBg"',
    '0x10e': '"fromCharCode"',
    '0x10f': '"toString"',
    '0x110': '"splice"',
    '0x111': '"mg3c3b04ba"',
    '0x112': '"substr"',
    '0x113': '"9634ttJadP"',
    '0x114': '"length"',
    '0x115': '"referrer"',
    '0x116': '"499416bEetdB"',
    '0x117': '"platform"',
    '0x118': '"appCodeName"',
    '0x119': '"navigator"',
    '0x11a': '"359664FzBQCJ"',
    '0x11b': '"12yJxJzi"',
    '0x11c': '"userAgent"',
    '0x11d': '"1065160icGOUk"',
    '0x11e': '"toLowerCase"',
    '0x11f': '"document"',
    '0x120': '"push"',
    '0x121': '"charCodeAt"',
    '0x122': '"undefined"',
    '0x123': '"178276lBUtOp"',
    '0x124': '"join"',
    '0x125': '"1766601DKvkfN"'
}

for k, v in mapping.items():
    code = re.sub(r'_0x[a-f0-9]+\(' + k + r'\)', v, code)

# also replace ['length'] with .length etc.
for v in mapping.values():
    clean = v.strip('"')
    code = code.replace(f"['{clean}']", f".{clean}")
    code = code.replace(f'["{clean}"]', f".{clean}")

with open("scripts/common_clean.js", "w", encoding="utf-8") as f:
    f.write(code)

print("Saved clean code. Length:", len(code))
