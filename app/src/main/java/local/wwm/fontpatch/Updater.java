package local.wwm.fontpatch;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageInstaller;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.widget.Toast;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

/** Tự cập nhật khép kín: tải APK trong nền, cài bằng PackageInstaller, rồi xoá file tạm. */
final class Updater {
    interface Progress { void onProgress(int percent); }

    private static final String ACTION = "local.wwm.fontpatch.INSTALL_RESULT";
    private static final String EXTRA_TOKEN = "local.wwm.fontpatch.TOKEN";
    private static boolean registered;
    /** Token của lần cài đang chạy; broadcast không mang đúng token này bị bỏ qua. */
    private static volatile String token;

    private Updater() { }

    private static File dir(Context c) {
        File d = new File(c.getCacheDir(), "update");
        d.mkdirs();
        return d;
    }

    /** Xoá mọi file cập nhật còn sót (gọi khi mở app và sau khi cài xong). */
    static void clean(Context c) {
        File[] fs = dir(c).listFiles();
        if (fs != null) for (File f : fs) f.delete();
        registered = false;
        token = null;
    }

    /**
     * Tải APK về thư mục tạm của app. Chạy ở luồng nền.
     * Chỉ nhận URL release của repo này trên github.com, mọi redirect phải ở github.com / *.githubusercontent.com (https),
     * tối đa MAX_APK_BYTES, và nội dung phải khớp sha256 (hex thường) lấy từ metadata release.
     */
    static File download(Context c, String url, String sha256, Progress p) throws IOException {
        clean(c);
        if (!UpdateGuard.isTrustedStartUrl(url))
            throw new IOException(Lang.t("Địa chỉ tải không đáng tin cậy", "Untrusted download address"));
        File dst = new File(dir(c), "update.apk");
        UpdateGuard.fetch(url, dst, UpdateGuard.MAX_APK_BYTES, sha256, UpdateGuard::isTrustedHop,
                p == null ? null : p::onProgress);
        return dst;
    }

    /** APK phải đúng gói, không thấp hơn bản đang cài và cùng chữ ký với bản đang cài. */
    @SuppressWarnings("deprecation")
    private static void verifyApk(Context app, File apk) throws IOException {
        PackageManager pm = app.getPackageManager();
        int fl = Build.VERSION.SDK_INT >= 28 ? PackageManager.GET_SIGNING_CERTIFICATES : PackageManager.GET_SIGNATURES;
        PackageInfo neu = pm.getPackageArchiveInfo(apk.getAbsolutePath(), fl);
        if (neu == null) throw new IOException(Lang.t("APK không hợp lệ", "Invalid APK"));
        if (!app.getPackageName().equals(neu.packageName))
            throw new IOException(Lang.t("APK không phải của ứng dụng này", "APK belongs to a different app"));
        PackageInfo cur;
        try {
            cur = pm.getPackageInfo(app.getPackageName(), fl);
        } catch (PackageManager.NameNotFoundException e) {
            throw new IOException(e);
        }
        long nv = Build.VERSION.SDK_INT >= 28 ? neu.getLongVersionCode() : neu.versionCode;
        long cv = Build.VERSION.SDK_INT >= 28 ? cur.getLongVersionCode() : cur.versionCode;
        if (nv < cv) throw new IOException(Lang.t("APK cũ hơn bản đang cài", "APK is older than the installed version"));
        Set<String> a = signers(neu), b = signers(cur);
        if (a == null || b == null || a.isEmpty() || !a.equals(b))
            throw new IOException(Lang.t("Chữ ký APK không khớp bản đang cài (hãy gỡ bản cũ rồi cài lại một lần)",
                    "APK signature does not match the installed app (uninstall the old one and install once)"));
    }

    @SuppressWarnings("deprecation")
    private static Set<String> signers(PackageInfo p) {
        Signature[] s;
        if (Build.VERSION.SDK_INT >= 28) {
            if (p.signingInfo == null) return null;
            s = p.signingInfo.getApkContentsSigners();
        } else {
            s = p.signatures;
        }
        if (s == null) return null;
        Set<String> out = new HashSet<>();
        for (Signature g : s) out.add(UpdateGuard.hex(UpdateGuard.sha256().digest(g.toByteArray())));
        return out;
    }

