import java.awt.*;
import java.awt.image.BufferedImage;

public class Player {

    //Size
    public static final int PS          = 100;
    public static final int DUCK_HEIGHT = 50;

    //Position & facing 
    public int     x = 100;
    public int     y;
    public boolean facingLeft = false;

    //Health
    public int health = 100;

    //Input flags
    public boolean moveLeft, moveRight, jumping, duck;

    //Physics 
    public  boolean onGround       = true;
    public  boolean jumpHeld       = false;
    private int     jumpHoldFrames = 0;
    private double  velocityY      = 0;
    /** Driven by GamePanel each frame; 1.0 = normal, < 1.0 = slowed (e.g. Kapre stomp). */
    public  float   moveSpeedMult  = 1.0f;
    private static final double GRAVITY       = 0.67;
    private static final double JUMP_IMPULSE  = -13;
    private static final int    MAX_JUMP_HOLD = 10;
    protected static final int  MOVE_SPEED    = 5;

    //Dash
    public  boolean isDashing    = false;
    public  int     dashTimer    = 0;
    public  int     dashCooldown = 0;
    public  static final int DASH_COOLDOWN = 60;
    private static final int DASH_DURATION = 10;
    protected static final int DASH_SPEED  = 18;

    // ── Immunity skill (slot 3) ───────────────────────────────────────────────
    /** True while the 5-second immunity window is active. */
    public  boolean immunityActive   = false;
    /** Counts down from IMMUNITY_DURATION to 0. */
    public  int     immunityTimer    = 0;
    /** Counts down from IMMUNITY_COOLDOWN to 0 after immunity expires. */
    public  int     immunityCooldown = 0;
    public  static final int IMMUNITY_DURATION = 300; // 5 s × 60 fps
    public  static final int IMMUNITY_COOLDOWN = 900; // 15 s × 60 fps

    //Animation
    public enum AnimState { IDLE, RUN, JUMP, DASH, DUCK, ATK1, ATK2, HEAL_ATK, HIT, DEAD }
    public AnimState animState = AnimState.IDLE;

    protected int runFrame   = 0;
    protected int runCounter = 0;
    protected int runFrameCount = 4; // overridden by subclasses / set from assets

    //Hit flash & death
    public int     hitFlashTimer    = 0;
    public boolean deathAnimPlaying = false;
    public int     deathFrame       = 0;
    public int     deathFrameTimer  = 0;

    //Physics
    public void updatePhysics(int groundY) {
        if (dashCooldown > 0) dashCooldown--;
        // Tick immunity timers
        if (immunityActive) {
            if (--immunityTimer <= 0) {
                immunityTimer    = 0;
                immunityActive   = false;
                immunityCooldown = IMMUNITY_COOLDOWN;
            }
        } else if (immunityCooldown > 0) {
            immunityCooldown--;
        }
        if (isDashing) {
            x += facingLeft ? -DASH_SPEED : DASH_SPEED;
            if (++dashTimer >= DASH_DURATION) { isDashing = false; dashTimer = 0; }
            return;
        }
        if (!duck) {
            if (moveLeft)  { x -= (int)(MOVE_SPEED * moveSpeedMult); facingLeft = true;  }
            if (moveRight) { x += (int)(MOVE_SPEED * moveSpeedMult); facingLeft = false; }
        }
        if (jumping && onGround) {
            velocityY = JUMP_IMPULSE; onGround = false; jumpHeld = true; jumpHoldFrames = 0;
        }
        if (jumpHeld && jumping && !onGround && jumpHoldFrames < MAX_JUMP_HOLD) {
            velocityY -= 0.5; jumpHoldFrames++;
        }
        if (!jumping) jumpHeld = false;
        velocityY += GRAVITY;
        y += (int) velocityY;
        if (y + PS >= groundY) { y = groundY - PS; velocityY = 0; onGround = true; }
    }

