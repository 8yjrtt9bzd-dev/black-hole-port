package com.example.blackholeport;

import com.mojang.brigadier.arguments.BoolArgumentType;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import com.mojang.brigadier.exceptions.CommandSyntaxException;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.RegisterCommandsEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * /blackhole spawn | kill | followSpeed <n> | growSpeed <n> | pullSpeed <n> | damage <bool> | pause | resume
 * The speed commands take a whole-number multiplier of the default, like the original (1 = default).
 */
@Mod.EventBusSubscriber(modid = BlackHolePortMod.MODID)
public class BlackHoleCommands {

    @SubscribeEvent
    public static void onRegisterCommands(RegisterCommandsEvent event) {
        event.getDispatcher().register(Commands.literal("blackhole")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("spawn").executes(ctx -> spawn(ctx.getSource())))
                .then(Commands.literal("kill").executes(ctx -> kill(ctx.getSource())))
                .then(Commands.literal("followSpeed")
                        .then(Commands.argument("followSpeed", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> followSpeed(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "followSpeed")))))
                .then(Commands.literal("growSpeed")
                        .then(Commands.argument("growSpeed", IntegerArgumentType.integer(0, 1000))
                                .executes(ctx -> growSpeed(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "growSpeed")))))
                .then(Commands.literal("pullSpeed")
                        .then(Commands.argument("pullSpeed", IntegerArgumentType.integer(0, 32))
                                .executes(ctx -> pullSpeed(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "pullSpeed")))))
                .then(Commands.literal("damage")
                        .then(Commands.argument("enabled", BoolArgumentType.bool())
                                .executes(ctx -> damage(ctx.getSource(), BoolArgumentType.getBool(ctx, "enabled")))))
                .then(Commands.literal("pause").executes(ctx -> setAll(ctx.getSource(), false)))
                .then(Commands.literal("resume").executes(ctx -> setAll(ctx.getSource(), true))));
    }

    private static int spawn(CommandSourceStack source) throws CommandSyntaxException {
        ServerPlayer player = source.getPlayerOrException();
        ServerLevel level = source.getLevel();
        BlackHoleSavedData data = BlackHoleSavedData.get(level);
        if (!data.holes().isEmpty()) {
            source.sendFailure(Component.literal("There is already a black hole in this world"));
            return 0;
        }
        data.holes().add(new BlackHole(data.nextId(), player.position(), 1.0F, BlackHoleConfig.COUNTDOWN_TICKS.get()));
        data.setDirty();
        BlackHoleNetwork.sendToLevel(level);
        source.sendSuccess(() -> Component.literal("Created new black hole"), true);
        return 1;
    }

    private static int kill(CommandSourceStack source) {
        ServerLevel level = source.getLevel();
        BlackHoleSavedData data = BlackHoleSavedData.get(level);
        if (data.holes().isEmpty()) {
            source.sendFailure(Component.literal("There are no black holes in this world"));
            return 0;
        }
        data.holes().clear();
        data.setDirty();
        BlackHoleNetwork.sendToLevel(level);
        source.sendSuccess(() -> Component.literal("Eliminated existing black hole"), true);
        return 1;
    }

    private static int followSpeed(CommandSourceStack source, int value) {
        int old = Math.round(BlackHoleSettings.followSpeed / BlackHoleSettings.DEFAULT_FOLLOW_SPEED);
        BlackHoleSettings.followSpeed = BlackHoleSettings.DEFAULT_FOLLOW_SPEED * value;
        source.sendSuccess(() -> Component.literal("Changed following speed from " + old + " to " + value), true);
        return 1;
    }

    private static int growSpeed(CommandSourceStack source, int value) {
        int old = Math.round(BlackHoleSettings.growSpeed / BlackHoleSettings.DEFAULT_GROW_SPEED);
        BlackHoleSettings.growSpeed = BlackHoleSettings.DEFAULT_GROW_SPEED * value;
        source.sendSuccess(() -> Component.literal("Changed grow speed from " + old + " to " + value), true);
        return 1;
    }

    private static int pullSpeed(CommandSourceStack source, int value) {
        int old = Math.round(BlackHoleSettings.pullSpeed / BlackHoleSettings.DEFAULT_PULL_SPEED);
        BlackHoleSettings.pullSpeed = BlackHoleSettings.DEFAULT_PULL_SPEED * value;
        source.sendSuccess(() -> Component.literal("Changed pull speed from " + old + " to " + value), true);
        return 1;
    }

    private static int damage(CommandSourceStack source, boolean enabled) {
        BlackHoleSettings.damage = enabled;
        source.sendSuccess(() -> Component.literal(enabled ? "Enabled damage" : "Disabled damage"), true);
        return 1;
    }

    /** pause turns follow, pull, grow and damage off; resume turns them back on. */
    private static int setAll(CommandSourceStack source, boolean on) {
        BlackHoleSettings.follow = on;
        BlackHoleSettings.pull = on;
        BlackHoleSettings.grow = on;
        BlackHoleSettings.damage = on;
        source.sendSuccess(() -> Component.literal(on ? "Resumed the black hole" : "Paused the black hole"), true);
        return 1;
    }
}
