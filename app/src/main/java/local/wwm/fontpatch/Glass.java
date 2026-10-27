package local.wwm.fontpatch;

import android.annotation.TargetApi;
import android.content.SharedPreferences;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.RenderEffect;
import android.graphics.RuntimeShader;
import android.graphics.Shader;
import android.os.Build;

/**
 * Liquid glass cho thanh nav: tham số, shader AGSL và các hàm dựng RenderEffect.
 *
 * Thuật toán khúc xạ (SDF hình chữ nhật bo góc, bản đồ vòng tròn, tán sắc 7 mẫu) được chuyển từ
 * Kotlin/Compose sang Java + View, dựa trên Kyant0/AndroidLiquidGlass (Apache License 2.0,
 * https://github.com/Kyant0/AndroidLiquidGlass) thông qua bản chuyển thể trong KernelSU Manager
 * (https://github.com/tiann/KernelSU, ui/component/liquid/Lens.kt).
 * Giấy phép Apache 2.0: https://www.apache.org/licenses/LICENSE-2.0
 *
 * Chỉ chạy trên Android 13+ (API 33) vì cần RuntimeShader (AGSL). Máy cũ hơn dùng thanh nav cũ.
 */
final class Glass {
    /** Công tắc trong phần Giao diện (mặc định bật). */
    static final String PREF = "glass";
    /** Tự đặt khi liquid glass gặp lỗi lúc vẽ, để lần mở sau quay về thanh nav cũ. */
    static final String PREF_BROKEN = "glass_broken";

    // ---- tham số hình ảnh (chỉnh ở đây) ----
    /** Độ mờ nền phía sau kính (dp). KernelSU dùng 4; để cao hơn một chút cho chữ nav dễ đọc trên nền có chữ. */
    static final float BLUR_DP = 8f;
    /** Độ bão hòa màu nền phía sau kính (1 = giữ nguyên). */
    static final float SATURATION = 1.5f;
    /** Alpha lớp phủ màu của thanh (0..255). */
    static final int TINT_ALPHA = 0x66;
    /** Khúc xạ mép của cả thanh (dp): bề dày dải khúc xạ và độ lệch tối đa. */
    static final float BAR_REFRACTION_DP = 24f;
    /** Khúc xạ của ô chọn khi đang nhấn/kéo (dp). */
    static final float PILL_REFRACTION_HEIGHT_DP = 10f;
    static final float PILL_REFRACTION_AMOUNT_DP = 14f;
    /** Độ tán sắc của ô chọn khi đang nhấn/kéo. */
    static final float PILL_CHROMA = 0.5f;
    /** Hệ số phóng ô chọn khi nhấn (78dp / 56dp như bản gốc). */
    static final float PRESSED_SCALE = 78f / 56f;
    /** Thanh chọn ngang (FPS, giao diện): ô nhỏ hơn thanh nav nên phóng và khúc xạ nhẹ tay hơn. */
    static final float SEG_PRESSED_SCALE = 1.3f;
    static final float SEG_LENS_SCALE = 0.8f;

    private Glass() { }

    static boolean supported() {
        return Build.VERSION.SDK_INT >= 33;
    }

    static boolean enabled(SharedPreferences sp) {
        return supported() && sp.getBoolean(PREF, true) && !sp.getBoolean(PREF_BROKEN, false);
    }

    // ---------------------------------------------------------------- AGSL

