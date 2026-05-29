import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Tiyanak {

    public static final int WIDTH  = 55;
    public static final int HEIGHT = 60;
    private static final int DRAW_W = 70;
    private static final int DRAW_H = 70;

    public  static final int JUMP_DAMAGE    = 14;
    private static final int JUMP_RANGE     = 180;  // px — triggers charge
    private static final int CHARGE_TICKS   = 60;   // 1 second at 60 fps
    private static final int JUMP_TICKS     = 35;   // airborne duration
    private static final int JUMP_SPEED_X   = 7;
    private static final int JUMP_SPEED_Y   = -12;  // upward impulse
    private static final float GRAVITY      = 0.6f;

    private static final int BASE_SPEED     = 2;
    private static final int ANIM_FRAMES    = 8;
    private static final int ANIM_DELAY     = 6;

    private int x, y;
    private final int groundY;          // pixel Y where feet rest
    private final float speedMult;
    private int dirX = 1;
    private int health = 180;
    private boolean deathSoundPlayed = false;
    private final Rectangle hitbox;

    //State 
    private enum State { CHASE, CHARGE, JUMP }
    private State state = State.CHASE;

    private int chargeTimer   = 0;
    private int jumpTimer     = 0;
    private int chaseCooldown = 0;          // ticks before jump-charge is allowed again
    private static final int JUMP_COOLDOWN = 180; // 3 s at 60 fps
    private float velX = 0, velY = 0;

    public int pendingDamage = 0;       // read by GamePanel each tick

    //Animation 
    private int animFrame = 0, animCounter = 0;

    //Sprites (shared across instances) 
    private static BufferedImage[] runFrames;
    private static BufferedImage   jumpAtkFrame;
    private static boolean spritesLoaded = false;

    public Tiyanak(int x, int groundY, float speedMult, int dirX) {
        this.x       = x;
        this.groundY = groundY;
        this.y       = groundY - HEIGHT;
        this.speedMult = speedMult;
        this.dirX    = dirX;
        hitbox = new Rectangle(x, this.y, WIDTH, HEIGHT);
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/tiyanak.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;
        runFrames = new BufferedImage[ANIM_FRAMES];
        for (int i = 0; i < ANIM_FRAMES; i++) {
            try { runFrames[i] = ImageIO.read(new File("assets/Enemy/Tiyanak/Tiyanak_KF" + (i + 1) + ".png")); }
            catch (IOException ignored) {}
        }
        try { jumpAtkFrame = ImageIO.read(new File("assets/Enemy/Tiyanak/Tiyanak_JumpAtk_KF1.png")); }
        catch (IOException ignored) {}
    }

    public void update(int playerX, int playerY) {
        pendingDamage = 0;
        if (health <= 0) return;

        int cx = playerX + 50; // player centre X
        dirX = cx > x + WIDTH / 2 ? 1 : -1;
        int dist = Math.abs(cx - (x + WIDTH / 2));

        State prevState = state;
        switch (state) {
            case CHASE -> {
                if (chaseCooldown > 0) chaseCooldown--;
                x += (int)(BASE_SPEED * speedMult) * dirX;
                if (dist <= JUMP_RANGE && chaseCooldown == 0) { state = State.CHARGE; chargeTimer = 0; }
                tickAnim();
            }
            case CHARGE -> {
                // Stand still and wind up
                if (++chargeTimer >= CHARGE_TICKS) {
                    // Launch
                    velX = JUMP_SPEED_X * dirX * speedMult;
                    velY = JUMP_SPEED_Y;
                    jumpTimer = 0;
                    state = State.JUMP;
                }
            }
            case JUMP -> {
                velY += GRAVITY;
                x += (int) velX;
                y += (int) velY;

                // Land check
                if (y + HEIGHT >= groundY) {
                    y = groundY - HEIGHT;
                    velX = 0; velY = 0;
                    chaseCooldown = JUMP_COOLDOWN;   // start 3 s cooldown
                    state = State.CHASE;
                }

                // Deal damage on contact during jump
                Rectangle playerHitbox = new Rectangle(playerX, playerY, 100, 100);
                if (hitbox.intersects(playerHitbox) && jumpTimer == 0) {
                    pendingDamage = JUMP_DAMAGE;
                }
                jumpTimer++;
            }
        }

        if (prevState == State.CHASE && state != State.CHASE && isAlive())
            SoundManager.playCooldown("sounds/enemy/tiyanak.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
    }

    private void tickAnim() {
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % ANIM_FRAMES; animCounter = 0; }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // CHARGE: pulse the run frame as a wind-up visual cue.
        // JUMP:   use the dedicated Tiyanak_JumpAtk_KF1 keyframe.
        BufferedImage frame;
        if (state == State.JUMP) {
            frame = jumpAtkFrame != null ? jumpAtkFrame
                                        : (runFrames != null ? runFrames[0] : null);
        } else {
            frame = runFrames != null ? runFrames[animFrame] : null;
        }

        // Charge pulse — subtle brightness flicker via alpha
        if (state == State.CHARGE) {
            float pulse = 0.55f + 0.45f * (float) Math.sin(chargeTimer * 0.25);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, pulse));
        }

        if (frame != null) {
            if (dirX < 0) g2.drawImage(frame, x + DRAW_W, y + HEIGHT - DRAW_H, -DRAW_W, DRAW_H, null);
            else          g2.drawImage(frame, x,           y + HEIGHT - DRAW_H,  DRAW_W, DRAW_H, null);
        } else {
            g2.setColor(new Color(180, 60, 30)); g2.fillRect(x, y, WIDTH, HEIGHT);
        }

        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        // HP bar
        int barX = x + WIDTH / 2 - 25, barY = y - 8;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, barY, 50, 4);
        g2.setColor(new Color(200, 30, 30));  g2.fillRect(barX, barY, (int)(50 * (health / 180f)), 4);
    }

    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/little.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }
}