    /** Đưa APK vào phiên cài đặt của hệ thống rồi xoá file tạm; hệ thống sẽ hiện hộp xác nhận cài. */
    static void install(Context c, File apk) throws IOException {
        final Context app = c.getApplicationContext();
        try {
            verifyApk(app, apk);
        } catch (IOException | RuntimeException e) {
            apk.delete();
            throw e;
        }
        register(app);
        token = UpdateGuard.newToken();
        PackageInstaller pi = app.getPackageManager().getPackageInstaller();
        PackageInstaller.SessionParams sp = new PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL);
        sp.setSize(apk.length());
        int id = pi.createSession(sp);
        try (PackageInstaller.Session s = pi.openSession(id)) {
            try (InputStream in = new FileInputStream(apk); OutputStream out = s.openWrite("update", 0, apk.length())) {
                byte[] buf = new byte[65536];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
                s.fsync(out);
            }
            Intent i = new Intent(ACTION).setPackage(app.getPackageName()).putExtra(EXTRA_TOKEN, token);
            int fl = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 31 ? PendingIntent.FLAG_MUTABLE : 0);
            s.commit(PendingIntent.getBroadcast(app, id, i, fl).getIntentSender());
        } catch (IOException | RuntimeException e) {
            pi.abandonSession(id);
            throw e;
        } finally {
            apk.delete();   // dữ liệu đã nằm trong phiên cài đặt, không cần giữ file
        }
    }

    private static final int URI_GRANT_FLAGS = Intent.FLAG_GRANT_READ_URI_PERMISSION | Intent.FLAG_GRANT_WRITE_URI_PERMISSION
            | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION;

    /** Chỉ mở intent xác nhận cài đặt khi nó hợp lệ: không trỏ về chính app, không mang quyền URI, không có selector. */
    private static boolean safeConfirm(Context ctx, Intent confirm) {
        String me = ctx.getPackageName();
        if (confirm.getComponent() != null && me.equals(confirm.getComponent().getPackageName())) return false;
        if (me.equals(confirm.getPackage())) return false;
        if (confirm.getComponent() == null && confirm.getPackage() == null) return false;   // phải nhắm đích cụ thể (bộ cài đặt hệ thống)
        if (confirm.getSelector() != null) return false;
        confirm.setFlags(confirm.getFlags() & ~URI_GRANT_FLAGS);
        confirm.setClipData(null);
        return true;
    }

    @SuppressWarnings("deprecation")
    private static Intent confirmIntent(Intent in) {
        return Build.VERSION.SDK_INT >= 33 ? in.getParcelableExtra(Intent.EXTRA_INTENT, Intent.class)
                : (Intent) in.getParcelableExtra(Intent.EXTRA_INTENT);
    }

    @SuppressWarnings("deprecation")
    private static synchronized void register(Context app) {
        if (registered) return;
        BroadcastReceiver r = new BroadcastReceiver() {
            @Override public void onReceive(Context ctx, Intent in) {
                // Receiver đăng ký động; trước Android 13 không thể đặt NOT_EXPORTED nên chỉ nhận
                // broadcast mang đúng token một lần do chính app tạo ra khi bắt đầu cài.
                if (in == null || !ACTION.equals(in.getAction())
                        || !UpdateGuard.constantTimeEquals(token, in.getStringExtra(EXTRA_TOKEN))) return;
                int st = in.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE);
                if (st == PackageInstaller.STATUS_PENDING_USER_ACTION) {
                    Intent confirm = confirmIntent(in);
                    if (confirm != null && safeConfirm(ctx, confirm)) {
                        confirm.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                        ctx.startActivity(confirm);
                    } else {
                        clean(ctx);
                        new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx,
                                Lang.t("Không xác nhận được yêu cầu cài đặt", "Could not verify the install request"), Toast.LENGTH_LONG).show());
                    }
                } else if (st == PackageInstaller.STATUS_SUCCESS) {
                    clean(ctx);
                } else {
                    clean(ctx);
                    String msg = in.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE);
                    String why = st == PackageInstaller.STATUS_FAILURE_CONFLICT || st == PackageInstaller.STATUS_FAILURE_INCOMPATIBLE
                            ? Lang.t(" (khác chữ ký: hãy gỡ bản cũ rồi cài lại một lần)", " (signature differs: uninstall the old one and install once)") : "";
                    if (st == PackageInstaller.STATUS_FAILURE_ABORTED) { why = ""; msg = Lang.t("Đã huỷ cài đặt", "Install cancelled"); }
                    final String text = Lang.t("Cài đặt thất bại: ", "Install failed: ") + (msg == null ? st : msg) + why;
                    new Handler(Looper.getMainLooper()).post(() -> Toast.makeText(ctx, text, Toast.LENGTH_LONG).show());
                }
            }
        };
        IntentFilter f = new IntentFilter(ACTION);
        if (Build.VERSION.SDK_INT >= 33) app.registerReceiver(r, f, Context.RECEIVER_NOT_EXPORTED);
        else app.registerReceiver(r, f);
        registered = true;
    }
}
