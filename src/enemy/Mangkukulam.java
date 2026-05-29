import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.util.ArrayList;
import javax.sound.sampled.*;

public class Mangkukulam {

    public static final int WIDTH  = 130;
    public static final int HEIGHT = 130;

    public int x, y;
    private final Rectangle hitbox;

    private int health              = 1000;
    private boolean deathSoundPlayed = false;
    private static final int MAX_HP = 1000;

    private static final int WALK_SPEED  = 2;
    private static final int MELEE_RANGE = 140;
    private int direction = -1;

    private enum State { WALK, MELEE, CURSE, GRAB }
    private State state = State.WALK;

    public  int pendingMeleeDamage = 0;
    private int meleeTimer         = 0;
    private int meleeCooldown      = 0;
    public  static final int MELEE_DAMAGE   = 10;   // nerfed from 15
    private static final int MELEE_DURATION = 25;
    private static final int MELEE_INTERVAL = 60;

    public final ArrayList<CurseOrb>       projectiles = new ArrayList<>();
    public final ArrayList<GrabProjectile> grabs       = new ArrayList<>();
    private int curseTimer = 120;
    private int grabTimer  = 210;
    private static final int CURSE_CD = 420;
    private static final int GRAB_CD  = 300;

    private int actionTimer = 0;

    private final BufferedImage[] walkFrames = new BufferedImage[6];
    private BufferedImage atkFrame, longAtkFrame;
    private int animFrame = 0, animCounter = 0;
    private static final int ANIM_DELAY = 10;

    public Mangkukulam(int x, int y) {
        this.x = x; this.y = y;
        hitbox = new Rectangle(x, y, WIDTH, HEIGHT);
        for (int i = 0; i < 6; i++) walkFrames[i] = img("assets/Enemy/Mangkukulam/Mangkukulam_KF" + (i+1) + ".png");
        atkFrame     = img("assets/Enemy/Mangkukulam/Mangkukulam_ShortAtk_KF1.png");
        longAtkFrame = img("assets/Enemy/Mangkukulam/Mangkukulam_LongAtk_KF1.png");
        SoundManager.playCooldown("sounds/enemy/mangkukulam.wav", 800); // spawn sound
    }

    public void update(int px, int py, Rectangle playerHitbox) {
        pendingMeleeDamage = 0;
        if (meleeCooldown > 0) meleeCooldown--;

        int dx  = (px + 50) - (x + WIDTH / 2);
        direction = dx < 0 ? -1 : 1;
        boolean near = Math.abs(dx) < MELEE_RANGE;

        if (curseTimer > 0) curseTimer--;
        if (grabTimer  > 0) grabTimer--;

        State prevState = state;
        switch (state) {
            case WALK -> {
                if (near && meleeCooldown == 0) {
                    state = State.MELEE; meleeTimer = 0;
                } else if (curseTimer == 0 && !near) {
                    if (grabTimer == 0) grabTimer = GRAB_CD / 2;
                    fireOrb(px, py);
                } else if (grabTimer == 0 && !near) {
                    if (curseTimer == 0) curseTimer = CURSE_CD / 2;
                    fireGrab(px, py);
                } else {
                    x += WALK_SPEED * direction;
                }
            }
            case MELEE -> {
                if (++meleeTimer == 10 && getMeleeHitbox().intersects(playerHitbox)) pendingMeleeDamage = MELEE_DAMAGE;
                if (meleeTimer >= MELEE_DURATION) { state = State.WALK; meleeTimer = 0; meleeCooldown = MELEE_INTERVAL; }
            }
            case CURSE, GRAB -> { if (++actionTimer > 20) { state = State.WALK; actionTimer = 0; } }
        }

        if (prevState == State.WALK && state != State.WALK && isAlive())
            SoundManager.playCooldown("sounds/enemy/mangkukulam.wav", 400);
        hitbox.setBounds(x, y, WIDTH, HEIGHT);
        if (++animCounter >= ANIM_DELAY) { animFrame = (animFrame + 1) % 6; animCounter = 0; }

        projectiles.removeIf(o -> !o.active);
        grabs.removeIf(g -> !g.active);
        projectiles.forEach(CurseOrb::update);
        grabs.forEach(g -> g.update(x + WIDTH / 2, y + HEIGHT / 2));
    }

    private void fireOrb(int px, int py) {
        double angle = Math.atan2((py+50)-(y+HEIGHT/2), (px+50)-(x+WIDTH/2));
        projectiles.add(new CurseOrb(x+WIDTH/2, y+HEIGHT/2, angle));
        curseTimer = CURSE_CD; state = State.CURSE; actionTimer = 0;
    }

    private void fireGrab(int px, int py) {
        double angle = Math.atan2((py+50)-(y+HEIGHT/2), (px+50)-(x+WIDTH/2));
        grabs.add(new GrabProjectile(x+WIDTH/2, y+HEIGHT/2, angle));
        grabTimer = GRAB_CD; state = State.GRAB; actionTimer = 0;
    }

