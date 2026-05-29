import java.util.ArrayList;

/**
 * Chapter1 — "Kagubatan ng Salinlahi"
 *
 * Level 1 : Bangis mob waves            (Bg_Forest)
 * Level 2 : Duwende + Bat mixed waves   (Bg_Forest1)
 * Level 3 : Aswang boss                 (Bg_Forest2)
 *
 * Bangis are aggressive ground beasts that charge at full speed once the
 * player enters their aggro range.
 *
 * Duwende are tiny cave goblins that overwhelm the player in numbers.
 *
 * Bats are airborne dive-bombers — they orbit above and swoop down.
 *
 * Aswang is the chapter boss — a shapeshifting creature that alternates
 * between melee slashes and dark-energy projectile bursts.
 */
public class Chapter1 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Kagubatan ng Salinlahi";
    public static final String[] BG_FILES        = { NAME, "Bg_Forest", "Bg_Forest1", "Bg_Forest2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 }; // level 3 = boss, no normal waves

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)  { return 2 + wave; }
    public static int duwendeCount(int wave) { return 3 + wave; }
    public static int batCount(int wave)     { return 2 + wave; }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 200; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /**
     * Level 1 — pure Bangis waves.
     */
    public static ArrayList<Bangis> buildWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bangis> queue = new ArrayList<>();
        int count = bangisCount(wave);
        int gY    = groundY - Bangis.HEIGHT;

        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 50;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Bangis(spawnX, gY, speedMult, dir));
        }
        return queue;
    }

    /** Simple container returned by {@link #buildMixedWave}. */
    public static class MixedWave {
        public final ArrayList<Duwende> duwendes;
        public final ArrayList<Bat>     bats;
        public MixedWave(ArrayList<Duwende> d, ArrayList<Bat> b) { duwendes = d; bats = b; }
    }

    /**
     * Level 2 — Duwende + Bat mixed waves.
     */
    public static MixedWave buildMixedWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Duwende> duwendes = new ArrayList<>();
        ArrayList<Bat>     bats     = new ArrayList<>();

        // Duwende
        int dCount = duwendeCount(wave);
        for (int i = 0; i < dCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 40 + i * 55;  dir = -1; }
                case 2  -> { spawnX = -40 - i * 55;           dir =  1; }
                default -> {
                    int side = (i % 2 == 0) ? 1 : -1;
                    spawnX = screenW / 2 + side * (50 + (i / 2) * 60);
                    dir    = -side;
                }
            }
            duwendes.add(new Duwende(spawnX, groundY, speedMult, dir));
        }

        // Bats — spawn above screen
        int bCount  = batCount(wave);
        int batY    = -Bat.HEIGHT - 20;
        for (int i = 0; i < bCount; i++) {
            int spawnX = (int)((screenW / (float)(bCount + 1)) * (i + 1));
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            bats.add(new Bat(spawnX, batY, speedMult, dir));
        }

        return new MixedWave(duwendes, bats);
    }

    /**
     * Backwards-compatible single-wave entry called by GamePanel for level 1.
     * Level 2 uses buildMixedWave.
     */
    public static ArrayList<Bangis> buildWave(int wave, int screenW, int groundY, float speedMult) {
        return buildWaveLevel1(wave, screenW, groundY, speedMult);
    }
}
