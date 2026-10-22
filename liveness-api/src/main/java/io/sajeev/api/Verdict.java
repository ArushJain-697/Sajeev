// #13 (WS1) | Bible §6, §9 | Java 11 only. Frozen API: changes need a BUILD_LOG.md note.
package io.sajeev.api;

import java.util.Optional;

/** Final answer for one session. Tier names match the diagnostic view (Bible §6.9). Immutable. */
public final class Verdict {
    /** The result shown to the operator or supervisor. */
    public enum Tier { LIVE_PASSIVE, LIVE_ACTIVE, ACTIVE_REQUIRED, ATTACK_SUSPECTED, CANNOT_JUDGE, LIMITED }

    private final Tier tier;
    private final ErrorState error;
    private final String reason;

    /**
     * @param tier the tier
     * @param error the PDF error state, or null when there is none
     * @param reason short reason, for audit
     */
    public Verdict(Tier tier, ErrorState error, String reason) {
        this.tier = tier;
        this.error = error;
        this.reason = reason;
    }

    /** @return the tier */
    public Tier tier() {
        return tier;
    }

    /** @return the error state, if any */
    public Optional<ErrorState> error() {
        return Optional.ofNullable(error);
    }

    /** @return the reason */
    public String reason() {
        return reason;
    }
}
