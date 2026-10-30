package local.wwm.fontpatch;

import android.annotation.TargetApi;
import android.app.Dialog;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Outline;
import android.graphics.Paint;
import android.graphics.Path;
import android.graphics.PixelFormat;
import android.graphics.RecordingCanvas;
import android.graphics.RectF;
import android.graphics.RenderEffect;
import android.graphics.RenderNode;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.graphics.SweepGradient;
import android.graphics.drawable.ColorDrawable;
import android.graphics.drawable.Drawable;
import android.view.View;
import android.view.ViewOutlineProvider;
import android.widget.PopupWindow;

/**
 * Liquid Glass cho Dialog và PopupWindow (Android 13+).
 *
 * Mỗi dialog/popup khi mở sẽ nhận được:
 *   1. Nền scene bị blur + bão hòa + khúc xạ mép (cùng thuật toán với GlassNav).
 *   2. Tint mờ lên trên (giữ cho text dễ đọc).
 *   3. Sheen — ánh sáng từ trên-trái.
 *   4. Viền sáng specular (xanh-tím, cùng hàm GlassFx.tintedLobes).
 *
 * Kỹ thuật: dùng GlassDrawable (Drawable) thay vì View con để không ảnh hưởng
 * bố cục LinearLayout của box dialog (View con MATCH_PARENT sẽ đẩy nội dung ra ngoài).
 *
 * Chỉ hoạt động trên API >= 33; máy cũ hơn giữ nguyên nền đặc thường.
 */
@TargetApi(33)
final class GlassDialog {

    // ---- tham số giao diện của kính dialog ----
    static final float BLUR_DP      = 22f;
    static final float SATURATION   = 1.15f;
    static final float REFRACT_DP   = 18f;
    static final float REFRACT_AMT  = 12f;
    /** Alpha lớp tint phủ: đủ mờ để text dễ đọc, đủ trong để thấy kính phía sau. */
    static final int   TINT_ALPHA_D = 0xCC;   // dark mode
    static final int   TINT_ALPHA_L = 0xBB;   // light mode

    private GlassDialog() { }

    // ================================================================ public API

    /**
     * Áp Liquid Glass vào Dialog: window background trong suốt,
     * nền box được thay bằng GlassDrawable.
     */
    static void applyToDialog(Dialog d, View box, View scene, Pal pal, float dp) {
        if (!Glass.supported()) return;
        if (d.getWindow() == null) return;
        d.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
        attachGlass(box, scene, pal, dp, true);
    }

    /**
     * Áp Liquid Glass vào PopupWindow: xóa nền popup mặc định,
     * nền contentBox được thay bằng GlassDrawable.
     */
    static void applyToPopup(PopupWindow pw, View contentBox, View scene, Pal pal, float dp) {
        if (!Glass.supported()) return;
        pw.setBackgroundDrawable(new ColorDrawable(0));
        attachGlass(contentBox, scene, pal, dp, false);
    }

