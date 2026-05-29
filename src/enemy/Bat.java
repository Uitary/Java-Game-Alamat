import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Bat {

    public static final int WIDTH  = 80;
    public static final int HEIGHT = 70;
    private static final int DRAW_W = 70;
    private static final int DRAW_H = 56;

    public  static final int BAT_DAMAGE    = 8;
    private static final int BITE_COUNT    = 3;
    private static final int BITE_INTERVAL = 18;
    private static final int BITE_COOLDOWN = 120; // 2 s at 60 fps

    private int x, y;
    private final float speedMult;
    private static final float BASE_SPEED = 2.5f;
    private int dirX = 1;
    private int health = 120;
    private boolean deathSoundPlayed = false;
    private final Rectangle hitbox;

    private int biteCount = 0, biteTimer = 0, biteCooldown = 0;
    public  int pendingDamage = 0;

    private static final int ANIM_FRAMES = 4;
    private static final int ANIM_DELAY  = 6;
    private int animFrame = 0, animCounter = 0;
    private boolean biting = false;

    private static BufferedImage[] flyFrames;
    private static BufferedImage   atkFrame;
    private static boolean spritesLoaded = false;

    public Bat(int x, int y, float speedMult, int dirX) {
        this.x = x; this.y = y;
        this.speedMult = speedMult;
        this.dirX = dirX;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/bat.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;
        flyFrames = new BufferedImage[ANIM_FRAMES];
        for (int i = 0; i < ANIM_FRAMES; i++) {
            try { flyFrames[i] = ImageIO.read(new File("assets/Enemy/bat/Bat_KF" + (i + 1) + ".png")); }
            catch (IOException ignored) {}
        }
        try { atkFrame = ImageIO.read(new File("assets/Enemy/bat/Bat_Atk_KF1.png")); } catch (IOException ignored) {}
    }

    public void update(int playerX, int playerY) {
        pendingDamage = 0;
        if (health <= 0) return;

        if (biteCooldown > 0) { biteCooldown--; biting = false; }

        // Chase player centre
        int targetX = playerX + 50, targetY = playerY + 50;
        double dist = Math.hypot(targetX - (x + WIDTH/2), targetY - (y + HEIGHT/2));
        if (dist > 5) {
            double speed = BASE_SPEED * speedMult;
            x += (int)((targetX - (x + WIDTH/2)) / dist * speed);
            y += (int)((targetY - (y + HEIGHT/2)) / dist * speed);
            dirX = targetX > x + WIDTH/2 ? 1 : -1;
        }

        hitbox.setBounds(x, y, WIDTH, HEIGHT);

        // Bite combo on contact
        if (biteCooldown == 0 && hitbox.intersects(new Rectangle(playerX, playerY, 100, 100))) {
            biting = true;
            biteTimer++;
            if (biteTimer % BITE_INTERVAL == 1 && biteCount < BITE_COUNT) {
                if (biteTimer == 1 && isAlive()) SoundManager.playCooldown("sounds/enemy/bat.wav", 300);
                pendingDamage = BAT_DAMAGE;
                if (++biteCount >= BITE_COUNT) { biteCount = 0; biteTimer = 0; biteCooldown = BITE_COOLDOWN; }
            }
        } else if (biteCooldown == 0) {
            biting = false; biteTimer = 0;
        }

        if (!biting && ++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % ANIM_FRAMES; animCounter = 0; }
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage frame = (biting && atkFrame != null) ? atkFrame
                            : (flyFrames != null ? flyFrames[animFrame] : null);

        if (frame != null) {
            if (dirX < 0) g2.drawImage(frame, x + DRAW_W, y, -DRAW_W, DRAW_H, null);
            else          g2.drawImage(frame, x,           y,  DRAW_W, DRAW_H, null);
        } else {
            g2.setColor(new Color(60, 0, 80)); g2.fillRect(x, y, WIDTH, HEIGHT);
        }

        // HP bar
        int barX = x + WIDTH/2 - 25, barY = y - 8;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, barY, 50, 4);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, barY, (int)(50 * (health / 120f)), 4);
    }

    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/bat.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }
    }
