package local.wwm.fontpatch;

/**
 * Lò xo giảm chấn một chiều (khối lượng 1), cùng quy ước với spring(dampingRatio, stiffness) của Compose.
 * Dùng cho chuyển động của ô kính trên thanh nav; không phụ thuộc thư viện ngoài.
 */
final class Spring {
    float value;
    float velocity;
    float target;

    private final float k;
    private final float c;
    private final float eps;

    Spring(float start, float dampingRatio, float stiffness, float eps) {
        value = start;
        target = start;
        k = stiffness;
        c = 2f * dampingRatio * (float) Math.sqrt(stiffness);
        this.eps = eps;
    }

    void snap(float v) {
        value = v;
        target = v;
        velocity = 0f;
    }

    /** Tiến dt giây. Trả về true nếu vẫn còn đang chuyển động. */
    boolean step(float dt) {
        if (value == target && velocity == 0f) return false;
        int n = Math.max(1, (int) Math.ceil(dt / 0.004f));   // chia nhỏ bước để ổn định với độ cứng lớn
        float h = dt / n;
        for (int i = 0; i < n; i++) {
            velocity += (-k * (value - target) - c * velocity) * h;
            value += velocity * h;
        }
        if (Math.abs(value - target) < eps && Math.abs(velocity) < eps * 50f) {
            value = target;
            velocity = 0f;
            return false;
        }
        return true;
    }
}
