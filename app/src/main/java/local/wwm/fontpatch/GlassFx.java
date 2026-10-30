package local.wwm.fontpatch;

import android.annotation.TargetApi;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.Shader;
import android.graphics.SweepGradient;

/**
 * Phần vẽ viền dùng chung cho GlassNav và GlassSeg (Android 13+): viền sáng specular quanh hình,
 * và bóng trong ở mép trên (InnerShadow của AndroidLiquidGlass). Mỗi view giữ một thể hiện riêng.
 */
@TargetApi(33)
final class GlassFx {
    private final float dp;
    private final Paint spec = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint shadowPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Matrix m = new Matrix();
    private final Path moved = new Path();
    private final Path crescent = new Path();
    private final RenderNode shadowNode = new RenderNode("glass-inner-shadow");
    private final Paint reflPaint = new Paint();
    private final Matrix reflM = new Matrix();
    /** Phản chiếu xanh chỉ ở góc đón sáng (tan hết trước giữa mặt kính) trên đơn vị vuông (0,0)-(1,1); co giãn theo hình chữ nhật khi vẽ. */
    private final LinearGradient refl = new LinearGradient(0f, 0f, 1f, 1f,
            new int[]{0xFF8AA6FF, 0x558AA6FF, 0x008AA6FF}, new float[]{0f, 0.28f, 0.65f}, Shader.TileMode.CLAMP);

    GlassFx(float dp) {
        this.dp = dp;
        float sw = Math.max(1f, 1.5f * dp);               // nét 1.5dp: viền kính lồi rõ hơn
        spec.setStyle(Paint.Style.STROKE);
        spec.setStrokeWidth(sw);
        base.setStyle(Paint.Style.STROKE);
        base.setStrokeWidth(sw);
        base.setColor(Color.WHITE);
        shadowPaint.setStyle(Paint.Style.FILL);
        shadowPaint.setColor(Color.BLACK);
    }

    /** Bề dày nét viền (px). */
    float strokeWidth() { return Math.max(1f, 1.5f * dp); }

    /** Vẽ viền sáng theo path, ánh sáng xoay quanh (cx, cy) theo gradient g; alpha 0..1. */
    void specular(Canvas c, Path path, SweepGradient g, float cx, float cy, float alpha) {
        float a = clamp(alpha, 0f, 1f);
        m.setTranslate(cx, cy);
        g.setLocalMatrix(m);
        spec.setShader(g);
        spec.setAlpha(Math.round(255 * a));
        c.drawPath(path, spec);
        base.setAlpha(Math.round(255 * 0.22f * a));
        c.drawPath(path, base);
    }

    /**
     * Vệt tối mờ ở mép trên trong lòng hình: lấy hình trừ đi chính nó dịch xuống, làm mờ, rồi cắt theo hình.
     * vw/vh là kích thước view đang vẽ; p là mức nhấn 0..1 (quyết định độ sâu và độ đậm).
     */
    void innerShadow(Canvas c, int vw, int vh, Path shape, float p) {
        float r8 = 8f * dp * p;
        if (r8 < 0.5f) return;
        moved.set(shape);
        moved.offset(0f, r8);
        crescent.reset();
        crescent.op(shape, moved, Path.Op.DIFFERENCE);
        shadowNode.setPosition(0, 0, vw, vh);
        RecordingCanvas rc = shadowNode.beginRecording(vw, vh);
        try {
            shadowPaint.setAlpha(Math.round(255 * 0.14f * p));
            rc.drawPath(crescent, shadowPaint);
        } finally {
            shadowNode.endRecording();
        }
        shadowNode.setRenderEffect(RenderEffect.createBlurEffect(r8, r8, Shader.TileMode.DECAL));
        c.save();
        c.clipPath(shape);
        c.drawRenderNode(shadowNode);
        c.restore();
    }

    /** Phản chiếu xanh phủ mặt kính, đậm ở góc đón sáng (trên-trái) và tan dần về phía dưới-phải. Vẽ trong vùng đã clip. */
    void reflection(Canvas c, RectF r, float alpha) {
        float a = clamp(alpha, 0f, 1f);
        if (a <= 0.002f) return;
        reflM.setScale(r.width(), r.height());
        reflM.postTranslate(r.left, r.top);
        refl.setLocalMatrix(reflM);
        reflPaint.setShader(refl);
        reflPaint.setAlpha(Math.round(255 * a));
        c.drawRect(r, reflPaint);
    }

    /**
     * Như lobes() nhưng mỗi đỉnh sáng có màu riêng (c1 cho đỉnh chính, c2 cho đỉnh phụ) và chuyển màu mượt giữa hai đỉnh:
     * viền kính ngả màu c1 ở phía đón sáng và c2 ở phía phản chiếu (hiện cả hai cùng tông xanh nhạt, không còn tím).
     */
    static SweepGradient tintedLobes(float a1, int c1, float a2, int c2, float s2) {
        int n = 72;
        int[] col = new int[n + 1];
        float[] pos = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float ang = i * 360f / n;
            float v1 = lobe(ang - a1);
            float v2 = s2 * lobe(ang - a2);
            float sum = v1 + v2;
            float t = sum > 1e-4f ? v2 / sum : 0f;
            int r = Math.round(Color.red(c1) + (Color.red(c2) - Color.red(c1)) * t);
            int g = Math.round(Color.green(c1) + (Color.green(c2) - Color.green(c1)) * t);
            int b = Math.round(Color.blue(c1) + (Color.blue(c2) - Color.blue(c1)) * t);
            col[i] = Color.argb(Math.round(255 * Math.min(1f, sum)), r, g, b);
            pos[i] = i / (float) n;
        }
        return new SweepGradient(0f, 0f, col, pos);
    }

    /** Dải sáng trắng quanh viền: đỉnh chính tại góc a1 (độ, theo chiều kim đồng hồ từ 3 giờ), đỉnh phụ tại a2 với cường độ s2. */
    static SweepGradient lobes(float a1, float a2, float s2) {
        int n = 72;
        int[] col = new int[n + 1];
        float[] pos = new float[n + 1];
        for (int i = 0; i <= n; i++) {
            float ang = i * 360f / n;
            float v = lobe(ang - a1) + s2 * lobe(ang - a2);
            col[i] = Color.argb(Math.round(255 * Math.min(1f, v)), 255, 255, 255);
            pos[i] = i / (float) n;
        }
        return new SweepGradient(0f, 0f, col, pos);
    }

    private static float lobe(float deg) {
        float d = ((deg % 360f) + 540f) % 360f - 180f;     // về khoảng [-180, 180)
        float x = d / 38f;
        return (float) Math.exp(-x * x);
    }

    static float clamp(float v, float lo, float hi) {
        return v < lo ? lo : (v > hi ? hi : v);
    }
}
