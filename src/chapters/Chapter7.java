import java.util.ArrayList;

/**
 * Chapter7 — "Tinatagong Kasukalan"
 *
 * Level 1 : Bangis + Duwende mixed waves               (Bg_Dilim)
 * Level 2 : Aswang_Mob + Bat mixed waves              (Bg_Dilim1)
 * Level 3 : Kapre boss                                 (Bg_Dilim2)
 *
 * Level 1 mixes ground chargers (Bangis) with swarming goblins (Duwende).
 * Level 2 escalates with flying demons — Aswang_Mob glide from above
 * while Bats dive-bomb, filling every vertical lane with threats.
 *
 * Kapre is the chapter boss — a massive cigar-smoking tree giant. It
 * stomps the ground to create screen-wide shockwaves and hurls boulders
 * in a slow arcing trajectory. Status effects (burn, confuse, slow) from
 * its cigarette attacks add layers of challenge.
 */
public class Chapter7 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Tinatagong Kasukalan";
    public static final String[] BG_FILES        = { NAME, "Bg_Dilim", "Bg_Dilim1", "Bg_Dilim2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)      { return 2 + wave; }
    public static int duwendeCount(int wave)     { return 4 + wave; }
    public static int manananggalCount(int wave) { return 1 + wave / 2; }
    public static int batCount(int wave)         { return 3 + wave; }

    /** Legacy alias. */
    public static int enemyCount(int wave)       { return duwendeCount(wave); }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 220; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /** Container for Level 1 mixed wave. */
    public static class MixedWave1 {
        public final ArrayList<Bangis>  bangis;
        public final ArrayList<Duwende> duwendes;
        public MixedWave1(ArrayList<Bangis> b, ArrayList<Duwende> d) { bangis = b; duwendes = d; }
    }

    /**
     * Level 1 — Bangis + Duwende mixed waves.
     */
    public static MixedWave1 buildMixedWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bangis>  bangis   = new ArrayList<>();
        ArrayList<Duwende> duwendes = new ArrayList<>();

        int bCount = bangisCount(wave);
        int gY     = groundY - Bangis.HEIGHT;
        for (int i = 0; i < bCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;     dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;              dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 50; dir = (i % 2 == 0) ? 1 : -1; }
            }
            bangis.add(new Bangis(spawnX, gY, speedMult, dir));
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

        return new MixedWave1(bangis, duwendes);
    }

    /** Container for Level 2 mixed wave. */
    public static class MixedWave2 {
        public final ArrayList<Aswang_Mob> mananangals;
        public final ArrayList<Bat>         bats;
        public MixedWave2(ArrayList<Aswang_Mob> m, ArrayList<Bat> b) { mananangals = m; bats = b; }
    }

    /**
     * Level 2 — Aswang_Mob + Bat mixed waves.
     */
    public static MixedWave2 buildMixedWaveLevel2(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Aswang_Mob> manas = new ArrayList<>();
        ArrayList<Bat>         bats  = new ArrayList<>();

        int mCount = manananggalCount(wave);
        int manaY  = groundY - 180;
        for (int i = 0; i < mCount; i++) {
            int spawnX = (i % 2 == 0) ? screenW + 60 + i * 80 : -60 - i * 80;
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            manas.add(new Aswang_Mob(spawnX, manaY));
        }

        int batCnt = batCount(wave);
        int batY   = -Bat.HEIGHT - 20;
        for (int i = 0; i < batCnt; i++) {
            int spawnX = (int)((screenW / (float)(batCnt + 1)) * (i + 1));
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            bats.add(new Bat(spawnX, batY, speedMult, dir));
        }

        return new MixedWave2(manas, bats);
    }

    /**
     * Legacy buildWave — returns Duwende list for old GamePanel code.
     */
    public static ArrayList<Duwende> buildWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Duwende> queue = new ArrayList<>();
        int count = enemyCount(wave);
        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1 -> { spawnX = screenW + 40 + i * 55;  dir = -1; }
                case 2 -> {
                    if (i % 2 == 0) { spawnX = screenW + 40 + (i / 2) * 55; dir = -1; }
                    else            { spawnX = -40 - (i / 2) * 55;           dir =  1; }
                }
                default -> {
                    int side  = (i % 2 == 0) ? 1 : -1;
                    int group = i / 2;
                    spawnX = screenW / 2 + side * (50 + group * 60);
                    dir    = -side;
                }
            }
            queue.add(new Duwende(spawnX, groundY, speedMult, dir));
        }
        return queue;
    }
}
