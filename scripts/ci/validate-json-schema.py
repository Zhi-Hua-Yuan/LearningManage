#!/usr/bin/env python3
"""Validate a JSON document against the local JSON-Schema subset used by CI.

The Stage 0 contract intentionally uses a small, dependency-free subset of
JSON Schema. Keeping this validator in the repository makes the CI check
deterministic and prevents a syntactically valid but structurally invalid
acceptance contract from passing the release guard.
"""

from __future__ import annotations

import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


class ValidationError(ValueError):
    """Raised when a JSON value does not satisfy the supplied schema."""


def resolve_pointer(root: dict[str, Any], reference: str) -> dict[str, Any]:
    if not reference.startswith("#/"):
        raise ValidationError(f"unsupported_ref:{reference}")
    current: Any = root
    for token in reference[2:].split("/"):
        token = token.replace("~1", "/").replace("~0", "~")
        if not isinstance(current, dict) or token not in current:
            raise ValidationError(f"unresolved_ref:{reference}")
        current = current[token]
    if not isinstance(current, dict):
        raise ValidationError(f"ref_not_schema:{reference}")
    return current


def check_type(value: Any, expected: str) -> bool:
    if expected == "object":
        return isinstance(value, dict)
    if expected == "array":
        return isinstance(value, list)
    if expected == "string":
        return isinstance(value, str)
    if expected == "integer":
        return isinstance(value, int) and not isinstance(value, bool)
    if expected == "boolean":
        return isinstance(value, bool)
    if expected == "number":
        return isinstance(value, (int, float)) and not isinstance(value, bool)
    if expected == "null":
        return value is None
    raise ValidationError(f"unsupported_type:{expected}")


def validate(value: Any, schema: dict[str, Any], root: dict[str, Any], path: str = "$") -> None:
    if "$ref" in schema:
        validate(value, resolve_pointer(root, schema["$ref"]), root, path)

    if "const" in schema and value != schema["const"]:
        raise ValidationError(f"{path}:const_mismatch")

    if "enum" in schema and value not in schema["enum"]:
        raise ValidationError(f"{path}:enum_mismatch")

    expected_type = schema.get("type")
    if expected_type is not None and not check_type(value, expected_type):
        raise ValidationError(f"{path}:type_mismatch:{expected_type}")

    if isinstance(value, str):
        if len(value) < schema.get("minLength", 0):
            raise ValidationError(f"{path}:min_length")
        pattern = schema.get("pattern")
        if pattern is not None and re.search(pattern, value) is None:
            raise ValidationError(f"{path}:pattern_mismatch")
        if schema.get("format") == "date-time":
            try:
                datetime.fromisoformat(value.replace("Z", "+00:00"))
            except ValueError as error:
                raise ValidationError(f"{path}:date_time_mismatch") from error

    if isinstance(value, (int, float)) and not isinstance(value, bool):
        if value < schema.get("minimum", value):
            raise ValidationError(f"{path}:minimum_mismatch")

    if isinstance(value, list):
        if len(value) < schema.get("minItems", 0):
            raise ValidationError(f"{path}:min_items")
        item_schema = schema.get("items")
        if item_schema is not None:
            for index, item in enumerate(value):
                validate(item, item_schema, root, f"{path}[{index}]")

    if isinstance(value, dict):
        required = schema.get("required", [])
        for key in required:
            if key not in value:
                raise ValidationError(f"{path}:missing_required:{key}")

        properties = schema.get("properties", {})
        if schema.get("additionalProperties") is False:
            unexpected = sorted(set(value) - set(properties))
            if unexpected:
                raise ValidationError(f"{path}:unexpected_properties:{','.join(unexpected)}")
        for key, child_schema in properties.items():
            if key in value:
                validate(value[key], child_schema, root, f"{path}.{key}")


def main() -> int:
    if len(sys.argv) != 3:
        print("usage: validate-json-schema.py DOCUMENT SCHEMA", file=sys.stderr)
        return 2

    document_path = Path(sys.argv[1])
    schema_path = Path(sys.argv[2])
    try:
        document = json.loads(document_path.read_text(encoding="utf-8"))
        schema = json.loads(schema_path.read_text(encoding="utf-8"))
        validate(document, schema, schema)
    except (OSError, json.JSONDecodeError, ValidationError) as error:
        print(f"json_schema.error={error}", file=sys.stderr)
        return 1

    print("json_schema.status=PASS")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
