import java.awt.image.BufferedImage;
import java.io.File;
import javax.imageio.ImageIO;

public class AssetManager {

    // ── Backgrounds ───────────────────────────────────────────────────────────
    public BufferedImage bgL1, bgL2, bgL3;

    // ── Player sprites ────────────────────────────────────────────────────────
    public BufferedImage[] runFrames  = new BufferedImage[8]; // max 8; warrior uses [0..3]
    public int             runFrameCount = 4;                 // actual count for this class

    public BufferedImage idleFrame, jumpFrame, dashFrame, duckFrame, hitFrame;
    public BufferedImage[] deadFrames   = new BufferedImage[3];
    public BufferedImage   atkFrame;        // replaces swordAtkFrame — generic "skill 1" frame
    public BufferedImage   healAtkFrame;
    public BufferedImage[] bowAtkFrames = new BufferedImage[2]; // warrior only

    // ── Equipment / effect sprites (warrior only) ─────────────────────────────
    public BufferedImage swordHeldImg, swordSlashImg, bowImg, healImg;

    // ── Regular mob sprites ───────────────────────────────────────────────────
    // These are loaded once at startup. If a file is missing the field stays
    // null and GamePanel's drawMobLabel() uses a styled shape fallback instead.
    public BufferedImage bangisImg;
    public BufferedImage voodooImg;
    public BufferedImage batImg;
    public BufferedImage tiyanakImg;
    public BufferedImage shokoyImg;
    public BufferedImage duwendeImg;
    public BufferedImage kapreImg;
    public BufferedImage santelmoImg;
    public BufferedImage skeletonImg;
    public BufferedImage aswangMobImg;   // Aswang_Mob (non-boss variant in Ch9)

    // ── Boss sprites ──────────────────────────────────────────────────────────
    public BufferedImage aswangBossImg;
    public BufferedImage mangkukulamImg;
    public BufferedImage severinoImg;
    public BufferedImage sigbinImg;
    public BufferedImage bakunawaImg;
    public BufferedImage tikbalangImg;
    public BufferedImage kapreBossImg;
    public BufferedImage amomongImg;
    public BufferedImage sitanImg;

    // ── Entry point ───────────────────────────────────────────────────────────
    public void loadAssets(String charType, String[] chapterBgs) {
        bgL1 = img("assets/BG/" + chapterBgs[1] + ".png");
        bgL2 = img("assets/BG/" + chapterBgs[2] + ".png");
        bgL3 = img("assets/BG/" + chapterBgs[3] + ".png");

        loadEnemySprites();

        if (charType == null) return;
        switch (charType) {
            case "mage"    -> loadMage();
            case "fighter" -> loadFighter();
            default        -> loadWarrior();
        }
    }

    // ── Enemy sprite loader ───────────────────────────────────────────────────
    /** Loads one idle/representative frame for every enemy type.
     *  Paths follow the pattern  assets/enemies/<Name>/<Name>_Idle.png  for mobs
     *  and  assets/bosses/<Name>/<Name>_Idle.png  for bosses.
     *  If any file is absent the field remains null and a shape fallback is used. */
    private void loadEnemySprites() {
        // ── Regular mobs ──────────────────────────────────────────────────────
        bangisImg   = firstOf(
            "assets/enemies/Bangis/Bangis_Idle.png",
            "assets/Character/Bangis/Bangis_Idle_KF1.png",
            "assets/enemies/bangis.png");

        voodooImg   = firstOf(
            "assets/enemies/Voodoo/Voodoo_Idle.png",
            "assets/Character/Voodoo/Voodoo_Idle_KF1.png",
            "assets/enemies/voodoo.png");

        batImg      = firstOf(
            "assets/enemies/Bat/Bat_Idle.png",
            "assets/Character/Bat/Bat_Idle_KF1.png",
            "assets/enemies/bat.png");

        tiyanakImg  = firstOf(
            "assets/enemies/Tiyanak/Tiyanak_Idle.png",
            "assets/Character/Tiyanak/Tiyanak_Idle_KF1.png",
            "assets/enemies/tiyanak.png");

        shokoyImg   = firstOf(
            "assets/enemies/Shokoy/Shokoy_Idle.png",
            "assets/Character/Shokoy/Shokoy_Idle_KF1.png",
            "assets/enemies/shokoy.png");

        duwendeImg  = firstOf(
            "assets/enemies/Duwende/Duwende_Idle.png",
            "assets/Character/Duwende/Duwende_Idle_KF1.png",
            "assets/enemies/duwende.png");

        kapreImg    = firstOf(
            "assets/enemies/Kapre/Kapre_Idle.png",
            "assets/Character/Kapre/Kapre_Idle_KF1.png",
            "assets/enemies/kapre.png");

        santelmoImg = firstOf(
            "assets/enemies/Santelmo/Santelmo_Idle.png",
            "assets/Character/Santelmo/Santelmo_Idle_KF1.png",
            "assets/enemies/santelmo.png");

        skeletonImg = firstOf(
            "assets/enemies/Skeleton/Skeleton_Idle.png",
            "assets/Character/Skeleton/Skeleton_Idle_KF1.png",
            "assets/enemies/skeleton.png");

        aswangMobImg = firstOf(
            "assets/enemies/Aswang/Aswang_Idle.png",
            "assets/Character/Aswang/Aswang_Idle_KF1.png",
            "assets/enemies/aswang_mob.png");

        // ── Bosses ────────────────────────────────────────────────────────────
        aswangBossImg  = firstOf(
            "assets/bosses/Aswang/Aswang_Boss_Idle.png",
            "assets/Character/Aswang/Aswang_Boss_Idle_KF1.png",
            "assets/bosses/aswang.png");

        mangkukulamImg = firstOf(
            "assets/bosses/Mangkukulam/Mangkukulam_Idle.png",
            "assets/Character/Mangkukulam/Mangkukulam_Idle_KF1.png",
            "assets/bosses/mangkukulam.png");

        severinoImg    = firstOf(
            "assets/bosses/Severino/Severino_Idle.png",
            "assets/Character/Severino/Severino_Idle_KF1.png",
            "assets/bosses/severino.png");

        sigbinImg      = firstOf(
            "assets/bosses/Sigbin/Sigbin_Idle.png",
            "assets/Character/Sigbin/Sigbin_Idle_KF1.png",
            "assets/bosses/sigbin.png");

        bakunawaImg    = firstOf(
            "assets/bosses/Bakunawa/Bakunawa_Idle.png",
            "assets/Character/Bakunawa/Bakunawa_Idle_KF1.png",
            "assets/bosses/bakunawa.png");

        tikbalangImg   = firstOf(
            "assets/bosses/Tikbalang/Tikbalang_Idle.png",
            "assets/Character/Tikbalang/Tikbalang_Idle_KF1.png",
            "assets/bosses/tikbalang.png");

        kapreBossImg   = firstOf(
            "assets/bosses/Kapre/Kapre_Boss_Idle.png",
            "assets/Character/Kapre/Kapre_Boss_Idle_KF1.png",
            "assets/bosses/kapre_boss.png");

        amomongImg     = firstOf(
            "assets/bosses/Amomongo/Amomongo_Idle.png",
            "assets/Character/Amomongo/Amomongo_Idle_KF1.png",
            "assets/bosses/amomongo.png");

        sitanImg       = firstOf(
            "assets/bosses/Sitan/Sitan_Idle.png",
            "assets/Character/Sitan/Sitan_Idle_KF1.png",
            "assets/bosses/sitan.png");
    }

