import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Bangis {

    public static final int WIDTH     = 60;
    public static final int HEIGHT    = 60;
    private static final int DRAW_SIZE = 70;

    private int x, y;
    private final float speedMult;
    private static final int BASE_SPEED = 2;
    private int direction = 1;          // last known chase direction
    private int health    = 200;
    private boolean deathSoundPlayed = false;
    private final Rectangle hitbox;

    private int atkCooldown = 0;
    private static final int ATK_INTERVAL = 30;

    // Sprites
    private static BufferedImage[] runFrames;
    private static BufferedImage   atkFrame;
    private static boolean spritesLoaded = false;

    private int animCounter = 0, animFrame = 0;
    private static final int ANIM_DELAY = 12;

    public Bangis(int x, int y) { this(x, y, 1f, 1); }

    public Bangis(int x, int y, float speedMult, int direction) {
        this.x = x; this.y = y;
        this.speedMult = speedMult;
        this.direction = direction;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/bangis.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;
        runFrames = new BufferedImage[2];
        for (int i = 0; i < 2; i++) {
            try { runFrames[i] = ImageIO.read(new File("assets/Enemy/Bangis/Bangis_Run_KF" + (i+1) + ".png")); }
            catch (IOException ignored) {}
        }
        try { atkFrame = ImageIO.read(new File("assets/Enemy/Bangis/Bangis_Atk_KF1.png")); }
        catch (IOException ignored) {}
    }

    //Update 
    public void update(int playerX, boolean playerOnGround) {
        // Only retarget when the player is grounded
        if (playerOnGround) {
            int ex = x + WIDTH / 2;
            direction = playerX < ex ? -1 : 1;
        }

        x += (int)(BASE_SPEED * speedMult) * direction;
        hitbox.setBounds(x, y, WIDTH, HEIGHT);

        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 2; animCounter = 0; }
        if (atkCooldown > 0) atkCooldown--;
    }

    //Draw
    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        boolean isAttacking = atkCooldown > ATK_INTERVAL - 15;
        BufferedImage frame = (isAttacking && atkFrame != null) ? atkFrame
                            : (runFrames != null ? runFrames[animFrame] : null);

        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * DRAW_SIZE);
            int drawX = x + WIDTH / 2 - drawW / 2;
            int drawY = y + HEIGHT    - DRAW_SIZE;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (direction < 0) g2.drawImage(frame, drawX + drawW, drawY, -drawW, DRAW_SIZE, null);
            else               g2.drawImage(frame, drawX,         drawY,  drawW, DRAW_SIZE, null);
        } else {
            int dx = x + WIDTH / 2 - DRAW_SIZE / 2;
            int dy = y + HEIGHT    - DRAW_SIZE;
            float hp = health / 200f;
            g.setColor(new Color(Math.min((int)(200*(1-hp)+80), 255), (int)(20*hp), 20));
            g.fillRect(dx, dy, DRAW_SIZE, DRAW_SIZE);
        }

        // HP bar
        int barX = x + WIDTH / 2 - DRAW_SIZE / 2;
        int barY = y + HEIGHT    - DRAW_SIZE;
        g.setColor(new Color(80, 0, 0));    g.fillRect(barX, barY-8, DRAW_SIZE, 5);
        g.setColor(new Color(200, 30, 30)); g.fillRect(barX, barY-8, (int)(DRAW_SIZE*(health/200f)), 5);
    }

    public boolean canAttack() {
        if (atkCooldown == 0) { atkCooldown = ATK_INTERVAL; SoundManager.playCooldown("sounds/enemy/bangis.wav", 300); return true; }
        return false;
    }

    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/bangis.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }
    }
