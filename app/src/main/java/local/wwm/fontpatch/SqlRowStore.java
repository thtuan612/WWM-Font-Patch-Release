package local.wwm.fontpatch;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteStatement;
import java.util.ArrayList;
import java.util.List;

/** RowStore backed by the game's SQLite file (table localdb(id TEXT, content BLOB)). */
final class SqlRowStore implements RowStore {
    private static final int CHUNK = 1024 * 1024;   // stay under the 2 MB CursorWindow limit
    private final SQLiteDatabase db;

    SqlRowStore(SQLiteDatabase db) { this.db = db; }

    @Override
    public List<String> hotfixIds() {
        List<String> ids = new ArrayList<>();
        try (Cursor c = db.rawQuery("SELECT id FROM localdb WHERE id LIKE 'hotfix\\_%' ESCAPE '\\'", null)) {
            while (c.moveToNext()) ids.add(c.getString(0));
        }
        return ids;
    }

    @Override
    public byte[] readRaw(String id) {
        long len = 0;
        try (Cursor c = db.rawQuery("SELECT length(content) FROM localdb WHERE id = ?", new String[]{id})) {
            if (c.moveToFirst()) len = c.getLong(0);
        }
        if (len <= 0) return new byte[0];
        byte[] out = new byte[(int) len];
        int off = 0;
        while (off < len) {
            try (Cursor c = db.rawQuery("SELECT substr(content, ?, ?) FROM localdb WHERE id = ?",
                    new String[]{String.valueOf(off + 1), String.valueOf(CHUNK), id})) {
                if (!c.moveToFirst()) break;
                byte[] part = c.getBlob(0);
                if (part == null || part.length == 0) break;
                int n = Math.min(part.length, out.length - off);
                System.arraycopy(part, 0, out, off, n);
                off += n;
            }
        }
        if (off != len) throw new IllegalStateException(
                Lang.t("Đọc dở hàng " + id + ": " + off + "/" + len, "Partial read of row " + id + ": " + off + "/" + len));
        return out;
    }

    @Override
    public void writeRaw(String id, byte[] raw) {
        SQLiteStatement st = db.compileStatement("UPDATE localdb SET content=? WHERE id=?");
        try {
            st.bindBlob(1, raw);
            st.bindString(2, id);
            st.execute();
        } finally {
            st.close();
        }
    }
}
