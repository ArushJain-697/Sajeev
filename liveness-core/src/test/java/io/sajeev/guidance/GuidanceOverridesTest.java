// Configurable user guidance (PDF good-to-have): an operator can change the wording through a folder of override files.
package io.sajeev.guidance;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class GuidanceOverridesTest {

    private static void write(Path dir, String file, String text) throws Exception {
        Files.write(dir.resolve(file), text.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    void test_a_real_override_file_changes_only_the_named_keys(@TempDir Path dir) throws Exception {
        write(dir, "messages_en.properties", "challenge.blink=Blink now, please\n");
        write(dir, "messages_hi.properties", "challenge.smile=\u0915\u0943\u092a\u092f\u093e \u092e\u0941\u0938\u094d\u0915\u0941\u0930\u093e\u090f\u0901 (\u0915\u0938\u094d\u091f\u092e)\n");
        GuidanceKeys base = new GuidanceKeys();
        GuidanceKeys custom = new GuidanceKeys(dir);
        assertEquals("Blink now, please", custom.text("challenge.blink", "en"));
        assertNotEquals(base.text("challenge.blink", "en"), custom.text("challenge.blink", "en"));
        assertEquals(base.text("challenge.smile", "en"), custom.text("challenge.smile", "en"), "untouched keys keep the PDF wording");
        assertEquals(base.text("challenge.blink", "fr"), custom.text("challenge.blink", "fr"), "other languages are untouched");
        assertNotEquals(base.text("challenge.smile", "hi"), custom.text("challenge.smile", "hi"));
    }

    @Test
    void test_none_and_a_missing_folder_mean_no_overrides(@TempDir Path dir) throws Exception {
        GuidanceKeys base = new GuidanceKeys();
        assertEquals(base.text("failure.pad", "en"), GuidanceKeys.fromConfig("none").text("failure.pad", "en"));
        assertEquals(base.text("failure.pad", "en"), GuidanceKeys.fromConfig("").text("failure.pad", "en"));
        assertEquals(base.text("failure.pad", "en"), new GuidanceKeys(dir.resolve("does-not-exist")).text("failure.pad", "en"));
    }

    @Test
    void test_a_typo_blank_text_or_unknown_language_is_refused_not_ignored(@TempDir Path dir) throws Exception {
        write(dir, "messages_en.properties", "challenge.blnk=oops\n");
        assertThrows(IllegalArgumentException.class, () -> new GuidanceKeys(dir));
        write(dir, "messages_en.properties", "challenge.blink=   \n");
        assertThrows(IllegalArgumentException.class, () -> new GuidanceKeys(dir));
        Files.delete(dir.resolve("messages_en.properties"));
        write(dir, "messages_xx.properties", "challenge.blink=x\n");
        assertThrows(IllegalArgumentException.class, () -> new GuidanceKeys(dir));
    }
}
