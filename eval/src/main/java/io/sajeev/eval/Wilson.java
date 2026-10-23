// #60 (WS4) | Bible §11 | Wilson score interval for a rate k/n. Java 11 only.
package io.sajeev.eval;

/** Wilson 95% interval (z = 1.96): reliable for small n and for 0 events. */
public final class Wilson {

    private static final double Z = 1.96;

    private Wilson() {
    }

    /**
     * @param k events (for example attacks accepted)
     * @param n trials (presentations)
     * @return {lower, upper}; {0, 1} when n is 0 (nothing is known)
     */
    public static double[] interval(int k, int n) {
        if (n <= 0) {
            return new double[] {0.0, 1.0};
        }
        double p = (double) k / n;
        double z2 = Z * Z;
        double denom = 1.0 + z2 / n;
        double centre = (p + z2 / (2.0 * n)) / denom;
        double half = Z * Math.sqrt(p * (1.0 - p) / n + z2 / (4.0 * n * (double) n)) / denom;
        return new double[] {Math.max(0.0, centre - half), Math.min(1.0, centre + half)};
    }
}
