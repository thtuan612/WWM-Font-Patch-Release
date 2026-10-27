package local.wwm.fontpatch;

import java.io.File;

/** A way to read/write files under the game's /Android/data/<pkg>/files/ directory. */
public interface Backend {
    String name();

    /** Folder (readable+writable by this app AND by the backend) used to stage files. */
    File stageDir() throws Exception;

    /** Best effort: stop the game so it isn't holding db_mp. May be a no-op. */
    void forceStop(String pkg) throws Exception;

    /** Whether the game process is running. null = unknown (backend cannot tell). */
    default Boolean isRunning(String pkg) throws Exception { return null; }

    boolean exists(String pkg, String rel) throws Exception;

    /** Copy game file {@code rel} (relative to files/) into local file {@code dst}. */
    void pull(String pkg, String rel, File dst) throws Exception;

    /** Copy local file {@code src} over game file {@code rel}. */
    void push(String pkg, File src, String rel) throws Exception;
}