    public void draw(Graphics g) {
        Graphics2D g2 = (Graphics2D) g;
        BufferedImage frame = switch (state) {
            case CURSE, GRAB -> longAtkFrame != null ? longAtkFrame : walkFrames[animFrame];
            case MELEE       -> atkFrame     != null ? atkFrame     : walkFrames[animFrame];
            default          -> walkFrames[animFrame];
        };
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        if (frame != null) {
            int drawW = (int)((double) frame.getWidth() / frame.getHeight() * HEIGHT);
            int drawX = x + WIDTH/2 - drawW/2;
            if (direction < 0) g2.drawImage(frame, drawX+drawW, y, -drawW, HEIGHT, null);
            else               g2.drawImage(frame, drawX,       y,  drawW, HEIGHT, null);
        } else {
            g2.setColor(new Color(80, 0, 120)); g2.fillRect(x, y, WIDTH, HEIGHT);
        }
        projectiles.forEach(o -> o.draw(g2));
        grabs.forEach(gb -> gb.draw(g2));
    }

    public Rectangle getMeleeHitbox() {
        return new Rectangle(direction > 0 ? x + WIDTH : x - 70, y, 70, HEIGHT);
    }

    public float     getHealthRatio() { return (float) health / MAX_HP; }
    public void      takeDamage(int n) { health = Math.max(0, health - n);
        if (health <= 0 && !deathSoundPlayed) { deathSoundPlayed = true; SoundManager.play("sounds/enemy/death/woman.wav"); } }
    public boolean   isAlive()         { return health > 0; }
    public Rectangle getHitbox()       { return hitbox; }

    private static BufferedImage img(String p) {
        try { return javax.imageio.ImageIO.read(new File(p)); } catch (Exception e) { return null; }
    }

    //CurseOrb
    public static class CurseOrb {
        public int x, y;
        public boolean active = true;
        private final double dx, dy;
        private int life = 0;

        private static final int SPEED    = 4;
        private static final int MAX_LIFE = 260;
        private static final int DRAW_R   = 36;

        public static final int DOT_DURATION_TICKS = 180;
        public static final int DOT_TICK_INTERVAL  = 20;
        public static final int DOT_TICK_DAMAGE    = 2;   // nerfed from 3

        private static BufferedImage effectImg;
        private static boolean imgLoaded = false;

        public CurseOrb(int sx, int sy, double angle) {
            x = sx; y = sy;
            dx = Math.cos(angle) * SPEED;
            dy = Math.sin(angle) * SPEED;
            if (!imgLoaded) { imgLoaded = true;
                try { effectImg = javax.imageio.ImageIO.read(new File("assets/Enemy/Mangkukulam/Mangkukulam_SumpaLongAt_Effect.png")); }
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
                g2.drawImage(effectImg, -DRAW_R, -DRAW_R, DRAW_R*2, DRAW_R*2, null);
                g2.setTransform(saved);
            } else {
                g2.setColor(new Color(140, 0, 200, 180)); g2.fillOval(x-DRAW_R, y-DRAW_R, DRAW_R*2, DRAW_R*2);
                g2.setColor(new Color(60, 255, 120, 200)); g2.fillOval(x-10, y-10, 20, 20);
            }
        }

        public Rectangle getHitbox() { return new Rectangle(x-14, y-14, 28, 28); }
    }

    //GrabProjectile
    public static class GrabProjectile {
        public int x, y;
        public boolean active = true, outgoing = true;
        private double dx, dy;
        private int life = 0;

        private static final int SPEED    = 6;
        private static final int MAX_LIFE = 180;
        private static final int DRAW_R   = 36;
        public  static final int DAMAGE   = 12;   // nerfed from 18

        private static BufferedImage effectImg;
        private static boolean imgLoaded = false;

        public GrabProjectile(int sx, int sy, double angle) {
            x = sx; y = sy;
            dx = Math.cos(angle) * SPEED;
            dy = Math.sin(angle) * SPEED;
            if (!imgLoaded) { imgLoaded = true;
                try { effectImg = javax.imageio.ImageIO.read(new File("assets/Enemy/Mangkukulam/Mangkukulam_GrabAtk_Effect.png")); }
                catch (java.io.IOException ignored) {}
            }
        }

        public void update(int bossX, int bossY) {
            if (!active) return;
            if (outgoing) {
                x += (int) dx; y += (int) dy;
                if (++life >= MAX_LIFE) { outgoing = false; life = 0; turnTo(bossX, bossY); }
            } else {
                turnTo(bossX, bossY);
                x += (int) dx; y += (int) dy;
                if (Math.hypot(bossX-x, bossY-y) < 20 || ++life > 200) active = false;
            }
        }

        private void turnTo(int tx, int ty) {
            double a = Math.atan2(ty-y, tx-x);
            dx = Math.cos(a) * SPEED; dy = Math.sin(a) * SPEED;
        }

        public void draw(Graphics2D g2) {
            float alpha = outgoing ? 1f : 0.65f;
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, alpha));
            if (effectImg != null) {
                double angle = Math.atan2(dy, dx);
                var saved = g2.getTransform();
                g2.translate(x, y); g2.rotate(angle);
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g2.drawImage(effectImg, -DRAW_R, -DRAW_R, DRAW_R*2, DRAW_R*2, null);
                g2.setTransform(saved);
            } else {
                g2.setColor(outgoing ? new Color(200,60,60,200) : new Color(200,60,60,100));
                g2.fillOval(x-DRAW_R, y-DRAW_R, DRAW_R*2, DRAW_R*2);
            }
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        public Rectangle getHitbox() { return new Rectangle(x-14, y-14, 28, 28); }
    }
    }
