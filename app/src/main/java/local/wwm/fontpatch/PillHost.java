package local.wwm.fontpatch;

import android.content.Context;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

/**
 * Khung của thanh nav: chiều cao do hàng nút (con cuối cùng) quyết định.
 * Các lớp trang trí (mờ, màu phủ, ô chọn) chỉ được ép theo kích thước đó,
 * nếu không FrameLayout wrap_content sẽ giãn ra theo các lớp match_parent và chiếm cả màn hình.
 */
final class PillHost extends FrameLayout {
    private final boolean vertical;

    PillHost(Context c, boolean vertical) {
        super(c);
        this.vertical = vertical;
    }

    @Override
    protected void onMeasure(int wSpec, int hSpec) {
        int n = getChildCount();
        View row = getChildAt(n - 1);
        int w;
        int h;
        // kích thước do hàng nút quyết định (mục nav có bề rộng cố định), không vượt chỗ cha cho phép
        row.measure(MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED),
                MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED));
        w = row.getMeasuredWidth();
        h = row.getMeasuredHeight();
        int avail = MeasureSpec.getSize(wSpec);
        if (!vertical && MeasureSpec.getMode(wSpec) != MeasureSpec.UNSPECIFIED && avail > 0) w = Math.min(w, avail);
        setMeasuredDimension(w, h);
        for (int i = 0; i < n - 1; i++) {
            View c = getChildAt(i);
            LayoutParams lp = (LayoutParams) c.getLayoutParams();
            int cw = lp.width == ViewGroup.LayoutParams.MATCH_PARENT ? w : lp.width;
            int ch = lp.height == ViewGroup.LayoutParams.MATCH_PARENT ? h : lp.height;
            c.measure(MeasureSpec.makeMeasureSpec(cw, MeasureSpec.EXACTLY),
                    MeasureSpec.makeMeasureSpec(ch, MeasureSpec.EXACTLY));
        }
    }
}
