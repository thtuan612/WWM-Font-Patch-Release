package local.wwm.fontpatch;

/** Ngôn ngữ hiện tại cho các lớp không phải Activity (Patcher, Backend, Hints). MainActivity cập nhật giá trị này. */
final class Lang {
    static volatile boolean en;

    private Lang() { }

    static String t(String vi, String e) { return en ? e : vi; }
}
