package local.wwm.fontpatch;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/** Đoán nguyên nhân lỗi từ thông báo và đưa ra cách tự xử lý. Thuần Java, không phụ thuộc Android. */
final class Hints {
    static final class Hint {
        final String cause;
        final List<String> fixes;
        Hint(String cause, List<String> fixes) { this.cause = cause; this.fixes = fixes; }
    }

    private Hints() { }

    private static boolean has(String m, String... keys) {
        for (String k : keys) if (m.contains(k)) return true;
        return false;
    }

    private static Hint h(String cause, String... fixes) {
        List<String> l = new ArrayList<>();
        for (String f : fixes) l.add(f);
        return new Hint(cause, l);
    }

    /**
     * @param message  thông báo lỗi (hoặc dòng log cuối khi không bắt đầu được)
     * @param useRoot  đang chọn Root (true) hay Shizuku (false)
     * @param notStarted true khi chưa chạy được (thiếu quyền, chưa chọn được phương thức)
     */
    static Hint diagnose(String message, boolean useRoot, boolean notStarted) {
        String m = message == null ? "" : message.toLowerCase(Locale.ROOT);

        if (has(m, "đã xin quyền shizuku", "requested shizuku permission")) {
            return h(Lang.t("Shizuku đang chờ bạn cấp quyền cho app.", "Shizuku is waiting for you to grant permission to this app."),
                    Lang.t("Bấm \"Cho phép\" ở hộp thoại Shizuku vừa hiện ra.", "Tap \"Allow\" in the Shizuku dialog that just appeared."),
                    Lang.t("Sau đó quay lại và bấm thao tác lại một lần nữa.", "Then come back and run the action again."));
        }
        if (has(m, "shizuku chưa chạy", "shizuku is not running")) {
            return h(Lang.t("Dịch vụ Shizuku chưa được khởi động.", "The Shizuku service is not running."),
                    Lang.t("Mở app Shizuku và bấm Khởi động (qua Wireless debugging hoặc adb).", "Open the Shizuku app and start it (via Wireless debugging or adb)."),
                    Lang.t("Sau khi khởi động lại máy, cần khởi động Shizuku lần nữa.", "After a reboot, Shizuku has to be started again."),
                    Lang.t("Hoặc chuyển sang Root ở tab Nâng cao nếu máy đã root.", "Or switch to Root in the Advanced tab if the device is rooted."));
        }
        if (has(m, "shizuku quá cũ", "shizuku is too old")) {
            return h(Lang.t("Phiên bản Shizuku quá cũ.", "The Shizuku version is too old."),
                    Lang.t("Cập nhật app Shizuku lên bản mới nhất rồi thử lại.", "Update the Shizuku app to the latest version and try again."));
        }
        if (has(m, "shizuku lỗi", "shizuku error")) {
            return h(Lang.t("Không giao tiếp được với Shizuku.", "Could not talk to Shizuku."),
                    Lang.t("Khởi động lại dịch vụ Shizuku rồi thử lại.", "Restart the Shizuku service and try again."),
                    Lang.t("Kiểm tra app này còn được cấp quyền trong Shizuku.", "Check that this app is still authorised in Shizuku."));
        }
        if (has(m, "không dùng được root", "root is not available", "su quá thời gian", "su timed out")) {
            return h(Lang.t("Không lấy được quyền root (su bị từ chối hoặc không có).", "Root access could not be obtained (su was denied or is missing)."),
                    Lang.t("Mở app quản lý root (Magisk/KernelSU...) và cấp quyền cho WWM Font Patch.", "Open your root manager (Magisk/KernelSU...) and grant WWM Font Patch access."),
                    Lang.t("Nếu máy không root, chuyển sang Shizuku ở tab Nâng cao.", "If the device is not rooted, switch to Shizuku in the Advanced tab."));
        }
        if (has(m, "bản trung quốc không có", "chinese version has no provider")) {
            return h(Lang.t("Bản game Trung Quốc không có đường Provider.", "The Chinese game version has no Provider route."),
                    Lang.t("Vào tab Nâng cao, chọn Root hoặc Shizuku và đảm bảo cách đó hoạt động.", "In the Advanced tab choose Root or Shizuku and make sure it works."));
        }
        if (has(m, "chưa chọn font", "no font selected")) {
            return h(Lang.t("Chưa có font nào được chọn.", "No font has been selected."),
                    Lang.t("Sang tab Font, chọn font cho ít nhất một vị trí rồi cài lại.", "Go to the Font tab, pick a font for at least one slot and install again."));
        }
        if (has(m, "chưa có bản sao lưu", "no backup")) {
            return h(Lang.t("Chưa có bản sao lưu db_mp để khôi phục.", "There is no db_mp backup to restore."),
                    Lang.t("Bản sao lưu chỉ được tạo khi cài hoặc gỡ patch thành công ít nhất một lần.", "A backup is only created after at least one install or removal."),
                    Lang.t("Nếu game lỗi, hãy xoá dữ liệu game rồi để game tải lại.", "If the game misbehaves, clear the game's data and let it download again."));
        }
        if (has(m, "không có hàng", "has no row")) {
            return h(Lang.t("Hotfix id bạn nhập không tồn tại trong db_mp.", "The hotfix id you entered does not exist in db_mp."),
                    Lang.t("Xoá ô Hotfix id ở tab Nâng cao để app tự chọn.", "Clear the Hotfix id field in the Advanced tab so the app picks one itself."),
                    Lang.t("Hoặc bấm \"Xem hotfix\" để xem id hợp lệ.", "Or tap \"View hotfix\" to see the valid ids."));
        }
        if (has(m, "không tìm thấy hotfix nào", "no usable hotfix")) {
            return h(Lang.t("db_mp chưa có hotfix dùng được (game chưa tải dữ liệu cập nhật).", "db_mp has no usable hotfix yet (the game has not downloaded its update data)."),
                    Lang.t("Mở game, đợi vào tới màn hình chính, rồi thoát hẳn game.", "Open the game, wait until the main screen, then close the game completely."),
                    Lang.t("Quay lại app và cài patch lần nữa.", "Come back to the app and install the patch again."));
        }
        if (has(m, "không thấy patch", "patch was not found")) {
            return h(Lang.t("Ghi xong nhưng đọc lại không thấy patch — hàng hotfix có thể không nhận bản sửa.", "Written, but the patch was not found on read-back — the hotfix row may not accept the edit."),
                    Lang.t("Thử nhập một Hotfix id khác ở tab Nâng cao (xem danh sách ở \"Xem hotfix\").", "Try another Hotfix id in the Advanced tab (see the list under \"View hotfix\")."),
                    Lang.t("Nếu vẫn lỗi, bấm Khôi phục rồi gửi log cho tác giả.", "If it still fails, tap Restore and send the log to the author."));
        }
        if (has(m, "malformed", "not a database", "file is encrypted", "corrupt")) {
            return h(Lang.t("db_mp bị hỏng hoặc chép dở (game có thể đang ghi vào file).", "db_mp is damaged or was copied halfway (the game may have been writing to it)."),
                    Lang.t("Thoát hẳn game rồi thử lại.", "Close the game completely and try again."),
                    Lang.t("Bấm Khôi phục để trả lại bản sao lưu gần nhất.", "Tap Restore to put back the latest backup."),
                    Lang.t("Nếu game vẫn lỗi, xoá dữ liệu game để tải lại.", "If the game still fails, clear the game's data to re-download."));
        }
        if (has(m, "locked", "busy")) {
            return h(Lang.t("db_mp đang bị game khoá.", "db_mp is locked by the game."),
                    Lang.t("Tắt hẳn game (vuốt khỏi đa nhiệm) rồi thử lại.", "Close the game completely (swipe it away from recents) and try again."),
                    Lang.t("Bật \"Tự tắt game\" ở tab Nâng cao.", "Turn on \"Auto-stop game\" in the Advanced tab."));
        }
        if (has(m, "thiếu dữ liệu", "missing data", "kích thước", "size mismatch")) {
            return h(Lang.t("Chép file chưa trọn vẹn (kích thước sau khi chép không khớp).", "The copy was incomplete (size after copying does not match)."),
                    Lang.t("Kiểm tra bộ nhớ trống của máy rồi thử lại.", "Check the free storage space and try again."),
                    Lang.t("Thoát hẳn game để nó không ghi vào file trong lúc chép.", "Close the game completely so it does not write to the file meanwhile."));
        }
        if (has(m, "không đọc được", "cannot read", "filenotfound", "no such file", "not found")) {
            return h(Lang.t("Không đọc được dữ liệu game — thư mục dữ liệu chưa tồn tại hoặc không có quyền.", "Cannot read the game data — the folder does not exist yet or access is denied."),
                    Lang.t("Mở game ít nhất một lần và đợi nó tải dữ liệu xong.", "Open the game at least once and let it finish downloading."),
                    Lang.t("Kiểm tra bạn đã chọn đúng phiên bản game (Global/VN/CN...).", "Check that you picked the right game version (Global/VN/CN...)."),
                    useRoot ? Lang.t("Chắc chắn quyền root đang được cấp cho app.", "Make sure root is granted to the app.")
                            : Lang.t("Chắc chắn Shizuku đang chạy và đã cấp quyền cho app.", "Make sure Shizuku is running and has granted this app access."));
        }
        if (has(m, "không ghi được", "cannot write", "permission denied", "read-only", "no space")) {
            return h(Lang.t("Không ghi được vào thư mục game.", "Cannot write to the game folder."),
                    Lang.t("Kiểm tra bộ nhớ trống của máy.", "Check the free storage space."),
                    useRoot ? Lang.t("Chắc chắn quyền root còn hiệu lực.", "Make sure root access is still valid.")
                            : Lang.t("Khởi động lại Shizuku rồi thử lại.", "Restart Shizuku and try again."),
                    Lang.t("Thoát hẳn game rồi thử lại.", "Close the game completely and try again."));
        }
        if (has(m, "quá thời gian", "timed out", "timeout")) {
            return h(Lang.t("Lệnh chạy quá lâu nên bị huỷ.", "The command took too long and was cancelled."),
                    Lang.t("Thử lại; nếu lặp lại, khởi động lại Shizuku/root hoặc khởi động lại máy.", "Try again; if it repeats, restart Shizuku/root or reboot the device."));
        }
        if (notStarted) {
            return h(Lang.t("Chưa thể bắt đầu vì chưa có phương thức ghi dùng được.", "Could not start because no usable write method is available."),
                    Lang.t("Vào tab Nâng cao, chọn Root hoặc Shizuku và đảm bảo nó đã sẵn sàng.", "In the Advanced tab choose Root or Shizuku and make sure it is ready."));
        }
        return h(Lang.t("Lỗi chưa được nhận diện.", "Unrecognised error."),
                Lang.t("Thoát hẳn game rồi thử lại.", "Close the game completely and try again."),
                Lang.t("Bấm \"Sao chép log\" và gửi cho tác giả (Discord @lmtuan612).", "Tap \"Copy log\" and send it to the author (Discord @lmtuan612)."));
    }
}
