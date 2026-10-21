package com.example.blackholeport;

import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.level.saveddata.SavedData;

import java.util.ArrayList;
import java.util.List;

/** The black holes of one dimension, saved with the world. */
public class BlackHoleSavedData extends SavedData {
    private static final String NAME = "blackholeport_holes";

    private final List<BlackHole> holes = new ArrayList<>();
    private int nextId = 1;

    public static BlackHoleSavedData get(ServerLevel level) {
        return level.getDataStorage().computeIfAbsent(BlackHoleSavedData::load, BlackHoleSavedData::new, NAME);
    }

    public static BlackHoleSavedData load(CompoundTag tag) {
        BlackHoleSavedData data = new BlackHoleSavedData();
        data.nextId = Math.max(1, tag.getInt("NextId"));
        ListTag list = tag.getList("Holes", Tag.TAG_COMPOUND);
        for (int i = 0; i < list.size(); i++) {
            data.holes.add(BlackHole.load(list.getCompound(i)));
        }
        return data;
    }

    @Override
    public CompoundTag save(CompoundTag tag) {
        ListTag list = new ListTag();
        for (BlackHole hole : holes) {
            list.add(hole.save());
        }
        tag.put("Holes", list);
        tag.putInt("NextId", nextId);
        return tag;
    }

    public List<BlackHole> holes() {
        return holes;
    }

    public int nextId() {
        setDirty();
        return nextId++;
    }
}
