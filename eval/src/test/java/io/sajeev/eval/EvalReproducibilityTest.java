// Red-team 2 fixes in the evaluation: failures are counted, the freeze hash follows the code and not the git HEAD, nothing is
// dropped silently, temporary frames are deleted. Pure computation plus a throw-away git repository: no models, no clips.
package io.sajeev.eval;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class EvalReproducibilityTest {

    private static Presentation p(String file, String species, boolean accepted, String tier) {
        return new Presentation(file, species, "s1", "d1", accepted, tier, Collections.<String, String>emptyMap(),
                Collections.<String, Double>emptyMap(), false);
    }

    @Test
    void test_every_tier_is_counted_per_species_and_for_bona_fide() {
        List<Presentation> ps = new ArrayList<>();
        ps.add(p("b1", "bona", true, "LIVE_PASSIVE"));
        ps.add(p("b2", "bona", false, "CANNOT_JUDGE"));
        ps.add(p("b3", "bona", false, "NO_DECISION"));
        ps.add(p("a1", "P1", false, "ATTACK_SUSPECTED"));
        ps.add(p("a2", "P1", false, "CANNOT_JUDGE"));
        String json = Metrics.toJson(ps, 0.8, 3);
        assertTrue(json.contains("\"tier_histogram\":{\"bona\":{\"LIVE_PASSIVE\":1,\"LIVE_ACTIVE\":0,\"ACTIVE_REQUIRED\":0,"
                + "\"CANNOT_JUDGE\":1,\"NO_DECISION\":1,\"ATTACK_SUSPECTED\":0,\"LIMITED\":0}"), json);
        assertTrue(json.contains("\"P1\":{\"LIVE_PASSIVE\":0,\"LIVE_ACTIVE\":0,\"ACTIVE_REQUIRED\":0,\"CANNOT_JUDGE\":1,"
                + "\"NO_DECISION\":0,\"ATTACK_SUSPECTED\":1,\"LIMITED\":0}"), json);
    }

    @Test
    void test_cannot_judge_no_decision_and_timeouts_are_not_defended_for_attacks_and_failures_for_bona_fide() {
        List<Presentation> ps = new ArrayList<>();
        ps.add(p("a1", "P2", false, "ATTACK_SUSPECTED")); // the only one that is defended
        ps.add(p("a2", "P2", false, "CANNOT_JUDGE"));
        ps.add(p("a3", "P2", false, "NO_DECISION"));
        ps.add(p("a4", "P2", false, "ACTIVE_REQUIRED")); // a challenge timeout
        ps.add(p("a5", "P2", true, "LIVE_PASSIVE"));
        ps.add(p("b1", "bona", true, "LIVE_PASSIVE"));
        ps.add(p("b2", "bona", false, "CANNOT_JUDGE"));
        String json = Metrics.toJson(ps, 0.8, 3);
        assertTrue(json.contains("\"not_defended\":"), json);
        assertTrue(json.contains("\"by_species\":{\"P2\":{\"n\":5,\"events\":4,"), "4 of 5 attacks are not defended: " + json);
        assertTrue(json.contains("\"bona_fide_not_accepted\":{\"n\":2,\"events\":1,"), json);
        // the classic APCER counts only the accepted one
        assertTrue(json.contains("\"apcer_by_species\":{\"P2\":{\"n\":5,\"events\":1,"), json);
        String text = Metrics.histogramText(ps);
        assertTrue(text.contains("NOT DEFENDED") && text.contains("4/5"), text);
        assertTrue(text.contains("BPCER failures") && text.contains("1/2"), text);
    }

    @Test
    void test_a_manifest_row_with_too_few_fields_is_refused_not_dropped(@TempDir Path dir) throws IOException {
        Path m = dir.resolve("manifest.csv");
        Files.write(m, Arrays.asList("file,sha256,session,subject,device,species,fps,duration_s",
                "a.mp4,h,d1,s1,cam,bona,30/1,5.0", "b.mp4,h,d1,s1,cam,P1"), StandardCharsets.UTF_8);
        IOException e = assertThrows(IOException.class, () -> RunEval.readManifest(m));
        assertTrue(e.getMessage().contains("line 3"), e.getMessage());
    }

    @Test
    void test_a_bad_fps_is_refused_instead_of_producing_nan_timestamps() {
        assertThrows(IllegalArgumentException.class, () -> RunEval.fps("0/0"));
        assertThrows(IllegalArgumentException.class, () -> RunEval.fps("0"));
        assertEquals(30.0, RunEval.fps("30/1"), 0.0);
    }

    @Test
    void test_the_temporary_frame_folder_is_deleted_with_everything_in_it(@TempDir Path dir) throws IOException {
        Path tmp = Files.createDirectories(dir.resolve("sajeev-eval123").resolve("sub"));
        Files.write(tmp.resolve("0001.jpg"), new byte[] {1, 2, 3});
        RunEval.deleteTree(dir.resolve("sajeev-eval123"));
        assertFalse(Files.exists(dir.resolve("sajeev-eval123")));
    }

    // ---- the freeze hash follows the engine sources, not the git HEAD ----

    private static void git(Path dir, String... args) throws IOException, InterruptedException {
        List<String> cmd = new ArrayList<>(Arrays.asList("git", "-C", dir.toString(), "-c", "user.name=t", "-c", "user.email=t@t",
                "-c", "commit.gpgsign=false"));
        cmd.addAll(Arrays.asList(args));
        Process pr = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        pr.getInputStream().readAllBytes();
        assertEquals(0, pr.waitFor(), String.join(" ", cmd));
    }

    @Test
    void test_the_freeze_state_changes_with_engine_code_and_dirt_but_not_with_a_commit_of_other_files(@TempDir Path root) throws Exception {
        try {
            Process pr = new ProcessBuilder("git", "--version").redirectErrorStream(true).start();
            pr.getInputStream().readAllBytes();
            assumeTrue(pr.waitFor() == 0, "git is not available");
        } catch (IOException e) {
            assumeTrue(false, "git is not available");
        }
        Path src = Files.createDirectories(root.resolve("liveness-core/src/main"));
        Files.write(src.resolve("A.java"), "class A {}".getBytes(StandardCharsets.UTF_8));
        Files.write(root.resolve("pom.xml"), "<project/>".getBytes(StandardCharsets.UTF_8));
        git(root, "init", "-q");
        git(root, "add", "-A");
        git(root, "commit", "-q", "-m", "one");
        String frozen = SourceState.describe(root);
        assertTrue(SourceState.isClean(frozen), frozen);

        // committing eval/FROZEN (or any other file outside the engine sources) must not change the state: the old HEAD did
        Files.createDirectories(root.resolve("eval"));
        Files.write(root.resolve("eval/FROZEN"), "hash".getBytes(StandardCharsets.UTF_8));
        git(root, "add", "-A");
        git(root, "commit", "-q", "-m", "freeze");
        assertEquals(frozen, SourceState.describe(root), "committing FROZEN changed the state");

        // an uncommitted edit of the engine is dirty
        Files.write(src.resolve("A.java"), "class A { int x; }".getBytes(StandardCharsets.UTF_8));
        String dirty = SourceState.describe(root);
        assertFalse(SourceState.isClean(dirty), dirty);
        assertTrue(dirty.endsWith("tree=dirty"), dirty);

        // a committed edit is clean again but is a different engine
        git(root, "add", "-A");
        git(root, "commit", "-q", "-m", "edit");
        String edited = SourceState.describe(root);
        assertTrue(SourceState.isClean(edited), edited);
        assertNotEquals(frozen, edited);

        // an untracked new engine file is dirty too
        Files.write(src.resolve("B.java"), "class B {}".getBytes(StandardCharsets.UTF_8));
        assertFalse(SourceState.isClean(SourceState.describe(root)));
    }

    @Test
    void test_a_folder_that_is_not_a_git_repository_is_never_clean(@TempDir Path root) {
        String s = SourceState.describe(root);
        assertFalse(SourceState.isClean(s), s);
    }
}
