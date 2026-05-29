import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.sound.sampled.*;

public class Severino {

    public static final int WIDTH  = 130;
    public static final int HEIGHT = 130;

    public int x, y;
    private final Rectangle hitbox;

    private int health              = 1200;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 1200;

    private static final int WALK_SPEED  = 2;
    private static final int MELEE_RANGE = 150;
    private int direction = -1;

    //Melee 
    public  int pendingMeleeDamage      = 0;
    private int meleeTimer = 0, meleeCooldown = 0;
    public  static final int MELEE_DAMAGE   = 12;
    private static final int MELEE_DURATION = 30;
    private static final int MELEE_INTERVAL = 50;

    //Dash attack
    public  static final int DASH_DAMAGE          = 22;
    private static final int DASH_CD              = 900;
    private static final int DASH_SPEED           = 20;
    private static final int DASH_DURATION        = 30;
    private static final int CHARGE_FRAMES        = 8;
    private static final int CHARGE_TICKS_PER_FRAME = 8;
    private static final int CHARGE_TOTAL_TICKS   = CHARGE_FRAMES * CHARGE_TICKS_PER_FRAME;
    private static final int SLASH_DURATION       = 20;

    private int dashCooldown = 300, chargeTimer = 0, dashTimer = 0, slashTimer = 0;
    public  boolean pendingDashDamage = false;

    //Claw 
    public final ArrayList<ClawProjectile> claws = new ArrayList<>();
    private int clawCooldown                 = 200;
    private static final int CLAW_CD         = 450;
    private static final int CLAW_COUNT      = 3;
    private static final double CLAW_SPREAD  = Math.toRadians(18);

    //State / animation
    private enum State { WALK, MELEE, CHARGE, DASH, SLASH, RANGED }
    private State state     = State.WALK;
    private int   actionTimer = 0;

    private final BufferedImage[] walkFrames   = new BufferedImage[6];
    private final BufferedImage[] chargeFrames = new BufferedImage[CHARGE_FRAMES];
    private BufferedImage atkFrame, dashAtkFrame, dashSlashFrame;
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 10;

