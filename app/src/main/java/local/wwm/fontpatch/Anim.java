package local.wwm.fontpatch;

import android.animation.ObjectAnimator;
import android.animation.ValueAnimator;
import android.app.Dialog;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.view.animation.Interpolator;
import android.view.animation.PathInterpolator;
import android.widget.PopupWindow;

/**
 * Chuyển động của UI, đặt trên động cơ lò xo {@link Motion}.
 *
 * Nguyên tắc (theo "Designing Fluid Interfaces" của Apple):
 *  - Phản hồi ngay lúc chạm, không đợi thả.
 *  - Mọi chuyển động là lò xo nên ngắt được/đổi hướng được tại bất kỳ lúc nào, vận tốc được giữ nguyên.
 *  - Mặc định tắt nảy (damping 1); chỉ nảy nhẹ khi chuyển động có "đà" (nhả nút, thả ô chọn, con dấu).
 *  - Vào và ra theo cùng một lối, bám vào nguồn gốc của nó (popup phóng ra từ nút bấm rồi thu về đúng nút đó).
 *  - Tất cả tự bỏ qua khi người dùng tắt animation trong hệ thống (Anim.on() = false).
 */
final class Anim {
    static final Interpolator OUT = new PathInterpolator(0f, 0f, 0.2f, 1f);
    static final Interpolator IN = new PathInterpolator(0.4f, 0f, 1f, 1f);
    static final Interpolator STD = new PathInterpolator(0.4f, 0f, 0.2f, 1f);
    /** Giữ lại cho chỗ còn dùng Interpolator (marquee, hiệu ứng đổi theme...). */
    static final Interpolator SPRING = new SpringInterpolator(1f, 0.4f, 0.55f);
    static final Interpolator POP = new SpringInterpolator(0.8f, 0.35f, 0.5f);

    // ---- bộ tham số lò xo (damping, response giây) dùng thống nhất cho cả app
    private static final float PRESS_DOWN_Z = 1f, PRESS_DOWN_R = 0.12f;      // lún xuống nhanh, không nảy
    private static final float PRESS_UP_Z = 0.72f, PRESS_UP_R = 0.30f;       // nhả ra: bật lại có đà, nảy nhẹ
    private static final float PAGE_Z = 1f, PAGE_R = 0.40f;                  // chuyển trang: êm, không nảy
    private static final float FADE_Z = 1f, FADE_R = 0.24f;
    private static final float POPUP_Z = 0.86f, POPUP_R = 0.34f;             // popup bung ra: nảy rất nhẹ
    private static final float DIALOG_Z = 0.86f, DIALOG_R = 0.36f;
    private static final float EXIT_Z = 1f, EXIT_R = 0.15f;                  // đóng: nhanh, gọn

    private Anim() { }

    /**
     * Đáp ứng bước của lò xo giảm chấn dạng Interpolator (cho các chỗ chưa chuyển sang Motion).
     */
    static final class SpringInterpolator implements Interpolator {
        private final float zeta, omega, total;

        SpringInterpolator(float damping, float response, float totalSeconds) {
            zeta = damping;
            omega = (float) (2 * Math.PI / response);
            total = totalSeconds;
        }

        @Override
        public float getInterpolation(float in) {
            if (in >= 1f) return 1f;
            double t = in * total;
            double e = Math.exp(-zeta * omega * t);
            double x;
            if (zeta >= 1f) {
                x = 1 - (1 + omega * t) * e;
            } else {
                double wd = omega * Math.sqrt(1 - zeta * zeta);
                x = 1 - e * (Math.cos(wd * t) + (zeta * omega / wd) * Math.sin(wd * t));
            }
            float tail = Math.max(0f, (in - 0.85f) / 0.15f);
            return (float) (x + (1 - x) * tail * tail);
        }
    }

    static boolean on() { return ValueAnimator.areAnimatorsEnabled(); }

    static void reset(View v) {
        Motion.stop(v);
        v.animate().cancel();
        v.setAlpha(1f);
        v.setScaleX(1f);
        v.setScaleY(1f);
        v.setTranslationX(0f);
        v.setTranslationY(0f);
        v.setRotation(0f);
    }

    private static float dp(View v, float x) { return x * v.getResources().getDisplayMetrics().density; }

    // ------------------------------------------------------------------ nhấn

