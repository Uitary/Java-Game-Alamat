import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Iterator;
import javax.imageio.ImageIO;

public class Mage extends Player {

    public static final int BOLT_DAMAGE  = 40;
    public static final int BEAM_DAMAGE  = 35;
    public static final int BEAM_CD_TICKS = 75;

    private static final int BOLT_SPEED  = 14;
    private static final int BOLT_LIFE   = 45;
    private static final int BEAM_DURATION = 30;
    private static final int BEAM_H        = 60;

    public int beamLevel = 0, boltLevel = 0;
    public int getBoltDamage() { return BOLT_DAMAGE + boltLevel * 4; }
    public int getBeamDamage() { return BEAM_DAMAGE + beamLevel * 6; }

    public int     beamCooldown = 0;
    public boolean beamActive   = false;
    private int    beamTimer    = 0;

    public  boolean skill1Active = false;
    private int     skill1Timer  = 0;
    private static final int SKILL1_ANIM = 12;
    public  boolean skill2Active = false;
    private int     skill2Timer  = 0;
    private static final int SKILL2_ANIM = 20;

    public final ArrayList<MageBolt> bolts = new ArrayList<>();

    private static BufferedImage beamImg;
    private static boolean beamImgLoaded = false;

    public Mage() {
        runFrameCount = 8;
        loadBeamImg();
    }

    private static void loadBeamImg() {
        if (beamImgLoaded) return;
        beamImgLoaded = true;
        try { beamImg = ImageIO.read(new File("assets/Cast/Mage_Beam_Atk.png")); }
        catch (IOException ignored) {}
    }

    public void useSkill1() {
        int bx = facingLeft ? x - 10 : x + PS;
        int by = (duck && onGround) ? y + PS - DUCK_HEIGHT/2 : y + PS/2 - 8;
        bolts.add(new MageBolt(bx, by, facingLeft ? -BOLT_SPEED : BOLT_SPEED));
        skill1Active = true; skill1Timer = 0;
    }

    public boolean useSkill2() {
        if (beamCooldown > 0) return false;
        beamActive = true; beamTimer = 0; beamCooldown = BEAM_CD_TICKS;
        skill2Active = true; skill2Timer = 0;
        return true;
    }

    @Override
    public void updatePhysics(int groundY) {
        boolean tL = moveLeft, tR = moveRight;
        if (beamActive) { moveLeft = false; moveRight = false; isDashing = false; }
        super.updatePhysics(groundY);
        if (beamActive) { moveLeft = tL; moveRight = tR; }
    }

    public void updateSkills() {
        if (beamCooldown > 0) beamCooldown--;
        if (skill1Active && ++skill1Timer > SKILL1_ANIM) { skill1Active = false; skill1Timer = 0; }
        if (beamActive   && ++beamTimer   > BEAM_DURATION) { beamActive = false; beamTimer = 0; }
        if (skill2Active && ++skill2Timer > SKILL2_ANIM)  { skill2Active = false; skill2Timer = 0; }
        bolts.forEach(MageBolt::update);
        bolts.removeIf(b -> !b.active);
    }

    public void applySkillHits(ArrayList<Bangis> bangis, Aswang boss,
                                ArrayList<Aswang.AswangProjectile> ignored) {
        Iterator<MageBolt> it = bolts.iterator();
        while (it.hasNext()) {
            MageBolt b = it.next(); Rectangle bHit = b.getHitbox(); boolean hit = false;
            for (Bangis en : bangis)
                if (en.isAlive() && en.getHitbox().intersects(bHit)) { en.takeDamage(getBoltDamage()); hit = true; break; }
            if (!hit && boss != null && boss.isAlive() && boss.getHitbox().intersects(bHit)) { boss.takeDamage(getBoltDamage()); hit = true; }
            if (hit) { b.active = false; it.remove(); }
        }
        if (beamActive) {
            Rectangle bh = getBeamHitbox();
            for (Bangis en : bangis) if (en.isAlive() && en.getHitbox().intersects(bh)) en.takeDamage(getBeamDamage());
            if (boss != null && boss.isAlive() && boss.getHitbox().intersects(bh)) boss.takeDamage(getBeamDamage());
        }
    }

    public Rectangle getBeamHitbox() {
        int bY = (duck && onGround) ? y + PS - DUCK_HEIGHT/2 - BEAM_H/2 : y + PS/2 - BEAM_H/2;
        return facingLeft ? new Rectangle(0, bY, x, BEAM_H) : new Rectangle(x+PS, bY, 9999, BEAM_H);
    }

