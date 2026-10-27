package local.wwm.fontpatch;

import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.ProviderInfo;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/**
 * The original app's no-Shizuku path: the game exports a ContentProvider
 * (".widget_file_provider") that maps to its Android/data/<pkg>/files folder.
 * Needs the game process to be alive; not available for the Chinese builds.
 */
final class ProviderBackend implements Backend {
    private final Activity act;
    private final java.io.File cache;

    ProviderBackend(Activity act) { this.act = act; this.cache = act.getCacheDir(); }

    @Override public String name() { return "Provider của game"; }

    @Override public File stageDir() { return cache; }

    @Override public void forceStop(String pkg) { /* cannot stop another app without root/shell */ }

    private String authority(String pkg) {
        try {
            PackageInfo pi = act.getPackageManager().getPackageInfo(pkg, PackageManager.GET_PROVIDERS);
            if (pi.providers != null) {
                for (ProviderInfo p : pi.providers) {
                    if (p.authority != null && p.authority.contains("widget_file_provider") && p.exported && p.enabled) {
                        return p.authority.split(";")[0];
                    }
                }
            }
        } catch (Exception ignored) {
        }
        return pkg + ".widget_file_provider";
    }

    private Uri uri(String pkg, String rel) {
        return Uri.parse("content://" + authority(pkg) + "/widget_external_files/" + rel);
    }

    private ParcelFileDescriptor open(String pkg, String rel, String mode) throws IOException {
        Uri u = uri(pkg, rel);
        try {
            ParcelFileDescriptor f = act.getContentResolver().openFileDescriptor(u, mode);
            if (f == null) throw new FileNotFoundException(rel);
            return f;
        } catch (IOException e) {
            if (e.getMessage() != null && e.getMessage().contains("No content provider")) {
                Intent i = act.getPackageManager().getLaunchIntentForPackage(pkg);
                if (i == null) throw e;
                act.startActivity(i);                       // wake the game so the provider exists
                try { Thread.sleep(3000); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                ParcelFileDescriptor f = act.getContentResolver().openFileDescriptor(u, mode);
                if (f == null) throw new FileNotFoundException(rel);
                return f;
            }
            throw e;
        }
    }

    @Override
    public boolean exists(String pkg, String rel) {
        try (ParcelFileDescriptor f = open(pkg, rel, "r")) {
            return f != null;
        } catch (Exception e) {
            return false;
        }
    }

    @Override
    public void pull(String pkg, String rel, File dst) throws Exception {
        try (ParcelFileDescriptor f = open(pkg, rel, "r");
             InputStream in = new FileInputStream(f.getFileDescriptor());
             OutputStream out = new FileOutputStream(dst)) {
            copy(in, out);
        }
    }

    @Override
    public void push(String pkg, File src, String rel) throws Exception {
        try (ParcelFileDescriptor f = open(pkg, rel, "wt");
             InputStream in = new FileInputStream(src);
             OutputStream out = new FileOutputStream(f.getFileDescriptor())) {
            copy(in, out);
            out.flush();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        byte[] buf = new byte[65536];
        int n;
        while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
    }
}
