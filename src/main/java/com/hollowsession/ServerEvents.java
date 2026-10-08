package com.hollowsession;

import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.event.RegisterCommandsEvent;
import net.neoforged.neoforge.event.entity.EntityJoinLevelEvent;
import net.neoforged.neoforge.event.entity.player.PlayerEvent;
import net.neoforged.neoforge.event.server.ServerStoppingEvent;
import net.neoforged.neoforge.event.tick.PlayerTickEvent;

/** Eventos do lado do servidor (servidor integrado no singleplayer). */
@EventBusSubscriber(modid = HollowSession.MODID, bus = EventBusSubscriber.Bus.GAME)
public final class ServerEvents {
    private static Session session;

    private ServerEvents() {}

    public static Session session() {
        return session;
    }

    @SubscribeEvent
    public static void onLogin(PlayerEvent.PlayerLoggedInEvent e) {
        if (!(e.getEntity() instanceof ServerPlayer sp) || session != null) return;
        // Retoma o timer exatamente de onde parou (arquivo .opt_state_cache).
        HollowState.load();
        session = new Session(sp);
        session.onEnter();
    }

    @SubscribeEvent
    public static void onLogout(PlayerEvent.PlayerLoggedOutEvent e) {
        if (session != null && e.getEntity().getUUID().equals(session.uuid)) {
            session.shutdown();   // remove a anomalia antes de salvar o mundo
            HollowState.save();
            session = null;
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent.Post e) {
        if (session != null
                && e.getEntity() instanceof ServerPlayer sp
                && sp.getUUID().equals(session.uuid)) {
            session.player = sp;  // pode mudar após respawn
            session.tick();
        }
    }

    @SubscribeEvent
    public static void onServerStopping(ServerStoppingEvent e) {
        if (session != null) {
            session.shutdown();
            HollowState.save();
            session = null;
        }
    }

    /** Limpa ArmorStands "órfãos" da anomalia (ex.: o jogo crashou com ela ativa e o mundo foi salvo). */
    @SubscribeEvent
    public static void onEntityJoin(EntityJoinLevelEvent e) {
        if (e.getLevel().isClientSide()) return;
        Entity ent = e.getEntity();
        if (ent instanceof ArmorStand && ent.getTags().contains(Anomaly.TAG) && !Anomaly.isLive(ent)) {
            e.setCanceled(true);
        }
    }

    @SubscribeEvent
    public static void onCommands(RegisterCommandsEvent e) {
        HollowCommands.register(e.getDispatcher());
    }
}
