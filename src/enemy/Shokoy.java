import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Shokoy {

    public static final int WIDTH  = 90;
    public static final int HEIGHT = 100;
    private static final int DRAW_H = 120;

    public int x, y;
    private final Rectangle hitbox;

    private int health              = 700;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 700;

    private static final int WALK_SPEED  = 3;
    private static final int MELEE_RANGE = 130;
    private int direction = -1;

    public  int pendingMeleeDamage = 0;
    public  static final int HIT1_DAMAGE = 10;
    public  static final int HIT2_DAMAGE = 8;
    public  static final int HIT3_DAMAGE = 14;     // finisher

    private enum State { WALK, COMBO, RECOVER }
    private State state = State.WALK;

    private int comboTimer    = 0;
    private int recoverTimer  = 0;
    private int comboCooldown = 0;

    // Combo hits at specific tick offsets
    private static final int HIT1_TICK      = 8;
    private static final int HIT2_TICK      = 22;
    private static final int HIT3_TICK      = 36;
    private static final int COMBO_DURATION = 50;
    private static final int RECOVER_DURATION = 30;
    private static final int COMBO_COOLDOWN   = 80;

    // ── Attack animation ──────────────────────────────────────────────────────
    private static final int ATK_FRAMES = 4;  // keyframes per attack animation
    private static final int ATK_DELAY  = 6;  // ticks between attack frames
    private int atkFrame = 0, atkCounter = 0;

    private final BufferedImage[] walkFrames  = new BufferedImage[6];
    private final BufferedImage[] atk1Frames  = new BufferedImage[ATK_FRAMES]; // hit 1 animation
    private final BufferedImage[] atk2Frames  = new BufferedImage[ATK_FRAMES]; // hit 2 animation
    private final BufferedImage[] atk3Frames  = new BufferedImage[ATK_FRAMES]; // finisher animation
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 9;

    public Shokoy(int x, int y) {
        this.x = x; this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++)
            walkFrames[i] = img("assets/Enemy/Shokoy/Shokoy_KF" + (i + 1) + ".png");
        for (int i = 0; i < ATK_FRAMES; i++) {
            atk1Frames[i] = img("assets/Enemy/Shokoy/Attack/frame_00" + (i + 1) + ".png");
            atk2Frames[i] = img("assets/Enemy/Shokoy/Attack/frame_00" + (i + 1) + ".png");
            atk3Frames[i] = img("assets/Enemy/Shokoy/Attack/frame_00" + (i + 1) + ".png");
            SoundManager.playCooldown("sounds/enemy/shokoy.wav", 800); // spawn sound
        }
    }

    /** Advances the attack animation frame counter. Call once per tick inside any attack state. */
    private void tickAtk() {
        if (++atkCounter >= ATK_DELAY) { atkFrame = (atkFrame + 1) % ATK_FRAMES; atkCounter = 0; }
    }

    public void update(int px, int py, Rectangle playerHitbox) {
        pendingMeleeDamage = 0;
        if (comboCooldown > 0) comboCooldown--;

        int dx = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near = Math.abs(dx) < MELEE_RANGE;

        State prevState = state;
        switch (state) {
            case WALK -> {
                if (near && comboCooldown == 0) {
                    state = State.COMBO; comboTimer = 0;
                    atkFrame = 0; atkCounter = 0; // reset on combo entry
                } else {
                    x += WALK_SPEED * direction;
                }
                if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }
            }
            case COMBO -> {
                tickAtk();
                Rectangle mh = getMeleeHitbox();
                if (comboTimer == HIT1_TICK && mh.intersects(playerHitbox)) pendingMeleeDamage = HIT1_DAMAGE;
                if (comboTimer == HIT2_TICK && mh.intersects(playerHitbox)) pendingMeleeDamage = HIT2_DAMAGE;
                if (comboTimer == HIT3_TICK && mh.intersects(playerHitbox)) pendingMeleeDamage = HIT3_DAMAGE;
                if (++comboTimer >= COMBO_DURATION) { state = State.RECOVER; recoverTimer = 0; }
            }
            case RECOVER -> {
                if (++recoverTimer >= RECOVER_DURATION) {
                    state = State.WALK; comboCooldown = COMBO_COOLDOWN;
                }
            }
        }

        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/shokoy.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
    }

    public Rectangle getMeleeHitbox() {
        return new Rectangle(direction > 0 ? x + WIDTH : x - 80, y + 10, 80, HEIGHT - 20);
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage frame;
        if (state == State.COMBO) {
            // Select the correct attack animation array based on combo progress
            BufferedImage[] atkSet;
            if      (comboTimer <= HIT2_TICK - 1) atkSet = atk1Frames;
            else if (comboTimer <= HIT3_TICK - 1) atkSet = atk2Frames;
            else                                   atkSet = atk3Frames;
            frame = (atkSet[atkFrame] != null) ? atkSet[atkFrame] : walkFrames[animFrame];
        } else {
            frame = walkFrames[animFrame];
        }

        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * DRAW_H);
            int drawX = x + WIDTH / 2 - drawW / 2;
            if (direction < 0) g2.drawImage(frame, drawX + drawW, y, -drawW, DRAW_H, null);
            else               g2.drawImage(frame, drawX,         y,  drawW, DRAW_H, null);
        } else {
            g2.setColor(new Color(20, 80, 60)); g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("SHOKOY", x + 4, y + 55);
        }

        // HP bar
        int barX = x + WIDTH / 2 - 40;
        g2.setColor(new Color(80, 0, 0));   g2.fillRect(barX, y - 10, 80, 5);
        g2.setColor(new Color(200, 30, 30)); g2.fillRect(barX, y - 10, (int)(80 * (health / (float) MAX_HP)), 5);
    }

    public float     getHealthRatio() { return (float) health / MAX_HP; }
    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/creature.wav"); SoundManager.play("sounds/enemy/death/water.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    private static BufferedImage img(String p) {
        try { return ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }
    }
