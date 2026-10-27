package local.wwm.fontpatch;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Pure Java (no Android deps). XOR + msgpack-string codec used by the game's localdb rows. */
public final class Codec {
    private static final byte[] KEY = "netease_hex_xor_encrypt_key".getBytes(StandardCharsets.UTF_8);

    private Codec() {}

    public static byte[] xor(byte[] in) {
        byte[] out = new byte[in.length];
        for (int i = 0; i < in.length; i++) out[i] = (byte) (in[i] ^ KEY[i % KEY.length]);
        return out;
    }

    /** msgpack str encoding (fixstr / str8 / str16 / str32). */
    public static byte[] encodeString(String s) {
        byte[] b = s.getBytes(StandardCharsets.UTF_8);
        int n = b.length;
        byte[] head;
        if (n < 32) head = new byte[]{(byte) (0xA0 | n)};
        else if (n < 256) head = new byte[]{(byte) 0xD9, (byte) n};
        else if (n < 65536) head = new byte[]{(byte) 0xDA, (byte) (n >> 8), (byte) n};
        else head = new byte[]{(byte) 0xDB, (byte) (n >> 24), (byte) (n >> 16), (byte) (n >> 8), (byte) n};
        byte[] out = new byte[head.length + n];
        System.arraycopy(head, 0, out, 0, head.length);
        System.arraycopy(b, 0, out, head.length, n);
        return out;
    }

    /** Same semantics as the original app: a str header is stripped, otherwise the whole array is UTF-8 text. */
    public static String decodeString(byte[] b) {
        if (b == null || b.length == 0) return "";
        int h = b[0] & 0xFF;
        int off, len;
        if (h >= 0xA0 && h <= 0xBF) { off = 1; len = h - 0xA0; }
        else if (h == 0xD9) { if (b.length < 2) return ""; off = 2; len = b[1] & 0xFF; }
        else if (h == 0xDA) { if (b.length < 3) return ""; off = 3; len = ((b[1] & 0xFF) << 8) | (b[2] & 0xFF); }
        else if (h == 0xDB) {
            if (b.length < 5) return "";
            off = 5;
            len = ((b[1] & 0xFF) << 24) | ((b[2] & 0xFF) << 16) | ((b[3] & 0xFF) << 8) | (b[4] & 0xFF);
        } else return new String(b, StandardCharsets.UTF_8);
        if (len < 0 || off + len > b.length) return "";
        return new String(b, off, len, StandardCharsets.UTF_8);
    }

    // ---- generic msgpack decoder (only needed to read hotfix_metaglobal) ----

    /** Returns LinkedHashMap / List / String / Long / Double / Boolean / byte[] / null. */
    public static Object decodeValue(byte[] b) {
        if (b == null || b.length == 0) return null;
        int[] p = {0};
        return parse(b, p);
    }

    private static long be(byte[] b, int[] p, int n) {
        if (p[0] + n > b.length) throw new IllegalArgumentException("truncated msgpack");
        long v = 0;
        for (int i = 0; i < n; i++) v = (v << 8) | (b[p[0]++] & 0xFF);
        return v;
    }

    private static String str(byte[] b, int[] p, int n) {
        if (n < 0 || p[0] + n > b.length) throw new IllegalArgumentException("truncated msgpack");
        String s = new String(b, p[0], n, StandardCharsets.UTF_8);
        p[0] += n;
        return s;
    }

    private static Object parse(byte[] b, int[] p) {
        int t = (int) be(b, p, 1);
        if (t <= 0x7F) return (long) t;
        if (t >= 0xE0) return (long) (byte) t;
        if (t >= 0x80 && t <= 0x8F) return map(b, p, t - 0x80);
        if (t >= 0x90 && t <= 0x9F) return arr(b, p, t - 0x90);
        if (t >= 0xA0 && t <= 0xBF) return str(b, p, t - 0xA0);
        switch (t) {
            case 0xC0: return null;
            case 0xC2: return Boolean.FALSE;
            case 0xC3: return Boolean.TRUE;
            case 0xC4: return bin(b, p, (int) be(b, p, 1));
            case 0xC5: return bin(b, p, (int) be(b, p, 2));
            case 0xC6: return bin(b, p, (int) be(b, p, 4));
            case 0xC7: { int n = (int) be(b, p, 1); be(b, p, 1); skip(b, p, n); return null; }
            case 0xC8: { int n = (int) be(b, p, 2); be(b, p, 1); skip(b, p, n); return null; }
            case 0xC9: { int n = (int) be(b, p, 4); be(b, p, 1); skip(b, p, n); return null; }
            case 0xCA: return (double) Float.intBitsToFloat((int) be(b, p, 4));
            case 0xCB: return Double.longBitsToDouble(be(b, p, 8));
            case 0xCC: return be(b, p, 1);
            case 0xCD: return be(b, p, 2);
            case 0xCE: return be(b, p, 4);
            case 0xCF: return be(b, p, 8);
            case 0xD0: return (long) (byte) be(b, p, 1);
            case 0xD1: return (long) (short) be(b, p, 2);
            case 0xD2: return (long) (int) be(b, p, 4);
            case 0xD3: return be(b, p, 8);
            case 0xD4: be(b, p, 1); skip(b, p, 1); return null;
            case 0xD5: be(b, p, 1); skip(b, p, 2); return null;
            case 0xD6: be(b, p, 1); skip(b, p, 4); return null;
            case 0xD7: be(b, p, 1); skip(b, p, 8); return null;
            case 0xD8: be(b, p, 1); skip(b, p, 16); return null;
            case 0xD9: return str(b, p, (int) be(b, p, 1));
            case 0xDA: return str(b, p, (int) be(b, p, 2));
            case 0xDB: return str(b, p, (int) be(b, p, 4));
            case 0xDC: return arr(b, p, (int) be(b, p, 2));
            case 0xDD: return arr(b, p, (int) be(b, p, 4));
            case 0xDE: return map(b, p, (int) be(b, p, 2));
            case 0xDF: return map(b, p, (int) be(b, p, 4));
            default: throw new IllegalArgumentException("bad msgpack type " + t);
        }
    }

    private static void skip(byte[] b, int[] p, int n) {
        if (n < 0 || p[0] + n > b.length) throw new IllegalArgumentException("truncated msgpack");
        p[0] += n;
    }

    private static byte[] bin(byte[] b, int[] p, int n) {
        if (n < 0 || p[0] + n > b.length) throw new IllegalArgumentException("truncated msgpack");
        byte[] r = new byte[n];
        System.arraycopy(b, p[0], r, 0, n);
        p[0] += n;
        return r;
    }

    private static List<Object> arr(byte[] b, int[] p, int n) {
        List<Object> l = new ArrayList<>();
        for (int i = 0; i < n; i++) l.add(parse(b, p));
        return l;
    }

    private static Map<String, Object> map(byte[] b, int[] p, int n) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            Object k = parse(b, p);
            m.put(String.valueOf(k), parse(b, p));
        }
        return m;
    }
}
