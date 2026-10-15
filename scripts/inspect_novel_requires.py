import os
import re
import json
from collections import Counter

plugins_dir = r"H:\Ideas\Tadami-Extensions\apks\novel\.js\src\plugins"
requires = Counter()
plugin_files = []

for root, dirs, files in os.walk(plugins_dir):
    for f in files:
        if f.endswith(".js"):
            fpath = os.path.join(root, f)
            plugin_files.append(fpath)
            with open(fpath, "r", encoding="utf-8", errors="ignore") as fp:
                code = fp.read()
                matches = re.findall(r'require\(["\']([^"\']+)["\']\)', code)
                for m in matches:
                    requires[m] += 1

print(f"Total plugin JS files found: {len(plugin_files)}")
print("\nMost common required modules:")
for mod, count in requires.most_common(20):
    print(f"  {mod:25}: {count}")
