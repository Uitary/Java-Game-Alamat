import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.sound.sampled.*;

public class Sigbin {

    public static final int WIDTH  = 130;
    public static final int HEIGHT = 130;

    public int x, y;
    private final Rectangle hitbox;

    private int health              = 1400;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 1400;

    private static final int WALK_SPEED  = 2;
    private static final int MELEE_RANGE = 150;
    private int direction = -1;

    //Melee
    public  int pendingMeleeDamage      = 0;
    private int meleeTimer = 0, meleeCooldown = 0;
    public  static final int MELEE_DAMAGE   = 12;
    private static final int MELEE_DURATION = 30;
    private static final int MELEE_INTERVAL = 50;

    //Dash
    public  static final int DASH_DAMAGE            = 22;
    private static final int DASH_CD                = 900;
    private static final int DASH_SPEED             = 20;
    private static final int DASH_DURATION          = 30;
    private static final int CHARGE_FRAMES          = 8;
    private static final int CHARGE_TICKS_PER_FRAME = 8;
    private static final int CHARGE_TOTAL_TICKS     = CHARGE_FRAMES * CHARGE_TICKS_PER_FRAME;
    private static final int SLASH_DURATION         = 20;

    private int dashCooldown = 300, chargeTimer = 0, dashTimer = 0, slashTimer = 0;
    public  boolean pendingDashDamage = false;

    //Long-range burst 
    // Fires BURST_COUNT straight-line projectiles toward the player,
    // one every BURST_INTERVAL ticks while in RANGED state.
    public final ArrayList<StraightProjectile> projectiles = new ArrayList<>();
    private int longAtkCooldown                  = 200;
    private static final int LONG_ATK_CD         = 450;   // ~7.5 s
    private static final int BURST_COUNT         = 3;
    private static final int BURST_INTERVAL      = 18;    // ticks between shots (~0.3 s)

    // Burst state (used while in RANGED)
    private int burstShot      = 0;   // how many projectiles fired so far
    private int burstTick      = 0;   // ticks elapsed in current burst
    private int capturedPX     = 0;   // player X at fire-trigger time
    private int capturedPY     = 0;   // player Y at fire-trigger time

    //State / animation 
    private enum State { WALK, MELEE, CHARGE, DASH, SLASH, RANGED }
    private State state     = State.WALK;

    private final BufferedImage[] walkFrames   = new BufferedImage[6];
    private final BufferedImage[] chargeFrames = new BufferedImage[CHARGE_FRAMES];
    private BufferedImage atkFrame, dashAtkFrame;
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 10;

