package com.hollowsession;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.arguments.IntegerArgumentType;
import net.minecraft.commands.CommandSourceStack;
import net.minecraft.commands.Commands;
import net.minecraft.network.chat.Component;

/** Comandos de TESTE (exigem permissão 2 / cheats ligados). Não aparecem em nada do jogo. */
public final class HollowCommands {
    private HollowCommands() {}

    public static void register(CommandDispatcher<CommandSourceStack> d) {
        d.register(Commands.literal("hollow")
                .requires(src -> src.hasPermission(2))
                .then(Commands.literal("time").executes(ctx -> time(ctx.getSource())))
                .then(Commands.literal("skip")
                        .then(Commands.argument("minutes", IntegerArgumentType.integer(1, 240))
                                .executes(ctx -> skip(ctx.getSource(),
                                        IntegerArgumentType.getInteger(ctx, "minutes")))))
                .then(Commands.literal("anomaly").executes(ctx -> anomaly(ctx.getSource())))
                .then(Commands.literal("climax").executes(ctx -> climax(ctx.getSource())))
                .then(Commands.literal("reset").executes(ctx -> reset(ctx.getSource()))));
    }

    private static boolean noSession(CommandSourceStack src) {
        if (ServerEvents.session() == null) {
            src.sendFailure(Component.literal("Sem sessão ativa."));
            return true;
        }
        return false;
    }

    private static int time(CommandSourceStack src) {
        long secs = HollowState.ticks / 20L;
        String msg = String.format("%02d:%02d | fase %d | clímax=%s | bricked=%s",
                secs / 60, secs % 60, HollowState.stage(), HollowState.climax, HollowState.bricked);
        src.sendSuccess(() -> Component.literal(msg), false);
        return 1;
    }

    private static int skip(CommandSourceStack src, int minutes) {
        HollowState.ticks += minutes * 1200L;
        Timeline.markPast(HollowState.ticks);
        HollowState.save();
        src.sendSuccess(() -> Component.literal("Avançou " + minutes + " min."), false);
        return 1;
    }

    private static int anomaly(CommandSourceStack src) {
        if (noSession(src)) return 0;
        ServerEvents.session().manifestAnomaly(0);
        return 1;
    }

    private static int climax(CommandSourceStack src) {
        if (noSession(src)) return 0;
        ServerEvents.session().startClimax();
        return 1;
    }

    private static int reset(CommandSourceStack src) {
        if (noSession(src)) return 0;
        ServerEvents.session().resetAll();
        src.sendSuccess(() -> Component.literal("Estado resetado."), false);
        return 1;
    }
}
