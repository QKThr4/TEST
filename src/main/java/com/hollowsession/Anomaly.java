package com.hollowsession;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Rotations;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.util.RandomSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.entity.decoration.ArmorStand;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.Items;
import net.minecraft.world.phys.Vec3;

import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;

/**
 * A "massa anômala": 6 ArmorStands invisíveis, sem gravidade, com Obsidiana na cabeça,
 * vibrando e girando a cabeça de forma aleatória, a exatamente 15 blocos atrás do jogador.
 *
 * Some NA HORA (com Enderman teleport + batimentos do Warden) se o jogador olhar direto
 * para ela ou chegar a menos de 8 blocos.
 *
 * Os ArmorStands são criados diretamente (new ArmorStand(...)) e guardados numa lista,
 * sem nenhum ID de entidade fixo, e inseridos com addFreshEntity.
 */
public final class Anomaly {
    public static final String TAG = "hollowsession_anomaly";

    private static final double DISTANCE = 15.0;
    private static final double VANISH_RADIUS = 8.0;
    private static final double LOOK_DOT = 0.93;            // ~21 graus
    private static final int LIFETIME_TICKS = 20 * 75;      // some sozinha em 75s

    /** Entidades vivas controladas por este mod (identidade, não equals). */
    private static final Set<Entity> LIVE = Collections.newSetFromMap(new IdentityHashMap<>());

    /** Offsets de cada stand em relação ao centro (x, y, z). Forma torta e alta. */
    private static final double[][] SHAPE = {
            {0.0, 0.4, 0.0},
            {0.3, 1.5, 0.1},
            {-0.6, -0.5, 0.3},
            {0.9, -1.2, -0.4},
            {-0.2, 2.4, -0.2},
            {0.2, -1.6, 0.8}
    };

    private final ServerLevel level;
    private final Vec3 center;
    private final List<ArmorStand> stands = new ArrayList<>();
    private final RandomSource rnd;
    private int age = 0;
    private boolean gone = false;

    private Anomaly(ServerLevel level, Vec3 center) {
        this.level = level;
        this.center = center;
        this.rnd = level.random;

        for (double[] o : SHAPE) {
            ArmorStand st = new ArmorStand(level, center.x + o[0], center.y + o[1], center.z + o[2]);
            st.setInvisible(true);
            st.setNoGravity(true);
            st.setSilent(true);
            st.setInvulnerable(true);
            st.setItemSlot(EquipmentSlot.HEAD, new ItemStack(Items.OBSIDIAN));
            st.addTag(TAG);
            stands.add(st);
        }
        // Registra ANTES de inserir, para o filtro de limpeza não cancelar os nossos.
        LIVE.addAll(stands);
        for (ArmorStand st : stands) {
            level.addFreshEntity(st);
        }
    }

    public static boolean isLive(Entity e) {
        return LIVE.contains(e);
    }

    /** Cria a anomalia 15 blocos atrás do jogador, ou retorna null se o local for inválido. */
    public static Anomaly tryCreate(Session s) {
        ServerPlayer p = s.player;
        ServerLevel level = p.serverLevel();

        Vec3 back = Scenes.flatForward(p).scale(-DISTANCE);
        double x = p.getX() + back.x;
        double z = p.getZ() + back.z;

        BlockPos col = BlockPos.containing(x, p.getY(), z);
        if (!level.hasChunkAt(col)) return null;

        int floor = findFloor(level, col, p.getBlockY());
        if (floor == Integer.MIN_VALUE) return null;

        return new Anomaly(level, new Vec3(x, floor, z));
    }

    private static int findFloor(ServerLevel level, BlockPos col, int playerY) {
        int top = playerY + 6;
        int bottom = Math.max(level.getMinBuildHeight(), playerY - 10);
        for (int y = top; y >= bottom; y--) {
            BlockPos pos = new BlockPos(col.getX(), y, col.getZ());
            BlockPos below = pos.below();
            if (level.getBlockState(pos).isAir()
                    && level.getBlockState(pos.above()).isAir()
                    && level.getBlockState(below).isFaceSturdy(level, below, Direction.UP)) {
                return y;
            }
        }
        return Integer.MIN_VALUE;
    }

    /** @return false quando a anomalia terminou (e deve ser descartada pela sessão). */
    boolean tick(Session s) {
        if (gone) return false;
        ServerPlayer p = s.player;
        age++;

        boolean invalid = p.serverLevel() != level || !p.isAlive();
        for (ArmorStand st : stands) {
            if (st.isRemoved()) invalid = true;
        }
        if (invalid || age > LIFETIME_TICKS) {
            discard();
            return false;
        }

        if (p.position().distanceTo(center.add(0.0, 1.5, 0.0)) < VANISH_RADIUS || lookedAt(p)) {
            vanish(s);
            return false;
        }

        glitch();
        return true;
    }

    private boolean lookedAt(ServerPlayer p) {
        Vec3 mass = center.add(0.0, 1.8, 0.0);
        Vec3 to = mass.subtract(p.getEyePosition());
        double len = to.length();
        if (len < 1.0E-3) return true;
        if (p.getViewVector(1.0F).dot(to.scale(1.0 / len)) < LOOK_DOT) return false;
        for (ArmorStand st : stands) {
            if (p.hasLineOfSight(st)) return true;
        }
        return false;
    }

    /** Vibração rápida + cabeças girando de forma aleatória a cada tick. */
    private void glitch() {
        for (int i = 0; i < stands.size(); i++) {
            ArmorStand st = stands.get(i);
            double[] o = SHAPE[i];
            double j = 0.10;
            double dx = (rnd.nextDouble() - 0.5) * 2.0 * j;
            double dy = (rnd.nextDouble() - 0.5) * 2.0 * j;
            double dz = (rnd.nextDouble() - 0.5) * 2.0 * j;
            if (rnd.nextInt(18) == 0) { // "salto" de glitch ocasional
                dx *= 8.0;
                dy *= 5.0;
                dz *= 8.0;
            }
            st.setPos(center.x + o[0] + dx, center.y + o[1] + dy, center.z + o[2] + dz);
            st.setHeadPose(new Rotations(
                    rnd.nextFloat() * 360.0f - 180.0f,
                    rnd.nextFloat() * 360.0f - 180.0f,
                    rnd.nextFloat() * 360.0f - 180.0f));
        }
    }

    private void vanish(Session s) {
        discard();
        ServerPlayer p = s.player;
        Scenes.sound(p, "entity.enderman.teleport", 1.0f, 0.85f);
        Scenes.sound(p, "entity.warden.heartbeat", 1.0f, 0.9f);
        s.later(14, () -> Scenes.sound(p, "entity.warden.heartbeat", 0.9f, 0.9f));
        s.later(28, () -> Scenes.sound(p, "entity.warden.heartbeat", 0.8f, 0.9f));
        HollowState.blinkTicks = 3; // piscada escura bem curta no cliente
    }

    /** Remove tudo silenciosamente. */
    public void discard() {
        gone = true;
        for (ArmorStand st : stands) {
            LIVE.remove(st);
            st.discard();
        }
        stands.clear();
    }
}
