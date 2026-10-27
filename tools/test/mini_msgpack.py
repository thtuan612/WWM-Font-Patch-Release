"""Independent msgpack packer/unpacker written straight from the spec (used only because the real
library cannot be installed in the offline sandbox). Mirrors the msgpack-python API we need."""
import struct

def packb(o, use_bin_type=True):
    if o is None: return b"\xc0"
    if o is True: return b"\xc3"
    if o is False: return b"\xc2"
    if isinstance(o, int):
        if 0 <= o < 128: return bytes([o])
        if -32 <= o < 0: return struct.pack("b", o)
        if o >= 0:
            for code, fmt, lim in ((0xcc, ">B", 1 << 8), (0xcd, ">H", 1 << 16), (0xce, ">I", 1 << 32), (0xcf, ">Q", 1 << 64)):
                if o < lim: return bytes([code]) + struct.pack(fmt, o)
        for code, fmt, lim in ((0xd0, ">b", 1 << 7), (0xd1, ">h", 1 << 15), (0xd2, ">i", 1 << 31), (0xd3, ">q", 1 << 63)):
            if -lim <= o: return bytes([code]) + struct.pack(fmt, o)
        raise ValueError(o)
    if isinstance(o, float): return b"\xcb" + struct.pack(">d", o)
    if isinstance(o, str):
        b = o.encode("utf-8"); n = len(b)
        if n < 32: return bytes([0xa0 | n]) + b
        if n < 256: return b"\xd9" + bytes([n]) + b
        if n < 65536: return b"\xda" + struct.pack(">H", n) + b
        return b"\xdb" + struct.pack(">I", n) + b
    if isinstance(o, bytes):
        n = len(o)
        if n < 256: return b"\xc4" + bytes([n]) + o
        if n < 65536: return b"\xc5" + struct.pack(">H", n) + o
        return b"\xc6" + struct.pack(">I", n) + o
    if isinstance(o, list):
        n = len(o); h = bytes([0x90 | n]) if n < 16 else (b"\xdc" + struct.pack(">H", n))
        return h + b"".join(packb(x) for x in o)
    if isinstance(o, dict):
        n = len(o); h = bytes([0x80 | n]) if n < 16 else (b"\xde" + struct.pack(">H", n))
        return h + b"".join(packb(k) + packb(v) for k, v in o.items())
    raise TypeError(type(o))

def unpackb(b, raw=False):
    """Only strings are needed for verification of what Java wrote."""
    t = b[0]
    if 0xa0 <= t <= 0xbf: return b[1:1 + (t - 0xa0)].decode()
    if t == 0xd9: return b[2:2 + b[1]].decode()
    if t == 0xda: n = struct.unpack(">H", b[1:3])[0]; return b[3:3 + n].decode()
    if t == 0xdb: n = struct.unpack(">I", b[1:5])[0]; return b[5:5 + n].decode()
    raise ValueError("not a str: %#x" % t)

# Vectors straight from the msgpack specification text
assert packb("abc") == bytes.fromhex("a3616263")
assert packb("x" * 31)[:1] == b"\xbf" and packb("x" * 32)[:2] == b"\xd9\x20"
assert packb("x" * 256)[:3] == b"\xda\x01\x00" and packb("x" * 65536)[:5] == b"\xdb\x00\x01\x00\x00"
assert packb(None) == b"\xc0" and packb(True) == b"\xc3" and packb(-1) == b"\xff" and packb(255) == b"\xcc\xff"
assert packb({"a": 1}) == bytes.fromhex("81a16101") and packb([1, 2]) == bytes.fromhex("920102")
