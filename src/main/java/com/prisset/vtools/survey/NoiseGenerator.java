package com.prisset.vtools.survey;

/**
 * Simplex noise generator for smooth continuous randomness.
 * Used for camera jitter, path drift, and behavioral variation.
 * Lightweight implementation -- no external dependencies.
 */
public final class NoiseGenerator {

    private static final int[] PERM = new int[512];
    private static final int[] PERM_ORIG = {
        151,160,137,91,90,15,131,13,201,95,96,53,194,233,7,225,
        140,36,103,30,69,142,8,99,37,240,21,10,23,190,6,148,
        247,120,234,75,0,26,197,62,94,252,219,203,117,35,11,32,
        57,177,33,88,237,149,56,87,174,20,125,136,171,168,68,175,
        74,165,71,134,139,48,27,166,77,146,158,231,83,111,229,122,
        60,211,133,230,220,105,92,41,55,46,245,40,244,102,143,54,
        65,25,63,161,1,216,80,73,209,76,132,187,208,89,18,169,
        200,196,135,130,116,188,159,86,164,100,109,198,173,186,3,64,
        52,217,226,250,124,123,5,202,38,147,118,126,255,82,85,212,
        207,206,59,227,47,16,58,17,182,189,28,42,223,183,170,213,
        119,248,152,2,44,154,163,70,221,153,101,155,167,43,172,9,
        129,22,39,253,19,98,108,110,79,113,224,232,178,185,112,104,
        218,246,97,228,251,34,242,193,238,210,144,12,191,179,162,241,
        81,51,145,235,249,14,239,107,49,192,214,31,181,199,106,157,
        184,84,204,176,115,121,50,45,127,4,150,254,138,236,205,93,
        222,114,67,29,24,72,243,141,128,195,78,66,215,61,156,180
    };

    static {
        for (int i = 0; i < 512; i++) {
            PERM[i] = PERM_ORIG[i & 255];
        }
    }

    private static final float F2 = 0.5f * ((float) Math.sqrt(3.0) - 1.0f);
    private static final float G2 = (3.0f - (float) Math.sqrt(3.0)) / 6.0f;

    private static final float[][] GRAD2 = {
        {1, 1}, {-1, 1}, {1, -1}, {-1, -1},
        {1, 0}, {-1, 0}, {0, 1}, {0, -1}
    };

    private NoiseGenerator() {}

    // --- 1D Simplex Noise ---

    public static float noise1D(float x) {
        return noise2D(x, 0.0f);
    }

    // --- 2D Simplex Noise ---

    public static float noise2D(float xin, float yin) {
        float s = (xin + yin) * F2;
        int i = fastFloor(xin + s);
        int j = fastFloor(yin + s);
        float t = (i + j) * G2;

        float x0 = xin - (i - t);
        float y0 = yin - (j - t);

        int i1, j1;
        if (x0 > y0) { i1 = 1; j1 = 0; }
        else { i1 = 0; j1 = 1; }

        float x1 = x0 - i1 + G2;
        float y1 = y0 - j1 + G2;
        float x2 = x0 - 1.0f + 2.0f * G2;
        float y2 = y0 - 1.0f + 2.0f * G2;

        int ii = i & 255;
        int jj = j & 255;

        float n0 = contribution(x0, y0, ii, jj);
        float n1 = contribution(x1, y1, ii + i1, jj + j1);
        float n2 = contribution(x2, y2, ii + 1, jj + 1);

        return 70.0f * (n0 + n1 + n2);
    }

    private static float contribution(float x, float y, int gi, int gj) {
        float t = 0.5f - x * x - y * y;
        if (t < 0) return 0.0f;
        t *= t;
        int idx = PERM[gi + PERM[gj & 255] & 255] & 7;
        return t * t * (GRAD2[idx][0] * x + GRAD2[idx][1] * y);
    }

    // --- Fractal (octave) noise ---

    public static float fractalNoise(float x, int octaves, float persistence) {
        float total = 0;
        float amplitude = 1.0f;
        float frequency = 1.0f;
        float maxValue = 0;

        for (int i = 0; i < octaves; i++) {
            total += noise1D(x * frequency) * amplitude;
            maxValue += amplitude;
            amplitude *= persistence;
            frequency *= 2.0f;
        }

        return total / maxValue;
    }

    // --- Convenience methods for specific use cases ---

    /**
     * Camera yaw jitter. Very slow, very small. Continuous.
     * Range: approximately +-0.15 degrees
     */
    public static float cameraJitterYaw(long tick) {
        float time = tick * 0.03f;
        return fractalNoise(time + 1000.0f, 3, 0.5f) * 0.15f;
    }

    /**
     * Camera pitch jitter. Different seed from yaw. Even smaller.
     * Range: approximately +-0.08 degrees
     */
    public static float cameraJitterPitch(long tick) {
        float time = tick * 0.025f;
        return fractalNoise(time + 5000.0f, 3, 0.5f) * 0.08f;
    }

    /**
     * Walking direction drift. Very slow variation.
     * Range: approximately +-0.5 degrees
     */
    public static float pathDrift(long tick) {
        float time = tick * 0.01f;
        return fractalNoise(time + 9000.0f, 2, 0.6f) * 0.5f;
    }

    /**
     * Speed micro-variation during walking.
     * Range: approximately +-0.03
     */
    public static float speedVariation(long tick) {
        float time = tick * 0.08f;
        return fractalNoise(time + 3000.0f, 2, 0.4f) * 0.03f;
    }

    /**
     * Block aim offset for a specific block position.
     * Deterministic per-block (same block = same offset within session).
     */
    public static float aimOffset(int blockHash, float axis) {
        return noise2D(blockHash * 0.37f + axis * 100.0f, axis * 0.53f) * 0.25f;
    }

    private static int fastFloor(float x) {
        int xi = (int) x;
        return x < xi ? xi - 1 : xi;
    }
}
