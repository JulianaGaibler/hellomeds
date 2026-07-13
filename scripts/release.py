#!/usr/bin/env python3
"""Release helpers for HelloMeds.

Subcommands:
  prepare              Move unreleased changelog entries into a versioned block
                       and write the F-Droid changelog .txt for the current
                       version.properties values. Run locally before the
                       version-bump commit.
  body <version>       Render the GitHub release body for <version> to stdout.
  validate <code>      Fail if the F-Droid changelog file for <code> is missing
                       or exceeds 500 chars (the Google Play release-notes cap;
                       F-Droid uses the same files).
"""

from __future__ import annotations

import datetime as _dt
import pathlib
import shutil
import sys
from typing import Any

try:
    import yaml
except ImportError:
    sys.stderr.write(
        "PyYAML is required. Install with: pip3 install pyyaml\n",
    )
    sys.exit(1)

# UTF-8 on stdout — release notes can contain emoji and the GitHub release body
# is rendered as UTF-8 regardless of the runner's default encoding.
sys.stdout.reconfigure(encoding="utf-8")

ROOT = pathlib.Path(__file__).resolve().parent.parent
VERSION_PROPS = ROOT / "version.properties"
CHANGELOG = ROOT / "changelog.yaml"
FASTLANE_LOCALES = ("en-US", "de-DE")
CHANGELOG_CHAR_LIMIT = 500


def _read_version_properties() -> tuple[str, int]:
    name: str | None = None
    code: int | None = None
    for raw in VERSION_PROPS.read_text().splitlines():
        line = raw.strip()
        if line.startswith("APP_VERSION_NAME="):
            name = line.split("=", 1)[1].strip()
        elif line.startswith("APP_VERSION_CODE="):
            code = int(line.split("=", 1)[1].strip())
    if name is None or code is None:
        sys.exit("version.properties missing APP_VERSION_NAME or APP_VERSION_CODE")
    return name, code


def _load_changelog() -> dict[str, Any]:
    data = yaml.safe_load(CHANGELOG.read_text()) or {}
    data.setdefault("unreleased", {"notes": []})
    data.setdefault("releases", [])
    data["unreleased"].setdefault("notes", [])
    return data


def _dump_changelog(data: dict[str, Any]) -> None:
    CHANGELOG.write_text(
        yaml.safe_dump(
            data,
            sort_keys=False,
            allow_unicode=True,
            default_flow_style=False,
        )
    )


def _find_release(data: dict[str, Any], version: str) -> dict[str, Any] | None:
    for entry in data.get("releases", []):
        if str(entry.get("version")) == version:
            return entry
    return None


def _changelog_file(locale: str, version_code: int) -> pathlib.Path:
    return ROOT / "fastlane" / "metadata" / "android" / locale / "changelogs" / f"{version_code}.txt"


def cmd_prepare() -> int:
    name, code = _read_version_properties()
    data = _load_changelog()

    existing = _find_release(data, name)
    if existing is None:
        notes = list(data["unreleased"].get("notes") or [])
        if not notes:
            sys.exit(
                f"unreleased.notes is empty and no releases entry for {name} exists yet — "
                "add notes to changelog.yaml first."
            )
        entry = {
            "version": name,
            "versionCode": code,
            "date": _dt.date.today().isoformat(),
            "notes": notes,
        }
        # Insert at the front so the most recent release reads first.
        data["releases"].insert(0, entry)
        data["unreleased"]["notes"] = []
        _dump_changelog(data)
        print(f"Moved {len(notes)} unreleased entr{'y' if len(notes) == 1 else 'ies'} into release {name} (versionCode {code}).")
    else:
        notes = list(existing.get("notes") or [])
        print(f"Reusing existing changelog entry for {name} (versionCode {code}).")

    body = "\n".join(f"- {n}" for n in notes) + "\n"
    if len(body) > CHANGELOG_CHAR_LIMIT:
        sys.exit(
            f"Rendered changelog is {len(body)} chars; trim to ≤{CHANGELOG_CHAR_LIMIT} "
            "(Google Play / F-Droid limit) and re-run."
        )

    primary = _changelog_file("en-US", code)
    primary.parent.mkdir(parents=True, exist_ok=True)
    primary.write_text(body)
    print(f"Wrote {primary.relative_to(ROOT)}")

    # Mirror to other locales so users with non-English devices don't see an
    # empty release-notes panel. Per-locale notes can be added later if needed.
    for locale in FASTLANE_LOCALES:
        if locale == "en-US":
            continue
        dest = _changelog_file(locale, code)
        dest.parent.mkdir(parents=True, exist_ok=True)
        shutil.copyfile(primary, dest)
        print(f"Mirrored to {dest.relative_to(ROOT)}")

    return 0


def cmd_body(version: str) -> int:
    data = _load_changelog()
    entry = _find_release(data, version)
    if entry is None:
        sys.exit(f"No releases entry for {version} in changelog.yaml")
    print(f"## v{version}\n")
    for note in entry.get("notes") or []:
        print(f"- {note}")
    return 0


def cmd_validate(version_code: str) -> int:
    code = int(version_code)
    path = _changelog_file("en-US", code)
    if not path.exists():
        sys.exit(
            f"Missing {path.relative_to(ROOT)} — run `./hm prepare-release` before bumping the version."
        )
    size = path.stat().st_size
    if size > CHANGELOG_CHAR_LIMIT:
        sys.exit(f"{path.relative_to(ROOT)} is {size} chars (> {CHANGELOG_CHAR_LIMIT}).")
    print(f"{path.relative_to(ROOT)} OK ({size} chars)")
    return 0


def main(argv: list[str]) -> int:
    if len(argv) < 2:
        sys.exit(__doc__)
    cmd, *rest = argv[1:]
    if cmd == "prepare":
        return cmd_prepare()
    if cmd == "body":
        if not rest:
            sys.exit("usage: release.py body <version>")
        return cmd_body(rest[0])
    if cmd == "validate":
        if not rest:
            sys.exit("usage: release.py validate <versionCode>")
        return cmd_validate(rest[0])
    sys.exit(f"unknown subcommand: {cmd}")


if __name__ == "__main__":
    raise SystemExit(main(sys.argv))
