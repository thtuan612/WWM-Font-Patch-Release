package local.wwm.fontpatch;

import android.view.Choreographer;
import android.view.View;

import java.util.ArrayList;
import java.util.WeakHashMap;

/**
 * Động cơ lò xo dùng chung cho mọi chuyển động của UI.
 *
 * Khác ViewPropertyAnimator (đường cong cố định theo thời gian): mỗi thuộc tính của view có một lò xo riêng,
 * đổi đích giữa chừng thì lò xo GIỮ NGUYÊN vị trí và vận tốc hiện tại. Vì vậy mọi chuyển động đều ngắt được và
 * đổi hướng được tại bất kỳ lúc nào mà không bị khựng, không bị nhảy về giá trị đích.
 *
 * Tham số theo quy ước của Apple: damping ratio (1 = không nảy, nhỏ hơn 1 = nảy) và response (giây, chu kỳ tự nhiên;
 * không phải thời lượng). Chỉ chạy trên luồng UI, đồng hồ là Choreographer (đồng bộ màn hình).
 */
final class Motion {
    static final int TX = 0, TY = 1, SCALE = 2, ALPHA = 3, ROT = 4;
    private static final int N = 5;
    /** Ngưỡng coi là đã dừng, theo từng thuộc tính: px, px, tỉ lệ, alpha, độ. */
    private static final float[] EPS = {0.4f, 0.4f, 0.0015f, 0.002f, 0.04f};

    private static final WeakHashMap<View, Motion> ALL = new WeakHashMap<>();
    private static final ArrayList<Motion> ACTIVE = new ArrayList<>();
    private static boolean looping;
    private static long lastNanos;

    private final View view;
    private final float[] val = new float[N];
    private final float[] vel = new float[N];
    private final float[] tgt = new float[N];
    private final float[] k = new float[N];
    private final float[] c = new float[N];
    private final boolean[] live = new boolean[N];
    private Runnable end;          // chạy khi mọi thuộc tính đã dừng
    private Runnable delayed;      // việc hoãn (stagger)
    private final Runnable runDelayed = new Runnable() {
        @Override public void run() {
            Runnable r = delayed;
            delayed = null;
            if (r != null) r.run();
        }
    };

    private Motion(View v) { view = v; }

    private static Motion of(View v) {
        Motion m = ALL.get(v);
        if (m == null) {
            m = new Motion(v);
            ALL.put(v, m);
        }
        return m;
    }

    // ------------------------------------------------------------------ API

    /**
     * Kéo thuộc tính p của view về target bằng lò xo. Bắt đầu từ giá trị và vận tốc ĐANG CÓ.
     * Khi người dùng tắt animation hệ thống thì đặt thẳng giá trị.
     */
    static void spring(View v, int p, float target, float damping, float response) {
        Motion m = of(v);
        if (!Anim.on()) {
            m.live[p] = false;
            m.vel[p] = 0f;
            m.write(p, target);
            return;
        }
        if (!m.live[p]) {
            m.val[p] = m.read(p);
            m.vel[p] = 0f;
        }
        float w = (float) (2.0 * Math.PI / response);
        m.tgt[p] = target;
        m.k[p] = w * w;
        m.c[p] = 2f * damping * w;
        m.live[p] = true;
        if (!ACTIVE.contains(m)) ACTIVE.add(m);
        kick();
    }

    /** Đặt thẳng giá trị (ngắt lò xo của thuộc tính đó). */
    static void snap(View v, int p, float value) {
        Motion m = of(v);
        m.live[p] = false;
        m.vel[p] = 0f;
        m.write(p, value);
    }

    /** Dừng mọi chuyển động của view tại chỗ (giữ nguyên giá trị đang hiển thị) và huỷ việc hoãn/kết thúc. */
    static void stop(View v) {
        Motion m = ALL.get(v);
        if (m == null) return;
        for (int i = 0; i < N; i++) {
            m.live[i] = false;
            m.vel[i] = 0f;
        }
        m.end = null;
        m.delayed = null;
        v.removeCallbacks(m.runDelayed);
        ACTIVE.remove(m);
    }

