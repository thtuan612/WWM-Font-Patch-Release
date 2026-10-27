package local.wwm.fontpatch;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;

/** Shared logic for backends that run shell commands (root su, Shizuku). */
abstract class ShellBackend implements Backend {

    static final class Result {
        final int code;
        final String out;
        Result(int code, String out) { this.code = code; this.out = out; }
    }

    abstract Result run(String cmd) throws Exception;

    static String q(String s) {
        return "'" + s.replace("'", "'\\''") + "'";
    }

    static String base(String pkg) {
        return "/sdcard/Android/data/" + pkg + "/files/";
    }

    static String readAll(InputStream in) throws IOException {
        ByteArrayOutputStream bo = new ByteArrayOutputStream();
        byte[] buf = new byte[4096];
        int n;
        while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
        return bo.toString("UTF-8");
    }

    @Override
    public void forceStop(String pkg) throws Exception {
        run("am force-stop " + q(pkg));
    }

    @Override
    public Boolean isRunning(String pkg) throws Exception {
        String p = q(pkg);
        Result r = run("pidof " + p + " >/dev/null 2>&1; r=$?; if [ $r = 127 ]; then ps -A 2>/dev/null | grep -q " + p + "; r=$?; fi; exit $r");
        if (r.code == 0) return Boolean.TRUE;
        if (r.code == 1) return Boolean.FALSE;
        return null;
    }

    @Override
    public boolean exists(String pkg, String rel) throws Exception {
        return run("[ -f " + q(base(pkg) + rel) + " ]").code == 0;
    }

    @Override
    public void pull(String pkg, String rel, File dst) throws Exception {
        String src = base(pkg) + rel;
        if (run("[ -f " + q(src) + " ]").code != 0) throw new FileNotFoundException(src);
        if (dst.exists()) dst.delete();
        dst.createNewFile();                 // created by this app so it keeps our ownership
        dst.setReadable(true, false);
        dst.setWritable(true, false);
        Result r = run("cat " + q(src) + " > " + q(dst.getAbsolutePath()));
        if (r.code != 0) throw new IOException(name() + ": " + Lang.t("không đọc được ", "cannot read ") + src + " (" + r.out.trim() + ")");
        Result sz = run("stat -c %s " + q(src));
        long want = parseLong(sz.out);
        if (want >= 0 && dst.length() != want) throw new IOException(Lang.t("Đọc " + rel + " thiếu dữ liệu: ", "Reading " + rel + " gave missing data: ") + dst.length() + "/" + want);
    }

    @Override
    public void push(String pkg, File src, String rel) throws Exception {
        String target = base(pkg) + rel;
        String dir = target.substring(0, target.lastIndexOf('/'));
        // `cat >` keeps owner/SELinux label of an existing file. New files/dirs get the owner of the parent.
        String cmd = "D=" + q(dir) + "; T=" + q(target) + "; "
                + "if [ ! -d \"$D\" ]; then mkdir -p \"$D\" && chown $(stat -c %u:%g \"$(dirname \"$D\")\") \"$D\" 2>/dev/null; fi; "
                + "NEW=0; [ -f \"$T\" ] || NEW=1; "
                + "cat " + q(src.getAbsolutePath()) + " > \"$T\" || exit 1; "
                + "if [ $NEW = 1 ]; then chown $(stat -c %u:%g \"$D\") \"$T\" 2>/dev/null; chmod 660 \"$T\" 2>/dev/null; fi; "
                + "exit 0";
        Result r = run(cmd);
        if (r.code != 0) throw new IOException(name() + ": " + Lang.t("không ghi được ", "cannot write ") + target + " (" + r.out.trim() + ")");
        long got = parseLong(run("stat -c %s " + q(target)).out);
        if (got != src.length()) throw new IOException(Lang.t("Ghi " + rel + " lỗi: kích thước ", "Writing " + rel + " failed: size mismatch ") + got + " ≠ " + src.length());
    }

    static long parseLong(String s) {
        try { return Long.parseLong(s.trim()); } catch (Exception e) { return -1; }
    }
}