    private static final String SDF = ""
            + "float2 nrm(float2 v) {\n"
            + "    float l = length(v);\n"
            + "    return l > 0.00001 ? v / l : float2(0.0);\n"
            + "}\n"
            + "\n"
            + "float radiusAt(float2 coord, float4 radii) {\n"
            + "    if (coord.x >= 0.0) {\n"
            + "        if (coord.y <= 0.0) return radii.y;\n"
            + "        else return radii.z;\n"
            + "    } else {\n"
            + "        if (coord.y <= 0.0) return radii.x;\n"
            + "        else return radii.w;\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "float sdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "    float outside = length(max(cornerCoord, 0.0)) - radius;\n"
            + "    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n"
            + "    return outside + inside;\n"
            + "}\n"
            + "\n"
            + "float2 gradSdRoundedRect(float2 coord, float2 halfSize, float radius) {\n"
            + "    float2 cornerCoord = abs(coord) - (halfSize - float2(radius));\n"
            + "    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n"
            + "        return sign(coord) * nrm(max(cornerCoord, 0.0));\n"
            + "    } else {\n"
            + "        float gradX = step(cornerCoord.y, cornerCoord.x);\n"
            + "        return sign(coord) * float2(gradX, 1.0 - gradX);\n"
            + "    }\n"
            + "}\n"
            + "\n"
            + "float circleMap(float x) {\n"
            + "    return 1.0 - sqrt(max(1.0 - x * x, 0.0));\n"
            + "}\n";

    private static final String UNIFORMS = ""
            + "uniform shader content;\n"
            + "uniform float2 size;\n"
            + "uniform float2 offset;\n"
            + "uniform float4 cornerRadii;\n"
            + "uniform float refractionHeight;\n"
            + "uniform float refractionAmount;\n"
            + "uniform float depthEffect;\n";

    /**
     * Phần chung: tính toạ độ đã khúc xạ (refractedCoord) hoặc trả về nguyên bản nếu ở sâu bên trong.
     * Điểm nằm ngoài hình (cov = 0) phải trả về trong suốt: nếu không, nội dung bên trong bị đẩy lan ra ngoài
     * thành một vành (và vành tán sắc nhiều màu) quanh ô kính. cov chỉ làm mềm 1px ở mép hình.
     */
    private static final String REFRACT = ""
            + "    float2 halfSize = size * 0.5;\n"
            + "    float2 centeredCoord = (coord + offset) - halfSize;\n"
            + "    float radius = radiusAt(centeredCoord, cornerRadii);\n"
            + "    float sd = sdRoundedRect(centeredCoord, halfSize, radius);\n"
            + "    float cov = clamp(0.5 - sd, 0.0, 1.0);\n"
            + "    if (cov <= 0.0) {\n"
            + "        return half4(0.0);\n"
            + "    }\n"
            + "    if (-sd >= refractionHeight) {\n"
            + "        return content.eval(coord) * half(cov);\n"
            + "    }\n"
            + "    sd = min(sd, 0.0);\n"
            + "    float d = circleMap(1.0 - -sd / refractionHeight) * refractionAmount;\n"
            + "    float gradRadius = min(radius * 1.5, min(halfSize.x, halfSize.y));\n"
            + "    float2 grad = nrm(gradSdRoundedRect(centeredCoord, halfSize, gradRadius)"
            + " + depthEffect * nrm(centeredCoord));\n"
            + "    float2 refractedCoord = coord + d * grad;\n";

    private static final String LENS = UNIFORMS + "\n" + SDF + "\n"
            + "half4 main(float2 coord) {\n"
            + REFRACT
            + "    return content.eval(refractedCoord) * half(cov);\n"
            + "}\n";

