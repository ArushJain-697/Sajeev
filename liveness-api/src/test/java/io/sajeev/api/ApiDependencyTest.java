// #13 (WS1) | Test: test_api_has_no_dependency_on_core_or_ui
package io.sajeev.api;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import org.junit.jupiter.api.Test;

class ApiDependencyTest {
    private static final String[] FORBIDDEN = {"io.sajeev.core", "io.sajeev.devices", "javafx", "org.onnxruntime", "android."};

    @Test
    void test_api_has_no_dependency_on_core_or_ui() throws IOException {
        Path src = Paths.get("src/main/java");
        List<Path> files;
        try (Stream<Path> s = Files.walk(src)) {
            files = s.filter(p -> p.toString().endsWith(".java")).collect(Collectors.toList());
        }
        assertTrue(files.size() >= 15, "expected the API sources, found " + files.size());
        for (Path f : files) {
            String text = new String(Files.readAllBytes(f), StandardCharsets.UTF_8);
            for (String bad : FORBIDDEN) {
                assertTrue(!text.contains("import " + bad), f + " imports " + bad);
            }
        }
    }
}
