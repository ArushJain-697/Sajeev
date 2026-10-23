// #62/#63 (WS4) | Tests named as claims, on a temporary eval directory (no real recordings involved).
package io.sajeev.eval;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class HeldOutGateTest {

    @TempDir Path eval;
    private static final String HASH = "a".repeat(64);

    private void seal() throws IOException {
        Path d = eval.resolve("data/heldout");
        Files.createDirectories(d);
        Files.write(d.resolve("one.mp4"), "clip one".getBytes(StandardCharsets.UTF_8));
        Files.write(d.resolve("two.mp4"), "clip two".getBytes(StandardCharsets.UTF_8));
        List<String> l = new ArrayList<>(HeldOutGate.listing(d));
        l.add("FILES 2");
        l.add("ROOT x");
        l.add("SEALED_AT now");
        Files.write(eval.resolve("HELDOUT.sha256"), l);
        Files.write(eval.resolve("HELDOUT_ACCESS.log"), "2026-10-24T00:00:00Z  SEALED  user=t  files=2\n".getBytes(StandardCharsets.UTF_8));
    }

    private void freeze(String h) throws IOException {
        Files.write(eval.resolve("FROZEN"), (h + "  frozen\n").getBytes(StandardCharsets.UTF_8));
    }

    @BeforeEach
    void setUp() throws IOException {
        seal();
    }

    @Test
    void test_heldout_run_refuses_without_frozen_file() {
        HeldOutGate.Refused r = assertThrows(HeldOutGate.Refused.class, () -> HeldOutGate.check(eval, HASH, null));
        assertTrue(r.getMessage().contains("FROZEN"), r.getMessage());
    }

    @Test
    void test_heldout_run_refuses_if_configuration_changed_after_the_freeze() throws IOException {
        freeze("b".repeat(64));
        assertThrows(HeldOutGate.Refused.class, () -> HeldOutGate.check(eval, HASH, null));
    }

    @Test
    void test_heldout_run_refuses_if_manifest_hash_changed() throws IOException {
        freeze(HASH);
        Files.write(eval.resolve("data/heldout/one.mp4"), "tampered".getBytes(StandardCharsets.UTF_8));
        HeldOutGate.Refused r = assertThrows(HeldOutGate.Refused.class, () -> HeldOutGate.check(eval, HASH, null));
        assertTrue(r.getMessage().contains("changed after sealing"), r.getMessage());
    }

    @Test
    void test_heldout_run_refuses_when_never_sealed() throws IOException {
        freeze(HASH);
        Files.delete(eval.resolve("HELDOUT.sha256"));
        assertThrows(HeldOutGate.Refused.class, () -> HeldOutGate.check(eval, HASH, null));
    }

    @Test
    void test_second_heldout_run_requires_logged_reason() throws IOException {
        freeze(HASH);
        assertDoesNotThrow(() -> HeldOutGate.check(eval, HASH, null)); // the first run is allowed and logged
        assertThrows(HeldOutGate.Refused.class, () -> HeldOutGate.check(eval, HASH, null));
        assertThrows(HeldOutGate.Refused.class, () -> HeldOutGate.check(eval, HASH, "  "));
        assertDoesNotThrow(() -> HeldOutGate.check(eval, HASH, "fixed a typo in the manifest"));
        String changelog = new String(Files.readAllBytes(eval.resolve("CHANGELOG.md")), StandardCharsets.UTF_8);
        assertTrue(changelog.contains("fixed a typo in the manifest"), changelog);
        String log = new String(Files.readAllBytes(eval.resolve("HELDOUT_ACCESS.log")), StandardCharsets.UTF_8);
        assertTrue(log.split("HELDOUT_RUN", -1).length - 1 == 2, "both runs are in the access log");
    }

    @Test
    void test_a_new_seal_resets_the_run_count() throws Exception {
        freeze(HASH);
        HeldOutGate.check(eval, HASH, null);
        Files.write(eval.resolve("HELDOUT_ACCESS.log"), "2026-10-25T00:00:00Z  SEALED  user=t  files=2\n".getBytes(StandardCharsets.UTF_8),
                java.nio.file.StandardOpenOption.APPEND);
        assertDoesNotThrow(() -> HeldOutGate.check(eval, HASH, null));
    }

    @Test
    void test_freeze_hash_changes_when_a_model_file_changes() throws IOException {
        Path models = eval.resolve("models");
        Files.createDirectories(models);
        Files.write(models.resolve("m.onnx"), "v1".getBytes(StandardCharsets.UTF_8));
        String a = FreezeHash.compute(models, "c1");
        assertTrue(a.equals(FreezeHash.compute(models, "c1")), "same input, same hash");
        Files.write(models.resolve("m.onnx"), "v2".getBytes(StandardCharsets.UTF_8));
        assertTrue(!a.equals(FreezeHash.compute(models, "c1")));
        assertTrue(!a.equals(FreezeHash.compute(models, "c2")), "a new engine commit changes the hash");
    }
}
