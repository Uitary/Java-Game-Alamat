import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

/**
 * Santelmo – floating fire-orb spirit.
 *
 * Behaviour:
 *  • Floats (hovers) toward the player, bobbing vertically.
 *  • Periodically stops and fires a burst of FireBall projectiles aimed at
 *    the player's current position.
 *  • Can fire a 1-, 2-, or 3-projectile burst depending on its HP tier.
 *
 * Contains the inner FireBall class (homing-optional straight projectile).
 */
public class Santelmo {

    public static final int WIDTH  = 70;
    public static final int HEIGHT = 70;
    private static final int DRAW_W = 80;
    private static final int DRAW_H = 80;

    private int x, y;
    private final float speedMult;
    private static final float BASE_SPEED = 2f;
    private int dirX = 1;
    private int health = 200;
    private boolean deathSoundPlayed = false;
    private final Rectangle hitbox;

    // Fire projectiles
    public final ArrayList<FireBall> projectiles = new ArrayList<>();
    private int fireCooldown = 120;
    private static final int FIRE_CD = 200;

    // Shoot-pose timer
    private int shootTimer = 0;
    private static final int SHOOT_POSE_TICKS = 25;
    private boolean shooting = false;

    // Bobbing motion
    private float bobOffset = 0f;
    private static final float BOB_SPEED = 0.06f;
    private static final int   BOB_AMP   = 8;

    private static final int ANIM_FRAMES = 6;
    private static final int ANIM_DELAY  = 8;
    private int animFrame = 0, animCounter = 0;

    private static BufferedImage[] floatFrames;
    private static BufferedImage   shootFrame;
    private static boolean spritesLoaded = false;

    public Santelmo(int x, int y, float speedMult, int dirX) {
        this.x = x; this.y = y;
        this.speedMult = speedMult;
        this.dirX = dirX;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/santelmo.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;
        floatFrames = new BufferedImage[ANIM_FRAMES];
        for (int i = 0; i < ANIM_FRAMES; i++) {
            try { floatFrames[i] = ImageIO.read(new File("assets/Enemy/Santelmo/Santelmo_KF" + (i + 1) + ".png")); }
            catch (IOException ignored) {}
        }
        try { shootFrame = ImageIO.read(new File("assets/Enemy/Santelmo/Santelmo_Shoot_KF1.png")); }
        catch (IOException ignored) {}
    }

    public void update(int playerX, int playerY) {
        if (health <= 0) return;
        if (fireCooldown > 0) fireCooldown--;

        // Determine facing direction
        int cx = playerX + 50;
        dirX = cx > x + WIDTH / 2 ? 1 : -1;

        if (shooting) {
            if (++shootTimer >= SHOOT_POSE_TICKS) { shooting = false; shootTimer = 0; }
        } else {
            // Chase player
            double dist = Math.hypot(cx - (x + WIDTH / 2), (playerY + 50) - (y + HEIGHT / 2));
            if (dist > 10) {
                float speed = BASE_SPEED * speedMult;
                x += (int)((cx - (x + WIDTH / 2)) / dist * speed);
                y += (int)(((playerY + 50) - (y + HEIGHT / 2)) / dist * speed);
            }

            // Fire when cooldown allows
            if (fireCooldown == 0) {
                fireBurst(playerX, playerY);
                SoundManager.playCooldown("sounds/enemy/santelmo.wav", 400);
                fireCooldown = FIRE_CD;
                shooting = true; shootTimer = 0;
            }
        }

        // Bob vertically
        bobOffset += BOB_SPEED;
        int renderY = y + (int)(Math.sin(bobOffset) * BOB_AMP);
        hitbox.setBounds(x, renderY, WIDTH, HEIGHT);

        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % ANIM_FRAMES; animCounter = 0; }

