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
import rikka.shizuku.Shizuku;
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

public class MainActivity extends ResultKit {
    private static final String TAG = "FontPatch";
    private static final int REQ_FONT = 1;
    private static final String[] SLOT_VI = {"Font Chính (Giao diện)", "Font Hội Thoại", "Font Nghệ Thuật (Tiêu đề, thư pháp)"};
    private static final String[] SLOT_EN = {"Main Font (Interface)", "Dialogue Font", "Art Font (Titles, calligraphy)"};
    /**
     * Font mặc định cho 3 ô (đóng gói sẵn trong assets/fonts, tên = tên file bỏ ".ttf").
     * Merienda_SC_18pt cũng được đóng gói nhưng chỉ có sẵn trong danh sách chọn, không đặt mặc định.
     */
    private static final String[] DEFAULT_FONTS = {"Truculenta_SC_fixed_18pt", "SVN-Jolly-Lodger", "Fz GameBr-V2"};

    private static final String RELEASE_PAGE = "https://github.com/thtuan612/WWM-Font-Patch-Release/releases/latest";
    private static final String DISCORD = "@lmtuan612";
    private static final String DISCORD_URL = "https://discord.gg/ajZXSthPN2";   // chỉ mở khi bấm nút, không hiển thị


    /** Chừa chỗ dưới nội dung cho thanh nav nổi (dp) để mục cuối cuộn lên không bị che. */
    private static final int NAV_CLEAR = 108;
    /** Khoảng nâng thanh nav nổi lên khỏi mép dưới (dp), chưa tính thanh điều hướng hệ thống. */
    private static final int NAV_LIFT = 26;
    /** Bề rộng một mục của thanh nav ngang (dp): thanh co lại vừa ba mục thay vì kéo hết chiều ngang. */
    private static final int NAV_ITEM_W = 86;

    /** Thanh nav dọc (xoay ngang): kích thước một mục và chỗ chừa cho cả thanh. */
    private static final int RAIL_ITEM_W = 60;
    private static final int RAIL_ITEM_H = 52;
    private static final int RAIL_PAD = 4;     // padding quanh hàng nút
    private static final int RAIL_EDGE = 10;   // lề giữa thanh và mép màn hình / nội dung
    private static final int RAIL_W = RAIL_PAD * 2 + RAIL_ITEM_W + RAIL_EDGE * 2;   // chỗ chừa cho thanh dọc

    private boolean rail;        // true: đang xoay ngang và dùng thanh nav dọc
    private boolean land;        // true: đang xoay ngang (nội dung chia 2 cột)
    private boolean railRight;   // true: thanh bên phải, false: bên trái

    private FontStore fonts;
    private int tab;

    private final View[] pages = new View[3];
    private final ScrollView[] scrollViews = new ScrollView[3];
    private final LinearLayout[] navItems = new LinearLayout[3];
    private final ImageView[] navIcons = new ImageView[3];
    private final TextView[] navLabels = new TextView[3];

    // animation state
    private static final ArgbEvaluator ARGB = new ArgbEvaluator();
    private final ViewGroup[] contents = new ViewGroup[3];
    private final ValueAnimator[] navAnim = new ValueAnimator[3];
    private final int[] navColor = new int[3];
    private final String[] shownFont = new String[3];
    private ImageView sealView;

    private FrameLayout navHost;
    private LinearLayout navRow;
    private View indicator;             // ô chọn trượt qua lại giữa các tab
    private Backdrop backdrop;          // chỉ có trên Android 12+ (làm mờ bằng RenderEffect)
    private GlassNav glass;             // thanh nav liquid glass (Android 13+, xem Glass.java); null = dùng thanh nav cũ
    private long blurUntil;
    private final Runnable blurTick = new Runnable() {
        @Override public void run() {
            if (!hasBackdrop()) return;
            invalidateBackdrop();
            if (SystemClock.uptimeMillis() < blurUntil) scene.postOnAnimation(this);
        }
    };

    private List<String> pkgList = new ArrayList<>();
    private String pkg;
    private final String[] selFonts = new String[3];

    // home
    private TextView tvGameName, tvGameSel, tvInfoVer, tvInfoDir, tvInfoMethod;
    // fonts
    private final TextView[] tvFontName = new TextView[3];
    private LinearLayout fontMgrList;       // danh sách font đã thêm (có nút xoá)
    private final TextView[] tvBadge = new TextView[3];
    private final TextView[] tvChoose = new TextView[3];
    private final TextView[] tvPreview = new TextView[3];
    // advanced
    private Switch swAutoStop;
    private TextView tvMethodVal;
    private TextView tvStRoot, tvStShizuku, tvStUse, btnCheck, btnGrant;
    private boolean checkingRoot;
    private static AccessCheck.Status stRoot, stShizuku;   // kết quả kiểm tra gần nhất (giữ qua recreate)
    private boolean syncing;
    private EditText etHotfix;
    private TextView tvUpdateStatus;
    private boolean checking;

    // work in progress / results.
    // Các biến static sống qua việc Activity bị tạo lại (xoay màn hình, đổi theme/ngôn ngữ) giữa lúc đang patch:
    // tác vụ chạy ở luồng nền nên khi xong phải nói chuyện với instance MỚI, không phải instance đã bị huỷ.
    private static Bitmap themeShot;              // ảnh chụp giao diện cũ, phủ lên rồi mờ dần khi đổi theme
    private static int[] savedScrollY = new int[3];
    private static MainActivity current;          // instance đang hiển thị
    private static String runningTitle;           // khác null khi có tác vụ đang chạy
    private static Pending pending;               // kết quả đã xong nhưng chưa có Activity nào hiển thị được

    private static final class Pending {
        final String action, message;
        final int state;
        final Hints.Hint hint;

        Pending(String action, int state, String message, Hints.Hint hint) {
            this.action = action;
            this.state = state;
            this.message = message;
            this.hint = hint;
        }
    }

    // ---------------------------------------------------------------- helpers


    private String none() { return t("(không đổi)", "(unchanged)"); }

    private String sample() { return t("Thế giới giang hồ, vạn sự tùy duyên", "Where winds meet, every road begins anew"); }

    private String slotTitle(int i) { return en ? SLOT_EN[i] : SLOT_VI[i]; }


    private void saveScrollPositions() {
        if (savedScrollY == null) savedScrollY = new int[3];
        for (int i = 0; i < 3; i++) {
            if (scrollViews[i] != null) savedScrollY[i] = scrollViews[i].getScrollY();
        }
    }

    private void restoreScroll(final ScrollView sv, final int targetY) {
        if (sv == null || targetY <= 0) return;
        final View.OnLayoutChangeListener listener = new View.OnLayoutChangeListener() {
            private int attempts = 0;
            @Override
            public void onLayoutChange(View v, int l, int t, int r, int b,
                                       int ol, int ot, int or, int ob) {
                int svH = b - t;
                if (svH <= 0) return;
                View child = sv.getChildAt(0);
                if (child == null || child.getHeight() <= 0) return;
                attempts++;
                int maxScroll = Math.max(0, child.getHeight() - svH);
                int clampedY = Math.min(targetY, maxScroll);
                sv.scrollTo(0, clampedY);
                if ((maxScroll > 0 && sv.getScrollY() == clampedY) || attempts >= 5) {
                    sv.removeOnLayoutChangeListener(this);
                }
            }
        };
        sv.addOnLayoutChangeListener(listener);
        sv.post(() -> {
            if (sv.getHeight() > 0 && targetY > 0) {
                View child = sv.getChildAt(0);
                if (child != null && child.getHeight() > sv.getHeight()) {
                    int clampedY = Math.min(targetY, child.getHeight() - sv.getHeight());
                    sv.scrollTo(0, clampedY);
                }
            }
        });
    }

    /** Khoảng chừa dưới nội dung: thanh dọc không che phía dưới nên chỉ cần lề nhỏ. */
    private int navClear() { return dp(rail ? 16 : NAV_CLEAR); }

    /** Phần màn hình nội dung phải chừa (px): thanh hệ thống + tai thỏ + bàn phím. {trái, trên, phải, dưới} */
    private static int[] insetsOf(WindowInsets w) {
        int l, t, r, b;
        if (Build.VERSION.SDK_INT >= 30) {
            android.graphics.Insets s = w.getInsets(WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            int ime = w.getInsets(WindowInsets.Type.ime()).bottom;
            l = s.left;
            t = s.top;
            r = s.right;
            b = Math.max(s.bottom, ime);
        } else {
            l = w.getSystemWindowInsetLeft();
            t = w.getSystemWindowInsetTop();
            r = w.getSystemWindowInsetRight();
            b = w.getSystemWindowInsetBottom();
            if (Build.VERSION.SDK_INT >= 28) {
                android.view.DisplayCutout dc = w.getDisplayCutout();
                if (dc != null) {
                    l = Math.max(l, dc.getSafeInsetLeft());
                    t = Math.max(t, dc.getSafeInsetTop());
                    r = Math.max(r, dc.getSafeInsetRight());
                    b = Math.max(b, dc.getSafeInsetBottom());
                }
            }
        }
        return new int[]{l, t, r, b};
    }










    private boolean isDark() {
        String t = sp.getString("theme", "system");
        if ("dark".equals(t)) return true;
        if ("light".equals(t)) return false;
        return (getResources().getConfiguration().uiMode & Configuration.UI_MODE_NIGHT_MASK) == Configuration.UI_MODE_NIGHT_YES;
    }

    /** Ngôn ngữ đã lưu; chưa chọn thì theo ngôn ngữ máy (vi -> Tiếng Việt, còn lại -> English). */
    private String savedLang() {
        String l = sp.getString("lang", "");
        if (l.isEmpty()) l = "vi".equals(Locale.getDefault().getLanguage()) ? "vi" : "en";
        return l;
    }

    // ---------------------------------------------------------------- lifecycle

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        current = this;
        sp = getSharedPreferences("fontpatch", MODE_PRIVATE);
        en = "en".equals(savedLang());
        Lang.en = en;
        Updater.clean(this);                // xoá file cập nhật còn sót lại sau lần cài trước
        fonts = new FontStore(this);
        pal = Pal.of(isDark());
        tab = b != null ? b.getInt("tab", 0) : 0;
        String side = sp.getString("navside", "right");
        land = getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE;
        rail = land && !"bottom".equals(side);
        railRight = !"left".equals(side);

        List<String> have = fonts.names();
        for (int i = 0; i < 3; i++) {
            // chưa từng chọn -> dùng font mặc định; đã chọn "không đổi" (chuỗi rỗng) thì giữ nguyên lựa chọn đó
            String n = sp.contains("font" + i) ? sp.getString("font" + i, "") : DEFAULT_FONTS[i];
            selFonts[i] = (!n.isEmpty() && have.contains(n)) ? n : null;
        }
        setupPackages();

        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);
        getWindow().setStatusBarColor(0x00000000);        // trong suốt: nền app tràn xuống dưới thanh hệ thống
        getWindow().setNavigationBarColor(0x00000000);
        if (Build.VERSION.SDK_INT >= 29) getWindow().setNavigationBarContrastEnforced(false);
        if (Build.VERSION.SDK_INT >= 28) {                // vẽ cả vào vùng tai thỏ / camera (cạnh ngắn)
            WindowManager.LayoutParams wl = getWindow().getAttributes();
            wl.layoutInDisplayCutoutMode = WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_SHORT_EDGES;
            getWindow().setAttributes(wl);
        }
        if (Build.VERSION.SDK_INT >= 30) getWindow().setDecorFitsSystemWindows(false);
        getWindow().setBackgroundDrawable(new ColorDrawable(pal.bar));
        int flags = View.SYSTEM_UI_FLAG_LAYOUT_STABLE | View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                | View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION;
        if (!pal.dark) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
            if (Build.VERSION.SDK_INT >= 26) flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        getWindow().getDecorView().setSystemUiVisibility(flags);

        FrameLayout root = new FrameLayout(this);
        root.setClipChildren(false);            // ô kính của thanh nav phồng ra ngoài khung thanh khi nhấn/kéo