    /**
     * Nhấn: lún xuống NGAY lúc chạm (không đợi thả) bằng lò xo cứng không nảy; nhả ra thì bật lại bằng lò xo có đà,
     * nảy rất nhẹ. Trượt ngón ra khỏi view thì coi như nhả. Vì là lò xo giữ vận tốc nên bấm/thả dồn dập vẫn liền mạch.
     * View rộng lún ít hơn để không bị "chìm" quá.
     */
    static void press(View v) {
        final boolean[] down = {false};
        v.setOnTouchListener((x, e) -> {
            switch (e.getActionMasked()) {
                case MotionEvent.ACTION_DOWN: {
                    down[0] = true;
                    float s = x.getWidth() > dp(x, 220) ? 0.985f : 0.96f;
                    Motion.spring(x, Motion.SCALE, s, PRESS_DOWN_Z, PRESS_DOWN_R);
                    Motion.spring(x, Motion.ALPHA, 0.9f, PRESS_DOWN_Z, PRESS_DOWN_R);
                    break;
                }
                case MotionEvent.ACTION_MOVE: {
                    float slop = dp(x, 12);
                    boolean inside = e.getX() > -slop && e.getY() > -slop
                            && e.getX() < x.getWidth() + slop && e.getY() < x.getHeight() + slop;
                    if (down[0] && !inside) {
                        down[0] = false;
                        release(x);
                    }
                    break;
                }
                case MotionEvent.ACTION_UP:
                case MotionEvent.ACTION_CANCEL:
                    if (down[0]) {
                        down[0] = false;
                        release(x);
                    }
                    break;
                default:
                    break;
            }
            return false;
        });
    }

    private static void release(View x) {
        Motion.spring(x, Motion.SCALE, 1f, PRESS_UP_Z, PRESS_UP_R);
        Motion.spring(x, Motion.ALPHA, 1f, 1f, FADE_R);
    }

    // ------------------------------------------------------------------ trượt (thanh nav dự phòng)

    /** Ô chọn của thanh nav dự phòng trượt theo trục X tới vị trí mới: lò xo có đà, nảy nhẹ ở cuối. */
    static void slide(View v, float x, boolean animate) {
        slideAxis(v, x, false, animate);
    }

    /** Trượt theo trục X (vertical = false) hoặc Y (vertical = true). */
    static void slideAxis(View v, float t, boolean vertical, boolean animate) {
        int p = vertical ? Motion.TY : Motion.TX;
        float cur = vertical ? v.getTranslationY() : v.getTranslationX();
        if (!animate || !on() || cur == t) {
            Motion.snap(v, p, t);
            return;
        }
        Motion.spring(v, p, t, 0.8f, 0.36f);
    }

    // ------------------------------------------------------------------ chuyển trang

    /** Các con trượt lên lần lượt (tổng tối đa ~300ms) bằng lò xo. */
    static void stagger(ViewGroup parent, int from, float dx, float dy, int delay) {
        if (!on()) return;
        int n = parent.getChildCount() - from;
        int step = n > 1 ? Math.min(32, 280 / (n - 1)) : 0;
        for (int i = 0; i < n; i++) {
            final View c = parent.getChildAt(from + i);
            reset(c);
            c.setAlpha(0f);
            c.setTranslationX(dx);
            c.setTranslationY(dy);
            Motion.later(c, delay + i * step, () -> {
                Motion.spring(c, Motion.TX, 0f, PAGE_Z, PAGE_R);
                Motion.spring(c, Motion.TY, 0f, PAGE_Z, PAGE_R);
                Motion.spring(c, Motion.ALPHA, 1f, FADE_Z, FADE_R);
            });
        }
    }

    /**
     * Trang mới trượt vào theo hướng người dùng đi (kiểu đẩy của iOS). Nếu trang đang dở dang lúc rời đi thì giữ nguyên
     * vị trí và vận tốc hiện tại rồi quay về, không nhảy về điểm xuất phát.
     */
    static void pageIn(View page, ViewGroup content, int dir, float dx) {
        Motion.cancelEnd(page);
        boolean wasHidden = page.getVisibility() != View.VISIBLE;
        page.setVisibility(View.VISIBLE);
        if (!on()) {
            reset(page);
            return;
        }
        if (wasHidden) {
            Motion.stop(page);
            page.setAlpha(0f);
            page.setTranslationX(dir * dx);
        }
        Motion.spring(page, Motion.TX, 0f, PAGE_Z, PAGE_R);
        Motion.spring(page, Motion.ALPHA, 1f, FADE_Z, FADE_R);
        if (wasHidden) stagger(content, 0, 0f, dp(page, 10), 20);
    }

    /**
     * Trang cũ lùi nhẹ về phía ngược lại (parallax, chỉ đi nửa quãng của trang mới) và mờ đi; end chạy khi đã dừng.
     */
    static void pageOut(View page, int dir, float dx, Runnable end) {
        if (!on()) {
            end.run();
            return;
        }
        Motion.spring(page, Motion.TX, -dir * dx * 0.5f, PAGE_Z, 0.30f);
        Motion.spring(page, Motion.ALPHA, 0f, FADE_Z, 0.20f);
        Motion.onSettle(page, end);
    }

    // ------------------------------------------------------------------ hiện / đổi nội dung

    /** Hiện view đang GONE: phóng nhẹ từ 0.8 lên 1 bằng lò xo có đà. */
    static void popIn(View v) {
        Motion.stop(v);
        v.setVisibility(View.VISIBLE);
        if (!on()) return;
        v.setAlpha(0f);
        v.setScaleX(0.8f);
        v.setScaleY(0.8f);
        Motion.spring(v, Motion.SCALE, 1f, 0.78f, 0.36f);
        Motion.spring(v, Motion.ALPHA, 1f, FADE_Z, FADE_R);
    }

