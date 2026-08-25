"""Tests for build-update-manifests.py, the writing half of the update protocol.

The reading half is `UpdateManifestSource.kt`, and since the security hardening it *rejects*
manifests that stray: a blank sha256, a download URL off this repository's releases tree, an asset
name with a path separator. So the invariants asserted here are not style — each one is a condition
under which every installed app would refuse the update and the channel would silently die. That is
also why these run in CI's build job rather than only on release day: the release workflow is the
one place a regression here would surface otherwise, and it surfaces as a broken update for users.

Stdlib unittest on purpose: CI's ubuntu runner has python3 and nothing else needs installing.
Run locally with:  python3 -m unittest discover -s .github/scripts
"""

from __future__ import annotations

import hashlib
import importlib.util
import json
import pathlib
import sys
import tempfile
import unittest
from unittest import mock

# The script's filename uses hyphens (it is a command, not a module), so a plain import cannot
# reach it; loading by path is the stdlib's sanctioned way around that.
_SCRIPT = pathlib.Path(__file__).with_name("build-update-manifests.py")
_spec = importlib.util.spec_from_file_location("build_update_manifests", _SCRIPT)
bum = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(bum)

REPOSITORY = "owner/nodyssey"

CHANGELOG = """\
# 更新记录

本文件记录用户可见的变化。

## Unreleased

### 改进

- 还没发布的条目，不该出现在任何清单里。

## 1.2.12 - 2026-08-24

### 修复

- 修了一个东西。

## 1.2.11 – 2026-08-20

- 用连接号变体的日期行。

## 1.2.10

- 没有日期的版本行。
"""


class ChangelogVersionsTest(unittest.TestCase):
    def test_parses_tagged_sections_and_skips_unreleased(self):
        versions = bum.changelog_versions(CHANGELOG, REPOSITORY)

        self.assertEqual(["1.2.12", "1.2.11", "1.2.10"], [v["versionName"] for v in versions])
        self.assertEqual("v1.2.12", versions[0]["tag"])
        self.assertEqual("2026-08-24", versions[0]["publishedOn"])
        self.assertIn("修了一个东西", versions[0]["notes"])
        # The Unreleased body must not leak into any version's notes.
        for version in versions:
            self.assertNotIn("还没发布的条目", version["notes"])

    def test_heading_variants(self):
        versions = bum.changelog_versions(CHANGELOG, REPOSITORY)

        # An en-dash between version and date is still a dated heading …
        self.assertEqual("2026-08-20", versions[1]["publishedOn"])
        # … and a bare version heading still counts, with the date left empty.
        self.assertEqual("", versions[2]["publishedOn"])

    def test_release_urls_point_at_this_repository(self):
        for version in bum.changelog_versions(CHANGELOG, REPOSITORY):
            self.assertEqual(
                f"https://github.com/{REPOSITORY}/releases/tag/{version['tag']}",
                version["releaseUrl"],
            )


def run_main(out: pathlib.Path, apk: pathlib.Path, notes: pathlib.Path, changelog: pathlib.Path,
             channel: str, tag: str, version: str) -> int:
    argv = [
        "build-update-manifests.py",
        "--tag", tag,
        "--version", version,
        "--channel", channel,
        "--date", "2026-08-25",
        "--repository", REPOSITORY,
        "--apk", str(apk),
        "--notes", str(notes),
        "--changelog", str(changelog),
        "--out", str(out),
    ]
    with mock.patch.object(sys, "argv", argv):
        return bum.main()


class MainTest(unittest.TestCase):
    def setUp(self):
        self._tmp = tempfile.TemporaryDirectory()
        self.addCleanup(self._tmp.cleanup)
        root = pathlib.Path(self._tmp.name)
        self.out = root / "updates"
        self.apk = root / "nodyssey-1.2.13.apk"
        self.apk.write_bytes(b"not really an apk, but bytes with a digest")
        self.notes = root / "notes.md"
        self.notes.write_text("发布说明正文。", encoding="utf-8")
        self.changelog = root / "CHANGELOG.md"
        self.changelog.write_text(CHANGELOG, encoding="utf-8")

    def read(self, name: str) -> dict:
        return json.loads((self.out / name).read_text(encoding="utf-8"))

    def test_stable_release_writes_both_channels_with_trusted_urls(self):
        code = run_main(self.out, self.apk, self.notes, self.changelog,
                        channel="stable", tag="v1.2.13", version="1.2.13")

        self.assertEqual(0, code)
        self.assertTrue((self.out / ".nojekyll").exists())
        stable, dev = self.read("stable.json"), self.read("dev.json")
        # The channel field names the file, not the build: both carry the same release.
        self.assertEqual("stable", stable["channel"])
        self.assertEqual("dev", dev["channel"])
        for manifest in (stable, dev):
            # Everything the client refuses a manifest over, asserted at the source:
            # sha256 present and correct, the URL inside this repository's releases tree,
            # the asset name a bare filename.
            self.assertEqual(hashlib.sha256(self.apk.read_bytes()).hexdigest(),
                             manifest["apk"]["sha256"])
            self.assertEqual(
                f"https://github.com/{REPOSITORY}/releases/download/v1.2.13/{self.apk.name}",
                manifest["apk"]["url"],
            )
            self.assertNotIn("/", manifest["apk"]["name"])
            self.assertEqual(self.apk.stat().st_size, manifest["apk"]["sizeBytes"])
            self.assertEqual("发布说明正文。", manifest["notes"])

    def test_dev_release_leaves_stable_alone_and_prepends_itself_to_the_log(self):
        code = run_main(self.out, self.apk, self.notes, self.changelog,
                        channel="dev", tag="v1.2.13-dev.1", version="1.2.13-dev.1")

        self.assertEqual(0, code)
        self.assertFalse((self.out / "stable.json").exists())
        versions = self.read("changelog.json")["versions"]
        # The dev build has no CHANGELOG section, so the script writes it into the log itself —
        # newest first, marked pre-release, carrying the release notes.
        self.assertEqual("1.2.13-dev.1", versions[0]["versionName"])
        self.assertTrue(versions[0]["preRelease"])
        self.assertEqual("发布说明正文。", versions[0]["notes"])
        self.assertEqual("1.2.12", versions[1]["versionName"])

    def test_missing_apk_fails_rather_than_writing_manifests(self):
        code = run_main(self.out, self.apk.with_name("missing.apk"), self.notes, self.changelog,
                        channel="stable", tag="v1.2.13", version="1.2.13")

        self.assertEqual(1, code)
        self.assertFalse((self.out / "dev.json").exists())


if __name__ == "__main__":
    unittest.main()
