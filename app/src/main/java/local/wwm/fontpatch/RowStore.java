package local.wwm.fontpatch;

import java.util.List;

/** Minimal view of the game's localdb(id, content) table so the logic can be tested without Android. */
public interface RowStore {
    /** Every id that starts with "hotfix_" (including hotfix_metaglobal). */
    List<String> hotfixIds() throws Exception;

    /** Raw (still XOR-encrypted) content bytes, or an empty array when the row is missing. */
    byte[] readRaw(String id) throws Exception;

    void writeRaw(String id, byte[] raw) throws Exception;
}
