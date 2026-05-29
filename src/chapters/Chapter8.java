import java.util.ArrayList;

/**
 * Chapter8 — "Mahiwagang Kweba"
 *
 * Level 1 : Bangis + Bat mixed waves               (Bg_Kweba)
 * Level 2 : Skeleton + Duwende mixed waves         (Bg_Kweba1)
 * Level 3 : Amomongo boss                          (Bg_Kweba2)
 *
 * Level 1 reprises the Bangis/Bat combo at higher difficulty.
 * Level 2 introduces Skeletons (ranged + melee) alongside swarming
 * Duwende — the ranged bone throws keep the player from camping corners
 * while Duwende close in from multiple directions.
 *
 * Amomongo is the chapter boss — a massive ape-like cryptid. It alternates
 * between a ground pound that sends a floor shockwave and a leaping pounce
 * that targets the player's current position. At half HP it enrages,
 * increasing move speed and adding a claw swipe combo after every pounce.
 */
public class Chapter8 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Mahiwagang Kweba";
    public static final String[] BG_FILES        = { NAME, "Bg_Kweba", "Bg_Kweba1", "Bg_Kweba2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)   { return 2 + wave; }
    public static int batCount(int wave)      { return 3 + wave; }
    public static int skeletonCount(int wave) { return 2 + wave; }
    public static int duwendeCount(int wave)  { return 3 + wave; }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 220; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /** Container for Level 1 mixed wave. */
    public static class MixedWave {
        public final ArrayList<Bat>    bats;
        public final ArrayList<Bangis> bangis;
        MixedWave(ArrayList<Bat> b, ArrayList<Bangis> g) { bats = b; bangis = g; }
    }

    /**
     * Level 1 — Bangis + Bat mixed waves.
     * Bats dive from above while Bangis charge from the sides.
     */
    public static MixedWave buildWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bat>    bats   = new ArrayList<>();
        ArrayList<Bangis> bangis = new ArrayList<>();

        int batCnt  = batCount(wave);
        int batY    = -Bat.HEIGHT - 20;
        for (int i = 0; i < batCnt; i++) {
            int spawnX = (int)((screenW / (float)(batCnt + 1)) * (i + 1));
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            bats.add(new Bat(spawnX, batY, speedMult, dir));
        }

        int bCount = bangisCount(wave);
        int gY     = groundY - Bangis.HEIGHT;
        for (int i = 0; i < bCount; i++) {
            int spawnX = screenW + 60 + i * 90;
            bangis.add(new Bangis(spawnX, gY, speedMult, -1));
        }

        return new MixedWave(bats, bangis);
    }

    /** Container for Level 2 mixed wave. */
    public static class MixedWave2 {
        public final ArrayList<Skeleton> skeletons;
        public final ArrayList<Duwende>  duwendes;
        public MixedWave2(ArrayList<Skeleton> s, ArrayList<Duwende> d) { skeletons = s; duwendes = d; }
    }

    /**
     * Level 2 — Skeleton + Duwende mixed waves.
     */
    public static MixedWave2 buildMixedWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Skeleton> skeletons = new ArrayList<>();
        ArrayList<Duwende>  duwendes  = new ArrayList<>();

        int sCount  = skeletonCount(wave);
        int skelY   = groundY - Skeleton.HEIGHT;
        for (int i = 0; i < sCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            skeletons.add(new Skeleton(spawnX, skelY, speedMult, dir));
        }

        int dCount = duwendeCount(wave);
        for (int i = 0; i < dCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1 -> { spawnX = screenW + 40 + i * 55;  dir = -1; }
                case 2 -> { spawnX = -40 - i * 55;           dir =  1; }
                default -> {
                    int side = (i % 2 == 0) ? 1 : -1;
                    spawnX = screenW / 2 + side * (50 + (i / 2) * 60);
                    dir    = -side;
                }
            }
            duwendes.add(new Duwende(spawnX, groundY, speedMult, dir));
        }

        return new MixedWave2(skeletons, duwendes);
    }
}
