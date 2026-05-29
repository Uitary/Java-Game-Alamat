import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Kapre {

    // ── Dimensions ────────────────────────────────────────────────────────────
    public static final int WIDTH  = 110;
    public static final int HEIGHT = 130;
    private static final int DRAW_H = 150;

    public int x, y;
    private final Rectangle hitbox;

    // ── Health ────────────────────────────────────────────────────────────────
    private int health              = 900;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 900;
    private static final int HEAL_PER_TICK = 3;

    private static final int WALK_SPEED  = 1;
    private static final int MELEE_RANGE = 170;
    private int direction = -1;

    // ── Pending outputs for GamePanel ─────────────────────────────────────────
    public int     pendingPunchDamage     = 0;
    public int     pendingCigaretteDamage = 0;
    public boolean pendingBurnApplied     = false;
    public boolean pendingConfusePlayer   = false;
    public boolean pendingSlow            = false;

    public static final int PUNCH_DAMAGE     = 45;
    public static final int CIGARETTE_DAMAGE = 15;

    // ── Cigarette projectile ──────────────────────────────────────────────────
    public static class Cigarette {
        public float x, y;
        private final float vx;
        private float vy;
        public boolean active = true;
        public static final int W = 16, H = 8;
        public final Rectangle hitbox;

        public Cigarette(float sx, float sy, float tx, float ty) {
            x = sx; y = sy;
            float dist = Math.max(1f, (float) Math.sqrt((tx - sx) * (tx - sx) + (ty - sy) * (ty - sy)));
            float spd  = 10f;
            vx  = (tx - sx) / dist * spd;
            vy  = (ty - sy) / dist * spd - 3.5f;
            hitbox = new Rectangle((int) x, (int) y, W, H);
        }

        public void update() {
            x  += vx;
            y  += vy;
            vy += 0.26f;
            hitbox.setBounds((int) x, (int) y, W, H);
            if (y > 900) active = false;
        }

        public void draw(Graphics2D g) {
            g.setColor(new Color(210, 190, 150));
            g.fillRoundRect((int) x, (int) y, W, H, 3, 3);
            g.setColor(new Color(255, 100, 20));
            g.fillOval((int) x + W - 6, (int) y + 1, 6, H - 2);
            g.setColor(new Color(220, 220, 220, 100));
            g.fillOval((int) x + W - 3, (int) y - 4, 5, 5);
        }
    }

    public final ArrayList<Cigarette> cigarettes = new ArrayList<>();

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State {
        WALK,
        CIGARETTE_WIND, CIGARETTE_THROW,
        PUNCH_WIND, PUNCH_STRIKE, PUNCH_RECOVER,
        PSYCHIC_WIND, PSYCHIC_ACTIVE,
        SIT_SMOKE,
        STOMP_WIND, STOMP_ACTIVE, STOMP_RECOVER,
        RECOVER
    }
    private State state = State.WALK;

    private int stateTimer  = 0;
    private int actionDelay = 60;

    // ── Cooldowns ─────────────────────────────────────────────────────────────
    private int cigaretteCooldown = 0;
    private int punchCooldown     = 0;
    private int psychicCooldown   = 0;
    private int sitCooldown       = 0;
    private int stompCooldown     = 0;

    // ── State durations ───────────────────────────────────────────────────────
    private static final int CIGARETTE_WIND_DUR    = 35;
    private static final int CIGARETTE_THROW_DUR   = 10;
    private static final int CIGARETTE_COOLDOWN_MAX = 190;

    private static final int PUNCH_WIND_DUR     = 100;
    private static final int PUNCH_STRIKE_DUR   = 14;
    private static final int PUNCH_RECOVER_DUR  = 70;
    private static final int PUNCH_COOLDOWN_MAX  = 140;

    private static final int PSYCHIC_WIND_DUR    = 50;
    private static final int PSYCHIC_ACTIVE_DUR  = 120;
    private static final int PSYCHIC_COOLDOWN_MAX = 600;

    private static final int SIT_SMOKE_DUR      = 120;
    private static final int SIT_COOLDOWN_MAX   = 420;

    private static final int STOMP_WIND_DUR     = 45;
    private static final int STOMP_ACTIVE_DUR   = 20;
    private static final int STOMP_RECOVER_DUR  = 50;
    private static final int STOMP_COOLDOWN_MAX = 200;

    private static final int RECOVER_DUR = 50;

    // ── State flags ───────────────────────────────────────────────────────────
    private boolean punchHitDealt  = false;
    private boolean psychicApplied = false;
    private boolean slowApplied    = false;
    private int     throwTargetX, throwTargetY;

    // ── Attack animation ──────────────────────────────────────────────────────
    private static final int ATK_FRAMES = 4;  // keyframes per attack animation
    private static final int ATK_DELAY  = 8;  // ticks between attack frames
    private int atkFrame = 0, atkCounter = 0;

    // ── Sprites ───────────────────────────────────────────────────────────────
    private final BufferedImage[] walkFrames       = new BufferedImage[6];
    private final BufferedImage[] punchFrames      = new BufferedImage[ATK_FRAMES]; // punch strike
    private final BufferedImage[] cigWindFrames    = new BufferedImage[ATK_FRAMES]; // cigarette wind-up
    private final BufferedImage[] sitFrames        = new BufferedImage[ATK_FRAMES]; // sit & smoke
    private final BufferedImage[] stompFrames      = new BufferedImage[ATK_FRAMES]; // stomp
    // Aliases – states that share sprite sheets with other attacks
    private BufferedImage[] windUpFrames;  // reuses punchFrames
    private BufferedImage[] psychicFrames; // reuses sitFrames
    private BufferedImage[] recoverFrames; // reuses punchFrames
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 12;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Kapre(int x, int y) {
        this.x = x; this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++)
            walkFrames[i] = img("assets/Enemy/Kapre/Kapre_KF" + (i + 1) + ".png");
        for (int i = 0; i < ATK_FRAMES; i++) {
            punchFrames[i]   = img("assets/Enemy/Kapre/Kapre_Punch_KF"   + (i + 1) + ".png");
            cigWindFrames[i] = img("assets/Enemy/Kapre/Kapre_Cigar_Effect" + (i + 1) + ".png");
            sitFrames[i]     = img("assets/Enemy/Kapre/Kapre_Smoke_KF"     + (i + 1) + ".png");
            stompFrames[i]   = img("assets/Enemy/Kapre/Kapre_Stomp_KF"   + (i + 1) + ".png");
        }
        // Alias arrays – point to the closest matching sprite sheet
        windUpFrames  = punchFrames;
        psychicFrames = sitFrames;
        recoverFrames = punchFrames;
        SoundManager.playCooldown("sounds/enemy/kapre.wav", 800); // spawn sound
    }

    /** Advances the attack animation frame counter. Call once per tick inside any attack state. */
    private void tickAtk() {
        if (++atkCounter >= ATK_DELAY) { atkFrame = (atkFrame + 1) % ATK_FRAMES; atkCounter = 0; }
    }

    // ── Update ────────────────────────────────────────────────────────────────
    public void update(int px, int py, Rectangle playerHitbox) {
        pendingPunchDamage     = 0;
        pendingCigaretteDamage = 0;
        pendingBurnApplied     = false;
        pendingConfusePlayer   = false;
        pendingSlow            = false;

        if (cigaretteCooldown > 0) cigaretteCooldown--;
        if (punchCooldown     > 0) punchCooldown--;
        if (psychicCooldown   > 0) psychicCooldown--;
        if (sitCooldown       > 0) sitCooldown--;
        if (stompCooldown     > 0) stompCooldown--;
        if (actionDelay       > 0) actionDelay--;

        int dx    = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near   = Math.abs(dx) < MELEE_RANGE;
        boolean medium = Math.abs(dx) < 500;

        State prevState = state;
        switch (state) {

            case WALK -> {
                if (actionDelay == 0) {
                    if (near && punchCooldown == 0) {
                        state = State.PUNCH_WIND; stateTimer = 0; punchHitDealt = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (near && stompCooldown == 0) {
                        state = State.STOMP_WIND; stateTimer = 0; slowApplied = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (medium && cigaretteCooldown == 0) {
                        state = State.CIGARETTE_WIND; stateTimer = 0;
                        throwTargetX = px; throwTargetY = py;
                        atkFrame = 0; atkCounter = 0;
                    } else if (psychicCooldown == 0) {
                        state = State.PSYCHIC_WIND; stateTimer = 0; psychicApplied = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (sitCooldown == 0 && health < MAX_HP * 0.72f) {
                        state = State.SIT_SMOKE; stateTimer = 0;
                        atkFrame = 0; atkCounter = 0;
                    } else {
                        x += WALK_SPEED * direction;
                    }
                } else {
                    x += WALK_SPEED * direction;
                }
                if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }
            }

            case CIGARETTE_WIND -> {
                tickAtk();
                if (++stateTimer >= CIGARETTE_WIND_DUR) {
                    cigarettes.add(new Cigarette(
                            x + WIDTH / 2f, y + 35,
                            throwTargetX + 25f, throwTargetY + 40f));
                    state = State.CIGARETTE_THROW; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case CIGARETTE_THROW -> {
                tickAtk();
                if (++stateTimer >= CIGARETTE_THROW_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    cigaretteCooldown = CIGARETTE_COOLDOWN_MAX; actionDelay = 60;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case PUNCH_WIND -> {
                tickAtk();
                if (++stateTimer >= PUNCH_WIND_DUR) {
                    state = State.PUNCH_STRIKE; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case PUNCH_STRIKE -> {
                tickAtk();
                if (stateTimer == 5 && !punchHitDealt
                        && getPunchHitbox().intersects(playerHitbox)) {
                    pendingPunchDamage = PUNCH_DAMAGE;
                    punchHitDealt = true;
                }
                if (++stateTimer >= PUNCH_STRIKE_DUR) {
                    state = State.PUNCH_RECOVER; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case PUNCH_RECOVER -> {
                tickAtk();
                if (++stateTimer >= PUNCH_RECOVER_DUR) {
                    state = State.WALK; punchCooldown = PUNCH_COOLDOWN_MAX; actionDelay = 60;
                }
            }

            case PSYCHIC_WIND -> {
                tickAtk();
                if (++stateTimer >= PSYCHIC_WIND_DUR) {
                    state = State.PSYCHIC_ACTIVE; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case PSYCHIC_ACTIVE -> {
                tickAtk();
                if (!psychicApplied) { pendingConfusePlayer = true; psychicApplied = true; }
                if (++stateTimer >= PSYCHIC_ACTIVE_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    psychicCooldown = PSYCHIC_COOLDOWN_MAX; actionDelay = 60;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case SIT_SMOKE -> {
                tickAtk();
                if (stateTimer % 10 == 0) health = Math.min(MAX_HP, health + HEAL_PER_TICK);
                if (++stateTimer >= SIT_SMOKE_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    sitCooldown = SIT_COOLDOWN_MAX; actionDelay = 80;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case STOMP_WIND -> {
                tickAtk();
                if (++stateTimer >= STOMP_WIND_DUR) {
                    state = State.STOMP_ACTIVE; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case STOMP_ACTIVE -> {
                tickAtk();
                if (!slowApplied) { pendingSlow = true; slowApplied = true; }
                if (++stateTimer >= STOMP_ACTIVE_DUR) {
                    state = State.STOMP_RECOVER; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case STOMP_RECOVER -> {
                tickAtk();
                if (++stateTimer >= STOMP_RECOVER_DUR) {
                    state = State.WALK; stompCooldown = STOMP_COOLDOWN_MAX; actionDelay = 60;
                }
            }

            case RECOVER -> {
                tickAtk();
                if (++stateTimer >= RECOVER_DUR) state = State.WALK;
            }
        }

        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/kapre.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);

        cigarettes.removeIf(c -> !c.active);
        for (Cigarette c : cigarettes) {
            c.update();
            if (c.active && c.hitbox.intersects(playerHitbox)) {
                pendingCigaretteDamage += CIGARETTE_DAMAGE;
                pendingBurnApplied      = true;
                c.active = false;
            }
        }
    }

    /** Extended hitbox in front of Kapre during the punch frame. */
    public Rectangle getPunchHitbox() {
        return new Rectangle(direction > 0 ? x + WIDTH : x - 110, y + 10, 110, HEIGHT - 20);
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        for (Cigarette c : cigarettes) c.draw(g2);

        // Stomp shockwave rings
        if (state == State.STOMP_ACTIVE) {
            int spread = stateTimer * 7;
            g2.setColor(new Color(120, 80, 20, Math.max(0, 130 - stateTimer * 7)));
            g2.fillRect(x + WIDTH / 2 - spread, y + HEIGHT - 10, spread * 2, 10);
        }

        // Psychic purple aura
        if (state == State.PSYCHIC_WIND || state == State.PSYCHIC_ACTIVE) {
            float pulse = 0.4f + 0.6f * (float) Math.sin(stateTimer * 0.25f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse * 0.45f));
            g2.setColor(new Color(160, 60, 255));
            g2.fillOval(x - 15, y - 10, WIDTH + 30, HEIGHT + 20);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Sit smoke particles
        if (state == State.SIT_SMOKE) {
            for (int i = 0; i < 4; i++) {
                int alpha = Math.min(200, 60 + stateTimer * 2);
                g2.setColor(new Color(200, 200, 200, alpha));
                int sx = x + WIDTH / 2 - 8 + (int) (Math.sin(stateTimer * 0.3 + i * 1.5) * 14);
                int sy = y - 10 - (stateTimer % 35) * 2 - i * 14;
                g2.fillOval(sx, sy, 10, 10);
            }
        }

        // Punch wind-up amber pulse
        if (state == State.PUNCH_WIND) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(stateTimer * 0.18f);
            g2.setColor(new Color(255, 140, 0, (int) (120 * pulse)));
            int dw = (int) ((double) WIDTH / HEIGHT * DRAW_H);
            g2.fillRect(x + WIDTH / 2 - dw / 2, y, dw, DRAW_H);
        }

        // ── Select cycling attack frame ────────────────────────────────────────
        BufferedImage frame = switch (state) {
            case CIGARETTE_WIND, CIGARETTE_THROW -> pick(cigWindFrames);
            case PUNCH_WIND                      -> pick(windUpFrames);
            case PUNCH_STRIKE                    -> pick(punchFrames);
            case PUNCH_RECOVER, RECOVER          -> pick(recoverFrames);
            case PSYCHIC_WIND, PSYCHIC_ACTIVE    -> pick(psychicFrames);
            case SIT_SMOKE                       -> pick(sitFrames);
            case STOMP_WIND, STOMP_ACTIVE,
                 STOMP_RECOVER                   -> pick(stompFrames);
            default                              -> walkFrames[animFrame];
        };

        if (frame != null) {
            int drawW = (int) ((double) frame.getWidth() / frame.getHeight() * DRAW_H);
            int drawX = x + WIDTH / 2 - drawW / 2;
            if (direction < 0) g2.drawImage(frame, drawX + drawW, y, -drawW, DRAW_H, null);
            else               g2.drawImage(frame, drawX,          y,  drawW, DRAW_H, null);
        } else {
            g2.setColor(new Color(70, 45, 15));
            g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("KAPRE", x + 5, y + 70);
        }

        // Punch wind-up warning bar
        if (state == State.PUNCH_WIND) {
            float prog = stateTimer / (float) PUNCH_WIND_DUR;
            int barX   = x + WIDTH / 2 - 40;
            g2.setColor(new Color(80, 0, 0));      g2.fillRect(barX, y - 20, 80, 7);
            g2.setColor(new Color(200, 30, 30));    g2.fillRect(barX, y - 20, (int) (80 * prog), 7);
            g2.setColor(new Color(255, 230, 0, 220));
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString("!", x + WIDTH / 2 - 3, y - 24);
        }

        // Psychic wind-up bar
        if (state == State.PSYCHIC_WIND) {
            float prog = stateTimer / (float) PSYCHIC_WIND_DUR;
            int barX   = x + WIDTH / 2 - 40;
            g2.setColor(new Color(80, 0, 0));     g2.fillRect(barX, y - 20, 80, 7);
            g2.setColor(new Color(200, 30, 30));  g2.fillRect(barX, y - 20, (int) (80 * prog), 7);
            g2.setColor(new Color(210, 160, 255, 220));
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("?!", x + WIDTH / 2 - 6, y - 24);
        }

        // Sit-smoke heal bar
        if (state == State.SIT_SMOKE) {
            float prog = stateTimer / (float) SIT_SMOKE_DUR;
            int barX   = x + WIDTH / 2 - 40;
            g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, y - 20, 80, 7);
            g2.setColor(new Color(200, 30, 30));  g2.fillRect(barX, y - 20, (int) (80 * prog), 7);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            g2.drawString("HEALING", barX, y - 23);
        }

        // HP bar
        int barX = x + WIDTH / 2 - 50;
        g2.setColor(new Color(80, 0, 0));    g2.fillRect(barX, y - 10, 100, 5);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 10, (int) (100 * (health / (float) MAX_HP)), 5);
    }

    /** Returns the current attack frame from an array, or null if all are null. */
    private BufferedImage pick(BufferedImage[] arr) {
        return (arr[atkFrame] != null) ? arr[atkFrame] : walkFrames[animFrame];
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public void takeDamage(int n) {
        if (state == State.SIT_SMOKE) {
            state = State.RECOVER;
            stateTimer = 0;
            sitCooldown = SIT_COOLDOWN_MAX;
        }
        health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/creature.wav"); }
    }

    public float     getHealthRatio() { return (float) health / MAX_HP; }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    private static BufferedImage img(String p) {
        try { return ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }
    }
