#!/usr/bin/env python3
"""Writes the static update manifests the app reads.

The app does not ask `api.github.com` what the newest release is. That endpoint's anonymous quota is
sixty calls an hour counted per *address*, which a phone shares with everything else behind its NAT or
proxy exit, so the answer is regularly a 403 that has nothing to do with this app. Instead the release
workflow publishes three small files onto the `updates` branch and the app reads those over plain
HTTPS — the same shape Sparkle's appcast and electron-builder's `latest.yml` have:

    stable.json     the newest generally-available build
    dev.json        the newest build of any kind, for 接收 dev 版更新
    changelog.json  the version history behind 关于 › 更新日志

`dev.json` is written by every release, stable ones included: someone on the dev channel wants the
newest build there is, and after a stable release that is the stable build. `stable.json` is only
written by a stable release, which is what keeps a test build away from everyone who did not ask.

The branch is published through GitHub Pages — `https://<owner>.github.io/<repo>/stable.json` — because
`raw.githubusercontent.com`, which serves the same files, throttles per address and answers 429 behind
a busy proxy exit. **Pages must stay enabled for this branch in the repository settings**; the app
reports a plain 404 if it is not, rather than pretending there is no update.

The reading end is `core/update/UpdateManifestSource.kt`; the two are one protocol, and its fixtures
under `core/src/test/resources/fixtures/` are this script's output. Change a field name here and the
test there fails, which is the point.
"""

from __future__ import annotations

import argparse
import hashlib
import json
import pathlib
import re
import sys

SCHEMA = 1

# `## 1.2.8 - 2026-08-16`, and the `## Unreleased` section this deliberately does not match: a version
# that has not been tagged has no release to point the log at.
VERSION_HEADING = re.compile(r"^##\s+(?P<version>\d+(?:\.\d+)*)\s*(?:[-–—]\s*(?P<date>\S+))?\s*$")


def sha256_of(path: pathlib.Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as handle:
        for chunk in iter(lambda: handle.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def release_url(repository: str, tag: str) -> str:
    return f"https://github.com/{repository}/releases/tag/{tag}"


def download_url(repository: str, tag: str, asset: str) -> str:
    return f"https://github.com/{repository}/releases/download/{tag}/{asset}"


def build_manifest(args, apk: pathlib.Path, notes: str) -> dict:
    return {
        "schema": SCHEMA,
        "channel": args.channel,
        "versionName": args.version,
        "tag": args.tag,
        "publishedOn": args.date,
        "notes": notes,
        "apk": {
            "name": apk.name,
            "url": download_url(args.repository, args.tag, apk.name),
            "sizeBytes": apk.stat().st_size,
            "sha256": sha256_of(apk),
        },
        "releaseUrl": release_url(args.repository, args.tag),
    }


def changelog_versions(markdown: str, repository: str) -> list[dict]:
    """Every tagged section of CHANGELOG.md, newest first, as the file already orders them."""
    versions: list[dict] = []
    current: dict | None = None
    body: list[str] = []

    def flush() -> None:
        if current is not None:
            current["notes"] = "\n".join(body).strip()
            versions.append(current)

    for line in markdown.splitlines():
        heading = VERSION_HEADING.match(line)
        if heading:
            flush()
            version = heading.group("version")
            current = {
                "versionName": version,
                "tag": f"v{version}",
                "publishedOn": heading.group("date") or "",
                "preRelease": False,
                "releaseUrl": release_url(repository, f"v{version}"),
                "notes": "",
            }
            body = []
        elif line.startswith("## "):
            # Some other section — `Unreleased`, or the file's own preamble headings.
            flush()
            current = None
            body = []
        elif current is not None:
            body.append(line)
    flush()
    return versions


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--tag", required=True, help="v1.2.9 or v1.3.0-dev.2")
    parser.add_argument("--version", required=True, help="the versionName inside the APK")
    parser.add_argument("--channel", required=True, choices=("stable", "dev"))
    parser.add_argument("--date", required=True, help="YYYY-MM-DD, the day this went out")
    parser.add_argument("--repository", required=True, help="owner/name")
    parser.add_argument("--apk", required=True, type=pathlib.Path)
    parser.add_argument("--notes", required=True, type=pathlib.Path, help="the release body")
    parser.add_argument("--changelog", required=True, type=pathlib.Path, help="CHANGELOG.md")
    parser.add_argument("--out", required=True, type=pathlib.Path, help="the updates branch checkout")
    args = parser.parse_args()

    apk: pathlib.Path = args.apk
    if not apk.is_file():
        print(f"::error::No APK at {apk}", file=sys.stderr)
        return 1

    notes = args.notes.read_text(encoding="utf-8").strip()
    manifest = build_manifest(args, apk, notes)
    args.out.mkdir(parents=True, exist_ok=True)
    # The branch is served by GitHub Pages, which runs Jekyll over a branch source unless told not
    # to. Nothing here is a site, and Jekyll's rules about leading underscores are not worth meeting.
    (args.out / ".nojekyll").write_text("", encoding="utf-8")

    written = ["dev.json"]
    if args.channel == "stable":
        written.append("stable.json")
    for name in written:
        # `channel` names the file, not the build: dev.json carries a stable release verbatim when
        # that is the newest thing there is, and the app reads the field to decide whether to warn.
        payload = dict(manifest, channel="dev" if name == "dev.json" else "stable")
        (args.out / name).write_text(
            json.dumps(payload, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
        )

    versions = changelog_versions(args.changelog.read_text(encoding="utf-8"), args.repository)
    if args.channel == "dev":
        # A dev build has no CHANGELOG section of its own — it is cut to try one thing out — so the
        # log would otherwise not contain the build the tester is running.
        versions.insert(
            0,
            {
                "versionName": args.version,
                "tag": args.tag,
                "publishedOn": args.date,
                "preRelease": True,
                "releaseUrl": release_url(args.repository, args.tag),
                "notes": notes,
            },
        )
    (args.out / "changelog.json").write_text(
        json.dumps({"schema": SCHEMA, "versions": versions}, ensure_ascii=False, indent=2) + "\n",
        encoding="utf-8",
    )

    print(f"Wrote {', '.join(written)} and changelog.json ({len(versions)} versions) to {args.out}")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
