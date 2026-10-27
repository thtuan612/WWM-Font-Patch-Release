package local.wwm.fontpatch;

import android.animation.Animator;
import android.animation.AnimatorSet;
import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewPropertyAnimator;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.animation.OvershootInterpolator;
import android.view.animation.PathInterpolator;

/** Motion helpers. Tất cả tự bỏ qua khi người dùng tắt animation trong hệ thống. */
final class Anim {
    static final Interpolator OUT = new PathInterpolator(0f, 0f, 0.2f, 1f);
    static final Interpolator IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    static final Interpolator STD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    static final Interpolator POP = new OvershootInterpolator(1.8f);
    private static final Interpolator GLIDE = new OvershootInterpolator(0.9f);

    /** Hiệu ứng giãn/nén của ô chọn trên thanh nav (chỉ có một ô nên giữ một biến). */
    private static AnimatorSet stretch;

    private Anim() { }

    static boolean on() { return ValueAnimator.areAnimatorsEnabled(); }

    /** animate() giữ lại delay/interpolator cũ, nên luôn đặt lại tường minh. */
    private static ViewPropertyAnimator a(View v, int ms, Interpolator ip) {
        return v.animate().setListener(null).setStartDelay(0).setDuration(ms).setInterpolator(ip);
    }

    static void reset(View v) {
        v.animate().cancel();
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setRotation(0f);
    }

