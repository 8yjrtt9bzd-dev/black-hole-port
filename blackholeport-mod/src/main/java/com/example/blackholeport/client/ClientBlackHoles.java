package com.example.blackholeport.client;

import com.example.blackholeport.BlackHoleNetwork;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.List;

/** What the client knows about the black holes of its dimension, plus the debris and the local-player pull. */
public final class ClientBlackHoles {
    public static final class Particle {
        public Vec3 pos;
        public final ItemStack stack;

        Particle(Vec3 pos, ItemStack stack) {
            this.pos = pos;
            this.stack = stack;
        }
    }

    public static final class Hole {
        public final int id;
        public Vec3 pos = Vec3.ZERO;
        public float size = 1.0F;
        public int countdown = 0;
        public final List<Particle> particles = new ArrayList<>();

        Hole(int id) {
            this.id = id;
        }
    }

    public static final List<Hole> HOLES = new ArrayList<>();
    public static boolean pull = true;
    public static boolean damage = true;
    public static float pullSpeed = 0.125F;
    /** Set while the camera is inside a hole; the HUD then fills the screen with black. */
    public static boolean blackedOut = false;

    private ClientBlackHoles() {
    }

    public static void apply(BlackHoleNetwork.SyncPacket packet) {
        pull = packet.pull();
        pullSpeed = packet.pullSpeed();
        damage = packet.damage();

        List<Hole> next = new ArrayList<>();
        for (BlackHoleNetwork.HoleData data : packet.holes()) {
            Hole hole = null;
            for (Hole existing : HOLES) {
                if (existing.id == data.id()) {
                    hole = existing;
                    break;
                }
            }
            if (hole == null) {
                hole = new Hole(data.id());
            }
            hole.pos = new Vec3(data.x(), data.y(), data.z());
            hole.size = data.size();
            hole.countdown = data.countdown();
            next.add(hole);
        }
        HOLES.clear();
        HOLES.addAll(next);
    }

    public static void clear() {
        HOLES.clear();
        blackedOut = false;
    }

    /** Runs at the end of every client tick. */
    public static void tick() {
        Minecraft mc = Minecraft.getInstance();
        if (mc.level == null || mc.player == null) {
            if (!HOLES.isEmpty()) {
                clear();
            }
            return;
        }
        for (Hole hole : HOLES) {
            if (hole.countdown <= 0) {
                if (damage) {
                    spawnDebris(mc, hole);
                }
                pullLocalPlayer(mc.player, hole);
            }
        }
    }

    /** Same pull as the original: unit vector toward the hole times pullSpeed ^ (1.5 * distance / (size * 2.5)). */
    private static void pullLocalPlayer(LocalPlayer player, Hole hole) {
        if (!pull || player.isSpectator() || player.isCreative()) {
            return;
        }
        double d = hole.pos.distanceTo(player.position());
        Vec3 dir = hole.pos.subtract(player.position()).normalize();
        Vec3 pullVec = dir.scale(Math.pow(pullSpeed, d * 1.5 / (hole.size * 2.5F)));

        if (d > hole.size && player.getDeltaMovement().lengthSqr() < 3.0) {
            player.push(Mth.clamp(pullVec.x, -1.0, 1.0), Mth.clamp(pullVec.y, -1.0, 1.0), Mth.clamp(pullVec.z, -1.0, 1.0));
        }
        if (d < hole.size) {
            player.setDeltaMovement(Vec3.ZERO);
        }
    }

    /**
     * Visual only: the same random column sampling the server uses to peel the surface, but instead of removing
     * the block it adds a flying piece of debris (1 in 5, at most 2048, only within 256 blocks of the player).
     */
    private static void spawnDebris(Minecraft mc, Hole hole) {
        ClientLevel level = mc.level;
        LocalPlayer player = mc.player;
        RandomSource random = level.random;

        int radius = (int) hole.size;
        int pickupRadius = (int) (radius * 2.5F);
        int chunkRadius = Math.max(1, radius / 16) + 6;
        int x = Mth.floor(hole.pos.x);
        int z = Mth.floor(hole.pos.z);
        long pickupSq = (long) pickupRadius * pickupRadius;

        for (int cx = (x >> 4) - chunkRadius; cx <= (x >> 4) + chunkRadius; cx++) {
            for (int cz = (z >> 4) - chunkRadius; cz <= (z >> 4) + chunkRadius; cz++) {
                LevelChunk chunk = level.getChunkSource().getChunkNow(cx, cz);
                if (chunk == null) {
                    continue;
                }
                int rx = random.nextInt(16);
                int rz = random.nextInt(16);
                long dx = (long) cx * 16 + rx - x;
                long dz = (long) cz * 16 + rz - z;
                if (dx * dx + dz * dz >= pickupSq) {
                    continue;
                }
                if (random.nextInt(5) != 0 || hole.particles.size() >= 2048) {
                    continue;
                }
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, rx, rz);
                if (top < level.getMinBuildHeight() || top >= level.getMaxBuildHeight()) {
                    continue;
                }
                int ax = cx * 16 + rx;
                int az = cz * 16 + rz;
                double px = ax - player.getX();
                double pz = az - player.getZ();
                if (px * px + pz * pz >= 256.0 * 256.0) {
                    continue;
                }
                BlockState state = chunk.getBlockState(new net.minecraft.core.BlockPos(ax, top, az));
                if (state.isAir() || state.getBlock() instanceof LiquidBlock) {
                    continue;
                }
                ItemStack stack = new ItemStack(state.getBlock());
                if (!stack.isEmpty()) {
                    hole.particles.add(new Particle(new Vec3(ax, top, az), stack));
                }
            }
        }
    }
}
