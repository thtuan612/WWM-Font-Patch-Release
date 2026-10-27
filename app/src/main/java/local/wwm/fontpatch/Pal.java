package local.wwm.fontpatch;

/** Colours and images for the light (rice paper) and dark (night) themes. */
final class Pal {
    boolean dark;
    int text, sub, accent, card, stroke, nav, field, badgeBg, badgeText, onBrush, overlay, bar, dialog, logoTint, swOff;
    /** Miuix dialog colours: flat solid surface, inner field, secondary button, primary button. */
    int mxSurface, mxField, mxBtn, mxPrimary;
    int bg, banner, brush;

    static final int SEAL_RED = 0xFFC8332A;

    private Pal() { }

    static Pal of(boolean dark) {
        Pal p = new Pal();
        p.dark = dark;
        if (dark) {
            p.text = 0xFFE9EFEF;
            p.sub = 0xFF93A3A5;
            p.accent = 0xFF3FD0B4;
            p.card = 0x99112A2D;
            p.stroke = 0x2E9FD8CC;
            p.nav = 0xB30C1E21;
            p.field = 0x66081618;
            p.badgeBg = 0xFF1F5A52;
            p.badgeText = 0xFFBFF3E6;
            p.onBrush = 0xFFF2F8F6;
            p.overlay = 0x33000000;
            p.bar = 0xFF071012;
            p.dialog = 0xF2102122;
            p.logoTint = 0xFFF1F6F6;
            p.swOff = 0x66A0B4B6;
            p.mxSurface = 0xFF242424;
            p.mxField = 0xFF2F2F2F;
            p.mxBtn = 0xFF383838;
            p.mxPrimary = 0xFF1F7A6C;
            p.bg = R.drawable.bg_dark;
            p.banner = R.drawable.banner_dark;
            p.brush = R.drawable.brush_dark;
        } else {
            p.text = 0xFF24282A;
            p.sub = 0xFF6B7073;
            p.accent = 0xFF16665A;
            p.card = 0xA6FFFFFF;
            p.stroke = 0x1F000000;
            p.nav = 0xB8FFFFFF;
            p.field = 0x66FFFFFF;
            p.badgeBg = 0xFFCDE9DD;
            p.badgeText = 0xFF1D6B54;
            p.onBrush = 0xFFF5F1E8;
            p.overlay = 0x00000000;
            p.bar = 0xFFF3EFE6;
            p.dialog = 0xF7F7F3EA;
            p.logoTint = 0xFF1B1F20;
            p.swOff = 0x55888E90;
            p.mxSurface = 0xFFFFFFFF;
            p.mxField = 0xFFF4F4F4;
            p.mxBtn = 0xFFEDEDED;
            p.mxPrimary = 0xFF16665A;
            p.bg = R.drawable.bg_light;
            p.banner = R.drawable.banner_light;
            p.brush = R.drawable.brush_light;
        }
        return p;
    }
}
