// #33 (WS1) | Bible §6.x, D35, R1-10 | Which ONNX Runtime provider to use. CPU is the reference. Java 11 only.
// An accelerator is used only when a recorded equivalence check shows its output within tolerance of the CPU's.
package io.sajeev.model;

import java.util.List;

/** Picks the provider from the configured value and the recorded equivalence evidence. */
public final class ExecutionProviders {

    /** Providers the engine can ask ONNX Runtime for. */
    public enum Provider { CPU, COREML, NNAPI, DIRECTML, XNNPACK }

    /** One recorded check of an accelerator against the CPU. */
    public static final class Evidence {
        private final Provider provider;
        private final double maxAbsDiff;
        private final double tolerance;
        private final String measuredOn;

        /**
         * @param provider the accelerator that was checked
         * @param maxAbsDiff the largest absolute output difference from the CPU on the check set
         * @param tolerance the largest difference that was accepted (measured on the DEV set, #62)
         * @param measuredOn the device and date, for audit
         */
        public Evidence(Provider provider, double maxAbsDiff, double tolerance, String measuredOn) {
            this.provider = provider;
            this.maxAbsDiff = maxAbsDiff;
            this.tolerance = tolerance;
            this.measuredOn = measuredOn;
        }

        /** @return true when the accelerator stayed within its tolerance */
        boolean within() {
            return maxAbsDiff <= tolerance;
        }
    }

    private ExecutionProviders() {
    }

    /**
     * @param configured the value of liveness.acceleration: "AUTO", "CPU", or a provider name
     * @param available providers present on this device, in preference order
     * @param evidence recorded equivalence checks (may be empty)
     * @return the provider to use. CPU unless an accelerator is requested or chosen by AUTO AND has passing evidence.
     */
    public static Provider select(String configured, List<Provider> available, List<Evidence> evidence) {
        String c = configured == null ? "AUTO" : configured.trim().toUpperCase();
        if ("CPU".equals(c)) {
            return Provider.CPU;
        }
        for (Provider p : available) {
            if (p == Provider.CPU) {
                continue;
            }
            boolean wanted = "AUTO".equals(c) || p.name().equals(c);
            if (wanted && hasPassingEvidence(p, evidence)) {
                return p;
            }
        }
        return Provider.CPU; // no evidence, failing evidence, or nothing available: the reference path
    }

    private static boolean hasPassingEvidence(Provider p, List<Evidence> evidence) {
        boolean any = false;
        for (Evidence e : evidence) {
            if (e.provider == p) {
                any = true;
                if (!e.within()) {
                    return false; // any failing record disables the accelerator
                }
            }
        }
        return any;
    }
}