    @Override
    public void updateAnimation(Equipments.Sword sword, Equipments.Bow bow, Equipments.Potion pot) {
        if (deathAnimPlaying) { animState = AnimState.DEAD; return; }
        if (hitFlashTimer > 0) { animState = AnimState.HIT; return; }
        if      (skill2Active)        animState = AnimState.ATK2;
        else if (skill1Active)        animState = AnimState.ATK1;
        else if (pot.active)          animState = AnimState.HEAL_ATK;
        else if (duck && onGround)    animState = AnimState.DUCK;
        else if (isDashing)           animState = AnimState.DASH;
        else if (!onGround)           animState = AnimState.JUMP;
        else if (moveLeft||moveRight) animState = AnimState.RUN;
        else                          animState = AnimState.IDLE;
        tickRun();
    }

    @Override
    public void draw(Graphics2D g2, AssetManager assets,
                     Equipments.Sword sword, int selectedItem) {
        runFrameCount = assets.runFrameCount;
        BufferedImage frame = pickFrame(assets);

        if (isDashing && frame != null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.3f));
            drawSprite(g2, frame, facingLeft ? x+20 : x-20);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }
        if (frame == null) { g2.setColor(new Color(100,80,200)); g2.fillRect(x, y, PS, PS); }
        else drawSprite(g2, frame, x);

        if (hitFlashTimer > 0 && assets.hitFrame == null) {
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.45f));
            g2.setColor(Color.RED); g2.fillRect(x, y, PS, PS);
            g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));
        }

        bolts.forEach(b -> b.draw(g2));
        if (beamActive) drawBeam(g2);

        if (beamCooldown > 0) {
            float frac = 1f - beamCooldown / (float) BEAM_CD_TICKS;
            int barW = 60, barX = x + PS/2 - barW/2, barY = y - 14;
            g2.setColor(new Color(30,20,60,180)); g2.fillRoundRect(barX, barY, barW, 8, 4, 4);
            g2.setColor(new Color(120,60,255));   g2.fillRoundRect(barX, barY, (int)(barW*frac), 8, 4, 4);
            g2.setFont(new Font("SansSerif", Font.BOLD, 9)); g2.setColor(new Color(180,140,255));
            String cd = String.format("%.1fs", beamCooldown/60f);
            FontMetrics fm = g2.getFontMetrics();
            g2.drawString(cd, barX + barW/2 - fm.stringWidth(cd)/2, barY - 2);
        }
    }

    /** Draw beam using only the tiled PNG (no coloured-rectangle hitbox indicator). */
    private void drawBeam(Graphics2D g2) {
        Rectangle bh = getBeamHitbox();
        if (beamImg == null) return; // no fallback rectangle — silently skip if asset missing

        int imgW = beamImg.getWidth(), imgH = beamImg.getHeight();
        int drawH = BEAM_H;
        int drawW = (imgW == 0 || imgH == 0) ? drawH : (int)((double) imgW / imgH * drawH);
        if (drawW <= 0) drawW = drawH;

        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);

        // Soft glow behind sprite
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.35f));
        g2.setColor(new Color(120, 60, 255));
        g2.fillRect(bh.x, bh.y - 10, bh.width, drawH + 20);
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 1f));

        Shape oldClip = g2.getClip();
        g2.clipRect(bh.x, bh.y, bh.width, drawH);
        if (facingLeft) {
            for (int tx = bh.x + bh.width - drawW; tx > bh.x - drawW; tx -= drawW)
                g2.drawImage(beamImg, tx + drawW, bh.y, -drawW, drawH, null);
        } else {
            for (int tx = bh.x; tx < bh.x + bh.width; tx += drawW)
                g2.drawImage(beamImg, tx, bh.y, drawW, drawH, null);
        }
        g2.setClip(oldClip);
    }

    @Override
    protected BufferedImage pickFrame(AssetManager assets) {
        return switch (animState) {
            case RUN      -> assets.runFrames[runFrame];
            case JUMP     -> assets.jumpFrame;
            case DASH     -> first(assets.dashFrame,    assets.runFrames[0]);
            case DUCK     -> first(assets.duckFrame,    assets.idleFrame);
            case ATK1, ATK2 -> first(assets.atkFrame,  assets.idleFrame);
            case HEAL_ATK -> first(assets.healAtkFrame, assets.idleFrame);
            case HIT      -> first(assets.hitFrame,     assets.idleFrame);
            case DEAD     -> first(assets.deadFrames[deathFrame], assets.idleFrame);
            default       -> assets.idleFrame;
        };
    }

    //Inner: MageBolt
    public static class MageBolt {
        public int x, y;
        public boolean active = true;
        private final int dx;
        private int life = 0;

        public MageBolt(int x, int y, int dx) { this.x = x; this.y = y; this.dx = dx; }
        public void update() { x += dx; if (++life > BOLT_LIFE) active = false; }
        public void draw(Graphics2D g2) {
            g2.setColor(new Color(180,120,255,180)); g2.fillOval(x-8, y-8, 16, 16);
            g2.setColor(new Color(240,200,255,220)); g2.fillOval(x-4, y-4,  8,  8);
        }
        public Rectangle getHitbox() { return new Rectangle(x-8, y-8, 16, 16); }
    }
}
