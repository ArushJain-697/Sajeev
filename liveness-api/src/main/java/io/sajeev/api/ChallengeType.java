// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** The 10 active challenge types (Bible §6.6). The client picks them; the person does not. */
public enum ChallengeType {
    BLINK,
    SMILE,
    TURN_HEAD_LEFT,
    TURN_HEAD_RIGHT,
    LOOK_LEFT,
    LOOK_RIGHT,
    LOOK_UP,
    LOOK_DOWN,
    EYES_CLOSED_HOLD,
    OPEN_MOUTH
}
