package com.hollowsession.client;

import com.hollowsession.HollowSession;
import com.hollowsession.HollowState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.components.Button;
import net.minecraft.client.gui.components.events.GuiEventListener;
import net.minecraft.client.gui.screens.PauseScreen;
import net.minecraft.client.gui.screens.TitleScreen;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.neoforged.api.distmarker.Dist;
import net.neoforged.bus.api.SubscribeEvent;
import net.neoforged.fml.common.EventBusSubscriber;
import net.neoforged.neoforge.client.event.ClientPlayerNetworkEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.ScreenEvent;

import java.util.ArrayList;

/** Lado do cliente: tela de título apagada, botão de sair "hesitando" e piscada escura. */
@EventBusSubscriber(modid = HollowSession.MODID, bus = EventBusSubscriber.Bus.GAME, value = Dist.CLIENT)
public final class ClientEvents {
    private static long brickedSince = 0L;

    private ClientEvents() {}

    /** O jogador saiu manualmente do mundo DEPOIS do clímax: a tela de título morre. */
    @SubscribeEvent
    public static void onLoggingOut(ClientPlayerNetworkEvent.LoggingOut e) {
        if (HollowState.climax) {
            HollowState.bricked = true;
            HollowState.save();
        }
    }

    @SubscribeEvent
    public static void onScreenInit(ScreenEvent.Init.Post e) {
        if (HollowState.bricked && e.getScreen() instanceof TitleScreen) {
            // Remove TODOS os botões/widgets da tela de título.
            for (GuiEventListener l : new ArrayList<>(e.getListenersList())) {
                e.removeListener(l);
            }
            return;
        }
        if (e.getScreen() instanceof PauseScreen) {
            tweakPause(e);
        }
    }

    private static void tweakPause(ScreenEvent.Init.Post e) {
        int stage = HollowState.stage();
        if (stage < 3 || Minecraft.getInstance().level == null) return;
        for (GuiEventListener l : e.getListenersList()) {
            if (l instanceof Button b && b.getMessage().getContents() instanceof TranslatableContents tc) {
                String k = tc.getKey();
                if (k.equals("menu.returnToMenu") || k.equals("menu.disconnect")) {
                    b.setMessage(Component.translatable(
                            stage >= 4 ? "ui.hollowsession.leave4" : "ui.hollowsession.leave3"));
                }
            }
        }
    }

    @SubscribeEvent
    public static void onScreenRender(ScreenEvent.Render.Post e) {
        if (!HollowState.bricked || !(e.getScreen() instanceof TitleScreen ts)) return;

        if (brickedSince == 0L) brickedSince = System.currentTimeMillis();
        GuiGraphics g = e.getGuiGraphics();
        g.fill(0, 0, ts.width, ts.height, 0xFF000000);

        // Depois de 8s, uma única frase quase invisível. Apague este bloco se quiser preto total.
        if (System.currentTimeMillis() - brickedSince > 8000L) {
            Component c = Component.translatable("msg.hollowsession.bricked",
                    System.getProperty("user.name", "?"));
            g.drawCenteredString(Minecraft.getInstance().font, c, ts.width / 2, ts.height / 2, 0x330000);
        }
        Minecraft.getInstance().getMusicManager().stopPlaying();
    }

    /** Piscada escura de ~3 ticks quando a anomalia some. */
    @SubscribeEvent
    public static void onGui(RenderGuiEvent.Post e) {
        if (HollowState.blinkTicks > 0) {
            GuiGraphics g = e.getGuiGraphics();
            g.fill(0, 0, g.guiWidth(), g.guiHeight(), 0xC8000000);
        }
    }
}
