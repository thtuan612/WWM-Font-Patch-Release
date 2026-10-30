package local.wwm.fontpatch;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RadialGradient;
import android.graphics.RectF;
import android.graphics.RecordingCanvas;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.util.Log;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.ViewOutlineProvider;
import android.widget.LinearLayout;

/**
 * Thanh nav liquid glass (Android 13+).
 *
 * Hỗ trợ 2 hướng:
 *  - vertical = false: thanh ngang nằm dưới màn hình (xoay dọc), ô chọn trượt/kéo theo trục X.
 *  - vertical = true : thanh dọc nằm cạnh trái/phải (xoay ngang), ô chọn trượt/kéo theo trục Y.
 *
 * Bố cục (từ dưới lên):
 *  1. PillView      nền kính: vẽ lại scene phía sau vào một RenderNode có hiệu ứng bão hòa + mờ + khúc xạ mép.
 *  2. row           hàng nút thật (icon + chữ, nhận click).
 *  3. accent        bản sao cùng bố cục nhưng toàn màu accent, ẩn; chỉ để IndicatorView vẽ vào ô chọn.
 *  4. IndicatorView ô chọn bằng kính: cùng nền kính của thanh + chữ accent, khúc xạ + tán sắc khi nhấn/kéo.
 *  5. RimView       bóng trong, viền sáng (specular) và viền mảnh, vẽ đè lên trên cùng.
 *
 * Ô chọn dùng lò xo (Spring) như DampedDragAnimation của bản gốc: nhấn thì phồng lên 78/56, kéo dọc theo trục
 * của thanh để đổi tab, bị bóp/giãn theo vận tốc. Nền kính trong ô chọn lấy đúng từ RenderNode của thanh.
 *
 * Chuyển thể từ Kyant0/AndroidLiquidGlass (Apache-2.0) qua KernelSU Manager (FloatingBottomBar.kt); xem Glass.java.
 */
@TargetApi(33)
final class GlassNav extends ViewGroup {

    interface Listener {
        /** Người dùng thả ô chọn ở tab khác; activity đổi trang rồi gọi lại select(). */
        void onSelect(int index);

        /** Vẽ lỗi: activity nên tắt glass và tạo lại màn hình. */
        void onFail(Throwable t);
    }

    private static final String TAG = "FontPatch";

    private final float dp;
    private final boolean vertical;  // true: thanh dọc (xoay ngang); false: thanh ngang
    private final int padX;          // phần ô chọn được phép tràn ra ngoài thanh khi phồng lên
    private final int padY;
    private final int slop;

    private final Pal pal;
    private final View scene;
    private final LinearLayout row;
    private final LinearLayout accent;
    private final int count;
    private final Listener listener;

    private final RenderNode pillNode = new RenderNode("glass-pill");
    /** Nền kính của thấu kính: rộng hơn thanh (gồm cả vùng nhô ra), mờ nhưng không khúc xạ mép thanh. */
    private final RenderNode lensNode = new RenderNode("glass-lens");
    private final PillView pill;
    private final IndicatorView ind;
    private final RimView rim;

    // lò xo: (giá trị đầu, damping ratio, stiffness, ngưỡng dừng) như bản gốc
    private final Spring index = new Spring(0f, 1f, 1000f, 0.001f);
    private final Spring press = new Spring(0f, 1f, 1000f, 0.001f);
    private final Spring sx = new Spring(1f, 0.6f, 250f, 0.001f);
    private final Spring sy = new Spring(1f, 0.7f, 250f, 0.001f);

    private boolean ready;
    private boolean running;
    private boolean pendingRelease;
    private boolean touching;
    private boolean dragging;
    private boolean failed;
    private int selected;
    private int builtW;
    private int builtH;
    private long lastNs;
    private float downP;   // toạ độ chạm xuống theo trục của thanh (X nếu ngang, Y nếu dọc)
    private float lastP;

    // hình học của hàng nút (px, toạ độ của GlassNav). "along" = dọc theo trục của thanh, "across" = vuông góc.
    private float p0;      // điểm bắt đầu của mục đầu tiên theo trục của thanh
    private float len;     // độ dài một mục theo trục của thanh
    private float c0;      // điểm bắt đầu của mục theo trục vuông góc
    private float cross;   // kích thước một mục theo trục vuông góc

