import java.util.ArrayList;

/**
 * Chapter5 — "Dagat ng San Juanita"
 *
 * Level 1 : Shokoy mob waves          (Bg_Dagat)
 * Level 2 : Sirena mob waves          (Bg_Dagat1)
 * Level 3 : Bakunawa boss             (Bg_Dagat2)
 *
 * Shokoy are pure-melee river/sea spirits that rush the player from both
 * sides. Level 2 switches to Sirena — aquatic songstresses that combine
 * a hypnotic slow debuff with melee tail slaps.
 *
 * Bakunawa is the chapter boss — a massive sea serpent that beams the
 * player, performs a screen-crossing slam, and rises out of the water
 * to bite.
 */
public class Chapter5 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Dagat ng San Juanita";
    public static final String[] BG_FILES        = { NAME, "Bg_Dagat", "Bg_Dagat1", "Bg_Dagat2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int shokoyCount(int wave)  { return 2 + wave; }
    public static int sirenaCount(int wave)  { return 2 + wave; }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 200; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /**
     * Level 1 — pure Shokoy waves.
     */
    public static ArrayList<Shokoy> buildWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Shokoy> queue = new ArrayList<>();
        int count  = shokoyCount(wave);
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

    /**
     * Level 2 — pure Sirena waves.
     */
    public static ArrayList<Sirena> buildWaveLevel2(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Sirena> queue = new ArrayList<>();
        int count  = sirenaCount(wave);
        int spawnY = groundY - Sirena.HEIGHT;

        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Sirena(spawnX, spawnY, speedMult, dir));
        }
        return queue;
    }

    /**
     * Legacy buildWave — returns Tiyanak list for old GamePanel compatibility.
     * GamePanel should call buildWaveLevel1 / buildWaveLevel2 explicitly.
     */
    public static ArrayList<Tiyanak> buildWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Tiyanak> queue = new ArrayList<>();
        int count = shokoyCount(wave);
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