    // ── Player character loaders ──────────────────────────────────────────────
    private void loadWarrior() {
        runFrameCount = 4;
        for (int i = 0; i < 4; i++) runFrames[i] = img("assets/Character/Player/Run_KF" + (i+1) + ".png");
        idleFrame       = img("assets/Character/Player/Idle_KF1.png");
        jumpFrame       = img("assets/Character/Player/Jump_KF1.png");
        dashFrame       = img("assets/Character/Player/Dash_KF1.png");
        duckFrame       = img("assets/Character/Player/Duck_KF1.png");
        atkFrame        = img("assets/Character/Player/sword_atk_KF1.png");
        bowAtkFrames[0] = img("assets/Character/Player/bow_atk_KF1.png");
        bowAtkFrames[1] = img("assets/Character/Player/bow_atk_KF2.png");
        healAtkFrame    = img("assets/Character/Player/heal_KF1.png");
        hitFrame        = img("assets/Character/Player/Hit_KF1.png");
        for (int i = 0; i < 3; i++) deadFrames[i] = img("assets/player/Dead_KF" + (i+1) + ".png");
        swordHeldImg  = img("assets/cast/sword.png");
        swordSlashImg = img("assets/cast/slash.png");
        bowImg        = img("assets/cast/bow.png");
        healImg       = img("assets/cast/heal.png");
    }

    private void loadMage() {
        runFrameCount = 8;
        for (int i = 0; i < 8; i++) runFrames[i] = img("assets/Character/Mage/Mage_Run_KF" + (i+1) + ".png");
        idleFrame    = img("assets/Character/Mage/Mage_Idle_KF1.png");
        jumpFrame    = img("assets/Character/Mage/Mage_Jump_KF1.png");
        dashFrame    = img("assets/Character/Mage/Mage_Dash_KF1.png");
        duckFrame    = img("assets/Character/Mage/Mage_Duck_KF1.png");
        atkFrame     = img("assets/Character/Mage/Mage_Atk_KF1.png");
        healAtkFrame = img("assets/Character/Mage/Mage_Heal_KF1.png");
        hitFrame     = img("assets/Character/Mage/Mage_Hit_KF1.png");
        for (int i = 0; i < 3; i++) deadFrames[i] = img("assets/Character/Mage/Mage_Dead_KF" + (i+1) + ".png");
        healImg = img("assets/cast/heal.png");
    }

    private void loadFighter() {
        runFrameCount = 8;
        for (int i = 0; i < 8; i++) runFrames[i] = img("assets/Character/Fighter/Fighter_Run_KF" + (i+1) + ".png");
        idleFrame    = img("assets/Character/Fighter/Fighter_Idle_KF1.png");
        jumpFrame    = img("assets/Character/Fighter/Fighter_Jump_KF1.png");
        dashFrame    = img("assets/Character/Fighter/Fighter_Dash_KF1.png");
        duckFrame    = img("assets/Character/Fighter/Fighter_Duck_KF1.png");
        atkFrame     = img("assets/Character/Fighter/Fighter_Atk_KF1.png");
        healAtkFrame = img("assets/Character/Fighter/Fighter_Heal_KF1.png");
        hitFrame     = img("assets/Character/Fighter/Fighter_Hit_KF1.png");
        for (int i = 0; i < 3; i++) deadFrames[i] = img("assets/Character/Fighter/Fighter_Dead_KF" + (i+1) + ".png");
        healImg = img("assets/cast/heal.png");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────
    /** Try several paths in order; return the first that loads, or null. */
    private static BufferedImage firstOf(String... paths) {
        for (String p : paths) {
            BufferedImage bi = img(p);
            if (bi != null) return bi;
        }
        return null;
    }

    /** Read one image silently; returns null if file is missing. */
    public static BufferedImage img(String path) {
        try { return ImageIO.read(new File(path)); }
        catch (Exception e) { return null; }
    }
}