    /** Nội dung vừa đổi: mờ + lệch xuống rồi về chỗ cũ. */
    static void swap(View v, float dy, float fromAlpha) {
        if (v == null || !on()) return;
        Motion.stop(v);
        v.setAlpha(fromAlpha);
        v.setTranslationY(dy);
        Motion.spring(v, Motion.TY, 0f, PAGE_Z, 0.30f);
        Motion.spring(v, Motion.ALPHA, 1f, FADE_Z, FADE_R);
    }

    /** Icon vừa được chọn: co nhẹ rồi bật về, có đà. */
    static void bounce(View v) {
        if (!on()) return;
        Motion.stop(v);
        v.setScaleX(0.86f);
        v.setScaleY(0.86f);
        Motion.spring(v, Motion.SCALE, 1f, 0.55f, 0.34f);
    }

    /** Con dấu đóng xuống. */
    static void stamp(View v, int delay) {
        if (v == null || !on()) return;
        reset(v);
        v.setAlpha(0f);
        v.setScaleX(1.45f);
        v.setScaleY(1.45f);
        v.setRotation(-6f);
        Motion.later(v, delay, () -> {
            Motion.spring(v, Motion.SCALE, 1f, 0.72f, 0.42f);
            Motion.spring(v, Motion.ROT, 0f, 0.72f, 0.42f);
            Motion.spring(v, Motion.ALPHA, 1f, FADE_Z, 0.18f);
        });
    }

    static void shake(View v, float px) {
        if (v == null || !on()) return;
        ObjectAnimator o = ObjectAnimator.ofFloat(v, View.TRANSLATION_X,
                0f, -px, px, -px * 0.6f, px * 0.6f, 0f);
        o.setDuration(380);
        o.setStartDelay(150);
        o.start();
    }

    // ------------------------------------------------------------------ hộp thoại

    static void dialogIn(Dialog d, View box, boolean fadeDim) {
        if (!on() || box == null) return;
        Motion.stop(box);
        box.setAlpha(0f);
        box.setScaleX(0.9f);
        box.setScaleY(0.9f);
        Motion.spring(box, Motion.SCALE, 1f, DIALOG_Z, DIALOG_R);
        Motion.spring(box, Motion.ALPHA, 1f, FADE_Z, FADE_R);
        if (fadeDim) dim(d, true);
    }

    static void dialogOut(Dialog d, View box) {
        if (!d.isShowing()) return;
        if (!on() || box == null) {
            d.dismiss();
            return;
        }
        dim(d, false);
        Motion.spring(box, Motion.SCALE, 0.95f, EXIT_Z, EXIT_R);
        Motion.spring(box, Motion.ALPHA, 0f, EXIT_Z, 0.13f);
        Motion.onSettle(box, () -> {
            try {
                d.dismiss();
            } catch (RuntimeException ignored) {
            }
        });
    }

    private static void dim(Dialog d, boolean in) {
        final Window w = d.getWindow();
        if (w == null) return;
        final float cur = w.getAttributes().dimAmount;
        final float from = in ? 0f : cur;
        final float to = in ? cur : 0f;
        w.setDimAmount(from);
        ValueAnimator va = ValueAnimator.ofFloat(from, to);
        va.setDuration(in ? 240 : 140);
        va.setInterpolator(OUT);
        va.addUpdateListener(u -> w.setDimAmount((float) u.getAnimatedValue()));
        va.start();
    }

    // ------------------------------------------------------------------ popup bám nút

    /**
     * Popup phóng ra từ chính nút đã bấm (pivot đặt tại tâm nút), không phải từ giữa popup, để thấy rõ nó đến từ đâu.
     */
    static void popupIn(View box, float pivotX, float pivotY) {
        if (!on()) return;
        Motion.stop(box);
        box.setPivotX(pivotX);
        box.setPivotY(pivotY);
        box.setScaleX(0.8f);
        box.setScaleY(0.8f);
        box.setAlpha(0f);
        Motion.spring(box, Motion.SCALE, 1f, POPUP_Z, POPUP_R);
        Motion.spring(box, Motion.ALPHA, 1f, FADE_Z, 0.20f);
    }

    /** Đóng popup: thu về đúng nút nó bung ra (cùng pivot) rồi mới huỷ cửa sổ. Gọi nhiều lần vẫn an toàn. */
    static void dismiss(PopupWindow pw) {
        if (pw == null || !pw.isShowing()) return;
        final View box = pw.getContentView();
        if (!on() || box == null) {
            pw.dismiss();
            return;
        }
        Motion.spring(box, Motion.SCALE, 0.88f, EXIT_Z, EXIT_R);
        Motion.spring(box, Motion.ALPHA, 0f, EXIT_Z, 0.13f);
        Motion.onSettle(box, () -> {
            try {
                pw.dismiss();
            } catch (RuntimeException ignored) {
            }
        });
    }
}
