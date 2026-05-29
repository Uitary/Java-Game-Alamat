import java.awt.*;
import java.awt.image.BufferedImage;
import javax.sound.sampled.*;

public class Bakunawa {

    // ── Size ──────────────────────────────────────────────────────────────────
    public static final int W = 160;
    public static final int H = 160;

    // ── Stats ─────────────────────────────────────────────────────────────────
    public int     health    = 500;
    private boolean deathSoundPlayed = false;
    public int     maxHealth = 500;
    public boolean alive     = true;

    // ── Screen bounds (set in constructor) ────────────────────────────────────
    private int screenW;
    private int screenH;
    private int floorY;   // bottom swim boundary
    private int ceilY;    // top  swim boundary

    // ── Damage constants (same as before so GamePanel compiles unchanged) ─────
    public static final int BEAM_DAMAGE = 20;
    public static final int SLAM_DAMAGE = 35;

    // ── State machine ─────────────────────────────────────────────────────────
    public enum State {
        SWIM,           // gliding around the frame
        BEAM_CHARGE,    // winding up beam
        BEAM_FIRE,      // beam active
        DIVE_WINDUP,    // hovering above player, shaking
        DIVE,           // plunging straight down
        SLAM,           // alias kept for GamePanel compatibility (same as DIVE)
        DIVE_RECOVER,   // flying back to a swim waypoint
        HIT,
        DEAD
    }

    public State state = State.SWIM;

    // ── Position / swimming movement ─────────────────────────────────────────
    public  int   x, y;
    private float swimX, swimY;
    private float velX,  velY;
    private static final float SWIM_SPEED     = 3.5f;
    private static final float STEER_STRENGTH = 0.18f;
    private float targetX, targetY;

    private int   waypointIndex = 0;
    private int[][] waypoints;

    // ── Attack scheduling ─────────────────────────────────────────────────────
    private static final int MIN_ATTACK_INTERVAL = 3 * 60;
    private static final int MAX_ATTACK_INTERVAL = 7 * 60;
    private int attackCooldown = 3 * 60;

    // ── Beam attack ───────────────────────────────────────────────────────────
    private static final int BEAM_CHARGE_TICKS = 60;
    private static final int BEAM_ACTIVE_TICKS = 50;
    public  boolean beamActive  = false;
    public  int     beamOriginX, beamOriginY;
    public  double  beamDirX = 1, beamDirY = 0;
    public  int     beamLength  = 0;
    private int     beamTimer   = 0;
    private int     lockedTargetX, lockedTargetY;

    private int beamChargeFrame = 0, beamChargeCounter = 0;
    private static final int BEAM_CHARGE_FRAME_COUNT = 8, BEAM_CHARGE_FRAME_DELAY = 8;

    // ── Dive attack ───────────────────────────────────────────────────────────
    private static final int   DIVE_WINDUP_TICKS = 60;
    private static final float DIVE_SPEED        = 22f;
    private static final float RECOVER_SPEED     = 5f;
    private int   diveTargetX;
    private float diveTargetY;
    private float recoverTargetX, recoverTargetY;

    private int chargeFrame = 0, chargeCounter = 0;
    private static final int CHARGE_FRAME_COUNT = 8, CHARGE_FRAME_DELAY = 10;

    // ── General timer ─────────────────────────────────────────────────────────
    private int actionTimer = 0;

    // ── Hit flash ─────────────────────────────────────────────────────────────
    private int   hitTimer       = 0;
    private static final int HIT_FLASH_TICKS = 12;
    private State stateBeforeHit = State.SWIM;

    // ── Facing ────────────────────────────────────────────────────────────────
    private boolean facingLeft = false;

    // ── Idle animation ────────────────────────────────────────────────────────
    private int idleFrame = 0, idleCounter = 0;
    private static final int IDLE_FRAME_COUNT = 8, IDLE_FRAME_DELAY = 8;

    // ── Dead animation ────────────────────────────────────────────────────────
    private int deadFrame = 0, deadCounter = 0;
    private static final int DEAD_FRAME_COUNT = 3, DEAD_FRAME_DELAY = 20;

