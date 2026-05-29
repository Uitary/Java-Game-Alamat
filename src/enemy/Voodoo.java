import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Voodoo {

    private static final int DRAW_H    = 80;
    private static final int DRAW_W    = 80;

    public  static final int WIDTH     = 40;
    public  static final int HEIGHT    = 60;

    private int x, y;  
    private final float speedMult;
    private static final int BASE_SPEED = 2;
    private int direction = 1;
    private int health    = 150;
    private boolean deathSoundPlayed = false;
    private final Rectangle hitbox = new Rectangle();

    public  int pendingContactDamage = 0;
    public  static final int CONTACT_DAMAGE = 5;

    private static final int STOP_TICKS = 90;
    private boolean stopped   = false;
    private int     stopTimer = 0;

    private boolean booming   = false;
    private int     boomTimer = 0;
    private static final int BOOM_DRAW_TICKS = 8;
    private static final int BOOM_DRAW_SIZE  = 120;
    private static final int BOOM_RING_OUTER = 60;

    private static final int ANIM_FRAMES = 8;
    private static final int ANIM_DELAY  = 6;
    private int animFrame = 0, animCounter = 0;

    private static BufferedImage[] runFrames;
    private static BufferedImage   atkFrame;
    private static BufferedImage   boomImg;
    private static boolean spritesLoaded = false;

    public Voodoo(int x, int groundY, float speedMult, int direction) {
        this.x = x;
        this.y = groundY - DRAW_H;
        this.speedMult = speedMult;
        this.direction = direction;
        syncHitbox();
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/voodoo.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;
        runFrames = new BufferedImage[ANIM_FRAMES];
        for (int i = 0; i < ANIM_FRAMES; i++) {
            try { runFrames[i] = ImageIO.read(new File("assets/Enemy/Voodoo/Voodoo_KF" + (i+1) + ".png")); }
            catch (IOException ignored) {}
        }
        try { atkFrame = ImageIO.read(new File("assets/Enemy/Voodoo/Voodoo_LongAtk_KF1.png")); } catch (IOException ignored) {}
        try { boomImg  = ImageIO.read(new File("assets/Enemy/Voodoo/Voodoo_Boom_Effect.png"));  } catch (IOException ignored) {}
    }

    private void syncHitbox() {
        hitbox.setBounds(x + (DRAW_W - WIDTH) / 2, y + (DRAW_H - HEIGHT), WIDTH, HEIGHT);
    }

    public boolean update(Rectangle playerHitbox, boolean playerOnGround) {
        pendingContactDamage = 0;

        if (booming) { boomTimer++; return boomTimer >= BOOM_DRAW_TICKS; }
        if (health <= 0) { booming = true; boomTimer = 0; return false; }

        if (stopped) {
            if (++stopTimer >= STOP_TICKS) {
                health = 0; booming = true; boomTimer = 0;
                pendingContactDamage = CONTACT_DAMAGE;
            }
            syncHitbox();
            return false;
        }

        if (playerOnGround) {
            int px = playerHitbox.x + playerHitbox.width  / 2;
            int ex = x              + DRAW_W               / 2;
            direction = px < ex ? -1 : 1;
        }

        x += (int)(BASE_SPEED * speedMult) * direction;
        syncHitbox();

        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % ANIM_FRAMES; animCounter = 0; }
        if (hitbox.intersects(playerHitbox)) { stopped = true; stopTimer = 0; SoundManager.playCooldown("sounds/enemy/voodoo.wav", 400); }
        return false;
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        if (booming) {
            float alpha = Math.max(0f, 1f - boomTimer / (float) BOOM_DRAW_TICKS);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            int cx = x + DRAW_W / 2, cy = y + DRAW_H / 2;
            if (boomImg != null)
                g2.drawImage(boomImg, cx - BOOM_DRAW_SIZE/2, cy - BOOM_DRAW_SIZE/2, BOOM_DRAW_SIZE, BOOM_DRAW_SIZE, null);
            else {
                g2.setColor(new Color(255, 160, 30, (int)(200 * alpha)));
                g2.fillOval(cx - BOOM_DRAW_SIZE/2, cy - BOOM_DRAW_SIZE/2, BOOM_DRAW_SIZE, BOOM_DRAW_SIZE);
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
            return;
        }

        BufferedImage frame = stopped
                ? (atkFrame  != null ? atkFrame     : (runFrames != null ? runFrames[0] : null))
                : (runFrames != null ? runFrames[animFrame] : null);

        if (frame != null) {
            int fw = (int)((double) frame.getWidth() / frame.getHeight() * DRAW_H);
            int fx = x + DRAW_W / 2 - fw / 2;
            if (direction < 0) g2.drawImage(frame, fx + fw, y, -fw, DRAW_H, null);
            else               g2.drawImage(frame, fx,      y,  fw, DRAW_H, null);
        } else {
            g2.setColor(new Color(160, 40, 200));
            g2.fillRect(x + DRAW_W/2 - 20, y, 40, DRAW_H);
        }

        if (!stopped) {
            int barX = x + DRAW_W/2 - 30, barY = y - 8;
            g2.setColor(new Color(80, 0, 0));    g2.fillRect(barX, barY, 60, 5);
            g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, barY, (int)(60*(health/150f)), 5);
        }
    }

    public boolean isBooming() { return booming; }

    public boolean isInBlastRing(Rectangle target) {
        int cx = x + DRAW_W / 2, cy = y + DRAW_H / 2;
        int closestX = Math.max(target.x, Math.min(cx, target.x + target.width));
        int closestY = Math.max(target.y, Math.min(cy, target.y + target.height));
        return Math.hypot(cx - closestX, cy - closestY) <= BOOM_RING_OUTER;
    }

    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/bat.wav"); } }
    public boolean   isAlive()         { return health > 0 || booming; }
    public Rectangle getHitbox()       { return hitbox; }
}
