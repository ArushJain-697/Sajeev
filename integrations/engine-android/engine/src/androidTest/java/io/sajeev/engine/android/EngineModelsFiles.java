package io.sajeev.engine.android;

import android.content.Context;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

/** Test helper: copies the models folder from the test assets to a real directory. */
final class EngineModelsFiles {
    private EngineModelsFiles() {
    }

    static void copy(Context c, File to) throws IOException {
        copyDir(c, "models", to);
    }

    private static void copyDir(Context c, String dir, File to) throws IOException {
        String[] names = c.getAssets().list(dir);
        to.mkdirs();
        for (String n : names) {
            String[] sub = c.getAssets().list(dir + "/" + n);
            if (sub != null && sub.length > 0) {
                copyDir(c, dir + "/" + n, new File(to, n));
            } else {
                try (InputStream in = c.getAssets().open(dir + "/" + n); OutputStream out = new FileOutputStream(new File(to, n))) {
                    byte[] buf = new byte[65536];
                    int r;
                    while ((r = in.read(buf)) > 0) {
                        out.write(buf, 0, r);
                    }
                }
            }
        }
    }
}
