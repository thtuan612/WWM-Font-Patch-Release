package local.wwm.fontpatch;

/**
 * Colours for the light and dark themes: strictly classic black and white, OLED black.
 *
 * Design rule: pure monochrome minimalism. Dark mode uses true OLED black (#000000) for maximum
 * contrast and battery savings, paired with crisp white (#FFFFFF) chrome and subtle translucent glass strokes.
 * Emphasis comes from contrast alone. The only colour left is SEAL_RED, reserved for errors and destructive states.
 */
final class Pal {
    boolean dark;
    int text, sub, accent, card, stroke, nav, field, badgeBg, badgeText, onBrush, overlay, bar, dialog, logoTint, swOff;
    /** Dialog colours: flat solid surface, inner field, secondary button, primary button. */
    int mxSurface, mxField, mxBtn, mxPrimary;
    /** Page background gradient (top -> bottom). */
    int bgTop, bgBottom;
    /** Primary button fill, its label colour, and the quiet "tonal" button fill. */
    int primary, onPrimary, tonal;

    static final int SEAL_RED = 0xFFC8332A;

    private Pal() { }

    static Pal of(boolean dark) {
        Pal p = new Pal();
        p.dark = dark;
        if (dark) {
            // Pure OLED Black & Crisp White
            p.bgTop     = 0xFF000000;   // true OLED black
            p.bgBottom  = 0xFF000000;   // true OLED black
            p.text      = 0xFFFFFFFF;   // pure white
            p.sub       = 0xFF999999;   // neutral medium gray
            p.accent    = 0xFFFFFFFF;   // white accent
            p.card      = 0xF2141414;   // dark elevated card
            p.stroke    = 0x22FFFFFF;   // subtle translucent white glass rim
            p.nav       = 0xB3101010;   // translucent dark nav glass
            p.field     = 0x66000000;   // deep dark input background
            p.badgeBg   = 0xFF282828;   // dark neutral badge
            p.badgeText = 0xFFFFFFFF;   // white badge text
            p.onBrush   = 0xFF000000;
            p.overlay   = 0x00000000;
            p.bar       = p.bgBottom;
            p.dialog    = 0xFA161616;   // dark OLED dialog
            p.logoTint  = 0xFFFFFFFF;   // crisp white logo
            p.swOff     = 0x66808080;   // neutral switch track
            p.mxSurface = 0xFF161616;
            p.mxField   = 0xFF242424;
            p.mxBtn     = 0xFF2A2A2A;
            p.mxPrimary = 0xFFFFFFFF;   // white primary button
            p.primary   = 0xFFFFFFFF;   // white primary button
            p.onPrimary = 0xFF000000;   // black text on white
            p.tonal     = 0x24FFFFFF;   // subtle translucent white
        } else {
            // Classic Clean White & Black
            p.bgTop     = 0xFFF6F6F6;
            p.bgBottom  = 0xFFEDEDED;
            p.text      = 0xFF000000;
            p.sub       = 0xFF6A6A6A;
            p.accent    = 0xFF000000;
            p.card      = 0xFFFFFFFF;
            p.stroke    = 0x1A000000;
            p.nav       = 0xB8FFFFFF;
            p.field     = 0x66FFFFFF;
            p.badgeBg   = 0xFFE4E4E4;
            p.badgeText = 0xFF000000;
            p.onBrush   = 0xFFFFFFFF;
            p.overlay   = 0x00000000;
            p.bar       = p.bgBottom;
            p.dialog    = 0xFAFFFFFF;
            p.logoTint  = 0xFF000000;
            p.swOff     = 0x55808080;
            p.mxSurface = 0xFFFFFFFF;
            p.mxField   = 0xFFF2F2F2;
            p.mxBtn     = 0xFFEBEBEB;
            p.mxPrimary = 0xFF000000;
            p.primary   = 0xFF000000;
            p.onPrimary = 0xFFFFFFFF;
            p.tonal     = 0x12000000;
        }
        return p;
    }
}
