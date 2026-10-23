// #32 (WS1) | Builds and signs a model manifest (ECDSA P-256, decision D43). Run by the person who releases models; the private
// key never goes into the repository. Java 11 only.
// Usage:  ManifestTool genkey <private.der> <public.der>
//         ManifestTool sign <model-dir> <version> <private.der> <manifest.txt> <manifest.sig>
package io.sajeev.model;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.spec.ECGenParameterSpec;
import java.security.spec.PKCS8EncodedKeySpec;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public final class ManifestTool {

    private ManifestTool() {
    }

    /** @return the manifest of every regular file under dir (relative names with '/', not manifest.txt / manifest.sig) */
    public static ModelManifest build(Path dir, long version) throws IOException {
        Map<String, Entry> files = new TreeMap<>();
        try (Stream<Path> s = Files.walk(dir)) {
            for (Path f : s.filter(Files::isRegularFile).collect(Collectors.toList())) {
                String name = dir.relativize(f).toString().replace('\\', '/');
                if (!(name.endsWith(".onnx") || name.endsWith(".onnx.data"))) { // only model files are covered (not scripts or source downloads)
                    continue;
                }
                files.put(name, new Entry(ModelVerifier.sha256Hex(f), Files.size(f)));
            }
        }
        return new ModelManifest(version, files);
    }

    /** @return the DER ECDSA signature of the manifest's canonical bytes */
    public static byte[] sign(ModelManifest m, PrivateKey key) throws Exception {
        Signature s = Signature.getInstance("SHA256withECDSA");
        s.initSign(key);
        s.update(m.canonicalBytes());
        return s.sign();
    }

    public static void main(String[] a) throws Exception {
        if (a.length >= 3 && a[0].equals("genkey")) {
            KeyPairGenerator g = KeyPairGenerator.getInstance("EC");
            g.initialize(new ECGenParameterSpec("secp256r1"));
            KeyPair kp = g.generateKeyPair();
            Files.write(Paths.get(a[1]), kp.getPrivate().getEncoded());
            Files.write(Paths.get(a[2]), kp.getPublic().getEncoded());
            System.out.println("wrote " + a[1] + " (KEEP SECRET, do not commit) and " + a[2]);
        } else if (a.length >= 6 && a[0].equals("sign")) {
            Path dir = Paths.get(a[1]);
            PrivateKey pk = KeyFactory.getInstance("EC").generatePrivate(new PKCS8EncodedKeySpec(Files.readAllBytes(Paths.get(a[3]))));
            ModelManifest m = build(dir, Long.parseLong(a[2]));
            Files.write(Paths.get(a[4]), m.canonicalBytes());
            Files.write(Paths.get(a[5]), sign(m, pk));
            System.out.println("signed " + m.files().size() + " files, version " + m.version());
        } else {
            System.err.println("usage: genkey <private.der> <public.der> | sign <model-dir> <version> <private.der> <manifest.txt> <manifest.sig>");
            System.exit(2);
        }
    }
}
