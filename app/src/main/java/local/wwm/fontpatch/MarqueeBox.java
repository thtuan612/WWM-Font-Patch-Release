package local.wwm.fontpatch;

import android.animation.ValueAnimator;
import android.content.Context;
import android.view.MotionEvent;
import android.view.View;
import android.widget.HorizontalScrollView;

/** Ô chữ một dòng tự trôi qua lại khi nội dung dài hơn khung. */
final class MarqueeBox extends HorizontalScrollView {
    private static final long PAUSE = 1400;
    private ValueAnimator anim;
    private View child;

    MarqueeBox(Context c) {
        super(c);
        setHorizontalScrollBarEnabled(false);
        setOverScrollMode(OVER_SCROLL_NEVER);
        setFillViewport(true);
    }

    void bind(View v) {
        child = v;
        v.addOnLayoutChangeListener((view, l, t, r, b, ol, ot, or, ob) -> {
            if (r - l != or - ol) restart();
        });
    }

    @Override public boolean onTouchEvent(MotionEvent e) { return false; }
    @Override public boolean onInterceptTouchEvent(MotionEvent e) { return false; }

    @Override protected void onAttachedToWindow() { super.onAttachedToWindow(); restart(); }

    @Override protected void onDetachedFromWindow() {
        if (anim != null) anim.cancel();
        super.onDetachedFromWindow();
    }

    @Override protected void onSizeChanged(int w, int h, int ow, int oh) {
        super.onSizeChanged(w, h, ow, oh);
        restart();
    }

    void restart() {
        if (anim != null) { anim.cancel(); anim = null; }
        scrollTo(0, 0);
        post(() -> {
            if (child == null || !isAttachedToWindow()) return;
            final int max = child.getWidth() - getWidth();
            if (max <= 0) return;
            float speed = 0.04f * getResources().getDisplayMetrics().density;   // ~40dp/giây
            final long move = Math.max(1500L, (long) (max / speed));
            anim = ValueAnimator.ofFloat(0f, 1f);
            anim.setDuration(2 * (move + PAUSE));
            anim.setInterpolator(new android.view.animation.LinearInterpolator());
            anim.setRepeatCount(ValueAnimator.INFINITE);
            anim.addUpdateListener(a -> scrollTo((int) (marqueePos((float) a.getAnimatedValue(), move, PAUSE) * max), 0));
            anim.start();
        });
    }

    /** 0 → 1 → 1 (dừng) → 0 → 0 (dừng) theo tiến độ f ∈ [0,1) của một chu kỳ qua lại. */
    private static float marqueePos(float f, long move, long pause) {
    float total = 2f * (move + pause);
    float x = f * total;
    if (x < pause) return 0f;
    x -= pause;
    if (x < move) return Anim.STD.getInterpolation(x / move);
    x -= move;
    if (x < pause) return 1f;
    x -= pause;
    return 1f - Anim.STD.getInterpolation(Math.min(1f, x / move));
    }

    /** Ô một dòng: nếu chữ dài hơn ô thì tự cuộn nhẹ qua lại; không bắt thao tác chạm. */
}
