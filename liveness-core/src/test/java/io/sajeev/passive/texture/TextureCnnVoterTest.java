// #18 (WS1) | Bible §6.3 | Tests named as claims. Need the ONNX models: -Dsajeev.models=<dir with minifasnet/ and yunet/>.
package io.sajeev.passive.texture;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import io.sajeev.api.Vote;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TextureCnnVoterTest {

    private static Path root() {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir with minifasnet/ and yunet/>");
        return Paths.get(dir);
    }

    private static float[] referenceInput() {
        int plane = 80 * 80;
        float[] chw = new float[3 * plane];
        for (int i = 0; i < chw.length; i++) {
            chw[i] = ((i * 37) % 256) / 255f; // same formula as the Python reference
        }
        return chw;
    }

    /** Pull the logits for one model out of the reference JSON (the engine has no JSON library). */
    private static float[] referenceLogits(String modelName) throws Exception {
        String json = new String(Files.readAllBytes(Paths.get("../testdata/reference/minifas_pytorch_ref.json")),
                StandardCharsets.UTF_8);
        int at = json.indexOf("\"" + modelName + "\"");
        assertTrue(at >= 0, "no reference for " + modelName);
        Matcher m = Pattern.compile("\"logits\"\\s*:\\s*\\[([^\\]]*)\\]").matcher(json.substring(at));
        assertTrue(m.find());
        String[] parts = m.group(1).split(",");
        float[] out = new float[parts.length];
        for (int i = 0; i < parts.length; i++) {
            out[i] = Float.parseFloat(parts[i].trim());
        }
        return out;
    }

    @Test
    void test_onnx_matches_pytorch_within_1e_4() throws Exception {
        Path dir = root().resolve("minifasnet");
        assumeTrue(new File(dir.toFile(), "2.7_80x80_MiniFASNetV2.onnx").exists(), "run export_compare.py first");
        float[] input = referenceInput();
        String[][] models = {
            {"2.7_80x80_MiniFASNetV2.pth", "2.7_80x80_MiniFASNetV2.onnx", "2.7"},
            {"4_0_0_80x80_MiniFASNetV1SE.pth", "4_0_0_80x80_MiniFASNetV1SE.onnx", "4.0"},
        };
        for (String[] m : models) {
            try (MiniFasNetModel net = new MiniFasNetModel(dir.resolve(m[1]), Double.parseDouble(m[2]))) {
                float[] got = net.logits(input);
                float[] want = referenceLogits(m[0]);
                for (int i = 0; i < want.length; i++) {
                    assertTrue(Math.abs(got[i] - want[i]) <= 1e-4f,
                            m[0] + " logit " + i + " got " + got[i] + " want " + want[i]);
                }
            }
        }
    }

    @Test
    void test_abstains_between_thresholds() {
        TextureCnnVoter v = new TextureCnnVoter(null, null, null, 0.80, 0.20);
        assertEquals(Vote.Label.ABSTAIN, v.label(0.50));
        assertEquals(Vote.Label.LIVE, v.label(0.85));
        assertEquals(Vote.Label.ATTACK, v.label(0.10));
        assertEquals(Vote.Label.ABSTAIN, v.label(0.80 - 1e-9));
    }
}
