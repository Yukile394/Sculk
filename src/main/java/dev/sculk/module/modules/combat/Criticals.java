package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.ServerClock;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.entity.effect.StatusEffects;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

public final class Criticals extends Module {

    public static boolean cancelCrit = false;

    private final ModeSetting<Mode> mode = new ModeSetting<>("Mode", Mode.Grim)
            .describe("Hangi anticheat bypass protokolü");

    // BoolSetting yok — ModeSetting<Toggle> ile aynı işi görür
    private final ModeSetting<Toggle> sprintReset = new ModeSetting<>("SprintReset", Toggle.On)
            .describe("Saldırı öncesi sprint keser, Grim için zorunlu");

    private final ModeSetting<Toggle> cooldownCheck = new ModeSetting<>("CooldownCheck", Toggle.On)
            .describe("Attack cooldown %90 dolmadan crit paketi göndermez");

    // ── fizik sabitleri ───────────────────────────────────────────────────────
    private static final double JUMP_VELOCITY = 0.42;
    private static final double GRAVITY       = 0.08;
    private static final double DRAG          = 0.98;
    private static final double TERMINAL_VEL  = -3.92;
    private static final double COOLDOWN_MIN  = 0.9;

    // ── iç durum ──────────────────────────────────────────────────────────────
    private double  simulatedVelY = 0.0;
    private boolean wasOnGround   = true;
    private long    lastCritMs    = 0L;
    private long    lastCritTick  = Long.MIN_VALUE;
    private boolean wasSprinting  = false;

    public Criticals() {
        super("Criticals", "Her saldırıyı critical'a çevirir", Category.COMBAT);
        this.addSettings(new Setting[]{this.mode, this.sprintReset, this.cooldownCheck});
    }

    @Override
    public void onEnable() {
        this.simulatedVelY = 0.0;
        this.wasOnGround   = true;
        this.lastCritMs    = 0L;
        this.lastCritTick  = Long.MIN_VALUE;
    }

    @Override
    public void onDisable() {
        if (this.getMC().player != null && wasSprinting) {
            this.getMC().player.setSprinting(true);
            wasSprinting = false;
        }
    }

    @Override
    public void onTick() {
        ClientPlayerEntity p = this.getMC().player;
        if (p == null) return;

        if (p.isOnGround()) {
            this.simulatedVelY = 0.0;
            this.wasOnGround   = true;
        } else {
            this.simulatedVelY = Math.max((this.simulatedVelY - GRAVITY) * DRAG, TERMINAL_VEL);
            this.wasOnGround   = false;
        }
    }

    public void onAttack(Entity target) {
        if (cancelCrit || target == null || target instanceof EndCrystalEntity) return;

        ClientPlayerEntity player = this.getMC().player;
        if (player == null || this.getMC().world == null) return;
        if (!isCritPossible(player)) return;

        if (this.cooldownCheck.getValue() == Toggle.On) {
            if (player.getAttackCooldownProgress(0.0f) < COOLDOWN_MIN) return;
        }

        long now = System.currentTimeMillis();
        if (now - this.lastCritMs < 150L) return;

        long tick = ServerClock.tick();
        if (tick != Long.MIN_VALUE) {
            if (tick == this.lastCritTick) return;
            this.lastCritTick = tick;
        }

        Mode m = this.mode.getValue();

        if (this.sprintReset.getValue() == Toggle.On && player.isSprinting()
                && (m == Mode.Grim || m == Mode.Polar || m == Mode.SemiGrim)) {
            wasSprinting = true;
            player.setSprinting(false);
        }

        switch (m) {
            case Grim       -> grimBypass(player);
            case SemiGrim   -> semiGrimBypass(player);
            case Polar      -> polarBypass(player);
            case Matrix     -> matrixBypass(player);
            case Ncp        -> ncpBypass(player);
            case Strict     -> strictBypass(player);
            case OldNCP     -> oldNcpBypass(player);
            case UpdatedNCP -> updatedNcpBypass(player);
            case Legit      -> { return; }
        }

        if (wasSprinting) {
            player.setSprinting(true);
            wasSprinting = false;
        }

        this.lastCritMs = now;
    }

    // ── bypass dizileri ───────────────────────────────────────────────────────

