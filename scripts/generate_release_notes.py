#!/usr/bin/env python3
"""Generate structured, repeatable GitHub Release notes for Sonorus Stable."""

from __future__ import annotations

import argparse
import os
import re
import subprocess
from dataclasses import dataclass
from pathlib import Path


DEFAULT_REPOSITORY = "Cluno1/Sonorus"
MAX_COMMITS_WITHOUT_TAG = 30


@dataclass(frozen=True)
class Change:
    category: str
    text: str
    short_sha: str


CATEGORY_TITLES = {
    "feature": "New and improved",
    "fix": "Fixes",
    "performance": "Performance",
    "docs": "Documentation",
    "engineering": "Engineering",
}


def run_git(*args: str) -> str:
    result = subprocess.run(
        ["git", *args],
        check=True,
        capture_output=True,
        text=True,
    )
    return result.stdout.strip()


def previous_release_tag(commit: str, current_tag: str) -> str | None:
    tags = run_git(
        "tag",
        "--list",
        "v[0-9]*",
        "--merged",
        commit,
        "--sort=-version:refname",
    ).splitlines()
    return next((tag for tag in tags if tag and tag != current_tag), None)


def classify_commit(subject: str, short_sha: str) -> Change | None:
    if re.match(r"^(merge|release)(?:\b|:)", subject, re.IGNORECASE):
        return None

    match = re.match(
        r"^(feat|fix|perf|docs|refactor|test|build|ci|chore|style)"
        r"(?:\(([^)]+)\))?!?:\s*(.+)$",
        subject,
        re.IGNORECASE,
    )
    if match:
        kind, scope, text = match.groups()
        kind = kind.lower()
        category = {
            "feat": "feature",
            "fix": "fix",
            "perf": "performance",
            "docs": "docs",
        }.get(kind, "engineering")
        if scope:
            scope_label = {
                "ci": "CI",
                "ui": "UI",
                "api": "API",
                "r8": "R8",
            }.get(scope.lower(), scope.replace("-", " ").title())
            text = f"{scope_label}: {text}"
    else:
        category = "feature"
        text = subject

    text = text.strip().rstrip(".")
    if text:
        text = text[0].upper() + text[1:]
    return Change(category=category, text=text, short_sha=short_sha)


def collect_changes(commit: str, previous_tag: str | None) -> list[Change]:
    revision = f"{previous_tag}..{commit}" if previous_tag else commit
    args = [
        "log",
        revision,
        "--no-merges",
        "--format=%h%x09%s",
    ]
    if previous_tag is None:
        args.insert(2, f"--max-count={MAX_COMMITS_WITHOUT_TAG}")

    seen: set[str] = set()
    changes: list[Change] = []
    for line in run_git(*args).splitlines():
        if "\t" not in line:
            continue
        short_sha, subject = line.split("\t", 1)
        change = classify_commit(subject.strip(), short_sha)
        if change is None or change.text.lower() in seen:
            continue
        seen.add(change.text.lower())
        changes.append(change)
    return changes


def version_from_tag(tag: str) -> str:
    version = tag.removeprefix("v")
    if not re.fullmatch(r"\d+\.\d+\.\d+", version):
        raise ValueError(f"Stable tag must look like v1.2.3, got: {tag}")
    return version


def render_notes(
    *,
    tag: str,
    version_code: str,
    repository: str,
    previous_tag: str | None,
    changes: list[Change],
) -> str:
    version = version_from_tag(tag)
    grouped = {category: [] for category in CATEGORY_TITLES}
    for change in changes:
        grouped[change.category].append(change)

    lines = [
        f"# Sonorus {version}",
        "",
        "A signed Stable build of Sonorus: local music playback, an optional private Catalog, "
        "MusicXML scores and synchronized lyrics editing.",
        "",
        "## What's changed",
        "",
    ]

    visible_categories = [category for category, items in grouped.items() if items]
    if not visible_categories:
        lines.extend(["- Maintenance and reliability improvements.", ""])
    else:
        for category in visible_categories:
            lines.append(f"### {CATEGORY_TITLES[category]}")
            lines.append("")
            for change in grouped[category]:
                lines.append(f"- {change.text} (`{change.short_sha}`)")
            lines.append("")

    lines.extend(
        [
            "## Choose your APK",
            "",
            "| Device | Download asset |",
            "| --- | --- |",
            f"| Most current Android phones and tablets | `Sonorus-{version}-githubRelease-arm64-v8a.apk` |",
            f"| Older 32-bit ARM devices | `Sonorus-{version}-githubRelease-armeabi-v7a.apk` |",
            f"| x86 / x86_64 devices and emulators | The matching `x86` or `x86_64` APK |",
            f"| Unsure which one to use | `Sonorus-{version}-githubRelease.apk` (universal) |",
            "",
            "Each APK has a matching `.sha256` file. Android 8.0 or newer is required.",
            "",
            "## Release integrity",
            "",
            f"- Version code: `{version_code}`",
            "- Production application ID: `io.github.cluno1.sonorus`",
            "- Every APK is checked for package identity, version, ABI, minimum SDK, and the permanent signing certificate before publication.",
            "- The private in-app updater additionally verifies a signed update manifest and APK SHA-256 before installation.",
            "",
            "> [!NOTE]",
            "> Stable and Debug are separate apps. Installing Stable does not copy Debug's local data or device registration; use backup/restore where applicable and enroll the Stable app separately.",
            "",
            "## Feedback and services",
            "",
            f"- [Report a bug](https://github.com/{repository}/issues)",
            "- [Join the Discord community](https://discord.gg/KaGCYshewX)",
            "- [Email the maintainer](mailto:clunojames@gmal.com)",
            "- Contact us through Discord or email for private deployments and custom backend services.",
            "",
        ]
    )

    if previous_tag:
        lines.append(
            f"**Full changelog:** https://github.com/{repository}/compare/{previous_tag}...{tag}"
        )
    else:
        lines.append(f"**Source at this release:** https://github.com/{repository}/tree/{tag}")

    lines.extend(
        [
            "",
            "---",
            "",
            "Sonorus is an independent GPL-3.0-or-later derivative of [Rhythm](https://github.com/cromaguy/Rhythm).",
            "",
        ]
    )
    return "\n".join(lines)


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("tag", help="Stable tag, for example v1.0.7")
    parser.add_argument("commit", nargs="?", default=os.environ.get("GITHUB_SHA", "HEAD"))
    parser.add_argument("previous_tag", nargs="?", default=None)
    parser.add_argument("--version-code", default="unknown")
    parser.add_argument(
        "--repository",
        default=os.environ.get("GITHUB_REPOSITORY", DEFAULT_REPOSITORY),
    )
    parser.add_argument("--output", type=Path, default=Path("release_notes.md"))
    return parser.parse_args()


def main() -> None:
    args = parse_args()
    version_from_tag(args.tag)
    previous_tag = args.previous_tag or previous_release_tag(args.commit, args.tag)
    changes = collect_changes(args.commit, previous_tag)
    notes = render_notes(
        tag=args.tag,
        version_code=str(args.version_code),
        repository=args.repository,
        previous_tag=previous_tag,
        changes=changes,
    )
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(notes, encoding="utf-8")
    print(
        f"Generated {args.output} with {len(changes)} change(s)"
        + (f" since {previous_tag}" if previous_tag else " from recent history")
    )


if __name__ == "__main__":
    main()
