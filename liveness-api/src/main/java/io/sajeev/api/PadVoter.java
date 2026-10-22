// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

import java.util.List;

/** One of the five passive inspectors (V1 texture, V2 moire, V3 flatness, V4 frame edge, V5 reflection). */
public interface PadVoter {

    /** @return a short stable name, for audit and config keys */
    String name();

    /**
     * @param window recent frames, oldest first
     * @return this voter's opinion; never null
     */
    Vote vote(List<Frame> window);
}
