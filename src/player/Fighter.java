import java.awt.*;
import java.awt.image.BufferedImage;

public class Fighter extends Player {

    public static final int  PUNCH_DAMAGE        = 55;
    public static final int  PUNCH_CD_TICKS      = 0;
    public static final int  POWER_PUNCH_DAMAGE  = 40;  // exposed for Shop
    private static final int POWER_PUNCH_SPEED   = 20;
    private static final int POWER_PUNCH_LIFE    = 55;  // travels full screen
    public  static final int POWER_PUNCH_CD      = 90;  // 1.5 s at 60 fps

    public  int     punchCooldown      = 0;
    public  boolean punchActive        = false;
    private int     punchTimer         = 0;
    private static final int PUNCH_DURATION = 14;

    public  int powerPunchCooldown = 0;
    public  final java.util.ArrayList<PowerPunch> powerPunches = new java.util.ArrayList<>();

    // Upgrade levels (set by GamePanel from Shop purchases)
    public int punchLevel      = 0;
    public int powerPunchLevel = 0;

    public Fighter() { runFrameCount = 8; }

    //Damage helpers (affected by upgrade level)
    public int getPunchDamage()      { return PUNCH_DAMAGE       + punchLevel      * 8; }
    public int getPowerPunchDamage() { return POWER_PUNCH_DAMAGE + powerPunchLevel * 7; }

    //Skill activation
    public boolean useSkill1() {
        if (punchCooldown > 0) return false;
        punchActive = true; punchTimer = 0; punchCooldown = PUNCH_CD_TICKS;
        return true;
    }

    public void useSkill2() {
        if (powerPunchCooldown > 0) return;
        int bx = facingLeft ? x - 10 : x + PS;
        int by = (duck && onGround) ? y + PS - DUCK_HEIGHT / 2 : y + PS / 2 - 8;
        powerPunches.add(new PowerPunch(bx, by, facingLeft ? -POWER_PUNCH_SPEED : POWER_PUNCH_SPEED));
        powerPunchCooldown = POWER_PUNCH_CD;
    }

    public int consumePowerPunchHit(Rectangle target) {
        java.util.Iterator<PowerPunch> it = powerPunches.iterator();
        while (it.hasNext()) {
            PowerPunch p = it.next();
            if (p.active && p.getHitbox().intersects(target)) {
                p.active = false; it.remove();
                return getPowerPunchDamage();
            }
        }
        return 0;
    }

    //Per-tick update
    public void updateSkills() {
        if (punchCooldown      > 0) punchCooldown--;
        if (powerPunchCooldown > 0) powerPunchCooldown--;
        if (punchActive && ++punchTimer > PUNCH_DURATION) { punchActive = false; punchTimer = 0; }
        powerPunches.forEach(PowerPunch::update);
        powerPunches.removeIf(p -> !p.active);
    }

    public void applySkillHits(java.util.ArrayList<Bangis> bangis, Aswang boss) {
        // Heavy Punch
        if (punchActive && punchTimer == 3) {
            Rectangle ph = getPunchHitbox();
            for (Bangis en : bangis)
                if (en.isAlive() && en.getHitbox().intersects(ph)) en.takeDamage(getPunchDamage());
            if (boss != null && boss.isAlive() && boss.getHitbox().intersects(ph))
                boss.takeDamage(getPunchDamage());
        }
        // Power Punch projectiles vs Bangis / Aswang boss
        java.util.Iterator<PowerPunch> it = powerPunches.iterator();
        while (it.hasNext()) {
            PowerPunch p = it.next(); boolean hit = false;
            for (Bangis en : bangis) {
                if (en.isAlive() && en.getHitbox().intersects(p.getHitbox())) {
                    en.takeDamage(getPowerPunchDamage()); hit = true; break;
                }
            }
            if (!hit && boss != null && boss.isAlive() && boss.getHitbox().intersects(p.getHitbox())) {
                boss.takeDamage(getPowerPunchDamage()); hit = true;
            }
            if (hit) { p.active = false; it.remove(); }
        }
    }

    public Rectangle getPunchHitbox() {
        int px = facingLeft ? x - 80 : x + PS;
        boolean ducking = duck && onGround;
        int py = ducking ? y + PS - DUCK_HEIGHT : y;
        return new Rectangle(px, py, 80, ducking ? DUCK_HEIGHT : PS);
    }

