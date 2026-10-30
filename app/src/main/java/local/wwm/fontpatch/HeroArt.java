package local.wwm.fontpatch;

import android.graphics.Bitmap;
import android.graphics.BitmapShader;
import android.graphics.Canvas;
import android.graphics.ColorFilter;
import android.graphics.LinearGradient;
import android.graphics.Matrix;
import android.graphics.Paint;
import android.graphics.PixelFormat;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.Drawable;

/** Nền thẻ game: ảnh minh hoạ bo góc (cắt kiểu center-crop), mờ dần sang màu thẻ ở bên trái để chữ dễ đọc, kèm viền mảnh. */
final class HeroArt extends Drawable {
    private final Paint img = new Paint(Paint.ANTI_ALIAS_FLAG | Paint.FILTER_BITMAP_FLAG);
    private final Paint scrim = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint base = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint stroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final BitmapShader shader;
    private final Matrix m = new Matrix();
    private final RectF r = new RectF();
    private final int bw, bh, cardColor;
    private final float radius, sw;

    HeroArt(Bitmap bmp, int cardColor, int strokeColor, float radius, float strokeW) {
        this.cardColor = cardColor;
        this.radius = radius;
        this.sw = strokeW;
        bw = bmp.getWidth();
        bh = bmp.getHeight();
        shader = new BitmapShader(bmp, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP);
        img.setShader(shader);
        base.setColor(cardColor);
        stroke.setStyle(Paint.Style.STROKE);
        stroke.setStrokeWidth(strokeW);
        stroke.setColor(strokeColor);
    }

    @Override protected void onBoundsChange(android.graphics.Rect b) {
        float w = b.width(), h = b.height();
        if (w <= 0 || h <= 0) return;
        float s = Math.max(w / bw, h / bh);
        m.setScale(s, s);
        m.postTranslate((w - bw * s) / 2f, (h - bh * s) / 2f);
        shader.setLocalMatrix(m);
        int solid = cardColor | 0xFF000000;
        int clear = cardColor & 0x00FFFFFF;
        scrim.setShader(new LinearGradient(0, 0, w, 0,
                new int[]{solid, (solid & 0x00FFFFFF) | 0xF0000000, (clear | 0x66000000), clear},
                new float[]{0f, 0.38f, 0.66f, 1f}, Shader.TileMode.CLAMP));
    }

    @Override public void draw(Canvas c) {
        r.set(getBounds());
        c.drawRoundRect(r, radius, radius, base);
        c.drawRoundRect(r, radius, radius, img);
        c.drawRoundRect(r, radius, radius, scrim);
        float in = sw / 2f;
        r.inset(in, in);
        c.drawRoundRect(r, radius - in, radius - in, stroke);
    }

    @Override public void setAlpha(int a) { }
    @Override public void setColorFilter(ColorFilter cf) { }
    @Override public int getOpacity() { return PixelFormat.TRANSLUCENT; }
}
