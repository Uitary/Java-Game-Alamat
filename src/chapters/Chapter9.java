import java.util.ArrayList;

/**
 * Chapter9 — "Kasanaan"
 *
 * Level 1 : Bangis + Santelmo mixed waves                       (Bg_Kasanaan)
 * Level 2 : Skeleton + Bat + Aswang (mob) mixed waves           (Bg_Kasanaan1)
 * Level 3 : Sitan boss (supreme demon lord, final boss)         (Bg_Kasanaan2)
 *
 * This is the final chapter. Enemy variety peaks here — every enemy type
 * the player has encountered returns in service of Sitan.
 *
 * Level 1 mixes ground-rushing Bangis with floating fire-spirit Santelmo
 * that bombard from range.
 *
 * Level 2 escalates dramatically: Skeletons throw bones from mid-range,
 * Bats dive-bomb from above, and Aswang appear as regular mob enemies
 * (weaker version of the Chapter 1 boss) to haunt the player before the
 * final confrontation.
 *
 * Sitan is the supreme demon lord and final boss. He walks toward the
 * player and alternates melee swings/slams with a deadly arsenal of spells:
 * homing violet orbs, ground fire patches, a Bangis summon, a blink
 * teleport, and a sustained purple beam — demanding constant movement.
 *
 * ── New Game+ loop ─────────────────────────────────────────────────────────
 * When Sitan is defeated, GamePanel resets chapter/level/wave to 0/1/1 and
 * restarts from Chapter 1, allowing the cycle to repeat indefinitely.
 */
public class Chapter9 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Kasanaan";
    public static final String[] BG_FILES        = { NAME, "Bg_Kasanaan", "Bg_Kasanaan1", "Bg_Kasanaan2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)   { return 2 + wave; }
    public static int santelmoCount(int wave) { return 2 + wave; }
    public static int skeletonCount(int wave) { return 2 + wave; }
    public static int batCount(int wave)      { return 2 + wave; }
    /** Aswang mob (non-boss): one per wave from wave 2, capped at 3. */
    public static int aswangMobCount(int wave){ return Math.min(Math.max(0, wave - 1), 3); }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 220; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /** Container for Level 1 mixed wave. */
    public static class MixedWave1 {
        public final ArrayList<Bangis>   bangis;
        public final ArrayList<Santelmo> santelmos;
        public MixedWave1(ArrayList<Bangis> b, ArrayList<Santelmo> s) { bangis = b; santelmos = s; }
    }

    /**
     * Level 1 — Bangis + Santelmo mixed waves.
     *
     * Bangis rush from the sides; Santelmo float mid-air and fire bursts.
     */
    public static MixedWave1 buildWaveLevel1(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Bangis>   bangis    = new ArrayList<>();
        ArrayList<Santelmo> santelmos = new ArrayList<>();

        // Bangis
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

        // Santelmo
        int sCount  = santelmoCount(wave);
        int spawnY  = groundY - 200;
        for (int i = 0; i < sCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            santelmos.add(new Santelmo(spawnX, spawnY, speedMult, dir));
        }

        return new MixedWave1(bangis, santelmos);
    }

    /** Container for Level 2 mixed wave. */
    public static class MixedWave2 {
        public final ArrayList<Skeleton> skeletons;
        public final ArrayList<Bat>      bats;
        public final ArrayList<Aswang>   aswangMobs; // non-boss Aswang
        public MixedWave2(ArrayList<Skeleton> s, ArrayList<Bat> b, ArrayList<Aswang> a) {
            skeletons = s; bats = b; aswangMobs = a;
        }
    }

    /**
     * Level 2 — Skeleton + Bat + Aswang (mob) mixed waves.
     *
     * Skeleton fire bones from mid-range, Bats dive from above, and mob
     * Aswang stalk the ground as a tougher melee threat. Together these
     * create the most demanding mob wave before the final boss.
     */
    public static MixedWave2 buildMixedWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Skeleton> skeletons  = new ArrayList<>();
        ArrayList<Bat>      bats       = new ArrayList<>();
        ArrayList<Aswang>   aswangMobs = new ArrayList<>();

        // Skeletons
        int skelCount = skeletonCount(wave);
        int skelY     = groundY - Skeleton.HEIGHT;
        for (int i = 0; i < skelCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            skeletons.add(new Skeleton(spawnX, skelY, speedMult, dir));
        }

        // Bats
        int batCnt = batCount(wave);
        int batY   = -Bat.HEIGHT - 20;
        for (int i = 0; i < batCnt; i++) {
            int spawnX = (int)((screenW / (float)(batCnt + 1)) * (i + 1));
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            bats.add(new Bat(spawnX, batY, speedMult, dir));
        }

        // Aswang mobs (non-boss) — appear only from wave 2 onward
        int aCount = aswangMobCount(wave);
        int aswY   = groundY - Aswang.HEIGHT;
        for (int i = 0; i < aCount; i++) {
            int spawnX = (i % 2 == 0) ? screenW + 80 + i * 100 : -80 - i * 100;
            aswangMobs.add(new Aswang(spawnX, aswY));
        }

        return new MixedWave2(skeletons, bats, aswangMobs);
    }

    // ── Legacy wrapper (kept for old single-level queueWave path) ────────────
    /**
     * @deprecated Use {@link #buildWaveLevel1} or {@link #buildMixedWave} explicitly.
     */
    @Deprecated
    public static ArrayList<Santelmo> buildWaveLevel1_legacy(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Santelmo> queue  = new ArrayList<>();
        int count  = santelmoCount(wave);
        int spawnY = groundY - 200;
        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;      dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;               dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 55;  dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Santelmo(spawnX, spawnY, speedMult, dir));
        }
        return queue;
    }
}