    @Override
    public void updateAnimation(Equipments.Sword sword, Equipments.Bow bow, Equipments.Potion pot) {
        if (deathAnimPlaying) { animState = AnimState.DEAD; return; }
        if (hitFlashTimer > 0) { animState = AnimState.HIT; return; }

        if      (punchActive)              animState = AnimState.ATK1;
        else if (!powerPunches.isEmpty()) animState = AnimState.ATK1;
        else if (pot.active)              animState = AnimState.HEAL_ATK;
        else if (duck && onGround)    animState = AnimState.DUCK;
        else if (isDashing)           animState = AnimState.DASH;
        else if (!onGround)           animState = AnimState.JUMP;
        else if (moveLeft||moveRight) animState = AnimState.RUN;
        else                          animState = AnimState.IDLE;

        tickRun();
    }

    @Override
    public void draw(Graphics2D g2, AssetManager assets,
                     Equipments.Sword sword, int selectedItem) {
        runFrameCount = assets.runFrameCount;
        BufferedImage frame = pickFrame(assets);

        if (isDashing && frame != null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            drawSprite(g2, frame, facingLeft ? x+20 : x-20);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
        if (frame == null) { g2.setColor(new Color(220,120,50)); g2.fillRect(x, y, PS, PS); }
        else drawSprite(g2, frame, x);

        if (hitFlashTimer > 0 && assets.hitFrame == null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g2.setColor(Color.RED); g2.fillRect(x, y, PS, PS);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Draw power punch projectiles
        powerPunches.forEach(p -> p.draw(g2));

        // Punch flash
        if (punchActive && punchTimer <= 5) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g2.setColor(new Color(255,200,80));
            Rectangle ph = getPunchHitbox();
            g2.fillOval(ph.x, ph.y + ph.height/2 - 20, 40, 40);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Power Punch cooldown badge
        if (powerPunchCooldown > 0) {
            float frac = 1f - powerPunchCooldown / (float) POWER_PUNCH_CD;
            int barW = 60, barX = x + PS/2 - barW/2, barY = y - 14;
            g2.setColor(new Color(50,20,10,180)); g2.fillRoundRect(barX, barY, barW, 8, 4, 4);
            g2.setColor(new Color(255,100,30));   g2.fillRoundRect(barX, barY, (int)(barW*frac), 8, 4, 4);
            g2.setFont(new Font("SansSerif", Font.BOLD, 9)); g2.setColor(new Color(255,180,120));
            String cd = String.format("%.1fs", powerPunchCooldown/60f);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(cd, barX + barW/2 - fm.stringWidth(cd)/2, barY - 2);
        }
    }

    @Override
    protected BufferedImage pickFrame(AssetManager assets) {
        return switch (animState) {
            case RUN      -> assets.runFrames[runFrame];
            case JUMP     -> assets.jumpFrame;
            case DASH     -> first(assets.dashFrame,    assets.runFrames[0]);
            case DUCK     -> first(assets.duckFrame,    assets.idleFrame);
            case ATK1     -> first(assets.atkFrame,     assets.idleFrame);
            case HEAL_ATK -> first(assets.healAtkFrame, assets.idleFrame);
            case HIT      -> first(assets.hitFrame,     assets.idleFrame);
            case DEAD     -> first(assets.deadFrames[deathFrame], assets.idleFrame);
            default       -> assets.idleFrame;
        };
    }

    // ── Inner: PowerPunch projectile ────────────────────────────────────────────
    public static class PowerPunch {
        public int x, y;
        public boolean active = true;
        private final int dx;
        private int life = 0;

        public PowerPunch(int x, int y, int dx) { this.x = x; this.y = y; this.dx = dx; }

        public void update() { x += dx; if (++life > POWER_PUNCH_LIFE) active = false; }

        public void draw(Graphics2D g2) {
            // Outer glow ring
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
            g2.setColor(new Color(255, 120, 30)); g2.fillOval(x - 18, y - 18, 36, 36);
            // Mid ring
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.65f));
            g2.setColor(new Color(255, 180, 60)); g2.fillOval(x - 12, y - 12, 24, 24);
            // Core
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1.0f));
            g2.setColor(new Color(255, 240, 160)); g2.fillOval(x - 6, y - 6, 12, 12);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        public Rectangle getHitbox() { return new Rectangle(x - 14, y - 14, 28, 28); }
    }
}
