package com.example.blackholeport;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.event.server.ServerStartingEvent;
import net.minecraftforge.event.server.ServerStoppedEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

@Mod.EventBusSubscriber(modid = BlackHolePortMod.MODID)
public class ServerEvents {

    @SubscribeEvent
    public static void onServerStarting(ServerStartingEvent event) {
        BlackHoleSettings.loadFromConfig();
        ServerLogic.reset();
    }

    @SubscribeEvent
    public static void onServerStopped(ServerStoppedEvent event) {
        ServerLogic.reset();
    }

    @SubscribeEvent
    public static void onLevelTick(TickEvent.LevelTickEvent event) {
        if (event.phase == TickEvent.Phase.END && !event.level.isClientSide && event.level instanceof ServerLevel level) {
            ServerLogic.tickLevel(level);
        }
    }

    // A player who joins, changes dimension or respawns must get the state of the dimension they are now in.

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlackHoleNetwork.sendToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlackHoleNetwork.sendToPlayer(player);
        }
    }

    @SubscribeEvent
    public static void onRespawn(PlayerEvent.PlayerRespawnEvent event) {
        if (event.getEntity() instanceof ServerPlayer player) {
            BlackHoleNetwork.sendToPlayer(player);
        }
    }
}
