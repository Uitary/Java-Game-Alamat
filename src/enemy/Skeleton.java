import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Skeleton {

    public static final int WIDTH  = 48;
    public static final int HEIGHT = 72;
    private static final int DRAW_W = 72;   
    private static final int DRAW_H = 90;

    public static final int   MAX_HP         = 60;
    public static final int   MELEE_DAMAGE   = 12;
    public static final int   MELEE_RANGE    = 55;
    public static final int   THROW_RANGE    = 350;
    public static final int   THROW_COOLDOWN = 120; // ~2 s at 60 fps
    public static final float BASE_SPEED     = 2.0f;

    private enum State { WALK, MELEE, THROW, HIT, DEAD }
    private State state = State.WALK;

    public int x, y;
    private int  hp    = MAX_HP;
    private boolean alive = true;
    private boolean deathSoundPlayed = false;
    private int  dirX  = 1;      
    private final float speed;

    private int meleeCooldown     = 0;
    private int meleeTimer        = 0;
    private int throwCooldown     = 0;
    public  int pendingMeleeDamage = 0; 

    public  int hitFlashTimer = 0;
    private static final int HIT_FLASH_TICKS  = 12;
    private static final int MELEE_DURATION   = 32; 
    private static final int THROW_DURATION   = 28; 
    private static final int DEAD_HOLD_TICKS  = 80; 

    public final ArrayList<BoneProjectile> bones = new ArrayList<>();

    private static final int WALK_FRAMES  = 6;
    private static final int ATK_FRAMES   = 4;
    private static final int DEAD_FRAMES  = 4;

    private static final int WALK_DELAY = 8;  
    private static final int ATK_DELAY  = 7;  
    private static final int DEAD_DELAY = 14; 

    private int animFrame   = 0, animTick   = 0;
    private int atkFrame    = 0, atkTick    = 0;
    private int deadFrame   = 0, deadTick   = 0;
    private int deadHoldTimer = 0;            

    private static BufferedImage[] walkFrames;
    private static BufferedImage[] meleeFrames;
    private static BufferedImage[] throwFrames;
    private static BufferedImage[] deadFrames;
    private static BufferedImage   hitFrame;
    private static boolean         spritesLoaded = false;

    public Skeleton(int x, int y, float speedMult, int dirX) {
        this.x    = x;
        this.y    = y;
        this.dirX = dirX;
        this.speed = BASE_SPEED * speedMult;
        if (!spritesLoaded) loadSprites();
        SoundManager.playCooldown("sounds/enemy/skeleton.wav", 800); // spawn sound
    }

    private static void loadSprites() {
        spritesLoaded = true;

        walkFrames  = new BufferedImage[WALK_FRAMES];
        meleeFrames = new BufferedImage[ATK_FRAMES];
        throwFrames = new BufferedImage[ATK_FRAMES];
        deadFrames  = new BufferedImage[DEAD_FRAMES];

        for (int i = 0; i < WALK_FRAMES; i++)
            walkFrames[i]  = img("assets/Enemy/Skeleton/Skeleton_KF"  + (i + 1) + ".png");
        for (int i = 0; i < ATK_FRAMES; i++) {
            meleeFrames[i] = img("assets/Enemy/Skeleton/Attack/frame_00" + (i + 1) + ".png");
            throwFrames[i] = img("assets/Enemy/Skeleton/Attack/frame_00" + (i + 1) + ".png");
        }
        for (int i = 0; i < DEAD_FRAMES; i++)
            deadFrames[i]  = img("assets/Enemy/Skeleton/Skeleton_Dead_KF"  + (i + 1) + ".png");

        hitFrame = img("assets/Enemy/Skeleton/Skeleton_Hit_KF1.png");
    }

    // ── Public API ────────────────────────────────────────────────────────────
    public boolean isAlive()    { return alive; }

    /**
     * Returns true once the death animation has fully played and the entity
     * is safe to remove from the game world.
     */
    public boolean isReadyToRemove() {
        return !alive && state == State.DEAD && deadHoldTimer >= DEAD_HOLD_TICKS;
    }

    public void takeDamage(int amount) {
        if (!alive) return;
        hp -= amount;
        hitFlashTimer = HIT_FLASH_TICKS;
        if (hp <= 0) {
            hp    = 0;
            alive = false;
            enterDead();
            if (!deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/skeleton.wav"); }
        }
    }

    public Rectangle getHitbox() {
        return new Rectangle(x, y, WIDTH, HEIGHT);
    }

    // ── Update ────────────────────────────────────────────────────────────────
    /** Called once per game tick by GamePanel. */
    public void update(int playerX, int playerY, Rectangle playerHitbox) {
        pendingMeleeDamage = 0;

        // Always update projectiles
        bones.removeIf(b -> !b.active);
        bones.forEach(BoneProjectile::update);

        // Death takes full control once entered
        if (state == State.DEAD) {
            tickDead();
            return;
        }

        if (hitFlashTimer > 0) hitFlashTimer--;
        if (meleeCooldown  > 0) meleeCooldown--;
        if (throwCooldown  > 0) throwCooldown--;

        int cx   = x + WIDTH / 2;
        int pcx  = playerX + 24;           // approximate player centre X
        int dist = Math.abs(cx - pcx);
        dirX = (pcx < cx) ? -1 : 1;

        State prevState = state;
        switch (state) {

            case WALK -> {
                if (dist <= MELEE_RANGE && meleeCooldown == 0) {
                    state      = State.MELEE;
                    meleeTimer = 0;
                    atkFrame   = 0; atkTick = 0;
                } else if (dist > MELEE_RANGE && dist <= THROW_RANGE && throwCooldown == 0) {
                    spawnBone();
                    state    = State.THROW;
                    atkFrame = 0; atkTick = 0;
                } else {
                    // Walk toward player (stay back slightly at melee boundary)
                    if (dist > MELEE_RANGE + 10) x += (int)(dirX * speed);
                }
                tickWalkAnim();
            }

            case MELEE -> {
                tickAtkAnim();
                meleeTimer++;

                // Hit 1 at frame 1, hit 2 at frame 3 of the 4-frame combo
                if (meleeTimer == 8  && getMeleeHitbox().intersects(playerHitbox))
                    pendingMeleeDamage = MELEE_DAMAGE;
                if (meleeTimer == 22 && getMeleeHitbox().intersects(playerHitbox))
                    pendingMeleeDamage = MELEE_DAMAGE;

                if (meleeTimer >= MELEE_DURATION) {
                    state         = State.WALK;
                    meleeCooldown = 60;
                }
            }

            case THROW -> {
                tickAtkAnim();
                meleeTimer++; // reuse timer for throw duration
                if (meleeTimer >= THROW_DURATION) {
                    state         = State.WALK;
                    meleeTimer    = 0;
                    throwCooldown = THROW_COOLDOWN;
                }
            }

            // HIT is a cosmetic flash — still reacts next tick
            case HIT -> state = State.WALK;
        }
        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/skeleton.wav", 400);
    }

    // ── Draw ──────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g2) {
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                            RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Draw bone projectiles behind the skeleton
        bones.forEach(b -> b.draw(g2));

        if (state == State.DEAD) {
            drawFrame(g2, safeGet(deadFrames, deadFrame));
            return;
        }

        // Hit flash: alternate full-white tint
        if (hitFlashTimer > 0 && hitFlashTimer % 2 == 0 && hitFrame != null) {
            drawFrame(g2, hitFrame);
        } else {
            BufferedImage frame = switch (state) {
                case MELEE -> safeGet(meleeFrames, atkFrame);
                case THROW -> safeGet(throwFrames, atkFrame);
                default    -> safeGet(walkFrames,  animFrame);
            };

            // If a sprite didn't load, fall back to the procedural placeholder
            if (frame != null) {
                drawFrame(g2, frame);
            } else {
                drawFallback(g2);
            }
        }

        drawHpBar(g2);
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /** Draws a sprite frame, horizontally flipped when facing left. */
    private void drawFrame(Graphics2D g2, BufferedImage frame) {
        if (frame == null) { drawFallback(g2); return; }
        int drawX = x + WIDTH / 2 - DRAW_W / 2;
        int drawY = y + HEIGHT    - DRAW_H;
        if (dirX < 0) g2.drawImage(frame, drawX + DRAW_W, drawY, -DRAW_W, DRAW_H, null);
        else          g2.drawImage(frame, drawX,           drawY,  DRAW_W, DRAW_H, null);
    }

    /** Procedural placeholder — drawn only when sprite assets are missing. */
    private void drawFallback(Graphics2D g2) {
        boolean flash = hitFlashTimer > 0 && hitFlashTimer % 2 == 0;
        Color bodyCol = flash ? Color.WHITE : new Color(220, 210, 190);

        g2.setColor(bodyCol);
        g2.fillRect(x + 8, y, WIDTH - 16, HEIGHT);

        g2.setColor(new Color(245, 235, 215));
        g2.fillOval(x + 10, y - 18, WIDTH - 20, 24);

        g2.setColor(Color.BLACK);
        int eyeY = y - 12;
        if (dirX == 1) { g2.fillOval(x + 18, eyeY, 8, 8); g2.fillOval(x + 28, eyeY, 8, 8); }
        else           { g2.fillOval(x + 12, eyeY, 8, 8); g2.fillOval(x + 22, eyeY, 8, 8); }

        g2.setColor(new Color(170, 160, 140));
        for (int i = 0; i < 3; i++) g2.drawLine(x + 8, y + 12 + i * 14, x + WIDTH - 8, y + 12 + i * 14);

        int leg = (animFrame % 2 == 0) ? 4 : -4;
        g2.setColor(bodyCol);
        g2.fillRect(x + 10,        y + HEIGHT - 6, 10, 14 + leg);
        g2.fillRect(x + WIDTH - 20, y + HEIGHT - 6, 10, 14 - leg);
    }

    private void drawHpBar(Graphics2D g2) {
        float ratio = hp / (float) MAX_HP;
        int barX = x + WIDTH / 2 - 24, barY = y - 10;
        g2.setColor(Color.DARK_GRAY);      g2.fillRect(barX, barY, 48, 5);
        g2.setColor(Color.RED);
        g2.fillRect(barX, barY, (int)(48 * ratio), 5);
    }

    private void tickWalkAnim() {
        if (++animTick >= WALK_DELAY) { animTick = 0; animFrame = (animFrame + 1) % WALK_FRAMES; }
    }

    private void tickAtkAnim() {
        if (++atkTick >= ATK_DELAY) { atkTick = 0; atkFrame = Math.min(atkFrame + 1, ATK_FRAMES - 1); }
    }

    private void tickDead() {
        if (deadFrame < DEAD_FRAMES - 1) {
            if (++deadTick >= DEAD_DELAY) { deadTick = 0; deadFrame++; }
        } else {
            deadHoldTimer++;
        }
    }

    private void enterDead() {
        state         = State.DEAD;
        deadFrame     = 0;
        deadTick      = 0;
        deadHoldTimer = 0;
    }

    private void spawnBone() {
        int bx = dirX == 1 ? x + WIDTH : x;
        int by = y + HEIGHT / 3;
        bones.add(new BoneProjectile(bx, by, dirX));
    }

    public Rectangle getMeleeHitbox() {
        return new Rectangle(dirX > 0 ? x + WIDTH : x - 60, y + 10, 60, HEIGHT - 20);
    }

    private static BufferedImage safeGet(BufferedImage[] arr, int i) {
        return (arr == null || i < 0 || i >= arr.length) ? null : arr[i];
    }

    private static BufferedImage img(String path) {
        try { return ImageIO.read(new File(path)); }
        catch (IOException ignored) { return null; }
    }

    public float getHealthRatio() { return hp / (float) MAX_HP; }

    // ══════════════════════════════════════════════════════════════════════════
    //  Inner class – BoneProjectile
    // ══════════════════════════════════════════════════════════════════════════
    /**
     * Straight-line bone hurled by the Skeleton.
     * Rotates its sprite to face its direction of travel.
     * Falls off-screen after MAX_LIFE ticks.
     *
     * Expected asset: assets/Enemy/Skeleton/Skeleton_BoneAtk_Effect.png
     */
    public static class BoneProjectile {
        public static final int DAMAGE = 8;

        private static final int SPEED    = 7;
        private static final int DRAW_W   = 40;
        private static final int DRAW_H   = 20;
        private static final int HIT_HALF = 10; // half-size of square hitbox
        private static final int MAX_LIFE = 240;

        public int     x, y;
        public boolean active = true;

        private final int   dx;
        private int         life = 0;
        private float       spin = 0f; // rotation angle in radians

        private static BufferedImage effectImg;
        private static boolean       imgLoaded = false;

        public BoneProjectile(int x, int y, int dirX) {
            this.x  = x;
            this.y  = y;
            this.dx = dirX * SPEED;

            if (!imgLoaded) {
                imgLoaded = true;
                try { effectImg = ImageIO.read(
                        new File("assets/Enemy/Skeleton/Skeleton_BoneAtk_Effect.png")); }
                catch (IOException ignored) {}
            }
        }

        public void update() {
            if (!active) return;
            if (++life > MAX_LIFE) { active = false; return; }
            x    += dx;
            spin += 0.18f; // tumbling rotation
            if (x < -200 || x > 3000) active = false;
        }

        public void draw(Graphics2D g2) {
            if (!active) return;
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                                RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            if (effectImg != null) {
                var saved = g2.getTransform();
                g2.translate(x, y);
                g2.rotate(spin);
                g2.drawImage(effectImg, -DRAW_W / 2, -DRAW_H / 2, DRAW_W, DRAW_H, null);
                g2.setTransform(saved);
            } else {
                // Fallback: spinning oval
                var saved = g2.getTransform();
                g2.translate(x, y);
                g2.rotate(spin);
                g2.setColor(new Color(230, 220, 200));
                g2.fillOval(-DRAW_W / 2, -DRAW_H / 2, DRAW_W, DRAW_H);
                g2.setColor(new Color(160, 150, 130));
                g2.drawOval(-DRAW_W / 2, -DRAW_H / 2, DRAW_W, DRAW_H);
                g2.setTransform(saved);
            }
        }

        public Rectangle getHitbox() {
            return new Rectangle(x - HIT_HALF, y - HIT_HALF, HIT_HALF * 2, HIT_HALF * 2);
        }
    }
    }
