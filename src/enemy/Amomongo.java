import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Amomongo {

    public static final int WIDTH  = 120;
    public static final int HEIGHT = 140;
    private static final int DRAW_H = 160;

    public int x, y;
    private final Rectangle hitbox;
    
    private int health              = 1200;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 1200;

    private static final int WALK_SPEED = 2;
    private int direction = -1;

    // ── Pending damage outputs ────────────────────────────────────────────────
    public int pendingSlashDamage   = 0;
    public int pendingHugDamage     = 0;
    public int pendingLandingDamage = 0;
    public int pendingDebrisDamage  = 0;
    public int pendingBoulderDamage = 0;

    public static final int SLASH_DAMAGE   = 35;
    public static final int HUG_DAMAGE     = 12;
    public static final int LANDING_DAMAGE = 50;
    public static final int DEBRIS_DAMAGE  = 15;
    public static final int BOULDER_DAMAGE = 40;

    // ── Boulder projectile ────────────────────────────────────────────────────
    public static class Boulder {
        public float x, y;
        private final float vx;
        private float vy;
        public boolean active = true;
        public static final int W = 32, H = 32;
        public final Rectangle hitbox;

        public Boulder(float sx, float sy, float tx, float ty) {
            x = sx; y = sy;
            float dist = Math.max(1f, (float) Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy)));
            float spd  = 9f;
            vx  = (tx - sx) / dist * spd;
            vy  = (ty - sy) / dist * spd - 4f;
            hitbox = new Rectangle((int) x, (int) y, W, H);
        }

        public void update() {
            x  += vx; y  += vy; vy += 0.22f;
            hitbox.setBounds((int) x, (int) y, W, H);
            if (y > 900) active = false;
        }

        public void draw(Graphics2D g) {
            g.setColor(new Color(100, 80, 60));
            g.fillOval((int) x, (int) y, W, H);
            g.setColor(new Color(60, 45, 30));
            g.drawOval((int) x, (int) y, W, H);
            g.setColor(new Color(160, 130, 100, 140));
            g.fillOval((int) x + 4, (int) y + 4, W / 3, H / 3);
        }
    }

    // ── Debris rock ──────────────────────────────────────────────────────────
    public static class DebrisRock {
        public float x, y;
        private float vy = 1.5f;
        public boolean active = true;
        public static final int W = 18, H = 18;
        public final Rectangle hitbox;
        private boolean landed = false;
        private int landTimer  = 0;

        public DebrisRock(float sx, float sy) {
            x = sx; y = sy;
            hitbox = new Rectangle((int) x, (int) y, W, H);
        }

        public void update() {
            if (!landed) {
                vy += 0.55f; y += vy;
                hitbox.setBounds((int) x, (int) y, W, H);
                if (y > 700) landed = true;
            } else {
                if (++landTimer > 35) active = false;
            }
        }

        public void draw(Graphics2D g) {
            if (!landed) {
                g.setColor(new Color(120, 95, 65));  g.fillRect((int) x, (int) y, W, H);
                g.setColor(new Color(80, 60, 40));   g.drawRect((int) x, (int) y, W, H);
            } else {
                int alpha = Math.max(0, 160 - landTimer * 5);
                g.setColor(new Color(180, 160, 120, alpha));
                g.fillOval((int) x - 4, (int) y, W + 8, H / 2);
            }
        }
    }

    public final ArrayList<Boulder>    boulders    = new ArrayList<>();
    public final ArrayList<DebrisRock> debrisRocks = new ArrayList<>();

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State {
        WALK,
        BOULDER_WIND, BOULDER_THROW,
        SLASH_WIND, SLASH_ACTIVE, SLASH_RECOVER,
        HUG_WIND, HUG_CRUSH,
        JUMP_RISE, JUMP_FALL, LAND,
        SCREAM,
        RECOVER
    }
    private State state = State.WALK;

    private int stateTimer  = 0;
    private int actionDelay = 80;

    private int boulderCooldown = 0;
    private int slashCooldown   = 0;
    private int hugCooldown     = 0;
    private int jumpCooldown    = 180;
    private int screamCooldown  = 0;

    private static final int BOULDER_WIND_DUR    = 45;
    private static final int BOULDER_THROW_DUR   = 15;
    private static final int BOULDER_COOLDOWN_MAX = 220;

    private static final int SLASH_WIND_DUR    = 25;
    private static final int SLASH_ACTIVE_DUR  = 10;
    private static final int SLASH_RECOVER_DUR = 30;
    private static final int SLASH_COOLDOWN_MAX = 160;

    private static final int HUG_WIND_DUR     = 55;
    private static final int HUG_CRUSH_DUR    = 90;
    private static final int HUG_COOLDOWN_MAX = 320;

    private static final int JUMP_RISE_DUR    = 180;
    private static final int JUMP_FALL_DUR    = 45;
    private static final int LAND_DUR         = 30;
    private static final int JUMP_COOLDOWN_MAX = 400;

    private static final int SCREAM_DUR        = 90;
    private static final int SCREAM_COOLDOWN_MAX = 300;

    private static final int RECOVER_DUR = 50;

    private boolean slashHitDealt   = false;
    private boolean landingHitDealt = false;
    private float   jumpY;
    private float   jumpVY;
    private int     landTargetX, landTargetY;
    private int     debrisSpawned = 0;
    private int     throwTargetX, throwTargetY;

    // ── Attack animation ──────────────────────────────────────────────────────
    private static final int ATK_FRAMES = 4;  // keyframes per attack animation
    private static final int ATK_DELAY  = 7;  // ticks between attack frames
    private int atkFrame = 0, atkCounter = 0;

    // ── Sprites ───────────────────────────────────────────────────────────────
    private final BufferedImage[] walkFrames     = new BufferedImage[6];
    private final BufferedImage[] slashFrames    = new BufferedImage[ATK_FRAMES]; // claw swipe
    private final BufferedImage[] jumpFrames     = new BufferedImage[ATK_FRAMES]; // airborne
    private final BufferedImage[] landFrames     = new BufferedImage[ATK_FRAMES]; // landing impact
    private final BufferedImage[] boulderFrames  = new BufferedImage[ATK_FRAMES]; // boulder throw
    // Aliases – states that share sprite sheets with other attacks
    private BufferedImage[] hugWindFrames;   // reuses slashFrames
    private BufferedImage[] hugCrushFrames;  // reuses slashFrames
    private BufferedImage[] screamFrames;    // reuses boulderFrames
    private BufferedImage[] recoverFrames;   // reuses slashFrames
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 10;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Amomongo(int x, int y) {
        this.x = x; this.y = y; this.jumpY = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++)
            walkFrames[i] = img("assets/Enemy/Amomongo/Amomong_KF" + (i + 1) + ".png");
        for (int i = 0; i < ATK_FRAMES; i++) {
            slashFrames[i]    = img("assets/Enemy/Amomongo/Amomongo_Atk_KF1"    + (i + 1) + ".png");
            jumpFrames[i]     = img("assets/Enemy/Amomongo/Amomongo_KF"     + (i + 1) + ".png");
            landFrames[i]     = img("assets/Enemy/Amomongo/Amomongo_Stomp_KF"     + (i + 1) + ".png");
            boulderFrames[i]  = img("assets/Enemy/Amomongo/Amomongo_Boulder_Effect"  + (i + 1) + ".png");
        }
        // Alias arrays – point to the closest matching sprite sheet
        hugWindFrames  = slashFrames;
        hugCrushFrames = slashFrames;
        screamFrames   = boulderFrames;
        recoverFrames  = slashFrames;
    }

    /** Advances the attack animation frame counter. Call once per tick inside any attack state. */
    private void tickAtk() {
        if (++atkCounter >= ATK_DELAY) { atkFrame = (atkFrame + 1) % ATK_FRAMES; atkCounter = 0; }
    }

    // ── Update ────────────────────────────────────────────────────────────────
    public void update(int px, int py, Rectangle playerHitbox) {
        pendingSlashDamage   = 0;
        pendingHugDamage     = 0;
        pendingLandingDamage = 0;
        pendingDebrisDamage  = 0;
        pendingBoulderDamage = 0;

        if (boulderCooldown > 0) boulderCooldown--;
        if (slashCooldown   > 0) slashCooldown--;
        if (hugCooldown     > 0) hugCooldown--;
        if (jumpCooldown    > 0) jumpCooldown--;
        if (screamCooldown  > 0) screamCooldown--;
        if (actionDelay     > 0) actionDelay--;

        int dx    = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near   = Math.abs(dx) < 130;
        boolean medium = Math.abs(dx) < 450;

        State prevState = state;
        switch (state) {

            case WALK -> {
                if (actionDelay == 0) {
                    if (near && hugCooldown == 0) {
                        state = State.HUG_WIND; stateTimer = 0;
                        atkFrame = 0; atkCounter = 0;
                    } else if (near && slashCooldown == 0) {
                        state = State.SLASH_WIND; stateTimer = 0; slashHitDealt = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (medium && boulderCooldown == 0) {
                        state = State.BOULDER_WIND; stateTimer = 0;
                        throwTargetX = px; throwTargetY = py;
                        atkFrame = 0; atkCounter = 0;
                    } else if (jumpCooldown == 0) {
                        state = State.JUMP_RISE; stateTimer = 0; landingHitDealt = false;
                        landTargetX = px; landTargetY = py;
                        jumpY = y; jumpVY = -13f;
                        atkFrame = 0; atkCounter = 0;
                    } else if (screamCooldown == 0) {
                        state = State.SCREAM; stateTimer = 0; debrisSpawned = 0;
                        atkFrame = 0; atkCounter = 0;
                    } else {
                        x += WALK_SPEED * direction;
                    }
                } else {
                    x += WALK_SPEED * direction;
                }
                if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }
            }

            case BOULDER_WIND -> {
                tickAtk();
                if (++stateTimer >= BOULDER_WIND_DUR) {
                    boulders.add(new Boulder(x + WIDTH / 2f, y + 30,
                            throwTargetX + 25f, throwTargetY + 25f));
                    state = State.BOULDER_THROW; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case BOULDER_THROW -> {
                tickAtk();
                if (++stateTimer >= BOULDER_THROW_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    boulderCooldown = BOULDER_COOLDOWN_MAX; actionDelay = 70;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case SLASH_WIND -> {
                tickAtk();
                if (++stateTimer >= SLASH_WIND_DUR) {
                    state = State.SLASH_ACTIVE; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case SLASH_ACTIVE -> {
                tickAtk();
                if (stateTimer == 4 && !slashHitDealt) {
                    Rectangle slashBox = new Rectangle(
                            direction > 0 ? x + WIDTH : x - 100, y, 100, HEIGHT);
                    if (slashBox.intersects(playerHitbox)) {
                        pendingSlashDamage = SLASH_DAMAGE; slashHitDealt = true;
                    }
                }
                if (++stateTimer >= SLASH_ACTIVE_DUR) {
                    state = State.SLASH_RECOVER; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case SLASH_RECOVER -> {
                tickAtk();
                if (++stateTimer >= SLASH_RECOVER_DUR) {
                    state = State.WALK; slashCooldown = SLASH_COOLDOWN_MAX; actionDelay = 60;
                }
            }

            case HUG_WIND -> {
                tickAtk();
                x += 4 * direction;
                if (prevState == State.WALK && state != State.WALK) playSound("sounds/enemy/amomongo.wav");
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
                if (hitbox.intersects(playerHitbox)) {
                    state = State.HUG_CRUSH; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                } else if (++stateTimer >= HUG_WIND_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    hugCooldown = HUG_COOLDOWN_MAX; actionDelay = 60;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case HUG_CRUSH -> {
                tickAtk();
                if (stateTimer % 18 == 0 && hitbox.intersects(playerHitbox))
                    pendingHugDamage = HUG_DAMAGE;
                if (++stateTimer >= HUG_CRUSH_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    hugCooldown = HUG_COOLDOWN_MAX; actionDelay = 80;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case JUMP_RISE -> {
                tickAtk();
                jumpY += jumpVY; jumpVY += 0.35f; y = (int) jumpY;
                if (++stateTimer >= JUMP_RISE_DUR) {
                    state = State.JUMP_FALL; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case JUMP_FALL -> {
                tickAtk();
                y += 22; stateTimer++;
                if (!landingHitDealt) {
                    Rectangle crashBox = new Rectangle(x, y, WIDTH, HEIGHT);
                    if (crashBox.intersects(playerHitbox)) {
                        pendingLandingDamage = LANDING_DAMAGE; landingHitDealt = true;
                    }
                }
                if (y >= landTargetY || stateTimer >= JUMP_FALL_DUR) {
                    y = landTargetY; state = State.LAND; stateTimer = 0;
                    jumpCooldown = JUMP_COOLDOWN_MAX;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case LAND -> {
                tickAtk();
                if (!landingHitDealt) {
                    Rectangle shockwave = new Rectangle(x - 70, y + HEIGHT - 20, WIDTH + 140, 30);
                    if (shockwave.intersects(playerHitbox)) {
                        pendingLandingDamage = LANDING_DAMAGE; landingHitDealt = true;
                    }
                }
                if (++stateTimer >= LAND_DUR) {
                    state = State.RECOVER; stateTimer = 0; actionDelay = 80;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case SCREAM -> {
                tickAtk();
                if (stateTimer % 10 == 0 && debrisSpawned < 8) {
                    float rx = x + WIDTH / 2f + (float) (Math.random() * 360 - 180);
                    debrisRocks.add(new DebrisRock(rx, -30));
                    debrisSpawned++;
                }
                if (++stateTimer >= SCREAM_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    screamCooldown = SCREAM_COOLDOWN_MAX; actionDelay = 60;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case RECOVER -> {
                tickAtk();
                if (++stateTimer >= RECOVER_DUR) state = State.WALK;
            }
        }

        hitbox.setBounds(x, y, WIDTH, HEIGHT);

        boulders.removeIf(b -> !b.active);
        for (Boulder b : boulders) {
            b.update();
            if (b.active && b.hitbox.intersects(playerHitbox)) {
                pendingBoulderDamage += BOULDER_DAMAGE; b.active = false;
            }
        }

        debrisRocks.removeIf(d -> !d.active);
        for (DebrisRock d : debrisRocks) {
            d.update();
            if (d.active && !d.landed && d.hitbox.intersects(playerHitbox)) {
                pendingDebrisDamage += DEBRIS_DAMAGE; d.active = false;
            }
        }
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (Boulder b    : boulders)    b.draw(g2);
        for (DebrisRock d : debrisRocks) d.draw(g2);

        // Scream shockwave ring
        if (state == State.SCREAM) {
            float pulse = 0.4f + 0.6f * (float) Math.sin(stateTimer * 0.3f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse * 0.5f));
            g2.setColor(new Color(200, 60, 60));
            g2.fillOval(x - 35, y + HEIGHT / 4, WIDTH + 70, HEIGHT / 2);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            g2.setColor(new Color(255, 80, 80, 200));
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString("DEBRIS!", x + WIDTH / 2 - 22, y - 26);
        }

        // Landing shockwave
        if (state == State.LAND) {
            int spread = stateTimer * 8;
            g2.setColor(new Color(180, 140, 80, Math.max(0, 140 - stateTimer * 5)));
            g2.fillRect(x + WIDTH / 2 - spread, y + HEIGHT - 12, spread * 2, 12);
        }

        // Hug wind-up red glow
        if (state == State.HUG_WIND) {
            float prog = stateTimer / (float) HUG_WIND_DUR;
            g2.setColor(new Color(255, 60, 60, (int) (80 * prog)));
            g2.fillRect(x - 15, y, WIDTH + 30, HEIGHT);
        }

        // Jump airborne shadow
        if (state == State.JUMP_RISE || state == State.JUMP_FALL) {
            int shadowY = landTargetY + HEIGHT - 8;
            int shadowW = WIDTH + 20;
            g2.setColor(new Color(0, 0, 0, 60));
            g2.fillOval(x + WIDTH / 2 - shadowW / 2, shadowY, shadowW, 16);
        }

        // ── Select cycling attack frame ────────────────────────────────────────
        BufferedImage frame = switch (state) {
            case SLASH_WIND, SLASH_ACTIVE, SLASH_RECOVER -> pick(slashFrames);
            case HUG_WIND                               -> pick(hugWindFrames);
            case HUG_CRUSH                              -> pick(hugCrushFrames);
            case JUMP_RISE, JUMP_FALL                   -> pick(jumpFrames);
            case LAND                                   -> pick(landFrames);
            case SCREAM                                 -> pick(screamFrames);
            case BOULDER_WIND, BOULDER_THROW            -> pick(boulderFrames);
            case RECOVER                                -> pick(recoverFrames);
            default                                     -> walkFrames[animFrame];
        };

        if (frame != null) {
            int drawW = (int) ((double) frame.getWidth() / frame.getHeight() * DRAW_H);
            int drawX = x + WIDTH / 2 - drawW / 2;
            if (direction < 0) g2.drawImage(frame, drawX + drawW, y, -drawW, DRAW_H, null);
            else               g2.drawImage(frame, drawX,          y,  drawW, DRAW_H, null);
        } else {
            g2.setColor(new Color(50, 30, 10));
            g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("AMOMONGO", x + 2, y + 70);
        }

        // Boulder wind-up bar
        if (state == State.BOULDER_WIND) {
            float prog = stateTimer / (float) BOULDER_WIND_DUR;
            int barX   = x + WIDTH / 2 - 40;
            g2.setColor(new Color(80, 0, 0));     g2.fillRect(barX, y - 20, 80, 7);
            g2.setColor(new Color(200, 30, 30));   g2.fillRect(barX, y - 20, (int) (80 * prog), 7);
            g2.setColor(new Color(255, 200, 50, 210));
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString("!", x + WIDTH / 2 - 3, y - 24);
        }

        // HP bar
        int barX = x + WIDTH / 2 - 55;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, y - 10, 110, 5);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 10, (int) (110 * (health / (float) MAX_HP)), 5);
    }

    /** Returns the current attack frame from an array, falling back to walk frame. */
    private BufferedImage pick(BufferedImage[] arr) {
        return (arr[atkFrame] != null) ? arr[atkFrame] : walkFrames[animFrame];
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public void      takeDamage(int n)  { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; playSound("sounds/enemy/death/creature.wav"); } }
    public float     getHealthRatio()   { return (float) health / MAX_HP; }
    public boolean   isAlive()          { return health > 0; }
    public Rectangle getHitbox()        { return hitbox; }

    private static BufferedImage img(String p) {
        try { return ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }
    private static void playSound(String path) {
        try {
            javax.sound.sampled.AudioInputStream ais =
                javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.File(path));
            javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        } catch (Exception ignored) {}
    }


}
