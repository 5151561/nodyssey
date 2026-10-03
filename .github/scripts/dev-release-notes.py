#!/usr/bin/env python3
"""The part of CHANGELOG.md's `## Unreleased` section that a dev tag adds over the tag before it.

Unreleased accumulates from one stable release to the next, so printing it whole made every dev
build re-announce everything the previous dev builds already had. This prints only the entries that
were not in the previous tag's Unreleased, keeping each `###` heading that still has something under
it. After a stable release the previous tag's Unreleased is empty, so the first dev build after it
gets the whole section, which is what it contains.

Usage: dev-release-notes.py CURRENT_CHANGELOG [PREVIOUS_CHANGELOG]
"""

import pathlib
import sys


def unreleased(markdown: str) -> list[str]:
    lines, inside = [], False
    for line in markdown.splitlines():
        if line.startswith("## "):
            if inside:
                break
            inside = line.startswith("## Unreleased")
            continue
        if inside:
            lines.append(line.rstrip())
    return lines


def added(current: list[str], previous: list[str]) -> list[str]:
    seen = {line for line in previous if line.strip() and not line.startswith("#")}
    out, heading = [], None
    for line in current:
        if line.startswith("#"):
            heading = line
        elif line.strip() and line not in seen:
            if heading is not None:
                if out:
                    out.append("")
                out += [heading, ""]
                heading = None
            out.append(line)
    return out


def main() -> None:
    current = unreleased(pathlib.Path(sys.argv[1]).read_text(encoding="utf-8"))
    previous = []
    if len(sys.argv) > 2:
        previous = unreleased(pathlib.Path(sys.argv[2]).read_text(encoding="utf-8"))
    print("\n".join(added(current, previous)))


if __name__ == "__main__":
    main()
