"""Resolves item model ids to PNG bytes: mod assets from the repo, vanilla assets from the Minecraft client jar."""
import glob
import json
import os
import zipfile

JAR_CANDIDATES = [
    "~/.gradle/caches/fabric-loom/26.3/minecraft-client.jar",
    "~/.gradle/caches/fabric-loom/26.3/minecraft-merged.jar",
    "~/.gradle/caches/fabric-loom/26.3/minecraft-client-only.jar",
]

# Texture keys tried in order: flat items first, then the faces of block models.
TEXTURE_KEYS = ["layer0", "all", "texture", "side", "front", "top", "end", "cross", "plant", "up", "north", "particle_nope"]


def split_id(rid, default="minecraft"):
    if ":" in rid:
        ns, path = rid.split(":", 1)
        return ns, path
    return default, rid


class AssetStore:
    def __init__(self, repo, cache_dir):
        self.repo = repo
        self.cache = cache_dir
        self.roots = {}  # namespace -> [asset root dirs]
        for p in glob.glob(os.path.join(repo, "*", "src", "*", "resources", "assets", "*")):
            ns = os.path.basename(p)
            if ns == "minecraft":
                continue
            self.roots.setdefault(ns, []).append(p)
        self.jar = None
        for c in JAR_CANDIDATES:
            c = os.path.expanduser(c)
            if os.path.exists(c):
                self.jar = zipfile.ZipFile(c)
                break
        self.memo = {}

    def read(self, ns, rel):
        """Bytes of assets/<ns>/<rel>, or None."""
        key = (ns, rel)
        if key in self.memo:
            return self.memo[key]
        data = None
        if ns == "minecraft":
            cp = os.path.join(self.cache, "vanilla", rel.replace("/", os.sep))
            if os.path.exists(cp):
                with open(cp, "rb") as f:
                    data = f.read()
            elif self.jar is not None:
                try:
                    data = self.jar.read("assets/minecraft/" + rel)
                except KeyError:
                    data = None
                if data is not None:
                    os.makedirs(os.path.dirname(cp), exist_ok=True)
                    with open(cp, "wb") as f:
                        f.write(data)
        else:
            for root in self.roots.get(ns, []):
                p = os.path.join(root, rel.replace("/", os.sep))
                if os.path.exists(p):
                    with open(p, "rb") as f:
                        data = f.read()
                    break
        self.memo[key] = data
        return data

    def read_json(self, ns, rel):
        data = self.read(ns, rel)
        if data is None:
            return None
        try:
            return json.loads(data.decode("utf-8"))
        except ValueError:
            return None

    def vanilla_lang(self):
        d = self.read_json("minecraft", "lang/en_us.json")
        return d or {}


def first_model(node):
    """The first model id in an item definition (assets/<ns>/items/<id>.json), following the usual wrappers."""
    if isinstance(node, str):
        return node
    if isinstance(node, list):
        for n in node:
            r = first_model(n)
            if r:
                return r
        return None
    if not isinstance(node, dict):
        return None
    t = node.get("type", "")
    if isinstance(node.get("model"), str):
        return node["model"]
    for key in ("model", "fallback", "on_false", "on_true", "base", "models", "cases", "entries"):
        if key in node:
            r = first_model(node[key])
            if r:
                return r
    return None


class IconResolver:
    def __init__(self, assets):
        self.a = assets
        self.cache = {}

    def model_textures(self, model_id, depth=0):
        """Merged textures dict for a model id following parents; also returns whether it is a builtin/entity model."""
        if depth > 8:
            return {}, False
        ns, path = split_id(model_id)
        m = self.a.read_json(ns, "models/%s.json" % path)
        if m is None:
            return {}, False
        textures = dict(m.get("textures", {}))
        builtin = False
        parent = m.get("parent")
        if parent:
            if parent.endswith("builtin/entity"):
                builtin = True
            else:
                pt, pb = self.model_textures(parent, depth + 1)
                builtin = pb
                for k, v in pt.items():
                    textures.setdefault(k, v)
        return textures, builtin

    def pick_texture(self, textures):
        def resolve(v, n=0):
            if isinstance(v, dict):
                v = v.get("sprite")
            while isinstance(v, str) and v.startswith("#") and n < 8:
                v = textures.get(v[1:])
                n += 1
            return v if isinstance(v, str) and not v.startswith("#") else None

        for key in TEXTURE_KEYS:
            if key in textures:
                r = resolve(textures[key])
                if r:
                    return r
        for key, v in textures.items():
            if key == "particle":
                continue
            r = resolve(v)
            if r:
                return r
        return None

    def resolve(self, item_id):
        """PNG bytes for an item id / model id, or None."""
        if item_id in self.cache:
            return self.cache[item_id]
        png = self._resolve(item_id)
        self.cache[item_id] = png
        return png

    def _resolve(self, item_id):
        ns, path = split_id(item_id)
        candidates = []
        d = self.a.read_json(ns, "items/%s.json" % path)
        if d is not None:
            mid = first_model(d.get("model", d))
            if mid:
                candidates.append(mid)
        candidates.append("%s:item/%s" % (ns, path))
        candidates.append("%s:block/%s" % (ns, path))
        for mid in candidates:
            textures, builtin = self.model_textures(mid)
            if builtin:
                continue
            tex = self.pick_texture(textures)
            if not tex:
                continue
            tns, tpath = split_id(tex)
            png = self.a.read(tns, "textures/%s.png" % tpath)
            if png:
                return png
        return None
