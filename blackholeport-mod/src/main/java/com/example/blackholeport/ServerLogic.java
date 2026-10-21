package com.example.blackholeport;

import net.minecraft.core.BlockPos;
import net.minecraft.resources.ResourceKey;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.LiquidBlock;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.levelgen.Heightmap;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * The server side of a black hole, following the original mod's tick order. While the countdown runs nothing
 * happens. After that, every tick: grow, destroy terrain, kill players inside, pull and kill entities, follow.
 */
public final class ServerLogic {
    private static final Set<ResourceKey<Level>> SYNCED = new HashSet<>();

    private ServerLogic() {
    }

    public static void reset() {
        SYNCED.clear();
    }

    public static void tickLevel(ServerLevel level) {
        BlackHoleSavedData data = BlackHoleSavedData.get(level);
        List<BlackHole> holes = data.holes();
        if (holes.isEmpty()) {
            if (SYNCED.remove(level.dimension())) {
                BlackHoleNetwork.sendToLevel(level); // tell clients the last hole is gone
            }
            return;
        }

        for (BlackHole hole : holes) {
            if (hole.countdown <= 0) {
                tickSize(hole);
                if (BlackHoleSettings.damage) {
                    destroy(level, hole);
                }
                tickPlayers(level, hole);
                tickEntities(level, hole);
                tickTarget(level, hole);
            } else {
                hole.countdown--;
            }
        }
        data.setDirty();
        SYNCED.add(level.dimension());
        BlackHoleNetwork.sendToLevel(level);
    }

    // ------------------------------------------------------------------ growth, following

    private static void tickSize(BlackHole hole) {
        if (BlackHoleSettings.grow) {
            hole.size = Math.min((float) BlackHoleSettings.limit, hole.size * (1.0F + BlackHoleSettings.growSpeed));
        }
    }

    private static boolean validTarget(ServerPlayer p, BlackHole hole) {
        return p.isAlive() && !p.isSpectator() && !p.isCreative()
                && p.position().distanceToSqr(hole.pos) < 1048576.0;
    }

    private static void tickTarget(ServerLevel level, BlackHole hole) {
        if (!BlackHoleSettings.follow) {
            return;
        }
        ServerPlayer target = null;
        if (hole.target != null) {
            ServerPlayer p = level.getServer().getPlayerList().getPlayer(hole.target);
            if (p != null && p.level() == level && validTarget(p, hole)) {
                target = p;
            }
        }
        if (target == null) {
            hole.target = null;
            for (ServerPlayer p : level.players()) {
                if (validTarget(p, hole)) {
                    hole.target = p.getUUID();
                    break;
                }
            }
            return;
        }
        Vec3 toward = target.position().subtract(hole.pos).normalize().scale(BlackHoleSettings.followSpeed);
        hole.pos = hole.pos.add(toward);
    }

    // ------------------------------------------------------------------ pulling and killing

    /** Pull strength: a unit vector toward the hole times pullSpeed ^ (1.5 * distance / (size * 2.5)). */
    private static Vec3 pullVector(BlackHole hole, Vec3 at, double distance) {
        Vec3 dir = hole.pos.subtract(at).normalize();
        double exponent = distance * 1.5 / (hole.size * 2.5F);
        return dir.scale(Math.pow(BlackHoleSettings.pullSpeed, exponent));
    }

    /** Players are pulled on their own client (that is where their movement lives); the server kills them. */
    private static void tickPlayers(ServerLevel level, BlackHole hole) {
        if (!BlackHoleSettings.pull) {
            return;
        }
        for (ServerPlayer player : level.players()) {
            if (player.isSpectator() || player.isCreative()) {
                continue;
            }
            double d = hole.pos.distanceTo(player.position());
            if (d < hole.size) {
                kill(level, player);
            }
        }
    }

    private static void tickEntities(ServerLevel level, BlackHole hole) {
        if (!BlackHoleSettings.pull) {
            return;
        }
        List<Entity> doomed = new ArrayList<>();
        for (Entity entity : level.getAllEntities()) {
            if (entity instanceof Player || entity.isRemoved()) {
                continue;
            }
            double d = hole.pos.distanceTo(entity.position());
            Vec3 pull = pullVector(hole, entity.position(), d);
            if (d > hole.size && entity.getDeltaMovement().lengthSqr() < 3.0) {
                entity.push(Mth.clamp(pull.x, -1.0, 1.0), Mth.clamp(pull.y, -1.0, 1.0), Mth.clamp(pull.z, -1.0, 1.0));
                entity.hurtMarked = true;
            }
            if (d < hole.size) {
                doomed.add(entity);
            }
        }
        for (Entity entity : doomed) {
            kill(level, entity);
            entity.setDeltaMovement(Vec3.ZERO);
        }
    }

    private static void kill(ServerLevel level, Entity entity) {
        if (entity instanceof LivingEntity living) {
            living.hurt(level.damageSources().genericKill(), Float.MAX_VALUE);
            if (!living.isDeadOrDying() && !(living instanceof ServerPlayer)) {
                living.discard();
            }
        } else {
            entity.discard();
        }
    }

