package com.hollowsession;

import net.neoforged.fml.common.Mod;

/**
 * Hollow Session - mod de horror ARG para NeoForge 1.21.1.
 * Toda a "quebra da quarta parede" acontece DENTRO da janela do Minecraft
 * (chat, sons, itens, placas). Nunca fecha o jogo e nunca abre programas externos.
 */
@Mod(HollowSession.MODID)
public class HollowSession {
    public static final String MODID = "hollowsession";

    public HollowSession() {
        // Carrega o cache cedo para que a flag "bricked" valha já na tela de título.
        HollowState.load();
    }
}
