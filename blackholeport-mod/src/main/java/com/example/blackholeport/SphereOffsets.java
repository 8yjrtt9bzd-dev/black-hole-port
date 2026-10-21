package com.example.blackholeport;

/**
 * Every integer (x, y, z) offset inside a sphere of radius {@link #MAX_RADIUS}, sorted by distance from the
 * centre, so a hole can sweep its clearing sphere nearest-first with no randomness. Built once, on first use.
 */
public final class SphereOffsets {
    public static final int MAX_RADIUS = 96;

    private static int[] packed;
    /** counts[d] = number of offsets with squared distance < d (so counts[d + 1] = number with squared distance <= d). */
    private static int[] counts;

    private SphereOffsets() {
    }

    public static synchronized void ensure() {
        if (packed != null) {
            return;
        }
        int r = MAX_RADIUS;
        int maxD2 = r * r;
        int[] c = new int[maxD2 + 2];

        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 <= maxD2) {
                        c[d2 + 1]++;
                    }
                }
            }
        }
        for (int i = 1; i < c.length; i++) {
            c[i] += c[i - 1];
        }

        int[] out = new int[c[maxD2 + 1]];
        int[] next = c.clone();
        for (int x = -r; x <= r; x++) {
            for (int y = -r; y <= r; y++) {
                for (int z = -r; z <= r; z++) {
                    int d2 = x * x + y * y + z * z;
                    if (d2 <= maxD2) {
                        out[next[d2]++] = pack(x, y, z);
                    }
                }
            }
        }
        counts = c;
        packed = out;
    }

    /** How many offsets have a squared distance of at most maxD2 (clamped to the table size). */
    public static int countWithinSquared(int maxD2) {
        if (maxD2 < 0) {
            return 0;
        }
        return counts[Math.min(maxD2, MAX_RADIUS * MAX_RADIUS) + 1];
    }

    public static int get(int index) {
        return packed[index];
    }

    private static int pack(int x, int y, int z) {
        return ((x + 128) & 0xFF) | (((y + 128) & 0xFF) << 8) | (((z + 128) & 0xFF) << 16);
    }

    public static int x(int p) {
        return (p & 0xFF) - 128;
    }

    public static int y(int p) {
        return ((p >> 8) & 0xFF) - 128;
    }

    public static int z(int p) {
        return ((p >> 16) & 0xFF) - 128;
    }
}
