package com.hollowsession;

import net.minecraft.ChatFormatting;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.component.DataComponents;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvent;
import net.minecraft.sounds.SoundSource;
import net.minecraft.util.Mth;
import net.minecraft.util.RotationSegment;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.item.DyeColor;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.item.component.ItemLore;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.StandingSignBlock;
import net.minecraft.world.level.block.entity.SignBlockEntity;
import net.minecraft.world.level.block.entity.SignText;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.Vec3;

import java.util.List;

/** Pequenas cenas reutilizáveis usadas pela Timeline. */
public final class Scenes {
    private Scenes() {}

    // ------------------------------------------------------------------ helpers

    public static MutableComponent t(String key, Object... args) {
        return Component.translatable("msg.hollowsession." + key, args);
    }

    /** Direção horizontal para onde o jogador olha (baseada no yaw, funciona mesmo olhando pra cima/baixo). */
    public static Vec3 flatForward(ServerPlayer p) {
        float yaw = p.getYRot() * Mth.DEG_TO_RAD;
        return new Vec3(-Mth.sin(yaw), 0.0, Mth.cos(yaw));
    }

    public static void sound(ServerPlayer p, String id, float vol, float pitch) {
        soundAt(p, id, p.position(), vol, pitch);
    }

    public static void soundAt(ServerPlayer p, String id, Vec3 pos, float vol, float pitch) {
        SoundEvent ev = BuiltInRegistries.SOUND_EVENT.get(ResourceLocation.withDefaultNamespace(id));
        if (ev == null) return; // id inexistente: simplesmente não toca
        p.serverLevel().playSound(null, pos.x, pos.y, pos.z, ev, SoundSource.AMBIENT, vol, pitch);
    }

