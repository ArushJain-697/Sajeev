// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

/** A device-side failure, carrying the PDF error state to show the person. */
public class DeviceException extends Exception {
    private static final long serialVersionUID = 1L;
    private final ErrorState state;

    /**
     * @param state the PDF error state
     * @param message detail for logs (never shown to the person as-is)
     */
    public DeviceException(ErrorState state, String message) {
        super(message);
        this.state = state;
    }

    /** @return the error state */
    public ErrorState state() {
        return state;
    }
}
