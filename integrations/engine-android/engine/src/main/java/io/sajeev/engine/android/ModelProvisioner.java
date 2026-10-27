// SAJEEV A3 | First-start provisioning of the model files: copies them from the app's assets to filesDir/models, but only
// the files the manifest lists (plus manifest.txt and manifest.sig). Nothing is trusted from this copy: the engine still
// verifies the ECDSA-signed manifest and every SHA-256 before it opens a model. Never downloads. Java 8 source level.
package io.sajeev.engine.android;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public final class ModelProvisioner {

    /** Where the app bundles the models: src/main/assets/sajeev-models/ (manifest.txt, manifest.sig, yunet/, ...). */
    public static final String ASSET_DIR = "sajeev-models";

    /** Opens a path under the asset directory; throws IOException (e.g. FileNotFound) when it does not exist. */
    public interface Source {
        InputStream open(String relativePath) throws IOException;
    }

    private ModelProvisioner() {
    }

    /**
     * Copies the model set when the destination has none, or when the bundled manifest differs from the installed one.
     * Returns true if files were copied; false if nothing was bundled or nothing was needed. An incomplete bundle (a
     * listed file is missing) copies nothing and throws, so a half set is never left behind.
     */
    public static boolean provision(Source assets, File dest) throws IOException {
        byte[] manifest;
        byte[] sig;
        try {
            manifest = readAll(assets.open("manifest.txt"));
            sig = readAll(assets.open("manifest.sig"));
        } catch (IOException notBundled) {
            return false; // nothing bundled: the models must be pushed (adb push) or the engine is UNAVAILABLE
        }
        File installed = new File(dest, "manifest.txt");
        if (installed.isFile() && java.util.Arrays.equals(readAll(new java.io.FileInputStream(installed)), manifest)) {
            return false;
        }
        List<String> names = listedFiles(manifest);
        File tmp = new File(dest.getParentFile(), dest.getName() + ".tmp");
        deleteTree(tmp);
        if (!tmp.mkdirs() && !tmp.isDirectory()) {
            throw new IOException("cannot create " + tmp);
        }
        try {
            for (String n : names) {
                write(new File(tmp, n), readAll(assets.open(n)));
            }
            write(new File(tmp, "manifest.txt"), manifest);
            write(new File(tmp, "manifest.sig"), sig);
            deleteTree(dest);
            if (!tmp.renameTo(dest)) {
                throw new IOException("cannot move " + tmp + " to " + dest);
            }
        } finally {
            deleteTree(tmp);
        }
        return true;
    }

    /** The relative paths of the "file <path> <sha256> <size>" lines. Rejects absolute paths and "..". */
    static List<String> listedFiles(byte[] manifest) throws IOException {
        List<String> out = new ArrayList<>();
        for (String line : new String(manifest, StandardCharsets.UTF_8).split("\n")) {
            String[] p = line.trim().split("\\s+");
            if (p.length >= 2 && p[0].equals("file")) {
                String n = p[1];
                if (n.startsWith("/") || n.startsWith("\\") || Arrays.asList(n.split("[/\\\\]")).contains("..")) {
                    throw new IOException("unsafe path in manifest: " + n);
                }
                out.add(n);
            }
        }
        return out;
    }

    private static void write(File f, byte[] data) throws IOException {
        File parent = f.getParentFile();
        if (!parent.isDirectory() && !parent.mkdirs()) {
            throw new IOException("cannot create " + parent);
        }
        try (FileOutputStream o = new FileOutputStream(f)) {
            o.write(data);
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        try (InputStream i = in) {
            ByteArrayOutputStream b = new ByteArrayOutputStream();
            byte[] buf = new byte[65536];
            int n;
            while ((n = i.read(buf)) > 0) {
                b.write(buf, 0, n);
            }
            return b.toByteArray();
        }
    }

    private static void deleteTree(File f) {
        File[] kids = f.listFiles();
        if (kids != null) {
            for (File k : kids) {
                deleteTree(k);
            }
        }
        f.delete();
    }
}
