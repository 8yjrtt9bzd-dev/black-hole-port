package com.example.blackholeport;

/** The live settings. Loaded from the config when a world starts; the /blackhole commands change them. */
public final class BlackHoleSettings {
    public static final float DEFAULT_FOLLOW_SPEED = 0.00125F;
    public static final float DEFAULT_GROW_SPEED = 3.851E-5F;
    public static final float DEFAULT_PULL_SPEED = 0.125F;

    public static int limit = 64;
    public static boolean follow = true;
    public static float followSpeed = DEFAULT_FOLLOW_SPEED;
    public static boolean grow = true;
    public static float growSpeed = DEFAULT_GROW_SPEED;
    public static boolean pull = true;
    public static float pullSpeed = DEFAULT_PULL_SPEED;
    public static boolean damage = true;

    private BlackHoleSettings() {
    }

    public static void loadFromConfig() {
        limit = BlackHoleConfig.SIZE_LIMIT.get();
        follow = BlackHoleConfig.FOLLOW.get();
        followSpeed = (float) (double) BlackHoleConfig.FOLLOW_SPEED.get();
        grow = BlackHoleConfig.GROW.get();
        growSpeed = (float) (double) BlackHoleConfig.GROW_SPEED.get();
        pull = BlackHoleConfig.PULL.get();
        pullSpeed = (float) (double) BlackHoleConfig.PULL_SPEED.get();
        damage = BlackHoleConfig.DAMAGE.get();
    }
}
