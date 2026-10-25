#!/usr/bin/env python3
"""Verify Catalog JSON source contracts and their minified R8 output."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
REMOTE_PACKAGE = "io.github.cluno1.sonorus.features.catalog.data.remote"
PROTECTED_CLASSES = {
    "io.github.cluno1.sonorus.features.catalog.data.local.CatalogOfflineCache$Entry",
    "io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueRecord",
    "io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueRecordEntry",
    "io.github.cluno1.sonorus.features.local.data.device.DeviceScanRoot",
    "io.github.cluno1.sonorus.util.LyricLine",
    "io.github.cluno1.sonorus.util.EnhancedLyricLine",
    "io.github.cluno1.sonorus.util.EnhancedWord",
    "io.github.cluno1.sonorus.util.Syllable",
    "io.github.cluno1.sonorus.util.WordByWordLyricLine",
    "io.github.cluno1.sonorus.util.WordByWordWord",
    "io.github.cluno1.sonorus.util.ExtractedColors",
    "io.github.cluno1.sonorus.shared.presentation.viewmodel.DownloadState",
    "io.github.cluno1.sonorus.shared.data.repository.PlaybackStatsRepository$PlaybackEvent",
}
FULLY_KEPT_PREFIXES = (
    "io.github.cluno1.sonorus.network.",
    "io.github.cluno1.sonorus.shared.data.model.",
    "io.github.cluno1.sonorus.features.streaming.domain.model.",
    "io.github.cluno1.sonorus.util.PlaylistImportExportUtils$",
)
PROTECTED_CLASS_PATTERNS = tuple(
    re.compile(pattern)
    for pattern in (
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.Work\w*",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.Arrangement",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.Part",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.Score\w*",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.Rendition\w*",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.CatalogLibrary\w*",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.CatalogLyric\w*",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.CatalogScoreOption",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.RhythmNowPlayingItem",
        r"io\.github\.cluno1\.sonorus\.features\.catalog\.domain\.CatalogPlaybackItem",
    )
)
REMOTE_SOURCES = (
    "CatalogAdminApi.kt",
    "CatalogApi.kt",
    "CatalogDeviceAuth.kt",
    "CatalogDtos.kt",
    "CatalogRoomDtos.kt",
    "ChorusDtos.kt",
)
EXPLICIT_MODELS = {
    "io.github.cluno1.sonorus.features.catalog.data.local.CatalogOfflineCache$Entry": (
        "app/src/main/java/io/github/cluno1/sonorus/features/catalog/data/local/CatalogOfflineCache.kt",
        "Entry",
    ),
    "io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueRecord": (
        "app/src/main/java/io/github/cluno1/sonorus/features/catalog/data/local/CatalogQueueStore.kt",
        "CatalogQueueRecord",
    ),
    "io.github.cluno1.sonorus.features.catalog.data.local.CatalogQueueRecordEntry": (
        "app/src/main/java/io/github/cluno1/sonorus/features/catalog/data/local/CatalogQueueStore.kt",
        "CatalogQueueRecordEntry",
    ),
    "io.github.cluno1.sonorus.features.local.data.device.DeviceScanRoot": (
        "app/src/main/java/io/github/cluno1/sonorus/features/local/data/device/DeviceScanFolderAccess.kt",
        "DeviceScanRoot",
    ),
    "io.github.cluno1.sonorus.util.LyricLine": (
        "app/src/main/java/io/github/cluno1/sonorus/util/LyricsParser.kt",
        "LyricLine",
    ),
    "io.github.cluno1.sonorus.util.EnhancedLyricLine": (
        "app/src/main/java/io/github/cluno1/sonorus/util/LyricsParser.kt",
        "EnhancedLyricLine",
    ),
    "io.github.cluno1.sonorus.util.EnhancedWord": (
        "app/src/main/java/io/github/cluno1/sonorus/util/LyricsParser.kt",
        "EnhancedWord",
    ),
    "io.github.cluno1.sonorus.util.Syllable": (
        "app/src/main/java/io/github/cluno1/sonorus/util/LyricsParser.kt",
        "Syllable",
    ),
    "io.github.cluno1.sonorus.util.WordByWordLyricLine": (
        "app/src/main/java/io/github/cluno1/sonorus/util/RhythmLyricsParser.kt",
        "WordByWordLyricLine",
    ),
    "io.github.cluno1.sonorus.util.WordByWordWord": (
        "app/src/main/java/io/github/cluno1/sonorus/util/RhythmLyricsParser.kt",
        "WordByWordWord",
    ),
}
DATA_CLASS = re.compile(r"\bdata\s+class\s+(\w+)\s*\(")
PROPERTY = re.compile(r"\b(?:val|var)\s+(\w+)\s*:")
SERIALIZED_NAME = re.compile(r"@SerializedName\s*\(")


def matching_parenthesis(text: str, opening: int) -> int:
    depth = 0
    quote: str | None = None
    escaped = False
    for index in range(opening, len(text)):
        char = text[index]
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
        elif char == "(":
            depth += 1
        elif char == ")":
            depth -= 1
            if depth == 0:
                return index
    raise ValueError("unbalanced data-class constructor")


def split_parameters(constructor: str) -> list[str]:
    parameters: list[str] = []
    start = 0
    depths = {"(": 0, "[": 0, "{": 0, "<": 0}
    closing = {")": "(", "]": "[", "}": "{", ">": "<"}
    quote: str | None = None
    escaped = False
    for index, char in enumerate(constructor):
        if quote is not None:
            if escaped:
                escaped = False
            elif char == "\\":
                escaped = True
            elif char == quote:
                quote = None
            continue
        if char in ('"', "'"):
            quote = char
        elif char in depths:
            depths[char] += 1
        elif char in closing:
            opener = closing[char]
            depths[opener] = max(0, depths[opener] - 1)
        elif char == "," and not any(depths.values()):
            parameters.append(constructor[start:index])
            start = index + 1
    parameters.append(constructor[start:])
    return parameters


def wire_fields() -> dict[str, set[str]]:
    source_root = ROOT / "app/src/main/java/io/github/cluno1/sonorus/features/catalog/data/remote"
    fields: dict[str, set[str]] = {}
    missing_annotations: list[str] = []
    for file_name in REMOTE_SOURCES:
        path = source_root / file_name
        text = path.read_text(encoding="utf-8")
        for match in DATA_CLASS.finditer(text):
            class_name = match.group(1)
            opening = match.end() - 1
            constructor = text[opening + 1 : matching_parenthesis(text, opening)]
            class_fields: set[str] = set()
            for parameter in split_parameters(constructor):
                property_match = PROPERTY.search(parameter)
                if property_match is None:
                    continue
                field_name = property_match.group(1)
                class_fields.add(field_name)
                if SERIALIZED_NAME.search(parameter) is None:
                    missing_annotations.append(f"{file_name}:{class_name}.{field_name}")
            fields[f"{REMOTE_PACKAGE}.{class_name}"] = class_fields
    if missing_annotations:
        raise SystemExit(
            "Catalog wire fields without @SerializedName:\n  "
            + "\n  ".join(sorted(missing_annotations))
        )
    return fields


def explicit_model_fields() -> dict[str, set[str]]:
    fields: dict[str, set[str]] = {}
    missing_annotations: list[str] = []
    for full_name, (relative_path, source_name) in EXPLICIT_MODELS.items():
        text = (ROOT / relative_path).read_text(encoding="utf-8")
        match = next((item for item in DATA_CLASS.finditer(text) if item.group(1) == source_name), None)
        if match is None:
            raise SystemExit(f"JSON model declaration not found: {relative_path}:{source_name}")
        opening = match.end() - 1
        constructor = text[opening + 1 : matching_parenthesis(text, opening)]
        class_fields: set[str] = set()
        for parameter in split_parameters(constructor):
            property_match = PROPERTY.search(parameter)
            if property_match is None:
                continue
            field_name = property_match.group(1)
            class_fields.add(field_name)
            if SERIALIZED_NAME.search(parameter) is None:
                missing_annotations.append(f"{relative_path}:{source_name}.{field_name}")
        fields[full_name] = class_fields
    if missing_annotations:
        raise SystemExit(
            "Explicit Gson fields without @SerializedName:\n  "
            + "\n  ".join(sorted(missing_annotations))
        )
    return fields


def is_protected(class_name: str, wire_classes: set[str]) -> bool:
    if "$$" in class_name:
        return False
    return (
        class_name in wire_classes
        or class_name in PROTECTED_CLASSES
        or class_name.startswith(FULLY_KEPT_PREFIXES)
        or any(pattern.fullmatch(class_name) for pattern in PROTECTED_CLASS_PATTERNS)
    )


def is_contract_field(class_name: str, field_name: str, fields: dict[str, set[str]]) -> bool:
    if class_name in fields:
        return field_name in fields[class_name]
    return not (
        field_name.startswith("$")
        or field_name in {"Companion", "INSTANCE", "CREATOR"}
        or field_name.isupper()
    )


def verify_seeds(path: Path, fields: dict[str, set[str]]) -> None:
    seeded: dict[str, set[str]] = {}
    for line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if ": " not in line:
            continue
        class_name, member = line.split(": ", 1)
        if class_name not in fields:
            continue
        seeded.setdefault(class_name, set()).add(member.rsplit(" ", 1)[-1])
    missing = [
        f"{class_name}.{field_name}"
        for class_name, class_fields in fields.items()
        for field_name in class_fields
        if field_name not in seeded.get(class_name, set())
    ]
    if missing:
        raise SystemExit("Catalog wire fields absent from R8 seeds:\n  " + "\n  ".join(sorted(missing)))


def verify_mapping(path: Path, fields: dict[str, set[str]]) -> None:
    current_class: str | None = None
    renamed: list[str] = []
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if raw_line and not raw_line[0].isspace() and raw_line.endswith(":") and " -> " in raw_line:
            current_class = raw_line.split(" -> ", 1)[0]
            continue
        if current_class is None or not is_protected(current_class, set(fields)):
            continue
        stripped = raw_line.strip()
        if not stripped or stripped.startswith("#") or " -> " not in stripped or "(" in stripped:
            continue
        original, output = stripped.rsplit(" -> ", 1)
        original_name = original.rsplit(" ", 1)[-1]
        if is_contract_field(current_class, original_name, fields) and original_name != output:
            renamed.append(f"{current_class}.{original_name} -> {output}")
    if renamed:
        raise SystemExit("Protected Gson fields renamed by R8:\n  " + "\n  ".join(sorted(renamed)))


def verify_usage(path: Path, fields: dict[str, set[str]]) -> None:
    current_class: str | None = None
    removed: list[str] = []
    for raw_line in path.read_text(encoding="utf-8", errors="replace").splitlines():
        if raw_line and not raw_line[0].isspace() and raw_line.endswith(":"):
            current_class = raw_line[:-1]
            continue
        if current_class is None or not is_protected(current_class, set(fields)):
            continue
        stripped = raw_line.strip()
        field_name = stripped.rsplit(" ", 1)[-1] if stripped else ""
        if (
            stripped
            and "(" not in stripped
            and not stripped.startswith("#")
            and is_contract_field(current_class, field_name, fields)
        ):
            removed.append(f"{current_class}: {stripped}")
    if removed:
        raise SystemExit("Protected Gson fields removed by R8:\n  " + "\n  ".join(sorted(removed)))


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--mapping", type=Path)
    parser.add_argument("--seeds", type=Path)
    parser.add_argument("--usage", type=Path)
    args = parser.parse_args()
    fields = wire_fields()
    fields.update(explicit_model_fields())
    supplied = (args.mapping, args.seeds, args.usage)
    if any(supplied) and not all(supplied):
        raise SystemExit("--mapping, --seeds and --usage must be supplied together")
    if all(supplied):
        verify_mapping(args.mapping, fields)
        verify_seeds(args.seeds, fields)
        verify_usage(args.usage, fields)
    field_count = sum(len(class_fields) for class_fields in fields.values())
    print(f"verified {field_count} fields across {len(fields)} explicit JSON models")
    if all(supplied):
        print("verified protected Gson fields in R8 mapping, seeds and usage")


if __name__ == "__main__":
    main()