        // "scene" = nền + các trang. Thanh nav nằm đè lên trên và làm mờ đúng phần scene phía sau nó.
        scene = new FrameLayout(this);
        View bg = new View(this);
        bg.setBackground(new GradientDrawable(GradientDrawable.Orientation.TOP_BOTTOM,
                new int[]{pal.bgTop, pal.bgBottom}));
        scene.addView(bg, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout frame = new FrameLayout(this);
        pages[0] = buildHomePage();
        pages[1] = buildFontPage();
        pages[2] = buildAdvancedPage();
        for (View p : pages) frame.addView(p, new FrameLayout.LayoutParams(MATCH, MATCH));
        FrameLayout.LayoutParams flp = new FrameLayout.LayoutParams(MATCH, MATCH);
        if (rail) {                                   // chừa chỗ cho thanh dọc
            if (railRight) flp.rightMargin = dp(RAIL_W);
            else flp.leftMargin = dp(RAIL_W);
        }
        scene.addView(frame, flp);
        root.addView(scene, new FrameLayout.LayoutParams(MATCH, MATCH));

        FrameLayout.LayoutParams nlp;
        if (rail) {
            nlp = new FrameLayout.LayoutParams(WRAP, WRAP,
                    (railRight ? Gravity.END : Gravity.START) | Gravity.CENTER_VERTICAL);
            nlp.setMargins(dp(RAIL_EDGE), 0, dp(RAIL_EDGE), 0);
        } else {
            nlp = new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL);   // thanh gọn, nằm giữa
            nlp.setMargins(dp(16), 0, dp(16), dp(NAV_LIFT));
        }
        final View navView = buildNav();
        root.addView(navView, nlp);
        // nền tràn full màn hình; nội dung và thanh nav chừa chỗ cho thanh trạng thái / thanh hệ thống / tai thỏ / bàn phím
        root.setOnApplyWindowInsetsListener((v, ins) -> {
            int[] in = insetsOf(ins);   // trái, trên, phải, dưới
            flp.leftMargin = in[0] + (rail && !railRight ? dp(RAIL_W) : 0);
            flp.rightMargin = in[2] + (rail && railRight ? dp(RAIL_W) : 0);
            flp.topMargin = in[1];
            frame.setLayoutParams(flp);
            if (rail) {
                nlp.leftMargin = dp(RAIL_EDGE) + (railRight ? 0 : in[0]);
                nlp.rightMargin = dp(RAIL_EDGE) + (railRight ? in[2] : 0);
            } else {
                nlp.leftMargin = dp(16) + in[0];
                nlp.rightMargin = dp(16) + in[2];
                nlp.bottomMargin = dp(NAV_LIFT) + in[3];
            }
            navView.setLayoutParams(nlp);
            for (int ci = 0; ci < contents.length; ci++) if (contents[ci] != null) contents[ci].setPadding(dp(16), ci == 0 ? dp(8) : dp(66), dp(16), navClear() + in[3]);
            return ins;
        });
        setupDrop(root);
        setContentView(root);
        playThemeFade(root);

        if (backdrop != null) backdrop.src = scene;
        if (hasBackdrop()) {
            // bố cục đổi (đổi chữ, hiện/ẩn badge...) thì vẽ lại lớp mờ / lớp kính
            getWindow().getDecorView().getViewTreeObserver()
                    .addOnGlobalLayoutListener(this::invalidateBackdrop);
        }

        refreshHome();
        refreshFonts(false);
        selectTab(tab, false);
        final int[] restoreY = (b != null && b.containsKey("scrollY"))
                ? b.getIntArray("scrollY")
                : savedScrollY;
        if (restoreY != null) {
            for (int i = 0; i < 3; i++) {
                if (i < restoreY.length && restoreY[i] > 0) {
                    restoreScroll(scrollViews[i], restoreY[i]);
                }
            }
        }
        if (b == null) {                       // chỉ khi mở app lần đầu
            Anim.stamp(sealView, 250);
            Anim.stagger(contents[tab], 0, 0f, dp(16), 80);
            checkUpdate(false, null);                // kiểm tra cập nhật âm thầm, chỉ báo khi có bản mới
        }
        blurTick(700);

