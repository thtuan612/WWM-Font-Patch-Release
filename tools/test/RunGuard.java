package local.wwm.fontpatch;

import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.IOException;
import java.io.OutputStream;
import java.io.RandomAccessFile;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Pure-Java checks for UpdateGuard (updater safety), FontCheck (font import) and PatchModel.deployedName. */
public class RunGuard {
    static int failed = 0, passed = 0;

    static void ok(boolean cond, String what) {
        if (cond) { passed++; } else { failed++; System.out.println("FAIL: " + what); }
    }

    static void throwsIO(Fx f, String what) {
        try { f.run(); failed++; System.out.println("FAIL (no exception): " + what); }
        catch (IOException e) { passed++; }
        catch (Exception e) { failed++; System.out.println("FAIL (wrong exception " + e + "): " + what); }
    }

    interface Fx { void run() throws Exception; }

    public static void main(String[] a) throws Exception {
        File fontsDir = new File(a[0]);
        urlChecks();
        digestChecks();
        fetchChecks();
        fontChecks(fontsDir);
        nameChecks();
        System.out.println("RunGuard: " + passed + " passed, " + failed + " failed");
        if (failed > 0) System.exit(1);
    }

    // ------------------------------------------------------------ URL / digest helpers
    static void urlChecks() {
        String good = "https://github.com/thtuan612/WWM-Font-Patch-Release/releases/download/latest/app-released-1.0.0.apk";
        ok(UpdateGuard.isTrustedStartUrl(good), "good start url");
        ok(!UpdateGuard.isTrustedStartUrl(null), "null url");
        ok(!UpdateGuard.isTrustedStartUrl(""), "empty url");
        ok(!UpdateGuard.isTrustedStartUrl("not a url"), "garbage url");
        ok(!UpdateGuard.isTrustedStartUrl(good.replace("https:", "http:")), "http scheme");
        ok(!UpdateGuard.isTrustedStartUrl("https://github.com.evil.com/thtuan612/WWM-Font-Patch-Release/releases/download/latest/a.apk"), "lookalike host");
        ok(!UpdateGuard.isTrustedStartUrl("https://github.com@evil.com/thtuan612/WWM-Font-Patch-Release/releases/download/latest/a.apk"), "userinfo trick");
        ok(!UpdateGuard.isTrustedStartUrl("https://evil.com/thtuan612/WWM-Font-Patch-Release/releases/download/latest/a.apk"), "other host");
        ok(!UpdateGuard.isTrustedStartUrl("https://github.com/evil/WWM-Font-Patch-Release/releases/download/latest/a.apk"), "other owner");
        ok(!UpdateGuard.isTrustedStartUrl("https://github.com/thtuan612/WWM-Font-Patch-Release/releases/download/../../../x/a.apk"), "path traversal");
        ok(!UpdateGuard.isTrustedStartUrl("https://github.com/thtuan612/WWM-Font-Patch-Release/releases/download/latest/a.zip"), "not apk");
        ok(!UpdateGuard.isTrustedStartUrl("https://github.com:8443/thtuan612/WWM-Font-Patch-Release/releases/download/latest/a.apk"), "odd port");

        ok(hop("https://github.com/x"), "hop github");
        ok(hop("https://objects.githubusercontent.com/abc"), "hop objects cdn");
        ok(hop("https://release-assets.githubusercontent.com/abc"), "hop release-assets cdn");
        ok(!hop("http://objects.githubusercontent.com/abc"), "hop http");
        ok(!hop("https://evilgithubusercontent.com/abc"), "hop lookalike cdn");
        ok(!hop("https://objects.githubusercontent.com.evil.com/abc"), "hop suffix trick");
        ok(!hop("https://githubusercontent.com@evil.com/abc"), "hop userinfo");
        ok(!hop("https://example.com/abc"), "hop other host");
    }

    static boolean hop(String u) {
        try { return UpdateGuard.isTrustedHop(new java.net.URL(u)); } catch (Exception e) { return false; }
    }

    static void digestChecks() {
        String h = "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad";
        ok(h.equals(UpdateGuard.parseSha256("sha256:" + h)), "parse sha256");
        ok(h.equals(UpdateGuard.parseSha256("  SHA256:" + h.toUpperCase() + " ")), "parse sha256 upper/space");
        ok(UpdateGuard.parseSha256(h) == null, "bare hex rejected");
        ok(UpdateGuard.parseSha256("sha256:abc") == null, "short hex");
        ok(UpdateGuard.parseSha256("md5:" + h) == null, "wrong algorithm");
        ok(UpdateGuard.parseSha256(null) == null, "null digest");
        String t1 = UpdateGuard.newToken(), t2 = UpdateGuard.newToken();
        ok(t1.length() == 32 && !t1.equals(t2), "token random");
        ok(UpdateGuard.constantTimeEquals(t1, t1) && !UpdateGuard.constantTimeEquals(t1, t2)
                && !UpdateGuard.constantTimeEquals(null, t1) && !UpdateGuard.constantTimeEquals(t1, null), "token compare");
    }

