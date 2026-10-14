"""Reads a mod jar's fabric.mod.json and checks that it really accepts the game version.

Modrinth's game_versions tags are written by hand and several mods tag old builds as 26.3, so publish_pack.py
downloads each jar and checks `depends` itself (the jar and the jars nested inside it, which Fabric also resolves).
Version predicates follow Fabric's semver syntax closely enough for Minecraft: >=, >, <=, <, =, ~, ^, x-ranges
(1.19.x), a bare version, `*`, space-separated AND inside one string, and a list of strings as OR.
"""
import io
import json
import re
import zipfile


def parse_version(text):
    """'26.3' -> [26, 3]; '0.9.2+mc26.3' -> [0, 9, 2]; pre-release tags are dropped."""
    core = re.split(r"[+-]", text.strip(), maxsplit=1)[0]
    parts = []
    for piece in core.split("."):
        if piece.isdigit():
            parts.append(int(piece))
        elif piece in ("x", "X", "*"):
            parts.append(None)
        else:
            break
    return parts


def cmp(a, b):
    n = max(len(a), len(b))
    a = a + [0] * (n - len(a))
    b = b + [0] * (n - len(b))
    return (a > b) - (a < b)


def matches_one(version, predicate):
    predicate = predicate.strip()
    if predicate in ("", "*"):
        return True
    for op in (">=", "<=", ">", "<", "=", "~", "^"):
        if predicate.startswith(op):
            break
    else:
        op = ""
    target = parse_version(predicate[len(op):])
    if None in target:                      # 1.19.x
        fixed = target[:target.index(None)]
        return version[:len(fixed)] == fixed
    if op in ("", "="):
        return cmp(version, target) == 0
    if op == ">=":
        return cmp(version, target) >= 0
    if op == ">":
        return cmp(version, target) > 0
    if op == "<=":
        return cmp(version, target) <= 0
    if op == "<":
        return cmp(version, target) < 0
    if op == "~":                           # same minor (or same major if only one part)
        upper = target[:1] + [target[1] + 1] if len(target) > 1 else [target[0] + 1]
        return cmp(version, target) >= 0 and cmp(version, upper) < 0
    upper = [target[0] + 1]                 # ^ : same major
    return cmp(version, target) >= 0 and cmp(version, upper) < 0


def accepts(version_text, requirement):
    """requirement is a string or a list of strings (OR); inside a string, spaces mean AND."""
    version = parse_version(version_text)
    options = requirement if isinstance(requirement, list) else [requirement]
    return any(all(matches_one(version, p) for p in str(option).split()) for option in options)


def read_metadata(jar_bytes, label="jar", depth=0):
    """[(label, fabric.mod.json dict)] for the jar and the jars nested in it."""
    found = []
    with zipfile.ZipFile(io.BytesIO(jar_bytes)) as zf:
        if "fabric.mod.json" not in zf.namelist():
            return found
        meta = json.loads(zf.read("fabric.mod.json").decode("utf-8-sig"), strict=False)
        found.append((label, meta))
        if depth < 3:
            for nested in meta.get("jars", []):
                try:
                    found += read_metadata(zf.read(nested["file"]), nested["file"].rsplit("/", 1)[-1], depth + 1)
                except KeyError:
                    pass
    return found


def minecraft_problems(jar_bytes, minecraft, label):
    """Messages for every jar (outer or nested) whose depends.minecraft rejects `minecraft`; [] when fine."""
    problems = []
    for name, meta in read_metadata(jar_bytes, label):
        wanted = meta.get("depends", {}).get("minecraft")
        if wanted is not None and not accepts(minecraft, wanted):
            problems.append(f"{name} ({meta.get('id')} {meta.get('version')}) needs minecraft {wanted}")
    return problems


def dependency_problems(jar_bytes, label, installed):
    """installed: {mod id: version}. Messages for `depends` on a mod we ship that its version does not satisfy."""
    problems = []
    meta = read_metadata(jar_bytes, label)
    if not meta:
        return problems
    _, top = meta[0]
    for mod_id, wanted in top.get("depends", {}).items():
        if mod_id in installed and not accepts(installed[mod_id], wanted):
            problems.append(f"{label} needs {mod_id} {wanted}, the pack has {installed[mod_id]}")
    return problems


def ids_and_version(jar_bytes):
    """({id and provided ids}, version) of the outer mod."""
    meta = read_metadata(jar_bytes)
    if not meta:
        return set(), ""
    top = meta[0][1]
    return {top.get("id"), *[p if isinstance(p, str) else p.get("id") for p in top.get("provides", [])]}, str(top.get("version", ""))
