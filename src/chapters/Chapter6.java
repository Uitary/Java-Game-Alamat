import java.util.ArrayList;

/**
 * Chapter6 — "Isla ng Lihim"
 *
 * Level 1 : Bangis mob waves                  (Bg_Isla)
 * Level 2 : Bangis + Duwende mixed waves      (Bg_Isla1)
 * Level 3 : Tikbalang boss                    (Bg_Isla2)
 *
 * Bangis charge relentlessly in Level 1. Level 2 adds Duwende swarms
 * to the mix, creating simultaneous heavy-charger and fast-goblin pressure
 * that forces constant movement.
 *
 * Tikbalang is the chapter boss — a horse-headed giant that charges across
 * the screen and performs a ground-slam sending a floor shockwave.
 * The player must time jumps to avoid both the charge and the shockwave.
 */
public class Chapter6 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Isla ng Lihim";
    public static final String[] BG_FILES        = { NAME, "Bg_Isla", "Bg_Isla1", "Bg_Isla2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)  { return 2 + wave; }
    public static int duwendeCount(int wave) { return 3 + wave; }

    /** Legacy alias used by old queueWave path. */
    public static int enemyCount(int wave)   { return bangisCount(wave); }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 220; }

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

    /** Container for Level 2 mixed wave. */
    public static class MixedWave {
        public final ArrayList<Bangis>  bangis;
        public final ArrayList<Duwende> duwendes;
        public MixedWave(ArrayList<Bangis> b, ArrayList<Duwende> d) { bangis = b; duwendes = d; }
    }

    /**
     * Level 2 — Bangis + Duwende mixed waves.
     */
    public static MixedWave buildMixedWave(int wave, int screenW, int groundY, float speedMult) {
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

        return new MixedWave(bangis, duwendes);
    }

    /**
     * Legacy buildWave — used by old GamePanel queueWave for Chapter6.
     */
    public static ArrayList<Shokoy> buildWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Shokoy> queue = new ArrayList<>();
        int count  = enemyCount(wave);
        int spawnY = groundY - Shokoy.HEIGHT;
        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Shokoy(spawnX, spawnY));
        }
        return queue;
    }
}
