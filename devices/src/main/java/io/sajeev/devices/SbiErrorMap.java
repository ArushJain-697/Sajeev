// #38 (WS2) | Bible §9 | Maps SBI error codes (MockMDS mds_ERROR_* table) to our 13 PDF error states. Java 11 only.
// Anything not listed becomes ENGINE_ERROR: a device error is never silently treated as a pass.
package io.sajeev.devices;

import io.sajeev.api.ErrorState;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/** Static table from SBI error code to the state we show and audit. */
public final class SbiErrorMap {

    private static final Map<Integer, ErrorState> MAP;

    static {
        Map<Integer, ErrorState> m = new HashMap<>();
        // device cannot serve the request
        for (int c : new int[] {100, 103, 104, 106, 107, 108, 110, 111, 500, 503, 504, 605, 607, 703, 704, 705, 706, 709, 806, 805, 809}) {
            m.put(c, ErrorState.DEVICE_UNAVAILABLE);
        }
        // face not in the frame / image problems
        m.put(101, ErrorState.FACE_NOT_DETECTED);
        m.put(105, ErrorState.POOR_QUALITY);
        // bad data from the device or the request
        for (int c : new int[] {102, 109, 112, 113, 114, 115, 116, 117, 118, 119, 120, 121, 122, 505, 506, 507, 501, 502, 707, 708}) {
            m.put(c, ErrorState.INVALID_FRAME_DATA);
        }
        // stream or capture cut off mid-way
        for (int c : new int[] {609, 700, 701}) {
            m.put(c, ErrorState.DEVICE_DISCONNECTED);
        }
        // a capture of the right kind is already running: the device is busy, the attempt is not a pass
        m.put(702, ErrorState.DEVICE_UNAVAILABLE);
        MAP = Collections.unmodifiableMap(m);
    }

    private SbiErrorMap() {
    }

    /**
     * @param code the SBI errorCode, for example 111
     * @return the PDF state to show and audit; ENGINE_ERROR for codes not in the table
     */
    public static ErrorState map(int code) {
        ErrorState s = MAP.get(code);
        return s == null ? ErrorState.ENGINE_ERROR : s;
    }

    /** @return the codes this table names explicitly (for the coverage test) */
    public static java.util.Set<Integer> knownCodes() {
        return MAP.keySet();
    }
}
