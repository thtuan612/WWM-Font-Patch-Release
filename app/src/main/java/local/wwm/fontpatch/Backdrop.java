package local.wwm.fontpatch;

import android.content.Context;
import android.graphics.Canvas;
import android.view.View;

/** Vẽ lại phần scene nằm sau nó (dùng làm nền mờ cho thanh nav). */
final class Backdrop extends View {
    View src;

    Backdrop(Context c) { super(c); }

    @Override
    protected void onDraw(Canvas cv) {
        if (src == null) return;
        int[] mine = new int[2];
        int[] theirs = new int[2];
        getLocationInWindow(mine);
        src.getLocationInWindow(theirs);
        cv.save();
        cv.translate(theirs[0] - mine[0], theirs[1] - mine[1]);
        src.draw(cv);
        cv.restore();
    }
}
