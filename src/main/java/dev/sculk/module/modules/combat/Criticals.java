package dev.sculk.module.modules.combat;

import dev.sculk.module.Category;
import dev.sculk.module.Module;
import dev.sculk.module.setting.BoolSetting;
import dev.sculk.module.setting.ModeSetting;
import dev.sculk.module.setting.NumberSetting;
import dev.sculk.module.setting.Setting;
import dev.sculk.util.ServerClock;
import net.minecraft.block.Blocks;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.ClientPlayerEntity;
import net.minecraft.entity.Entity;
import net.minecraft.entity.decoration.EndCrystalEntity;
import net.minecraft.network.packet.c2s.play.PlayerMoveC2SPacket;
import net.minecraft.registry.tag.BlockTags;
import net.minecraft.util.math.BlockPos;

/**
 * Criticals — Grim 2.3+, Polar 2.x, Matrix 7.x, NCP bypass.
 *
 * Her saldırıyı critical'a çevirir. Paket dizisi anticheatin
 * fizik simülasyonunu taklit eder — ban almamak için detay aşağıda.
 */
public final class Criticals extends Module {

    public static boolean cancelCrit = false;

    // ── ayarlar ───────────────────────────────────────────────────────────────
    private final ModeSetting<Mode> mode = new ModeSetting<>("Mode", Mode.Grim)
            .describe("Hangi anticheat bypass protokolü");

    private final BoolSetting sprintReset = new BoolSetting("SprintReset", true)
            .describe("Saldırı öncesi sprint keserek vanilla crit koşulunu sağlar (Grim zorunlu)");

    private final NumberSetting cooldownThreshold = new NumberSetting(
            "CooldownThreshold", 0.9, 0.5, 1.0, 0.05)
            .describe("Saldırı yapılabilmesi için gereken minimum attack cooldown (1.0 = tam hazır)");

    // ── iç durum ──────────────────────────────────────────────────────────────
    /** Sunucuya gönderilen Y pozisyonunu izleriz — Grim tahminini bozmamalıyız */
    private double trackedY        = 0.0;
    /** Fizik simülasyonu: her tick güncellenir */
    private double simulatedVelY   = 0.0;
    private boolean wasOnGround    = true;
    /** Fallback zaman damgası */
    private long   lastCritMs      = 0L;
    private long   lastCritTick    = Long.MIN_VALUE;
    /** Sprint durumu geri yükleme */
    private boolean wasSprinting   = false;

    // ── vanilla fizik sabitleri ───────────────────────────────────────────────
    /** Entity.jump() içinden: jumpVelocity = 0.42 (jump boost yok) */
    private static final double JUMP_VELOCITY   = 0.42;
    /** ClientPlayerEntity gravity per tick */
    private static final double GRAVITY         = 0.08;
    /** Air drag per tick */
    private static final double DRAG            = 0.98;
    /** Terminal velocity (yaklaşık) */
    private static final double TERMINAL_VEL    = -3.92;

    public Criticals() {
        super("Criticals", "Her saldırıyı critical'a çevirir", Category.COMBAT);
        this.addSettings(new Setting[]{this.mode, this.sprintReset, this.cooldownThreshold});
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
        // Sprint kesilmişse geri yükle
        if (this.getMC().player != null && wasSprinting) {
            this.getMC().player.setSprinting(true);
            wasSprinting = false;
        }
    }

    // ── her tick fizik simülasyonu ────────────────────────────────────────────
    @Override
    public void onTick() {
        ClientPlayerEntity p = this.getMC().player;
        if (p == null) return;

        if (p.isOnGround()) {
            this.simulatedVelY = 0.0;
            this.wasOnGround   = true;
            this.trackedY      = p.getY();
        } else {
            // Vanilla gravity formülü: velY = (velY - 0.08) * 0.98
            this.simulatedVelY = Math.max((this.simulatedVelY - GRAVITY) * DRAG, TERMINAL_VEL);
            this.trackedY     += this.simulatedVelY;
            this.wasOnGround   = false;
        }
    }

