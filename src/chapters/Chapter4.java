import java.util.ArrayList;

/**
 * Chapter4 — "Sa may Lungsod ng Caramoan"
 *
 * Level 1 : Bangis + Tiyanak mixed waves           (Bg_Caramoan)
 * Level 2 : Duwende + Aswang_Mob mixed waves      (Bg_Caramoan1)
 * Level 3 : Sigbin boss                            (Bg_Caramoan2)
 *
 * Bangis charge while Tiyanak ambush from close range in Level 1.
 * Level 2 layers Duwende swarms with flying Aswang_Mob to create
 * simultaneous ground and air pressure.
 *
 * Sigbin is the chapter boss — a foul creature that dashes at the player
 * and fires straight bone-spike projectiles.
 */
public class Chapter4 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Sa may Lungsod ng Caramoan";
    public static final String[] BG_FILES        = { NAME, "Bg_Caramoan", "Bg_Caramoan1", "Bg_Caramoan2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)      { return 2 + wave; }
    public static int tiyanakCount(int wave)     { return 2 + wave; }
    public static int duwendeCount(int wave)     { return 3 + wave; }
    public static int manananggalCount(int wave) { return 1 + wave / 2; }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 200; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /** Container for Level 1 mixed wave. */
    public static class MixedWave1 {
        public final ArrayList<Bangis>  bangis;
        public final ArrayList<Tiyanak> tiyanaks;
        public MixedWave1(ArrayList<Bangis> b, ArrayList<Tiyanak> t) { bangis = b; tiyanaks = t; }
    }

    /**
     * Level 1 — Bangis + Tiyanak mixed waves.
     */
    public static MixedWave1 buildMixedWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bangis>  bangis   = new ArrayList<>();
        ArrayList<Tiyanak> tiyanaks = new ArrayList<>();

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

        int tCount = tiyanakCount(wave);
        for (int i = 0; i < tCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 60 + i * 80;     dir = -1; }
                case 2  -> { spawnX = -60 - i * 80;              dir =  1; }
                default -> { spawnX = screenW / 2 + (i % 2 == 0 ? 80 : -80) + i * 30; dir = (i % 2 == 0) ? -1 : 1; }
            }
            tiyanaks.add(new Tiyanak(spawnX, groundY, speedMult, dir));
        }

        return new MixedWave1(bangis, tiyanaks);
    }

    /** Container for Level 2 mixed wave. */
    public static class MixedWave2 {
        public final ArrayList<Duwende>     duwendes;
        public final ArrayList<Aswang_Mob> mananangals;
        public MixedWave2(ArrayList<Duwende> d, ArrayList<Aswang_Mob> m) { duwendes = d; mananangals = m; }
    }

    /**
     * Level 2 — Duwende + Aswang_Mob mixed waves.
     */
    public static MixedWave2 buildMixedWaveLevel2(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Duwende>     duwendes = new ArrayList<>();
        ArrayList<Aswang_Mob> manas    = new ArrayList<>();

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

        int mCount = manananggalCount(wave);
        int manaY  = groundY - 180;
        for (int i = 0; i < mCount; i++) {
            int spawnX = (i % 2 == 0) ? screenW + 60 + i * 80 : -60 - i * 80;
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            manas.add(new Aswang_Mob(spawnX, manaY));
        }

        return new MixedWave2(duwendes, manas);
    }

    /**
     * Legacy buildWave — returns Tiyanak list for old GamePanel compatibility.
     */
    public static ArrayList<Tiyanak> buildWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Tiyanak> queue = new ArrayList<>();
        int count = tiyanakCount(wave);
        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 50; dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Tiyanak(spawnX, groundY, speedMult, dir));
        }
        return queue;
    }
}
