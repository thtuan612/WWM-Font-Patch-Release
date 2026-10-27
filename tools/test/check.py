import subprocess, sys, os, tempfile, random
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
import mini_msgpack as msgpack
KEY = b"netease_hex_xor_encrypt_key"
def xor(b): return bytes(c ^ KEY[i % len(KEY)] for i, c in enumerate(b))
def enc(s): return xor(msgpack.packb(s, use_bin_type=True))     # what the game stores
def dec(b): return msgpack.unpackb(xor(b), raw=False)
sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
here = os.path.dirname(os.path.abspath(__file__)); src = os.path.join(here, "..", "..", "app/src/main/java/local/wwm/fontpatch")
out = tempfile.mkdtemp(); 
subprocess.check_call(["javac", "-d", out, *[os.path.join(src, f) for f in ("Codec.java","Lang.java","PatchModel.java","RowStore.java","HotfixEditor.java","UpdateGuard.java","FontCheck.java")], os.path.join(here, "RunPatch.java"), os.path.join(here, "RunFps.java"), os.path.join(here, "RunGuard.java")])

# 1) codec cross-check against real msgpack, all four str header sizes
subprocess.run(["true"])
import json
long_lua = "local x = 1\n" + "-- pad é ư ơ 越南\n" * 3000          # > 65535 bytes -> str32
cases = ["", "a", "x"*31, "x"*32, "y"*255, "z"*256, "ü"*40000, long_lua]
for s in cases: assert len(msgpack.packb(s, use_bin_type=True)) > 0
# Java side checks encode == msgpack.packb by reading hex vectors
vec = os.path.join(out, "vec.tsv"); open(vec, "w").write("".join(f"{i}\t{msgpack.packb(s,use_bin_type=True).hex()}\t{s.encode().hex()}\n" for i, s in enumerate(cases)))
open(os.path.join(out, "V.java"), "w").write('''
import local.wwm.fontpatch.*; import java.nio.file.*; import java.util.*; import java.nio.charset.StandardCharsets;
public class V { static byte[] unhex(String h){byte[] b=new byte[h.length()/2];for(int i=0;i<b.length;i++)b[i]=(byte)Integer.parseInt(h.substring(2*i,2*i+2),16);return b;}
 public static void main(String[] a) throws Exception { int n=0; for(String l: Files.readAllLines(Paths.get(a[0]))){ String[] p=l.split("\\t",-1); byte[] packed=unhex(p[1]); String s=new String(unhex(p[2]),StandardCharsets.UTF_8);
  if(!Arrays.equals(Codec.encodeString(s),packed)) throw new RuntimeException("encode mismatch case "+p[0]);
  if(!Codec.decodeString(packed).equals(s)) throw new RuntimeException("decode mismatch case "+p[0]);
  if(!Codec.decodeValue(packed).equals(s)) throw new RuntimeException("decodeValue mismatch case "+p[0]); n++; }
  Object v=Codec.decodeValue(unhex(a[1])); System.out.println("generic decode: "+v); System.out.println("codec ok "+n); } }''')
meta = {"hotfix_id_d": {"1001": 1, "1002": 2, "1003": 3, "9": [1, 2.5, None, True, -5, 70000, 2**40, b"bin"]}, "ver": 12, "neg": -33}
subprocess.check_call(["javac", "-cp", out, "-d", out, os.path.join(out, "V.java")])
subprocess.check_call(["java", "-cp", out, "V", vec, msgpack.packb(meta, use_bin_type=True).hex()])

# 2) fake db_mp: meta + hotfix rows; 1001 is tiny (<100 bytes), 1002 has no "local ", 1003 is the first usable one
lua_other = "-- other patch (kept)\nlocal WWMVH=0;\nlocal a = 1\nlocal WWMVHEND=0;\n"
rows = {
 "hotfix_metaglobal": xor(msgpack.packb(meta, use_bin_type=True)),
 "hotfix_1001global": enc("local a=1"),
 "hotfix_1002global": enc("print('no locals here') " * 30),
 "hotfix_1003global": enc("function f() return 1 end\n" + "local t = {}\n" * 12 + lua_other),
 "hotfix_1004global": enc("local zzz = 2\n" * 20),
 "other_row": b"\x01\x02",
}
def write(path, rows): open(path, "w").write("".join(f"{k}\t{v.hex()}\n" for k, v in rows.items()))
def read(path): return {l.split("\t")[0]: bytes.fromhex(l.rstrip("\n").split("\t")[1]) for l in open(path)}
fin, fout = os.path.join(out, "in.tsv"), os.path.join(out, "out.tsv"); write(fin, rows)

def run(pkg, fonts, forced=None):
    r = subprocess.run(["java", "-cp", out, "RunPatch", fin, fout, pkg, *fonts, *([forced] if forced else [])], capture_output=True, text=True)
    print(r.stdout.strip()); assert r.returncode == 0, r.stderr
    return read(fout)

res = run("com.netease.yysls", ["My Font", "-", "Genshin"])
assert "candidates=[hotfix_1003global]" in subprocess.run(["java","-cp",out,"RunPatch",fin,fout,"com.netease.yysls","a","-","-"],capture_output=True,text=True).stdout
assert res["hotfix_1001global"] == rows["hotfix_1001global"] and res["hotfix_1002global"] == rows["hotfix_1002global"]
assert res["hotfix_1004global"] == rows["hotfix_1004global"] and res["other_row"] == rows["other_row"] and res["hotfix_metaglobal"] == rows["hotfix_metaglobal"]
lua = dec(res["hotfix_1003global"])
assert lua.count("[FONTPATCH:BEGIN]") == 1 and lua.count("[FONTPATCH:END]") == 1, "idempotency"
assert lua.startswith("function f()") and lua_other.strip() in lua, "original script preserved"
for needle in ["ov('AlegreyaSans-Regular.ttf', 'myfont.ttf')", "ov('FZShengSKSJW_Cu.TTF', 'myfont.ttf')", "ov('fzht.ttf', 'myfont.ttf')",
               "ov('MrsEavSmaCap.ttf', 'genshin.ttf')", "ov('ChillLongCangKaiShu_Bold.ttf', 'genshin.ttf')"]:
    assert needle in lua, needle
assert "AlegreyaSans-Medium" not in lua   # slot 2 left alone
print("---- appended block ----"); print(lua[lua.index("-- [FONTPATCH:BEGIN]"):])

# Chinese package uses Chinese names; forced id works; stored bytes decode with the real msgpack lib
res = run("com.netease.yyslscn", ["A", "B", "C"], "hotfix_1004global")
lua = dec(res["hotfix_1004global"]); assert "FZSKBXKJW.TTF" in lua and "AaShiSongTi-2.ttf" in lua and "ZHJB-Xiangjiahong.TTF" in lua
assert dec(res["hotfix_1003global"]) == dec(rows["hotfix_1003global"])
# meta missing -> scan fallback
del rows["hotfix_metaglobal"]; write(fin, rows); res = run("com.netease.yyvn", ["A", "-", "-"])
assert "FONTPATCH:BEGIN" in dec(res["hotfix_1003global"])
r = subprocess.run(["java", "-cp", out, "RunFps"], capture_output=True, text=True); print(r.stdout.strip()); assert r.returncode == 0, r.stderr
r = subprocess.run(["java", "-cp", out, "local.wwm.fontpatch.RunGuard", os.path.join(src, "..", "..", "..", "..", "assets", "fonts")], capture_output=True, text=True); print(r.stdout.strip()); assert r.returncode == 0, r.stdout + r.stderr
print("ALL TESTS PASSED")
