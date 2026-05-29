import java.util.ArrayList;

/**
 * Chapter3 — "Sigaw ni Severino"
 *
 * Level 1 : Bangis + Bat mixed waves         (Bg_Severino)
 * Level 2 : Skeleton waves                   (Bg_Severino1)
 * Level 3 : Severino boss                    (Bg_Severino2)
 *
 * Bangis charge while Bats dive in Level 1 — a familiar opener that
 * escalates into undead territory. Level 2 introduces Skeletons, which
 * can both melee-combo and throw bone projectiles, forcing the player
 * to stay mobile at range.
 *
 * Severino is the chapter boss — a cursed warrior ghost that dashes
 * across the screen and flings claw projectiles in wide arcs.
 */
public class Chapter3 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Sigaw ni Severino";
    public static final String[] BG_FILES        = { NAME, "Bg_Severino", "Bg_Severino1", "Bg_Severino2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)   { return 2 + wave; }
    public static int batCount(int wave)      { return 2 + wave; }
    public static int skeletonCount(int wave) { return 2 + wave; }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 200; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /** Container for Level 1 mixed wave. */
    public static class MixedWave1 {
        public final ArrayList<Bangis> bangis;
        public final ArrayList<Bat>    bats;
        public MixedWave1(ArrayList<Bangis> b, ArrayList<Bat> bts) { bangis = b; bats = bts; }
    }

    /**
     * Level 1 — Bangis + Bat mixed waves.
     */
    public static MixedWave1 buildMixedWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bangis> bangis = new ArrayList<>();
        ArrayList<Bat>    bats   = new ArrayList<>();

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

        int batCnt = batCount(wave);
        int batY   = -Bat.HEIGHT - 20;
        for (int i = 0; i < batCnt; i++) {
            int spawnX = (int)((screenW / (float)(batCnt + 1)) * (i + 1));
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            bats.add(new Bat(spawnX, batY, speedMult, dir));
        }

        return new MixedWave1(bangis, bats);
    }

    /**
     * Level 2 — pure Skeleton waves.
     * Skeletons are ground-bound; spawn at ground level.
     */
    public static ArrayList<Skeleton> buildWaveLevel2(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Skeleton> queue = new ArrayList<>();
        int count  = skeletonCount(wave);
        int spawnY = groundY - Skeleton.HEIGHT;

        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Skeleton(spawnX, spawnY, speedMult, dir));
        }
        return queue;
    }

    /**
     * Legacy buildWave — returns Bat list (used by old GamePanel code).
     * New code should call buildMixedWaveLevel1 / buildWaveLevel2 explicitly.
     */
    public static ArrayList<Bat> buildWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bat> queue = new ArrayList<>();
        int count  = batCount(wave);
        int spawnY = groundY - 200;
        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1 -> { spawnX = screenW + 50 + i * 80; dir = -1; }
                case 2 -> { spawnX = -50 - i * 80;          dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 50; dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Bat(spawnX, spawnY, speedMult, dir));
        }
        return queue;
    }
}