    private static final String LENS_DISPERSION = UNIFORMS
            + "uniform float chromaticAberration;\n\n" + SDF + "\n"
            + "half4 main(float2 coord) {\n"
            + REFRACT
            + "    float dispersionIntensity = chromaticAberration"
            + " * ((centeredCoord.x * centeredCoord.y) / (halfSize.x * halfSize.y));\n"
            + "    float2 dispersedCoord = d * grad * dispersionIntensity;\n"
            + "\n"
            + "    half4 color = half4(0.0);\n"
            + "\n"
            + "    half4 red = content.eval(refractedCoord + dispersedCoord);\n"
            + "    color.r += red.r / 3.5;\n"
            + "    color.a += red.a / 7.0;\n"
            + "\n"
            + "    half4 orange = content.eval(refractedCoord + dispersedCoord * (2.0 / 3.0));\n"
            + "    color.r += orange.r / 3.5;\n"
            + "    color.g += orange.g / 7.0;\n"
            + "    color.a += orange.a / 7.0;\n"
            + "\n"
            + "    half4 yellow = content.eval(refractedCoord + dispersedCoord * (1.0 / 3.0));\n"
            + "    color.r += yellow.r / 3.5;\n"
            + "    color.g += yellow.g / 3.5;\n"
            + "    color.a += yellow.a / 7.0;\n"
            + "\n"
            + "    half4 green = content.eval(refractedCoord);\n"
            + "    color.g += green.g / 3.5;\n"
            + "    color.a += green.a / 7.0;\n"
            + "\n"
            + "    half4 cyan = content.eval(refractedCoord - dispersedCoord * (1.0 / 3.0));\n"
            + "    color.g += cyan.g / 3.5;\n"
            + "    color.b += cyan.b / 3.0;\n"
            + "    color.a += cyan.a / 7.0;\n"
            + "\n"
            + "    half4 blue = content.eval(refractedCoord - dispersedCoord * (2.0 / 3.0));\n"
            + "    color.b += blue.b / 3.0;\n"
            + "    color.a += blue.a / 7.0;\n"
            + "\n"
            + "    half4 purple = content.eval(refractedCoord - dispersedCoord);\n"
            + "    color.r += purple.r / 7.0;\n"
            + "    color.b += purple.b / 3.0;\n"
            + "    color.a += purple.a / 7.0;\n"
            + "\n"
            + "    return color * half(cov);\n"
            + "}\n";

    // ---------------------------------------------------------------- effects

    /** Biên dịch shader; ném IllegalArgumentException nếu AGSL lỗi (người gọi tự bắt và hạ cấp). */
    @TargetApi(33)
    static RuntimeShader newLens(boolean dispersion) {
        return new RuntimeShader(dispersion ? LENS_DISPERSION : LENS);
    }

    /**
     * Đặt uniform rồi dựng RenderEffect khúc xạ cho một hình chữ nhật bo góc đồng đều.
     * (left, top) là góc trên trái của hình trong toạ độ của view/node chứa hiệu ứng.
     * Phải dựng lại effect mỗi lần đổi uniform.
     */
    @TargetApi(33)
    static RenderEffect lens(RuntimeShader s, boolean dispersion, float w, float h, float left, float top,
                             float radius, float refractionHeight, float refractionAmount,
                             float depthEffect, float chroma) {
        float r = Math.min(radius, Math.min(w, h) / 2f);
        s.setFloatUniform("size", w, h);
        s.setFloatUniform("offset", -left, -top);
        s.setFloatUniform("cornerRadii", new float[]{r, r, r, r});
        s.setFloatUniform("refractionHeight", refractionHeight);
        s.setFloatUniform("refractionAmount", -refractionAmount);   // âm: lấy mẫu từ phía trong, mép cong như thấu kính
        s.setFloatUniform("depthEffect", depthEffect);
        if (dispersion) s.setFloatUniform("chromaticAberration", chroma);
        return RenderEffect.createRuntimeShaderEffect(s, "content");
    }

    /** Tăng bão hòa → làm mờ → (tuỳ chọn) khúc xạ. Thứ tự giống vibrancy() → blur() → lens() của bản gốc. */
    @TargetApi(33)
    static RenderEffect backdrop(float blurPx, RenderEffect lens) {
        ColorMatrix cm = new ColorMatrix();
        cm.setSaturation(SATURATION);
        RenderEffect sat = RenderEffect.createColorFilterEffect(new ColorMatrixColorFilter(cm));
        RenderEffect blur = RenderEffect.createBlurEffect(blurPx, blurPx, sat, Shader.TileMode.CLAMP);
        return lens == null ? blur : RenderEffect.createChainEffect(lens, blur);
    }
}