    /**
     * Nút tròn kính (nút quay lại ở đầu trang). Nút nằm TRONG scene nên không chụp scene làm backdrop
     * (sẽ vẽ đệ quy); chỉ dùng tint + sheen + viền + specular. Trả về false nếu máy không hỗ trợ.
     */
    static boolean applyToButton(View btn, Pal pal, float dp, float sizeDp) {
        if (!Glass.supported()) return false;
        float r = sizeDp / 2f * dp;
        GlassDrawable g = new GlassDrawable(btn, null, pal, dp, false, r);
        btn.setBackground(g);
        btn.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), r);
            }
        });
        btn.setClipToOutline(false);
        btn.setElevation(0f);
        return true;
    }

    // ================================================================ core

    /**
     * Thay nền đặc của box bằng GlassDrawable (không thêm View con nào —
     * tránh phá vỡ LinearLayout bên trong box).
     * Đặt outline + bóng đổ cho box.
     */
    private static void attachGlass(View box, View scene, Pal pal, float dp, boolean isDialog) {
        float cornerPx = 28f * dp;

        // Thay background: GlassDrawable vẽ kính bên dưới nội dung
        box.setBackground(new GlassDrawable(box, scene, pal, dp, isDialog, 28f * dp));

        // Outline cho elevation shadow + clipToOutline nếu cần
        box.setOutlineProvider(new ViewOutlineProvider() {
            @Override public void getOutline(View v, Outline o) {
                o.setRoundRect(0, 0, v.getWidth(), v.getHeight(), cornerPx);
                o.setAlpha(isDialog ? 0.9f : 0.8f);
            }
        });
        box.setClipToOutline(true);
        // Không đặt elevation: cửa sổ Dialog/Popup vừa khít kích thước box nên bóng đổ bị cắt thẳng ở mép
        // cửa sổ và lộ ra thành khối vuông tối sau bốn góc bo. Độ nổi đã có từ viền sáng + lớp dim phía sau.
        box.setElevation(0f);
    }

    // ================================================================ GlassDrawable

    /**
     * Drawable vẽ hiệu ứng Liquid Glass trong bounds của box.
     * Không tham gia đo lường/bố cục nên không ảnh hưởng đến LinearLayout.
     */
    @TargetApi(33)
    private static final class GlassDrawable extends Drawable {

        private final View  host;   // view mang Drawable này (cần getLocationOnScreen)
        private final View  scene;
        private final Pal   pal;
        private final float dp;
        private final boolean isDialog;
        private final float radius;

        // RenderNode để capture + effect scene
        private final RenderNode backdropNode = new RenderNode("glass-dialog-backdrop");
        private final int[]      mePos = new int[2];
        private final int[]      scPos = new int[2];

        // AGSL lens shader
        private RuntimeShader lensShader;

        // Hiệu ứng vẽ
        private final GlassFx     fx;
        private final SweepGradient specGrad;
        private final LinearGradient sheenGrad;

        private final Paint tintPaint   = new Paint();
        private final Paint sheenPaint  = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint strokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

        private final Path  shapePath   = new Path();
        private final Path  strokePath  = new Path();
        private final RectF rect        = new RectF();
        private final Matrix sheenM     = new Matrix();

        GlassDrawable(View host, View scene, Pal pal, float dp, boolean isDialog, float radius) {
            this.radius = radius;
            this.host     = host;
            this.scene    = scene;
            this.pal      = pal;
            this.dp       = dp;
            this.isDialog = isDialog;
            this.fx       = new GlassFx(dp);

            try {
                lensShader = Glass.newLens(false);
            } catch (RuntimeException ignored) {
                lensShader = null;
            }

            // Specular: xanh từ trên-trái (225°), phụ cùng tông xanh ở dưới-phải (45°)
            specGrad = GlassFx.tintedLobes(225f, 0xFFD6E0FF, 45f, 0xFFD6E0FF, 0.5f);

            // Sheen: gradient trắng mờ từ trên-trái xuống
            int sheenTop = pal.dark ? 0x18FFFFFF : 0x28FFFFFF;
            sheenGrad = new LinearGradient(0f, 0f, 1f, 1f,
                    new int[]{sheenTop, sheenTop & 0x00FFFFFF},
                    new float[]{0f, 0.7f}, Shader.TileMode.CLAMP);

            // Tint: màu nền dialog bán trong suốt
            int tintBase  = pal.dialog & 0x00FFFFFF;
            int tintAlpha = pal.dark ? TINT_ALPHA_D : TINT_ALPHA_L;
            tintPaint.setColor(tintBase | (tintAlpha << 24));

            // Viền mảnh
            strokePaint.setStyle(Paint.Style.STROKE);
            strokePaint.setStrokeWidth(Math.max(1f, 1.5f * dp));
            strokePaint.setColor(pal.stroke);
        }

        @Override
        public void draw(Canvas canvas) {
            if (!canvas.isHardwareAccelerated()) {
                // Fallback nền đặc khi không có hardware acceleration
                Paint fb = new Paint();
                fb.setColor(pal.dialog);
                canvas.drawRect(getBounds(), fb);
                return;
            }

            int w = getBounds().width();
            int h = getBounds().height();
            if (w <= 0 || h <= 0) return;

            float r = radius;
            float sw = Math.max(1f, 1.5f * dp);
            float in = sw / 2f;

            // Rebuild path mỗi lần vẽ (bounds có thể đổi khi dialog resize)
            shapePath.reset();
            shapePath.addRoundRect(0f, 0f, w, h, r, r, Path.Direction.CW);
            strokePath.reset();
            strokePath.addRoundRect(in, in, w - in, h - in, r - in, r - in, Path.Direction.CW);
            rect.set(0f, 0f, w, h);

            canvas.save();
            canvas.clipPath(shapePath);

            // 1. Backdrop: scene blurred + refracted
            if (scene != null) drawBackdrop(canvas, w, h, r);

            // 2. Tint mờ
            canvas.drawRect(rect, tintPaint);

            // 3. Sheen (ánh sáng từ trên-trái)
            sheenM.setScale(w, h);
            sheenGrad.setLocalMatrix(sheenM);
            sheenPaint.setShader(sheenGrad);
            canvas.drawRect(rect, sheenPaint);

            // 4. Viền mảnh (bên trong clip)
            canvas.drawPath(strokePath, strokePaint);

            canvas.restore();

            // 5. Specular highlight (ngoài clip để không bị cắt sát mép)
            fx.specular(canvas, strokePath, specGrad,
                    w / 2f, h / 2f, isDialog ? 0.6f : 0.45f);
        }

        private void drawBackdrop(Canvas canvas, int w, int h, float cornerR) {
            try {
                // Dùng getLocationOnScreen vì host và scene có thể thuộc hai Window khác nhau
                // (Dialog/PopupWindow window ≠ Activity window)
                host.getLocationOnScreen(mePos);
                scene.getLocationOnScreen(scPos);

                backdropNode.setPosition(0, 0, w, h);

                // Hiệu ứng: bão hoà → blur → (tuỳ chọn) khúc xạ mép
                RenderEffect lens = null;
                if (lensShader != null) {
                    try {
                        lens = Glass.lens(lensShader, false, w, h, 0f, 0f, cornerR,
                                REFRACT_DP * dp, REFRACT_AMT * dp, 0f, 0f, 1f);
                    } catch (RuntimeException e) {
                        lensShader = null;
                    }
                }
                backdropNode.setRenderEffect(buildEffect(dp, lens));

                // Vẽ phần scene nằm sau host vào node
                RecordingCanvas rc = backdropNode.beginRecording(w, h);
                try {
                    rc.translate(scPos[0] - mePos[0], scPos[1] - mePos[1]);
                    scene.draw(rc);
                } finally {
                    backdropNode.endRecording();
                }

                canvas.drawRenderNode(backdropNode);
            } catch (RuntimeException e) {
                // Fallback: nền đặc
                Paint fb = new Paint();
                fb.setColor(pal.dialog);
                canvas.drawRect(0f, 0f, w, h, fb);
            }
        }

        private static RenderEffect buildEffect(float dp, RenderEffect lens) {
            ColorMatrix cm = new ColorMatrix();
            cm.setSaturation(SATURATION);
            RenderEffect sat = RenderEffect.createColorFilterEffect(
                    new ColorMatrixColorFilter(cm));
            float blurPx = BLUR_DP * dp;
            RenderEffect blur = RenderEffect.createBlurEffect(blurPx, blurPx, sat,
                    Shader.TileMode.CLAMP);
            return lens == null ? blur : RenderEffect.createChainEffect(lens, blur);
        }

        // ---- Drawable boilerplate ----

        @Override public void setAlpha(int alpha) { }
        @Override public void setColorFilter(ColorFilter cf) { }
        @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
    }
}
