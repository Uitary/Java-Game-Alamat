import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Sitan {

    // ── Dimensions ────────────────────────────────────────────────────────────
    public  static final int WIDTH  = 130;
    public  static final int HEIGHT = 150;
    private static final int DRAW_H = 175;

    // ── Position / physics ────────────────────────────────────────────────────
    public int x, y;
    public final int groundY;
    private final Rectangle hitbox;

    // ── Stats ─────────────────────────────────────────────────────────────────
    private int health              = 1800;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 1800;

    private static final int WALK_SPEED  = 3;
    private static final int MELEE_RANGE = 180;
    private int direction = -1;

    // ── Pending damage fields ─────────────────────────────────────────────────
    public int pendingSwingDamage = 0;
    public int pendingSlamDamage  = 0;
    public int pendingBeamDamage  = 0;
    public boolean pendingSummon  = false;

    // ── Damage constants ──────────────────────────────────────────────────────
    public static final int SWING_DAMAGE      = 18;
    public static final int SLAM_DAMAGE       = 25;
    public static final int BEAM_TICK_DAMAGE  = 3;

    // ── Cooldowns (ticks @ 60 fps) ────────────────────────────────────────────
    private int meleeCooldown = 0;
    private int fireGroundCD  = 0;
    private int violetOrbCD   = 0;
    private int summonCD      = 0;
    private int teleportCD    = 200;
    private int beamCD        = 360;

    private static final int MELEE_CD_TICKS    =  60;
    private static final int FIRE_GROUND_TICKS = 600;
    private static final int VIOLET_ORB_TICKS  = 300;
    private static final int SUMMON_TICKS      = 1200;
    private static final int TELEPORT_TICKS    = 180;
    private static final int BEAM_CD_TICKS     = 1200;

    // ── State machine ─────────────────────────────────────────────────────────
    public enum State {
        WALK,
        SWING, SLAM,
        FIRE_GROUND, VIOLET_ORB, SUMMON, TELEPORT,
        CHANNEL, BEAM,
        RECOVER
    }
    public State state = State.WALK;

    private int  actionTimer  = 0;
    private int  recoverTimer = 0;
    private boolean swingNext = true;

    private static final int SWING_DURATION   = 24;
    private static final int SLAM_DURATION    = 32;
    private static final int CAST_DURATION    = 42;
    private static final int TELEPORT_ANIM    = 30;
    private static final int CHANNEL_DURATION = 80;
    private static final int BEAM_DURATION    = 160;
    private static final int RECOVER_DURATION = 40;

    // ── Beam geometry ─────────────────────────────────────────────────────────
    private static final int BEAM_LENGTH = 700;
    private static final int BEAM_HEIGHT = 70;

    // ── Projectile / hazard lists ─────────────────────────────────────────────
    public final ArrayList<VioletOrb> violetOrbs  = new ArrayList<>();
    public final ArrayList<FirePatch> firePatches = new ArrayList<>();

    // ── Attack animation ──────────────────────────────────────────────────────
    private static final int ATK_FRAMES = 4;  // keyframes per attack animation
    private static final int ATK_DELAY  = 6;  // ticks between attack frames
    private int atkFrame = 0, atkCounter = 0;

    // ── Sprites ───────────────────────────────────────────────────────────────
    private final BufferedImage[] walkFrames     = new BufferedImage[6];
    private final BufferedImage[] swingFrames    = new BufferedImage[ATK_FRAMES]; // horizontal sweep
    private final BufferedImage[] slamFrames     = new BufferedImage[ATK_FRAMES]; // downward arc
    private final BufferedImage[] castFrames     = new BufferedImage[ATK_FRAMES]; // spell cast (shared)
    private final BufferedImage[] channelFrames  = new BufferedImage[ATK_FRAMES]; // beam wind-up
    private final BufferedImage[] beamFrames     = new BufferedImage[ATK_FRAMES]; // sustained beam
    private final BufferedImage[] teleportFrames = new BufferedImage[ATK_FRAMES]; // blink
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 10;

    // ─────────────────────────────────────────────────────────────────────────
    public Sitan(int x, int groundY) {
        this.x       = x;
        this.groundY = groundY;
        this.y       = groundY - HEIGHT;
        hitbox       = new Rectangle(x, y, WIDTH, HEIGHT);
        loadSprites();
        SoundManager.playCooldown("sounds/enemy/sitan.wav", 800); // spawn sound
    }

    private void loadSprites() {
        for (int i = 0; i < 6; i++)
            walkFrames[i] = img("assets/Enemy/Walk/frame_00" + (i + 1) + ".png");
        for (int i = 0; i < ATK_FRAMES; i++) {
            swingFrames[i]    = img("assets/Enemy/Sitan/Stomp/frame_00"    + (i + 1) + ".png");
            slamFrames[i]     = img("assets/Enemy/Sitan/Stomp/frame_00"     + (i + 1) + ".png");
            castFrames[i]     = img("assets/Enemy/Sitan/Cast/frame_00"     + (i + 1) + ".png");
            channelFrames[i]  = img("assets/Enemy/Sitan/Channel/frame_00"  + (i + 1) + ".png");
            beamFrames[i]     = img("assets/Enemy/Sitan/Sitan_Beam_KF"     + (i + 1) + ".png");
            teleportFrames[i] = img("assets/Enemy/Sitan/Sitan_Teleport_KF" + (i + 1) + ".png");
        }
    }

    /** Advances the attack animation frame counter. Call once per tick inside any attack state. */
    private void tickAtk() {
        if (++atkCounter >= ATK_DELAY) { atkFrame = (atkFrame + 1) % ATK_FRAMES; atkCounter = 0; }
    }

    private void tickAnim() {
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }
    }

    // ─────────────────────────────────────────────────────────────────────────
    public void update(int px, int py, Rectangle playerHitbox) {
        pendingSwingDamage = 0;
        pendingSlamDamage  = 0;
        pendingBeamDamage  = 0;
        pendingSummon      = false;

        if (meleeCooldown > 0) meleeCooldown--;
        if (fireGroundCD  > 0) fireGroundCD--;
        if (violetOrbCD   > 0) violetOrbCD--;
        if (summonCD      > 0) summonCD--;
        if (teleportCD    > 0) teleportCD--;
        if (beamCD        > 0) beamCD--;

        int dx = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean inMeleeRange = Math.abs(dx) < MELEE_RANGE;

        State prevState = state;
        switch (state) {

            case WALK -> {
                x += WALK_SPEED * direction;
                tickAnim();

                if (inMeleeRange && meleeCooldown == 0) {
                    state = swingNext ? State.SWING : State.SLAM;
                    swingNext = !swingNext;
                    actionTimer = 0; meleeCooldown = MELEE_CD_TICKS;
                    atkFrame = 0; atkCounter = 0;
                } else if (beamCD == 0) {
                    state = State.CHANNEL; actionTimer = 0;
                    beamCD = BEAM_CD_TICKS;
                    atkFrame = 0; atkCounter = 0;
                } else if (summonCD == 0) {
                    state = State.SUMMON; actionTimer = 0;
                    summonCD = SUMMON_TICKS;
                    atkFrame = 0; atkCounter = 0;
                } else if (teleportCD == 0) {
                    state = State.TELEPORT; actionTimer = 0;
                    teleportCD = TELEPORT_TICKS;
                    atkFrame = 0; atkCounter = 0;
                } else if (fireGroundCD == 0) {
                    state = State.FIRE_GROUND; actionTimer = 0;
                    fireGroundCD = FIRE_GROUND_TICKS;
                    atkFrame = 0; atkCounter = 0;
                } else if (violetOrbCD == 0) {
                    state = State.VIOLET_ORB; actionTimer = 0;
                    violetOrbCD = VIOLET_ORB_TICKS;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case SWING -> {
                tickAtk();
                if (actionTimer == 10 && getSwingHitbox().intersects(playerHitbox))
                    pendingSwingDamage = SWING_DAMAGE;
                if (++actionTimer >= SWING_DURATION) enterRecover();
            }

            case SLAM -> {
                tickAtk();
                if (actionTimer == 16 && getSlamHitbox().intersects(playerHitbox))
                    pendingSlamDamage = SLAM_DAMAGE;
                if (++actionTimer >= SLAM_DURATION) enterRecover();
            }

            case FIRE_GROUND -> {
                tickAtk();
                if (actionTimer == 20)
                    firePatches.add(new FirePatch(px, groundY));
                if (++actionTimer >= CAST_DURATION) enterRecover();
            }

            case VIOLET_ORB -> {
                tickAtk();
                if (actionTimer == 15)
                    violetOrbs.add(new VioletOrb(x + WIDTH / 2, y + HEIGHT / 3));
                if (++actionTimer >= CAST_DURATION) enterRecover();
            }

            case SUMMON -> {
                tickAtk();
                if (actionTimer == 25) pendingSummon = true;
                if (++actionTimer >= CAST_DURATION + 8) enterRecover();
            }

            case TELEPORT -> {
                tickAtk();
                if (actionTimer == 15) {
                    x = Math.max(0, px - WIDTH - 20);
                    y = groundY - HEIGHT;
                }
                if (++actionTimer >= TELEPORT_ANIM) enterRecover();
            }

            case CHANNEL -> {
                tickAtk();
                if (++actionTimer >= CHANNEL_DURATION) {
                    state = State.BEAM; actionTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case BEAM -> {
                tickAtk();
                if (getBeamHitbox().intersects(playerHitbox))
                    pendingBeamDamage = BEAM_TICK_DAMAGE;
                if (++actionTimer >= BEAM_DURATION) enterRecover();
            }

            case RECOVER -> {
                tickAtk();
                if (++recoverTimer >= RECOVER_DURATION) state = State.WALK;
            }
        }

        violetOrbs.removeIf(v -> !v.active);
        for (VioletOrb v : violetOrbs)  v.update(px, py);
        for (FirePatch fp : firePatches) fp.update();
        firePatches.removeIf(fp -> !fp.active);

        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/sitan.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
    }

    // ─────────────────────────────────────────────────────────────────────────
    private void enterRecover() {
        state = State.RECOVER; recoverTimer = 0;
        atkFrame = 0; atkCounter = 0;
    }

    // ── Attack hitboxes ───────────────────────────────────────────────────────
    public Rectangle getSwingHitbox() {
        int reach = 110;
        return new Rectangle(direction > 0 ? x + WIDTH : x - reach, y + 10, reach, HEIGHT - 20);
    }

    public Rectangle getSlamHitbox() {
        int reach = 150;
        return new Rectangle(direction > 0 ? x + WIDTH / 2 : x - reach + WIDTH / 2,
                             y + HEIGHT / 2, reach + WIDTH / 2, HEIGHT / 2);
    }

    public Rectangle getBeamHitbox() {
        int originX = direction > 0 ? x + WIDTH : x - BEAM_LENGTH;
        int originY = y + HEIGHT / 2 - BEAM_HEIGHT / 2;
        return new Rectangle(originX, originY, BEAM_LENGTH, BEAM_HEIGHT);
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,  RenderingHints.VALUE_ANTIALIAS_ON);

        for (FirePatch fp : firePatches) fp.draw(g2);

        // Beam (drawn behind body)
        if (state == State.BEAM) {
            Rectangle beam = getBeamHitbox();
            float pulse = 0.55f + 0.45f * (float) Math.sin(actionTimer * 0.2);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
            g2.setColor(new Color(110, 0, 200, 200));
            g2.fillRect(beam.x, beam.y, beam.width, beam.height);
            g2.setColor(new Color(220, 150, 255, 160));
            g2.fillRect(beam.x, beam.y + BEAM_HEIGHT / 4, beam.width, BEAM_HEIGHT / 2);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Channel glow
        if (state == State.CHANNEL) {
            float pulse = 0.4f + 0.6f * (float) Math.sin(actionTimer * 0.18);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse * 0.55f));
            g2.setColor(new Color(130, 0, 255));
            g2.fillOval(x - 18, y - 12, WIDTH + 36, HEIGHT + 24);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Teleport flash
        if (state == State.TELEPORT && actionTimer < 15) {
            float alpha = 1f - actionTimer / 15f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            g2.setColor(new Color(80, 0, 160, 200));
            g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Summon rune circle
        if (state == State.SUMMON && actionTimer < CAST_DURATION) {
            float prog = actionTimer / (float) CAST_DURATION;
            int r = (int)(80 * prog);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.5f * (1f - prog)));
            g2.setColor(new Color(180, 0, 200));
            g2.setStroke(new BasicStroke(3f));
            g2.drawOval(x + WIDTH / 2 - r, groundY - r * 2, r * 2, r * 2);
            g2.setStroke(new BasicStroke(1f));
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // ── Select cycling attack frame ────────────────────────────────────────
        BufferedImage frame = switch (state) {
            case SWING                                 -> pick(swingFrames);
            case SLAM                                  -> pick(slamFrames);
            case FIRE_GROUND, VIOLET_ORB, SUMMON      -> pick(castFrames);
            case CHANNEL                               -> pick(channelFrames);
            case BEAM                                  -> pick(beamFrames);
            case TELEPORT                              -> pick(teleportFrames);
            default                                    -> walkFrames[animFrame];
        };

        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * DRAW_H);
            int drawX = x + WIDTH / 2 - drawW / 2;
            if (direction < 0) g2.drawImage(frame, drawX + drawW, y, -drawW, DRAW_H, null);
            else               g2.drawImage(frame, drawX,         y,  drawW, DRAW_H, null);
        } else {
            g2.setColor(new Color(70, 0, 110));
            g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(new Color(200, 100, 255));
            g2.setFont(new Font("SansSerif", Font.BOLD, 16));
            g2.drawString("SITAN", x + WIDTH / 2 - 22, y + HEIGHT / 2 + 6);
        }

        // Channel wind-up warning bar
        if (state == State.CHANNEL) {
            float prog = actionTimer / (float) CHANNEL_DURATION;
            int barX = x + WIDTH / 2 - 45;
            g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, y - 22, 90, 7);
            g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 22, (int)(90 * prog), 7);
            g2.setColor(new Color(230, 180, 255, 230));
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.drawString("!!", x + WIDTH / 2 - 8, y - 26);
        }

        for (VioletOrb v : violetOrbs) v.draw(g2);

        // HP bar
        int barX = x + WIDTH / 2 - 55;
        g2.setColor(new Color(80, 0, 0));    g2.fillRect(barX, y - 13, 110, 6);
        g2.setColor(new Color(200, 30, 30));  g2.fillRect(barX, y - 13, (int)(110 * (health / (float) MAX_HP)), 6);
        g2.setColor(new Color(210, 160, 255, 160));
        g2.setStroke(new BasicStroke(1f));    g2.drawRect(barX, y - 13, 110, 6);
    }

    /** Returns the current attack frame from an array, falling back to walk frame. */
    private BufferedImage pick(BufferedImage[] arr) {
        return (arr[atkFrame] != null) ? arr[atkFrame] : walkFrames[animFrame];
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/guy.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public float     getHealthRatio()  { return (float) health / MAX_HP; }
    public Rectangle getHitbox()       { return hitbox; }


    // ═════════════════════════════════════════════════════════════════════════
    //  Inner class – VioletOrb  (homing projectile)
    // ═════════════════════════════════════════════════════════════════════════
    public static class VioletOrb {
        public  int     x, y;
        public  boolean active = true;

        private float   fx, fy;
        private static final float SPEED    = 4.5f;
        private static final int   HIT_R    = 18;
        private static final int   MAX_LIFE = 360;
        public  static final int   DAMAGE   = 16;

        private static final int DRAW_R = 24;
        private float spin = 0f;

        private static BufferedImage orbImg;
        private static boolean       imgLoaded = false;

        public VioletOrb(int sx, int sy) {
            fx = sx; fy = sy;
            x  = sx; y  = sy;
            if (!imgLoaded) {
                imgLoaded = true;
                try { orbImg = ImageIO.read(new File("assets/Sitan_VioletOrb_Effect.png")); }
                catch (IOException ignored) {}
            }
        }

        private int life = 0;

        public void update(int px, int py) {
            if (++life > MAX_LIFE) { active = false; return; }
            float tdx  = (px + 50) - fx;
            float tdy  = (py + 50) - fy;
            float dist = (float) Math.sqrt(tdx * tdx + tdy * tdy);
            if (dist > 2f) { fx += (tdx / dist) * SPEED; fy += (tdy / dist) * SPEED; }
            x    = Math.round(fx);
            y    = Math.round(fy);
            spin += 0.09f;
        }

        public void draw(Graphics2D g2) {
            var saved = g2.getTransform();
            g2.translate(x, y);
            g2.rotate(spin);
            if (orbImg != null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(orbImg, -DRAW_R, -DRAW_R, DRAW_R * 2, DRAW_R * 2, null);
            } else {
                g2.setColor(new Color(100, 0, 200, 200));
                g2.fillOval(-DRAW_R, -DRAW_R, DRAW_R * 2, DRAW_R * 2);
                g2.setColor(new Color(200, 100, 255, 220));
                g2.fillOval(-DRAW_R / 2, -DRAW_R / 2, DRAW_R, DRAW_R);
                g2.setColor(new Color(255, 220, 255, 255));
                g2.fillOval(-5, -5, 10, 10);
            }
            g2.setTransform(saved);
        }

        public Rectangle getHitbox() {
            return new Rectangle(x - HIT_R, y - HIT_R, HIT_R * 2, HIT_R * 2);
        }
    }


    // ═════════════════════════════════════════════════════════════════════════
    //  Inner class – FirePatch  (persistent ground hazard)
    // ═════════════════════════════════════════════════════════════════════════
    public static class FirePatch {
        public int     x, y;
        public boolean active = true;

        private static final int PATCH_W  = 120;
        private static final int PATCH_H  = 30;
        private static final int LIFETIME = 480;

        public  static final int TICK_DAMAGE = 2;
        private static final int TICK_CD     = 20;

        private int   life        = 0;
        private int   tickTimer   = 0;
        public  int   pendingDamage = 0;
        private float flicker     = 0f;

        private static BufferedImage fireImg;
        private static boolean       imgLoaded = false;

        public FirePatch(int px, int groundY) {
            this.x = px - PATCH_W / 2;
            this.y = groundY - PATCH_H;
            if (!imgLoaded) {
                imgLoaded = true;
                try { fireImg = ImageIO.read(new File("assets/Sitan_FirePatch_Effect.png")); }
                catch (IOException ignored) {}
            }
        }

        public void update() {
            pendingDamage = 0;
            if (++life >= LIFETIME) { active = false; return; }
            flicker += 0.15f;
            if (++tickTimer >= TICK_CD) { pendingDamage = TICK_DAMAGE; tickTimer = 0; }
        }

        public void draw(Graphics2D g2) {
            float lifeRatio = (float) life / LIFETIME;
            float fadeAlpha = Math.min(1f, (1f - lifeRatio) * 3f);
            float flick     = 0.75f + 0.25f * (float) Math.sin(flicker);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, fadeAlpha * flick));
            if (fireImg != null) {
                g2.drawImage(fireImg, x, y - 20, PATCH_W, PATCH_H + 20, null);
            } else {
                g2.setColor(new Color(255, 60, 0, 210));
                g2.fillRect(x, y, PATCH_W, PATCH_H);
                g2.setColor(new Color(255, 200, 0, 170));
                g2.fillRect(x + 10, y, PATCH_W - 20, PATCH_H / 2);
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        public Rectangle getHitbox() {
            return new Rectangle(x, y, PATCH_W, PATCH_H);
        }
    }


    // ── Utility ───────────────────────────────────────────────────────────────
    private static BufferedImage img(String p) {
        try { return ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }
    }