    //Animation state machine 
    /** Override in subclasses that have different skill states. */
    public void updateAnimation(Equipments.Sword sword, Equipments.Bow bow, Equipments.Potion pot) {
        if (deathAnimPlaying)  { animState = AnimState.DEAD; return; }
        if (hitFlashTimer > 0) { animState = AnimState.HIT;  return; }

        if      (sword.active)        animState = AnimState.ATK1;
        else if (bow.active)          animState = AnimState.ATK2;
        else if (pot.active)          animState = AnimState.HEAL_ATK;
        else if (duck && onGround)    animState = AnimState.DUCK;
        else if (isDashing)           animState = AnimState.DASH;
        else if (!onGround)           animState = AnimState.JUMP;
        else if (moveLeft||moveRight) animState = AnimState.RUN;
        else                          animState = AnimState.IDLE;

        tickRun();
    }

    protected void tickRun() {
        if (animState == AnimState.RUN) {
            if (++runCounter >= 5) { runFrame = (runFrame+1) % runFrameCount; runCounter = 0; }
        } else { runFrame = 0; runCounter = 0; }
    }

    //Hitbox 
    public Rectangle getHitbox() {
        if (duck && onGround) return new Rectangle(x, y + PS - DUCK_HEIGHT, PS, DUCK_HEIGHT);
        return new Rectangle(x, y, PS, PS);
    }

    /** Launch the player upward (used by Tikbalang charge knockup). */
    public void applyKnockup() {
        velocityY = -18;
        onGround  = false;
        jumpHeld  = false;
    }

    //Drawing
    public void draw(Graphics2D g2, AssetManager assets,
                     Equipments.Sword sword, int selectedItem) {
        runFrameCount = assets.runFrameCount;
        BufferedImage frame = pickFrame(assets);

        if (isDashing && frame != null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            drawSprite(g2, frame, facingLeft ? x+20 : x-20);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
        if (frame == null) { g2.setColor(Color.WHITE); g2.fillRect(x, y, PS, PS); }
        else                drawSprite(g2, frame, x);

        if (hitFlashTimer > 0 && assets.hitFrame == null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g2.setColor(Color.RED); g2.fillRect(x, y, PS, PS);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        // Warrior-specific held weapons
        boolean attacking = (animState==AnimState.ATK1||animState==AnimState.ATK2||animState==AnimState.HEAL_ATK);
        if (!attacking) {
            BufferedImage wImg = null;
            if      (selectedItem==1 && !sword.active) wImg = assets.swordHeldImg;
            else if (selectedItem==2)                   wImg = assets.bowImg;
            if (wImg != null) {
                int ox = facingLeft ? 0 : PS-40;
                int oy = (duck && onGround) ? PS-32 : PS/2-32;
                if (facingLeft) g2.drawImage(wImg, x+ox+40, y+oy, -40, 40, null);
                else            g2.drawImage(wImg, x+ox,    y+oy,  40, 40, null);
            }
        }
    }

    protected BufferedImage pickFrame(AssetManager assets) {
        return switch (animState) {
            case RUN      -> assets.runFrames[runFrame];
            case JUMP     -> assets.jumpFrame;
            case DASH     -> first(assets.dashFrame,    assets.runFrames[0]);
            case DUCK     -> first(assets.duckFrame,    assets.idleFrame);
            case ATK1     -> first(assets.atkFrame,     assets.runFrames[0]);
            case ATK2     -> first(assets.bowAtkFrames[0], assets.runFrames[0]);
            case HEAL_ATK -> first(assets.healAtkFrame, assets.idleFrame);
            case HIT      -> first(assets.hitFrame,     assets.idleFrame);
            case DEAD     -> first(assets.deadFrames[deathFrame], assets.idleFrame);
            default       -> assets.idleFrame;
        };
    }

    protected static BufferedImage first(BufferedImage a, BufferedImage b) { return a != null ? a : b; }

    protected void drawSprite(Graphics2D g2, BufferedImage img, int drawX) {
        if (facingLeft) g2.drawImage(img, drawX+PS, y, -PS, PS, null);
        else            g2.drawImage(img, drawX,    y,  PS, PS, null);
    }
}