    // hình dạng ô chọn hiện tại (toạ độ của GlassNav)
    private final RectF rect = new RectF();
    private final Path thumb = new Path();    // cùng hình ô chọn, để khoét hàng nút thật bên dưới
    private final Path barShape = new Path();     // viên thuốc của cả thanh (toạ độ GlassNav): vùng tô kính
    private final Path barBorder = new Path();    // viền của thanh, thụt vào nửa nét
    private float radius;
    /**
     * Bán kính bo của cả thanh: bán kính ô chọn lúc nghỉ + khoảng hở từ ô tới mép thanh, để cung của thanh và cung của ô
     * đồng tâm. Thanh ngang (ô dài hơn cao) vẫn ra đúng hình viên thuốc; thanh dọc (ô thấp hơn rộng) bo nhỏ hơn chút
     * thay vì nửa bề dày, nếu không khe giữa hai cung phình ra ở hai góc cuối.
     */
    private float barR = -1f;

    private float barRadius(float w, float h) {
        float cap = Math.min(w, h) / 2f;
        return barR > 0f ? Math.min(cap, barR) : cap;
    }

    private final Runnable tick = new Runnable() {
        @Override public void run() { step(); }
    };

    /** Chạy sau khi click của mục nav (được post ở lúc nhả tay) đã xử lý xong, rồi mới đưa ô chọn về đúng tab. */
    private final Runnable settle = new Runnable() {
        @Override public void run() {
            if (touching) return;
            index.target = selected;
            release();
            kick();
        }
    };

