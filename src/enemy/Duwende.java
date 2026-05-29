import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Duwende {

    public static final int WIDTH  = 38;
    public static final int HEIGHT = 44;
    private static final int DRAW_W = 52;
    private static final int DRAW_H = 52;

    public  static final int JUMP_DAMAGE = 10;

    private static final int JUMP_RANGE    = 160; // start charging when within this px
    private static final int CHARGE_TICKS  = 40;
    private static final int JUMP_SPEED_X  = 6;
    private static final float JUMP_SPEED_Y = -10f;
    private static final float GRAVITY     = 0.55f;

    private static final int BASE_SPEED  = 3;   // faster skitter than Tiyanak
    private static final int ANIM_FRAMES = 6;
    private static final int ANIM_DELAY  = 5;   // quick animation

    // ── Attack animation ──────────────────────────────────────────────────────
    private static final int ATK_FRAMES = 4;  // keyframes per attack animation
    private static final int ATK_DELAY  = 5;  // ticks between attack frames
    private int atkFrame = 0, atkCounter = 0;

    private int x, y;
    private final int groundY;
    private final float speedMult;
    private int dirX = 1;
    private int health = 80;
    private boolean deathSoundPlayed = false;
    private final Rectangle hitbox;

    private enum State { CHASE, CHARGE, JUMP }
    private State state = State.CHASE;

    private int chargeTimer   = 0;
    private int jumpTimer     = 0;
    private int chaseCooldown = 0;
    private static final int JUMP_COOLDOWN = 150; // 2.5 s at 60 fps

    private float velX = 0, velY = 0;
    public  int pendingDamage = 0;

    private int animFrame = 0, animCounter = 0;

    private static BufferedImage[] runFrames;
    private static BufferedImage[] jumpFrames;   // multi-frame jump animation
    private static boolean spritesLoaded = false;

    public Duwende(int x, int groundY, float speedMult, int dirX) {
        this.x        = x;
        this.groundY  = groundY;
        this.y        = groundY - HEIGHT;
        this.speedMult = speedMult;
        this.dirX     = dirX;
        hitbox = new Rectangle(x, this.y, WIDTH, HEIGHT);
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/duwende.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;
        runFrames = new BufferedImage[ANIM_FRAMES];
        for (int i = 0; i < ANIM_FRAMES; i++) {
            try { runFrames[i] = ImageIO.read(new File("assets/Enemy/Duwende/Duwende_KF" + (i + 1) + ".png")); }
            catch (IOException ignored) {}
        }
        jumpFrames = new BufferedImage[ATK_FRAMES];
        for (int i = 0; i < ATK_FRAMES; i++) {
            try { jumpFrames[i] = ImageIO.read(new File("assets/Enemy/Duwende/Duwende_Jump_KF" + (i + 1) + ".png")); }
            catch (IOException ignored) {}
        }
    }

    public void update(int playerX, int playerY) {
        pendingDamage = 0;
        if (health <= 0) return;

        int cx = playerX + 50;
        dirX = cx > x + WIDTH / 2 ? 1 : -1;
        int dist = Math.abs(cx - (x + WIDTH / 2));

        State prevState = state;
        switch (state) {
            case CHASE -> {
                if (chaseCooldown > 0) chaseCooldown--;
                x += (int)(BASE_SPEED * speedMult) * dirX;
                if (dist <= JUMP_RANGE && chaseCooldown == 0) {
                    state = State.CHARGE; chargeTimer = 0;
                    atkFrame = 0; atkCounter = 0; // reset on state entry
                }
                tickAnim();
            }
            case CHARGE -> {
                // Wind up – crouch stance
                tickAtk();
                if (++chargeTimer >= CHARGE_TICKS) {
                    velX = JUMP_SPEED_X * dirX * speedMult;
                    velY = JUMP_SPEED_Y;
                    jumpTimer = 0;
                    state = State.JUMP;
                    atkFrame = 0; atkCounter = 0;
                }
            }
            case JUMP -> {
                tickAtk();
                velY += GRAVITY;
                x += (int) velX;
                y += (int) velY;

                if (y + HEIGHT >= groundY) {
                    y = groundY - HEIGHT;
                    velX = 0; velY = 0;
                    chaseCooldown = JUMP_COOLDOWN;
                    state = State.CHASE;
                }

                Rectangle playerHitbox = new Rectangle(playerX, playerY, 100, 100);
                if (hitbox.intersects(playerHitbox) && jumpTimer == 0)
                    pendingDamage = JUMP_DAMAGE;

                jumpTimer++;
            }
        }

        if (prevState == State.CHASE && state != State.CHASE && isAlive())
            SoundManager.playCooldown("sounds/enemy/duwende.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
    }

    private void tickAnim() {
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % ANIM_FRAMES; animCounter = 0; }
    }

    /** Advances the attack animation frame counter. Call once per tick inside any attack state. */
    private void tickAtk() {
        if (++atkCounter >= ATK_DELAY) { atkFrame = (atkFrame + 1) % ATK_FRAMES; atkCounter = 0; }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage frame;
        if (state == State.JUMP || state == State.CHARGE) {
            // Use cycling jump/charge frames
            frame = (jumpFrames != null && jumpFrames[atkFrame] != null)
                    ? jumpFrames[atkFrame]
                    : (runFrames != null ? runFrames[0] : null);
        } else {
            frame = runFrames != null ? runFrames[animFrame] : null;
        }

        // Charge: crouching pulse
        if (state == State.CHARGE) {
            float pulse = 0.5f + 0.5f * (float) Math.sin(chargeTimer * 0.3);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
        }

        if (frame != null) {
            if (dirX < 0) g2.drawImage(frame, x + DRAW_W, y + HEIGHT - DRAW_H, -DRAW_W, DRAW_H, null);
            else          g2.drawImage(frame, x,           y + HEIGHT - DRAW_H,  DRAW_W, DRAW_H, null);
        } else {
            g2.setColor(new Color(60, 120, 40)); g2.fillRect(x, y, WIDTH, HEIGHT);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // Tiny HP bar
        int barX = x + WIDTH / 2 - 18, barY = y - 7;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, barY, 36, 3);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, barY, (int)(36 * (health / 80f)), 3);
    }

    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/little.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }
}
