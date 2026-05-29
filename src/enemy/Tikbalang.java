import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Tikbalang {

    // ── Dimensions ────────────────────────────────────────────────────────────
    public static final int WIDTH  = 100;
    public static final int HEIGHT = 130;
    private static final int DRAW_H = 150;

    public int x, y;
    private final Rectangle hitbox;

    // ── Health ────────────────────────────────────────────────────────────────
    private int health              = 1050;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 1050;

    private static final int WALK_SPEED   = 5;
    private static final int CHARGE_SPEED = 18;
    private static final int DASH_SPEED   = 14;
    private int direction = -1;

    // ── Pending outputs for GamePanel ─────────────────────────────────────────
    public int     pendingChargeDamage = 0;
    public boolean pendingKnockup      = false;
    public int     pendingStompDamage  = 0;
    public int     pendingJumpDamage   = 0;
    public int     pendingBiteDamage   = 0;
    public int     pendingDashDamage   = 0;

    public static final int CHARGE_DAMAGE = 28;
    public static final int STOMP_DAMAGE  = 32;
    public static final int JUMP_DAMAGE   = 38;
    public static final int BITE_DAMAGE   = 40;
    public static final int DASH_DAMAGE   = 20;

    // ── State machine ─────────────────────────────────────────────────────────
    private enum State {
        WALK,
        CHARGE_WIND, CHARGE,
        STOMP_WIND, STOMP, STOMP_RECOVER,
        JUMP_RISE, JUMP_FALL, LAND,
        DASH_FRENZY,
        BITE_WIND, BITE, BITE_RECOVER,
        RECOVER
    }
    private State state = State.WALK;

    private int stateTimer  = 0;
    private int actionDelay = 60;

    // ── Cooldowns ─────────────────────────────────────────────────────────────
    private int chargeCooldown = 180;
    private int stompCooldown  = 0;
    private int jumpCooldown   = 220;
    private int dashCooldown   = 300;
    private int biteCooldown   = 0;

    // ── State durations ───────────────────────────────────────────────────────
    private static final int CHARGE_WIND_DUR    = 45;
    private static final int CHARGE_DUR         = 35;
    private static final int CHARGE_COOLDOWN_MAX = 200;

    private static final int STOMP_WIND_DUR     = 30;
    private static final int STOMP_DUR          = 12;
    private static final int STOMP_RECOVER_DUR  = 40;
    private static final int STOMP_COOLDOWN_MAX  = 170;

    private static final int JUMP_RISE_DUR      = 60;
    private static final int JUMP_FALL_DUR      = 35;
    private static final int LAND_DUR           = 25;
    private static final int JUMP_COOLDOWN_MAX   = 300;

    private static final int DASH_FRENZY_DUR    = 300;
    private static final int DASH_COOLDOWN_MAX   = 400;

    private static final int BITE_WIND_DUR      = 28;
    private static final int BITE_DUR           = 10;
    private static final int BITE_RECOVER_DUR   = 35;
    private static final int BITE_COOLDOWN_MAX   = 150;

    private static final int RECOVER_DUR = 40;

    // ── State flags ───────────────────────────────────────────────────────────
    private boolean chargeHitDealt  = false;
    private boolean stompHitDealt   = false;
    private boolean jumpHitDealt    = false;
    private boolean biteHitDealt    = false;
    private boolean dashHitThisPass = false;

    private float jumpY;
    private float jumpVY;
    private int   jumpTargetX, jumpTargetY;

    private int dashLeft, dashRight;
    private int dashDir = 1;

    // ── Attack animation ──────────────────────────────────────────────────────
    private static final int ATK_FRAMES = 4;  // keyframes per attack animation
    private static final int ATK_DELAY  = 6;  // ticks between attack frames
    private int atkFrame = 0, atkCounter = 0;

    // ── Sprites ───────────────────────────────────────────────────────────────
    private final BufferedImage[] walkFrames    = new BufferedImage[6];
    private final BufferedImage[] chargeFrames  = new BufferedImage[ATK_FRAMES]; // charging sprint
    private final BufferedImage[] stompFrames   = new BufferedImage[ATK_FRAMES]; // stomp
    private final BufferedImage[] dashFrames    = new BufferedImage[ATK_FRAMES]; // dash frenzy
    private final BufferedImage[] biteFrames    = new BufferedImage[ATK_FRAMES]; // bite
    // Aliases – states that share sprite sheets with other attacks
    private BufferedImage[] windFrames;    // reuses chargeFrames
    private BufferedImage[] recoverFrames; // reuses chargeFrames
    private BufferedImage[] jumpFrames;    // reuses stompFrames
    private BufferedImage[] landFrames;    // reuses stompFrames
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 7;

    // ── Constructor ───────────────────────────────────────────────────────────
    public Tikbalang(int x, int y) {
        this.x = x; this.y = y;
        this.jumpY = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++)
            walkFrames[i] = img("assets/Enemy/Tikbalang/Tikbalang_KF" + (i + 1) + ".png");
        for (int i = 0; i < ATK_FRAMES; i++) {
            chargeFrames[i]  = img("assets/Enemy/Tikbalang/Tikbalang_Charge_KF"  + (i + 1) + ".png");
            stompFrames[i]   = img("assets/Enemy/Tikbalang/Tikbalang_Stomp_KF"   + (i + 1) + ".png");
            dashFrames[i]    = img("assets/Enemy/Tikbalang/Tikbalang_ChargeAtk_KF"    + (i + 1) + ".png");
            biteFrames[i]    = img("assets/Enemy/Tikbalang/Tikbalang_Atk_KF"    + (i + 1) + ".png");
        }
        // Alias arrays – point to the closest matching sprite sheet
        windFrames    = chargeFrames;
        recoverFrames = chargeFrames;
        jumpFrames    = stompFrames;
        landFrames    = stompFrames;
        SoundManager.playCooldown("sounds/enemy/tikbalang.wav", 800); // spawn sound
    }

    /** Advances the attack animation frame counter. Call once per tick inside any attack state. */
    private void tickAtk() {
        if (++atkCounter >= ATK_DELAY) { atkFrame = (atkFrame + 1) % ATK_FRAMES; atkCounter = 0; }
    }

    // ── Update ────────────────────────────────────────────────────────────────
    public void update(int px, int py, Rectangle playerHitbox) {
        pendingChargeDamage = 0;
        pendingKnockup      = false;
        pendingStompDamage  = 0;
        pendingJumpDamage   = 0;
        pendingBiteDamage   = 0;
        pendingDashDamage   = 0;

        if (chargeCooldown > 0) chargeCooldown--;
        if (stompCooldown  > 0) stompCooldown--;
        if (jumpCooldown   > 0) jumpCooldown--;
        if (dashCooldown   > 0) dashCooldown--;
        if (biteCooldown   > 0) biteCooldown--;
        if (actionDelay    > 0) actionDelay--;

        int dx    = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near   = Math.abs(dx) < 140;
        boolean medium = Math.abs(dx) < 400;

        State prevState = state;
        switch (state) {

            case WALK -> {
                x += WALK_SPEED * direction;
                if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }

                if (actionDelay == 0) {
                    if (near && biteCooldown == 0) {
                        state = State.BITE_WIND; stateTimer = 0; biteHitDealt = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (near && stompCooldown == 0) {
                        state = State.STOMP_WIND; stateTimer = 0; stompHitDealt = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (chargeCooldown == 0) {
                        state = State.CHARGE_WIND; stateTimer = 0; chargeHitDealt = false;
                        atkFrame = 0; atkCounter = 0;
                    } else if (jumpCooldown == 0) {
                        state = State.JUMP_RISE; stateTimer = 0; jumpHitDealt = false;
                        jumpTargetX = px; jumpTargetY = py;
                        jumpY = y; jumpVY = -14f;
                        atkFrame = 0; atkCounter = 0;
                    } else if (dashCooldown == 0) {
                        state = State.DASH_FRENZY; stateTimer = 0;
                        dashLeft  = Math.max(0,   x - 350);
                        dashRight = Math.min(1200, x + 350);
                        dashDir   = direction; dashHitThisPass = false;
                        atkFrame = 0; atkCounter = 0;
                    }
                }
            }

            case CHARGE_WIND -> {
                tickAtk();
                direction = dx < 0 ? -1 : 1;
                if (++stateTimer >= CHARGE_WIND_DUR) {
                    state = State.CHARGE; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case CHARGE -> {
                tickAtk();
                x += CHARGE_SPEED * direction;
                if (prevState == State.WALK && state != State.WALK && isAlive())
                    SoundManager.playCooldown("sounds/enemy/tikbalang.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
                if (!chargeHitDealt && hitbox.intersects(playerHitbox)) {
                    pendingChargeDamage = CHARGE_DAMAGE;
                    pendingKnockup      = true;
                    chargeHitDealt      = true;
                }
                if (++stateTimer >= CHARGE_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    chargeCooldown = CHARGE_COOLDOWN_MAX; actionDelay = 60;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case STOMP_WIND -> {
                tickAtk();
                if (++stateTimer >= STOMP_WIND_DUR) {
                    state = State.STOMP; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case STOMP -> {
                tickAtk();
                if (stateTimer == 3 && !stompHitDealt) {
                    Rectangle stompBox = new Rectangle(x - 30, y + HEIGHT - 20, WIDTH + 60, 30);
                    if (stompBox.intersects(playerHitbox)) {
                        pendingStompDamage = STOMP_DAMAGE; stompHitDealt = true;
                    }
                }
                if (++stateTimer >= STOMP_DUR) {
                    state = State.STOMP_RECOVER; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case STOMP_RECOVER -> {
                tickAtk();
                if (++stateTimer >= STOMP_RECOVER_DUR) {
                    state = State.WALK; stompCooldown = STOMP_COOLDOWN_MAX; actionDelay = 50;
                }
            }

            case JUMP_RISE -> {
                tickAtk();
                jumpY  += jumpVY;
                jumpVY += 0.5f;
                y = (int) jumpY;
                int jumpDx = jumpTargetX - x;
                x += (jumpDx > 0 ? 1 : -1) * Math.min(6, Math.abs(jumpDx));
                if (++stateTimer >= JUMP_RISE_DUR || jumpVY >= 0) {
                    state = State.JUMP_FALL; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case JUMP_FALL -> {
                tickAtk();
                y += 18; stateTimer++;
                if (!jumpHitDealt) {
                    Rectangle crashBox = new Rectangle(x, y, WIDTH, HEIGHT);
                    if (crashBox.intersects(playerHitbox)) {
                        pendingJumpDamage = JUMP_DAMAGE; jumpHitDealt = true;
                    }
                }
                if (y >= jumpTargetY || stateTimer >= JUMP_FALL_DUR) {
                    y = jumpTargetY; state = State.LAND; stateTimer = 0;
                    jumpCooldown = JUMP_COOLDOWN_MAX;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case LAND -> {
                tickAtk();
                if (!jumpHitDealt) {
                    Rectangle shockwave = new Rectangle(x - 50, y + HEIGHT - 15, WIDTH + 100, 20);
                    if (shockwave.intersects(playerHitbox)) {
                        pendingJumpDamage = JUMP_DAMAGE; jumpHitDealt = true;
                    }
                }
                if (++stateTimer >= LAND_DUR) {
                    state = State.RECOVER; stateTimer = 0; actionDelay = 60;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case DASH_FRENZY -> {
                tickAtk();
                x += DASH_SPEED * dashDir;
                hitbox.setBounds(x, y, WIDTH, HEIGHT);
                if (!dashHitThisPass && hitbox.intersects(playerHitbox)) {
                    pendingDashDamage = DASH_DAMAGE; dashHitThisPass = true;
                }
                if (x <= dashLeft || x >= dashRight) {
                    dashDir = -dashDir; direction = dashDir; dashHitThisPass = false;
                }
                if (++stateTimer >= DASH_FRENZY_DUR) {
                    state = State.RECOVER; stateTimer = 0;
                    dashCooldown = DASH_COOLDOWN_MAX; actionDelay = 80;
                    atkFrame = 0; atkCounter = 0;
                }
            }

            case BITE_WIND -> {
                tickAtk();
                if (++stateTimer >= BITE_WIND_DUR) {
                    state = State.BITE; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case BITE -> {
                tickAtk();
                if (stateTimer == 3 && !biteHitDealt) {
                    Rectangle biteBox = new Rectangle(
                            direction > 0 ? x + WIDTH : x - 90, y + 10, 90, HEIGHT / 2);
                    if (biteBox.intersects(playerHitbox)) {
                        pendingBiteDamage = BITE_DAMAGE; biteHitDealt = true;
                    }
                }
                if (++stateTimer >= BITE_DUR) {
                    state = State.BITE_RECOVER; stateTimer = 0;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case BITE_RECOVER -> {
                tickAtk();
                if (++stateTimer >= BITE_RECOVER_DUR) {
                    state = State.WALK; biteCooldown = BITE_COOLDOWN_MAX; actionDelay = 50;
                }
            }

            case RECOVER -> {
                tickAtk();
                x += (WALK_SPEED / 2) * direction;
                if (++stateTimer >= RECOVER_DUR) state = State.WALK;
            }
        }

        hitbox.setBounds(x, y, WIDTH, HEIGHT);
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Charge: speed-lines ghost
        if (state == State.CHARGE) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.25f));
            g2.setColor(new Color(255, 200, 80));
            BufferedImage cf = pick(chargeFrames);
            if (cf != null) {
                int drawW2 = (int) ((double) cf.getWidth() / cf.getHeight() * DRAW_H);
                int ghost  = direction * 22;
                g2.drawImage(cf, x + WIDTH / 2 - drawW2 / 2 - ghost, y, drawW2, DRAW_H, null);
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Dash frenzy: trailing colour overlay
        if (state == State.DASH_FRENZY) {
            float flash = 0.3f + 0.3f * (float) Math.sin(stateTimer * 0.4f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, flash));
            g2.setColor(new Color(200, 30, 30));
            g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Charge wind-up: red glow
        if (state == State.CHARGE_WIND) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(stateTimer * 0.25f);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse * 0.5f));
            g2.setColor(new Color(255, 80, 0));
            g2.fillOval(x - 10, y, WIDTH + 20, HEIGHT);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Stomp shockwave
        if (state == State.STOMP) {
            int spread = stateTimer * 8;
            g2.setColor(new Color(140, 100, 30, Math.max(0, 140 - stateTimer * 12)));
            g2.fillRect(x + WIDTH / 2 - spread, y + HEIGHT - 12, spread * 2, 12);
        }

        // Jump shadow on ground
        if (state == State.JUMP_RISE || state == State.JUMP_FALL) {
            int shadowY = jumpTargetY + HEIGHT - 6;
            int shadowW = WIDTH + 20;
            g2.setColor(new Color(0, 0, 0, 55));
            g2.fillOval(x + WIDTH / 2 - shadowW / 2, shadowY, shadowW, 14);
        }

        // ── Select cycling attack frame ────────────────────────────────────────
        BufferedImage frame = switch (state) {
            case CHARGE_WIND                          -> pick(windFrames);
            case CHARGE                               -> pick(chargeFrames);
            case RECOVER                              -> pick(recoverFrames);
            case STOMP_WIND, STOMP, STOMP_RECOVER    -> pick(stompFrames);
            case JUMP_RISE, JUMP_FALL                -> pick(jumpFrames);
            case LAND                                -> pick(landFrames);
            case DASH_FRENZY                         -> pick(dashFrames);
            case BITE_WIND, BITE, BITE_RECOVER       -> pick(biteFrames);
            default                                  -> walkFrames[animFrame];
        };

        if (frame != null) {
            int drawW = (int) ((double) frame.getWidth() / frame.getHeight() * DRAW_H);
            int drawX = x + WIDTH / 2 - drawW / 2;
            if (direction < 0) g2.drawImage(frame, drawX + drawW, y, -drawW, DRAW_H, null);
            else               g2.drawImage(frame, drawX,          y,  drawW, DRAW_H, null);
        } else {
            g2.setColor(new Color(60, 40, 10));
            g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(Color.WHITE);
            g2.setFont(new Font("SansSerif", Font.BOLD, 9));
            g2.drawString("TIKBALANG", x + 2, y + 68);
        }

        // Charge wind-up bar
        if (state == State.CHARGE_WIND) {
            float prog = stateTimer / (float) CHARGE_WIND_DUR;
            int barX   = x + WIDTH / 2 - 35;
            g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, y - 20, 70, 6);
            g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 20, (int) (70 * prog), 6);
        }

        // Dash frenzy timer bar
        if (state == State.DASH_FRENZY) {
            float rem  = 1f - stateTimer / (float) DASH_FRENZY_DUR;
            int barX   = x + WIDTH / 2 - 35;
            g2.setColor(new Color(80, 0, 0));    g2.fillRect(barX, y - 20, 70, 6);
            g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 20, (int) (70 * rem), 6);
        }

        // HP bar
        int barX = x + WIDTH / 2 - 45;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, y - 10, 90, 5);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 10, (int) (90 * (health / (float) MAX_HP)), 5);
    }

    /** Returns the current attack frame from an array, falling back to walk frame. */
    private BufferedImage pick(BufferedImage[] arr) {
        return (arr[atkFrame] != null) ? arr[atkFrame] : walkFrames[animFrame];
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public void      takeDamage(int n)  { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/tikbalang.wav"); } }
    public float     getHealthRatio()   { return (float) health / MAX_HP; }
    public boolean   isAlive()          { return health > 0; }
    public Rectangle getHitbox()        { return hitbox; }

    private static BufferedImage img(String p) {
        try { return ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }
    }
