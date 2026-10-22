// #38 (WS2) | Tests named as claims. The code list is copied from MockMDS application.properties (mds_ERROR_*).
package io.sajeev.devices;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import io.sajeev.api.ErrorState;
import java.util.Arrays;
import java.util.List;
import org.junit.jupiter.api.Test;

class SbiErrorMapTest {

    /** Every error code MockMDS defines (application.properties, 22 Oct 2026). */
    private static final List<Integer> MOCK_CODES = Arrays.asList(
            0, 100, 101, 102, 103, 104, 105, 106, 107, 108, 109, 110, 111, 112, 113, 114, 115, 116, 117, 118, 119,
            120, 121, 122, 500, 501, 502, 503, 504, 505, 506, 507, 551, 601, 604, 605, 606, 607, 608, 609, 610,
            700, 701, 702, 703, 704, 705, 706, 707, 708, 709, 710, 800, 801, 803, 804, 805, 806, 809, 810, 999);

    /** Codes we do not map explicitly. They fall to ENGINE_ERROR on purpose (live-stream and auth-only codes). */
    private static final List<Integer> DELIBERATE_ENGINE = Arrays.asList(
            551, 601, 604, 606, 608, 610, 710, 800, 801, 803, 804, 810, 999);

    @Test
    void test_every_sbi_error_code_maps_to_a_state() {
        for (int code : MOCK_CODES) {
            if (code == 0) {
                continue; // success is not an error
            }
            boolean explicit = SbiErrorMap.knownCodes().contains(code);
            boolean deliberate = DELIBERATE_ENGINE.contains(code);
            assertTrue(explicit || deliberate, "code " + code + " is neither mapped nor listed as deliberate");
        }
    }

    @Test
    void test_unlisted_code_is_an_engine_error_never_a_pass() {
        assertEquals(ErrorState.ENGINE_ERROR, SbiErrorMap.map(999));
        assertEquals(ErrorState.ENGINE_ERROR, SbiErrorMap.map(12345));
    }

    @Test
    void test_face_not_detected_code_maps_to_face_not_detected() {
        assertEquals(ErrorState.FACE_NOT_DETECTED, SbiErrorMap.map(101));
        assertEquals(ErrorState.DEVICE_UNAVAILABLE, SbiErrorMap.map(106));
        assertEquals(ErrorState.DEVICE_DISCONNECTED, SbiErrorMap.map(700));
    }
}