    // ------------------------------------------------------------ download (local http server)
    static byte[] body = new byte[5000];

    static void fetchChecks() throws Exception {
        for (int i = 0; i < body.length; i++) body[i] = (byte) (i * 31 + 7);
        String sha = UpdateGuard.hex(MessageDigest.getInstance("SHA-256").digest(body));
        HttpServer s = HttpServer.create(new InetSocketAddress(InetAddress.getByName("127.0.0.1"), 0), 0);
        final int port = s.getAddress().getPort();
        s.createContext("/ok", x -> { x.sendResponseHeaders(200, body.length); try (OutputStream o = x.getResponseBody()) { o.write(body); } });
        s.createContext("/chunked", x -> { x.sendResponseHeaders(200, 0); try (OutputStream o = x.getResponseBody()) { o.write(body); } });
        s.createContext("/redir", x -> { x.getResponseHeaders().add("Location", "/ok"); x.sendResponseHeaders(302, -1); x.close(); });
        s.createContext("/evil", x -> { x.getResponseHeaders().add("Location", "http://localhost:" + port + "/ok"); x.sendResponseHeaders(302, -1); x.close(); });
        s.createContext("/loop", x -> { x.getResponseHeaders().add("Location", "/loop"); x.sendResponseHeaders(302, -1); x.close(); });
        s.createContext("/missing", x -> { x.sendResponseHeaders(404, -1); x.close(); });
        s.start();
        String base = "http://127.0.0.1:" + port;
        java.util.function.Predicate<java.net.URL> trust = u -> u.getHost().equals("127.0.0.1");   // test-only: allow http loopback
        File d = Files.createTempDirectory("guard").toFile();
        try {
            File f = new File(d, "u.apk");
            int[] last = {-1};
            long n = UpdateGuard.fetch(base + "/ok", f, 1 << 20, sha, trust, p -> last[0] = p);
            ok(n == body.length && Arrays.equals(Files.readAllBytes(f.toPath()), body), "fetch ok");
            ok(last[0] == 100, "progress reaches 100");
            f.delete();
            UpdateGuard.fetch(base + "/redir", f, 1 << 20, sha, trust, null);
            ok(f.isFile(), "fetch follows trusted redirect");
            f.delete();

            throwsIO(() -> UpdateGuard.fetch(base + "/ok", f, 1 << 20, sha.replace(sha.charAt(0), sha.charAt(0) == '0' ? '1' : '0'), trust, null), "wrong sha rejected");
            ok(!f.exists(), "file removed after sha mismatch");
            throwsIO(() -> UpdateGuard.fetch(base + "/ok", f, 1 << 20, null, trust, null), "missing sha rejected");
            throwsIO(() -> UpdateGuard.fetch(base + "/ok", f, 1 << 20, "abc", trust, null), "malformed sha rejected");
            ok(!f.exists(), "no file when sha missing");
            throwsIO(() -> UpdateGuard.fetch(base + "/evil", f, 1 << 20, sha, trust, null), "redirect to untrusted host rejected");
            ok(!f.exists(), "no file after untrusted redirect");
            throwsIO(() -> UpdateGuard.fetch(base + "/loop", f, 1 << 20, sha, trust, null), "redirect loop rejected");
            throwsIO(() -> UpdateGuard.fetch(base + "/ok", f, 1000, sha, trust, null), "declared size over cap rejected");
            ok(!f.exists(), "no file when declared size over cap");
            throwsIO(() -> UpdateGuard.fetch(base + "/chunked", f, 1000, sha, trust, null), "streamed size over cap rejected");
            ok(!f.exists(), "file removed after streamed size over cap");
            throwsIO(() -> UpdateGuard.fetch(base + "/missing", f, 1 << 20, sha, trust, null), "HTTP 404 rejected");
            throwsIO(() -> UpdateGuard.fetch("https://example.com/x.apk", f, 1 << 20, sha, UpdateGuard::isTrustedHop, null), "untrusted start host rejected (no network used)");
        } finally {
            s.stop(0);
            for (File x : d.listFiles()) x.delete();
            d.delete();
        }
    }

    // ------------------------------------------------------------ fonts
    /** Minimal synthetic sfnt: header + table records pointing into a zero payload. */
    static byte[] sfnt(int magic, String... tags) throws IOException {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        int hdr = 12 + 16 * tags.length;
        w32(o, magic); o.write(0); o.write(tags.length); o.write(new byte[6]);
        for (String t : tags) { o.write(t.getBytes("ISO-8859-1")); w32(o, 0); w32(o, hdr); w32(o, 32); }
        o.write(new byte[64]);
        return o.toByteArray();
    }