    private void grimBypass(ClientPlayerEntity player) {
        double base = player.getY();

        if (player.isOnGround()) {
            double v1 = JUMP_VELOCITY;
            double v2 = (v1 - GRAVITY) * DRAG;
            double v3 = (v2 - GRAVITY) * DRAG;
            sendPos(player, base + v1,            false);
            sendPos(player, base + v1 + v2,       false);
            sendPos(player, base + v1 + v2 + v3,  false);
            sendPos(player, base,                  true);
            this.simulatedVelY = v3;
            this.wasOnGround   = false;
        } else {
            double next = Math.max((this.simulatedVelY - GRAVITY) * DRAG, TERMINAL_VEL);
            sendPos(player, base + next, false);
            this.simulatedVelY = next;
        }
    }

    private void semiGrimBypass(ClientPlayerEntity player) {
        double base = player.getY();
        sendPos(player, base + 2.7e-7,  false);
        sendPos(player, base - 2.7e-7,  false);
    }

    private void polarBypass(ClientPlayerEntity player) {
        if (!player.isOnGround()) return;
        double base = player.getY();
        sendPos(player, base + JUMP_VELOCITY, false);
        sendPos(player, base,                 true);
    }

    private void matrixBypass(ClientPlayerEntity player) {
        if (!player.isOnGround()) return;
        double base = player.getY();
        sendPos(player, base + 0.0625, false);
        sendPos(player, base,          false);
        sendPos(player, base,          true);
    }

    private void ncpBypass(ClientPlayerEntity player) {
        if (!player.isOnGround()) return;
        sendPos(player, player.getY() + 0.0625, false);
        sendPos(player, player.getY(),           false);
    }

    private void strictBypass(ClientPlayerEntity player) {
        if (!player.isOnGround()) return;
        sendPos(player, player.getY() + 0.062600301692775, false);
        sendPos(player, player.getY() + 0.07260029960661,  false);
        sendPos(player, player.getY(),                      true);
    }

    private void oldNcpBypass(ClientPlayerEntity player) {
        if (!player.isOnGround()) return;
        sendPos(player, player.getY() + 1.058293536e-5, false);
        sendPos(player, player.getY() + 9.16580235e-6,  false);
        sendPos(player, player.getY() + 1.0371854e-7,   false);
    }

    private void updatedNcpBypass(ClientPlayerEntity player) {
        if (!player.isOnGround()) return;
        sendPos(player, player.getY() + 2.71875e-7, false);
        sendPos(player, player.getY(),               false);
    }

    // ── yardımcılar ───────────────────────────────────────────────────────────

    private boolean isCritPossible(ClientPlayerEntity player) {
        if (player.getAbilities().flying)        return false;
        if (player.isSpectator())                return false;
        if (player.isFallFlying())               return false;
        if (player.isInLava())                   return false;
        if (player.isSubmergedInWater())         return false;
        if (player.isTouchingWater())            return false;
        if (isOnClimbable(player))               return false;
        if (player.hasStatusEffect(StatusEffects.BLINDNESS)) return false;
        return true;
    }

    private boolean isOnClimbable(ClientPlayerEntity player) {
        if (this.getMC().world == null) return false;
        BlockPos feet = player.getBlockPos();
        BlockPos body = feet.up();
        var world = this.getMC().world;
        return world.getBlockState(feet).isIn(BlockTags.CLIMBABLE)
            || world.getBlockState(body).isIn(BlockTags.CLIMBABLE)
            || world.getBlockState(feet).isOf(Blocks.SCAFFOLDING)
            || world.getBlockState(body).isOf(Blocks.SCAFFOLDING);
    }

    private void sendPos(ClientPlayerEntity player, double y, boolean onGround) {
        ClientPlayNetworkHandler net = this.getMC().getNetworkHandler();
        if (net == null) return;
        if (Double.isNaN(y) || Double.isInfinite(y)) return;
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), y, player.getZ(), onGround));
    }

    // ── enum'lar ──────────────────────────────────────────────────────────────

    public enum Mode {
        Grim, SemiGrim, Polar, Matrix,
        Ncp, Strict, OldNCP, UpdatedNCP, Legit
    }

    public enum Toggle { On, Off }
}