    public Severino(int x, int y) {
        this.x = x; this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++)             walkFrames[i]   = img("assets/Enemy/Severino/Severino_KF"         + (i+1) + ".png");
        for (int i = 0; i < CHARGE_FRAMES; i++) chargeFrames[i] = img("assets/Enemy/Severino/Severino_Charge_KF"  + (i+1) + ".png");
        atkFrame       = img("assets/Enemy/Severino/Severino_ShortAtk_KF1.png");
        dashAtkFrame   = img("assets/Enemy/Severino/Severino_DashAtk_KF1.png");
        dashSlashFrame = img("assets/Enemy/Severino/Severino_DashSlash_KF1.png");
        SoundManager.playCooldown("sounds/enemy/severino.wav", 800); // spawn sound
    }

    public void update(int px, int py, Rectangle playerHitbox) {
        pendingMeleeDamage = 0;
        pendingDashDamage  = false;

        if (meleeCooldown > 0) meleeCooldown--;
        if (dashCooldown  > 0) dashCooldown--;
        if (clawCooldown  > 0) clawCooldown--;

        int dx  = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near = Math.abs(dx) < MELEE_RANGE;

        State prevState = state;
        switch (state) {
            case WALK -> {
                if      (dashCooldown == 0 && !near) { state = State.CHARGE; chargeTimer = 0; }
                else if (clawCooldown == 0 && !near)  fireClaw(px, py);
                else if (near && meleeCooldown == 0)  { state = State.MELEE; meleeTimer = 0; }
                else                                   x += WALK_SPEED * direction;
            }
            case MELEE -> {
                if (++meleeTimer == 12 && getMeleeHitbox().intersects(playerHitbox))
                    pendingMeleeDamage = MELEE_DAMAGE;
                if (meleeTimer >= MELEE_DURATION) { state = State.WALK; meleeTimer = 0; meleeCooldown = MELEE_INTERVAL; }
            }
            case CHARGE -> {
                if (++chargeTimer >= CHARGE_TOTAL_TICKS) { state = State.DASH; dashTimer = 0; dashCooldown = DASH_CD; }
            }
            case DASH -> {
                x += DASH_SPEED * direction;
                if (prevState == State.WALK && state != State.WALK && isAlive())
                    SoundManager.playCooldown("sounds/enemy/severino.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
                if (hitbox.intersects(playerHitbox)) pendingDashDamage = true;
                if (++dashTimer >= DASH_DURATION)    { state = State.SLASH; slashTimer = 0; }
            }
            case SLASH  -> { if (++slashTimer  >= SLASH_DURATION) { state = State.WALK; slashTimer  = 0; } }
            case RANGED -> { if (++actionTimer  > 25)             { state = State.WALK; actionTimer = 0; } }
        }

        hitbox.setBounds(x, y, WIDTH, HEIGHT);
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }

        claws.removeIf(c -> !c.active);
        claws.forEach(ClawProjectile::update);
    }

    private void fireClaw(int px, int py) {
        double base = Math.atan2((py + 50) - (y + HEIGHT / 2), (px + 50) - (x + WIDTH / 2));
        for (int i = 0; i < CLAW_COUNT; i++)
            claws.add(new ClawProjectile(x + WIDTH/2, y + HEIGHT/2, base + CLAW_SPREAD * (i - (CLAW_COUNT - 1) / 2.0)));
        clawCooldown = CLAW_CD; state = State.RANGED; actionTimer = 0;
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        BufferedImage frame = switch (state) {
            case CHARGE -> {
                int fi = Math.min(chargeTimer / CHARGE_TICKS_PER_FRAME, CHARGE_FRAMES - 1);
                yield chargeFrames[fi] != null ? chargeFrames[fi] : walkFrames[animFrame];
            }
            case DASH  -> dashAtkFrame   != null ? dashAtkFrame   : walkFrames[animFrame];
            case SLASH -> dashSlashFrame != null ? dashSlashFrame : walkFrames[animFrame];
            case MELEE -> atkFrame       != null ? atkFrame       : walkFrames[animFrame];
            default    -> walkFrames[animFrame];
        };

        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * HEIGHT);
            int drawX = x + WIDTH / 2 - drawW / 2;
            if (direction < 0) g2.drawImage(frame, drawX + drawW, y, -drawW, HEIGHT, null);
            else               g2.drawImage(frame, drawX,         y,  drawW, HEIGHT, null);
        } else {
            g2.setColor(new Color(60, 0, 80)); g2.fillRect(x, y, WIDTH, HEIGHT);
            g2.setColor(Color.WHITE); g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString("SEVERINO", x + 4, y + 70);
        }

        claws.forEach(c -> c.draw(g2));
    }

    public Rectangle getMeleeHitbox() {
        return new Rectangle(direction > 0 ? x + WIDTH : x - 70, y, 70, HEIGHT);
    }

    public float     getHealthRatio() { return (float) health / MAX_HP; }
    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/guy.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    private static BufferedImage img(String p) {
        try { return javax.imageio.ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }

    //ClawProjectile
    public static class ClawProjectile {
        public int x, y;
        public boolean active = true;
        private final double dx, dy;
        private int life = 0;

        private static final int SPEED    = 6;
        private static final int MAX_LIFE = 200;
        private static final int DRAW_W   = 72;
        private static final int DRAW_H   = 72;
        public  static final int DAMAGE   = 14;

        private static BufferedImage effectImg;
        private static boolean imgLoaded = false;

        public ClawProjectile(int sx, int sy, double angle) {
            x = sx; y = sy;
            dx = Math.cos(angle) * SPEED;
            dy = Math.sin(angle) * SPEED;
            if (!imgLoaded) { imgLoaded = true;
                try { effectImg = javax.imageio.ImageIO.read(new File("assets/Enemy/Severino/Severino_ClawAtk_Effect.png")); }
                catch (java.io.IOException ignored) {}
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
                int r = 20;
                g2.setColor(new Color(255, 200,  50, 200)); g2.fillOval(x-r, y-r, r*2, r*2);
                g2.setColor(new Color(255,  80,   0, 180)); g2.fillOval(x-r+4, y-r+4, r*2-8, r*2-8);
            }
        }

        public Rectangle getHitbox() { return new Rectangle(x - 16, y - 16, 32, 32); }
    }
    }
