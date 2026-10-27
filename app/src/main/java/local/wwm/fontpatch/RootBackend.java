package local.wwm.fontpatch;

import android.content.Context;
import java.io.File;
import java.util.concurrent.TimeUnit;

/** Writes with `su`. No Shizuku, no need for the game to be running. */
final class RootBackend extends ShellBackend {
    private final Context ctx;

    RootBackend(Context ctx) { this.ctx = ctx; }

    @Override public String name() { return "Root"; }

    @Override public File stageDir() { return ctx.getCacheDir(); }

    @Override
    Result run(String cmd) throws Exception {
        Process p = new ProcessBuilder("su", "-c", cmd).redirectErrorStream(true).start();
        String out = readAll(p.getInputStream());
        if (!p.waitFor(120, TimeUnit.SECONDS)) { p.destroy(); throw new java.io.IOException(Lang.t("su quá thời gian chờ", "su timed out")); }
        return new Result(p.exitValue(), out);
    }

    /** True when `su` is present and has been granted. Shows the root prompt the first time. */
    boolean available() {
        try {
            Result r = run("id");
            return r.code == 0 && r.out.contains("uid=0");
        } catch (Exception e) {
            return false;
        }
    }
}
