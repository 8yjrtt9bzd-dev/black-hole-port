package com.example.blackholeport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.world.phys.Vec3;

import java.util.UUID;

/** One black hole: position, size and the countdown before it wakes up. */
public class BlackHole {
    public final int id;
    public Vec3 pos;
    public float size;
    public int countdown;

    /** Server-only, not saved. */
    public int clearCursor = 0;
    public UUID target;

    public BlackHole(int id, Vec3 pos, float size, int countdown) {
        this.id = id;
        this.pos = pos;
        this.size = size;
        this.countdown = countdown;
    }

    public CompoundTag save() {
        CompoundTag tag = new CompoundTag();
        tag.putDouble("X", pos.x);
        tag.putDouble("Y", pos.y);
        tag.putDouble("Z", pos.z);
        tag.putFloat("Size", size);
        tag.putInt("Countdown", countdown);
        tag.putInt("Id", id);
        return tag;
    }

    public static BlackHole load(CompoundTag tag) {
        return new BlackHole(
                tag.getInt("Id"),
                new Vec3(tag.getDouble("X"), tag.getDouble("Y"), tag.getDouble("Z")),
                tag.getFloat("Size"),
                tag.getInt("Countdown"));
    }
}
