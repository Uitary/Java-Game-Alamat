import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Aswang_Mob {

    public static final int WIDTH  = 130;
    public static final int HEIGHT = 130;

    public int x, y;
    private final Rectangle hitbox;

    private int health              = 800;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 800;

    private static final int WALK_SPEED  = 2;
    private static final int MELEE_RANGE = 150;
    private int direction = -1;

    private enum State { WALK, MELEE, RANGED }
    private State state = State.WALK;

    private int meleeTimer = 0, meleeCooldown = 0;
    private static final int MELEE_DURATION = 30;
    private static final int MELEE_INTERVAL = 72;
    public  int pendingMeleeDamage = 0;

    public final ArrayList<AswangProjectile> projectiles = new ArrayList<>();
    private int fireTimer = 120;
    private static final int FIRE_CD = 260;

    private final BufferedImage[] walkFrames = new BufferedImage[6];
    private BufferedImage atkFrame;
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 10;

    public Aswang_Mob(int x, int y) {
        this.x = x; this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++) walkFrames[i] = img("assets/Enemy/Aswang Mob/MiniAswang_KF" + (i+1) + ".png");
        atkFrame = img("assets/Enemy/Aswang Mob/MiniAswang_KF1.png"); // fallback to walk frame if no dedicated attack sprite
        SoundManager.playCooldown("sounds/enemy/aswang_mob.wav", 500); // spawn sound
    }

    public void update(int px, int py, Rectangle playerHitbox) {
        pendingMeleeDamage = 0;
        if (meleeCooldown > 0) meleeCooldown--;

        int dx  = (px + 50) - (x + WIDTH/2);
        direction = dx < 0 ? -1 : 1;
        boolean near = Math.abs(dx) < MELEE_RANGE;

        if (fireTimer > 0) fireTimer--;
        else if (!near) {
            double angle = Math.atan2((py+50)-(y+HEIGHT/2), (px+50)-(x+WIDTH/2));
            projectiles.add(new AswangProjectile(x+WIDTH/2, y+HEIGHT/2, angle));
            fireTimer = FIRE_CD; state = State.RANGED;
        }

        State prevState = state;
        switch (state) {
            case WALK -> {
                if (near && meleeCooldown == 0) { state = State.MELEE; meleeTimer = 0; }
                else x += WALK_SPEED * direction;
            }
            case MELEE -> {
                if (++meleeTimer == 12 && getMeleeHitbox().intersects(playerHitbox)) pendingMeleeDamage = 7; // nerfed
                if (meleeTimer >= MELEE_DURATION) { state = State.WALK; meleeTimer = 0; meleeCooldown = MELEE_INTERVAL; }
            }
            case RANGED -> { if (++meleeTimer > 20) { state = State.WALK; meleeTimer = 0; } }
        }

        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/aswang_mob.wav", 400);

        hitbox.setBounds(x, y, WIDTH, HEIGHT);
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame+1) % 6; animCounter = 0; }
        projectiles.removeIf(p -> !p.active);
        projectiles.forEach(AswangProjectile::update);
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        boolean atk = (state == State.MELEE || state == State.RANGED);
        BufferedImage frame = (atk && atkFrame != null) ? atkFrame : walkFrames[animFrame];
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * HEIGHT);
            int drawX = x + WIDTH/2 - drawW/2;
            if (direction < 0) g2.drawImage(frame, drawX+drawW, y, -drawW, HEIGHT, null);
            else               g2.drawImage(frame, drawX,       y,  drawW, HEIGHT, null);
        } else {
            System.err.println("[Aswang_Mob] No frame to draw at frame index " + animFrame + " — check assets/Enemy/Aswang/");
            g2.setColor(new Color(120, 0, 40)); g2.fillRect(x, y, WIDTH, HEIGHT);
        }
        projectiles.forEach(p -> p.draw(g2));

        // ── Health bar ────────────────────────────────────────────────────────
        int barW  = WIDTH;           // same width as the sprite
        int barH  = 6;
        int barX  = x;
        int barY  = y - 12;          // a few pixels above the sprite

        // Dark background track
        g2.setColor(new Color(30, 0, 0, 200));
        g2.fillRoundRect(barX, barY, barW, barH, 4, 4);

        // Filled portion — colour shifts from green → yellow → red as HP drops
        float ratio = getHealthRatio();
        g2.setColor(new Color(200, 30, 30));
        g2.fillRoundRect(barX, barY, (int)(barW * ratio), barH, 4, 4);

        // Thin border
        g2.setColor(new Color(0, 0, 0, 160));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(barX, barY, barW, barH, 4, 4);
    }

    public Rectangle getMeleeHitbox() {
        return new Rectangle(direction > 0 ? x + WIDTH : x - 70, y, 70, HEIGHT);
    }

    public float     getHealthRatio() { return (float) health / MAX_HP; }
    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/creature.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    private static BufferedImage img(String p) {
        try {
            File f = new File(p);
            if (!f.exists()) {
                System.err.println("[Aswang_Mob] Missing sprite: " + f.getAbsolutePath());
                return null;
            }
            return ImageIO.read(f);
        } catch (Exception e) {
            System.err.println("[Aswang_Mob] Failed to load: " + p + " — " + e.getMessage());
            return null;
        }
    }

    //AswangProjectile
    public static class AswangProjectile {
        public int x, y;
        public boolean active = true;
        private final double dx, dy;
        private int life = 0;

        private static final int SPEED    = 4;
        private static final int MAX_LIFE = 200;
        private static final int DRAW_W   = 80;
        private static final int DRAW_H   = 80;
        public  static final int DAMAGE   = 9;   // nerfed

        private static BufferedImage effectImg;
        private static boolean imgLoaded = false;

        public AswangProjectile(int sx, int sy, double angle) {
            x = sx; y = sy;
            dx = Math.cos(angle) * SPEED;
            dy = Math.sin(angle) * SPEED;
            if (!imgLoaded) { imgLoaded = true;
                try { effectImg = ImageIO.read(new File("assets/Aswang/Aswang_ClawAtk_Effect.png")); }
                catch (IOException ignored) {}
            }
        }

        public void update() { x += (int) dx; y += (int) dy; if (++life > MAX_LIFE) active = false; }

        public void draw(Graphics2D g2) {
            if (effectImg != null) {
                double angle = Math.atan2(dy, dx);
                var saved = g2.getTransform();
                g2.translate(x, y); g2.rotate(angle);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(effectImg, -DRAW_W/2, -DRAW_H/2, DRAW_W, DRAW_H, null);
                g2.setTransform(saved);
            } else {
                int r = 22;
                g2.setColor(new Color(255,200,50,200)); g2.fillOval(x-r, y-r, r*2, r*2);
                g2.setColor(new Color(255, 80, 0,180)); g2.fillOval(x-r+4, y-r+4, r*2-8, r*2-8);
            }
        }

        public Rectangle getHitbox() { return new Rectangle(x-16, y-16, 32, 32); }
    }
}
