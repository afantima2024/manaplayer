"""Refresh shipped radio lists; regular Gradle builds use saved snapshots."""

import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import time
import urllib.parse
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
DESTINATION = ROOT / "app" / "src" / "main" / "resources" / "radio" / "subscriptions"
BASE = "https://raw.githubusercontent.com/AlonDrilich/radio-playlists/main"
SOURCES = {
    "world": f"{BASE}/top/top-500.m3u",
    "us": f"{BASE}/countries/us.m3u",
    "tw": f"{BASE}/countries/tw.m3u",
    "cctv": "https://raw.githubusercontent.com/Kimentanm/aptv/master/m3u/radio.m3u",
}


def cctv_audio(text):
    rows = ["#EXTM3U"]
    title = None
    for line in text.splitlines():
        line = line.strip()
        if line.startswith(("#EXT-X-APP ", "#EXT-X-APTV-TYPE ", "#EXT-X-SUB-URL ")):
            continue
        if line.upper().startswith("#EXT-X-"):
            raise ValueError("CCTV subscription is an HLS manifest, not a station list")
        if line.startswith("#EXTINF:"):
            if title:
                raise ValueError(f"Missing CCTV audio address: {title}")
            match = re.match(r'^#EXTINF:(?:[^",]|"(?:\\.|[^"\\])*")*,(.*)$', line)
            title = match[1].strip() if match else None
            if title and not re.match(r"CCTV(?:-|\s|\d)", title, re.IGNORECASE):
                title = None
        elif line and not line.startswith("#"):
            uri = urllib.parse.urlsplit(line)
            if title:
                if (uri.scheme != "https" or
                        uri.hostname not in {"piccpndali.v.myalicdn.com", "piccpndks.v.kcdnvip.com"} or
                        not re.fullmatch(r"/audio/cctv(?:[1-9][0-9]?|5plus|europe|america)_2\.m3u8", uri.path)):
                    raise ValueError(f"Unsupported CCTV audio address: {title}")
                rows.extend([f'#EXTINF:-1 group-title="National",{title}', line])
            title = None
    if title:
        raise ValueError(f"Missing CCTV audio address: {title}")
    if len(rows) == 1:
        raise ValueError("CCTV subscription has no supported audio entries")
    return ("\n".join(rows) + "\n").encode("utf-8")


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--only", choices=SOURCES, help="Refresh one source, retaining other frozen snapshots.")
    args = parser.parse_args()
    downloads = {}
    previous = json.loads((DESTINATION / "manifest.json").read_text("utf-8")) if args.only else []
    metadata = {entry["key"]: entry for entry in previous}
    for name, url in SOURCES.items():
        if args.only and name != args.only:
            if name not in metadata:
                raise ValueError(f"Missing saved metadata for {name}")
            continue
        request = urllib.request.Request(url, headers={"User-Agent": "ManaMusicRadio-development"})
        with urllib.request.urlopen(request, timeout=30) as response:
            if not response.url.startswith("https://"):
                raise ValueError(f"Insecure redirect for {name}")
            data = response.read(1024 * 1024 + 1)
        text = data.decode("utf-8-sig")
        if len(data) > 1024 * 1024 or not text.lstrip().startswith("#EXTM3U"):
            raise ValueError(f"Invalid or oversized subscription: {name}")
        if name != "cctv" and "#EXT-X-" in text:
            raise ValueError(f"Subscription is an HLS manifest: {name}")
        if text.count("#EXTINF:") == 0:
            raise ValueError(f"Empty subscription: {name}")
        if name == "cctv":
            data = cctv_audio(text)
        downloads[f"{name}.m3u"] = data
        metadata[name] = {
            "key": name, "url": url, "checkedAtMs": int(time.time() * 1000),
            "sha256": hashlib.sha256(data).hexdigest(),
        }
    ordered = [metadata[name] for name in SOURCES]
    downloads["manifest.json"] = (json.dumps(ordered, indent=2) + "\n").encode("utf-8")
    DESTINATION.mkdir(parents=True, exist_ok=True)
    for filename, data in downloads.items():
        temporary = DESTINATION / f"{filename}.new"
        try:
            temporary.write_bytes(data)
            os.replace(temporary, DESTINATION / filename)
        finally:
            temporary.unlink(missing_ok=True)
    for entry in ordered:
        content = downloads.get(entry["key"] + ".m3u")
        if content is not None:
            print(f"{entry['key']}: {len(content)} bytes, SHA-256 {entry['sha256']}")


if __name__ == "__main__":
    main()