    static void w32(OutputStream o, int v) throws IOException { o.write(v >>> 24); o.write(v >>> 16); o.write(v >>> 8); o.write(v); }

    static File tmp(File dir, String name, byte[] data) throws IOException {
        File f = new File(dir, name);
        Files.write(f.toPath(), data);
        return f;
    }

    static boolean valid(File f) {
        try { FontCheck.validate(f); return true; } catch (IOException e) { return false; }
    }

    static void fontChecks(File fontsDir) throws Exception {
        File d = Files.createTempDirectory("fontchk").toFile();
        try {
            File[] real = fontsDir.listFiles((x, n) -> n.toLowerCase().endsWith(".ttf"));
            ok(real != null && real.length > 0, "bundled fonts found");
            if (real != null) for (File f : real) ok(valid(f), "bundled font passes: " + f.getName());

            byte[] one = Files.readAllBytes(real[0].toPath());
            ok(!valid(tmp(d, "trunc.ttf", Arrays.copyOf(one, 200))), "truncated font rejected");
            byte[] ttc = one.clone(); ttc[0] = 't'; ttc[1] = 't'; ttc[2] = 'c'; ttc[3] = 'f';
            ok(!valid(tmp(d, "ttc.ttf", ttc)), "wrong magic rejected");
            ok(!valid(tmp(d, "empty.ttf", new byte[0])), "empty rejected");
            ok(!valid(tmp(d, "html.ttf", "<html><body>not a font, just a webpage pretending to be one</body></html>".getBytes("UTF-8"))), "html rejected");
            byte[] rnd = new byte[4000]; new java.util.Random(1).nextBytes(rnd);
            ok(!valid(tmp(d, "rnd.ttf", rnd)), "random bytes rejected");
            ok(!valid(tmp(d, "zip.ttf", new byte[]{'P', 'K', 3, 4, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0, 0})), "zip rejected");

            ok(valid(tmp(d, "syn_ok.ttf", sfnt(0x00010000, "cmap", "glyf", "head", "loca", "maxp"))), "synthetic glyf font ok");
            ok(valid(tmp(d, "syn_cff.ttf", sfnt(0x4F54544F, "CFF ", "cmap", "head", "maxp"))), "synthetic CFF font ok");
            ok(!valid(tmp(d, "syn_nocmap.ttf", sfnt(0x00010000, "glyf", "head", "loca", "maxp"))), "missing cmap rejected");
            ok(!valid(tmp(d, "syn_noglyph.ttf", sfnt(0x00010000, "cmap", "head", "maxp"))), "no glyph data rejected");

            File big = new File(d, "big.ttf");
            try (RandomAccessFile r = new RandomAccessFile(big, "rw")) { r.setLength(FontCheck.MAX_BYTES + 1); }
            ok(!valid(big), "oversize rejected");
            big.delete();
        } finally {
            for (File x : d.listFiles()) x.delete();
            d.delete();
        }
    }

    static void nameChecks() {
        ok("My Font.ttf".equals(FontCheck.safeName("My Font.TTF")), "extension normalised to lowercase");
        ok("Phông Việt.ttf".equals(FontCheck.safeName("Phông Việt.ttf")), "unicode name kept");
        ok("custom.ttf".equals(FontCheck.safeName(".ttf")), "bare extension -> custom");
        ok("custom.ttf".equals(FontCheck.safeName("....ttf")), "dots only -> custom");
        ok("custom.ttf".equals(FontCheck.safeName(null)), "null -> custom");
        String t = FontCheck.safeName("../../evil.ttf");
        ok(t.endsWith(".ttf") && t.indexOf('/') < 0 && t.indexOf('\\') < 0, "no path separators");
        ok(FontCheck.safeName("a\\b.ttf").indexOf('\\') < 0, "no backslash");
        ok(FontCheck.safeName("a\u0000b\n.ttf").chars().allMatch(c -> c >= 0x20), "no control chars");
        char[] longName = new char[300]; Arrays.fill(longName, 'a');
        ok(FontCheck.safeName(new String(longName) + ".ttf").length() <= 104, "length capped");

        ok("myfont.ttf".equals(PatchModel.deployedName("My Font")), "deployedName ascii unchanged");
        ok("fzgamebrv2.ttf".equals(PatchModel.deployedName("Fz GameBr-V2")), "deployedName bundled font unchanged");
        String c1 = PatchModel.deployedName("我的字体"), c2 = PatchModel.deployedName("另一个字体");
        ok(!c1.equals(".ttf") && c1.startsWith("font") && c1.endsWith(".ttf"), "non-ascii name no longer empty");
        ok(!c1.equals(c2) && c1.equals(PatchModel.deployedName("我的字体")), "non-ascii names distinct and stable");
    }
}