    /** Nhấn: thu nhỏ + mờ nhẹ, thả ra thì bật lại. View rộng thu ít hơn. */
    static void press(View v) {
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    float s = x.getWidth() > 220 * x.getResources().getDisplayMetrics().density ? 0.98f : 0.95f;
                    to(x, s, 0.8f, 90, OUT);
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    to(x, 1f, 1f, 180, OUT);
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void to(View v, float s, float al, int ms, Interpolator ip) {
        if (!on()) {
            v.setScaleX(s);
            v.setScaleY(s);
            v.setAlpha(al);
            return;
        }
        a(v, ms, ip).scaleX(s).scaleY(s).alpha(al).start();
    }

    /**
     * Ô chọn của thanh nav trượt tới vị trí mới: lướt có nảy nhẹ ở cuối,
     * giãn ngang lúc đang chạy rồi co lại như giọt nước.
     */
    static void slide(View v, float x, boolean animate) {
        v.animate().cancel();
        if (stretch != null) {
            stretch.cancel();
            stretch = null;
        }
        if (!animate || !on() || v.getTranslationX() == x) {
            v.setTranslationX(x);
            v.setScaleX(1f);
            v.setScaleY(1f);
            return;
        }
        float dpi = v.getResources().getDisplayMetrics().density;
        float dist = Math.abs(x - v.getTranslationX()) / dpi;
        int ms = (int) Math.min(380f, 260f + dist * 0.35f);
        a(v, ms, GLIDE).translationX(x).start();

        ObjectAnimator sx = ObjectAnimator.ofFloat(v, View.SCALE_X, 1f, 1.14f, 1f);
        ObjectAnimator sy = ObjectAnimator.ofFloat(v, View.SCALE_Y, 1f, 0.93f, 1f);
        AnimatorSet set = new AnimatorSet();
        set.playTogether(sx, sy);
        set.setDuration(ms);
        set.setInterpolator(STD);
        set.addListener(new android.animation.AnimatorListenerAdapter() {
            @Override
            public void onAnimationCancel(Animator animation) {
                v.setScaleX(1f);
                v.setScaleY(1f);
            }
        });
        stretch = set;
        set.start();
    }

    /** Các con trượt vào lần lượt (tối đa ~400ms tổng). */
    static void stagger(ViewGroup parent, int from, float dx, float dy, int delay) {
        if (!on()) return;
        int n = parent.getChildCount() - from;
        int step = n > 1 ? Math.min(40, 360 / (n - 1)) : 0;
        for (int i = 0; i < n; i++) {
            View c = parent.getChildAt(from + i);
            reset(c);
            c.setAlpha(0f);
            c.setTranslationX(dx);
            c.setTranslationY(dy);
            a(c, 260, OUT).alpha(1f).translationX(0f).translationY(0f)
                    .setStartDelay(delay + i * step).start();
        }
    }

    static void pageIn(View page, ViewGroup content, int dir, float dx) {
        reset(page);
        page.setVisibility(View.VISIBLE);
        if (!on()) return;
        page.setAlpha(0f);
        a(page, 200, OUT).alpha(1f).start();
        stagger(content, 0, dir * dx, 0f, 0);
    }

    static void pageOut(View page, int dir, float dx, Runnable end) {
        if (!on()) {
            end.run();
            return;
        }
        a(page, 130, IN).alpha(0f).translationX(-dir * dx).withEndAction(end).start();
    }

    /** Hiện view đang GONE bằng hiệu ứng bật ra. */
    static void popIn(View v) {
        v.setVisibility(View.VISIBLE);
        if (!on()) return;
        v.setAlpha(0f);
        v.setScaleX(0.7f);
        v.setScaleY(0.7f);
        a(v, 320, POP).alpha(1f).scaleX(1f).scaleY(1f).start();
    }

    /** Nội dung vừa đổi: fade + trượt nhẹ lên. */
    static void swap(View v, float dy, float fromAlpha) {
        if (v == null || !on()) return;
        v.animate().cancel();
        v.setAlpha(fromAlpha);
        v.setTranslationY(dy);
        a(v, 220, OUT).alpha(1f).translationY(0f).start();
    }

    static void bounce(View v) {
        if (!on()) return;
        v.animate().cancel();
        v.setScaleX(0.75f);
        v.setScaleY(0.75f);
        a(v, 320, POP).scaleX(1f).scaleY(1f).start();
    }

    /** Con dấu đóng xuống. */
    static void stamp(View v, int delay) {
        if (v == null || !on()) return;
        reset(v);
        v.setAlpha(0f);
        v.setScaleX(1.9f);
        v.setScaleY(1.9f);
        v.setRotation(-10f);
        a(v, 340, new OvershootInterpolator(1.4f)).alpha(1f).scaleX(1f).scaleY(1f)
                .rotation(0f).setStartDelay(delay).start();
    }

    static void shake(View v, float px) {
        if (v == null || !on()) return;
        ObjectAnimator o = ObjectAnimator.ofFloat(v, View.TRANSLATION_X,
                0f, -px, px, -px * 0.6f, px * 0.6f, 0f);
        o.setDuration(380);
        o.setStartDelay(150);
        o.start();
    }

    static void dialogIn(Dialog d, View box, boolean fadeDim) {
        if (!on() || box == null) return;
        box.setAlpha(0f);
        box.setScaleX(0.92f);
        box.setScaleY(0.92f);
        a(box, 240, OUT).alpha(1f).scaleX(1f).scaleY(1f).start();
        if (fadeDim) dim(d, true);
    }

    static void dialogOut(Dialog d, View box) {
        if (!d.isShowing()) return;
        if (!on() || box == null) {
            d.dismiss();
            return;
        }
        dim(d, false);
        a(box, 130, IN).alpha(0f).scaleX(0.95f).scaleY(0.95f).withEndAction(() -> {
            try {
                d.dismiss();
            } catch (RuntimeException ignored) {
            }
        }).start();
    }

    private static void dim(Dialog d, boolean in) {
        final Window w = d.getWindow();
        if (w == null) return;
        final float cur = w.getAttributes().dimAmount;
        final float from = in ? 0f : cur;
        final float to = in ? cur : 0f;
        w.setDimAmount(from);
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(in ? 240 : 130);
        va.addUpdateListener(u -> w.setDimAmount((float) u.getAnimatedValue()));
        va.start();
    }
}
