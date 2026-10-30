package local.wwm.fontpatch;

import android.annotation.TargetApi;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.SweepGradient;
import android.graphics.drawable.Drawable;
import android.graphics.Typeface;
import android.util.Log;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.function.Consumer;
import java.util.function.IntConsumer;

/**
 * Thanh chọn ngang liquid glass (Android 13+), thay cho segmented() thường: dùng cho chọn FPS và chọn giao diện.
 *
 * Bố cục (từ dưới lên): nền thanh (setBackground) → cells (chữ thật, bị khoét đúng chỗ ô kính) →
 * accent (bản sao chữ màu accent, ẩn, chỉ để vẽ vào ô kính) → IndicatorView (ô kính) → RimView (viền sáng, bóng trong).
 *
 * Ô kính chạy bằng cùng bộ lò xo và cùng cơ chế render với GlassNav: chạm thì phồng lên và nhô ra ngoài thanh,
 * kéo ngang để đổi ô, bị bóp/giãn theo vận tốc, khi nhấn có khúc xạ + tán sắc + phản chiếu xanh tím.
 * Nền kính của ô (lensNode) ghi lại thẻ chứa thanh và nền thanh, nên mép thanh/mép thẻ đi qua ô cũng bị bẻ cong
 * theo thấu kính thay vì lộ nguyên bên dưới.
 *
 * Chuyển thể từ Kyant0/AndroidLiquidGlass (Apache-2.0); xem Glass.java.
 */
@TargetApi(33)
final class GlassSeg extends ViewGroup {
    private static final String TAG = "FontPatch";

    private final float dp;
    private final int pad;           // lề trong của thanh (dp)
    private final int cellH;         // chiều cao ô (dp)
    private final int padX;          // phần ô kính được tràn ra ngoài thanh khi phồng lên
    private final int padY;
    private final int slop;

    private final Pal pal;
    private final int n;
    private final int delayMs;
    private final IntConsumer onPick;
    private final Consumer<Throwable> onFail;

    private final LinearLayout cells;
    private final LinearLayout accent;
    private final TextView[] labels;
    private final IndicatorView ind;
    private final RimView rim;

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
    private long lastNs;
    private float downX;
    private float lastX;

    // hình học (px, toạ độ của GlassSeg)
    private float x0;
    private float cw;
    private float itemTop;
    private float ih;

    /** Nền kính của ô: rộng hơn thanh (gồm cả phần nhô ra), ghi lại thẻ chứa + nền thanh rồi làm mờ nhẹ. */
    private final RenderNode lensNode = new RenderNode("glass-seg-lens");
    private int builtW;
    private int builtH;

    private final RectF rect = new RectF();   // ô kính hiện tại
    private final Path thumb = new Path();    // cùng hình đó, để khoét chữ thật
    private float radius;

    private final Runnable tick = new Runnable() {
        @Override public void run() { step(); }
    };

    GlassSeg(Context c, Pal pal, String[] names, int initial, int delayMs,
             IntConsumer onPick, Consumer<Throwable> onFail) {
        super(c);
        this.pal = pal;
        this.n = names.length;
        this.delayMs = delayMs;
        this.onPick = onPick;
        this.onFail = onFail;
        this.selected = initial;
        dp = c.getResources().getDisplayMetrics().density;
        pad = Math.round(4f * dp);
        cellH = Math.round(42f * dp);
        padX = Math.round(28f * dp);
        padY = Math.round(14f * dp);       // chừa chỗ cho ô nhô ra ~6dp mỗi phía + lò xo nảy + viền sáng
        slop = ViewConfiguration.get(c).getScaledTouchSlop();
        index.snap(initial);

        setClipChildren(false);
        setClipToPadding(false);
        // viên thuốc bo tròn trọn hai đầu; viền nằm ở thanh ngoài, ô kính bên trong không viền
        setBackground(rounded(pal.tonal, 30, (pal.accent & 0x00FFFFFF) | 0x40000000));

        cells = new LinearLayout(c);
        accent = new LinearLayout(c);
        labels = new TextView[n];
        for (int i = 0; i < n; i++) {
            final int idx = i;
            labels[i] = label(c, names[i], false);
            labels[i].setOnClickListener(v -> commit(idx));     // chỉ để dịch vụ trợ năng bấm được; cảm ứng do GlassSeg xử lý
            cells.addView(labels[i], new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
            accent.addView(label(c, names[i], true), new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f));
        }
        cells.setOrientation(LinearLayout.HORIZONTAL);
        accent.setOrientation(LinearLayout.HORIZONTAL);
        accent.setVisibility(INVISIBLE);
        accent.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS);
        styleLabels();

