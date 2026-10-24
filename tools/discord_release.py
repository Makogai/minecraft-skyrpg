#!/usr/bin/env python3
"""Posts a SkyRPG update to a Discord channel through a webhook: the friend zip, a changelog and screenshots.

    python tools/discord_release.py --set-webhook             # once: paste the webhook URL (hidden, saved locally)
    python tools/discord_release.py -m "Enchanting is in!" -i shot1.png -i shot2.png
    python tools/discord_release.py --no-zip -f plan.pdf -m "..."   # any file, without the build
    python tools/discord_release.py --dry-run -m "..."        # show what would be sent, send nothing

By default it sends the newest dist/SkyRPG-*.zip (build it first with tools/package_friend.py, or pass --package).
The webhook URL lives in tools/.discord_webhook (gitignored) or the DISCORD_WEBHOOK_URL environment variable. Treat it
like a password: anyone who has it can post in that channel.

Discord takes files up to 10 MB per message on a server without boosts (--max-mb to change). Only the standard library
is used.
"""
import argparse
import getpass
import json
import mimetypes
import os
import subprocess
import sys
import urllib.error
import urllib.request
import uuid
from datetime import datetime, timezone
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
WEBHOOK_FILE = ROOT / "tools" / ".discord_webhook"
ACCENT = 0xB98CFF


def webhook_url():
    url = os.environ.get("DISCORD_WEBHOOK_URL", "").strip()
    if not url and WEBHOOK_FILE.exists():
        url = WEBHOOK_FILE.read_text(encoding="utf-8").strip()
    if not url:
        sys.exit("No webhook set. Run: python tools/discord_release.py --set-webhook")
    if not url.startswith("https://discord.com/api/webhooks/") and not url.startswith("https://discordapp.com/api/webhooks/"):
        sys.exit("The saved webhook doesn't look like a Discord webhook URL; set it again with --set-webhook.")
    return url


def set_webhook():
    url = getpass.getpass("Paste the Discord webhook URL (input hidden): ").strip()
    if not url.startswith("https://discord.com/api/webhooks/") and not url.startswith("https://discordapp.com/api/webhooks/"):
        sys.exit("That isn't a Discord webhook URL (it starts with https://discord.com/api/webhooks/).")
    WEBHOOK_FILE.write_text(url + "\n", encoding="utf-8")
    print(f"Saved to {WEBHOOK_FILE.relative_to(ROOT)} (gitignored).")


def newest_zip():
    zips = sorted((ROOT / "dist").glob("SkyRPG-*.zip"), key=lambda p: p.stat().st_mtime)
    return zips[-1] if zips else None


def multipart(payload, files):
    """A multipart/form-data body: payload_json plus files[0..n]."""
    boundary = "skyrpg-" + uuid.uuid4().hex
    parts = []
    parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"payload_json\"\r\n"
                 f"Content-Type: application/json\r\n\r\n".encode() + json.dumps(payload).encode() + b"\r\n")
    for i, path in enumerate(files):
        kind = mimetypes.guess_type(path.name)[0] or "application/octet-stream"
        parts.append(f"--{boundary}\r\nContent-Disposition: form-data; name=\"files[{i}]\"; filename=\"{path.name}\"\r\n"
                     f"Content-Type: {kind}\r\n\r\n".encode() + path.read_bytes() + b"\r\n")
    parts.append(f"--{boundary}--\r\n".encode())
    return b"".join(parts), f"multipart/form-data; boundary={boundary}"


def main():
    parser = argparse.ArgumentParser(description="Post a SkyRPG update to Discord.")
    parser.add_argument("--set-webhook", action="store_true", help="save the webhook URL (asked for, hidden)")
    parser.add_argument("-c", "--content", default="", help="plain message text above the embed; mentions like @everyone ping there (not inside the embed)")
    parser.add_argument("-m", "--message", default="", help="changelog text (Discord markdown; \\n for new lines)")
    parser.add_argument("-t", "--title", default="", help="title (default: SkyRPG update <version>)")
    parser.add_argument("-i", "--image", action="append", default=[], help="a PNG/JPG to attach (repeatable); the first is shown big")
    parser.add_argument("-f", "--file", action="append", default=[], help="any other file to attach, e.g. a PDF (repeatable)")
    parser.add_argument("-z", "--zip", help="the zip to attach (default: newest dist/SkyRPG-*.zip)")
    parser.add_argument("--no-zip", action="store_true", help="post only text and images")
    parser.add_argument("--package", action="store_true", help="run tools/package_friend.py first")
    parser.add_argument("--max-mb", type=float, default=10, help="Discord's upload limit for this server (default 10)")
    parser.add_argument("--dry-run", action="store_true")
    args = parser.parse_args()

    if args.set_webhook:
        set_webhook()
        return
    if args.package:
        subprocess.run([sys.executable, str(ROOT / "tools" / "package_friend.py")], check=True)

    files = []
    archive = None
    if not args.no_zip:
        archive = Path(args.zip) if args.zip else newest_zip()
        if archive is None or not archive.exists():
            sys.exit("No zip found; build one with tools/package_friend.py (or pass --package).")
        files.append(archive)
    images = [Path(p) for p in args.image]
    for image in images:
        if not image.exists():
            sys.exit(f"No such image: {image}")
    files.extend(images)
    others = [Path(p) for p in args.file]
    for other in others:
        if not other.exists():
            sys.exit(f"No such file: {other}")
    files.extend(others)
    total = sum(p.stat().st_size for p in files) / 1_000_000
    if total > args.max_mb:
        sys.exit(f"{total:.1f} MB is over Discord's {args.max_mb:g} MB limit; drop some images or use --max-mb.")

    version = archive.stem.removeprefix("SkyRPG-") if archive else ""
    embed = {
        "title": args.title or (f"SkyRPG update {version}" if version else "SkyRPG update"),
        "description": args.message.replace("\\n", "\n")[:4000],
        "color": ACCENT,
        "timestamp": datetime.now(timezone.utc).isoformat(),
        "footer": {"text": "Install: unzip, then follow HOW-TO-INSTALL.txt (replace your old SkyRPG jars)" if archive else "SkyRPG"},
    }
    if images:
        embed["image"] = {"url": f"attachment://{images[0].name}"}
    payload = {"username": "SkyRPG Builds", "embeds": [embed]}
    if args.content:
        payload["content"] = args.content.replace("\\n", "\n")
        payload["allowed_mentions"] = {"parse": ["everyone", "roles", "users"]}
    if archive:
        payload["content"] = (payload["content"] + "\n" if args.content else "") + f"New build: **{archive.name}** ({archive.stat().st_size / 1_000_000:.1f} MB). Download it below."

    if args.dry_run:
        print(json.dumps(payload, indent=2))
        print("files:", ", ".join(f"{p.name} ({p.stat().st_size / 1_000_000:.2f} MB)" for p in files))
        return

    body, content_type = multipart(payload, files)
    request = urllib.request.Request(webhook_url(), data=body, method="POST",
                                     headers={"Content-Type": content_type, "User-Agent": "SkyRPG-release (python urllib)"})
    try:
        with urllib.request.urlopen(request, timeout=120) as response:
            print(f"Posted to Discord (HTTP {response.status}): {', '.join(p.name for p in files) or 'text only'}")
    except urllib.error.HTTPError as e:
        detail = e.read().decode("utf-8", "replace")[:300]
        sys.exit(f"Discord refused it (HTTP {e.code}): {detail}")


if __name__ == "__main__":
    main()