    private static String shorten(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max);
    }

    // ------------------------------------------------------------------ fase 1

    /** Passos atrás do jogador, cada vez mais perto... e param de repente. */
    public static void footstepsBehind(Session s) {
        for (int i = 0; i < 6; i++) {
            final int step = i;
            s.later(i * 11, () -> {
                ServerPlayer p = s.player;
                ServerLevel lvl = p.serverLevel();
                double dist = 9.0 - step * 1.1;
                Vec3 pos = p.position().subtract(flatForward(p).scale(dist));
                BlockState under = lvl.getBlockState(p.blockPosition().below());
                SoundEvent stepSound = under.getSoundType().getStepSound();
                lvl.playSound(null, pos.x, pos.y, pos.z, stepSound, SoundSource.AMBIENT,
                        0.55f, 0.85f + lvl.random.nextFloat() * 0.2f);
            });
        }
    }

    /** Uma porta abre e fecha em algum lugar perto. */
    public static void doorSounds(Session s) {
        ServerPlayer p = s.player;
        double ang = p.serverLevel().random.nextDouble() * Math.PI * 2.0;
        Vec3 pos = p.position().add(Math.cos(ang) * 7.0, 0.0, Math.sin(ang) * 7.0);
        soundAt(p, "block.wooden_door.open", pos, 0.8f, 0.9f);
        s.later(32, () -> soundAt(p, "block.wooden_door.close", pos, 0.8f, 0.9f));
    }

    /** "<você> entrou no jogo" e, 3s depois, "<você> saiu do jogo". */
    public static void fakeJoinLeave(Session s) {
        Component name = s.player.getDisplayName();
        s.chat(Component.translatable("multiplayer.player.joined", name).withStyle(ChatFormatting.YELLOW));
        s.later(60, () -> s.chat(Component.translatable("multiplayer.player.left", name)
                .withStyle(ChatFormatting.YELLOW)));
    }

    // ------------------------------------------------------------------ fase 2

    public static void fakeDeath(Session s) {
        s.chat(Component.translatable("death.attack.generic", s.player.getDisplayName()));
    }

    public static void darkness(Session s) {
        s.player.addEffect(new MobEffectInstance(MobEffects.DARKNESS, 220, 0, false, false, false));
    }

    // ------------------------------------------------------------------ fase 3

    /** Uma "página rasgada" aparece no inventário, com o nome do usuário e o tempo de jogo. */
    public static void giveNote(Session s) {
        ItemStack paper = new ItemStack(Items.PAPER);
        paper.set(DataComponents.CUSTOM_NAME, t("note.name").withStyle(ChatFormatting.RED));
        long minutes = HollowState.ticks / 1200L;
        paper.set(DataComponents.LORE, new ItemLore(List.<Component>of(
                t("note.l1", s.user).withStyle(ChatFormatting.GRAY),
                t("note.l2", String.valueOf(minutes)).withStyle(ChatFormatting.GRAY),
                t("note.l3").withStyle(ChatFormatting.DARK_RED))));
        s.player.getInventory().add(paper);
    }

    /** Uma placa virada para o jogador, a ~12 blocos, com o nome dele. */
    public static void placeSign(Session s) {
        ServerPlayer p = s.player;
        ServerLevel lvl = p.serverLevel();
        Vec3 fwd = flatForward(p);
        double[] angles = {0, 25, -25, 50, -50, 80, -80};

        for (double deg : angles) {
            double r = Math.toRadians(deg);
            double cx = fwd.x * Math.cos(r) - fwd.z * Math.sin(r);
            double cz = fwd.x * Math.sin(r) + fwd.z * Math.cos(r);
            int x = Mth.floor(p.getX() + cx * 12.0);
            int z = Mth.floor(p.getZ() + cz * 12.0);
            if (!lvl.hasChunkAt(new BlockPos(x, p.getBlockY(), z))) continue;

            for (int y = p.getBlockY() + 6; y >= p.getBlockY() - 8; y--) {
                BlockPos pos = new BlockPos(x, y, z);
                BlockPos below = pos.below();
                if (lvl.getBlockState(pos).isAir()
                        && lvl.getBlockState(pos.above()).isAir()
                        && lvl.getBlockState(below).isFaceSturdy(lvl, below, Direction.UP)) {

                    float yawFace = (float) (Mth.atan2(-(p.getX() - (x + 0.5)), p.getZ() - (z + 0.5))
                            * 180.0 / Math.PI);
                    BlockState state = Blocks.OAK_SIGN.defaultBlockState()
                            .setValue(StandingSignBlock.ROTATION, RotationSegment.convertToSegment(yawFace));
                    lvl.setBlock(pos, state, 3);

                    if (lvl.getBlockEntity(pos) instanceof SignBlockEntity sign) {
                        SignText text = new SignText()
                                .setMessage(0, t("sign.0"))
                                .setMessage(1, t("sign.1"))
                                .setMessage(2, Component.literal(shorten(s.user, 15)))
                                .setColor(DyeColor.RED)
                                .setHasGlowingText(true);
                        sign.setText(text, true);
                        sign.setWaxed(true);
                    }
                    return;
                }
            }
        }
    }

    /** A câmera vira 180° de repente, como se alguém tivesse encostado no ombro. */
    public static void lookBehind(Session s) {
        ServerPlayer p = s.player;
        if (s.anomaly != null || p.isPassenger() || p.isSleeping()) return;
        float yaw = Mth.wrapDegrees(p.getYRot() + 180.0f);
        p.connection.teleport(p.getX(), p.getY(), p.getZ(), yaw, 0.0f);
    }

    /** Mensagem no chat que parece ter sido escrita pelo próprio jogador. */
    public static void forgedChat(Session s) {
        s.chat(Component.translatable("chat.type.text", s.player.getDisplayName(), t("forged")));
    }

    // ------------------------------------------------------------------ fase 4

    public static void fakeKilled(Session s) {
        s.chat(Component.translatable("commands.kill.success.single", s.player.getDisplayName()));
    }
}