        ind = new IndicatorView(c);
        rim = new RimView(c);
        ind.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        rim.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);

        addView(cells);
        addView(accent);
        addView(ind);
        addView(rim);
    }

    private TextView label(Context c, String text, boolean copy) {
        TextView t = new TextView(c);
        t.setText(text);
        t.setTextSize(13);
        t.setGravity(Gravity.CENTER);
        t.setSingleLine(true);
        t.setTextColor(copy ? pal.accent : pal.text);
        if (copy) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private android.graphics.drawable.GradientDrawable rounded(int fill, int radiusDp, int stroke) {
        android.graphics.drawable.GradientDrawable g = new android.graphics.drawable.GradientDrawable();
        g.setColor(fill);
        g.setCornerRadius(radiusDp * dp);
        if (stroke != 0) g.setStroke(Math.round(1.5f * dp), stroke);
        return g;
    }

    private void styleLabels() {
        for (int i = 0; i < n; i++) {
            boolean on = i == selected;
            labels[i].setTextColor(on ? pal.accent : pal.text);
            labels[i].setTypeface(on ? Typeface.DEFAULT_BOLD : Typeface.DEFAULT);
        }
    }

    // ---------------------------------------------------------------- bố cục

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int w = MeasureSpec.getSize(wSpec);
        int h = cellH + 2 * pad;
        int inner = MeasureSpec.makeMeasureSpec(Math.max(0, w - 2 * pad), MeasureSpec.EXACTLY);
        int cellSpec = MeasureSpec.makeMeasureSpec(cellH, MeasureSpec.EXACTLY);
        cells.measure(inner, cellSpec);
        accent.measure(inner, cellSpec);
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
        cells.layout(pad, pad, w - pad, pad + cellH);
        accent.layout(pad, pad, w - pad, pad + cellH);
        ind.layout(-padX, -padY, w + padX, h + padY);
        rim.layout(-padX, -padY, w + padX, h + padY);
        if (w <= 2 * pad || n == 0) {
            ready = false;
            return;
        }
        cw = (w - 2f * pad) / n;
        x0 = pad;
        itemTop = pad;
        ih = cellH;
        if (w != builtW || h != builtH) {
            lensNode.setPosition(0, 0, w + 2 * padX, h + 2 * padY);
            lensNode.setRenderEffect(Glass.backdrop(Glass.SEG_BLUR_DP * dp, null));
            builtW = w;
            builtH = h;
        }
        ready = true;
        update();
        invalidateAll();
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        // ô kính phồng ra ngoài khung thanh: thẻ chứa nó không được cắt (cả theo khung con lẫn theo vùng padding)
        if (getParent() instanceof ViewGroup) {
            ViewGroup g = (ViewGroup) getParent();
            g.setClipChildren(false);
            g.setClipToPadding(false);
        }
    }

    /** Chữ thật bị khoét đúng hình ô kính (chỗ đó ô kính tự vẽ lại chữ accent), tránh hiện hai lớp chữ chồng nhau. */
    @Override
    protected boolean drawChild(Canvas canvas, View child, long drawingTime) {
        if (child == cells && ready && canvas.isHardwareAccelerated()) {
            canvas.save();
            canvas.clipOutPath(thumb);
            boolean r = super.drawChild(canvas, child, drawingTime);
            canvas.restore();
            return r;
        }
        return super.drawChild(canvas, child, drawingTime);
    }

    // ---------------------------------------------------------------- chuyển động

    private void press() {
        if (!Anim.on()) return;
        pendingRelease = false;
        press.target = 1f;
        sx.target = Glass.SEG_LENS_ALONG;
        sy.target = ih > 0f ? Math.max(1f, getHeight() * Glass.SEG_LENS_OVERSHOOT / ih) : Glass.SEG_PRESSED_SCALE;
    }

    private void release() {
        if (!Anim.on()) return;
        pendingRelease = true;
    }

    private void kick() {
        if (failed || !isAttachedToWindow()) return;
        if (!Anim.on()) {                       // tắt animation hệ thống: nhảy thẳng tới đích
            index.snap(clamp(index.target, 0f, n - 1));
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
        if (running) return;
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

            // nhả tay: đợi ô kính gần tới nơi (2.5% quãng đường) rồi mới thu nhỏ lại, như bản gốc
            if (pendingRelease && Math.abs(index.value - index.target) < 0.025f * Math.max(1, n - 1)) {
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

    /** Tính lại hình dạng ô kính từ trạng thái các lò xo. */
    private void update() {
        if (!ready) return;
        float cx = x0 + (index.value + 0.5f) * cw;
        float cy = itemTop + ih / 2f;
        float v = Math.abs(index.velocity) / Math.max(1, n - 1) / 10f;
        float wx = sx.value / (1f - clamp(v * 0.75f, 0f, 0.2f));
        float wy = sy.value * (1f - clamp(v * 0.25f, 0f, 0.2f));
        float hw = cw * wx / 2f;
        float hh = ih * wy / 2f;
        rect.set(cx - hw, cy - hh, cx + hw, cy + hh);
        // luôn là viên thuốc: bo tròn trọn hai đầu cả lúc nghỉ lẫn lúc nhấn
        radius = Math.min(hw, hh);
        thumb.reset();
        thumb.addRoundRect(rect, radius, radius, Path.Direction.CW);

        // chữ trong ô được shader phóng đại (Glass.SEG_LENS_MAGNIFY), không phóng tay từng view nữa
        ind.applyLens();
    }

    private void invalidateAll() {
        invalidate();       // vẽ lại chữ thật với vết khoét mới
        ind.invalidate();
        rim.invalidate();
    }

    private void fail(Throwable t) {
        if (failed) return;
        failed = true;
        removeCallbacks(tick);
        post(() -> onFail.accept(t));
    }

    private static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }

    // ---------------------------------------------------------------- chọn / cảm ứng

    private int indexAt(float x) {
        if (cw <= 0f) return selected;
        int i = (int) Math.floor((x - x0) / cw);
        return Math.max(0, Math.min(n - 1, i));
    }

    /** Chốt lựa chọn: đổi màu chữ, đưa ô kính về ô đó, rồi báo ra ngoài (sau delayMs nếu có). */
    /** Phản hồi rung nhẹ mỗi khi ô kính đi qua sang một mục khác (chạm hoặc kéo), như thanh chọn của iOS. */
    private int tickIdx = -1;

    private void tickIfChanged() {
        int i = Math.round(index.target);
        if (i == tickIdx) return;
        tickIdx = i;
        performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);
    }

    private void commit(int t) {
        t = Math.max(0, Math.min(n - 1, t));
        index.target = t;
        if (!touching) press();         // bấm bằng nút/trợ năng: tự nhấn rồi nhả
        release();
        kick();
        if (t == selected) return;
        selected = t;
        styleLabels();
        final int pick = t;
        if (delayMs > 0) postDelayed(() -> onPick.accept(pick), delayMs);
        else onPick.accept(pick);
    }

    @Override
    public boolean onInterceptTouchEvent(MotionEvent e) {
        return e.getActionMasked() == MotionEvent.ACTION_DOWN;     // mọi cảm ứng do GlassSeg tự xử lý
    }

    @Override
    public boolean onTouchEvent(MotionEvent e) {
        switch (e.getActionMasked()) {
            case MotionEvent.ACTION_DOWN:
                if (!ready) return true;
                touching = true;
                dragging = false;
                downX = e.getX();
                lastX = downX;
                index.target = indexAt(downX);      // ô kính chạy ngay tới ô đang chạm và phồng lên
                tickIdx = selected;
                tickIfChanged();
                press();
                kick();
                return true;
            case MotionEvent.ACTION_MOVE:
                if (!touching) return true;
                if (!dragging && Math.abs(e.getX() - downX) > slop) {
                    dragging = true;
                    lastX = e.getX();
                    if (getParent() != null) getParent().requestDisallowInterceptTouchEvent(true);   // không để ScrollView giành
                }
                if (dragging && cw > 0f) {
                    float dx = e.getX() - lastX;
                    lastX = e.getX();
                    index.target = clamp(index.target + dx / cw, 0f, n - 1);
                    tickIfChanged();
                    kick();
                }
                return true;
            case MotionEvent.ACTION_UP:
                if (touching) {
                    int t = dragging ? Math.round(index.target) : indexAt(downX);
                    touching = false;
                    dragging = false;
                    commit(t);
                }
                return true;
            case MotionEvent.ACTION_CANCEL:
                if (touching) {
                    touching = false;
                    dragging = false;
                    index.target = selected;
                    release();
                    kick();
                }
                return true;
            default:
                return true;
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        removeCallbacks(tick);
        running = false;
        super.onDetachedFromWindow();
    }

    // ================================================================ các lớp vẽ

    /** Ô kính. Toạ độ vẽ là toạ độ GlassSeg cộng (padX, padY) vì view này rộng hơn thanh. */
    private final class IndicatorView extends View {
        private final Paint surface = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Path clip = new Path();
        private final int[] a = new int[2];
        private final int[] b = new int[2];
        private final GlassFx fx = new GlassFx(dp);
        private RuntimeShader lensShader;
        private boolean lensOn;

        IndicatorView(Context c) {
            super(c);
            try {
                lensShader = Glass.newLens(true);
            } catch (RuntimeException e) {
                Log.w(TAG, "glass seg: lens unavailable", e);
            }
        }

        /** Gọi từ update(), không gọi trong onDraw (setRenderEffect làm view invalidate). */
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
                float k = Glass.SEG_LENS_SCALE * dp * p;
                setRenderEffect(Glass.lens(lensShader, true, rect.width(), rect.height(),
                        rect.left + padX, rect.top + padY, radius,
                        Glass.PILL_REFRACTION_HEIGHT_DP * k, Glass.PILL_REFRACTION_AMOUNT_DP * k,
                        1f, Glass.PILL_CHROMA, 1f + Glass.SEG_LENS_MAGNIFY * p));
                lensOn = true;
            } catch (RuntimeException e) {
                Log.w(TAG, "glass seg: lens failed, disabled", e);
                lensShader = null;
                setRenderEffect(null);
                lensOn = false;
            }
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!ready || !c.isHardwareAccelerated()) return;
            try {
                float p = clamp(press.value, 0f, 1f);
                clip.reset();
                clip.addRoundRect(rect.left + padX, rect.top + padY, rect.right + padX, rect.bottom + padY,
                        radius, radius, Path.Direction.CW);
                int w = GlassSeg.this.getWidth();
                int h = GlassSeg.this.getHeight();
                // nền kính của ô: nền trang + thẻ chứa + nền thanh. Ghi vào node riêng (không dùng chính view cha vì cha chứa luôn
                // ô này: vẽ lại cha sẽ kéo theo ảnh cũ của ô vào nền), nên nền kính hiện cả phần ô nhô ra ngoài thanh.
                RecordingCanvas rc = lensNode.beginRecording(w + 2 * padX, h + 2 * padY);
                try {
                    rc.drawColor(pal.bgBottom);
                    rc.translate(padX, padY);                  // toạ độ GlassSeg
                    android.view.ViewParent pp = GlassSeg.this.getParent();
                    if (pp instanceof View) {
                        View par = (View) pp;
                        Drawable pbg = par.getBackground();
                        if (pbg != null) {
                            GlassSeg.this.getLocationInWindow(a);
                            par.getLocationInWindow(b);
                            rc.save();
                            rc.translate(b[0] - a[0], b[1] - a[1]);
                            pbg.draw(rc);
                            rc.restore();
                        }
                    }
                    Drawable sbg = GlassSeg.this.getBackground();
                    if (sbg != null) sbg.draw(rc);
                } finally {
                    lensNode.endRecording();
                }
                c.save();
                c.clipPath(clip);
                c.drawRenderNode(lensNode);                    // toạ độ view (đã gồm lề padX/padY)
                c.translate(padX, padY);                       // từ đây là toạ độ GlassSeg
                // màu nền ô: accent nhạt khi nghỉ (giống khối sáng cũ), gần như trong suốt khi nhấn để thấy khúc xạ
                int al = Math.round(0x26 * (1f - 0.6f * p));
                surface.setColor((pal.accent & 0x00FFFFFF) | (al << 24));
                c.drawRect(rect, surface);
                c.save();
                c.translate(accent.getLeft(), accent.getTop());
                accent.draw(c);                                // chữ accent
                c.restore();
                // phản chiếu xanh, cùng kiểu với thanh nav
                fx.reflection(c, rect, (pal.dark ? 0.02f : 0.02f) + (pal.dark ? 0.08f : 0.05f) * p);
                c.restore();
            } catch (RuntimeException e) {
                fail(e);
            }
        }
    }

    /** Chỉ vẽ khi nhấn: viền sáng + bóng trong của thấu kính. Nằm ngoài IndicatorView nên không bị khúc xạ. */
    private final class RimView extends View {
        private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final GlassFx fx = new GlassFx(dp);
        private final Path shape = new Path();
        private final Path shapeStroke = new Path();
        private final SweepGradient grad = GlassFx.tintedLobes(225f, 0xFF86A4FF, 45f, 0xFF86A4FF, 0.85f);

        RimView(Context c) {
            super(c);
            stroke.setStyle(Paint.Style.STROKE);
            stroke.setStrokeWidth(fx.strokeWidth());
        }

        @Override
        protected void onDraw(Canvas c) {
            if (!ready || !c.isHardwareAccelerated()) return;
            try {
                float p = clamp(press.value, 0f, 1f);
                float l = rect.left + padX;
                float t = rect.top + padY;
                float rr = rect.right + padX;
                float bb = rect.bottom + padY;
                float in = fx.strokeWidth() / 2f;
                float ri = Math.max(0f, radius - in);
                shapeStroke.reset();
                shapeStroke.addRoundRect(l + in, t + in, rr - in, bb - in, ri, ri, Path.Direction.CW);

                // lúc nghỉ ô không có viền (viền là của thanh ngoài); chỉ khi nhấn mới có viền sáng của thấu kính
                if (p <= 0.002f) return;
                shape.reset();
                shape.addRoundRect(l, t, rr, bb, radius, radius, Path.Direction.CW);
                fx.innerShadow(c, getWidth(), getHeight(), shape, p);
                fx.specular(c, shapeStroke, grad, (l + rr) / 2f, (t + bb) / 2f, p);
            } catch (RuntimeException e) {
                fail(e);
            }
        }
    }
}
