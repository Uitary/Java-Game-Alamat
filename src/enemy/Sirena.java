import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class Sirena {

    // =========================
    // CONSTANTS
    // =========================
    public static final int WIDTH = 120;
    public static final int HEIGHT = 120;

    private static final int WALK_SPEED = 2;
    private static final int BEAM_RANGE = 250;
    private static final int MELEE_RANGE = 160;

    private static final int BEAM_DAMAGE = 2;
    private static final int BEAM_LENGTH = 200;
    private static final int BEAM_HEIGHT = 60;

    private static final int CHANNEL_TIME = 70;
    private static final int BEAM_TIME = 140;
    private static final int RECOVER_TIME = 50;
    private static final int COOLDOWN = 280;

    // =========================
    // POSITION
    // =========================
    public int x, y;
    private float speedMult = 1.0f;
    private int direction = -1;

    // =========================
    // HITBOX
    // =========================
    private final Rectangle hitbox;

    // =========================
    // STATE
    // =========================
    private enum State {
        WALK, CHANNEL, BEAM, RECOVER
    }

    private State state = State.WALK;

    private int channelTimer = 0;
    private int beamTimer = 0;
    private int recoverTimer = 0;
    private int cooldown = 120;

    public int pendingBeamDamage = 0;

    // =========================
    // SPRITES
    // =========================
    private final BufferedImage[] walkFrames = new BufferedImage[6];
    private final BufferedImage[] channelFrames = new BufferedImage[4];
    // beamFrames intentionally removed — we use the idle walk frame instead

    private int animFrame = 0, animCounter = 0;
    private int atkFrame = 0, atkCounter = 0;

    // Frozen walk frame index used while beaming/channelling
    private int frozenWalkFrame = 0;

    // =========================
    // HEALTH
    // =========================
    private int health = 1100;
    private static final int MAX_HP = 1100;
    private boolean deathSoundPlayed = false;

    // =========================
    // CONSTRUCTORS
    // =========================

    public Sirena(int x, int y) {
        this.x = x;
        this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        loadSprites();
        SoundManager.playCooldown("sounds/enemy/sirena.wav", 800); // spawn sound
    }

    public Sirena(int x, int y, float speedMult, int dir) {
        this(x, y);
        this.speedMult = speedMult;
        this.direction = dir;
    }

    private void loadSprites() {
        for (int i = 0; i < 6; i++)
            walkFrames[i] = img("assets/Enemy/Sirena/Sirena_KF" + (i + 1) + ".png");

        for (int i = 0; i < 4; i++)
            channelFrames[i] = img("assets/Enemy/Sirena/Attack/frame_00" + (i + 1) + ".png");

        // No beam sprite files — beam is drawn procedurally in draw()
    }

    // =========================
    // UPDATE
    // =========================

    public void update(int px, int py, Rectangle playerHitbox) {

        pendingBeamDamage = 0;
        if (cooldown > 0)
            cooldown--;

        int dx = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;

        boolean inRange = Math.abs(dx) < BEAM_RANGE && Math.abs(dx) > MELEE_RANGE;

        State prevState = state;
        switch (state) {

            case WALK -> {
                if (inRange && cooldown == 0) {
                    state = State.CHANNEL;
                    channelTimer = 0;
                    frozenWalkFrame = animFrame; // freeze current walk pose
                    resetAtkAnim();
                } else {
                    if (Math.abs(dx) < MELEE_RANGE)
                        x -= (int) (WALK_SPEED * speedMult) * direction;
                    else
                        x += (int) (WALK_SPEED * speedMult) * direction;
                }
                animate();
            }

            case CHANNEL -> {
                tickAtk();
                if (++channelTimer >= CHANNEL_TIME) {
                    state = State.BEAM;
                    beamTimer = 0;
                    resetAtkAnim();
                }
            }

            case BEAM -> {
                tickAtk();
                if (getBeamHitbox().intersects(playerHitbox))
                    pendingBeamDamage = BEAM_DAMAGE;

                if (++beamTimer >= BEAM_TIME) {
                    state = State.RECOVER;
                    recoverTimer = 0;
                }
            }

            case RECOVER -> {
                if (++recoverTimer >= RECOVER_TIME) {
                    state = State.WALK;
                    cooldown = COOLDOWN;
                }
            }
        }

        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/sirena.wav", 400);

        hitbox.setBounds(x, y, WIDTH, HEIGHT);
    }

    // =========================
    // ANIMATION HELPERS
    // =========================

    private void animate() {
        if (++animCounter >= 10) {
            animFrame = (animFrame + 1) % walkFrames.length;
            animCounter = 0;
        }
    }

    private void tickAtk() {
        if (++atkCounter >= 7) {
            atkFrame = (atkFrame + 1) % 4;
            atkCounter = 0;
        }
    }

    private void resetAtkAnim() {
        atkFrame = 0;
        atkCounter = 0;
    }

    // =========================
    // DRAW
    // =========================

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // ── Choose frame ──────────────────────────────────────────────────────
        BufferedImage frame;
        switch (state) {
            case CHANNEL -> {
                // Use channel sprite if available, else fall back to frozen walk frame
                BufferedImage ch = channelFrames[atkFrame];
                frame = (ch != null) ? ch : walkFrames[frozenWalkFrame];
            }
            case BEAM, RECOVER -> {
                // No beam sprite — hold the frozen idle walk frame while firing
                frame = walkFrames[frozenWalkFrame];
            }
            default -> frame = walkFrames[animFrame];
        }

        // ── Draw integrated beam BEHIND the sprite ────────────────────────────
        if (state == State.BEAM) {
            drawIntegratedBeam(g2);
        }

        // ── Draw sprite (flipped for direction) ───────────────────────────────
        if (frame != null) {
            if (direction > 0) {
                // Facing right — draw normally (sprites face right by default)
                g2.drawImage(frame, x, y, WIDTH, HEIGHT, null);
            } else {
                // Facing left — mirror horizontally
                g2.drawImage(frame, x + WIDTH, y, -WIDTH, HEIGHT, null);
            }
        } else {
            // Ultimate fallback if no sprites loaded at all
            g2.setColor(new Color(0, 100, 180));
            g2.fillRect(x, y, WIDTH, HEIGHT);
        }

        // ── Health bar ────────────────────────────────────────────────────────
        float ratio = (float) health / MAX_HP;
        int barW = WIDTH;
        int barH = 6;
        int barX = x;
        int barY = y - 12;

        g2.setColor(new Color(30, 0, 0, 200));
        g2.fillRoundRect(barX, barY, barW, barH, 4, 4);

        g2.setColor(new Color(200, 30, 30));
        g2.fillRoundRect(barX, barY, (int) (barW * ratio), barH, 4, 4);

        g2.setColor(new Color(0, 0, 0, 160));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(barX, barY, barW, barH, 4, 4);
    }

    /**
     * Draws a stylised sonic/water beam emanating from Sirena's mouth area,
     * integrated with the sprite so no separate beam image is needed.
     *
     * • A tapered gradient rectangle representing the beam core
     * • A soft glow halo around it
     * • Pulsing rings at the origin to sell the "charging" feel
     */
    private void drawIntegratedBeam(Graphics2D g2) {
        Rectangle beam = getBeamHitbox();

        // ── Outer glow (wide, very transparent) ──────────────────────────────
        int glowPad = 18;
        g2.setPaint(new GradientPaint(
                beam.x, beam.y + beam.height / 2f,
                new Color(0, 220, 255, 60),
                beam.x + beam.width, beam.y + beam.height / 2f,
                new Color(0, 100, 200, 0)));
        g2.fillRoundRect(beam.x, beam.y - glowPad,
                beam.width, beam.height + glowPad * 2, 30, 30);

        // ── Core beam (narrower, more opaque) ────────────────────────────────
        g2.setPaint(new GradientPaint(
                beam.x, beam.y + beam.height / 2f,
                new Color(180, 240, 255, 210),
                beam.x + beam.width, beam.y + beam.height / 2f,
                new Color(0, 160, 220, 30)));
        int coreH = beam.height / 3;
        g2.fillRoundRect(beam.x, beam.y + (beam.height - coreH) / 2,
                beam.width, coreH, 12, 12);

        // ── Origin pulse rings (3 concentric ovals at Sirena's mouth side) ───
        int originX = direction > 0 ? x + WIDTH : x;
        int originY = y + HEIGHT / 2;
        g2.setStroke(new BasicStroke(1.5f));
        for (int r = 1; r <= 3; r++) {
            int rad = r * 14;
            int alpha = 160 - r * 40;
            g2.setColor(new Color(0, 210, 255, Math.max(0, alpha)));
            g2.drawOval(originX - rad, originY - rad, rad * 2, rad * 2);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    // =========================
    // UTIL
    // =========================

    public Rectangle getBeamHitbox() {
        int bx = direction > 0 ? x + WIDTH : x - BEAM_LENGTH;
        int by = y + HEIGHT / 2 - BEAM_HEIGHT / 2;
        return new Rectangle(bx, by, BEAM_LENGTH, BEAM_HEIGHT);
    }

    public Rectangle getHitbox() {
        return hitbox;
    }

    public float getHealthRatio() {
        return (float) health / MAX_HP;
    }

    public void takeDamage(int dmg) {
        health = Math.max(0, health - dmg);
        if (health <= 0 && !deathSoundPlayed) {
            deathSoundPlayed = true;
            SoundManager.play("sounds/enemy/death/water.wav"); SoundManager.play("sounds/enemy/death/woman.wav");
        }
    }

    public boolean isAlive() {
        return health > 0;
    }

    // =========================
    // ASSET / SOUND HELPERS
    // =========================

    private static BufferedImage img(String path) {
        try {
            return ImageIO.read(new File(path));
        } catch (Exception e) {
            return null;
        }
    }
}