        projectiles.removeIf(fb -> !fb.active);
        projectiles.forEach(FireBall::update);
    }

    /**
     * Fires a burst of 1–3 FireBalls depending on remaining HP.
     * Higher HP → more projectiles.
     */
    private void fireBurst(int px, int py) {
        int count = health > 140 ? 3 : health > 70 ? 2 : 1;
        double base = Math.atan2((py + 50) - (y + HEIGHT / 2), (px + 50) - (x + WIDTH / 2));
        double spread = Math.toRadians(14);
        for (int i = 0; i < count; i++) {
            double angle = base + spread * (i - (count - 1) / 2.0);
            projectiles.add(new FireBall(x + WIDTH / 2, y + HEIGHT / 2, angle));
        }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Glow effect – orange halo behind sprite
        float glow = 0.25f + 0.2f * (float) Math.sin(bobOffset * 2);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, glow));
        g2.setColor(new Color(200, 30, 30));
        g2.fillOval(x - 10, (int)(y + Math.sin(bobOffset) * BOB_AMP) - 10, DRAW_W + 20, DRAW_H + 20);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        int renderY = (int)(y + Math.sin(bobOffset) * BOB_AMP);
        BufferedImage frame = (shooting && shootFrame != null) ? shootFrame
                            : (floatFrames != null ? floatFrames[animFrame] : null);

        if (frame != null) {
            if (dirX < 0) g2.drawImage(frame, x + DRAW_W, renderY, -DRAW_W, DRAW_H, null);
            else          g2.drawImage(frame, x,           renderY,  DRAW_W, DRAW_H, null);
        } else {
            g2.setColor(new Color(255, 100, 0)); g2.fillOval(x, renderY, WIDTH, HEIGHT);
            g2.setColor(new Color(255, 200, 0)); g2.fillOval(x + 10, renderY + 10, WIDTH - 20, HEIGHT - 20);
        }

        projectiles.forEach(fb -> fb.draw(g2));

        // HP bar
        int barX = x + WIDTH / 2 - 25, barY = renderY - 10;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, barY, 50, 4);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, barY, (int)(50 * (health / 200f)), 4);
    }

    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/bat.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    // ── FireBall ──────────────────────────────────────────────────────────────
    /**
     * Straight-line fire projectile fired by Santelmo.
     * Rotates its sprite to face its direction of travel.
     */
    public static class FireBall {
        public int x, y;
        public boolean active = true;

        private static final int   SPEED    = 7;
        private static final int   DRAW_W   = 56;
        private static final int   DRAW_H   = 56;
        private static final int   HIT_R    = 16;
        private static final int   MAX_LIFE = 260;
        public  static final int   DAMAGE   = 12;

        private float fx, fy;
        private final float velX, velY;
        private final double angle;
        private int life = 0;

        private static BufferedImage effectImg;
        private static boolean imgLoaded = false;

        public FireBall(int sx, int sy, double angle) {
            fx = sx; fy = sy;
            x = sx; y = sy;
            this.angle = angle;
            velX = (float)(Math.cos(angle) * SPEED);
            velY = (float)(Math.sin(angle) * SPEED);
            if (!imgLoaded) {
                imgLoaded = true;
                try { effectImg = ImageIO.read(new File("assets/Santelmo_FireAtk_Effect.png")); }
                catch (IOException ignored) {}
            }
        }

        public void update() {
            if (++life > MAX_LIFE) { active = false; return; }
            fx += velX; fy += velY;
            x = Math.round(fx); y = Math.round(fy);
            if (x < -300 || x > 3500 || y < -300 || y > 3500) active = false;
        }

        public void draw(Graphics2D g2) {
            if (effectImg != null) {
                var saved = g2.getTransform();
                g2.translate(x, y); g2.rotate(angle);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(effectImg, -DRAW_W / 2, -DRAW_H / 2, DRAW_W, DRAW_H, null);
                g2.setTransform(saved);
            } else {
                // Fallback: layered fire circles
                int r = 20;
                g2.setColor(new Color(255, 60, 0, 200));  g2.fillOval(x - r, y - r, r * 2, r * 2);
                g2.setColor(new Color(255, 200, 0, 180)); g2.fillOval(x - r / 2, y - r / 2, r, r);
            }
        }

        public Rectangle getHitbox() { return new Rectangle(x - HIT_R, y - HIT_R, HIT_R * 2, HIT_R * 2); }
    }
    }