        // Activity vừa được tạo lại giữa lúc đang patch: dựng lại hộp thoại tiến trình, hoặc hiện kết quả đã xong trong lúc đó
        if (runningTitle != null) {
            showProgress(runningTitle);
        } else if (pending != null) {
            Pending p = pending;
            pending = null;
            showResult(p.action, p.state, p.message, p.hint);
        }
    }

    @Override
    protected void onSaveInstanceState(Bundle out) {
        super.onSaveInstanceState(out);
        saveScrollPositions();
        out.putInt("tab", tab);
        out.putIntArray("scrollY", savedScrollY);
    }

    @Override
    protected void onDestroy() {
        if (progressDlg != null && progressDlg.isShowing()) progressDlg.dismiss();
        progressDlg = null;
        progressStep = null;
        if (current == this) current = null;
        if (scene != null) scene.removeCallbacks(blurTick);
        super.onDestroy();
    }

    @Override
    public void onBackPressed() {
        if (tab != 0) selectTab(0);
        else super.onBackPressed();
    }

    // ---------------------------------------------------------------- shared pieces

    /** Đầu trang con: một viên kính nổi chứa tiêu đề (không có thanh nền), nội dung cuộn trôi bên dưới. Quay về Home bằng thanh nav / nút Back. */
    private View backPill(String title) {
        float den = getResources().getDisplayMetrics().density;
        LinearLayout pill = new LinearLayout(this);
        pill.setOrientation(LinearLayout.HORIZONTAL);
        pill.setGravity(Gravity.CENTER_VERTICAL);
        pill.setPadding(dp(22), 0, dp(22), 0);
        if (!(Glass.enabled(sp) && GlassDialog.applyToButton(pill, pal, den, 44)))
            pill.setBackground(rounded(pal.tonal, 22, 0));
        TextView t = serif(tv(title, 18, pal.text, false), false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        pill.addView(t, lpm(WRAP, WRAP, 0, 0, 0, 0));
        FrameLayout.LayoutParams lp = new FrameLayout.LayoutParams(WRAP, dp(44), Gravity.TOP | Gravity.START);
        lp.setMargins(dp(16), dp(10), dp(16), 0);
        pill.setLayoutParams(lp);
        return pill;
    }

    private View header(boolean home, String title) {
        LinearLayout h = new LinearLayout(this);
        h.setOrientation(LinearLayout.HORIZONTAL);
        h.setGravity(Gravity.CENTER_VERTICAL);
        h.setPadding(dp(16), dp(10), dp(16), dp(6));
        if (home) {
            FrameLayout logo = new FrameLayout(this);
            ImageView ink = new ImageView(this);
            ink.setImageResource(R.drawable.logo_ink);
            ink.setColorFilter(pal.logoTint);
            ink.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.addView(ink, new FrameLayout.LayoutParams(dp(30), dp(38), Gravity.CENTER));
            ImageView seal = new ImageView(this);
            seal.setImageResource(R.drawable.seal);
            seal.setScaleType(ImageView.ScaleType.FIT_CENTER);
            logo.addView(seal, new FrameLayout.LayoutParams(dp(11), dp(13), Gravity.TOP | Gravity.END));
            sealView = seal;
            h.addView(logo, new LinearLayout.LayoutParams(dp(42), dp(46)));
        } else {
            return backPill(title);
        }
        TextView t = serif(tv(title, 21, pal.text, false), false);
        t.setSingleLine(true);
        t.setEllipsize(TextUtils.TruncateAt.END);
        h.addView(t, lpm(0, WRAP, home ? 8 : 12, 0, 0, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        return h;
    }

    /** Nút chính: một khối màu nhấn phẳng, bo tròn. Phản hồi lúc chạm, trở về bằng lò xo (Anim.press). */
    private TextView primaryButton(String label, int heightDp) {
        TextView b = serif(tv(label, 17, pal.onPrimary, false), false);
        b.setGravity(Gravity.CENTER);
        b.setLetterSpacing(0.01f);
        b.setSingleLine(true);
        b.setEllipsize(TextUtils.TruncateAt.END);
        b.setPadding(dp(20), 0, dp(20), 0);
        b.setMinHeight(dp(heightDp));
        b.setBackground(rounded(pal.primary, heightDp / 2, 0));
        pressFx(b);
        return b;
    }

    /** Nút phụ: nền nhạt cùng tông, không viền. */
    private TextView tonalButton(String label, int heightDp) {
        TextView b = tv(label, 14, pal.text, true);
        b.setGravity(Gravity.CENTER);
        b.setSingleLine(true);
        b.setMinHeight(dp(heightDp));
        b.setBackground(rounded(pal.tonal, heightDp / 2, 0));
        pressFx(b);
        return b;
    }

    private View mainButton(String label, View.OnClickListener l) {
        TextView b = primaryButton(label, 56);
        b.setOnClickListener(l);
        b.setLayoutParams(lpm(MATCH, WRAP, 0, 0, 0, 0));
        return b;
    }

    // ---------------------------------------------------------------- Patch (home)

    /**
     * Xoay ngang: chia nội dung trang thành 2 cột (không phải kéo dài một cột). Xoay dọc: trả về {c, c}, tức một cột như cũ.
     * Cột trái/phải nhận phần tử theo thứ tự gọi addView; dùng tightTop() sau khi thêm xong để cột phải không bị lệch xuống.
     */
    private LinearLayout[] columns(LinearLayout c) {
        if (!land) return new LinearLayout[]{c, c};
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setBaselineAligned(false);
        LinearLayout l = new LinearLayout(this);
        l.setOrientation(LinearLayout.VERTICAL);
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.VERTICAL);
        row.addView(l, lpm(0, WRAP, 0, 0, 6, 0));
        row.addView(r, lpm(0, WRAP, 6, 0, 0, 0));
        ((LinearLayout.LayoutParams) l.getLayoutParams()).weight = 1f;
        ((LinearLayout.LayoutParams) r.getLayoutParams()).weight = 1f;
        c.addView(row, new LinearLayout.LayoutParams(MATCH, WRAP));
        return new LinearLayout[]{l, r};
    }

    /** Phần tử đầu mỗi cột không chừa lề trên, để hai cột thẳng hàng. */
    private void tightTop(LinearLayout[] cols) {
        if (cols[0] == cols[1]) return;
        for (LinearLayout col : cols) {
            if (col.getChildCount() == 0) continue;
            ((LinearLayout.LayoutParams) col.getChildAt(0).getLayoutParams()).topMargin = 0;
        }
    }

    private View buildHomePage() {
        LinearLayout page = new LinearLayout(this);
        page.setOrientation(LinearLayout.VERTICAL);
        page.addView(header(true, "Patch Font"));

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(8), dp(16), navClear());
        contents[0] = c;
        c.setClipToPadding(false);      // con trượt/lệch khi chuyển trang không bị cắt ở mép padding (nút cuối trang)
        final LinearLayout[] col = columns(c);   // xoay ngang: trái = game/nút/thông tin, phải = FPS

        // game card
        LinearLayout game = card();
        game.setOrientation(LinearLayout.HORIZONTAL);
        game.setGravity(Gravity.CENTER_VERTICAL);
        game.setPadding(dp(14), dp(14), dp(14), dp(14));
        // nền: ảnh minh hoạ bên phải, mờ dần sang màu thẻ ở bên trái cho chữ dễ đọc
        game.setBackground(new HeroArt(
                android.graphics.BitmapFactory.decodeResource(getResources(), R.drawable.hero_art),
                pal.card, (pal.accent & 0x00FFFFFF) | 0x2E000000, dp(28), Math.round(1.5f * dp(1))));
        // Avatar: icon thật của app game đang chọn (mỗi bản VN / Global / CN có gói riêng nên tự đồng bộ);
        // chưa cài game thì hiện ô trống ghi mã vùng.
        FrameLayout avatar = new FrameLayout(this);
        avatar.setBackground(rounded(pal.tonal, 22, 0));
        avatar.setOutlineProvider(ViewOutlineProvider.BACKGROUND);
        avatar.setClipToOutline(true);
        ivGameIcon = new ImageView(this);
        ivGameIcon.setScaleType(ImageView.ScaleType.CENTER_CROP);
        avatar.addView(ivGameIcon, new FrameLayout.LayoutParams(MATCH, MATCH));
        tvGameRegion = tv("", 20, pal.sub, true);
        tvGameRegion.setGravity(Gravity.CENTER);
        avatar.addView(tvGameRegion, new FrameLayout.LayoutParams(MATCH, MATCH));
        // khung viền sáng quanh icon, vẽ đè lên ảnh
        GradientDrawable ring = new GradientDrawable();
        ring.setCornerRadius(dp(22));
        ring.setStroke(Math.round(1.5f * dp(1)), (pal.accent & 0x00FFFFFF) | 0x40000000);
        avatar.setForeground(ring);
        game.addView(avatar, new LinearLayout.LayoutParams(dp(88), dp(88)));
        LinearLayout info = new LinearLayout(this);
        info.setOrientation(LinearLayout.VERTICAL);
        tvGameName = serif(tv("", 17, pal.text, true), true);
        tvGameName.setMaxLines(2);
        tvGameName.setEllipsize(TextUtils.TruncateAt.END);
        tvGameSel = tv("", 12, pal.sub, false);
        tvGameSel.setSingleLine(true);
        tvGameSel.setEllipsize(TextUtils.TruncateAt.END);
        TextView change = tv(t("Thay đổi", "Change"), 13, pal.text, false);
        change.setBackground(rounded(pal.tonal, 20, 0));
        change.setPadding(dp(18), dp(8), dp(18), dp(8));
        change.setOnClickListener(v -> showGamePicker(v));
        pressFx(change);
        info.addView(tvGameName);
        // trạng thái bằng một chấm: xanh = đã cài game, xám = chưa cài (thay cho dòng chữ trạng thái)
        LinearLayout verRow = new LinearLayout(this);
        verRow.setOrientation(LinearLayout.HORIZONTAL);
        verRow.setGravity(Gravity.CENTER_VERTICAL);
        viewGameDot = new View(this);
        GradientDrawable dotBg = new GradientDrawable();
        dotBg.setShape(GradientDrawable.OVAL);
        dotBg.setColor(pal.sub);
        viewGameDot.setBackground(dotBg);
        verRow.addView(viewGameDot, lpm(dp(8), dp(8), 0, 0, 7, 0));
        verRow.addView(tvGameSel);
        info.addView(verRow, lpm(WRAP, WRAP, 0, 3, 0, 0));
        // đệm giãn: đẩy nút "Thay đổi" xuống góc dưới bên phải, ngang đáy avatar
        info.addView(new View(this), new LinearLayout.LayoutParams(1, 0, 1f));
        LinearLayout.LayoutParams chLp = new LinearLayout.LayoutParams(WRAP, WRAP);
        chLp.gravity = Gravity.END;
        chLp.topMargin = dp(6);
        info.addView(change, chLp);
        game.addView(info, new LinearLayout.LayoutParams(0, MATCH, 1f));
        ((LinearLayout.LayoutParams) info.getLayoutParams()).leftMargin = dp(16);
        col[0].addView(game, lpm(MATCH, WRAP, 0, 0, 0, 0));

        // main action
        col[0].addView(mainButton(t("Cài patch font", "Install font patch"), v -> doInstall()),
                lpm(MATCH, WRAP, 0, 14, 0, 0));

        // three small actions
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.addView(actionCard(R.drawable.ic_delete, t("Gỡ patch", "Remove patch"),
                v -> task(t("Gỡ patch font", "Remove font patch"), t("Đã gỡ patch font.", "Font patch removed."),
                        (p, bk, pk) -> p.uninstall(pk, bk), this::clearApplied)), lpm(0, WRAP, 0, 0, 5, 0));
        row.addView(actionCard(R.drawable.ic_adjust, t("Xem hotfix", "View hotfix"),
                v -> task(t("Xem hotfix", "View hotfix"), t("Danh sách hotfix ở bên dưới.", "The hotfix list is shown below."),
                        (p, bk, pk) -> { p.listCandidates(pk, bk); return null; }, null)), lpm(0, WRAP, 5, 0, 5, 0));
        row.addView(actionCard(R.drawable.ic_restore, t("Khôi phục", "Restore"),
                v -> task(t("Khôi phục", "Restore"), t("Đã khôi phục db_mp từ bản sao lưu.", "db_mp restored from the backup."),
                        (p, bk, pk) -> p.restore(pk, bk), () -> { clearApplied(); clearFpsApplied(); })), lpm(0, WRAP, 5, 0, 0, 0));
        for (int i = 0; i < 3; i++) ((LinearLayout.LayoutParams) row.getChildAt(i).getLayoutParams()).weight = 1f;
        col[0].addView(row, lpm(MATCH, WRAP, 0, 12, 0, 0));

        // FPS unlock
        LinearLayout fc = card();
        LinearLayout ft = new LinearLayout(this);
        ft.setOrientation(LinearLayout.HORIZONTAL);
        ft.setGravity(Gravity.CENTER_VERTICAL);
        ft.addView(tv(t("Mở khóa FPS", "FPS unlock"), 15, pal.text, true), new LinearLayout.LayoutParams(0, WRAP, 1f));
        tvFpsBadge = tv("", 11, pal.badgeText, true);
        tvFpsBadge.setBackground(rounded(pal.badgeBg, 12, 0));
        tvFpsBadge.setPadding(dp(10), dp(4), dp(10), dp(4));
        tvFpsBadge.setVisibility(View.GONE);
        ft.addView(tvFpsBadge);
        fc.addView(ft);
        fc.addView(tv(t("Ép game chạy đúng mức FPS đã chọn, bỏ qua cài đặt FPS trong game và tắt vsync.",
                "Forces the game to the FPS you pick, ignoring the in-game FPS setting, and turns vsync off."),
                12, pal.sub, false), lpm(WRAP, WRAP, 0, 2, 0, 0));
        final int[] fpsVals = PatchModel.FPS_CHOICES;
        String[] fpsNames = new String[fpsVals.length];
        int fpsSel = 0;
        int savedFps = sp.getInt("fps", 120);
        for (int i = 0; i < fpsVals.length; i++) {
            fpsNames[i] = fpsVals[i] + " FPS";
            if (fpsVals[i] == savedFps) fpsSel = i;
        }
        fc.addView(segmented(fpsNames, fpsSel, 0, i -> sp.edit().putInt("fps", fpsVals[i]).apply()),
                lpm(MATCH, WRAP, 0, 10, 0, 0));
        LinearLayout fb = new LinearLayout(this);
        fb.setOrientation(LinearLayout.HORIZONTAL);
        TextView fApply = primaryButton(t("Áp dụng", "Apply"), 44);
        fApply.setTextSize(14);
        fApply.setTypeface(Typeface.DEFAULT_BOLD);
        fApply.setOnClickListener(v -> doInstallFps());
        TextView fRemove = tonalButton(t("Gỡ", "Remove"), 44);
        fRemove.setOnClickListener(v -> task(t("Gỡ mở khóa FPS", "Remove FPS unlock"),
                t("Đã gỡ mở khóa FPS.", "FPS unlock removed."),
                (p, bk, pk) -> p.uninstallFps(pk, bk), this::clearFpsApplied));
        fb.addView(fApply, new LinearLayout.LayoutParams(0, dp(44), 1f));
        fb.addView(fRemove, lpm(0, dp(44), 10, 0, 0, 0));
        ((LinearLayout.LayoutParams) fRemove.getLayoutParams()).weight = 1f;
        fc.addView(fb, lpm(MATCH, WRAP, 0, 12, 0, 0));
        // ghi chú: nói rõ phạm vi của tính năng để không bị hiểu nhầm là "tăng FPS"
        TextView fNote = tv(t("Lưu ý: tính năng này chỉ gỡ giới hạn FPS của game, tùy chọn FPS trong phần cài đặt của game vẫn không được mở khóa. "
                        + "FPS thực tế đạt được phụ thuộc vào cấu hình máy của bạn.",
                "Note: this only lifts the game's FPS cap; the FPS option in the game's own settings stays locked. "
                        + "The FPS you actually reach depends on your device's hardware."),
                11, pal.sub, false);
        fNote.setBackground(rounded(pal.tonal, 14, 0));
        fNote.setPadding(dp(12), dp(9), dp(12), dp(9));
        fc.addView(fNote, lpm(MATCH, WRAP, 0, 12, 0, 0));
        col[1].addView(fc, lpm(MATCH, WRAP, 0, 12, 0, 0));

        // information
        LinearLayout inf = card();
        inf.addView(tv(t("Thông tin", "Information"), 15, pal.text, true));
        tvInfoVer = infoRow(inf, t("Phiên bản game", "Game version"), false);
        tvInfoDir = infoRow(inf, t("Thư mục game", "Game folder"), true);
        tvInfoMethod = infoRow(inf, t("Phương thức ghi", "Write method"), false);
        col[0].addView(inf, lpm(MATCH, WRAP, 0, 12, 0, 0));

        tightTop(col);
        ScrollView sv = scroll(c);
        scrollViews[0] = sv;
        page.addView(sv, new LinearLayout.LayoutParams(MATCH, 0, 1f));
        return page;
    }

    /**
     * Ô chọn ngang: khối sáng trượt giữa các ô, rồi gọi onPick sau delayMs.
     * Android 13+ và đang bật Liquid Glass thì dùng bản kính (GlassSeg: nhấn/kéo, khúc xạ, tán sắc); không thì bản thường.
     */
    private View segmented(String[] names, int initial, int delayMs, java.util.function.IntConsumer onPick) {
        if (Glass.enabled(sp)) {
            try {
                return new GlassSeg(this, pal, names, initial, delayMs, onPick, this::glassFailed);
            } catch (Throwable e) {            // thiết bị không dựng được -> dùng ô chọn thường
                Log.w(TAG, "liquid glass segmented unavailable, using classic", e);
            }
        }
        final int n = names.length;
        final int[] sel = {initial};
        final int[] cw = {0};
        final FrameLayout seg = new FrameLayout(this);
        // viền nằm ở thanh ngoài, ô chọn bên trong không viền
        seg.setBackground(rounded(pal.tonal, 30, pal.accent & 0x00FFFFFF | 0x40000000));
        seg.setPadding(dp(4), dp(4), dp(4), dp(4));
        final View hl = new View(this);
        hl.setBackground(rounded(pal.accent & 0x00FFFFFF | 0x26000000, 30, 0));
        seg.addView(hl, new FrameLayout.LayoutParams(0, dp(42)));
        LinearLayout cells = new LinearLayout(this);
        cells.setOrientation(LinearLayout.HORIZONTAL);
        seg.addView(cells, new FrameLayout.LayoutParams(MATCH, dp(42)));
        final TextView[] lbs = new TextView[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            TextView lb = tv(names[i], 13, i == sel[0] ? pal.accent : pal.text, i == sel[0]);
            lb.setGravity(Gravity.CENTER);
            lb.setSingleLine(true);
            lb.setOnClickListener(v -> {
                if (idx == sel[0]) return;
                sel[0] = idx;
                for (int k = 0; k < n; k++) {
                    lbs[k].setTextColor(k == idx ? pal.accent : pal.text);
                    lbs[k].setTypeface(k == idx ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
                }
                Anim.slideAxis(hl, idx * cw[0], false, true);
                if (delayMs > 0) seg.postDelayed(() -> onPick.accept(idx), delayMs);
                else onPick.accept(idx);
            });
            lbs[i] = lb;
            cells.addView(lb, new LinearLayout.LayoutParams(0, MATCH, 1f));
        }
        // Đo theo layout thật: seg.post() chạy trước lần layout đầu (lúc view vừa gắn vào cửa sổ, getWidth() còn 0)
        // nên khối sáng bị rộng 0 và mất khung chọn khi tắt Liquid Glass.
        seg.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, or, ob) -> {
            int w = (r - l) - dp(8);
            if (w <= 0) return;
            int c = w / n;
            FrameLayout.LayoutParams hp = (FrameLayout.LayoutParams) hl.getLayoutParams();
            if (cw[0] == c && hp.width == c) return;
            cw[0] = c;
            hp.width = c;
            hl.setLayoutParams(hp);
            Motion.stop(hl);
            hl.setTranslationX(sel[0] * c);
        });
        return seg;
    }

    private View actionCard(int iconRes, String label, View.OnClickListener l) {
        LinearLayout a = new LinearLayout(this);
        a.setOrientation(LinearLayout.VERTICAL);
        a.setGravity(Gravity.CENTER_HORIZONTAL);
        a.setBackground(rounded(pal.card, 20, pal.stroke));
        a.setPadding(dp(6), dp(14), dp(6), dp(12));
        ImageView iv = icon(iconRes, pal.text, 26);
        a.addView(iv);
        TextView t = tv(label, 13, pal.text, false);
        t.setGravity(Gravity.CENTER);
        a.addView(t, lpm(WRAP, WRAP, 0, 8, 0, 0));
        a.setOnClickListener(l);
        pressFx(a);
        return a;
    }

    private TextView infoRow(LinearLayout parent, String label, boolean marquee) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.addView(tv(label, 13, pal.sub, false));
        TextView v = tv("", 13, pal.text, false);
        v.setSingleLine(true);
        v.setGravity(Gravity.END);
        if (marquee) {
            // giá trị dài: tự cuộn nhẹ qua lại thay vì cắt bằng "…"
            MarqueeBox box = new MarqueeBox(this);
            box.addView(v, new FrameLayout.LayoutParams(WRAP, WRAP));
            r.addView(box, lpm(0, WRAP, 16, 0, 0, 0));
            ((LinearLayout.LayoutParams) box.getLayoutParams()).weight = 1f;
            box.bind(v);
        } else {
            v.setEllipsize(TextUtils.TruncateAt.END);
            r.addView(v, lpm(0, WRAP, 16, 0, 0, 0));
            ((LinearLayout.LayoutParams) v.getLayoutParams()).weight = 1f;
        }
        parent.addView(r, lpm(MATCH, WRAP, 0, 10, 0, 0));
        return v;
    }

    private TextView tvFpsBadge;
    private ImageView ivGameIcon;
    private View viewGameDot;
    private TextView tvGameRegion;

    private void refreshHome() {
        String ver = Games.version(this, pkg);
        tvGameName.setText(Games.name(pkg));
        tvGameSel.setText(ver != null ? ver : t("Chưa cài game", "Game not installed"));
        ((GradientDrawable) viewGameDot.getBackground()).setColor(ver != null ? 0xFF34C759 : pal.sub);
        Drawable icon = null;
        if (ver != null) {
            try { icon = getPackageManager().getApplicationIcon(pkg); } catch (Exception ignored) { }
        }
        ivGameIcon.setImageDrawable(icon);
        tvGameRegion.setText(icon == null ? Games.region(pkg) : "");
        tvInfoVer.setText(ver != null ? ver : t("Chưa cài", "Not installed"));
        tvInfoDir.setText("/storage/emulated/0/Android/data/" + pkg + "/files");
        tvInfoMethod.setText(methodName(method()));
        refreshFps();
    }

    private String fpsKey() { return "fps_applied_" + pkg; }

    private void refreshFps() {
        if (tvFpsBadge == null) return;
        int f = sp.getInt(fpsKey(), 0);
        tvFpsBadge.setVisibility(f > 0 ? View.VISIBLE : View.GONE);
        if (f > 0) tvFpsBadge.setText(t("Đang dùng " + f + " FPS", "Using " + f + " FPS"));
    }

    private void markFpsApplied(int fps) {
        sp.edit().putInt(fpsKey(), fps).apply();
        refreshFps();
    }

    private void clearFpsApplied() {
        sp.edit().remove(fpsKey()).apply();
        refreshFps();
    }

    private void doInstallFps() {
        final int fps = sp.getInt("fps", 120);
        final String hot = etHotfix.getText().toString();
        task(t("Mở khóa FPS", "FPS unlock"),
                t("Đã gắn mở khóa " + fps + " FPS. Hãy mở lại game để áp dụng.", "FPS unlock set to " + fps + ". Reopen the game to apply."),
                (p, bk, pk) -> { p.installFps(pk, bk, fps, hot); return null; }, () -> markFpsApplied(fps));
    }

    // ---------------------------------------------------------------- Font

    private View buildFontPage() {
        FrameLayout page = new FrameLayout(this);
        View pill = header(false, t("Quản lý Font", "Font Manager"));

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(66), dp(16), navClear());
        contents[1] = c;
        c.setClipToPadding(false);      // con trượt/lệch khi chuyển trang không bị cắt ở mép padding (nút cuối trang)
        final LinearLayout[] col = columns(c);   // xoay ngang: trái = 3 mục font, phải = thêm font + font đã thêm
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout card = card();
            LinearLayout top = new LinearLayout(this);
            top.setOrientation(LinearLayout.HORIZONTAL);
            top.setGravity(Gravity.CENTER_VERTICAL);
            LinearLayout colL = new LinearLayout(this);
            colL.setOrientation(LinearLayout.VERTICAL);
            String st = slotTitle(i);
            SpannableString title = new SpannableString(st);
            int k = st.indexOf('(');
            if (k > 0) title.setSpan(new RelativeSizeSpan(0.8f), k, title.length(), Spanned.SPAN_EXCLUSIVE_EXCLUSIVE);
            TextView tt = tv("", 14, pal.text, true);
            tt.setText(title);
            colL.addView(tt);
            tvFontName[i] = tv("", 12, pal.sub, false);
            tvFontName[i].setSingleLine(true);
            tvFontName[i].setEllipsize(TextUtils.TruncateAt.END);
            colL.addView(tvFontName[i], lpm(WRAP, WRAP, 0, 2, 0, 0));
            top.addView(colL, new LinearLayout.LayoutParams(0, WRAP, 1f));

            FrameLayout right = new FrameLayout(this);
            tvBadge[i] = tv(t("Đang sử dụng", "In use"), 11, pal.badgeText, true);
            tvBadge[i].setBackground(rounded(pal.badgeBg, 12, 0));
            tvBadge[i].setPadding(dp(10), dp(4), dp(10), dp(4));
            right.addView(tvBadge[i], new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_VERTICAL | Gravity.END));
            tvChoose[i] = tv(t("Chọn", "Select"), 12, pal.text, false);
            tvChoose[i].setBackground(rounded(pal.tonal, 16, 0));
            tvChoose[i].setPadding(dp(16), dp(6), dp(16), dp(6));
            right.addView(tvChoose[i], new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER_VERTICAL | Gravity.END));
            top.addView(right, lpm(WRAP, WRAP, 8, 0, 0, 0));
            card.addView(top);

            tvPreview[i] = tv(sample(), 20, pal.text, false);
            tvPreview[i].setGravity(Gravity.CENTER);
            card.addView(tvPreview[i], lpm(MATCH, WRAP, 0, 14, 0, 4));

            card.setOnClickListener(v -> showFontPicker(idx, tvChoose[idx]));   // neo vào nút "Chọn", không phải cả thẻ
            pressFx(card);
            col[0].addView(card, lpm(MATCH, WRAP, 0, i == 0 ? 0 : 12, 0, 0));
        }
        col[1].addView(mainButton(t("Thêm font .ttf", "Add .ttf font"), v -> pickFont()), lpm(MATCH, WRAP, 0, 18, 0, 0));

        TextView dragNote = tv(t("Mẹo: có thể kéo thả file .ttf vào đây từ ZArchiver, khi mở ở chế độ chia đôi màn hình hoặc cửa sổ nổi.",
                "Tip: you can drag and drop a .ttf file in from ZArchiver while it is open in split-screen or a floating window."), 11, pal.sub, false);
        dragNote.setGravity(Gravity.CENTER);
        col[1].addView(dragNote, lpm(MATCH, WRAP, 8, 8, 8, 0));

        // quản lý các font đã thêm: xoá font không dùng nữa
        LinearLayout mgr = card();
        mgr.addView(tv(t("Font đã thêm", "Added fonts"), 15, pal.text, true));
        mgr.addView(tv(t("Chỉ xoá khỏi app, font đã cài vào game không bị ảnh hưởng", "Removed from the app only; fonts already installed in the game are not affected"),
                11, pal.sub, false), lpm(MATCH, WRAP, 0, 2, 0, 0));
        fontMgrList = new LinearLayout(this);
        fontMgrList.setOrientation(LinearLayout.VERTICAL);
        mgr.addView(fontMgrList, lpm(MATCH, WRAP, 0, 6, 0, 0));
        col[1].addView(mgr, lpm(MATCH, WRAP, 0, 12, 0, 0));
        refreshFontMgr();
        tightTop(col);
        ScrollView sv = scroll(c);
        scrollViews[1] = sv;
        page.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        page.addView(pill);
        return page;
    }

    private void refreshFonts(boolean animate) {
        for (int i = 0; i < 3; i++) {
            String n = selFonts[i];
            tvFontName[i].setText(n != null ? n : none());
            boolean applied = n != null && n.equals(sp.getString(appliedKey(i), ""));
            boolean was = tvBadge[i].getVisibility() == View.VISIBLE;
            Anim.reset(tvBadge[i]);
            Anim.reset(tvChoose[i]);
            tvBadge[i].setVisibility(applied ? View.VISIBLE : View.GONE);
            tvChoose[i].setVisibility(applied ? View.GONE : View.VISIBLE);
            if (animate && applied != was) Anim.popIn(applied ? tvBadge[i] : tvChoose[i]);
            TextView p = tvPreview[i];
            if (n == null) {
                p.setText(t("(không đổi font này)", "(this font stays unchanged)"));
                p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
                p.setTextColor(pal.sub);
            } else {
                Typeface tf = fonts.typeface(n);
                if (tf == null) {   // file hỏng / không đọc được: báo rõ thay vì lặng lẽ hiện font serif như thể font vẫn tốt
                    p.setText(t("(không đọc được font này)", "(can't load this font)"));
                    p.setTypeface(Typeface.create(Typeface.SERIF, Typeface.NORMAL));
                    p.setTextColor(pal.sub);
                } else {
                    p.setText(sample());
                    p.setTypeface(tf);
                    p.setTextColor(pal.text);
                }
            }
            if (animate && !Objects.equals(shownFont[i], n)) Anim.swap(p, dp(6), 0f);
            shownFont[i] = n;
        }
        blurTick(400);
    }

    private String fontSize(long b) {
        return b >= 1048576 ? String.format(Locale.US, "%.1f MB", b / 1048576.0) : Math.max(1, b / 1024) + " KB";
    }

    /** Dựng lại danh sách font đã thêm; mỗi dòng có nút xoá (hỏi xác nhận trước). */
    private void refreshFontMgr() {
        if (fontMgrList == null) return;
        fontMgrList.removeAllViews();
        List<String> user = fonts.userNames();
        if (user.isEmpty()) {
            fontMgrList.addView(tv(t("Chưa có font nào được thêm", "No fonts added yet"), 13, pal.sub, false), lpm(MATCH, WRAP, 0, 8, 0, 4));
            return;
        }
        for (int i = 0; i < user.size(); i++) {
            final String name = user.get(i);
            if (i > 0) fontMgrList.addView(divider());
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setPadding(0, dp(8), 0, dp(8));
            LinearLayout col = new LinearLayout(this);
            col.setOrientation(LinearLayout.VERTICAL);
            TextView nm = tv(name, 15, pal.text, false);
            Typeface tf = fonts.typeface(name);
            if (tf != null) nm.setTypeface(tf);
            nm.setSingleLine(true);
            nm.setEllipsize(TextUtils.TruncateAt.END);
            col.addView(nm);
            col.addView(tv(fontSize(fonts.userSize(name)), 11, pal.sub, false), lpm(WRAP, WRAP, 0, 2, 0, 0));
            row.addView(col, new LinearLayout.LayoutParams(0, WRAP, 1f));
            ImageView del = icon(R.drawable.ic_delete, Pal.SEAL_RED, 22);
            del.setPadding(dp(4), dp(4), dp(4), dp(4));
            del.setOnClickListener(v -> confirmDeleteFont(name));
            pressFx(del);
            row.addView(del, lpm(dp(34), dp(34), 8, 0, 0, 0));
            fontMgrList.addView(row);
        }
    }

    private void confirmDeleteFont(final String name) {
        boolean used = false;
        for (int i = 0; i < 3; i++) if (name.equals(selFonts[i])) used = true;
        String msg = t("Xoá \"" + name + "\" khỏi app?", "Remove \"" + name + "\" from the app?")
                + (used ? t("\nFont này đang được chọn, mục đó sẽ về \"không đổi\".", "\nThis font is currently selected; that slot will go back to \"unchanged\".") : "");
        infoDialog(t("Xoá font", "Remove font"), msg, t("Xoá", "Remove"), () -> {
            if (!fonts.delete(name)) {
                Toast.makeText(this, t("Không xoá được font", "Could not remove the font"), Toast.LENGTH_SHORT).show();
                return;
            }
            boolean changed = false;
            for (int i = 0; i < 3; i++) {
                if (name.equals(selFonts[i]) && !fonts.names().contains(name)) {   // font đóng gói cùng tên thì vẫn còn
                    selFonts[i] = null;
                    sp.edit().putString("font" + i, "").apply();
                    changed = true;
                }
            }
            if (changed) refreshFonts(true);
            refreshFontMgr();
        });
    }

    private String appliedKey(int slot) { return "applied_" + pkg + "_" + slot; }

    private void markApplied(String[] chosen) {
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < 3; i++) e.putString(appliedKey(i), chosen[i] == null ? "" : chosen[i]);
        e.apply();
        refreshFonts(false);
    }

    private void clearApplied() {
        SharedPreferences.Editor e = sp.edit();
        for (int i = 0; i < 3; i++) e.remove(appliedKey(i));
        e.apply();
        refreshFonts(false);
    }

    private void showFontPicker(int slot, View anchor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        List<String> names = new ArrayList<>();
        names.add(none());                       // vị trí 0 = "không đổi"
        names.addAll(fonts.names());
        final PopupWindow[] ref = new PopupWindow[1];
        for (int i = 0; i < names.size(); i++) {
            final String name = names.get(i);
            final boolean isNone = i == 0;
            boolean cur = isNone ? selFonts[slot] == null : name.equals(selFonts[slot]);
            TextView row = tv(name, 16, cur ? pal.accent : pal.text, cur);
            if (!isNone) {
                Typeface tf = fonts.typeface(name);
                if (tf != null) row.setTypeface(tf, cur ? Typeface.BOLD : Typeface.NORMAL);
            }
            row.setSingleLine(true);
            row.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout line = mxRow(row, cur);
            line.setOnClickListener(v -> {
                selFonts[slot] = isNone ? null : name;
                sp.edit().putString("font" + slot, selFonts[slot] == null ? "" : selFonts[slot]).apply();
                refreshFonts(true);
                Anim.dismiss(ref[0]);
            });
            c.addView(line);
        }
        TextView add = tv(t("+ Thêm font .ttf", "+ Add .ttf font"), 15, pal.accent, true);
        LinearLayout addRow = mxRow(add, false);
        addRow.setOnClickListener(v -> { Anim.dismiss(ref[0]); pickFont(); });
        c.addView(addRow);
        ref[0] = showMxPopup(anchor, c, 200);
    }

    private void pickFont() {
        Intent i = new Intent(Intent.ACTION_OPEN_DOCUMENT);
        i.addCategory(Intent.CATEGORY_OPENABLE);
        i.setType("*/*");
        i.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true);      // chọn nhiều font cùng lúc
        startActivityForResult(i, REQ_FONT);
    }

    @Override
    protected void onActivityResult(int req, int res, Intent data) {
        super.onActivityResult(req, res, data);
        if (req != REQ_FONT || res != RESULT_OK || data == null) return;
        List<Uri> uris = new ArrayList<>();
        ClipData cd = data.getClipData();
        if (cd != null) {                                   // chọn nhiều: các file nằm trong ClipData
            for (int k = 0; k < cd.getItemCount(); k++) {
                Uri u = cd.getItemAt(k).getUri();
                if (u != null) uris.add(u);
            }
        } else if (data.getData() != null) {                // chọn một file
            uris.add(data.getData());
        }
        importFonts(uris, null);
    }

    /** Tên hiển thị của một Uri (provider có thể trả về null), mặc định "custom.ttf". */
    private String displayName(Uri u) {
        String name = "custom.ttf";
        try (Cursor c = getContentResolver().query(u, null, null, null, null)) {
            if (c != null && c.moveToFirst()) {
                int ix = c.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (ix >= 0 && !c.isNull(ix)) name = c.getString(ix);
            }
        } catch (Exception e) {
            android.util.Log.w(TAG, "đọc tên file thất bại", e);
        }
        return name;
    }

    /**
     * Thêm lần lượt nhiều font (từ hộp chọn file hoặc kéo thả) ở luồng nền, rồi báo một thông báo tổng kết.
     * perms: quyền đọc của thao tác kéo thả, phải giữ đến khi đọc xong (null nếu đến từ hộp chọn file).
     */
    private void importFonts(final List<Uri> uris, final android.view.DragAndDropPermissions perms) {
        if (uris.isEmpty()) {
            if (perms != null) perms.release();
            return;
        }
        new Thread(() -> {
            final List<String> added = new ArrayList<>();
            int skipped = 0;
            int failed = 0;
            String firstErr = null;
            for (Uri u : uris) {
                String name = displayName(u);
                if (!name.toLowerCase(Locale.ROOT).endsWith(".ttf")) {
                    skipped++;
                    continue;
                }
                try {
                    InputStream in = getContentResolver().openInputStream(u);
                    if (in == null) throw new java.io.IOException("cannot open " + u);
                    fonts.add(in, name);                    // add() tự đóng InputStream
                    added.add(name);
                } catch (Exception e) {
                    Log.w(TAG, "import font failed: " + name, e);
                    failed++;
                    if (firstErr == null) firstErr = name + ": " + (e.getMessage() != null ? e.getMessage() : e.toString());
                }
            }
            if (perms != null) perms.release();
            final int fSkipped = skipped;
            final int fFailed = failed;
            final String fErr = firstErr;
            runOnUiThread(() -> {
                if (isFinishing() || isDestroyed()) return;
                StringBuilder msg = new StringBuilder();
                if (added.size() == 1) msg.append(t("Đã thêm font: ", "Font added: ")).append(added.get(0));
                else if (added.size() > 1) msg.append(t("Đã thêm ", "Added ")).append(added.size()).append(t(" font", " fonts"));
                if (fSkipped > 0) {
                    if (msg.length() > 0) msg.append('\n');
                    msg.append(t("Bỏ qua " + fSkipped + " file không phải .ttf", "Skipped " + fSkipped + " non-.ttf file(s)"));
                }
                if (fFailed > 0) {
                    if (msg.length() > 0) msg.append('\n');
                    msg.append(t("Lỗi " + fFailed + " font: ", fFailed + " failed: ")).append(fErr);
                }
                Toast.makeText(this, msg.toString(), fFailed > 0 || added.isEmpty() ? Toast.LENGTH_LONG : Toast.LENGTH_SHORT).show();
                if (!added.isEmpty()) { refreshFonts(true); refreshFontMgr(); }
            });
        }).start();
    }

    // ---------------------------------------------------------------- drag & drop

    private View dropOverlay;
    private boolean dropShown;

    /** Hiện/ẩn vùng nhận file; idempotent để bộ đếm làm mờ nền luôn cân bằng dù sự kiện kéo thả lặp lại. */
    private void showDropZone(boolean on) {
        if (on == dropShown) return;
        dropShown = on;
        dropOverlay.animate().cancel();
        if (on) {
            dropOverlay.setAlpha(0f);
            dropOverlay.setVisibility(View.VISIBLE);
            dropOverlay.animate().alpha(1f).setDuration(Anim.on() ? 160 : 0).start();
        } else {
            dropOverlay.setVisibility(View.GONE);
        }
        backdropBlur(on);
    }

    /** Cho phép kéo file .ttf từ ứng dụng khác (cửa sổ nổi / chia đôi màn hình) thả vào app để thêm font. */
    private void setupDrop(final FrameLayout root) {
        // Khi kéo file vào: nền app mờ đi + tối nhẹ, ở giữa là một thẻ nhỏ đặc nên chữ luôn đọc rõ
        FrameLayout ov = new FrameLayout(this);
        ov.setBackgroundColor(pal.dark ? 0x40000000 : 0x26000000);
        LinearLayout dz = new LinearLayout(this);
        dz.setOrientation(LinearLayout.VERTICAL);
        dz.setGravity(Gravity.CENTER_HORIZONTAL);
        dz.setPadding(dp(28), dp(22), dp(28), dp(22));
        GradientDrawable g = new GradientDrawable();
        g.setColor(pal.mxSurface);
        g.setCornerRadius(dp(28));
        g.setStroke(dp(2), pal.sub & 0x00FFFFFF | 0xAA000000, dp(8), dp(5));        // viền nét đứt
        dz.setBackground(g);
        dz.addView(icon(R.drawable.ic_font, pal.text, 30));
        TextView hint = tv(t("Thả file .ttf vào đây", "Drop .ttf files here"), 16, pal.text, true);
        hint.setGravity(Gravity.CENTER);
        dz.addView(hint, lpm(WRAP, WRAP, 0, 10, 0, 0));
        TextView hint2 = tv(t("để thêm font", "to add fonts"), 13, pal.sub, false);
        hint2.setGravity(Gravity.CENTER);
        dz.addView(hint2, lpm(WRAP, WRAP, 0, 2, 0, 0));
        ov.addView(dz, new FrameLayout.LayoutParams(WRAP, WRAP, Gravity.CENTER));
        dropOverlay = ov;
        dropOverlay.setVisibility(View.GONE);
        dropOverlay.setClickable(false);
        root.addView(dropOverlay, new FrameLayout.LayoutParams(MATCH, MATCH));

        root.setOnDragListener((v, e) -> {
            switch (e.getAction()) {
                case android.view.DragEvent.ACTION_DRAG_STARTED:
                    return e.getClipDescription() != null;   // nhận cả file lẫn văn bản; lọc ở lúc thả
                case android.view.DragEvent.ACTION_DRAG_ENTERED:
                    showDropZone(true);
                    return true;
                case android.view.DragEvent.ACTION_DRAG_EXITED:
                case android.view.DragEvent.ACTION_DRAG_ENDED:
                    showDropZone(false);
                    return true;
                case android.view.DragEvent.ACTION_DROP: {
                    showDropZone(false);
                    ClipData cd = e.getClipData();
                    if (cd == null) return false;
                    List<Uri> uris = new ArrayList<>();
                    for (int k = 0; k < cd.getItemCount(); k++) {
                        Uri u = cd.getItemAt(k).getUri();
                        if (u != null) uris.add(u);
                    }
                    if (uris.isEmpty()) return false;
                    // quyền đọc các Uri kéo thả chỉ có hiệu lực khi xin ngay trong lúc thả
                    android.view.DragAndDropPermissions perms = requestDragAndDropPermissions(e);
                    if (tab != 1) selectTab(1);
                    importFonts(uris, perms);
                    return true;
                }
                default:
                    return true;
            }
        });
    }

    // ---------------------------------------------------------------- Advanced

    private View buildAdvancedPage() {
        FrameLayout page = new FrameLayout(this);
        View pill = header(false, t("Nâng cao", "Advanced"));

        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        c.setPadding(dp(16), dp(66), dp(16), navClear());
        contents[2] = c;
        c.setClipToPadding(false);      // con trượt/lệch khi chuyển trang không bị cắt ở mép padding (nút cuối trang)
        final LinearLayout[] col = columns(c);   // xoay ngang: trái = cách ghi/hotfix/ngôn ngữ, phải = giao diện/cập nhật/credit

        // writing method: two switches that behave like a radio pair
        LinearLayout how = card();
        how.addView(tv(t("Cài đặt nâng cao", "Advanced settings"), 15, pal.text, true));
        LinearLayout mrow = new LinearLayout(this);
        mrow.setOrientation(LinearLayout.HORIZONTAL);
        mrow.setGravity(Gravity.CENTER_VERTICAL);
        mrow.setPadding(0, dp(10), 0, dp(10));
        mrow.addView(icon(R.drawable.ic_security, pal.text, 22));
        LinearLayout mt = new LinearLayout(this);
        mt.setOrientation(LinearLayout.VERTICAL);
        mt.addView(tv(t("Cách ghi file", "Write method"), 14, pal.text, false));
        mt.addView(tv(t("Tự động thử Root trước, không được thì dùng Shizuku", "Auto tries Root first, then Shizuku"), 11, pal.sub, false));
        LinearLayout.LayoutParams mtl = lpm(0, WRAP, 12, 0, 8, 0);
        mtl.weight = 1f;
        mrow.addView(mt, mtl);
        tvMethodVal = tv(methodName(method()), 14, pal.sub, false);
        mrow.addView(tvMethodVal);
        mrow.addView(icon(R.drawable.ic_chevron_updown, pal.sub, 22), lpm(dp(22), dp(22), 6, 0, 0, 0));
        mrow.setOnClickListener(v -> showMethodPicker(mrow));
        pressFx(mrow);
        how.addView(mrow, lpm(MATCH, WRAP, 0, 8, 0, 0));

        // trạng thái khả dụng của từng cách ghi file
        LinearLayout st = new LinearLayout(this);
        st.setOrientation(LinearLayout.VERTICAL);
        tvStUse = tv("", 13, pal.text, true);
        tvStRoot = tv("", 12, pal.sub, false);
        tvStShizuku = tv("", 12, pal.sub, false);
        st.addView(tvStUse);
        st.addView(tvStRoot, lpm(MATCH, WRAP, 0, 6, 0, 0));
        st.addView(tvStShizuku, lpm(MATCH, WRAP, 0, 2, 0, 0));
        LinearLayout btns = new LinearLayout(this);
        btns.setOrientation(LinearLayout.HORIZONTAL);
        btnCheck = tonalButton(t("Kiểm tra", "Check"), 36);
        btnCheck.setPadding(dp(16), 0, dp(16), 0);
        btnCheck.setOnClickListener(v -> runAccessCheck(!"shizuku".equals(method())));
        btnGrant = tonalButton(t("Cấp quyền Shizuku", "Grant Shizuku"), 36);
        btnGrant.setPadding(dp(16), 0, dp(16), 0);
        btnGrant.setVisibility(View.GONE);
        btnGrant.setOnClickListener(v -> {
            try { Shizuku.requestPermission(0); } catch (Throwable ignored) { }
        });
        btns.addView(btnCheck, lpm(WRAP, WRAP, 0, 0, 0, 0));
        btns.addView(btnGrant, lpm(WRAP, WRAP, 8, 0, 0, 0));
        st.addView(btns, lpm(MATCH, WRAP, 0, 10, 0, 0));
        how.addView(st, lpm(MATCH, WRAP, 0, 2, 0, 8));
        renderAccess();
        runAccessCheck(false);
        swAutoStop = new Switch(this);
        styleSwitch(swAutoStop);
        swAutoStop.setChecked(sp.getBoolean("autostop", true));
        swAutoStop.setOnCheckedChangeListener((v, on) -> sp.edit().putBoolean("autostop", on).apply());
        how.addView(divider());
        how.addView(switchRow(R.drawable.ic_close, t("Tự tắt game", "Auto-stop game"),
                t("Chỉ tắt khi game đang chạy", "Only stops the game if it is running"), swAutoStop));
        col[0].addView(how, lpm(MATCH, WRAP, 0, 0, 0, 0));

        // hotfix id
        LinearLayout hot = card();
        hot.addView(tv("Hotfix id", 15, pal.text, true));
        etHotfix = new EditText(this);
        etHotfix.setSingleLine(true);
        etHotfix.setHint(t("Ví dụ: hotfix_1234global", "Example: hotfix_1234global"));
        etHotfix.setTextColor(pal.text);
        etHotfix.setHintTextColor(pal.sub);
        etHotfix.setTextSize(14);
        etHotfix.setBackground(rounded(pal.field, 12, pal.stroke));
        etHotfix.setPadding(dp(14), dp(11), dp(14), dp(11));
        etHotfix.setText(sp.getString("hotfix", ""));
        etHotfix.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b2, int c2) { }
            @Override public void onTextChanged(CharSequence s, int a, int b2, int c2) { }
            @Override public void afterTextChanged(Editable e) { sp.edit().putString("hotfix", e.toString()).apply(); }
        });
        hot.addView(etHotfix, lpm(MATCH, WRAP, 0, 10, 0, 0));
        hot.addView(tv(t("Để trống = tự chọn. Chỉ nhập khi tự chọn bị sai (xem danh sách ở nút Xem hotfix).",
                        "Leave empty = automatic. Only fill this in if the automatic pick is wrong (see the list under View hotfix)."),
                11, pal.sub, false), lpm(WRAP, WRAP, 0, 8, 0, 0));
        col[0].addView(hot, lpm(MATCH, WRAP, 0, 12, 0, 0));

        // theme
        LinearLayout th = card();
        th.addView(tv(t("Giao diện", "Appearance"), 15, pal.text, true));
        th.addView(tv(t("Chế độ giao diện", "Theme mode"), 12, pal.sub, false), lpm(WRAP, WRAP, 0, 2, 0, 0));
        String cur = sp.getString("theme", "system");
        // ô chọn ngang: khối sáng trượt giữa 3 ô, xong mới đổi theme
        final String[] vals = {"light", "dark", "system"};
        final String[] names = {t("Sáng", "Light"), t("Tối", "Dark"), t("Theo hệ thống", "System default")};
        final View seg = segmented(names, "light".equals(cur) ? 0 : "dark".equals(cur) ? 1 : 2,
                Anim.on() ? 280 : 0, i -> switchTheme(vals[i]));
        th.addView(seg, lpm(MATCH, WRAP, 0, 10, 0, 0));
        // vị trí thanh điều hướng khi xoay ngang
        th.addView(tv(t("Thanh điều hướng khi xoay ngang", "Navigation bar in landscape"), 12, pal.sub, false),
                lpm(WRAP, WRAP, 0, 14, 0, 0));
        final String[] sides = {"right", "left", "bottom"};
        final String[] sideNames = {t("Phải", "Right"), t("Trái", "Left"), t("Dưới", "Bottom")};
        String curSide = sp.getString("navside", "right");
        th.addView(segmented(sideNames, "left".equals(curSide) ? 1 : "bottom".equals(curSide) ? 2 : 0,
                Anim.on() ? 280 : 0, i -> {
                    String oldSide = sp.getString("navside", "right");
                    String newSide = sides[i];
                    if (newSide.equals(oldSide)) return;
                    sp.edit().putString("navside", newSide).apply();
                    if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                        saveScrollPositions();
                        recreate();
                        overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                    }
                }), lpm(MATCH, WRAP, 0, 10, 0, 0));
        if (Glass.supported()) {               // liquid glass cần Android 13+ (AGSL)
            Switch swGlass = new Switch(this);
            styleSwitch(swGlass);
            swGlass.setChecked(Glass.enabled(sp));
            swGlass.setOnCheckedChangeListener((v, on) -> {
                sp.edit().putBoolean(Glass.PREF, on).putBoolean(Glass.PREF_BROKEN, false).apply();
                saveScrollPositions();
                recreate();
                overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
            });
            th.addView(divider(), lpm(MATCH, 1, 0, 6, 0, 0));
            th.addView(switchRow(R.drawable.ic_adjust, t("Kính lỏng (Liquid Glass)", "Liquid Glass"),
                    t("Thanh điều hướng khúc xạ như kính, có thể kéo thả. Có thể nặng trên máy yếu.",
                            "Refractive glass navigation bar you can drag. May be heavy on weaker devices."), swGlass));
        }
        col[1].addView(th, lpm(MATCH, WRAP, 0, 12, 0, 0));

        // language
        LinearLayout lang = card();
        lang.addView(tv(t("Ngôn ngữ", "Language"), 15, pal.text, true));
        LinearLayout lr = new LinearLayout(this);
        lr.setOrientation(LinearLayout.HORIZONTAL);
        lr.setGravity(Gravity.CENTER_VERTICAL);
        lr.addView(tv(t("Chọn ngôn ngữ ứng dụng", "Choose the app language"), 13, pal.sub, false), new LinearLayout.LayoutParams(0, WRAP, 1f));
        lr.addView(tv(en ? "English" : "Tiếng Việt", 14, pal.sub, false));
        lr.addView(icon(R.drawable.ic_chevron_updown, pal.sub, 22), lpm(dp(22), dp(22), 6, 0, 0, 0));
        lr.setOnClickListener(v -> showLanguagePicker(v));
        pressFx(lr);
        lang.addView(lr, lpm(MATCH, WRAP, 0, 10, 0, 0));
        col[0].addView(lang, lpm(MATCH, WRAP, 0, 12, 0, 0));

        // update check
        LinearLayout up = card();
        up.addView(tv(t("Cập nhật", "Updates"), 15, pal.text, true));
        LinearLayout ur = new LinearLayout(this);
        ur.setOrientation(LinearLayout.HORIZONTAL);
        ur.setGravity(Gravity.CENTER_VERTICAL);
        tvUpdateStatus = tv(t("Phiên bản hiện tại: ", "Current version: ") + Games.appVersion(this), 13, pal.sub, false);
        ur.addView(tvUpdateStatus, new LinearLayout.LayoutParams(0, WRAP, 1f));
        TextView btn = tv(t("Kiểm tra", "Check"), 13, pal.text, false);
        btn.setBackground(rounded(0x00000000, 16, pal.sub & 0x00FFFFFF | 0x66000000));
        btn.setPadding(dp(16), dp(7), dp(16), dp(7));
        btn.setOnClickListener(v -> checkUpdate(true, v));
        pressFx(btn);
        ur.addView(btn, lpm(WRAP, WRAP, 10, 0, 0, 0));
        up.addView(ur, lpm(MATCH, WRAP, 0, 10, 0, 0));
        col[1].addView(up, lpm(MATCH, WRAP, 0, 12, 0, 0));

        // credit
        LinearLayout cr = card();
        cr.addView(tv("Credit", 15, pal.text, true));
        TextView dc = tv("Discord: " + DISCORD, 14, pal.text, false);
        dc.setOnClickListener(v -> {
            ClipboardManager cm = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            if (cm != null) cm.setPrimaryClip(ClipData.newPlainText("discord", DISCORD));
            Toast.makeText(this, t("Đã sao chép ", "Copied ") + DISCORD, Toast.LENGTH_SHORT).show();
        });
        pressFx(dc);
        cr.addView(dc, lpm(WRAP, WRAP, 0, 10, 0, 0));
        cr.addView(tv(t("Chạm để sao chép", "Tap to copy"), 11, pal.sub, false), lpm(WRAP, WRAP, 0, 2, 0, 0));
        // Màu Discord (blurple). Biểu tượng: đặt file res/drawable/ic_discord.xml (logo chính thức) thì sẽ tự hiện.
        LinearLayout join = new LinearLayout(this);
        join.setOrientation(LinearLayout.HORIZONTAL);
        join.setGravity(Gravity.CENTER);
        join.setBackground(rounded(0xFF5865F2, 22, 0));
        join.setPadding(dp(8), dp(11), dp(8), dp(11));
        int dcIcon = getResources().getIdentifier("ic_discord", "drawable", getPackageName());
        if (dcIcon != 0) join.addView(icon(dcIcon, 0xFFFFFFFF, 20), lpm(dp(20), dp(20), 0, 0, 8, 0));
        join.addView(tv(t("Tham gia Discord", "Join Discord"), 14, 0xFFFFFFFF, true));
        join.setOnClickListener(v -> openUrl(DISCORD_URL));
        pressFx(join);
        cr.addView(join, lpm(MATCH, WRAP, 0, 14, 0, 0));
        col[1].addView(cr, lpm(MATCH, WRAP, 0, 12, 0, 0));

        TextView ver = tv("WWM Font Patch " + Games.appVersion(this), 12, pal.sub, false);
        ver.setGravity(Gravity.CENTER);
        c.addView(ver, lpm(MATCH, WRAP, 0, 18, 0, 6));

        tightTop(col);
        ScrollView sv = scroll(c);
        scrollViews[2] = sv;
        page.addView(sv, new FrameLayout.LayoutParams(MATCH, MATCH));
        page.addView(pill);
        return page;
    }

    @Override
    protected void onResume() {
        super.onResume();
        runAccessCheck(false);      // quay lại từ app Shizuku / hộp thoại cấp quyền: cập nhật lại (Shizuku nhanh, không hiện hộp thoại)
    }

    /** Shizuku luôn được kiểm tra; Root (chạy su, có thể hiện hộp thoại cấp quyền) chỉ khi người dùng bấm Kiểm tra. */
    private void runAccessCheck(boolean withRoot) {
        if (withRoot) {
            if (checkingRoot) return;
            checkingRoot = true;
            renderAccess();
        }
        final Context app = getApplicationContext();
        new Thread(() -> {
            final AccessCheck.Status sz = AccessCheck.shizuku(app);
            final AccessCheck.Status rt = withRoot ? AccessCheck.root() : null;
            runOnUiThread(() -> {
                stShizuku = sz;
                if (rt != null) stRoot = rt;
                if (withRoot) checkingRoot = false;
                renderAccess();
            });
        }).start();
    }

    private void renderAccess() {
        if (tvStRoot == null || tvStShizuku == null || tvStUse == null) return;
        String m = method();
        boolean auto = "auto".equals(m);
        boolean showRoot = auto || "root".equals(m), showSz = auto || "shizuku".equals(m);
        showAccessLine(tvStRoot, "Root", stRoot);
        showAccessLine(tvStShizuku, "Shizuku", stShizuku);
        tvStRoot.setVisibility(showRoot ? View.VISIBLE : View.GONE);
        tvStShizuku.setVisibility(showSz ? View.VISIBLE : View.GONE);
        tvStUse.setText(t("Sẽ dùng: ", "Will use: ") + AccessCheck.willUse(m, stRoot, stShizuku));
        tvStUse.setVisibility(auto ? View.VISIBLE : View.GONE);
        btnGrant.setVisibility(showSz && stShizuku != null && stShizuku.state == AccessCheck.State.DENIED ? View.VISIBLE : View.GONE);
        btnCheck.setText(checkingRoot ? t("Đang kiểm tra…", "Checking…") : t("Kiểm tra", "Check"));
        btnCheck.setEnabled(!checkingRoot);
    }

    private void showAccessLine(TextView tv, String name, AccessCheck.Status s) {
        String body = s == null ? t("Chưa kiểm tra", "Not checked") : s.text;
        tv.setText((s == null ? "○ " : "● ") + name + ": " + body);
        tv.setTextColor(s == null ? pal.sub : s.ready() ? pal.ok : Pal.SEAL_RED);
    }

    /** "auto" | "root" | "shizuku". Mặc định tự động. */
    private String method() {
        String s = sp.getString("method", "auto");
        return "root".equals(s) || "shizuku".equals(s) ? s : "auto";
    }

    private String methodName(String m) {
        return "root".equals(m) ? "Root" : "shizuku".equals(m) ? "Shizuku" : t("Tự động", "Auto");
    }

    private void showMethodPicker(View anchor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        final PopupWindow[] ref = new PopupWindow[1];
        final String[] ids = {"auto", "root", "shizuku"};
        final String[] subs = {t("Root nếu có, không thì Shizuku", "Root if available, else Shizuku"),
                stRoot != null ? stRoot.text : t("Cần quyền root", "Needs root access"),
                stShizuku != null ? stShizuku.text : t("Cần cài ứng dụng Shizuku", "Requires the Shizuku app")};
        for (int i = 0; i < 3; i++) {
            final String id = ids[i];
            boolean cur = id.equals(method());
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(tv(methodName(id), 16, cur ? pal.accent : pal.text, cur));
            t.addView(tv(subs[i], 11, pal.sub, false));
            LinearLayout row = mxRow(t, cur);
            row.setOnClickListener(x -> {
                sp.edit().putString("method", id).apply();
                tvMethodVal.setText(methodName(id));
                renderAccess();
                refreshHome();
                Anim.swap(tvMethodVal, dp(6), 0f);
                Anim.swap(tvInfoMethod, dp(6), 0f);
                Anim.dismiss(ref[0]);
            });
            c.addView(row);
        }
        ref[0] = showMxPopup(anchor, c, 240);
    }

    private View switchRow(int iconRes, String title, String sub, Switch sw) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        r.setPadding(0, dp(10), 0, dp(10));
        r.addView(icon(iconRes, pal.text, 22));
        LinearLayout t = new LinearLayout(this);
        t.setOrientation(LinearLayout.VERTICAL);
        t.addView(tv(title, 14, pal.text, false));
        if (sub != null) t.addView(tv(sub, 11, pal.sub, false));
        r.addView(t, lpm(0, WRAP, 12, 0, 8, 0));
        ((LinearLayout.LayoutParams) t.getLayoutParams()).weight = 1f;
        r.addView(sw, new LinearLayout.LayoutParams(WRAP, WRAP));
        return r;
    }

    private void styleSwitch(Switch s) {
        int[][] st = {{android.R.attr.state_checked}, {}};
        s.setThumbTintList(new ColorStateList(st, new int[]{pal.onPrimary, 0xFFFFFFFF}));   // bật: nút ngược màu với track (trắng/đen thuần)
        s.setTrackTintList(new ColorStateList(st, new int[]{pal.accent, pal.swOff}));
    }

    /** Đổi theme: chụp giao diện hiện tại, tạo lại Activity, rồi mờ dần ảnh chụp để lộ theme mới. */
    private void switchTheme(String v) {
        String oldPref = sp.getString("theme", "system");
        if (v.equals(oldPref)) return;
        boolean wasDark = isDark();
        sp.edit().putString("theme", v).apply();
        boolean nowDark = isDark();
        if (wasDark == nowDark) return;
        saveScrollPositions();
        themeShot = null;
        try {
            View c = findViewById(android.R.id.content);
            if (c != null && c.getWidth() > 0 && c.getHeight() > 0) {
                Bitmap bm = Bitmap.createBitmap(c.getWidth(), c.getHeight(), Bitmap.Config.ARGB_8888);
                c.draw(new Canvas(bm));
                themeShot = bm;
            }
        } catch (Throwable e) {
            themeShot = null;
        }
        recreate();
        overridePendingTransition(0, 0);
    }

    private void playThemeFade(final FrameLayout root) {
        final Bitmap bm = themeShot;
        themeShot = null;
        if (bm == null) return;
        final ImageView shot = new ImageView(this);
        shot.setImageBitmap(bm);
        shot.setScaleType(ImageView.ScaleType.FIT_XY);
        root.addView(shot, new FrameLayout.LayoutParams(MATCH, MATCH));
        if (!Anim.on()) { root.removeView(shot); return; }
        shot.animate().alpha(0f).setStartDelay(80).setDuration(560).setInterpolator(Anim.STD)
                .withEndAction(() -> {
                    root.removeView(shot);
                    shot.setImageBitmap(null);
                    bm.recycle();
                }).start();
    }

    private RadioButton radio(String label) {
        RadioButton rb = new RadioButton(this);
        rb.setId(View.generateViewId());
        rb.setText(label);
        rb.setTextColor(pal.text);
        rb.setTextSize(14);
        int[][] st = {{android.R.attr.state_checked}, {}};
        rb.setButtonTintList(new ColorStateList(st, new int[]{pal.accent, pal.sub}));
        return rb;
    }

    private void showLanguagePicker(View anchor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        final PopupWindow[] ref = new PopupWindow[1];
        String[] codes = {"vi", "en"};
        String[] names = {"Tiếng Việt", "English"};
        for (int i = 0; i < 2; i++) {
            final String code = codes[i];
            boolean cur = code.equals(en ? "en" : "vi");
            LinearLayout row = mxRow(tv(names[i], 16, cur ? pal.accent : pal.text, cur), cur);
            row.setOnClickListener(x -> {
                Anim.dismiss(ref[0]);
                if (code.equals(en ? "en" : "vi")) return;
                sp.edit().putString("lang", code).apply();
                saveScrollPositions();
                // Chờ hiệu ứng đóng hộp thoại chạy xong (~130ms) rồi mới tạo lại màn hình.
                // Gọi recreate() ngay sẽ hủy Activity khi animation làm mờ nền vẫn đang chạy trên cửa sổ đã bị gỡ -> app thoát.
                scene.postDelayed(() -> {
                    if (isFinishing() || isDestroyed()) return;
                    saveScrollPositions();
                    recreate();
                    overridePendingTransition(android.R.anim.fade_in, android.R.anim.fade_out);
                }, 300);
            });
            c.addView(row);
        }
        ref[0] = showMxPopup(anchor, c, 200);
    }

    // ---------------------------------------------------------------- update check (GitHub releases)

    private void checkUpdate(final boolean manual, final View anchor) {
        if (checking) return;
        checking = true;
        if (manual && tvUpdateStatus != null) tvUpdateStatus.setText(t("Đang kiểm tra…", "Checking…"));
        new Thread(() -> {
            String remote = null;
            String url = RELEASE_PAGE;
            String sha = null;
            String err = null;
            try {
                UpdateChecker.Release r = UpdateChecker.latest();
                remote = r.version;
                url = r.url != null ? r.url : RELEASE_PAGE;
                sha = r.sha256;
            } catch (Exception e) {
                Log.w(TAG, "update check failed", e);
                err = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String fr = remote;
            final String fu = url;
            final String fs = sha;
            final String fe = err;
            runOnUiThread(() -> {
                checking = false;
                if (isFinishing() || isDestroyed()) return;
                String cur = Games.appVersion(this);
                if (tvUpdateStatus != null) tvUpdateStatus.setText(t("Phiên bản hiện tại: ", "Current version: ") + cur);
                if (fe != null) {
                    if (manual) showUpdateResult(anchor, t("Không kiểm tra được", "Couldn't check"),
                            t("Không kết nối được tới GitHub. Hãy kiểm tra mạng rồi thử lại.\n", "Could not reach GitHub. Check your connection and try again.\n") + fe,
                            null, null);
                    return;
                }
                if (UpdateChecker.cmpVer(fr, cur) > 0) {
                    showUpdateResult(anchor, t("Có bản cập nhật mới", "Update available"),
                            t("Phiên bản mới: v", "New version: v") + fr + "\n" + t("Hiện tại: ", "Current: ") + cur,
                            t("Cập nhật", "Update"), () -> startUpdate(fu, fs));
                } else if (manual) {
                    showUpdateResult(anchor, t("Đã là bản mới nhất", "You're up to date"),
                            t("Bạn đang dùng ", "You are using ") + cur, null, null);
                }
            });
        }).start();
    }

    /** Tải APK trong nền rồi cài ngay trong app; file tạm tự xoá. */
    private void startUpdate(final String url, final String sha256) {
        // Không có APK hợp lệ hoặc không có SHA-256 để xác minh -> không tự tải/cài, mở trang release để người dùng tự quyết định.
        if (url == null || !UpdateGuard.isTrustedStartUrl(url) || sha256 == null) { openUrl(RELEASE_PAGE); return; }
        if (Build.VERSION.SDK_INT >= 26 && !getPackageManager().canRequestPackageInstalls()) {
            infoDialog(t("Cần cấp quyền cài đặt", "Install permission needed"),
                    t("Hãy bật \"Cho phép từ nguồn này\" cho WWM Font Patch, rồi quay lại và bấm Cập nhật lần nữa.",
                            "Turn on \"Allow from this source\" for WWM Font Patch, then come back and tap Update again."),
                    t("Mở cài đặt", "Open settings"), () -> startActivity(new Intent(
                            android.provider.Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:" + getPackageName()))));
            return;
        }
        showProgress(t("Đang tải bản cập nhật", "Downloading update"));
        if (progressStep != null) progressStep.setText("0%");
        new Thread(() -> {
            String err = null;
            try {
                java.io.File f = Updater.download(this, url, sha256, pc -> runOnUiThread(() -> {
                    if (progressStep != null) progressStep.setText(pc + "%");
                }));
                Updater.install(this, f);
            } catch (Exception e) {
                Log.w(TAG, "update failed", e);
                Updater.clean(this);
                err = e.getMessage() != null ? e.getMessage() : e.toString();
            }
            final String fe = err;
            runOnUiThread(() -> {
                dismissProgress();
                if (fe != null && !isFinishing() && !isDestroyed())
                    infoDialog(t("Cập nhật thất bại", "Update failed"),
                            t("Không tải hoặc cài được bản mới. Kiểm tra mạng và dung lượng trống rồi thử lại.\n", "Could not download or install the update. Check your connection and free space, then try again.\n") + fe,
                            t("Mở trang tải", "Open download page"), () -> openUrl(RELEASE_PAGE));
            });
        }).start();
    }

    private void openUrl(String u) {
        try {
            startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(u)));
        } catch (ActivityNotFoundException e) {
            Toast.makeText(this, t("Không mở được liên kết", "Can't open the link"), Toast.LENGTH_SHORT).show();
        }
    }



    // ---------------------------------------------------------------- navigation (floating pill)

    /** Giữ lớp mờ được vẽ lại liên tục trong ms mili giây (lúc đang có animation chuyển trang). */
    private void blurTick(int ms) {
        if (!hasBackdrop()) return;
        long until = SystemClock.uptimeMillis() + ms;
        if (until > blurUntil) blurUntil = until;
        scene.removeCallbacks(blurTick);
        scene.postOnAnimation(blurTick);
    }

    /** Có lớp nền mờ/kính phía sau thanh nav cần vẽ lại khi scene đổi. */
    private boolean hasBackdrop() { return backdrop != null || glass != null; }

    @Override
    void invalidateBackdrop() {
        if (backdrop != null) backdrop.invalidate();
        if (glass != null) glass.invalidateBackdrop();
    }

    /** Một hàng 3 nút nav. copy=true: bản sao toàn màu accent, không bắt sự kiện, dùng bên trong ô kính. */
    private LinearLayout makeNavRow(boolean copy) {
        LinearLayout r = new LinearLayout(this);
        r.setOrientation(rail ? LinearLayout.VERTICAL : LinearLayout.HORIZONTAL);
        int pd = rail ? RAIL_PAD : 6;
        r.setPadding(dp(pd), dp(pd), dp(pd), dp(pd));
        String[] labels = {"Patch", "Font", t("Nâng cao", "Advanced")};
        int[] icons = {R.drawable.ic_home, R.drawable.ic_font, R.drawable.ic_tune};
        for (int i = 0; i < 3; i++) {
            final int idx = i;
            LinearLayout item = new LinearLayout(this);
            item.setOrientation(LinearLayout.VERTICAL);
            item.setGravity(Gravity.CENTER);
            item.setPadding(dp(rail ? 2 : 8), dp(rail ? 4 : 8), dp(rail ? 2 : 8), dp(rail ? 4 : 8));
            ImageView ic = new ImageView(this);
            ic.setImageResource(icons[i]);
            item.addView(ic, new LinearLayout.LayoutParams(dp(rail ? 22 : 24), dp(rail ? 22 : 24)));
            TextView tx = tv(labels[i], rail ? 10 : 12, copy ? pal.accent : pal.sub, copy);
            tx.setSingleLine(true);
            item.addView(tx, lpm(WRAP, WRAP, 0, 2, 0, 0));
            if (copy) {
                ic.setColorFilter(pal.accent);
            } else {
                pressFx(item);
                item.setOnClickListener(v -> selectTab(idx));
                navItems[i] = item;
                navIcons[i] = ic;
                navLabels[i] = tx;
            }
            r.addView(item, rail
                    ? new LinearLayout.LayoutParams(dp(RAIL_ITEM_W), dp(RAIL_ITEM_H))
                    : new LinearLayout.LayoutParams(dp(NAV_ITEM_W), WRAP));
        }
        return r;
    }

    /** Thanh nav liquid glass: khúc xạ + tán sắc + kéo thả, xem GlassNav. Ném lỗi nếu không dựng được. */
    private View buildGlassNav() {
        navRow = makeNavRow(false);
        LinearLayout accentRow = makeNavRow(true);
        glass = new GlassNav(this, pal, scene, navRow, accentRow, 3, tab, rail, new GlassNav.Listener() {
            @Override public void onSelect(int index) { selectTab(index); }

            @Override public void onFail(Throwable e) { glassFailed(e); }
        });
        return glass;
    }

    /** Lỗi lúc vẽ liquid glass: ghi nhận để lần sau dùng thanh nav cũ, rồi tạo lại màn hình ngay. */
    private void glassFailed(Throwable e) {
        Log.w(TAG, "liquid glass disabled after error", e);
        sp.edit().putBoolean(Glass.PREF_BROKEN, true).apply();
        if (!isFinishing() && !isDestroyed()) {
            saveScrollPositions();
            recreate();
        }
    }

    private View buildNav() {
        if (Glass.enabled(sp)) {
            try {
                return buildGlassNav();
            } catch (Throwable e) {            // thiết bị không dựng được -> quay về thanh nav cũ
                Log.w(TAG, "liquid glass unavailable, using classic nav", e);
                glass = null;
            }
        }
        final boolean blur = Build.VERSION.SDK_INT >= 31;

        navHost = new PillHost(this, rail);
        navHost.setClipToOutline(true);
        navHost.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), Math.min(v.getWidth(), v.getHeight()) / 2f);   // viên thuốc
            }
        });

        if (blur) {
            backdrop = new Backdrop(this);
            float r = dp(20);
            backdrop.setRenderEffect(RenderEffect.createBlurEffect(r, r, Shader.TileMode.CLAMP));
            navHost.addView(backdrop, new FrameLayout.LayoutParams(MATCH, MATCH));
        }

        // lớp phủ màu + viền sáng mảnh; không có blur thì phủ đặc hơn để chữ vẫn dễ đọc
        int fill = blur ? pal.nav : ((pal.nav & 0x00FFFFFF) | 0xF2000000);
        View tint = new View(this);
        tint.setBackground(rounded(fill, 60, pal.stroke));
        navHost.addView(tint, new FrameLayout.LayoutParams(MATCH, MATCH));

        // ô chọn trượt qua lại, kích thước/vị trí đặt sau khi bố cục xong (placeIndicator)
        indicator = new View(this);
        indicator.setBackground(rounded(pal.dark ? 0x2EFFFFFF : 0x17000000, 40, 0));
        navHost.addView(indicator, new FrameLayout.LayoutParams(1, 1, Gravity.TOP | Gravity.START));

        navRow = makeNavRow(false);
        navHost.addView(navRow, new FrameLayout.LayoutParams(WRAP, WRAP));   // phải là con cuối (PillHost dựa vào đó)
        navHost.addOnLayoutChangeListener((v, l, t, r, b, ol, ot, orr, ob) -> placeIndicator());
        return navHost;
    }

    /** Vị trí ô chọn của mục i: theo X (thanh ngang) hoặc theo Y (thanh dọc). */
    private float indicatorPos(int i) {
        return rail ? navRow.getTop() + navItems[i].getTop() : navRow.getLeft() + navItems[i].getLeft();
    }

    private void moveIndicator(int idx, boolean animate) {
        float p = indicatorPos(idx);
        if (!rail) {
            Anim.slide(indicator, p, animate);
            return;
        }
        Anim.slideAxis(indicator, p, true, animate);
    }

    /** Khớp ô chọn với kích thước một mục nav; chỉ chạy lại khi kích thước thật sự đổi. */
    private void placeIndicator() {
        View it = navItems[tab];
        if (it.getWidth() == 0) return;
        FrameLayout.LayoutParams lp = (FrameLayout.LayoutParams) indicator.getLayoutParams();
        int w = it.getWidth();
        int h = it.getHeight();
        int top = rail ? 0 : navRow.getTop() + it.getTop();
        int left = rail ? navRow.getLeft() + it.getLeft() : 0;
        if (lp.width != w || lp.height != h || lp.topMargin != top || lp.leftMargin != left) {
            lp.width = w;
            lp.height = h;
            lp.topMargin = top;
            lp.leftMargin = left;
            indicator.setLayoutParams(lp);
            if (rail) indicator.setTranslationY(indicatorPos(tab));
            else indicator.setTranslationX(indicatorPos(tab));
        }
    }

    private void selectTab(int idx) { selectTab(idx, true); }

    private void selectTab(int idx, boolean animate) {
        final int prev = tab;
        tab = idx;
        final boolean anim = animate && prev != idx && Anim.on();
        final int dir = idx > prev ? 1 : -1;
        for (int i = 0; i < 3; i++) {
            final int j = i;
            boolean on = i == idx;
            if (!anim) {
                resetPage(i);
                pages[i].setVisibility(on ? View.VISIBLE : View.GONE);
            } else if (on) {
                Anim.pageIn(pages[i], contents[i], dir, dp(28));
            } else if (i == prev) {
                Anim.pageOut(pages[i], dir, dp(16), () -> {
                    if (tab == j) return;          // người dùng đã quay lại tab này
                    pages[j].setVisibility(View.GONE);
                    resetPage(j);
                });
            } else {
                resetPage(i);
                pages[i].setVisibility(View.GONE);
            }
            styleNav(i, on, anim);
        }
        if (glass != null) glass.select(idx, animate);
        else if (navItems[idx].getWidth() > 0) moveIndicator(idx, anim);
        if (anim) blurTick(600);
    }

    private void resetPage(int i) {
        Anim.reset(pages[i]);
        for (int k = 0; k < contents[i].getChildCount(); k++) Anim.reset(contents[i].getChildAt(k));
    }

    private void applyNavColor(int i, int c) {
        navColor[i] = c;
        navIcons[i].setColorFilter(c);
        navLabels[i].setTextColor(c);
    }

    private void styleNav(int i, boolean on, boolean animate) {
        final int target = on ? pal.accent : pal.sub;
        navLabels[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        if (navAnim[i] != null) navAnim[i].cancel();
        if (!animate) {
            applyNavColor(i, target);
            return;
        }
        final int fromColor = navColor[i];
        ValueAnimator va = ValueAnimator.ofFloat(0f, 1f);
        va.setDuration(220);
        va.setInterpolator(Anim.STD);
        va.addUpdateListener(u -> {
            float f = (float) u.getAnimatedValue();
            applyNavColor(i, (Integer) ARGB.evaluate(f, fromColor, target));
        });
        navAnim[i] = va;
        va.start();
        if (on) Anim.bounce(navIcons[i]);
    }

    // ---------------------------------------------------------------- games

    private void setupPackages() {
        pkgList = new ArrayList<>();
        for (String p : Games.PKGS) if (Games.version(this, p) != null) pkgList.add(p);
        if (pkgList.isEmpty()) for (String p : Games.PKGS) pkgList.add(p);
        String saved = sp.getString("pkg", "");
        pkg = pkgList.contains(saved) ? saved : pkgList.get(0);
    }

    private void showGamePicker(View anchor) {
        LinearLayout c = new LinearLayout(this);
        c.setOrientation(LinearLayout.VERTICAL);
        final PopupWindow[] ref = new PopupWindow[1];
        for (String p : pkgList) {
            final String pk = p;
            boolean cur = p.equals(pkg);
            LinearLayout t = new LinearLayout(this);
            t.setOrientation(LinearLayout.VERTICAL);
            t.addView(tv(Games.name(p), 16, cur ? pal.accent : pal.text, cur));
            String v = Games.version(this, p);
            t.addView(tv(v != null ? t("Đã cài ", "Installed ") + v : t("Chưa cài game", "Game not installed"), 11, pal.sub, false));
            LinearLayout row = mxRow(t, cur);
            row.setOnClickListener(x -> {
                pkg = pk;
                sp.edit().putString("pkg", pk).apply();
                refreshHome();
                for (View tvw : new View[]{ivGameIcon, tvGameRegion, tvGameName, tvGameSel, tvInfoVer, tvInfoDir}) Anim.swap(tvw, dp(6), 0f);
                refreshFonts(true);
                Anim.dismiss(ref[0]);
            });
            c.addView(row);
        }
        ref[0] = showMxPopup(anchor, c, 240);
    }

    // ---------------------------------------------------------------- dialogs

    // ---- Miuix dialog building blocks






    // ---- làm mờ nền phía sau popup / hộp thoại để người dùng tập trung vào popup



    @android.annotation.TargetApi(31)









    // ---------------------------------------------------------------- running work off the UI thread

    /** Trả về câu thay cho thông báo thành công mặc định (vd. "không có gì để gỡ"), hoặc null để dùng câu mặc định. */
    private interface Job { String run(Patcher p, Backend b, String pkg) throws Exception; }

    private void doInstall() {
        final String[] chosen = selFonts.clone();   // read on the UI thread
        final String hot = etHotfix.getText().toString();
        boolean any = false;
        for (String s : chosen) if (s != null) any = true;
        if (!any) {
            Toast.makeText(this, t("Chưa chọn font nào — hãy chọn ở tab Font", "No font selected — pick one in the Font tab"),
                    Toast.LENGTH_SHORT).show();
            selectTab(1);
            return;
        }
        task(t("Cài patch font", "Install font patch"),
                t("Đã cài patch font. Hãy mở game để dùng font mới.", "Font patch installed. Open the game to use the new font."),
                (p, bk, pk) -> { p.install(pk, bk, chosen, hot); return null; }, () -> markApplied(chosen));
    }

    private void task(String title, String okMsg, Job job, Runnable onOk) {
        final String pk = pkg;
        final String method = method();
        final boolean useRoot = "root".equals(method);
        final boolean autoStop = sp.getBoolean("autostop", true);
        synchronized (logBuf) { logBuf.setLength(0); }
        runningTitle = title;
        showProgress(title);
        new Thread(() -> {
            int state = 0;
            String msg = okMsg;
            try {
                Backend b = chooseBackend(method, pk);
                if (b == null) {
                    state = 2;
                    msg = lastLine();
                } else {
                    String note = job.run(new Patcher(this, this::log, autoStop), b, pk);
                    if (note != null) msg = note;
                }
            } catch (Throwable t) {
                Log.e(TAG, "task failed", t);
                log(this.t("LỖI: ", "ERROR: ") + t);
                state = 1;
                msg = t.getMessage() != null ? t.getMessage() : t.toString();
            }
            final int st = state;
            final String m = msg;
            runOnUiThread(() -> {
                runningTitle = null;
                // onOk chỉ ghi SharedPreferences (+ vẽ lại view của instance này) nên an toàn kể cả khi instance đã bị huỷ
                if (st == 0 && onOk != null) onOk.run();
                final Hints.Hint hint = st == 0 ? null : Hints.diagnose(m, useRoot, st == 2);
                final MainActivity ui = current;
                if (ui == null || ui.isFinishing() || ui.isDestroyed()) {
                    // không có cửa sổ nào để hiện kết quả (mở dialog trên Activity đã huỷ sẽ crash) -> giữ lại cho lần onCreate sau
                    pending = new Pending(title, st, m, hint);
                    return;
                }
                ui.dismissProgress();
                ui.refreshFonts(false);     // instance mới có thể đã đọc trạng thái "đang sử dụng" trước khi tác vụ xong
                ui.refreshHome();
                ui.showResult(title, st, m, hint);
            });
        }).start();
    }

    private String lastLine() {
        synchronized (logBuf) {
            String[] ls = logBuf.toString().trim().split("\n");
            return ls.length == 0 ? "" : ls[ls.length - 1].replaceFirst("^\\[\\d{2}:\\d{2}:\\d{2}\\] ", "");
        }
    }

    /** The chosen method (Root or Shizuku); the game's provider only as a fallback. Returns null (after logging why) when nothing works. */
    private Backend chooseBackend(String method, String pkg) {
        boolean tryRoot = !"shizuku".equals(method);
        boolean tryShizuku = !"root".equals(method);
        if (tryRoot) {
            RootBackend r = new RootBackend(this);
            if (r.available()) return r;
            log(t("Không dùng được root (su bị từ chối hoặc không có).", "Root is not available (su was denied or is missing)."));
        }
        if (tryShizuku) {
            ShizukuBackend s = new ShizukuBackend(this);
            String why = s.prepare();
            if (why == null) return s;
            log(why);
            if (s.permissionRequested) return null;   // wait for the user to answer the dialog
        }
        if (PatchModel.isChinese(pkg)) {
            log(t("Bản Trung Quốc không có đường provider — hãy chọn Root hoặc Shizuku ở tab Nâng cao và chắc chắn cách đó dùng được.",
                    "The Chinese version has no provider route — choose Root or Shizuku in the Advanced tab and make sure it works."));
            return null;
        }
        log(t("Dùng Provider của game (cần game đang chạy ngầm).", "Using the game's Provider (the game must be running in the background)."));
        return new ProviderBackend(this);
    }

    private void log(String s) {
        Log.i(TAG, s);
        final String stamp = new SimpleDateFormat("HH:mm:ss", Locale.US).format(new Date());
        synchronized (logBuf) { logBuf.append('[').append(stamp).append("] ").append(s).append('\n'); }
        runOnUiThread(() -> {
            final MainActivity ui = current;       // instance đang hiển thị, không phải instance đã khởi chạy tác vụ
            if (ui != null && ui.progressStep != null) {
                ui.progressStep.setText(s);
                Anim.swap(ui.progressStep, ui.dp(6), 0.35f);
            }
        });
    }
}
