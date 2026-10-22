// #19 (WS1) | Bible §6.4 | Plain 2D FFT for power-of-two sizes (no native libs, D23). Java 11 only.
package io.sajeev.passive.moire;

/** In-place radix-2 FFT on a square n x n complex grid (row pass, then column pass). */
final class Fft2d {

    private Fft2d() {
    }

    /**
     * @param re real parts, n*n, row-major (modified in place)
     * @param im imaginary parts, n*n (modified in place)
     * @param n side length, a power of two
     */
    static void transform(double[] re, double[] im, int n) {
        double[] rr = new double[n];
        double[] ri = new double[n];
        for (int r = 0; r < n; r++) {
            System.arraycopy(re, r * n, rr, 0, n);
            System.arraycopy(im, r * n, ri, 0, n);
            fft1d(rr, ri);
            System.arraycopy(rr, 0, re, r * n, n);
            System.arraycopy(ri, 0, im, r * n, n);
        }
        for (int c = 0; c < n; c++) {
            for (int r = 0; r < n; r++) {
                rr[r] = re[r * n + c];
                ri[r] = im[r * n + c];
            }
            fft1d(rr, ri);
            for (int r = 0; r < n; r++) {
                re[r * n + c] = rr[r];
                im[r * n + c] = ri[r];
            }
        }
    }

    private static void fft1d(double[] re, double[] im) {
        int n = re.length;
        for (int i = 1, j = 0; i < n; i++) {
            int bit = n >> 1;
            for (; (j & bit) != 0; bit >>= 1) {
                j ^= bit;
            }
            j ^= bit;
            if (i < j) {
                double t = re[i];
                re[i] = re[j];
                re[j] = t;
                t = im[i];
                im[i] = im[j];
                im[j] = t;
            }
        }
        for (int len = 2; len <= n; len <<= 1) {
            double ang = -2 * Math.PI / len;
            double wr = Math.cos(ang);
            double wi = Math.sin(ang);
            for (int i = 0; i < n; i += len) {
                double cr = 1;
                double ci = 0;
                for (int k = 0; k < len / 2; k++) {
                    int a = i + k;
                    int b = a + len / 2;
                    double xr = re[b] * cr - im[b] * ci;
                    double xi = re[b] * ci + im[b] * cr;
                    re[b] = re[a] - xr;
                    im[b] = im[a] - xi;
                    re[a] += xr;
                    im[a] += xi;
                    double nr = cr * wr - ci * wi;
                    ci = cr * wi + ci * wr;
                    cr = nr;
                }
            }
        }
    }
}
