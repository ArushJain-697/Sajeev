// #01 (WS1) | Test: test_build_rejects_post_java11_api
// DELIBERATELY BAD. Must FAIL to compile with `javac --release 11`. `Stream.toList()` is Java 16+.
// Only `make test-negative` compiles this file, and it passes when javac fails.
package io.sajeev.api;

import java.util.List;

class PostJava11Api {
    List<String> bad() {
        return List.of("a").stream().toList();
    }
}
