// #01 (WS1) | Bible §15 | Trivial test proving the Maven build and JUnit 5 run
package io.sajeev.api;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class SkeletonTest {
    @Test
    void test_skeleton_build_runs_tests() {
        assertEquals(11, Integer.parseInt("11"));
    }
}
