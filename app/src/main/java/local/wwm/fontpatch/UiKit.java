package local.wwm.fontpatch;

import android.animation.ArgbEvaluator;
import android.animation.ValueAnimator;
import android.app.Activity;
import android.app.Dialog;
import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.content.res.Configuration;
import android.database.Cursor;
import android.util.DisplayMetrics;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Outline;
import android.graphics.RenderEffect;
import android.graphics.Shader;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.graphics.drawable.StateListDrawable;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.SystemClock;
import android.provider.OpenableColumns;
import android.text.Editable;
import android.text.SpannableString;
import android.text.Spanned;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.text.SpannableStringBuilder;
import android.text.style.ForegroundColorSpan;
import android.text.style.RelativeSizeSpan;
import android.util.Log;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.PopupWindow;
import android.widget.FrameLayout;
import android.widget.HorizontalScrollView;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;
import java.io.ByteArrayOutputStream;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Nền của MainActivity: trạng thái giao diện dùng chung (bảng màu, prefs, ngôn ngữ, scene) cùng các hàm dựng view
 * và hộp thoại/popup kiểu kính. Không biết gì về từng màn hình.
 */
abstract class UiKit extends Activity {
    /**
     * Thu nhỏ toàn bộ giao diện (dp, sp, popup, hộp thoại) bằng cách giảm mật độ điểm ảnh của riêng app.
     * 1.0 = đúng kích thước hệ thống; chỉnh số này để to/nhỏ hơn.
     */
    static final float UI_SCALE = 0.9f;

    @Override
    protected void attachBaseContext(Context base) {
        Configuration cfg = new Configuration(base.getResources().getConfiguration());
        if (cfg.densityDpi > 0) cfg.densityDpi = Math.max(120, Math.round(cfg.densityDpi * UI_SCALE));
        super.attachBaseContext(base.createConfigurationContext(cfg));
    }