    // ── Assets ────────────────────────────────────────────────────────────────
    public BufferedImage[] idleFrames       = new BufferedImage[IDLE_FRAME_COUNT];
    public BufferedImage[] chargeFrames     = new BufferedImage[CHARGE_FRAME_COUNT];
    public BufferedImage[] beamChargeFrames = new BufferedImage[BEAM_CHARGE_FRAME_COUNT];
    public BufferedImage   beamKF1;
    public BufferedImage   beamKF2;
    public BufferedImage   chargeAtkFrame;
    public BufferedImage   hitFrameImg;
    public BufferedImage[] deadFrames = new BufferedImage[DEAD_FRAME_COUNT];

    // ── RNG ───────────────────────────────────────────────────────────────────
    private static final java.util.Random RNG = new java.util.Random();

    // ─────────────────────────────────────────────────────────────────────────
    // Constructor
    // ─────────────────────────────────────────────────────────────────────────
    public Bakunawa(int spawnX, int baseGroundY, int screenW) {
        this.screenW = screenW;
        this.screenH = 607;                   // GamePanel.BASE_H
        this.ceilY   = 40;
        this.floorY  = baseGroundY - 20;

        // Eight waypoints around the perimeter of the swim area
        int marg = W / 2 + 20;
        int midY = (ceilY + floorY) / 2 - H / 2;
        waypoints = new int[][] {
            { marg,                  ceilY + H / 2   },   // top-left
            { screenW / 2 - W / 2,  ceilY + H / 2   },   // top-center
            { screenW - W - marg,    ceilY + H / 2   },   // top-right
            { screenW - W - marg,    midY             },   // mid-right
            { screenW - W - marg,    floorY - H       },   // bottom-right
            { screenW / 2 - W / 2,  floorY - H       },   // bottom-center
            { marg,                  floorY - H       },   // bottom-left
            { marg,                  midY             },   // mid-left
        };

        swimX = screenW / 2f - W / 2f;
        swimY = ceilY + H / 2f;
        x = (int) swimX;
        y = (int) swimY;
        velX = SWIM_SPEED;
        velY = 0.5f;
        waypointIndex = RNG.nextInt(waypoints.length);
        pickNextWaypoint();

        attackCooldown = 3 * 60;
    }

