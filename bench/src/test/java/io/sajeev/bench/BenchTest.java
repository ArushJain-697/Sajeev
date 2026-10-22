// #47 (WS2) | Tests named as claims. Needs the models (-Dsajeev.models=<dir>); runs the Bench in-process on the fixtures.
package io.sajeev.bench;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BenchTest {

    private static String models() {
        String dir = System.getProperty("sajeev.models", System.getenv("SAJEEV_MODELS"));
        assumeTrue(dir != null, "set -Dsajeev.models=<dir>");
        return dir;
    }

    private static String run(int expectCode, String... args) {
        ByteArrayOutputStream o = new ByteArrayOutputStream();
        ByteArrayOutputStream e = new ByteArrayOutputStream();
        int code = Bench.run(args, new PrintStream(o, true), new PrintStream(e, true));
        assertTrue(expectCode < 0 || expectCode == code, "exit " + code + " stderr: " + new String(e.toByteArray(), StandardCharsets.UTF_8));
        return new String(o.toByteArray(), StandardCharsets.UTF_8);
    }

    @Test
    void test_run_sh_prints_verdict_and_votes(@TempDir Path dir) throws Exception {
        byte[] one = Files.readAllBytes(Paths.get("../testdata/fixtures-faces/one_face.jpg"));
        for (int i = 0; i < 30; i++) {
            Files.write(dir.resolve(String.format("%03d.jpg", i)), one);
        }
        String out = run(0, "--clip", dir.toString(), "--fps", "10", "--models", models());
        assertTrue(out.contains("verdict: "), out);
        for (String v : new String[] {"V1_texture_minifasnet", "V2_moire", "V3_flatness", "V4_frame_edge", "V5_reflection"}) {
            assertTrue(out.contains("vote " + v), "missing " + v + "\n" + out);
        }
        assertTrue(out.contains("measured fps: 10.00"), out);
    }

    @Test
    void test_bench_output_carries_the_mock_label() {
        // exit 3 is allowed: a 4 s still clip can end in the middle of a challenge, which is "no decision", not a failure
        String out = run(-1, "--clip", "../testdata/fixtures-faces/one_face.jpg", "--models", models());
        assertTrue(out.startsWith(Bench.LABEL), out);
    }

    @Test
    void test_bad_arguments_exit_2_with_usage() {
        run(2, "--nonsense");
    }

    @Test
    void test_a_recorded_mjpeg_stream_is_replayed() {
        String out = run(0, "--clip", "../testdata/recorded-095/stream-face.mjpeg", "--models", models());
        assertTrue(out.contains("frames available"), out);
        assertTrue(out.contains("verdict: "), out);
    }
}
