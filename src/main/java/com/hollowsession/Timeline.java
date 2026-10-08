package com.hollowsession;

import java.util.List;
import java.util.function.Consumer;

/**
 * Linha do tempo (em segundos de jogo ATIVO). Duração total padrão: 40 minutos.
 * Mude SCALE para esticar/encurtar tudo (1.5 = 60 min, 0.5 = 20 min).
 */
public final class Timeline {
    public static final double SCALE = 1.0;

    public static final int CLIMAX_SECONDS = 2400;      // 40:00
    public static final int HEARTBEAT_SECONDS = 2040;   // 34:00 - batimentos começam

    public record Ev(String id, int seconds, Consumer<Session> action) {}

    public static long at(int seconds) {
        return Math.round(seconds * SCALE * 20.0);
    }

    public static long climaxTicks() {
        return at(CLIMAX_SECONDS);
    }

    static final List<Ev> EVENTS = List.of(
            // ---- Fase 1: coincidências deniáveis (0:00 - 9:00) ----
            new Ev("cave", 150, s -> Scenes.sound(s.player, "ambient.cave", 0.7f, 0.8f)),
            new Ev("steps", 240, Scenes::footstepsBehind),
            new Ev("door", 330, Scenes::doorSounds),
            new Ev("joined", 420, Scenes::fakeJoinLeave),

            // ---- Fase 2: algo está errado (9:00 - 18:00) ----
            new Ev("stare1", 570, s -> s.startStare(400)),
            new Ev("w1", 660, s -> s.whisper("w1", s.user)),
            new Ev("anomaly1", 720, s -> s.manifestAnomaly(0)),          // 12:00 (regra original)
            new Ev("died", 840, Scenes::fakeDeath),
            new Ev("w2", 900, s -> s.whisper("w2")),
            new Ev("darkness1", 960, Scenes::darkness),

            // ---- Fase 3: alguém está aqui (18:00 - 30:00) ----
            new Ev("anomaly2", 1080, s -> s.manifestAnomaly(0)),
            new Ev("note", 1140, Scenes::giveNote),
            new Ev("sign", 1230, Scenes::placeSign),
            new Ev("w3", 1290, s -> s.whisper("w3")),
            new Ev("snap", 1320, Scenes::lookBehind),
            new Ev("anomaly3", 1350, s -> s.manifestAnomaly(0)),
            new Ev("w4", 1440, s -> s.whisper("w4")),
            new Ev("darkness2", 1500, Scenes::darkness),
            new Ev("anomaly4", 1560, s -> s.manifestAnomaly(0)),
            new Ev("forged", 1620, Scenes::forgedChat),
            new Ev("stare2", 1680, s -> s.startStare(500)),
            new Ev("anomaly5", 1740, s -> s.manifestAnomaly(0)),

            // ---- Fase 4: impossível ignorar (30:00 - 40:00) ----
            new Ev("w6", 1860, s -> s.whisper("w6", s.user)),
            new Ev("anomaly6", 1920, s -> s.manifestAnomaly(0)),
            new Ev("w7", 1980, s -> s.whisper("w7")),
            new Ev("killed", 2100, Scenes::fakeKilled),
            new Ev("anomaly7", 2160, s -> s.manifestAnomaly(0)),
            new Ev("w9", 2220, s -> s.whisper("w9")),
            new Ev("anomaly8", 2250, s -> s.manifestAnomaly(0)),
            new Ev("darkness3", 2310, Scenes::darkness),
            new Ev("w10", 2340, s -> s.whisper("w10", s.user, s.user, s.user)),
            new Ev("w11", 2370, s -> s.whisper("w11")),

            // ---- Clímax (40:00) ----
            new Ev("climax", CLIMAX_SECONDS, Session::startClimax)
    );

    /** Dispara tudo que já venceu e ainda não aconteceu. */
    static void runDue(Session s) {
        long t = HollowState.ticks;
        for (Ev ev : EVENTS) {
            if (t >= at(ev.seconds()) && HollowState.fired.add(ev.id())) {
                ev.action().accept(s);
            }
        }
    }

    /** Usado pelo /hollow skip: marca eventos muito antigos como já ocorridos (exceto o clímax). */
    static void markPast(long nowTicks) {
        for (Ev ev : EVENTS) {
            if (!ev.id().equals("climax") && at(ev.seconds()) < nowTicks - at(30)) {
                HollowState.fired.add(ev.id());
            }
        }
    }
}