    // ── saldırı hook'u (KillAura / event sisteminden çağrılır) ───────────────
    public void onAttack(Entity target) {
        if (cancelCrit || target == null || target instanceof EndCrystalEntity) return;

        ClientPlayerEntity player = this.getMC().player;
        if (player == null || this.getMC().world == null) return;

        // Crit mümkün değilse çık
        if (!isCritPossible(player)) return;

        // Attack cooldown kontrolü — Grim spam saldırıyı zaten reddeder
        // ama cooldown tam değilse crit paketi göndermenin anlamı yok
        double cooldown = player.getAttackCooldownProgress(0.0f);
        if (cooldown < this.cooldownThreshold.getValue()) return;

        // Duplicate crit koruması — aynı tick / 150ms içinde tekrar gönderme
        long now = System.currentTimeMillis();
        if (now - this.lastCritMs < 150L) return;
        long tick = ServerClock.tick();
        if (tick != Long.MIN_VALUE) {
            if (tick == this.lastCritTick) return;
            this.lastCritTick = tick;
        }

        Mode m = this.mode.getValue();

        // Grim ve Polar: sprint kesilmeden crit olmaz (vanilla koşulu)
        if (sprintReset.isEnabled() && player.isSprinting()
                && (m == Mode.Grim || m == Mode.Polar || m == Mode.SemiGrim)) {
            wasSprinting = true;
            player.setSprinting(false);
        }

        // Mod seçimine göre paket dizisi gönder
        switch (m) {
            case Grim     -> grimBypass(player);
            case SemiGrim -> semiGrimBypass(player);
            case Polar    -> polarBypass(player);
            case Matrix   -> matrixBypass(player);
            case Ncp      -> ncpBypass(player);
            case Strict   -> strictBypass(player);
            case OldNCP   -> oldNcpBypass(player);
            case UpdatedNCP -> updatedNcpBypass(player);
            case Legit    -> { /* hiçbir şey yapma — natural crit bekle */ return; }
        }

        // Sprint geri yükle
        if (wasSprinting) {
            player.setSprinting(true);
            wasSprinting = false;
        }

        this.lastCritMs = now;
    }

    // ── GRIM 2.3+ bypass ─────────────────────────────────────────────────────
    /**
     * Grim sunucu tarafında client fiziğini birebir simüle eder.
     * Gönderilen Y paketlerinin vanilla jump physics'e uyması şart.
     *
     * Paket sırası:
     *   tick 0 (jump başlangıcı):  y + 0.42        velY = 0.42
     *   tick 1:                    y + 0.3332       velY = (0.42-0.08)*0.98
     *   tick 2 (düşüş başlar):     y + 0.2493...   velY = (0.3332-0.08)*0.98  — kritik nokta
     *   land:                      y + 0.0, onGround=true
     *
     * NOT: 0.32 kullanmak yanlış — vanilla fizik dışı, Grim flag atar.
     */
    private void grimBypass(ClientPlayerEntity player) {
        double base = player.getY();
        boolean onGround = player.isOnGround();

        if (onGround) {
            double v1 = JUMP_VELOCITY;
            double v2 = (v1 - GRAVITY) * DRAG;
            double v3 = (v2 - GRAVITY) * DRAG;

            sendPos(player, base + v1, false);   // zıplıyor
            sendPos(player, base + v1 + v2, false); // yükseliyor
            sendPos(player, base + v1 + v2 + v3, false); // en yüksek nokta geçildi
            sendPos(player, base, true);          // indi

            // Yerel simülasyonu senkronize et
            this.simulatedVelY = v3;
            this.wasOnGround   = false;
        } else {
            // Zaten havadaysa mevcut velY'den devam et
            double nextVel = Math.max((this.simulatedVelY - GRAVITY) * DRAG, TERMINAL_VEL);
            sendPos(player, player.getY() + nextVel, false);
            this.simulatedVelY = nextVel;
        }
    }