    // ---------------------------------------------------------------- trạng thái dùng chung
    Pal pal;
    SharedPreferences sp;
    boolean en;                 // ngôn ngữ giao diện: true = English
    // floating pill navigation
    FrameLayout scene;          // nền + các trang: đây là thứ bị làm mờ phía sau thanh nav
    static final int MATCH = ViewGroup.LayoutParams.MATCH_PARENT;
    static final int WRAP = ViewGroup.LayoutParams.WRAP_CONTENT;
    int backdropBlurs;
    float backdropRadius;
    ValueAnimator backdropAnim;
    Dialog progressDlg;
    TextView progressStep;
    /** Chọn chuỗi theo ngôn ngữ đang dùng. */
    String t(String vi, String e) { return en ? e : vi; }
    int dp(int v) { return (int) (v * getResources().getDisplayMetrics().density + 0.5f); }
    GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(dp(radiusDp));
        if (stroke != 0) g.setStroke(dp(1), stroke);
        return g;
    }
    LinearLayout.LayoutParams lpm(int w, int h, int l, int t, int r, int b) {
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(w, h);
        p.setMargins(dp(l), dp(t), dp(r), dp(b));
        return p;
    }
    TextView tv(String s, int sizeSp, int color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sizeSp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }
    /** Serif face used for titles, close to the calligraphic feel of the design. */
    TextView serif(TextView t, boolean bold) {
        t.setTypeface(Typeface.create(Typeface.SERIF, bold ? Typeface.BOLD : Typeface.NORMAL));
        return t;
    }
    LinearLayout card() {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setBackground(rounded(pal.card, 22, pal.stroke));
        c.setPadding(dp(16), dp(14), dp(16), dp(14));
        return c;
    }
    ImageView icon(int res, int tint, int sizeDp) {
        ImageView iv = new ImageView(this);
        iv.setImageResource(res);
        if (tint != 0) iv.setColorFilter(tint);
        iv.setLayoutParams(new LinearLayout.LayoutParams(dp(sizeDp), dp(sizeDp)));
        return iv;
    }
    void pressFx(View v) { Anim.press(v); }
    View divider() {
        View d = new View(this);
        d.setBackgroundColor(pal.stroke);
        d.setLayoutParams(lpm(MATCH, 1, 0, 0, 0, 0));
        return d;
    }
    /** Tiêu đề hộp thoại kiểu Miuix: căn giữa, chữ không chân. */
    TextView mxTitle(String s) {
        TextView t = tv(s, 20, pal.text, true);
        t.setGravity(Gravity.CENTER);
        t.setPadding(0, 0, 0, dp(16));
        return t;
    }
    /** Nút kiểu Miuix: bo 16dp, nền phẳng; primary = màu nhấn, còn lại = xám phụ. */
    TextView mxButton(String label, boolean primary) {
        TextView b = tv(label, 15, primary ? pal.onBrush : pal.text, true);
        b.setGravity(Gravity.CENTER);
        b.setPadding(dp(8), dp(14), dp(8), dp(14));
        b.setBackground(rounded(primary ? pal.mxPrimary : pal.mxBtn, 20, 0));
        pressFx(b);
        return b;
    }
    /** Hàng nút: các nút chia đều, cách nhau 12dp. */
    LinearLayout mxButtons(TextView... bs) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int i = 0; i < bs.length; i++) {
            LinearLayout.LayoutParams lp = lpm(0, WRAP, i == 0 ? 0 : 6, 0, i == bs.length - 1 ? 0 : 6, 0);
            lp.weight = 1f;
            row.addView(bs[i], lp);
        }
        return row;
    }
    /** Hàng chọn trong danh sách kiểu Miuix popup: không đường kẻ, mục đang chọn tô màu nhấn + dấu tích bên phải. */
    LinearLayout mxRow(View label, boolean cur) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(20), dp(12), dp(16), dp(12));
        row.addView(label, new LinearLayout.LayoutParams(0, WRAP, 1f));
        if (cur) row.addView(tv("✓", 18, pal.accent, true));
        pressFx(row);
        return row;
    }
    /**
     * Popup kiểu Miuix (như menu chọn của KernelSU): bám vào view bấm, bo 28dp, bóng đổ,
     * hiện từ góc trên-phải. minWidthDp là bề ngang tối thiểu.
     */
    PopupWindow showMxPopup(View anchor, LinearLayout content, int minWidthDp) {
        DisplayMetrics dm = getResources().getDisplayMetrics();
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(0, dp(8), 0, dp(8));
        // Nền đặc mặc định; GlassDialog.applyToPopup sẽ thay bằng kính nếu API >= 33 và đang bật glass
        box.setBackground(rounded(pal.dialog, 28, pal.stroke));
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(content, new ViewGroup.LayoutParams(MATCH, WRAP));
        box.addView(sv, new LinearLayout.LayoutParams(MATCH, WRAP));

        // Kích thước gọn: không quá 72% bề ngang / 320dp và không quá 42% chiều cao màn hình
        int maxW = Math.min((int) (dm.widthPixels * 0.72f), dp(320));
        int capH = (int) (dm.heightPixels * 0.42f);
        box.measure(View.MeasureSpec.makeMeasureSpec(maxW, View.MeasureSpec.AT_MOST),
                View.MeasureSpec.makeMeasureSpec(capH, View.MeasureSpec.AT_MOST));
        int w = Math.min(maxW, Math.max(dp(minWidthDp), box.getMeasuredWidth()));

        // Vị trí: bám sát nút bấm, mở xuống nếu đủ chỗ, không thì mở lên; chiều cao bị giới hạn theo chỗ trống
        int[] loc = new int[2];
        int[] scr = new int[2];
        anchor.getLocationInWindow(loc);
        anchor.getLocationOnScreen(scr);
        android.graphics.Rect vis = new android.graphics.Rect();
        getWindow().getDecorView().getWindowVisibleDisplayFrame(vis);
        int gap = dp(6), edge = dp(12);
        int below = vis.bottom - (scr[1] + anchor.getHeight()) - gap - edge;
        int above = scr[1] - vis.top - gap - edge;
        int want = Math.min(box.getMeasuredHeight(), capH);
        boolean down = below >= want || below >= above;
        int h = Math.max(dp(64), Math.min(want, down ? below : above));
        int y = down ? loc[1] + anchor.getHeight() + gap : loc[1] - h - gap;
        // ngang: nút nằm nửa phải màn hình thì căn mép phải popup theo mép phải nút, ngược lại căn mép trái
        boolean right = scr[0] + anchor.getWidth() / 2f > dm.widthPixels / 2f;
        int x = right ? loc[0] + anchor.getWidth() - w : loc[0];
        int dx = scr[0] - loc[0];                       // đổi về toạ độ màn hình để kẹp trong màn hình
        x = Math.max(edge - dx, Math.min(x, dm.widthPixels - w - edge - dx));

        PopupWindow pw = new PopupWindow(box, w, h, true);
        pw.setBackgroundDrawable(new ColorDrawable(0));
        pw.setOutsideTouchable(true);
        pw.setElevation(dp(12));
        // chạm ra ngoài thì thu popup về nút rồi mới đóng (thay vì biến mất ngay)
        pw.setTouchInterceptor((tv, ev) -> {
            boolean outside = ev.getAction() == MotionEvent.ACTION_OUTSIDE
                    || (ev.getAction() == MotionEvent.ACTION_DOWN
                    && (ev.getX() < 0 || ev.getY() < 0 || ev.getX() >= tv.getWidth() || ev.getY() >= tv.getHeight()));
            if (outside) Anim.dismiss(pw);
            return outside;
        });

        // Áp Liquid Glass vào popup (chỉ khi đang bật và API 13+)
        if (Glass.enabled(sp) && scene != null) {
            GlassDialog.applyToPopup(pw, box, scene, pal, dm.density);
        }

        pw.showAtLocation(anchor.getRootView(), Gravity.TOP | Gravity.START, x, y);
        backdropBlur(true);
        pw.setOnDismissListener(() -> backdropBlur(false));
        // menu popup không có dim mặc định: thêm lớp tối nhẹ phía sau cùng với lớp mờ
        try {
            View root = box.getRootView();
            WindowManager.LayoutParams wl = (WindowManager.LayoutParams) root.getLayoutParams();
            wl.flags |= WindowManager.LayoutParams.FLAG_DIM_BEHIND;
            wl.dimAmount = 0.22f;
            ((WindowManager) getSystemService(WINDOW_SERVICE)).updateViewLayout(root, wl);
        } catch (RuntimeException ignored) {
        }
        // phóng ra từ chính nút đã bấm: pivot = tâm nút (theo toạ độ popup), cạnh gần nút nhất
        float pivX = Math.max(0f, Math.min(w, scr[0] + anchor.getWidth() / 2f - (x + dx)));
        Anim.popupIn(box, pivX, down ? 0f : h);
        return pw;
    }
    /** Bật/tắt (đếm lồng nhau) lớp mờ nhẹ phủ lên toàn bộ màn hình app. Android 12+; máy cũ chỉ có lớp dim. */
    void backdropBlur(boolean on) {
        // Chỉ làm mờ scene (nền + các trang). Không đụng tới thanh nav: nó vẽ viền/ô kính/bóng tràn ra ngoài khung view,
        // bị RenderEffect cắt cụt và kéo giãn pixel mép thành các dải sáng. Thanh nav vẫn tối đi nhờ lớp dim của cửa sổ popup.
        if (Build.VERSION.SDK_INT < 31) return;
        backdropBlurs = Math.max(0, backdropBlurs + (on ? 1 : -1));
        final float to = backdropBlurs > 0 ? dp(4) : 0f;
        if (scene == null) return;
        if (backdropAnim != null) backdropAnim.cancel();
        if (!Anim.on()) {
            backdropRadius = to;
            applyBackdropBlur(scene, to);
            return;
        }
        backdropAnim = ValueAnimator.ofFloat(backdropRadius, to);
        backdropAnim.setDuration(on ? 240 : 160);
        backdropAnim.addUpdateListener(a -> {
            backdropRadius = (float) a.getAnimatedValue();
            applyBackdropBlur(scene, backdropRadius);
        });
        backdropAnim.start();
    }
    static void applyBackdropBlur(View target, float radius) {
        if (target == null) return;
        try {
            target.setRenderEffect(radius < 0.5f ? null
                    : RenderEffect.createBlurEffect(radius, radius, Shader.TileMode.CLAMP));
        } catch (RuntimeException ignored) {
        }
    }
    Dialog makeDialog(View content, boolean cancelable) {
        return makeDialog(content, cancelable, true);
    }
    Dialog makeDialog(View content, boolean cancelable, boolean fadeDim) {
        Dialog d = new Dialog(this);
        d.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        // Miuix: bo 28dp, viền mảnh mờ (GlassDialog thay thế nền đặc bằng kính khi API >= 33)
        box.setBackground(rounded(pal.dialog, 28, pal.stroke));
        box.setPadding(dp(24), dp(24), dp(24), dp(24));
        // Nội dung cuộn được: xoay ngang hoặc máy thấp thì hộp thoại cao hơn màn hình, nút ở cuối không được bị cắt
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.setOverScrollMode(View.OVER_SCROLL_NEVER);
        sv.addView(content, new ViewGroup.LayoutParams(MATCH, WRAP));
        box.addView(sv, new LinearLayout.LayoutParams(MATCH, WRAP));
        d.setContentView(box);
        d.setCancelable(cancelable);
        Window w = d.getWindow();
        if (w != null) {
            w.setBackgroundDrawable(new ColorDrawable(0));
            w.setDimAmount(Glass.enabled(sp) ? 0.28f : 0.4f);
            // 88% bề ngang, nhưng không quá 420dp để không bành ra ở chế độ ngang / tablet
            w.setLayout(Math.min((int) (getResources().getDisplayMetrics().widthPixels * 0.88f), dp(420)), WRAP);
        }
        // Áp Liquid Glass nền kính lên dialog (chỉ khi đang bật và API 13+)
        if (Glass.enabled(sp) && scene != null) {
            GlassDialog.applyToDialog(d, box, scene, pal, getResources().getDisplayMetrics().density);
        }
        d.setOnShowListener(x -> {
            backdropBlur(true);
            Anim.dialogIn(d, box, fadeDim);
        });
        d.setOnDismissListener(x -> backdropBlur(false));
        return d;
    }
    void closeDialog(Dialog d) {
        View c = d.findViewById(android.R.id.content);
        View box = (c instanceof ViewGroup && ((ViewGroup) c).getChildCount() > 0)
                ? ((ViewGroup) c).getChildAt(0) : null;
        Anim.dialogOut(d, box);
    }
    void showProgress(String title) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setGravity(Gravity.CENTER_HORIZONTAL);
        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.setIndeterminateTintList(ColorStateList.valueOf(pal.accent));
        c.addView(pb, new LinearLayout.LayoutParams(dp(44), dp(44)));
        TextView t = tv(this.t("Đang xử lý…", "Working…"), 20, pal.text, true);
        c.addView(t, lpm(WRAP, WRAP, 0, 14, 0, 0));
        c.addView(tv(title, 12, pal.sub, false), lpm(WRAP, WRAP, 0, 2, 0, 0));
        progressStep = tv("", 12, pal.sub, false);
        progressStep.setGravity(Gravity.CENTER);
        progressStep.setMaxLines(3);
        progressStep.setEllipsize(TextUtils.TruncateAt.END);
        c.addView(progressStep, lpm(MATCH, WRAP, 0, 10, 0, 4));
        progressDlg = makeDialog(c, false);
        progressDlg.show();
    }
    void dismissProgress() {
        if (progressDlg != null && progressDlg.isShowing()) progressDlg.dismiss();
        progressDlg = null;
        progressStep = null;
    }
    /** Hộp thoại thông báo: có okLabel thì hiện thêm nút hành động bên cạnh nút Đóng. */
    void infoDialog(String head, String msg, String okLabel, Runnable ok) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.addView(mxTitle(head));
        TextView m = tv(msg, 15, pal.sub, false);
        m.setGravity(Gravity.CENTER);
        c.addView(m, lpm(MATCH, WRAP, 0, 0, 0, 0));
        final Dialog[] ref = new Dialog[1];
        TextView close = mxButton(ok != null ? t("Để sau", "Later") : t("Đóng", "Close"), ok == null);
        close.setOnClickListener(v -> closeDialog(ref[0]));
        LinearLayout btns;
        if (ok != null) {
            TextView go = mxButton(okLabel, true);
            go.setOnClickListener(v -> { closeDialog(ref[0]); ok.run(); });
            btns = mxButtons(close, go);
        } else {
            btns = mxButtons(close);
        }
        c.addView(btns, lpm(MATCH, WRAP, 0, 20, 0, 0));
        ref[0] = makeDialog(c, true);
        ref[0].show();
    }

    /** Cuộn thì lớp mờ phía sau thanh nav phải vẽ lại. */
    abstract void invalidateBackdrop();

    ScrollView scroll(View content) {
        ScrollView sv = new ScrollView(this);
        sv.setVerticalScrollBarEnabled(false);
        sv.addView(content, new ViewGroup.LayoutParams(MATCH, WRAP));
        // cuộn thì lớp mờ phía sau thanh nav phải vẽ lại
        sv.setOnScrollChangeListener((v, x, y, ox, oy) -> invalidateBackdrop());
        return sv;
    }
}
