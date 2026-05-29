import java.util.ArrayList;

/**
 * Chapter2 — "Lungsod ng Dilim"
 *
 * Level 1 : Bangis + Bat mixed waves             (Bg_Town)
 * Level 2 : Voodoo + Aswang_Mob mixed waves     (Bg_Town1)
 * Level 3 : Mangkukulam boss                     (Bg_Town2)
 *
 * Bangis charge from the sides while Bats dive from above in Level 1,
 * forcing the player to manage both ground and air threats.
 *
 * Voodoo are explosive walkers — they chase the player and detonate on
 * contact. Aswang_Mob are flying half-body demons that swoop and bite.
 * Their mix in Level 2 creates a chaotic threat from every angle.
 *
 * Mangkukulam is the chapter boss — a dark witch that hurls curse orbs
 * (DoT), flings grab-projectiles, and teleports to safety when cornered.
 */
public class Chapter2 {

    // ── Identity ──────────────────────────────────────────────────────────────
    public static final String   NAME            = "Lungsod ng Dilim";
    public static final String[] BG_FILES        = { NAME, "Bg_Town", "Bg_Town1", "Bg_Town2" };

    // ── Structure ─────────────────────────────────────────────────────────────
    public static final int      TOTAL_LEVELS    = 3;
    public static final int[]    WAVES_PER_LEVEL = { 3, 5, 0 };

    // ── Enemy scaling ─────────────────────────────────────────────────────────
    public static int bangisCount(int wave)       { return 2 + wave; }
    public static int batCount(int wave)          { return 2 + wave; }
    public static int voodooCount(int wave)       { return 2 + wave; }
    public static int manananggalCount(int wave)  { return 1 + wave / 2; }

    public static boolean isBossLevel(int level)  { return level == TOTAL_LEVELS; }
    public static int     bossSpawnX(int screenW) { return screenW - 200; }

    // ── Wave builders ─────────────────────────────────────────────────────────

    /** Simple container for Level 1 mixed wave. */
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

    /** Simple container for Level 2 mixed wave. */
    public static class MixedWave2 {
        public final ArrayList<Voodoo>       voodoos;
        public final ArrayList<Aswang_Mob>  mananangals;
        public MixedWave2(ArrayList<Voodoo> v, ArrayList<Aswang_Mob> m) { voodoos = v; mananangals = m; }
    }

    /**
     * Level 2 — Voodoo + Aswang_Mob mixed waves.
     */
    public static MixedWave2 buildMixedWaveLevel2(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Voodoo>      voodoos = new ArrayList<>();
        ArrayList<Aswang_Mob> manas   = new ArrayList<>();

        int vCount = voodooCount(wave);
        for (int i = 0; i < vCount; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1  -> { spawnX = screenW + 50 + i * 80;     dir = -1; }
                case 2  -> { spawnX = -50 - i * 80;              dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 50; dir = (i % 2 == 0) ? 1 : -1; }
            }
            voodoos.add(new Voodoo(spawnX, groundY, speedMult, dir));
        }

        int mCount = manananggalCount(wave);
        int manaY  = groundY - 180; // Aswang_Mob flies mid-air
        for (int i = 0; i < mCount; i++) {
            int spawnX = (i % 2 == 0) ? screenW + 60 + i * 80 : -60 - i * 80;
            int dir    = (spawnX > screenW / 2) ? -1 : 1;
            manas.add(new Aswang_Mob(spawnX, manaY));
        }

        return new MixedWave2(voodoos, manas);
    }

    /**
     * Backwards-compatible single-list entry — used when GamePanel calls
     * buildWave for Level 1 (returns Voodoo list for legacy compatibility).
     * Level 1 now uses buildMixedWaveLevel1 and Level 2 uses buildMixedWaveLevel2.
     */
    public static ArrayList<Voodoo> buildWave(int wave, int screenW, int groundY, float speedMult) {
        ArrayList<Voodoo> queue = new ArrayList<>();
        int count = voodooCount(wave);
        for (int i = 0; i < count; i++) {
            int spawnX, dir;
            switch (wave % 3) {
                case 1 -> { spawnX = screenW + 50 + i * 80; dir = -1; }
                case 2 -> { spawnX = -50 - i * 80;          dir =  1; }
                default -> { spawnX = screenW / 2 - 60 + i * 50; dir = (i % 2 == 0) ? 1 : -1; }
            }
            queue.add(new Voodoo(spawnX, groundY, speedMult, dir));
        }
        return queue;
    }
}