    public Sigbin(int x, int y) {
        this.x = x; this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++)             walkFrames[i]   = img("assets/Enemy/Sigbin/Sigbin_KF"        + (i + 1) + ".png");
        for (int i = 0; i < CHARGE_FRAMES; i++) chargeFrames[i] = img("assets/Enemy/Sigbin/Sigbin_Charge_KF" + (i + 1) + ".png");
        atkFrame     = img("assets/Enemy/Sigbin/Sigbin_ShortAtk_KF1.png");
        dashAtkFrame = img("assets/Enemy/Sigbin/Sigbin_DashAtk_KF1.png");
        SoundManager.playCooldown("sounds/enemy/sigbin.wav", 800); // spawn sound
    }

    public void update(int px, int py, Rectangle playerHitbox) {
        pendingMeleeDamage = 0;
        pendingDashDamage  = false;

        if (meleeCooldown   > 0) meleeCooldown--;
        if (dashCooldown    > 0) dashCooldown--;
        if (longAtkCooldown > 0) longAtkCooldown--;

        int dx  = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near = Math.abs(dx) < MELEE_RANGE;

        State prevState = state;
        switch (state) {
            case WALK -> {
                if      (dashCooldown == 0 && !near)      { state = State.CHARGE; chargeTimer = 0; }
                else if (longAtkCooldown == 0 && !near)   startBurst(px, py);
                else if (near && meleeCooldown == 0)       { state = State.MELEE; meleeTimer = 0; }
                else                                        x += WALK_SPEED * direction;
            }
            case MELEE -> {
                if (++meleeTimer == 12 && getMeleeHitbox().intersects(playerHitbox))
                    pendingMeleeDamage = MELEE_DAMAGE;
                if (meleeTimer >= MELEE_DURATION) { state = State.WALK; meleeTimer = 0; meleeCooldown = MELEE_INTERVAL; }
            }
            case CHARGE -> {
                if (++chargeTimer >= CHARGE_TOTAL_TICKS) { state = State.DASH; dashTimer = 0; dashCooldown = DASH_CD; }
            }
            case DASH -> {
                x += DASH_SPEED * direction;
                if (prevState == State.WALK && state != State.WALK && isAlive())
                    SoundManager.playCooldown("sounds/enemy/sigbin.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
                if (hitbox.intersects(playerHitbox)) pendingDashDamage = true;
                if (++dashTimer >= DASH_DURATION)    { state = State.SLASH; slashTimer = 0; }
            }
            case SLASH  -> { if (++slashTimer >= SLASH_DURATION) { state = State.WALK; slashTimer = 0; } }
            case RANGED -> {
                // Fire one projectile every BURST_INTERVAL ticks until BURST_COUNT shots are done.
                burstTick++;
                if (burstTick % BURST_INTERVAL == 0 && burstShot < BURST_COUNT) {
                    fireProjectile(capturedPX, capturedPY);
                    burstShot++;
                }
                if (burstShot >= BURST_COUNT) {
                    // Burst complete — return to walk
                    state = State.WALK;
                    burstShot = 0;
                    burstTick = 0;
                }
            }
        }

        hitbox.setBounds(x, y, WIDTH, HEIGHT);
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }

        projectiles.removeIf(p -> !p.active);
        projectiles.forEach(StraightProjectile::update);
    }

    /**
     * Begins a 3-shot burst aimed at the player's current position.
     * The first shot fires immediately on the next BURST_INTERVAL tick.
     */
    private void startBurst(int px, int py) {
        capturedPX      = px + 50;  // aim at player centre
        capturedPY      = py + 50;
        burstShot       = 0;
        burstTick       = 0;
        longAtkCooldown = LONG_ATK_CD;
        state           = State.RANGED;
    }

    /** Fires a single straight projectile toward (targetX, targetY). */
    private void fireProjectile(int targetX, int targetY) {
        int originX = x + WIDTH  / 2;
        int originY = y + HEIGHT / 2;
        projectiles.add(new StraightProjectile(originX, originY, targetX, targetY));
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage frame = switch (state) {
            case CHARGE -> {
                int fi = Math.min(chargeTimer / CHARGE_TICKS_PER_FRAME, CHARGE_FRAMES - 1);
                yield chargeFrames[fi] != null ? chargeFrames[fi] : walkFrames[animFrame];
            }
            case DASH   -> dashAtkFrame != null ? dashAtkFrame : walkFrames[animFrame];
            case SLASH  -> dashAtkFrame != null ? dashAtkFrame : walkFrames[animFrame]; // reuse dash frame for linger
            case MELEE, RANGED -> atkFrame != null ? atkFrame : walkFrames[animFrame];
            default     -> walkFrames[animFrame];
        };

        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * HEIGHT);
            int drawX = x + WIDTH / 2 - drawW / 2;
            // atkFrame (ShortAtk_KF1) is saved facing the opposite direction to the
            // walk/dash frames, so flip the condition for MELEE and RANGED states only.
            boolean atkFrameActive = (state == State.MELEE || state == State.RANGED)
                                     && atkFrame != null;
            boolean shouldFlip = atkFrameActive ? direction > 0 : direction < 0;
            if (shouldFlip) g2.drawImage(frame, drawX + drawW, y, -drawW, HEIGHT, null);
            else            g2.drawImage(frame, drawX,         y,  drawW, HEIGHT, null);
        } else {
            g2.setColor(new Color(120, 30, 10)); g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("Sigbin", x + 2, y + 70);
        }

        projectiles.forEach(p -> p.draw(g2));
    }

    public Rectangle getMeleeHitbox() {
        return new Rectangle(direction > 0 ? x + WIDTH : x - 70, y, 70, HEIGHT);
    }

    public float     getHealthRatio() { return (float) health / MAX_HP; }
    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/creature.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    private static BufferedImage img(String p) {
        try { return javax.imageio.ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }

    // ── StraightProjectile ────────────────────────────────────────────────────
    /**
     * Travels in a straight line from the boss's centre toward the player's
     * position captured at fire time. Rotates the sprite to face its direction.
     *
     * Larger than the old ArcProjectile: 96×96 sprite, 48×48 hitbox.
     */
    public static class StraightProjectile {
        public int x, y;
        public boolean active = true;

        private static final int   SPEED   = 10;     // pixels per tick
        private static final int   DRAW_W  = 96;     // sprite render size (increased)
        private static final int   DRAW_H  = 96;
        private static final int   HIT_R   = 24;     // half-size of square hitbox (48×48)
        private static final int   MAX_LIFE = 300;
        public  static final int   DAMAGE  = 15;

        private final float velX, velY;
        private float fx, fy;           // sub-pixel position
        private int   life = 0;
        private final double angle;     // radians, for sprite rotation

        private static BufferedImage effectImg;
        private static boolean imgLoaded = false;

        public StraightProjectile(int sx, int sy, int targetX, int targetY) {
            fx = sx; fy = sy;
            x  = sx; y  = sy;

            // Direction vector toward target, normalised to SPEED
            float dxRaw = targetX - sx;
            float dyRaw = targetY - sy;
            float len   = (float) Math.sqrt(dxRaw * dxRaw + dyRaw * dyRaw);
            if (len < 1) len = 1;   // guard against zero-length

            velX  = (dxRaw / len) * SPEED;
            velY  = (dyRaw / len) * SPEED;
            angle = Math.atan2(dyRaw, dxRaw);

            if (!imgLoaded) {
                imgLoaded = true;
                try { effectImg = javax.imageio.ImageIO.read(new File("assets/Enemy/Sigbin/Sigbin_LongAtk_Effect.png")); }
                catch (java.io.IOException ignored) {}
            }
        }

        public void update() {
            if (++life > MAX_LIFE) { active = false; return; }
            fx += velX;
            fy += velY;
            x = Math.round(fx);
            y = Math.round(fy);
            // Deactivate if far off-screen
            if (x < -200 || x > 3000 || y < -200 || y > 3000) active = false;
        }

        public void draw(Graphics2D g2) {
            if (effectImg != null) {
                var saved = g2.getTransform();
                g2.translate(x, y);
                g2.rotate(angle);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(effectImg, -DRAW_W / 2, -DRAW_H / 2, DRAW_W, DRAW_H, null);
                g2.setTransform(saved);
            } else {
                // Fallback circle (larger than before)
                int r = 24;
                g2.setColor(new Color(220, 80, 30, 210)); g2.fillOval(x - r, y - r, r * 2, r * 2);
                g2.setColor(new Color(255, 200, 80, 180)); g2.fillOval(x - r + 6, y - r + 6, r * 2 - 12, r * 2 - 12);
            }
        }

        /** 48×48 hitbox centred on the projectile. */
        public Rectangle getHitbox() { return new Rectangle(x - HIT_R, y - HIT_R, HIT_R * 2, HIT_R * 2); }
    }
    }
