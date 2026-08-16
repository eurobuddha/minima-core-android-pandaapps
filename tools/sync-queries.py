#!/usr/bin/env python3
"""
Keep the manifest's <queries> list in step with the catalog.

    tools/sync-queries.py            # report drift, exit 1 if the manifest is stale
    tools/sync-queries.py --write    # rewrite the block

PandaApps dropped QUERY_ALL_PACKAGES (it reads as a dropper next to
REQUEST_INSTALL_PACKAGES, and Play Protect was blocking installs over it). Visibility now comes
from an explicit <queries> list, which has to be regenerated whenever the catalog gains an app —
otherwise that app shows "Not installed" forever, however many times you install it.

Run this at every PandaApps release, before building.
"""

import json
import os
import re
import sys

HERE = os.path.dirname(os.path.abspath(__file__))
MANIFEST = os.path.join(HERE, "..", "app", "src", "main", "AndroidManifest.xml")
CATALOG = os.path.join(HERE, "..", "..", "..", "desktop", "minima-core-apks", "apks.json")


def catalog_packages():
    with open(CATALOG) as f:
        d = json.load(f)
    return sorted({a["packageId"] for a in d["apps"]
                   if a.get("file", "").lower().endswith(".apk") and a.get("packageId")})


def manifest_packages(text):
    block = re.search(r"<queries>(.*?)</queries>", text, re.S)
    if not block:
        return None
    return sorted(re.findall(r'<package android:name="([^"]+)"', block.group(1)))


def main():
    write = "--write" in sys.argv
    text = open(MANIFEST).read()

    want = catalog_packages()
    have = manifest_packages(text)

    if have is None:
        print("no <queries> block in the manifest — nothing to sync")
        return 1

    missing = [p for p in want if p not in have]     # in the catalog, invisible to the app
    extra = [p for p in have if p not in want]       # delisted; harmless but dead weight

    if not missing and not extra:
        print(f"in sync — {len(want)} packages")
        return 0

    for p in missing:
        print(f"  MISSING  {p}  (catalog app the store cannot see)")
    for p in extra:
        print(f"  stale    {p}  (no longer in the catalog)")

    if not write:
        print("\nrun with --write to fix")
        return 1

    body = "\n".join(f'        <package android:name="{p}" />' for p in want)
    text = re.sub(r"(<queries>).*?(</queries>)",
                  lambda m: f"{m.group(1)}\n{body}\n    {m.group(2)}", text, flags=re.S)
    open(MANIFEST, "w").write(text)
    print(f"\nwritten — {len(want)} packages")
    return 0


if __name__ == "__main__":
    sys.exit(main())
