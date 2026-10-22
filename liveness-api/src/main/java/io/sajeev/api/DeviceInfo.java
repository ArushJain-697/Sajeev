// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** Identity of a discovered device. The level (L0 / L1) follows the MOSIP SBI spec. Immutable. */
public final class DeviceInfo {
    /** MOSIP device level. */
    public enum Level { L0, L1 }

    private final String id;
    private final String name;
    private final Level level;

    /**
     * @param id stable device identifier
     * @param name human-readable name
     * @param level L0 or L1
     */
    public DeviceInfo(String id, String name, Level level) {
        this.id = id;
        this.name = name;
        this.level = level;
    }

    /** @return the device identifier */
    public String id() {
        return id;
    }

    /** @return the display name */
    public String name() {
        return name;
    }

    /** @return the device level */
    public Level level() {
        return level;
    }
}
