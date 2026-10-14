"""A tiny NBT reader/writer (just what structure templates need), so dungeon pieces can be built from Python.

Python values map to NBT like this when writing:
    bool/int        -> Int            (use Byte(...), Short(...), Long(...) to force another size)
    float           -> Double         (use Float(...) for a 32-bit float)
    str             -> String
    dict            -> Compound
    list            -> List (element type from the first item; empty lists are lists of End)
    IntArray(...), LongArray(...), ByteArray(...) for the array tags.

    write_file("piece.nbt", {"DataVersion": 5023, ...})      # gzip-compressed, root name ""
    data = read_file("piece.nbt")                             # back to plain Python (sized ints come back as int)
"""
import gzip
import io
import struct

END, BYTE, SHORT, INT, LONG, FLOAT, DOUBLE, BYTE_ARRAY, STRING, LIST, COMPOUND, INT_ARRAY, LONG_ARRAY = range(13)


class Byte(int):
    pass


class Short(int):
    pass


class Long(int):
    pass


class Float(float):
    pass


class ByteArray(list):
    pass


class IntArray(list):
    pass


class LongArray(list):
    pass


def _tag_of(value):
    if isinstance(value, bool):
        return BYTE
    if isinstance(value, Byte):
        return BYTE
    if isinstance(value, Short):
        return SHORT
    if isinstance(value, Long):
        return LONG
    if isinstance(value, int):
        return INT
    if isinstance(value, Float):
        return FLOAT
    if isinstance(value, float):
        return DOUBLE
    if isinstance(value, str):
        return STRING
    if isinstance(value, ByteArray):
        return BYTE_ARRAY
    if isinstance(value, IntArray):
        return INT_ARRAY
    if isinstance(value, LongArray):
        return LONG_ARRAY
    if isinstance(value, dict):
        return COMPOUND
    if isinstance(value, (list, tuple)):
        return LIST
    raise TypeError(f"can't store {type(value).__name__} in NBT: {value!r}")


def _write_string(out, text):
    raw = text.encode("utf-8")
    out.write(struct.pack(">H", len(raw)))
    out.write(raw)


def _write_payload(out, tag, value):
    if tag == BYTE:
        out.write(struct.pack(">b", int(value)))
    elif tag == SHORT:
        out.write(struct.pack(">h", value))
    elif tag == INT:
        out.write(struct.pack(">i", value))
    elif tag == LONG:
        out.write(struct.pack(">q", value))
    elif tag == FLOAT:
        out.write(struct.pack(">f", value))
    elif tag == DOUBLE:
        out.write(struct.pack(">d", value))
    elif tag == STRING:
        _write_string(out, value)
    elif tag == BYTE_ARRAY:
        out.write(struct.pack(">i", len(value)))
        out.write(struct.pack(f">{len(value)}b", *value))
    elif tag == INT_ARRAY:
        out.write(struct.pack(">i", len(value)))
        out.write(struct.pack(f">{len(value)}i", *value))
    elif tag == LONG_ARRAY:
        out.write(struct.pack(">i", len(value)))
        out.write(struct.pack(f">{len(value)}q", *value))
    elif tag == LIST:
        inner = _tag_of(value[0]) if value else END
        out.write(struct.pack(">bi", inner, len(value)))
        for item in value:
            _write_payload(out, inner, item)
    elif tag == COMPOUND:
        for key, item in value.items():
            item_tag = _tag_of(item)
            out.write(struct.pack(">b", item_tag))
            _write_string(out, key)
            _write_payload(out, item_tag, item)
        out.write(struct.pack(">b", END))


def dumps(root):
    out = io.BytesIO()
    out.write(struct.pack(">b", COMPOUND))
    _write_string(out, "")
    _write_payload(out, COMPOUND, root)
    return out.getvalue()


def write_file(path, root):
    with open(path, "wb") as f:
        # mtime=0 keeps the file byte-identical between runs with the same content
        with gzip.GzipFile(fileobj=f, mode="wb", mtime=0) as gz:
            gz.write(dumps(root))


class _Reader:
    def __init__(self, data):
        self.data = data
        self.pos = 0

    def take(self, fmt):
        size = struct.calcsize(fmt)
        values = struct.unpack_from(fmt, self.data, self.pos)
        self.pos += size
        return values

    def string(self):
        (length,) = self.take(">H")
        raw = self.data[self.pos:self.pos + length]
        self.pos += length
        return raw.decode("utf-8")

    def payload(self, tag):
        if tag == BYTE:
            return self.take(">b")[0]
        if tag == SHORT:
            return self.take(">h")[0]
        if tag == INT:
            return self.take(">i")[0]
        if tag == LONG:
            return self.take(">q")[0]
        if tag == FLOAT:
            return self.take(">f")[0]
        if tag == DOUBLE:
            return self.take(">d")[0]
        if tag == STRING:
            return self.string()
        if tag in (BYTE_ARRAY, INT_ARRAY, LONG_ARRAY):
            (n,) = self.take(">i")
            code = {BYTE_ARRAY: "b", INT_ARRAY: "i", LONG_ARRAY: "q"}[tag]
            return list(self.take(f">{n}{code}"))
        if tag == LIST:
            inner, n = self.take(">bi")
            return [self.payload(inner) for _ in range(n)]
        if tag == COMPOUND:
            result = {}
            while True:
                (item_tag,) = self.take(">b")
                if item_tag == END:
                    return result
                key = self.string()
                result[key] = self.payload(item_tag)
        raise ValueError(f"unknown NBT tag {tag}")


def loads(data):
    reader = _Reader(data)
    (tag,) = reader.take(">b")
    reader.string()
    return reader.payload(tag)


def read_file(path):
    with open(path, "rb") as f:
        raw = f.read()
    if raw[:2] == b"\x1f\x8b":
        raw = gzip.decompress(raw)
    return loads(raw)
