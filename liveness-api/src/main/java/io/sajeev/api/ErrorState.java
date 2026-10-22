// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** The 13 error states from Bible §9 (PDF error cases). Each has a person-facing message key in guidance. */
public enum ErrorState {
    DEVICE_UNAVAILABLE,
    DEVICE_DISCONNECTED,
    CAMERA_UNAVAILABLE,
    FACE_NOT_DETECTED,
    MULTIPLE_FACES,
    POOR_QUALITY,
    ACTIVE_REQUIRED,
    PAD_FAIL,
    CHALLENGE_FAILED,
    CHALLENGE_TIMEOUT,
    MAX_RETRIES,
    INVALID_FRAME_DATA,
    /** Never a silent pass: the engine refuses and reports this. */
    ENGINE_ERROR
}