    /** Huỷ việc "khi dừng thì làm" đã đăng ký trước đó (ví dụ trang đang rời đi nhưng người dùng quay lại). */
    static void cancelEnd(View v) {
        Motion m = ALL.get(v);
        if (m != null) m.end = null;
    }

    /** Chạy r khi mọi lò xo của view đã dừng (chạy ngay nếu không có lò xo nào đang chạy). */
    static void onSettle(View v, Runnable r) {
        Motion m = of(v);
        if (!m.anyLive()) {
            m.end = null;
            r.run();
            return;
        }
        m.end = r;
    }

    /** Hoãn r ms mili giây; stop(v) sẽ huỷ. */
    static void later(View v, int ms, Runnable r) {
        Motion m = of(v);
        v.removeCallbacks(m.runDelayed);
        if (ms <= 0 || !Anim.on()) {
            m.delayed = null;
            r.run();
            return;
        }
        m.delayed = r;
        v.postDelayed(m.runDelayed, ms);
    }

    /** Vận tốc hiện tại (đơn vị/giây) của một thuộc tính; 0 nếu đang đứng yên. */
    static float velocity(View v, int p) {
        Motion m = ALL.get(v);
        return m != null && m.live[p] ? m.vel[p] : 0f;
    }

    // ------------------------------------------------------------------ nội bộ

    private boolean anyLive() {
        for (int i = 0; i < N; i++) if (live[i]) return true;
        return false;
    }

    private float read(int p) {
        switch (p) {
            case TX: return view.getTranslationX();
            case TY: return view.getTranslationY();
            case SCALE: return view.getScaleX();
            case ALPHA: return view.getAlpha();
            default: return view.getRotation();
        }
    }

    private void write(int p, float x) {
        switch (p) {
            case TX: view.setTranslationX(x); break;
            case TY: view.setTranslationY(x); break;
            case SCALE:
                view.setScaleX(x);
                view.setScaleY(x);
                break;
            case ALPHA: view.setAlpha(Math.max(0f, Math.min(1f, x))); break;
            default: view.setRotation(x); break;
        }
    }

    /** Tiến dt giây. Trả về true nếu còn thuộc tính đang chạy. */
    private boolean step(float dt) {
        boolean any = false;
        int n = Math.max(1, (int) Math.ceil(dt / 0.004f));   // chia nhỏ bước để ổn định với lò xo cứng
        float h = dt / n;
        for (int p = 0; p < N; p++) {
            if (!live[p]) continue;
            float x = val[p], vx = vel[p];
            for (int i = 0; i < n; i++) {
                vx += (-k[p] * (x - tgt[p]) - c[p] * vx) * h;
                x += vx * h;
            }
            if (Math.abs(x - tgt[p]) < EPS[p] && Math.abs(vx) < EPS[p] * 40f) {
                x = tgt[p];
                vx = 0f;
                live[p] = false;
            } else {
                any = true;
            }
            val[p] = x;
            vel[p] = vx;
            write(p, x);
        }
        return any;
    }

    private static final Choreographer.FrameCallback TICK = new Choreographer.FrameCallback() {
        @Override public void doFrame(long nanos) {
            float dt = lastNanos == 0 ? 1f / 60f : Math.min(0.032f, (nanos - lastNanos) / 1e9f);
            lastNanos = nanos;
            ArrayList<Runnable> done = null;
            for (int i = ACTIVE.size() - 1; i >= 0; i--) {
                Motion m = ACTIVE.get(i);
                if (!m.step(dt)) {
                    ACTIVE.remove(i);
                    if (m.end != null) {
                        if (done == null) done = new ArrayList<>();
                        done.add(m.end);
                        m.end = null;
                    }
                }
            }
            if (ACTIVE.isEmpty()) {
                looping = false;
                lastNanos = 0;
            } else {
                Choreographer.getInstance().postFrameCallback(this);
            }
            // chạy sau vòng lặp: các việc này có thể tạo thêm lò xo mới
            if (done != null) for (Runnable r : done) r.run();
        }
    };

    private static void kick() {
        if (looping) return;
        looping = true;
        lastNanos = 0;
        Choreographer.getInstance().postFrameCallback(TICK);
    }
}
