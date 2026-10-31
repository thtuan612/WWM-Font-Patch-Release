package local.wwm.fontpatch;

import android.content.Context;
import android.content.pm.PackageManager;
import java.io.File;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/** Same approach as the original app: run `sh -c ...` through Shizuku (shell uid). */
final class ShizukuBackend extends ShellBackend {
    private final Context ctx;

    /** True when prepare() just opened the Shizuku permission dialog (caller should stop and let the user answer). */
    boolean permissionRequested;

    ShizukuBackend(Context ctx) { this.ctx = ctx; }

    @Override public String name() { return "Shizuku"; }

    @Override
    public File stageDir() throws Exception {
        File d = ctx.getExternalCacheDir();   // shell can read/write this one; our private dir it cannot
        if (d == null) throw new IOException(Lang.t("Không có bộ nhớ ngoài của app để làm thư mục tạm cho Shizuku", "The app has no external storage to use as a staging folder for Shizuku"));
        return d;
    }

    /** null = ready, otherwise a human readable reason. May trigger the Shizuku permission dialog. */
    String prepare() {
        try {
            ctx.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
        } catch (PackageManager.NameNotFoundException e) {
            // Shizuku may still run via Sui; don't fail yet, ping below decides.
        }
        try {
            if (!Shizuku.pingBinder()) return Lang.t("Shizuku chưa chạy (mở app Shizuku và khởi động dịch vụ)", "Shizuku is not running (open the Shizuku app and start the service)");
            if (Shizuku.isPreV11()) return Lang.t("Shizuku quá cũ (pre-v11)", "Shizuku is too old (pre-v11)");
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED) return null;
            Shizuku.requestPermission(0);
            permissionRequested = true;
            return Lang.t("Đã xin quyền Shizuku — hãy cấp quyền rồi bấm lại", "Requested Shizuku permission — grant it, then tap again");
        } catch (Throwable t) {
            return Lang.t("Shizuku lỗi: ", "Shizuku error: ") + t;
        }
    }

    @Override
    Result run(String cmd) throws Exception {
        // Shizuku.newProcess is private in API 13.x, so call it by reflection (works on 12.x and 13.x).
        // TODO: migrate to UserService + AIDL when newProcess is fully removed upstream.
        Method m;
        try {
            m = Shizuku.class.getDeclaredMethod("newProcess", String[].class, String[].class, String.class);
            m.setAccessible(true);
        } catch (NoSuchMethodException e) {
            throw new IOException(Lang.t(
                    "Phiên bản Shizuku này không còn hỗ trợ phương thức hiện tại. Hãy dùng Root thay thế hoặc cập nhật app.",
                    "This Shizuku version no longer supports the current method. Use Root instead or update the app."));
        }
        Process p = (Process) m.invoke(null, new Object[]{new String[]{"sh", "-c", cmd}, null, null});
        // Đọc stderr song song (tránh nghẽn pipe). KHÔNG dùng waitFor(timeout): ShizukuRemoteProcess.exitValue() ném
        // lỗi "process hasn't exited" khi tiến trình chưa kết thúc, mà bản mặc định của waitFor(timeout) chỉ bắt
        // IllegalThreadStateException nên lỗi nổ ra ngoài. Dùng waitFor() chặn + đồng hồ canh để huỷ khi quá hạn.
        final StringBuilder errBuf = new StringBuilder();
        final Process fp = p;
        Thread errT = new Thread(() -> {
            try { errBuf.append(readAll(fp.getErrorStream())); } catch (Exception ignored) { }
        });
        errT.start();
        final boolean[] timedOut = {false};
        Thread dog = new Thread(() -> {
            try { Thread.sleep(120000); timedOut[0] = true; fp.destroy(); } catch (InterruptedException ignored) { }
        });
        dog.setDaemon(true);
        dog.start();
        String out = readAll(p.getInputStream());
        int code = -1;
        for (int i = 0; ; i++) {
            try { code = p.waitFor(); break; }
            catch (InterruptedException e) { throw e; }
            catch (RuntimeException e) {                  // tiến trình báo chưa thoát: chờ rồi hỏi lại
                if (timedOut[0] || i > 600) break;
                Thread.sleep(100);
            }
        }
        dog.interrupt();
        errT.join(2000);
        if (timedOut[0]) throw new IOException(Lang.t("Shizuku quá thời gian chờ", "Shizuku timed out"));
        return new Result(code, out + errBuf);
    }
}
