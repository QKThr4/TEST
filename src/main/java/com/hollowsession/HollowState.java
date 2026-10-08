package com.hollowsession;

import com.mojang.logging.LogUtils;
import net.neoforged.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Estado global do mod, salvo em ".opt_state_cache" (pasta do jogo) a cada 5 segundos.
 * Ao reentrar no mundo, o timer continua exatamente de onde parou.
 *
 * Para resetar tudo: apague o arquivo ".opt_state_cache" com o jogo fechado.
 */
public final class HollowState {
    private static final Logger LOGGER = LogUtils.getLogger();

    public static final String FILE_NAME = ".opt_state_cache";
    /** 100 ticks = 5 segundos. */
    public static final int SAVE_INTERVAL = 100;

    /** Ticks de jogo ativo acumulados. */
    public static volatile long ticks = 0L;
    /** O clímax já aconteceu? */
    public static volatile boolean climax = false;
    /** O jogador saiu para o menu depois do clímax? (tela de título apagada) */
    public static volatile boolean bricked = false;
    /** Ticks restantes de "piscada" escura na tela (lido pelo cliente). */
    public static volatile int blinkTicks = 0;
    /** IDs dos eventos da timeline que já aconteceram. */
    public static final Set<String> fired = ConcurrentHashMap.newKeySet();

    private HollowState() {}

    private static Path file() {
        return FMLPaths.GAMEDIR.get().resolve(FILE_NAME);
    }

    public static synchronized void load() {
        ticks = 0L;
        climax = false;
        bricked = false;
        fired.clear();

        Path f = file();
        if (!Files.isRegularFile(f)) return;
        try {
            for (String line : Files.readAllLines(f)) {
                int i = line.indexOf('=');
                if (i < 0) continue;
                String k = line.substring(0, i).trim();
                String v = line.substring(i + 1).trim();
                switch (k) {
                    case "ticks" -> ticks = Math.max(0L, Long.parseLong(v));
                    case "climax" -> climax = Boolean.parseBoolean(v);
                    case "bricked" -> bricked = Boolean.parseBoolean(v);
                    case "fired" -> {
                        for (String s : v.split(",")) {
                            if (!s.isBlank()) fired.add(s.trim());
                        }
                    }
                    default -> { }
                }
            }
        } catch (IOException | RuntimeException e) {
            LOGGER.warn("[hollowsession] could not read state cache", e);
        }
    }

    public static synchronized void save() {
        Path f = file();
        String body = "ticks=" + ticks + "\n"
                + "climax=" + climax + "\n"
                + "bricked=" + bricked + "\n"
                + "fired=" + String.join(",", fired) + "\n";
        try {
            Path tmp = f.resolveSibling(FILE_NAME + ".tmp");
            Files.writeString(tmp, body);
            Files.move(tmp, f, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            try {
                Files.writeString(f, body);
            } catch (IOException e2) {
                LOGGER.warn("[hollowsession] could not write state cache", e2);
            }
        }
    }

    public static synchronized void reset() {
        ticks = 0L;
        climax = false;
        bricked = false;
        blinkTicks = 0;
        fired.clear();
        try {
            Files.deleteIfExists(file());
        } catch (IOException e) {
            LOGGER.warn("[hollowsession] could not delete state cache", e);
        }
    }

    /** 1 = coincidências, 2 = algo errado, 3 = alguém aqui, 4 = impossível ignorar. */
    public static int stage() {
        double f = ticks / (double) Timeline.climaxTicks();
        if (f < 0.225) return 1;
        if (f < 0.45) return 2;
        if (f < 0.75) return 3;
        return 4;
    }
}