    // ── SEMI-GRIM: micro-delta bypass ─────────────────────────────────────────
    /**
     * Grim'in floating-point toleransı içinde çalışır.
     * Çok küçük Y deltaları — Grim'in diff threshold'unun altında kalır.
     * Grim yamalarına göre 1e-7 ile 1e-5 arasında ayarlanabilir.
     *
     * Polar ve Matrix için de çalışır, daha temiz bir alternatif.
     */
    private void semiGrimBypass(ClientPlayerEntity player) {
        double base = player.getY();

        // Pozitif delta: "yukarı hareket" simüle et
        // Negatif delta: düşüş — crit koşulu (velY < 0) tetiklenir
        double up   =  2.7e-7;
        double down = -2.7e-7;

        sendPos(player, base + up,   false);
        sendPos(player, base + down, false); // bu noktada crit koşulu oluştu
        // onGround=true paket gönderme — yer tespiti bypass
    }

    // ── POLAR 2.x bypass ──────────────────────────────────────────────────────
    /**
     * Polar, Grim kadar katı değil. 2 paket yeterli.
     * jumpStart = 0.42 (vanilla), ardından 0.0.
     */
    private void polarBypass(ClientPlayerEntity player) {
        double base = player.getY();
        boolean onGround = player.isOnGround();

        if (!onGround) return; // Polar havada pakete gerek bırakmaz

        double v1 = JUMP_VELOCITY;
        sendPos(player, base + v1, false);
        sendPos(player, base,      true);
    }

    // ── MATRIX 7.x bypass ────────────────────────────────────────────────────
    /**
     * Matrix rotasyon + hareket karşılaştırması yapar.
     * Üç paketlik dizi, Matrix'in delta-time doğrulamasını geçer.
     */
    private void matrixBypass(ClientPlayerEntity player) {
        double base = player.getY();
        if (!player.isOnGround()) return;

        sendPos(player, base + 0.0625, false);
        sendPos(player, base + 0.0,    false);
        sendPos(player, base,          true);
    }

    // ── LEGACY NCP paketleri ─────────────────────────────────────────────────
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

    /**
     * Crit mümkün mü? Vanilla koşullarını kontrol et.
     * Grim bu koşulların sunucu tarafında da geçerli olmasını bekler.
     */
    private boolean isCritPossible(ClientPlayerEntity player) {
        if (player.getAbilities().flying)      return false;
        if (player.isSpectator())              return false;
        if (player.isFallFlying())             return false;
        if (player.isInLava())                 return false;
        if (player.isSubmergedInWater())       return false;
        if (player.isTouchingWater())          return false;
        if (isOnClimbable(player))             return false;
        if (player.hasStatusEffect(net.minecraft.entity.effect.StatusEffects.BLINDNESS)) return false;
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

    /**
     * PositionAndOnGround paketi gönder.
     * NaN / Infinity koruma — Grim bunları da kontrol eder, NaN flag atar.
     */
    private void sendPos(ClientPlayerEntity player, double y, boolean onGround) {
        ClientPlayNetworkHandler net = this.getMC().getNetworkHandler();
        if (net == null) return;
        if (Double.isNaN(y) || Double.isInfinite(y)) return;
        net.sendPacket(new PlayerMoveC2SPacket.PositionAndOnGround(
                player.getX(), y, player.getZ(), onGround));
    }

    // ── enum ─────────────────────────────────────────────────────────────────
    public enum Mode {
        Grim,       // Grim 2.3+ — tam fizik simülasyonu
        SemiGrim,   // micro-delta — Grim tolerans altı, Polar/Matrix için de çalışır
        Polar,      // Polar 2.x — 2 paket
        Matrix,     // Matrix 7.x — 3 paket
        Ncp,        // NoCheatPlus standart
        Strict,     // NCP strict sunucular
        OldNCP,     // Eski NCP versiyonları
        UpdatedNCP, // Güncel NCP
        Legit       // Hiçbir şey yapma
    }
           }
