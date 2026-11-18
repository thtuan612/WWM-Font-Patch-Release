package local.wwm.fontpatch;

import android.content.Context;
import android.content.pm.PackageManager;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;
import rikka.shizuku.Shizuku;

/**
 * Kiểm tra cách ghi file (Root / Shizuku) có dùng được không, để hiện ở tab Nâng cao.
 * Shizuku: không có tác dụng phụ (không tự xin quyền). Root: chạy `su -c id`, lần đầu có thể hiện hộp thoại cấp root.
 */
final class AccessCheck {
    enum State { READY, DENIED, NOT_RUNNING, NOT_INSTALLED, TOO_OLD, ERROR }

    static final class Status {
        final State state;
        final String text;

        Status(State state, String text) {
            this.state = state;
            this.text = text;
        }

        boolean ready() { return state == State.READY; }
    }

    private AccessCheck() { }

    /** Chạy trên luồng nền (có thể chờ tới ~20s nếu hộp thoại cấp root đang đợi người dùng). */
    static Status root() {
        Process p;
        try {
            p = new ProcessBuilder("su", "-c", "id").redirectErrorStream(true).start();
        } catch (IOException e) {
            return new Status(State.NOT_INSTALLED, Lang.t("Không tìm thấy su (máy chưa root)", "su not found (device is not rooted)"));
        }
        try {
            if (!p.waitFor(20, TimeUnit.SECONDS)) {
                return new Status(State.ERROR, Lang.t("su không phản hồi (hộp thoại cấp quyền root có thể đang chờ)",
                        "su did not respond (a root permission prompt may be waiting)"));
            }
            ByteArrayOutputStream bo = new ByteArrayOutputStream();
            byte[] buf = new byte[512];
            InputStream in = p.getInputStream();
            int n;
            while ((n = in.read(buf)) > 0) bo.write(buf, 0, n);
            String out = bo.toString("UTF-8");
            if (p.exitValue() == 0 && out.contains("uid=0"))
                return new Status(State.READY, Lang.t("Sẵn sàng", "Ready"));
            return new Status(State.DENIED, Lang.t("Có su nhưng chưa được cấp quyền root", "su exists but root access was not granted"));
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return new Status(State.ERROR, Lang.t("Bị gián đoạn", "Interrupted"));
        } catch (Exception e) {
            return new Status(State.ERROR, Lang.t("Lỗi: ", "Error: ") + e.getMessage());
        } finally {
            p.destroy();
        }
    }

    /** Nhanh, gọi được trên luồng bất kỳ. */
    static Status shizuku(Context ctx) {
        try {
            if (!Shizuku.pingBinder()) {
                boolean installed;
                try {
                    ctx.getPackageManager().getPackageInfo("moe.shizuku.privileged.api", 0);
                    installed = true;
                } catch (PackageManager.NameNotFoundException e) {
                    installed = false;
                }
                return installed
                        ? new Status(State.NOT_RUNNING, Lang.t("Chưa chạy — mở app Shizuku và khởi động dịch vụ", "Not running — open the Shizuku app and start the service"))
                        : new Status(State.NOT_INSTALLED, Lang.t("Chưa cài app Shizuku", "Shizuku app is not installed"));
            }
            if (Shizuku.isPreV11())
                return new Status(State.TOO_OLD, Lang.t("Shizuku quá cũ (pre-v11)", "Shizuku is too old (pre-v11)"));
            if (Shizuku.checkSelfPermission() == PackageManager.PERMISSION_GRANTED)
                return new Status(State.READY, Lang.t("Sẵn sàng", "Ready"));
            return new Status(State.DENIED, Lang.t("Đang chạy nhưng chưa cấp quyền cho app", "Running, but this app has no permission yet"));
        } catch (Throwable t) {
            return new Status(State.ERROR, Lang.t("Lỗi: ", "Error: ") + t.getMessage());
        }
    }

    /** Dòng "Sẽ dùng: ..." theo cách đang chọn. root/shizuku có thể null (chưa kiểm tra). */
    static String willUse(String method, Status root, Status shizuku) {
        if ("root".equals(method)) {
            if (root == null) return Lang.t("Root (chưa kiểm tra)", "Root (not checked)");
            return root.ready() ? "Root" : Lang.t("Root — không dùng được", "Root — unavailable");
        }
        if ("shizuku".equals(method)) {
            if (shizuku == null) return Lang.t("Shizuku (chưa kiểm tra)", "Shizuku (not checked)");
            return shizuku.ready() ? "Shizuku" : Lang.t("Shizuku — không dùng được", "Shizuku — unavailable");
        }
        // auto: Root trước, rồi Shizuku, rồi Provider của game (bản CN không có Provider)
        if (root != null && root.ready()) return "Root";
        if (root == null) return Lang.t("Thử Root trước, không được thì Shizuku", "Tries Root first, then Shizuku");
        if (shizuku != null && shizuku.ready()) return "Shizuku";
        return Lang.t("Không có Root/Shizuku — dùng Provider của game (bản CN không hỗ trợ)",
                "No Root/Shizuku — falls back to the game's Provider (not supported on CN)");
    }
}