    // ── Asset loading (unchanged) ─────────────────────────────────────────────
    public void loadAssets() {
        for (int i = 0; i < 8; i++)
            idleFrames[i]       = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_KF"            + (i+1) + ".png");
        for (int i = 0; i < 8; i++)
            chargeFrames[i]     = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_Charge_KF"     + (i+1) + ".png");
        for (int i = 0; i < 8; i++)
            beamChargeFrames[i] = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_ChargeBeam_KF" + (i+1) + ".png");
        beamKF1        = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_BeamAtk_Effect_KF1.png");
        beamKF2        = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_BeamAtk_Effect_KF2.png");
        hitFrameImg    = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_Hit_KF1.png");
        chargeAtkFrame = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_ChargeAtk_KF1.png");
        for (int i = 0; i < 3; i++)
            deadFrames[i] = AssetManager.img("assets/Enemy/Bakunawa/Bakunawa_Dead_KF" + (i+1) + ".png");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Main update  (signature unchanged — GamePanel needs no edits)
    // ─────────────────────────────────────────────────────────────────────────
    public void update(int playerX, int playerY) {
        if (!alive) { tickDead(); return; }

        if (hitTimer > 0) {
            hitTimer--;
            if (hitTimer == 0 && state == State.HIT) state = stateBeforeHit;
        }

        switch (state) {
            case SWIM         -> tickSwim(playerX, playerY);
            case BEAM_CHARGE  -> tickBeamCharge(playerX, playerY);
            case BEAM_FIRE    -> tickBeamFire();
            case DIVE_WINDUP  -> tickDiveWindup(playerX, playerY);
            case DIVE, SLAM   -> tickDive();
            case DIVE_RECOVER -> tickDiveRecover();
            default           -> {}
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // SWIM — smooth waypoint-based movement around the frame
    // ─────────────────────────────────────────────────────────────────────────
    private void tickSwim(int playerX, int playerY) {
        float dx   = targetX - swimX;
        float dy   = targetY - swimY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);

        if (dist < 40f) {
            pickNextWaypoint();
        } else {
            velX += (dx / dist) * STEER_STRENGTH;
            velY += (dy / dist) * STEER_STRENGTH;
        }

        // Cap to cruise speed
        float spd = (float) Math.sqrt(velX * velX + velY * velY);
        if (spd > SWIM_SPEED) { velX = velX / spd * SWIM_SPEED; velY = velY / spd * SWIM_SPEED; }

        swimX += velX;
        swimY += velY;
        swimX = Math.max(0, Math.min(screenW - W, swimX));
        swimY = Math.max(ceilY, Math.min(floorY - H, swimY));
        x = (int) swimX;
        y = (int) swimY;

        facingLeft = (velX < 0);
        tickIdleAnim();

        // Attack scheduling
        if (--attackCooldown <= 0) {
            triggerRandomAttack(playerX, playerY);
        }
    }

    private void triggerRandomAttack(int playerX, int playerY) {
        playSound("sounds/enemy/bakunawa.wav");
        if (RNG.nextInt(2) == 0) startBeamCharge(playerX, playerY);
        else                     startDiveWindup(playerX, playerY);
        attackCooldown = MIN_ATTACK_INTERVAL
                + RNG.nextInt(MAX_ATTACK_INTERVAL - MIN_ATTACK_INTERVAL + 1);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // BEAM ATTACK
    // ─────────────────────────────────────────────────────────────────────────
    private void startBeamCharge(int playerX, int playerY) {
        state             = State.BEAM_CHARGE;
        actionTimer       = 0;
        beamChargeFrame   = 0;
        beamChargeCounter = 0;
        lockedTargetX     = playerX;
        lockedTargetY     = playerY;
        facingLeft        = (playerX < x + W / 2);
        // Stop moving while charging
        velX = 0; velY = 0;
    }

    private void tickBeamCharge(int playerX, int playerY) {
        facingLeft = (lockedTargetX < x + W / 2);
        actionTimer++;
        if (++beamChargeCounter >= BEAM_CHARGE_FRAME_DELAY) {
            beamChargeCounter = 0;
            if (beamChargeFrame < BEAM_CHARGE_FRAME_COUNT - 1) beamChargeFrame++;
        }
        if (actionTimer >= BEAM_CHARGE_TICKS) fireBeam(lockedTargetX, lockedTargetY);
    }

    private void fireBeam(int playerX, int playerY) {
        int mouthX = facingLeft ? x : x + W;
        int mouthY = y + H / 2;
        double dx  = (playerX + Player.PS / 2.0) - mouthX;
        double dy  = (playerY + Player.PS / 2.0) - mouthY;
        double len = Math.sqrt(dx * dx + dy * dy);
        if (len < 1) len = 1;
        beamDirX    = dx / len;
        beamDirY    = dy / len;
        beamOriginX = mouthX;
        beamOriginY = mouthY;
        beamLength  = computeBeamLength(mouthX, mouthY, beamDirX, beamDirY);
        beamActive  = true;
        beamTimer   = 0;
        state       = State.BEAM_FIRE;
    }

    private int computeBeamLength(int ox, int oy, double dx, double dy) {
        double t = Double.MAX_VALUE;
        if (dx > 0)      t = Math.min(t, (screenW - ox) / dx);
        else if (dx < 0) t = Math.min(t, -ox / dx);
        if (dy > 0)      t = Math.min(t, (floorY - oy) / dy);
        else if (dy < 0) t = Math.min(t, -oy / dy);
        return (int) Math.max(0, t);
    }

    private void tickBeamFire() {
        if (++beamTimer >= BEAM_ACTIVE_TICKS) {
            beamActive = false;
            state      = State.SWIM;
            pickNextWaypoint();
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // DIVE ATTACK
    // ─────────────────────────────────────────────────────────────────────────
    private void startDiveWindup(int playerX, int playerY) {
        state         = State.DIVE_WINDUP;
        actionTimer   = 0;
        chargeFrame   = 0;
        chargeCounter = 0;
        diveTargetX   = playerX - W / 2;
        swimX = diveTargetX;
        swimY = Math.max(ceilY, playerY - H - 60);
        x = (int) swimX;
        y = (int) swimY;
        velX = 0; velY = 0;
        facingLeft = (playerX < x + W / 2);
    }

    private void tickDiveWindup(int playerX, int playerY) {
        // Track player horizontally during windup
        diveTargetX = playerX - W / 2;
        swimX = diveTargetX;
        facingLeft = (playerX < (int) swimX + W / 2);
        // Shake
        x = (int) swimX + (int)(Math.sin(actionTimer * 0.8) * 5);
        y = (int) swimY;

        actionTimer++;
        if (++chargeCounter >= CHARGE_FRAME_DELAY) {
            chargeCounter = 0;
            chargeFrame   = (chargeFrame + 1) % CHARGE_FRAME_COUNT;
        }
        if (actionTimer >= DIVE_WINDUP_TICKS) {
            diveTargetY = floorY - H;
            state       = State.SLAM;   // GamePanel checks State.SLAM for contact damage
            actionTimer = 0;
        }
    }

    private void tickDive() {
        swimY += DIVE_SPEED;
        y = (int) swimY;
        if (swimY >= diveTargetY) {
            swimY = diveTargetY;
            y     = (int) swimY;
            // Pick a random waypoint to recover toward
            int wi = RNG.nextInt(waypoints.length);
            recoverTargetX = waypoints[wi][0];
            recoverTargetY = waypoints[wi][1];
            state = State.DIVE_RECOVER;
        }
    }

    private void tickDiveRecover() {
        float dx   = recoverTargetX - swimX;
        float dy   = recoverTargetY - swimY;
        float dist = (float) Math.sqrt(dx * dx + dy * dy);
        if (dist <= RECOVER_SPEED + 1f) {
            swimX = recoverTargetX;
            swimY = recoverTargetY;
            x = (int) swimX;
            y = (int) swimY;
            state = State.SWIM;
            velX = (RNG.nextFloat() - 0.5f) * SWIM_SPEED;
            velY = (RNG.nextFloat() - 0.5f) * SWIM_SPEED;
            pickNextWaypoint();
            return;
        }
        swimX += (dx / dist) * RECOVER_SPEED;
        swimY += (dy / dist) * RECOVER_SPEED;
        x = (int) swimX;
        y = (int) swimY;
        facingLeft = (dx < 0);
        tickIdleAnim();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Waypoint helper
    // ─────────────────────────────────────────────────────────────────────────
    private void pickNextWaypoint() {
        waypointIndex = (waypointIndex + 1) % waypoints.length;
        targetX = waypoints[waypointIndex][0];
        targetY = waypoints[waypointIndex][1];
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Animations
    // ─────────────────────────────────────────────────────────────────────────
    private void tickIdleAnim() {
        if (++idleCounter >= IDLE_FRAME_DELAY) {
            idleCounter = 0;
            idleFrame   = (idleFrame + 1) % IDLE_FRAME_COUNT;
        }
    }

    private void tickDead() {
        if (++deadCounter >= DEAD_FRAME_DELAY) {
            deadCounter = 0;
            if (deadFrame < DEAD_FRAME_COUNT - 1) deadFrame++;
        }
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Hitboxes (signatures unchanged)
    // ─────────────────────────────────────────────────────────────────────────
    public Rectangle getHitbox() {
        return new Rectangle(x, y, W, H);
    }

    public Rectangle getBeamHitbox() {
        if (!beamActive) return new Rectangle(0, 0, 0, 0);
        int endX = beamOriginX + (int)(beamDirX * beamLength);
        int endY = beamOriginY + (int)(beamDirY * beamLength);
        return new Rectangle(
            Math.min(beamOriginX, endX) - 16,
            Math.min(beamOriginY, endY) - 16,
            Math.abs(endX - beamOriginX) + 32,
            Math.abs(endY - beamOriginY) + 32
        );
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Damage (unchanged)
    // ─────────────────────────────────────────────────────────────────────────
    public boolean takeDamage(int dmg) {
        if (!alive || state == State.DEAD) return false;
        health -= dmg;
        if (health <= 0) {
            health = 0; alive = false;
            state = State.DEAD; deadFrame = 0; deadCounter = 0;
            if (!deathSoundPlayed) { deathSoundPlayed = true; playSound("sounds/enemy/death/bakunawa.wav"); playSound("sounds/enemy/death/water.wav"); }
            return true;
        }
        if (state != State.HIT) stateBeforeHit = state;
        hitTimer = HIT_FLASH_TICKS;
        state    = State.HIT;
        return false;
    }

    public float getHealthRatio() {
        return (float) health / maxHealth;
    }

    @Override
    public String toString() {
        return "Bakunawa[" + state + " hp=" + health + "]";
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Drawing
    // ─────────────────────────────────────────────────────────────────────────
    public void draw(Graphics2D g2) {
        BufferedImage frame = pickBodyFrame();
        if (frame != null) {
            int fw = frame.getWidth(), fh = frame.getHeight();
            if (facingLeft) g2.drawImage(frame, x + fw, y, -fw, fh, null);
            else            g2.drawImage(frame, x,      y,  fw, fh, null);
        } else {
            g2.setColor(new Color(0, 120, 200, 180));
            g2.fillRoundRect(x, y, W, H, 30, 30);
        }

        // Hit flash
        if (hitTimer > 0) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g2.setColor(Color.RED);
            g2.fillRoundRect(x, y, W, H, 30, 30);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        if (beamActive) drawBeam(g2);

        // Dive warning: dashed red line below the boss during windup
        if (state == State.DIVE_WINDUP) {
            float pulse = (float)(0.5 + 0.5 * Math.sin(actionTimer * 0.25));
            int alpha = (int)(120 + pulse * 110);
            g2.setColor(new Color(255, 60, 60, alpha));
            g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND,
                    1f, new float[]{12f, 8f}, 0f));
            int cx = x + W / 2;
            g2.drawLine(cx, y + H, cx, floorY);
            g2.setStroke(new BasicStroke(1f));
        }

        drawHealthBar(g2);
    }

    private BufferedImage pickBodyFrame() {
        return switch (state) {
            case BEAM_CHARGE              -> safeGet(beamChargeFrames, beamChargeFrame);
            case DIVE_WINDUP, DIVE, SLAM -> safeGet(chargeFrames, chargeFrame);
            case DIVE_RECOVER             -> chargeAtkFrame != null ? chargeAtkFrame : safeGet(idleFrames, idleFrame);
            case HIT                      -> hitFrameImg    != null ? hitFrameImg    : safeGet(idleFrames, idleFrame);
            case DEAD                     -> safeGet(deadFrames, deadFrame);
            default                       -> safeGet(idleFrames, idleFrame);
        };
    }

    private void drawBeam(Graphics2D g2) {
        if (beamKF1 == null && beamKF2 == null) return;
        int kf1NatW = beamKF1 != null ? beamKF1.getWidth()  : 0;
        int kf1NatH = beamKF1 != null ? beamKF1.getHeight() : 0;
        int kf2NatW = beamKF2 != null ? beamKF2.getWidth()  : kf1NatW;
        int kf2NatH = beamKF2 != null ? beamKF2.getHeight() : kf1NatH;
        int kf1W = kf1NatW * 2, kf1H = kf1NatH * 2;
        int kf2W = kf2NatW * 2, kf2H = kf2NatH * 2;
        int segH = Math.max(kf1H, kf2H);

        java.awt.geom.AffineTransform saved = g2.getTransform();
        g2.translate(beamOriginX, beamOriginY);
        g2.rotate(Math.atan2(beamDirY, beamDirX));
        int cur = 0;
        if (beamKF1 != null) { g2.drawImage(beamKF1, cur, -segH / 2, kf1W, kf1H, null); cur += kf1W; }
        while (beamKF2 != null && cur < beamLength) {
            g2.drawImage(beamKF2, cur, -segH / 2, kf2W, kf2H, null);
            cur += kf2W;
        }
        g2.setTransform(saved);
    }

    private void drawHealthBar(Graphics2D g2) {
        int barW = 200, barH = 12;
        int bx   = x + W / 2 - barW / 2;
        int by   = y - 22;
        g2.setColor(Color.DARK_GRAY);
        g2.fillRect(bx, by, barW, barH);
        g2.setColor(new Color(200, 40, 40));
        g2.fillRect(bx, by, (int)(barW * (float) health / maxHealth), barH);
        g2.setColor(Color.WHITE);
        g2.drawRect(bx, by, barW, barH);
    }

    private static BufferedImage safeGet(BufferedImage[] arr, int i) {
        return (arr == null || i < 0 || i >= arr.length) ? null : arr[i];
    }
    private static void playSound(String path) {
        try {
            javax.sound.sampled.AudioInputStream ais =
                javax.sound.sampled.AudioSystem.getAudioInputStream(new java.io.File(path));
            javax.sound.sampled.Clip clip = javax.sound.sampled.AudioSystem.getClip();
            clip.open(ais);
            clip.start();
        } catch (Exception ignored) {}
    }


}