    GlassNav(Context c, Pal pal, View scene, LinearLayout row, LinearLayout accent, int count, int initial,
             boolean vertical, Listener listener) {
        super(c);
        this.pal = pal;
        this.scene = scene;
        this.row = row;
        this.accent = accent;
        this.count = count;
        this.listener = listener;
        this.selected = initial;
        this.vertical = vertical;
        dp = c.getResources().getDisplayMetrics().density;
        // ô chọn tràn nhiều theo trục của thanh, ít theo trục vuông góc
        // chừa đủ chỗ cho thấu kính nhô ra ngoài thanh (phóng 1.4x dọc trục, ~1.2x ngang trục) cộng độ nảy của lò xo,
        // viền sáng và vùng làm mờ của bóng trong; thiếu chỗ thì thấu kính bị cắt phẳng ở mép view
        padX = Math.round((vertical ? 24f : 48f) * dp);
        padY = Math.round((vertical ? 48f : 24f) * dp);
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        index.snap(initial);

        setClipChildren(false);
        setClipToPadding(false);
        // bóng đổ mềm dưới thanh: thanh trông như nổi lên khỏi nội dung. alpha = 1 để bóng chỉ hiện bên ngoài viên thuốc.
        setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), barRadius(v.getWidth(), v.getHeight()));
                o.setAlpha(1f);
            }
        });
        setElevation(14f * dp);
        setOutlineAmbientShadowColor(0x66000000);
        setOutlineSpotShadowColor(0x99000000);

        pill = new PillView(c);
        ind = new IndicatorView(c);
        rim = new RimView(c);
        accent.setClipChildren(false);          // mục phóng 1.2x khi nhấn không được bị cắt ở mép hàng
        accent.setClipToPadding(false);
        accent.setVisibility(INVISIBLE);
        accent.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        ind.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        rim.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        addView(pill);
        addView(row);
        addView(accent);
        addView(ind);
        addView(rim);
    }

    // ---------------------------------------------------------------- API cho activity

    /** Đặt tab đang chọn. animate=false thì nhảy thẳng tới, không hiệu ứng. */
    void select(int idx, boolean animate) {
        selected = idx;
        if (!ready || !animate || !Anim.on()) {
            index.snap(idx);
            press.snap(0f);
            sx.snap(1f);
            sy.snap(1f);
            pendingRelease = false;
            if (ready) {
                update();
                invalidateAll();
            }
            return;
        }
        index.target = idx;
        if (!touching) {            // bấm bằng nút/phím: tự nhấn rồi nhả; còn đang chạm thì để thao tác tay điều khiển
            press();
            release();
        }
        kick();
    }

    /** Scene phía sau đổi (cuộn, hiệu ứng chuyển trang, đổi chữ...): vẽ lại nền kính. */
    void invalidateBackdrop() {
        pill.invalidate();
        ind.invalidate();
    }

    // ---------------------------------------------------------------- bố cục

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w;
        int h;
        // kích thước do hàng nút quyết định cả hai chiều (mục nav có bề rộng cố định) nên thanh ngang cũng co gọn;
        // chỉ bị chặn bởi chỗ cha cho phép
        row.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        w = row.getMeasuredWidth();
        h = row.getMeasuredHeight();
        int avail = MeasureSpec.getSize(wSpec);
        if (!vertical && MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED && avail > 0) w = Math.min(w, avail);
        int exactW = MeasureSpec.makeMeasureSpec(w, MeasureSpec.EXACTLY);
        int exactH = MeasureSpec.makeMeasureSpec(h, MeasureSpec.EXACTLY);
        row.measure(exactW, exactH);
        accent.measure(exactW, exactH);
        pill.measure(exactW, exactH);
        int bigW = MeasureSpec.makeMeasureSpec(w + 2 * padX, MeasureSpec.EXACTLY);
        int bigH = MeasureSpec.makeMeasureSpec(h + 2 * padY, MeasureSpec.EXACTLY);
        ind.measure(bigW, bigH);
        rim.measure(bigW, bigH);
        setMeasuredDimension(w, h);
    }

    @Override
    protected void onLayout(boolean changed, int l, int t, int r, int b) {
        int w = r - l;
        int h = b - t;
        row.layout(0, 0, w, h);
        accent.layout(0, 0, w, h);
        pill.layout(0, 0, w, h);
        ind.layout(-padX, -padY, w + padX, h + padY);
        rim.layout(-padX, -padY, w + padX, h + padY);

        View first = count > 0 ? row.getChildAt(0) : null;
        if (first == null || w <= 0 || h <= 0 || first.getWidth() <= 0 || first.getHeight() <= 0) {
            ready = false;
            return;
        }
        if (vertical) {
            p0 = row.getTop() + first.getTop();
            len = first.getHeight();
            c0 = row.getLeft() + first.getLeft();
            cross = first.getWidth();
        } else {
            p0 = row.getLeft() + first.getLeft();
            len = first.getWidth();
            c0 = row.getTop() + first.getTop();
            cross = first.getHeight();
        }
        if (w != builtW || h != builtH) buildPill(w, h);
        ready = true;
        update();
        invalidateAll();
    }

    private void buildPill(int w, int h) {
        // len/cross/c0 đã đo xong ở onLayout: c0 = khoảng hở từ mép thanh tới ô chọn theo trục vuông góc
        barR = Math.min(Math.min(w, h) / 2f, Math.min(len, cross) / 2f + Math.max(0f, c0));
        invalidateOutline();
        pill.invalidateOutline();
        RenderEffect lens = null;
        try {
            RuntimeShader s = Glass.newLens(false);
            float cr = barR;
            lens = Glass.lens(s, false, w, h, 0f, 0f, cr,
                    Glass.BAR_REFRACTION_DP * dp, Glass.BAR_REFRACTION_DP * dp, 0f, 0f, 1f);
        } catch (RuntimeException e) {
            Log.w(TAG, "glass: lens shader unavailable, blur only", e);
        }
        float br = barR;
        float bin = Math.max(1f, 1.5f * dp) / 2f;
        barShape.reset();
        barShape.addRoundRect(0f, 0f, w, h, br, br, Path.Direction.CW);
        barBorder.reset();
        barBorder.addRoundRect(bin, bin, w - bin, h - bin, br - bin, br - bin, Path.Direction.CW);
        pillNode.setPosition(0, 0, w, h);
        pillNode.setRenderEffect(Glass.backdrop(Glass.BLUR_DP * dp, lens));
        lensNode.setPosition(0, 0, w + 2 * padX, h + 2 * padY);
        lensNode.setRenderEffect(Glass.backdrop(Glass.BLUR_DP * dp, null));
        pill.rebuildGlow(w, h);
        builtW = w;
        builtH = h;
    }

    // ---------------------------------------------------------------- chuyển động

    private void press() {
        if (!Anim.on()) return;
        pendingRelease = false;
        press.target = 1f;
        sx.target = Glass.LENS_ALONG;
        sy.target = crossTarget();
    }

    /** Hệ số phóng theo trục vuông góc để thấu kính cao hơn cả thanh (nhô lên/xuống), tính từ bề dày thanh thật. */
    private float crossTarget() {
        float bar = vertical ? getWidth() : getHeight();
        return cross > 0f && bar > 0f ? Math.max(1f, bar * Glass.LENS_OVERSHOOT / cross) : Glass.PRESSED_SCALE;
    }

    private void release() {
        if (!Anim.on()) return;
        pendingRelease = true;
    }

    private void kick() {
        if (running || failed || !isAttachedToWindow()) return;
        running = true;
        lastNs = System.nanoTime();
        postOnAnimation(tick);
    }

    private void step() {
        if (!isAttachedToWindow() || failed) {
            running = false;
            return;
        }
        try {
            long now = System.nanoTime();
            float dt = Math.min(0.032f, (now - lastNs) / 1e9f);
            lastNs = now;

            boolean moving = index.step(dt);
            moving |= press.step(dt);
            moving |= sx.step(dt);
            moving |= sy.step(dt);

            // nhả tay: đợi ô chọn gần tới nơi (2.5% quãng đường) rồi mới thu nhỏ lại, như bản gốc
            if (pendingRelease && Math.abs(index.value - index.target) < 0.025f * Math.max(1, count - 1)) {
                pendingRelease = false;
                press.target = 0f;
                sx.target = 1f;
                sy.target = 1f;
                moving = true;
            }
            update();
            invalidateAll();
            if (moving || pendingRelease) {
                postOnAnimation(tick);
            } else {
                running = false;
            }
        } catch (RuntimeException e) {
            running = false;
            fail(e);
        }
    }

    /** Tính lại hình dạng ô chọn từ trạng thái các lò xo. */
    private void update() {
        if (!ready) return;
        float along = p0 + (index.value + 0.5f) * len;     // tâm ô chọn theo trục của thanh
        float across = c0 + cross / 2f;                    // tâm theo trục vuông góc
        // bóp/giãn theo vận tốc (đơn vị: phần quãng đường/giây, chia 10 như bản gốc); dùng độ lớn để hai chiều đối xứng
        float v = Math.abs(index.velocity) / Math.max(1, count - 1) / 10f;
        float wa = sx.value / (1f - clamp(v * 0.75f, 0f, 0.2f));   // hệ số theo trục của thanh
        float wc = sy.value * (1f - clamp(v * 0.25f, 0f, 0.2f));   // hệ số theo trục vuông góc
        float ha = len * wa / 2f;
        float hc = cross * wc / 2f;
        if (vertical) {
            rect.set(across - hc, along - ha, across + hc, along + ha);
        } else {
            rect.set(along - ha, across - hc, along + ha, across + hc);
        }
        radius = Math.min(ha, hc);
        thumb.reset();
        thumb.addRoundRect(rect, radius, radius, Path.Direction.CW);

        // chữ/icon trong ô được shader phóng đại (Glass.LENS_MAGNIFY) cùng với phần bẻ cong ở mép, không phóng tay từng view nữa
        ind.applyLens();
    }

    /**
     * Hàng nút thật bị khoét đúng hình ô chọn: trong ô đã có bản sao chữ/icon màu accent (có khúc xạ),
     * nếu không khoét thì chữ thật nằm dưới sẽ lộ ra và thành hai lớp chữ chồng lệch nhau.
     */
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == row && ready && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.clipOutPath(thumb);
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    private void invalidateAll() {
        invalidate();       // vẽ lại hàng nút với vết khoét mới
        pill.invalidate();
        ind.invalidate();
        rim.invalidate();
    }

    private void fail(Throwable t) {
        if (failed) return;
        failed = true;
        removeCallbacks(tick);
        post(() -> listener.onFail(t));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    private boolean dark() { return pal.dark; }

    // ---------------------------------------------------------------- cảm ứng

    /** Toạ độ chạm theo trục của thanh. */
    private float pos(MotionEvent e) {
        return vertical ? e.getY() : e.getX();
    }

    private int indexAt(float p) {
        if (len <= 0f) return selected;
        int i = (int) Math.floor((p - p0) / len);
        return Math.max(0, Math.min(count - 1, i));
    }

    @Override
    public boolean dispatchTouchEvent(MotionEvent e) {
        int a = e.getActionMasked();
        if (a == MotionEvent.ACTION_DOWN) beginTouch(pos(e));
        boolean handled = super.dispatchTouchEvent(e);
        if (a == MotionEvent.ACTION_UP || a == MotionEvent.ACTION_CANCEL) endTouch();
        return handled;
    }

    private void beginTouch(float p) {
        if (!ready) return;
        touching = true;
        dragging = false;
        downP = p;
        lastP = p;
        removeCallbacks(settle);
        index.target = indexAt(p);      // ô chọn chạy ngay tới tab đang chạm và phồng lên
        tickIdx = selected;
        tickIfChanged();
        press();
        kick();
    }

    /** Phản hồi rung nhẹ mỗi khi ô kính đi qua sang một mục khác (chạm hoặc kéo), như thanh chọn của iOS. */
    private int tickIdx = -1;

    private void tickIfChanged() {
        int i = Math.round(index.target);
        if (i == tickIdx) return;
        tickIdx = i;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void endTouch() {
        touching = false;
        dragging = false;
        // click của mục nav được post lúc nhả tay; post sau nó để selected đã được cập nhật
        post(settle);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        if (e.getActionMasked() == MotionEvent.ACTION_MOVE && touching && !dragging
                && Math.abs(pos(e) - downP) > slop) {
            dragging = true;
            lastP = pos(e);
            return true;            // từ giờ là thao tác kéo, các mục nav nhận CANCEL
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_MOVE:
                if (touching && !dragging && Math.abs(pos(e) - downP) > slop) {
                    dragging = true;
                    lastP = pos(e);
                }
                if (dragging && len > 0f) {
                    float d = pos(e) - lastP;
                    lastP = pos(e);
                    index.target = clamp(index.target + d / len, 0f, count - 1);
                    tickIfChanged();
                    kick();
                }
                break;
            case MotionEvent.ACTION_UP:
                if (dragging) {
                    int t = Math.max(0, Math.min(count - 1, Math.round(index.target)));
                    if (t != selected) listener.onSelect(t);
                }
                break;
            default:
                break;
        }
        return true;                // nuốt cả chạm vào khoảng trống giữa các nút, không để lọt xuống trang phía sau
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        removeCallbacks(settle);
        running = false;
        super.onDetachedFromWindow();
    }

    // ================================================================ các lớp vẽ

    /** Nền kính của cả thanh. */
    private final class PillView extends View {
        private final Paint tint = new Paint();
        private final Paint flat = new Paint();
        private final Paint glow = new Paint();
        private final Matrix glowM = new Matrix();
        private final int[] a = new int[2];
        private final int[] b = new int[2];
        private RadialGradient glowShader;
        private final Paint sheen = new Paint();

        PillView(Context c) {
            super(c);
            setClipToOutline(true);
            setOutlineProvider(new ViewOutlineProvider() {
                @Override public void getOutline(View v, Outline o) {
                    // viên thuốc: bo theo cạnh ngắn (thanh ngang -> chiều cao, thanh dọc -> chiều rộng)
                    o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), barRadius(v.getWidth(), v.getHeight()));
                }
            });
            tint.setColor((pal.nav & 0x00FFFFFF) | (Glass.TINT_ALPHA << 24));
            // ánh sáng theo ngón tay: cộng màu (BlendMode.Plus của bản gốc)
            flat.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
            flat.setColor(Color.WHITE);
            glow.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.ADD));
        }

        void rebuildGlow(int w, int h) {
            float r = Math.min(w, h) * 1.2f;
            int on = Color.argb(Math.round(255 * 0.12f), 255, 255, 255);
            glowShader = new RadialGradient(0f, 0f, r, new int[]{on, on, on & 0x00FFFFFF},
                    new float[]{0f, 0.5f, 1f}, Shader.TileMode.CLAMP);
            // ánh sáng từ trên xuống (thanh ngang) hoặc từ trái sang (thanh dọc): mặt kính hơi sáng ở mép đón sáng
            int top = Color.argb(Math.round(255 * (dark() ? 0.10f : 0.35f)), 255, 255, 255);
            int none = Color.argb(0, 255, 255, 255);
            sheen.setShader(vertical
                    ? new LinearGradient(0f, 0f, w * 0.7f, 0f, top, none, Shader.TileMode.CLAMP)
                    : new LinearGradient(0f, 0f, 0f, h * 0.7f, top, none, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onDraw(Canvas canvas) {
            if (!ready || !canvas.isHardwareAccelerated()) return;
            try {
                int w = getWidth();
                int h = getHeight();
                // ghi lại đúng phần scene nằm sau thanh vào node; hiệu ứng (mờ + khúc xạ) gắn trên node
                RecordingCanvas rc = pillNode.beginRecording(w, h);
                try {
                    getLocationInWindow(a);
                    scene.getLocationInWindow(b);
                    rc.translate(b[0] - a[0], b[1] - a[1]);
                    scene.draw(rc);
                } finally {
                    pillNode.endRecording();
                }
                canvas.drawRenderNode(pillNode);
                canvas.drawRect(0f, 0f, w, h, tint);
                canvas.drawRect(0f, 0f, w, h, sheen);

                float p = press.value;
                if (p > 0.002f && glowShader != null) {
                    flat.setAlpha(Math.round(255 * 0.06f * p));
                    canvas.drawRect(0f, 0f, w, h, flat);
                    float along = p0 + (index.value + 0.5f) * len;
                    if (vertical) glowM.setTranslate(w / 2f, along);
                    else glowM.setTranslate(along, h / 2f);
                    glowShader.setLocalMatrix(glowM);
                    glow.setShader(glowShader);
                    glow.setAlpha(Math.round(255 * p));
                    canvas.drawRect(0f, 0f, w, h, glow);
                }
            } catch (RuntimeException e) {
                fail(e);
            }
        }
    }

    /** Ô chọn bằng kính. Toạ độ vẽ là toạ độ GlassNav cộng (padX, padY) vì view này rộng hơn thanh. */
    private final class IndicatorView extends View {
        private final Paint tint = new Paint();
        private final Paint surface = new Paint();
        private final Path clip = new Path();
        private final int[] a = new int[2];
        private final int[] b = new int[2];
        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GlassFx fx = new GlassFx(dp);
        private RuntimeShader lensShader;
        private boolean lensOn;

        IndicatorView(Context c) {
            super(c);
            tint.setColor((pal.nav & 0x00FFFFFF) | (Glass.TINT_ALPHA << 24));
            border.setStyle(Paint.Style.STROKE);
            border.setStrokeWidth(Math.max(1f, 1.5f * dp));
            border.setColor(pal.dark ? 0x59FFFFFF : 0x40000000);
            try {
                lensShader = Glass.newLens(true);
            } catch (RuntimeException e) {
                Log.w(TAG, "glass: indicator lens unavailable", e);
            }
        }

        /** Gọi từ step()/update(), không gọi trong onDraw (setRenderEffect làm view invalidate). */
        void applyLens() {
            float p = press.value;
            if (lensShader == null || p <= 0.002f) {
                if (lensOn) {
                    setRenderEffect(null);
                    lensOn = false;
                }
                return;
            }
            try {
                setRenderEffect(Glass.lens(lensShader, true, rect.width(), rect.height(),
                        rect.left + padX, rect.top + padY, radius,
                        Glass.PILL_REFRACTION_HEIGHT_DP * dp * p, Glass.PILL_REFRACTION_AMOUNT_DP * dp * p,
                        1f, Glass.PILL_CHROMA, 1f + Glass.LENS_MAGNIFY * p));
                lensOn = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "glass: indicator lens failed, disabled", e);
                lensShader = null;
                setRenderEffect(null);
                lensOn = false;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!ready || !c.isHardwareAccelerated()) return;
            try {
                float p = press.value;
                clip.reset();
                clip.addRoundRect(rect.left + padX, rect.top + padY, rect.right + padX, rect.bottom + padY,
                        radius, radius, Path.Direction.CW);
                int w = GlassNav.this.getWidth();
                int h = GlassNav.this.getHeight();
                // nền kính của thấu kính: ghi lại scene phía sau cả phần nhô ra ngoài thanh (node của thanh chỉ rộng bằng thanh)
                RecordingCanvas rc = lensNode.beginRecording(w + 2 * padX, h + 2 * padY);
                try {
                    GlassNav.this.getLocationInWindow(a);
                    scene.getLocationInWindow(b);
                    rc.translate(b[0] - a[0] + padX, b[1] - a[1] + padY);
                    scene.draw(rc);
                } finally {
                    lensNode.endRecording();
                }
                c.save();
                c.clipPath(clip);
                c.drawRenderNode(lensNode);                    // toạ độ view (đã gồm lề padX/padY)
                c.translate(padX, padY);                       // từ đây là toạ độ GlassNav
                // kính + viền của thanh vẽ NGAY TRONG ô, trước hiệu ứng khúc xạ: viền thanh đi qua ô sẽ bị bẻ cong theo thấu kính,
                // còn phần ô nhô ra ngoài thanh chỉ thấy scene (không có kính nền) nên mép thanh hiện rõ qua thấu kính
                c.drawPath(barShape, tint);
                c.drawPath(barShape, pill.sheen);
                c.drawPath(barBorder, border);
                accent.draw(c);                                // icon + chữ màu accent
                // lớp bề mặt: nhạt khi nghỉ, gần như trong suốt khi đang nhấn
                surface.setColor(pal.dark ? Color.WHITE : Color.BLACK);
                surface.setAlpha(Math.round(255 * 0.1f * (1f - p)));
                c.drawRect(rect, surface);
                surface.setColor(pal.dark ? Color.WHITE : Color.BLACK);
                surface.setAlpha(Math.round(255 * (pal.dark ? 0.07f : 0.03f) * p));   // nhấn: kính sáng nhẹ lên, không tối sầm
                c.drawRect(rect, surface);
                // phản chiếu xanh: có sẵn mờ khi nghỉ, đậm dần khi nhấn
                fx.reflection(c, rect, (pal.dark ? 0.03f : 0.02f) + (pal.dark ? 0.09f : 0.06f) * p);
                c.restore();
            } catch (RuntimeException e) {
                fail(e);
            }
        }
    }

    /** Viền mảnh, viền sáng và bóng trong; không bị khúc xạ vì nằm ngoài IndicatorView. */
    private final class RimView extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GlassFx fx = new GlassFx(dp);
        private final Path barPath = new Path();
        private final Path shape = new Path();
        private final Path shapeStroke = new Path();
        // ánh sáng chính từ trên-trái (thanh) / bên phải (ô chọn), ánh sáng phụ yếu hơn từ dưới
        private final SweepGradient barGrad = GlassFx.tintedLobes(225f, 0xFFD6E0FF, 45f, 0xFFD6E0FF, 0.5f);
        // sáng xanh từ trên-trái, phản chiếu phụ cùng tông xanh ở dưới-phải
        private final SweepGradient indGrad = GlassFx.tintedLobes(225f, 0xFF86A4FF, 45f, 0xFF86A4FF, 0.85f);

        RimView(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(fx.strokeWidth());
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!ready || !c.isHardwareAccelerated()) return;
            try {
                float sw = fx.strokeWidth();

                // viền thanh
                int w = getWidth() - 2 * padX;
                int h = getHeight() - 2 * padY;
                float in = sw / 2f;
                float r = barRadius(w, h) - sw / 2f;
                barPath.reset();
                barPath.addRoundRect(padX + in, padY + in, padX + w - in, padY + h - in, r, r, Path.Direction.CW);
                stroke.setColor(pal.stroke);
                // viền thanh nằm dưới thấu kính: khoét đúng hình ô, phần trong ô do chính ô vẽ lại (đã khúc xạ)
                c.save();
                c.translate(padX, padY);
                c.clipOutPath(thumb);
                c.translate(-padX, -padY);
                c.drawPath(barPath, stroke);
                fx.specular(c, barPath, barGrad, padX + w / 2f, padY + h / 2f, 0.55f);
                c.restore();

                // ô chọn: chỉ hiện khi đang nhấn
                float p = press.value;
                if (p <= 0.002f) return;
                float l = rect.left + padX;
                float t = rect.top + padY;
                float rr = rect.right + padX;
                float bb = rect.bottom + padY;
                shape.reset();
                shape.addRoundRect(l, t, rr, bb, radius, radius, Path.Direction.CW);
                fx.innerShadow(c, getWidth(), getHeight(), shape, p);
                float ri = Math.max(0f, radius - in);
                shapeStroke.reset();
                shapeStroke.addRoundRect(l + in, t + in, rr - in, bb - in, ri, ri, Path.Direction.CW);
                fx.specular(c, shapeStroke, indGrad, (l + rr) / 2f, (t + bb) / 2f, p);
            } catch (RuntimeException e) {
                fail(e);
            }
        }
    }
}
