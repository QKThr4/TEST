package com.hollowsession;

import net.minecraft.ChatFormatting;
import net.minecraft.core.Holder;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.Mth;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.animal.Animal;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.UUID;

/** Estado de execução do jogador atual (pensado para singleplayer / servidor integrado). */
public final class Session {
    public ServerPlayer player;
    public final UUID uuid;
    /** Nome de usuário do SO (só em singleplayer; fora disso usa o nick do jogo). */
    public final String user;
    public Anomaly anomaly;

    private record Task(int at, Runnable run) {}

    private final List<Task> tasks = new ArrayList<>();
    private int age = 0;
    private int stareLeft = 0;
    private List<Animal> stareTargets = List.of();
    private long nextBeatAt = 0L;

    public Session(ServerPlayer p) {
        this.player = p;
        this.uuid = p.getUUID();
        String os = null;
        if (p.server.isSingleplayer()) {
            os = System.getProperty("user.name");
        }
        this.user = (os == null || os.isBlank()) ? p.getGameProfile().getName() : os.trim();
    }

    // ------------------------------------------------------------------ ciclo de vida

    public void onEnter() {
        if (HollowState.climax) {
            applyTrap();
            later(80, () -> chat(Scenes.t("back_end", user).withStyle(ChatFormatting.RED)));
        } else if (HollowState.ticks > Timeline.at(240)) {
            later(80, () -> whisper("back", user));
        }
    }

    public void shutdown() {
        if (anomaly != null) {
            anomaly.discard();
            anomaly = null;
        }
        tasks.clear();
    }

    public void resetAll() {
        shutdown();
        HollowState.reset();
        stareLeft = 0;
        nextBeatAt = 0L;
        player.removeAllEffects();
    }

    // ------------------------------------------------------------------ tick

    public void tick() {
        if (!player.isAlive() || player.isSpectator()) return;
        age++;
        runTasks();

        HollowState.ticks++;
        if (HollowState.blinkTicks > 0) HollowState.blinkTicks--;
        if (HollowState.ticks % HollowState.SAVE_INTERVAL == 0) HollowState.save(); // a cada 5s

        if (!HollowState.climax) Timeline.runDue(this);

        if (anomaly != null && !anomaly.tick(this)) anomaly = null;
        stareTick();
        heartbeatTick();

        if (HollowState.climax && age % 20 == 0) applyTrap();
    }

    // ------------------------------------------------------------------ agendador

    public void later(int delayTicks, Runnable r) {
        tasks.add(new Task(age + Math.max(0, delayTicks), r));
    }

    private void runTasks() {
        if (tasks.isEmpty()) return;
        List<Task> due = new ArrayList<>();
        for (Iterator<Task> it = tasks.iterator(); it.hasNext(); ) {
            Task t = it.next();
            if (t.at() <= age) {
                due.add(t);
                it.remove();
            }
        }
        for (Task t : due) t.run().run();
    }

    // ------------------------------------------------------------------ chat

    public void chat(Component c) {
        player.sendSystemMessage(c);
    }

    public void whisper(String key, Object... args) {
        chat(Scenes.t(key, args).withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    // ------------------------------------------------------------------ anomalia

    public void manifestAnomaly(int attempt) {
        if (anomaly != null || HollowState.climax) return;
        Anomaly a = Anomaly.tryCreate(this);
        if (a != null) {
            anomaly = a;
        } else if (attempt < 8) {
            // terreno inválido / chunk não carregado: tenta de novo em 10s
            later(200, () -> manifestAnomaly(attempt + 1));
        }
    }

    // ------------------------------------------------------------------ animais encarando

    public void startStare(int ticks) {
        stareLeft = ticks;
        stareTargets = List.of();
    }

    private void stareTick() {
        if (stareLeft <= 0) return;
        stareLeft--;
        if (stareLeft % 20 == 0 || stareTargets.isEmpty()) {
            stareTargets = player.serverLevel().getEntitiesOfClass(
                    Animal.class, player.getBoundingBox().inflate(24.0), Animal::isAlive);
        }
        for (Animal a : stareTargets) {
            if (a.isRemoved()) continue;
            a.getNavigation().stop();
            a.getLookControl().setLookAt(player, 60.0f, 60.0f);
            double dx = player.getX() - a.getX();
            double dz = player.getZ() - a.getZ();
            float yaw = (float) (Mth.atan2(dz, dx) * (180.0 / Math.PI)) - 90.0f;
            a.setYRot(yaw);
            a.setYHeadRot(yaw);
            a.setYBodyRot(yaw);
        }
    }

    // ------------------------------------------------------------------ batimentos

    private void heartbeatTick() {
        long t = HollowState.ticks;
        long start = Timeline.at(Timeline.HEARTBEAT_SECONDS);
        if (t < start || t < nextBeatAt) return;
        double prog = Mth.clamp((t - start) / (double) (Timeline.climaxTicks() - start), 0.0, 1.0);
        int period = (int) Math.round(70 - 46 * prog);       // 70 ticks -> 24 ticks
        float vol = (float) (0.25 + 0.75 * prog);            // baixo -> alto
        Scenes.sound(player, "entity.warden.heartbeat", vol, 1.0f);
        nextBeatAt = t + period;
    }

    // ------------------------------------------------------------------ clímax

    public void startClimax() {
        if (HollowState.climax) return;
        HollowState.fired.add("climax");
        HollowState.climax = true;
        HollowState.save();

        if (anomaly != null) {
            anomaly.discard();
            anomaly = null;
        }
        applyTrap();

        // NÃO fecha o jogo. Prende o jogador dentro do mundo e deixa ele decidir sair.
        later(60, () -> whisper("c1"));
        later(160, () -> whisper("c2", user));
        later(280, () -> whisper("c3"));
        later(420, () -> chat(Scenes.t("c4", user).withStyle(ChatFormatting.RED)));
    }

    /** Blindness 255 + Slowness 255 infinitos (reaplicados caso sejam removidos). */
    public void applyTrap() {
        addInfinite(MobEffects.BLINDNESS, 255);
        addInfinite(MobEffects.MOVEMENT_SLOWDOWN, 255);
        // Proteção oculta para o jogador não morrer e "escapar" via respawn durante a gravação.
        addInfinite(MobEffects.DAMAGE_RESISTANCE, 4);
    }

    private void addInfinite(Holder<MobEffect> effect, int amplifier) {
        if (!player.hasEffect(effect)) {
            player.addEffect(new MobEffectInstance(
                    effect, MobEffectInstance.INFINITE_DURATION, amplifier, false, false, false));
        }
    }
}