    // ------------------------------------------------------------------ terrain

    private static void destroy(ServerLevel level, BlackHole hole) {
        int radius = (int) hole.size;
        int clearRadius = (int) (radius * 1.5F);
        int pickupRadius = (int) (radius * 2.5F);
        int x = Mth.floor(hole.pos.x);
        int y = Mth.floor(hole.pos.y);
        int z = Mth.floor(hole.pos.z);
        peel(level, hole, x, z, radius, pickupRadius);
        clear(level, hole, x, y, z, clearRadius);
    }

    /**
     * Surface peeling, like the original: in every loaded chunk nearby ONE random column is picked each tick.
     * If it is within the pickup radius (sideways only) its top block is removed, with a 1-in-sqrt(size) chance.
     */
    private static void peel(ServerLevel level, BlackHole hole, int x, int z, int radius, int pickupRadius) {
        RandomSource random = level.random;
        int chunkRadius = Math.max(1, radius / 16) + 6;
        int falloff = Math.max(1, (int) Math.sqrt(hole.size));
        long pickupSq = (long) pickupRadius * pickupRadius;
        int centerChunkX = x >> 4;
        int centerChunkZ = z >> 4;

        for (int cx = centerChunkX - chunkRadius; cx <= centerChunkX + chunkRadius; cx++) {
            for (int cz = centerChunkZ - chunkRadius; cz <= centerChunkZ + chunkRadius; cz++) {
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
                int top = chunk.getHeight(Heightmap.Types.WORLD_SURFACE, rx, rz);
                if (top < level.getMinBuildHeight() || top >= level.getMaxBuildHeight()) {
                    continue;
                }
                BlockPos pos = new BlockPos(cx * 16 + rx, top, cz * 16 + rz);
                BlockState state = chunk.getBlockState(pos);
                if (state.isAir() || state.getBlock() instanceof LiquidBlock
                        || state.getDestroySpeed(level, pos) < 0.0F) {
                    continue;
                }
                if (random.nextInt(falloff) == 0) {
                    level.setBlock(pos, Blocks.AIR.defaultBlockState(), 3);
                }
            }
        }
    }

    /**
     * Clearing sphere, like the original: every block closer than 1.5 x the size is removed. It is swept nearest
     * first through a fixed list, a limited number of blocks per tick, so a big hole does not freeze the server.
     */
    private static void clear(ServerLevel level, BlackHole hole, int ox, int oy, int oz, int clearRadius) {
        if (clearRadius <= 0) {
            return;
        }
        SphereOffsets.ensure();
        int total = SphereOffsets.countWithinSquared(clearRadius * clearRadius - 1);
        if (total <= 0) {
            return;
        }
        if (hole.clearCursor >= total) {
            hole.clearCursor = 0;
        }

        ChunkCache cache = new ChunkCache(level);
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        double edge = Math.max(0.0, clearRadius - 1.5);
        double edgeSq = edge * edge;
        int steps = Math.min(BlackHoleConfig.BLOCKS_SCANNED_PER_TICK.get(), total);
        int maxClear = BlackHoleConfig.BLOCKS_CLEARED_PER_TICK.get();
        int cleared = 0;

        for (int n = 0; n < steps; n++) {
            int packed = SphereOffsets.get(hole.clearCursor);
            if (++hole.clearCursor >= total) {
                hole.clearCursor = 0;
            }
            int dx = SphereOffsets.x(packed);
            int dy = SphereOffsets.y(packed);
            int dz = SphereOffsets.z(packed);
            pos.set(ox + dx, oy + dy, oz + dz);

            BlockState state = cache.get(pos);
            if (state == null || state.isAir() || state.getDestroySpeed(level, pos) < 0.0F) {
                continue;
            }
            // Inside: 2 | 16 = tell clients but skip neighbour updates. On the outer skin: 3 = update neighbours too.
            double distSq = (double) dx * dx + (double) dy * dy + (double) dz * dz;
            level.setBlock(pos.immutable(), Blocks.AIR.defaultBlockState(), distSq > edgeSq ? 3 : (2 | 16));

            if (++cleared >= maxClear) {
                break;
            }
        }
    }

    /** Remembers the last chunk looked up and never loads or generates one. */
    private static final class ChunkCache {
        private final Level level;
        private int chunkX = Integer.MIN_VALUE;
        private int chunkZ = Integer.MIN_VALUE;
        private LevelChunk chunk;

        ChunkCache(Level level) {
            this.level = level;
        }

        BlockState get(BlockPos pos) {
            if (pos.getY() < level.getMinBuildHeight() || pos.getY() >= level.getMaxBuildHeight()) {
                return null;
            }
            int cx = pos.getX() >> 4;
            int cz = pos.getZ() >> 4;
            if (cx != chunkX || cz != chunkZ) {
                chunkX = cx;
                chunkZ = cz;
                chunk = level.getChunkSource().getChunkNow(cx, cz);
            }
            return chunk == null ? null : chunk.getBlockState(pos);
        }
    }
}
