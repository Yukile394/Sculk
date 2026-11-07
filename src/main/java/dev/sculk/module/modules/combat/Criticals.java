// language: java17, file: Criticals.java, runtime: fabric/1.20.x client mixin env, target: sculk client-combat module
package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
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
import net.minecraft.util.math.Vec3d;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.ThreadLocalRandom;

public final class Criticals extends Module {

    public static boolean cancelCrit = false;

    private final ModeSetting<Mode> mode = new ModeSetting<>("Mode", Mode.Grim)
            .describe("Anticheat bypass protokolü");

    private final ModeSetting<Toggle> sprintReset = new ModeSetting<>("SprintReset", Toggle.On);
    private final ModeSetting<Toggle> cooldownCheck = new ModeSetting<>("CooldownCheck", Toggle.On);
    private final ModeSetting<Toggle> humanJitter = new ModeSetting<>("HumanJitter", Toggle.On)
            .describe("Paket zamanlamasına insan-varyansı gürültüsü ekler");
    private final ModeSetting<Toggle> rateLimit = new ModeSetting<>("RateLimit", Toggle.On)
            .describe("Crit oranını istatistiksel olarak normale yaklaştırır");
    private final ModeSetting<Toggle> fullPhysics = new ModeSetting<>("FullPhysicsSim", Toggle.On)
            .describe("X/Z sürtünme + yüzey-tipi fiziği de simüle eder — sadece Y değil");
    private final NumberSetting resyncThreshold = new NumberSetting("ResyncEPS", 0.03, 0.005, 0.1, 0.005)
            .describe("Gerçek pozisyonla simülasyon farkı bunu aşarsa sessiz resync");

    // ── fizik sabitleri (vanilla client physics, 1.20.x) ────────────────────
    private static final double JUMP_VELOCITY   = 0.42;
    private static final double GRAVITY         = 0.08;
    private static final double AIR_DRAG        = 0.9800000190734863;
    private static final double GROUND_FRICTION_DEFAULT = 0.6;
    private static final double GROUND_FRICTION_ICE     = 0.98;
    private static final double GROUND_FRICTION_SLIME   = 0.8;
    private static final double GROUND_FRICTION_SOUL    = 0.4 * 1.5; // soul sand base * speed mod
    private static final double HORIZONTAL_DRAG = 0.91; // airborne horizontal
    private static final double TERMINAL_VEL     = -3.92;
    private static final double COOLDOWN_MIN     = 0.9;
    private static final double EPS              = 1.0E-7;

    // ── simülasyon durumu ─────────────────────────────────────────────────────
    private Vec3d simulatedVel = Vec3d.ZERO;
    private Vec3d simulatedPos = Vec3d.ZERO;
    private boolean simInitialized = false;
    private boolean wasOnGround   = true;
    private long    lastCritMs    = 0L;
    private long    lastCritTick  = Long.MIN_VALUE;
    private boolean wasSprinting  = false;

    private final Deque<Long> recentCrits = new ArrayDeque<>();
    private static final int RATE_WINDOW_MS = 4000;
    private static final int RATE_MAX_IN_WINDOW = 5;

    public Criticals() {
        super("Criticals", "Her saldırıyı critical'a çevirir", Category.COMBAT);
        this.addSettings(new Setting[]{this.mode, this.sprintReset, this.cooldownCheck,
                this.humanJitter, this.rateLimit, this.fullPhysics, this.resyncThreshold});
    }

    @Override
    public void onEnable() {
        this.simulatedVel  = Vec3d.ZERO;
        this.wasOnGround    = true;
        this.lastCritMs     = 0L;
        this.lastCritTick   = Long.MIN_VALUE;
        this.recentCrits.clear();
        this.simInitialized = false;
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

        if (!simInitialized) {
            simulatedPos = p.getPos();
            simInitialized = true;
        }

        // ground-truth divergence correction: simülasyon gerçek pozisyondan
        // EPS'i aşarsa sessizce resync — görünür teleport yok, flag yok
        double diff = Math.abs(simulatedPos.y - p.getY());
        if (diff > resyncThreshold.getValue()) {
            simulatedPos = p.getPos();
            simulatedVel = Vec3d.ZERO;
        }

        double friction = currentSurfaceFriction(p);

        if (p.isOnGround()) {
            simulatedVel = new Vec3d(
                    simulatedVel.x * friction,
                    0.0,
                    simulatedVel.z * friction);
            wasOnGround = true;
        } else {
            double nextY = Math.max((simulatedVel.y - GRAVITY) * AIR_DRAG, TERMINAL_VEL);
            simulatedVel = new Vec3d(
                    simulatedVel.x * HORIZONTAL_DRAG,
                    nextY,
                    simulatedVel.z * HORIZONTAL_DRAG);
            wasOnGround = false;
        }

        simulatedPos = simulatedPos.add(simulatedVel);
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

        if (this.rateLimit.getValue() == Toggle.On) {
            this.recentCrits.removeIf(t -> now - t > RATE_WINDOW_MS);
            if (this.recentCrits.size() >= RATE_MAX_IN_WINDOW) return;
        }

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

        long jitterMs = this.humanJitter.getValue() == Toggle.On
                ? ThreadLocalRandom.current().nextLong(0, 12)
                : 0L;

        switch (m) {
            case Grim       -> grimBypass(player, jitterMs);
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
        this.recentCrits.addLast(now);
    }

    // ── bypass dizileri ───────────────────────────────────────────────────────

    private void grimBypass(ClientPlayerEntity player, long jitterMs) {
        double base = player.getY();

        if (player.isOnGround()) {
            double delta = JUMP_VELOCITY - GRAVITY;
            if (jitterMs > 0) {
                try { Thread.sleep(jitterMs); } catch (InterruptedException ignored) {}
            }
            sendPos(player, base + delta, false);
            sendPos(player, base, true);
            simulatedVel = new Vec3d(simulatedVel.x, delta - GRAVITY, simulatedVel.z);
            simulatedPos = new Vec3d(simulatedPos.x, base + delta, simulatedPos.z);
            wasOnGround  = false;
        } else {
            double next = Math.max((simulatedVel.y - GRAVITY) * AIR_DRAG, TERMINAL_VEL);
            sendPos(player, base + next, false);
            simulatedVel = new Vec3d(simulatedVel.x, next, simulatedVel.z);
        }
    }

    private void semiGrimBypass(ClientPlayerEntity player) {
        double base = player.getY();
        sendPos(player, base + EPS, false);
        sendPos(player, base - EPS, false);
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

    // ── yüzey fiziği ─────────────────────────────────────────────────────────

    private double currentSurfaceFriction(ClientPlayerEntity player) {
        if (this.fullPhysics.getValue() != Toggle.On) return GROUND_FRICTION_DEFAULT;
        if (this.getMC().world == null) return GROUND_FRICTION_DEFAULT;

        BlockPos below = player.getBlockPos().down();
        var block = this.getMC().world.getBlockState(below).getBlock();

        if (block == Blocks.ICE || block == Blocks.PACKED_ICE || block == Blocks.BLUE_ICE)
            return GROUND_FRICTION_ICE;
        if (block == Blocks.SLIME_BLOCK)
            return GROUND_FRICTION_SLIME;
        if (block == Blocks.SOUL_SAND || block == Blocks.SOUL_SOIL)
            return GROUND_FRICTION_SOUL;

        return GROUND_FRICTION_DEFAULT;
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

    public enum Mode {
        Grim, SemiGrim, Polar, Matrix,
        Ncp, Strict, OldNCP, UpdatedNCP, Legit
    }

    public enum Toggle { On, Off }
        }
