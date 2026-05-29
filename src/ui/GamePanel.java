import javax.swing.JPanel;
import java.awt.*;
import java.awt.event.*;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.Iterator;

public class GamePanel extends JPanel implements Runnable, KeyListener, MouseListener {

    private final GameSettings settings;
    private final PlayerProfile playerData;
    private final AssetManager assets = new AssetManager();
    private Player player;        // non-final — dev mode can switch class
    private String charType;      // non-final — updated on character switch

    private final Equipments.Sword sword = new Equipments.Sword();
    private final Equipments.Bow bow = new Equipments.Bow();
    private final Equipments.Potion potion = new Equipments.Potion();
    private final Equipments.MaxHP maxhp = new Equipments.MaxHP(); // replaces Defense
    private final ArrayList<Equipments.Arrow> arrows = new ArrayList<>();

    private int mageBeamLevel = 0, mageBoltLevel = 0;
    private int fighterPunchLevel = 0, fighterPowerPunchLevel = 0;

    private final Shop shop = new Shop();
    private final UIManager ui = new UIManager();

    // ── Hotbar / shop slot icons (loaded from assets/Icons/) ─────────────────
    private java.awt.image.BufferedImage iconSword, iconBow, iconBolt, iconBeam;
    private java.awt.image.BufferedImage iconPunch, iconPower, iconShield, iconPotion;

    private static final String[][] CHAPTER_BG = {
            Chapter1.BG_FILES, Chapter2.BG_FILES, Chapter3.BG_FILES, Chapter4.BG_FILES, Chapter5.BG_FILES,
            Chapter6.BG_FILES, Chapter7.BG_FILES, Chapter8.BG_FILES, Chapter9.BG_FILES
    };
    private static final int[][] WAVES_PER_LEVEL = {
            Chapter1.WAVES_PER_LEVEL, Chapter2.WAVES_PER_LEVEL, Chapter3.WAVES_PER_LEVEL, Chapter4.WAVES_PER_LEVEL,
            Chapter5.WAVES_PER_LEVEL,
            Chapter6.WAVES_PER_LEVEL, Chapter7.WAVES_PER_LEVEL, Chapter8.WAVES_PER_LEVEL, Chapter9.WAVES_PER_LEVEL
    };

    private int chapter = 0, level = 1, wave = 1;

    private final int enemyDmgMult;
    private final float enemySpeedMult;
    /** Multiplier applied to ALL player-dealt damage (weapons, skills). */
    private final float playerDmgMult;

    static final int BASE_W = 989;
    static final int BASE_H = 607;

    private static final float GROUND_FRAC = 0.82f;

    private int groundY() {
        return (int) (BASE_H * GROUND_FRAC);
    }

    private int sessionCoins = 0, sessionScore = 0, enemiesDefeated = 0;
    private int selectedItem = 1;

    private static final java.util.Random LOOT_RNG = new java.util.Random();
    private final BufferedImage lootCoinImg = loadLootImg("assets/drop/coins.png");
    private final BufferedImage lootPotionImg = loadLootImg("assets/drop/potion.png");
    private final BufferedImage lootHealthImg = loadLootImg("assets/drop/health.png");

    private static BufferedImage loadLootImg(String path) {
        try {
            return javax.imageio.ImageIO.read(new java.io.File(path));
        } catch (Exception e) {
            return null;
        }
    }

    private static class LootDrop {
        enum Type {
            COINS, POTION, HEALTH
        }

        int x;
        float currentY; // actual rendered Y position (falls to groundY)
        int groundY; // Y coordinate of the floor where item rests
        Type type;
        int coinValue;
        int labelTimer;
        // ── Physics ───────────────────────────────────────────────────────────
        float velX; // horizontal scatter velocity
        float velY; // vertical velocity (positive = downward)
        int bounceCount; // how many times it has hit the ground
        boolean landed; // true once item has fully settled
        int animTick; // ticks since spawn (drives glow, bob, pop-in)
        static final int PICKUP_RADIUS = 28;
        static final int LABEL_TICKS = 90;
        static final int MAX_BOUNCES = 3;
        static final int ITEM_HALF = 21; // SIZE/2 — bottom edge aligns with floor
        static final float GRAVITY = 0.55f;
        static final float BOUNCE_DAMP = 0.42f;
        static final float STOP_VEL = 1.2f;
        static final float HORIZ_FRICTION = 0.82f;

        LootDrop(int spawnX, int spawnY, Type t, int coinValue, int groundY) {
            this.x = spawnX;
            this.currentY = spawnY;
            this.groundY = groundY;
            this.type = t;
            this.coinValue = coinValue;
            this.labelTimer = LABEL_TICKS;
            this.velY = -(2.5f + (float) Math.random() * 3.5f);
            this.velX = (float) (Math.random() * 5.0 - 2.5);
            this.bounceCount = 0;
            this.landed = false;
            this.animTick = 0;
        }

        java.awt.Rectangle getPickupRect() {
            return new java.awt.Rectangle(x - PICKUP_RADIUS, (int) currentY - PICKUP_RADIUS,
                    PICKUP_RADIUS * 2, PICKUP_RADIUS * 2);
        }
    }

    private final ArrayList<LootDrop> lootDrops = new ArrayList<>();

    private float bgParallaxOffset = 0f;
    private int bgTick = 0;

    // ── Cached rendering resources (allocated once, not every frame) ──────────
    private static final Color[] CHAPTER_OVERLAY_COLORS = {
        new Color(80,  0,   0,   25), // Ch1 – blood red
        new Color(60,  0,   100, 28), // Ch2 – deep purple
        new Color(40,  40,  60,  20), // Ch3 – cold grey
        new Color(0,   50,  10,  25), // Ch4 – swamp green
        new Color(0,   40,  80,  27), // Ch5 – ocean blue
        new Color(80,  60,  0,   22), // Ch6 – golden haze
        new Color(80,  30,  0,   31), // Ch7 – ember orange
        new Color(0,   60,  20,  25), // Ch8 – jungle green
        new Color(80,  0,   100, 35), // Ch9 – violet hellfire
    };
    private static final Font HINT_FONT    = new Font("SansSerif", Font.BOLD, 18);
    private static final Font LABEL_FONT   = new Font("SansSerif", Font.BOLD, 11);
    private static final Font NGP_FONT1    = new Font("Serif", Font.BOLD | Font.ITALIC, 52);
    private static final Font NGP_FONT2    = new Font("Serif", Font.BOLD, 34);
    private static final Font NGP_FONT3    = new Font("Serif", Font.ITALIC, 20);

    private static class BgParticle {
        float x, y, vx, vy, size, alpha;
        int r, g, b, life, maxLife;

        BgParticle(float x, float y, float vx, float vy, float size,
                int r, int g, int b, int life) {
            this.x = x;
            this.y = y;
            this.vx = vx;
            this.vy = vy;
            this.size = size;
            this.r = r;
            this.g = g;
            this.b = b;
            this.life = this.maxLife = life;
            this.alpha = 1f;
        }
    }

    private final ArrayList<BgParticle> bgParticles = new ArrayList<>();

    private static class BgCloud {
        float x, y, speed, alpha, scaleX, scaleY;
        int r, g, b;

        BgCloud(float x, float y, float speed, float alpha, float scaleX, float scaleY, int r, int g, int b) {
            this.x = x;
            this.y = y;
            this.speed = speed;
            this.alpha = alpha;
            this.scaleX = scaleX;
            this.scaleY = scaleY;
            this.r = r;
            this.g = g;
            this.b = b;
        }
    }

    private final ArrayList<BgCloud> bgClouds = new ArrayList<>();

    private final ArrayList<Bangis> bangis = new ArrayList<>();
    private final ArrayList<Voodoo> voodoos = new ArrayList<>();
    private final ArrayList<Bat> bats = new ArrayList<>();
    private final ArrayList<Tiyanak> tiyanaks = new ArrayList<>();
    private final ArrayList<Shokoy> shokoys = new ArrayList<>();
    private final ArrayList<Duwende> duwendes = new ArrayList<>();
    private final ArrayList<Kapre> kapres = new ArrayList<>();
    private final ArrayList<Santelmo> santelmos = new ArrayList<>();
    private final ArrayList<Skeleton> skeletons = new ArrayList<>();
    private final java.util.ArrayDeque<Object> spawnQueue = new java.util.ArrayDeque<>();
    private int spawnTick = 0;
    private static final int SPAWN_INTERVAL = 60;

    private Aswang aswang = null;
    private Mangkukulam mangkukulam = null;
    private Severino severino = null;
    private Sigbin sigbin = null;
    private Bakunawa bakunawa = null;
    private Tikbalang tikbalang = null;
    private Kapre kapreBoss = null;
    private Amomongo amomongo = null;
    private Sitan sitan = null;
    private boolean bossIntroActive = false;
    private int bossIntroTimer = 0;
    private static final int BOSS_INTRO_TICKS = 180;

    private int curseDotTimer = 0, curseTickCounter = 0;

    private int kapreBurnTimer = 0, kapreBurnTick = 0;
    private static final int KAPRE_BURN_TICK_DMG = 5;
    private static final int KAPRE_BURN_TICK_INTERVAL = 30;
    private static final int KAPRE_BURN_DURATION = 180;
    private int kapreConfuseTimer = 0;
    private int kapreSlowTimer = 0;

    private final ArrayList<Aswang_Mob> aswangMobs = new ArrayList<>();
    private final ArrayList<Sirena> sirenas = new ArrayList<>();

    private int newGamePlusCount = 0;
    private boolean newGamePlusTransition = false;
    private int newGamePlusTimer = 0;
    private static final int NEW_GAME_PLUS_DELAY = 240;

    private boolean levelClear = false, bossDefeated = false;
    private int advanceTimer = 0; // counts up after clear/boss defeated; triggers auto-advance

    private boolean shopOpen = false, paused = false, gameOver = false;
    private int shopHoveredSlot = -1, pauseHovered = -1;

    // ── Alive-sound heartbeat ─────────────────────────────────────────────────
    /** Ticks since last "alive" ambient sound; fires every ~6 s (360 ticks @ 60 fps). */
    private int aliveAudioTick = 0;
    private static final int ALIVE_SOUND_INTERVAL = 360;

    private Thread gameThread;
    private Runnable onExitToMenu;
    private volatile boolean running = false;

    // ── Background music ──────────────────────────────────────────────────────
    private javax.sound.sampled.Clip bgMusicClip;

    // ── Dev/test mode (unlocked by secret names) ──────────────────────────────
    private final boolean devMode;
    private boolean devChapterSelectOpen = false;  // T-key chapter jump overlay
    private static final java.util.Set<String> DEV_NAMES = java.util.Set.of(
            "Ivankaizer", "StevenKenn", "JamesStephen");

    public void setOnExitToMenu(Runnable r) {
        onExitToMenu = r;
    }

    public GamePanel(GameSettings settings, PlayerProfile playerData, String charType) {
        this.settings = settings;
        this.playerData = playerData;
        this.charType = charType;

        devMode = DEV_NAMES.contains(playerData.name);

        int diff = settings.difficulty;
        enemyDmgMult = diff == 0 ? 1 : diff == 1 ? 3 : 6;
        enemySpeedMult = diff == 0 ? 0.8f : diff == 1 ? 1.5f : 2.5f;
        // Easy: player is noticeably stronger; Normal: balanced; Hard: enemies slightly overpower player
        playerDmgMult = diff == 0 ? 1.8f : diff == 1 ? 1.0f : 0.65f;

        player = switch (charType) {
            case "mage" -> new Mage();
            case "fighter" -> new Fighter();
            default -> new Player();
        };

        // Always start at full 100 HP — difficulty balance comes from damage/speed multipliers above,
        // not from inflating or deflating the starting health pool.
        player.health = 100;

        setBackground(Color.BLACK);
        setFocusable(true);
        addKeyListener(this);
        addMouseListener(this);
        addMouseMotionListener(new MouseMotionAdapter() {
            @Override
            public void mouseMoved(MouseEvent e) {
                int mx = mouseToCanvasX(e.getX()), my = mouseToCanvasY(e.getY());
                if (shopOpen)
                    shopHoveredSlot = shop.getHoveredSlot(mx, my, BASE_W, BASE_H);
                if (paused)
                    pauseHovered = pauseHit(mx, my);
            }
        });
        assets.loadAssets(charType, CHAPTER_BG[chapter]);
        loadHotbarIcons();
    }

    public void startGameThread() {
        player.y = (int) (BASE_H * GROUND_FRAC) - Player.PS;
        setupLevel();
        running = true;
        playBgMusic();
        (gameThread = new Thread(this)).start();
    }

    public void stopGameThread() {
        running = false;
        stopBgMusic();
        SoundManager.stopAll();   // kill every in-flight enemy / player SFX clip
        if (gameThread != null) {
            gameThread.interrupt();
        }
    }

    /** Loads all slot icon images from assets/Icons/. Falls back to drawn shapes if missing. */
    private void loadHotbarIcons() {
        iconSword   = AssetManager.img("assets/Icons/sword.png");
        iconBow     = AssetManager.img("assets/Icons/bow.png");
        iconBolt    = AssetManager.img("assets/Icons/bolt.png");
        iconBeam    = AssetManager.img("assets/Icons/beam.png");
        iconPunch   = AssetManager.img("assets/Icons/punch.png");
        iconPower   = AssetManager.img("assets/Icons/power.png");
        iconShield  = AssetManager.img("assets/Icons/shield.png");
        iconPotion  = AssetManager.img("assets/Icons/potion.png");
        // Also push shared icons to the shop renderer
        shop.loadIcons(iconSword, iconBow, iconBolt, iconBeam,
                       iconPunch, iconPower, iconShield, iconPotion);
    }

    /**
     * Dev-only: swap the active player class at runtime.
     * Preserves current HP, position, coins and equipment levels.
     */
    private void switchCharacter(String newCharType) {
        if (newCharType.equals(charType)) return;

        Player old = player;
        Player neo = switch (newCharType) {
            case "mage"    -> new Mage();
            case "fighter" -> new Fighter();
            default        -> new Player();
        };
        // Carry over runtime state so the swap feels seamless
        neo.health    = old.health;
        neo.x         = old.x;
        neo.y         = old.y;
        neo.facingLeft = old.facingLeft;

        // Reset skill levels tied to the old class
        mageBeamLevel = 0; mageBoltLevel = 0;
        fighterPunchLevel = 0; fighterPowerPunchLevel = 0;
        sword.level = 0; bow.level = 0;

        charType = newCharType;
        player   = neo;
        assets.loadAssets(charType, CHAPTER_BG[chapter]);
        loadHotbarIcons();
        // Re-setup so enemies / spawns reflect the new session cleanly
        setupLevel();
    }

    private void playBgMusic() {
        try {
            java.io.File f = new java.io.File("sounds/system/BackgroundMusic.wav");
            if (!f.exists()) {
                System.err.println("[GamePanel] BGM not found: sound/BackgroundMusic.wav");
                return;
            }
            javax.sound.sampled.AudioInputStream ais = javax.sound.sampled.AudioSystem.getAudioInputStream(f);
            bgMusicClip = javax.sound.sampled.AudioSystem.getClip();
            bgMusicClip.open(ais);
            bgMusicClip.loop(javax.sound.sampled.Clip.LOOP_CONTINUOUSLY);
            bgMusicClip.start();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void stopBgMusic() {
        if (bgMusicClip != null) {
            try { bgMusicClip.stop();  } catch (Exception ignored) {}
            try { bgMusicClip.close(); } catch (Exception ignored) {}
            bgMusicClip = null;
        }
    }

    /** Freeze background music at its current position (used when pausing). */
    private void pauseBgMusic() {
        if (bgMusicClip != null && bgMusicClip.isRunning())
            bgMusicClip.stop();   // stop() preserves the frame position
    }

    /** Resume background music from where it was frozen (used when unpausing). */
    private void resumeBgMusic() {
        if (bgMusicClip != null && !bgMusicClip.isRunning())
            bgMusicClip.start(); // start() continues from the saved frame position
    }

    @Override
    public void run() {
        while (running) {
            long frameStart = System.nanoTime();
            if (!paused && !gameOver && !shopOpen)
                update();
            repaint();
            long elapsed = (System.nanoTime() - frameStart) / 1_000_000;
            long sleepTime = 16 - elapsed;
            if (sleepTime > 0) {
                try {
                    Thread.sleep(sleepTime);
                } catch (InterruptedException e) {
                    break;
                }
            }
        }
    }

    private void setupLevel() {
        bangis.clear();
        voodoos.clear();
        bats.clear();
        tiyanaks.clear();
        shokoys.clear();
        duwendes.clear();
        kapres.clear();
        santelmos.clear();
        skeletons.clear();
        aswangMobs.clear();
        sirenas.clear();
        spawnQueue.clear();
        spawnTick = 0;
        lootDrops.clear();
        bgParticles.clear();
        bgClouds.clear();
        bgParallaxOffset = 0f;
        levelClear = false;
        bossDefeated = false;
        advanceTimer = 0;
        aswang = null;
        mangkukulam = null;
        severino = null;
        sigbin = null;
        bakunawa = null;
        tikbalang = null;
        kapreBoss = null;
        amomongo = null;
        sitan = null;
        bossIntroActive = false;
        curseDotTimer = 0;
        curseTickCounter = 0;
        kapreBurnTimer = 0;
        kapreBurnTick = 0;
        kapreConfuseTimer = 0;
        kapreSlowTimer = 0;
        player.moveSpeedMult = 1.0f;

        if (isBossLevel()) {
            bossIntroActive = true;
            bossIntroTimer = 0;
        } else {
            wave = 1;
            queueWave();
        }
    }

    private boolean isBossLevel() {
        return switch (chapter) {
            case 0 -> Chapter1.isBossLevel(level);
            case 1 -> Chapter2.isBossLevel(level);
            case 2 -> Chapter3.isBossLevel(level);
            case 3 -> Chapter4.isBossLevel(level);
            case 4 -> Chapter5.isBossLevel(level);
            case 5 -> Chapter6.isBossLevel(level);
            case 6 -> Chapter7.isBossLevel(level);
            case 7 -> Chapter8.isBossLevel(level);
            default -> Chapter9.isBossLevel(level);
        };
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Wave size — scales with both difficulty and how many chapters the player
    // has cleared (chapter is 0-indexed, so chapter 1 cleared = chapter == 1).
    //
    // Easy : 20 base + 2 × chapter
    // Normal: 30 base + 3 × chapter
    // Hard : 35 base + 4 × chapter
    // ─────────────────────────────────────────────────────────────────────────
    private int getWaveSize() {
        int diff = settings.difficulty;
        int base = diff == 0 ? 7 : diff == 1 ? 10 : 15;
        int perChapter = diff == 0 ? 2 : diff == 1 ? 3 : 4;
        return base + chapter * perChapter;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // queueWave — fills spawnQueue with the correct enemy mix for the current
    // chapter. Enemies alternate spawn side (left / right off-screen) and
    // cycle through the chapter's designated enemy pool so every wave has
    // variety while respecting the per-chapter design spec.
    //
    // Chapter → Enemy pool (boss excluded):
    // 1 Kagubatan ng Salinlahi : Bangis, Duwende, Bat
    // 2 Lungsod ng Dilim : Bangis, Bat, Voodoo, Aswang_Mob
    // 3 Sigaw ni Severio : Bangis, Bat, Skeleton
    // 4 Bayan ng Caramoan : Bangis, Tiyanak, Duwende, Aswang_Mob
    // 5 Dagat ng San Juanita : Shokoy, Sirena
    // 6 Isla ng Lihim : Bangis, Duwende
    // 7 Tinatagong Kasukalan : Bangis, Duwende, Aswang_Mob, Bat
    // 8 Mahiwagang Kweba : Bangis, Bat, Skeleton, Duwende
    // 9 Kasanaan : Bangis, Santelmo, Skeleton, Bat, Aswang_Mob
    //
    // On stages 1 and 2 (level <= 2): different mob types spawn sequentially —
    // the next type only queues after all enemies of the current type are cleared.
    // The wave counter does NOT advance until all sequential groups are done.
    // From stage 3 onward all types spawn interleaved as before.
    //
    // NOTE: Sirena is not yet in the mob-update / spawn pipeline (no active
    // list in GamePanel), so it is omitted here. Manananggal has been replaced
    // by Aswang_Mob across all chapters. Shokoy has no speed-mult constructor
    // so it runs at its internal speed.
    // ─────────────────────────────────────────────────────────────────────────

    // Tracks which sequential mob group we are currently spawning (only used on level 1 & 2).
    private int seqGroupIndex = 0;

    private void queueWave() {
        spawnQueue.clear();
        seqGroupIndex = 0;
        int W = BASE_W;
        int gY = groundY();
        int n = getWaveSize();

        // On stages 1 and 2, queue only the first mob group; remaining groups
        // are queued one-at-a-time via checkLevelProgress when the screen clears.
        if (level <= 2) {
            queueSequentialGroup(0, n, W, gY);
        } else {
            // Stage 3+ — interleaved spawning (original behaviour)
            for (int i = 0; i < n; i++) {
                int dir = (i % 2 == 0) ? 1 : -1;
                int spawnX = dir > 0 ? -80 : W + 80;
                addChapterEnemy(i, spawnX, gY, dir);
            }
        }
        spawnTick = 3;
    }

    /** Queues one sequential mob group for the current chapter/wave on levels 1-2.
     *  Each group contains n/poolSize enemies of a single type (rounded up for last group). */
    private void queueSequentialGroup(int groupIdx, int totalN, int W, int gY) {
        int poolSize = getChapterPoolSize();
        // Divide enemies evenly across groups; last group gets any remainder
        int perGroup = Math.max(1, totalN / poolSize);
        int start = groupIdx * perGroup;
        int end   = (groupIdx == poolSize - 1) ? totalN : Math.min(start + perGroup, totalN);

        for (int i = start; i < end; i++) {
            int dir    = (i % 2 == 0) ? 1 : -1;
            int spawnX = dir > 0 ? -80 : W + 80;
            addChapterEnemyByGroup(groupIdx, i - start, spawnX, gY, dir);
        }
    }

    /** Returns how many distinct mob types the current chapter has (pool size). */
    private int getChapterPoolSize() {
        return switch (chapter) {
            case 0 -> 3; // Bangis, Duwende, Bat
            case 1 -> 4; // Bangis, Bat, Voodoo, Aswang_Mob
            case 2 -> 3; // Bangis, Bat, Skeleton
            case 3 -> 4; // Bangis, Tiyanak, Duwende, Aswang_Mob
            case 4 -> 2; // Shokoy, Sirena
            case 5 -> 2; // Bangis, Duwende
            case 6 -> 4; // Bangis, Duwende, Aswang_Mob, Bat
            case 7 -> 4; // Bangis, Bat, Skeleton, Duwende
            default -> 5; // Bangis, Santelmo, Skeleton, Bat, Aswang_Mob
        };
    }

    /** Spawns a single enemy of the type assigned to groupIdx for the current chapter. */
    private void addChapterEnemyByGroup(int groupIdx, int i, int spawnX, int gY, int dir) {
        switch (chapter) {
            case 0 -> { // Bangis · Duwende · Bat
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
                else spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
            }
            case 1 -> { // Bangis · Bat · Voodoo · Aswang_Mob
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else if (groupIdx == 2) spawnQueue.add(new Voodoo(spawnX, gY, enemySpeedMult, dir));
                else spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
            }
            case 2 -> { // Bangis · Bat · Skeleton
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else spawnQueue.add(new Skeleton(spawnX, gY - Skeleton.HEIGHT, enemySpeedMult, dir));
            }
            case 3 -> { // Bangis · Tiyanak · Duwende · Aswang_Mob
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Tiyanak(spawnX, gY, enemySpeedMult, dir));
                else if (groupIdx == 2) spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
                else spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
            }
            case 4 -> { // Shokoy · Sirena
                if (groupIdx == 0) spawnQueue.add(new Shokoy(spawnX, gY - Shokoy.HEIGHT));
                else spawnQueue.add(new Sirena(spawnX, gY - Sirena.HEIGHT, enemySpeedMult, dir));
            }
            case 5 -> { // Bangis · Duwende
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
            }
            case 6 -> { // Bangis · Duwende · Aswang_Mob · Bat
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
                else if (groupIdx == 2) spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
                else spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
            }
            case 7 -> { // Bangis · Bat · Skeleton · Duwende
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else if (groupIdx == 2) spawnQueue.add(new Skeleton(spawnX, gY - Skeleton.HEIGHT, enemySpeedMult, dir));
                else spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
            }
            default -> { // Bangis · Santelmo · Skeleton · Bat · Aswang_Mob
                if (groupIdx == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (groupIdx == 1) spawnQueue.add(new Santelmo(spawnX, gY - 210, enemySpeedMult, dir));
                else if (groupIdx == 2) spawnQueue.add(new Skeleton(spawnX, gY - Skeleton.HEIGHT, enemySpeedMult, dir));
                else if (groupIdx == 3) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
            }
        }
    }

    /** Adds one enemy using the original interleaved cycling logic (stage 3+). */
    private void addChapterEnemy(int i, int spawnX, int gY, int dir) {
        switch (chapter) {
            case 0 -> {
                int t = i % 3;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
                else spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
            }
            case 1 -> {
                int t = i % 4;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else if (t == 2) spawnQueue.add(new Voodoo(spawnX, gY, enemySpeedMult, dir));
                else spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
            }
            case 2 -> {
                int t = i % 3;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else spawnQueue.add(new Skeleton(spawnX, gY - Skeleton.HEIGHT, enemySpeedMult, dir));
            }
            case 3 -> {
                int t = i % 4;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Tiyanak(spawnX, gY, enemySpeedMult, dir));
                else if (t == 2) spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
                else spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
            }
            case 4 -> {
                if (i % 2 == 0) spawnQueue.add(new Shokoy(spawnX, gY - Shokoy.HEIGHT));
                else spawnQueue.add(new Sirena(spawnX, gY - Sirena.HEIGHT, enemySpeedMult, dir));
            }
            case 5 -> {
                if (i % 2 == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
            }
            case 6 -> {
                int t = i % 4;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
                else if (t == 2) spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
                else spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
            }
            case 7 -> {
                int t = i % 4;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else if (t == 2) spawnQueue.add(new Skeleton(spawnX, gY - Skeleton.HEIGHT, enemySpeedMult, dir));
                else spawnQueue.add(new Duwende(spawnX, gY, enemySpeedMult, dir));
            }
            default -> {
                int t = i % 5;
                if (t == 0) spawnQueue.add(new Bangis(spawnX, gY - 50, enemySpeedMult, dir));
                else if (t == 1) spawnQueue.add(new Santelmo(spawnX, gY - 210, enemySpeedMult, dir));
                else if (t == 2) spawnQueue.add(new Skeleton(spawnX, gY - Skeleton.HEIGHT, enemySpeedMult, dir));
                else if (t == 3) spawnQueue.add(new Bat(spawnX, gY - 160, enemySpeedMult, dir));
                else spawnQueue.add(new Aswang_Mob(spawnX, gY - Aswang_Mob.HEIGHT));
            }
        }
    }

    private void update() {
        // ── New Game+ transition (Sitan defeated) ────────────────────────────
        if (newGamePlusTransition) {
            if (++newGamePlusTimer >= NEW_GAME_PLUS_DELAY) {
                newGamePlusTransition = false;
                newGamePlusTimer = 0;
                restartFromChapter1();
            }
            return; // freeze normal updates while showing the transition screen
        }

        // ── Periodic "alive" ambient voice ───────────────────────────────────
        if (!gameOver && !player.deathAnimPlaying) {
            if (++aliveAudioTick >= ALIVE_SOUND_INTERVAL) {
                aliveAudioTick = 0;
                if (player instanceof Mage)
                    SoundManager.playCooldown("sounds/player/woman.wav", 4000);
                else
                    SoundManager.playCooldown("sounds/player/man.wav", 4000);
            }
        }

        player.updatePhysics(groundY());
        if (player instanceof Mage m)
            m.updateSkills();
        if (player instanceof Fighter f)
            f.updateSkills();
        updateWarriorWeapons();
        updateArrows();
        updateBackground();
        player.updateAnimation(sword, bow, potion);
        updateCurseDot();
        updateKapreStatusEffects();
        updateEnemySpawn();
        updateEnemies();
        updateBats();
        updateTiyanaks();
        updateShokoys();
        updateDuwendes();
        updateKapres();
        updateSantelmos();
        updateSkeletons();
        updateAswangMobs();
        updateSirenas();
        updateBoss();
        reapDeadEnemies();
        updateLootDrops();
        checkLevelProgress();
        checkBounds();
        checkGameOver();
    }

    private void updateBackground() {
        bgTick++;
        // Parallax: drift offset gently with player movement
        if (player.moveLeft)
            bgParallaxOffset -= 0.4f;
        if (player.moveRight)
            bgParallaxOffset += 0.4f;
        // Clamp so the image never gaps at edges (max shift = 80px each side)
        bgParallaxOffset = Math.max(-80f, Math.min(80f, bgParallaxOffset));
        // Slowly return to center when player is still
        if (!player.moveLeft && !player.moveRight)
            bgParallaxOffset *= 0.97f;

        int W = BASE_W;
        int H = BASE_H;
        // Spawn ambient particles — rate and colour are chapter-themed
        if (bgTick % 4 == 0) {
            spawnBgParticle(W, H);
        }
        // ── Cloud spawning ────────────────────────────────────────────────────
        if (bgClouds.size() < 7 && bgTick % 90 == 0) {
            spawnBgCloud(W, H);
        }
        // Pre-populate clouds on first tick
        if (bgTick == 1) {
            for (int i = 0; i < 5; i++) {
                spawnBgCloud(W, H);
                // Spread them across the screen initially
                if (!bgClouds.isEmpty())
                    bgClouds.get(bgClouds.size() - 1).x = (float) (Math.random() * W);
            }
        }
        // Tick existing clouds (drift left, wrap around)
        java.util.Iterator<BgCloud> cit = bgClouds.iterator();
        while (cit.hasNext()) {
            BgCloud c = cit.next();
            c.x -= c.speed;
            if (c.x + c.scaleX * 120 < 0)
                cit.remove(); // offscreen left
        }

        // Tick existing particles
        java.util.Iterator<BgParticle> it = bgParticles.iterator();
        while (it.hasNext()) {
            BgParticle p = it.next();
            p.x += p.vx;
            p.y += p.vy;
            p.life--;
            p.alpha = (float) p.life / p.maxLife;
            if (p.life <= 0)
                it.remove();
        }
    }

    /** Spawn one ambient particle themed to the current chapter. */
    private void spawnBgParticle(int W, int H) {
        float x = (float) (Math.random() * W);
        // Particles drift upward from the lower half of the screen
        float y = (float) (H * 0.4 + Math.random() * H * 0.6);
        float vx = (float) (Math.random() * 0.8 - 0.4);
        float vy = (float) (-Math.random() * 0.8 - 0.2); // upward
        float size = (float) (2 + Math.random() * 4);
        int life = 80 + (int) (Math.random() * 120);
        // Colour theme per chapter
        int r, g, b;
        switch (chapter) {
            case 0 -> {
                r = 180;
                g = 60;
                b = 60;
            } // Ch1 – dark red embers (Aswang forest)
            case 1 -> {
                r = 160;
                g = 0;
                b = 200;
            } // Ch2 – purple curse motes (Mangkukulam)
            case 2 -> {
                r = 200;
                g = 200;
                b = 220;
            } // Ch3 – pale grey bone dust (Severino)
            case 3 -> {
                r = 80;
                g = 200;
                b = 80;
            } // Ch4 – sickly green wisps (Sigbin swamp)
            case 4 -> {
                r = 40;
                g = 160;
                b = 220;
            } // Ch5 – ocean blue sea spray (Bakunawa)
            case 5 -> {
                r = 220;
                g = 160;
                b = 40;
            } // Ch6 – golden dust (Tikbalang plains)
            case 6 -> {
                r = 200;
                g = 80;
                b = 20;
            } // Ch7 – orange embers (Kapre smoke)
            case 7 -> {
                r = 140;
                g = 220;
                b = 140;
            } // Ch8 – jungle green spores (Amomongo)
            default -> {
                r = 200;
                g = 50;
                b = 240;
            } // Ch9 – violet hellfire (Sitan)
        }
        bgParticles.add(new BgParticle(x, y, vx, vy, size, r, g, b, life));
    }

    /** Spawn one drifting cloud themed to the current chapter. */
    private void spawnBgCloud(int W, int H) {
        // Clouds spawn just off the right edge and drift left
        float x = W + (float) (Math.random() * 60);
        float y = (float) (Math.random() * H * 0.55); // upper 55 % of screen
        float speed = (float) (0.3 + Math.random() * 0.5);
        float alpha = (float) (0.10 + Math.random() * 0.25);
        float scaleX = (float) (1.0 + Math.random() * 1.8);
        float scaleY = (float) (0.5 + Math.random() * 0.8);
        // Colour is driven by chapter atmosphere
        int r, g, b;
        switch (chapter) {
            case 0 -> {
                r = 220;
                g = 160;
                b = 160;
            } // Ch1 – dusty red wisps
            case 1 -> {
                r = 180;
                g = 140;
                b = 220;
            } // Ch2 – purple haze
            case 2 -> {
                r = 200;
                g = 200;
                b = 210;
            } // Ch3 – cold white-grey
            case 3 -> {
                r = 140;
                g = 200;
                b = 140;
            } // Ch4 – swamp mist
            case 4 -> {
                r = 140;
                g = 200;
                b = 240;
            } // Ch5 – sea-spray blue
            case 5 -> {
                r = 240;
                g = 220;
                b = 160;
            } // Ch6 – golden dust clouds
            case 6 -> {
                r = 220;
                g = 160;
                b = 100;
            } // Ch7 – smoky amber (Kapre)
            case 7 -> {
                r = 160;
                g = 220;
                b = 160;
            } // Ch8 – jungle mist
            default -> {
                r = 200;
                g = 160;
                b = 240;
            } // Ch9 – violet hellsmoke
        }
        bgClouds.add(new BgCloud(x, y, speed, alpha, scaleX, scaleY, r, g, b));
    }

    private void updateWarriorWeapons() {
        potion.update();
        if (player.hitFlashTimer > 0)
            player.hitFlashTimer--;
        if (player.deathAnimPlaying && ++player.deathFrameTimer >= 12) {
            player.deathFrameTimer = 0;
            if (player.deathFrame < 2)
                player.deathFrame++;
        }
        if (player.getClass() != Player.class)
            return;
        sword.update();
        if (bow.active || bow.reloading) {
            if (bow.update()) {
                int ay = player.duck && player.onGround
                        ? player.y + Player.PS - Player.DUCK_HEIGHT / 2
                        : player.y + Player.PS / 2 - 3;
                int ax = player.facingLeft ? player.x - 10 : player.x + Player.PS;
                arrows.add(new Equipments.Arrow(ax, ay, player.facingLeft ? -12 : 12));
            }
        }
    }

    private void updateKapreStatusEffects() {
        // Burn DoT
        if (kapreBurnTimer > 0) {
            kapreBurnTimer--;
            if (++kapreBurnTick >= KAPRE_BURN_TICK_INTERVAL) {
                kapreBurnTick = 0;
                applyDamageToPlayer(KAPRE_BURN_TICK_DMG * enemyDmgMult);
            }
        }
        // Confuse: tick down (actual input reversal handled in keyPressed)
        if (kapreConfuseTimer > 0)
            kapreConfuseTimer--;
        // Slow: tick down and drive player speed multiplier every frame
        if (kapreSlowTimer > 0) {
            kapreSlowTimer--;
            player.moveSpeedMult = 0.35f;
        } else {
            player.moveSpeedMult = 1.0f;
        }
    }

    private void updateCurseDot() {
        if (curseDotTimer <= 0)
            return;
        curseDotTimer--;
        if (++curseTickCounter >= Mangkukulam.CurseOrb.DOT_TICK_INTERVAL) {
            curseTickCounter = 0;
            applyDamageToPlayer(Mangkukulam.CurseOrb.DOT_TICK_DAMAGE * enemyDmgMult);
        }
    }

    private void updateArrows() {
        Iterator<Equipments.Arrow> it = arrows.iterator();
        while (it.hasNext()) {
            Equipments.Arrow a = it.next();
            a.update();
            Rectangle ar = a.getHitbox();
            boolean hit = false;
            for (Bangis en : bangis)
                if (en.isAlive() && en.getHitbox().intersects(ar)) {
                    en.takeDamage(devDmg(bow.getDamage()));
                    hit = true;
                    break;
                }
            if (!hit)
                for (Voodoo v : voodoos)
                    if ((v.isAlive() || v.isBooming()) && v.getHitbox().intersects(ar)) {
                        v.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Bat b : bats)
                    if (b.isAlive() && b.getHitbox().intersects(ar)) {
                        b.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Tiyanak t : tiyanaks)
                    if (t.isAlive() && t.getHitbox().intersects(ar)) {
                        t.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Shokoy s : shokoys)
                    if (s.isAlive() && s.getHitbox().intersects(ar)) {
                        s.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Duwende d : duwendes)
                    if (d.isAlive() && d.getHitbox().intersects(ar)) {
                        d.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Kapre k : kapres)
                    if (k.isAlive() && k.getHitbox().intersects(ar)) {
                        k.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Santelmo sm : santelmos)
                    if (sm.isAlive() && sm.getHitbox().intersects(ar)) {
                        sm.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Skeleton sk : skeletons)
                    if (sk.isAlive() && sk.getHitbox().intersects(ar)) {
                        sk.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit)
                for (Aswang_Mob am : aswangMobs)
                    if (am.isAlive() && am.getHitbox().intersects(ar)) {
                        am.takeDamage(devDmg(bow.getDamage()));
                        hit = true;
                        break;
                    }
            if (!hit && aswang != null && aswang.isAlive() && aswang.getHitbox().intersects(ar)) {
                aswang.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && mangkukulam != null && mangkukulam.isAlive() && mangkukulam.getHitbox().intersects(ar)) {
                mangkukulam.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && severino != null && severino.isAlive() && severino.getHitbox().intersects(ar)) {
                severino.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && sigbin != null && sigbin.isAlive() && sigbin.getHitbox().intersects(ar)) {
                sigbin.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && tikbalang != null && tikbalang.isAlive() && tikbalang.getHitbox().intersects(ar)) {
                tikbalang.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && kapreBoss != null && kapreBoss.isAlive() && kapreBoss.getHitbox().intersects(ar)) {
                kapreBoss.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && amomongo != null && amomongo.isAlive() && amomongo.getHitbox().intersects(ar)) {
                amomongo.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (!hit && sitan != null && sitan.isAlive() && sitan.getHitbox().intersects(ar)) {
                sitan.takeDamage(devDmg(bow.getDamage()));
                hit = true;
            }
            if (hit || a.x > BASE_W || a.x < 0)
                it.remove();
        }
    }

    private void reapDeadEnemies() {
        bangis.removeIf(en -> {
            if (!en.isAlive()) {
                Rectangle hb = en.getHitbox();
                awardMob(10, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        voodoos.removeIf(v -> !v.isAlive() && !v.isBooming());
        bats.removeIf(b -> {
            if (!b.isAlive()) {
                Rectangle hb = b.getHitbox();
                awardMob(10, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        tiyanaks.removeIf(t -> {
            if (!t.isAlive()) {
                Rectangle hb = t.getHitbox();
                awardMob(10, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        shokoys.removeIf(s -> {
            if (!s.isAlive()) {
                Rectangle hb = s.getHitbox();
                awardMob(15, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        duwendes.removeIf(d -> {
            if (!d.isAlive()) {
                Rectangle hb = d.getHitbox();
                awardMob(10, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        kapres.removeIf(k -> {
            if (!k.isAlive()) {
                Rectangle hb = k.getHitbox();
                awardMob(20, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        santelmos.removeIf(s -> {
            if (!s.isAlive()) {
                Rectangle hb = s.getHitbox();
                awardMob(15, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        skeletons.removeIf(sk -> {
            if (!sk.isAlive()) {
                Rectangle hb = sk.getHitbox();
                awardMob(12, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        aswangMobs.removeIf(a -> {
            if (!a.isAlive()) {
                Rectangle hb = a.getHitbox();
                awardMob(20, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        sirenas.removeIf(sr -> {
            if (!sr.isAlive()) {
                Rectangle hb = sr.getHitbox();
                awardMob(18, hb.x + hb.width / 2, hb.y + hb.height / 2);
                return true;
            }
            return false;
        });
        if (aswang != null && !aswang.isAlive()) {
            award(100, 50);
            aswang = null;
            bossDefeated = true;
        }
        if (mangkukulam != null && !mangkukulam.isAlive()) {
            award(100, 50);
            mangkukulam = null;
            bossDefeated = true;
        }
        if (severino != null && !severino.isAlive()) {
            award(100, 50);
            severino = null;
            bossDefeated = true;
        }
        if (sigbin != null && !sigbin.isAlive()) {
            award(100, 50);
            sigbin = null;
            bossDefeated = true;
        }
        if (bakunawa != null && !bakunawa.alive) {
            award(100, 50);
            bakunawa = null;
            bossDefeated = true;
        }
        if (tikbalang != null && !tikbalang.isAlive()) {
            award(100, 50);
            tikbalang = null;
            bossDefeated = true;
        }
        if (kapreBoss != null && !kapreBoss.isAlive()) {
            award(100, 50);
            kapreBoss = null;
            bossDefeated = true;
        }
        if (amomongo != null && !amomongo.isAlive()) {
            award(100, 50);
            amomongo = null;
            bossDefeated = true;
        }
        if (sitan != null && !sitan.isAlive()) {
            award(150, 75);
            sitan = null;
            bossDefeated = true;
            newGamePlusCount++;
            newGamePlusTransition = true;
            newGamePlusTimer = 0;
        }
    }

    private void award(int coins, int score) {
        sessionCoins += coins;
        sessionScore += score;
        enemiesDefeated++;
    }

    private void awardMob(int score, int ex, int ey) {
        sessionScore += score;
        enemiesDefeated++;
        rollLootDrop(ex, ey);
    }

    /** Returns 99999 in dev mode (1-hit kill), otherwise scales by difficulty. */
    private int devDmg(int realDamage) {
        return devMode ? 99999 : (int) (realDamage * playerDmgMult);
    }

    /** Roll a random loot drop for a slain mob and add it to the field. */
    private void rollLootDrop(int ex, int ey) {
        // groundY() is where the player's feet stand; subtract ITEM_HALF so the
        // bottom of the loot sprite aligns with that same floor line.
        int gY = groundY() - LootDrop.ITEM_HALF;
        int coinAmt = 1 + LOOT_RNG.nextInt(6); // 1–6 coins
        lootDrops.add(new LootDrop(ex, ey, LootDrop.Type.COINS, coinAmt, gY));
        SoundManager.playCooldown("sounds/item/buy.wav", 300); // item-drop sound (shared wav)

        int roll = LOOT_RNG.nextInt(100);
        if (roll < 25) { // 25% potion drop
            lootDrops.add(new LootDrop(ex - 20, ey, LootDrop.Type.POTION, 0, gY));
        } else if (roll < 70) { // additional 45% health drop
            lootDrops.add(new LootDrop(ex + 20, ey, LootDrop.Type.HEALTH, 0, gY));
        }
    }

    private void updateLootDrops() {
        if (lootDrops.isEmpty())
            return;
        Rectangle ph = player.getHitbox();
        Iterator<LootDrop> it = lootDrops.iterator();
        while (it.hasNext()) {
            LootDrop ld = it.next();
            if (ld.labelTimer > 0)
                ld.labelTimer--;
            ld.animTick++;

            // ── Physics: gravity + bounce until settled ────────────────────────
            if (!ld.landed) {
                // Apply gravity
                ld.velY += LootDrop.GRAVITY;
                ld.currentY += ld.velY;

                // Horizontal drift with friction
                ld.x += (int) ld.velX;
                ld.velX *= LootDrop.HORIZ_FRICTION;
                // Clamp to screen
                ld.x = Math.max(20, Math.min(BASE_W - 20, ld.x));

                // Ground collision
                if (ld.currentY >= ld.groundY) {
                    ld.currentY = ld.groundY;
                    ld.bounceCount++;
                    float reboundVel = -ld.velY * LootDrop.BOUNCE_DAMP;
                    if (ld.bounceCount >= LootDrop.MAX_BOUNCES
                            || Math.abs(reboundVel) < LootDrop.STOP_VEL) {
                        // Fully settled
                        ld.velY = 0;
                        ld.velX = 0;
                        ld.landed = true;
                    } else {
                        ld.velY = reboundVel; // bounce up with dampening
                    }
                }
            }

            if (ld.getPickupRect().intersects(ph)) {
                switch (ld.type) {
                    case COINS -> sessionCoins += ld.coinValue;
                    case POTION -> {
                        if (potion.count < Equipments.Potion.MAX_POTIONS)
                            potion.count++;
                    }
                    case HEALTH -> player.health = Math.min(100, player.health + 20);
                }
                it.remove();
            }
        }
    }

    private void drawLootDrops(Graphics2D g2) {
        final int SIZE = 34;
        for (LootDrop ld : lootDrops) {

            // ── Pop-in scale: 0 → 1 over first 10 ticks (overshoot to 1.15 → settle) ──
            float scale;
            if (ld.animTick < 6) {
                scale = ld.animTick / 6f;
            } else if (ld.animTick < 9) {
                scale = 1f + 0.15f * ((ld.animTick - 6) / 3f); // overshoot
            } else if (ld.animTick < 13) {
                scale = 1.15f - 0.15f * ((ld.animTick - 9) / 4f); // settle back
            } else {
                scale = 1f;
            }

            // ── Gentle bob after landing ───────────────────────────────────────
            float bob = ld.landed ? (float) Math.sin(ld.animTick * 0.08f) * 3.5f : 0f;

            // ── Squash-and-stretch: stretch while falling, squash on bounce ────
            float speedRatio = Math.abs(ld.velY) / 12f; // 0 at rest, ~1 at peak fall speed
            float scaleX = ld.landed ? 1f : Math.max(0.75f, 1f - speedRatio * 0.20f);
            float scaleY = ld.landed ? 1f : Math.min(1.30f, 1f + speedRatio * 0.30f);

            int renderY = (int) (ld.currentY + bob);
            int cx = ld.x;
            int drawSize = Math.max(1, (int) (SIZE * scale));
            int ix = cx - drawSize / 2;
            int iy = renderY - drawSize / 2;

            // ── Pulsing item glow — concentric halo rings around the item ──────
            {
                float glowPulse = 0.5f + 0.5f * (float) Math.sin(ld.animTick * 0.10f);
                Color glowColor;
                switch (ld.type) {
                    case COINS -> glowColor = new Color(255, 200, 0);
                    case POTION -> glowColor = new Color(180, 60, 220);
                    default -> glowColor = new Color(220, 50, 50);
                }
                // Draw concentric oval rings from largest (most transparent) inward
                int rings = 7;
                for (int r = rings; r >= 1; r--) {
                    float t = r / (float) rings;
                    float size = drawSize * (1f + t * 0.9f * (0.8f + 0.2f * glowPulse));
                    int alpha = (int) (140 * (1f - t) * (0.6f + 0.4f * glowPulse));
                    g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(),
                            glowColor.getBlue(), alpha));
                    int gs = (int) size;
                    g2.fillOval(cx - gs / 2, renderY - gs / 2, gs, gs);
                }

                // Floor drop shadow — grows closer to ground, disappears in air
                {
                    float heightAboveGround = ld.groundY - ld.currentY;
                    float shadowAlpha = Math.max(0f, 1f - heightAboveGround / 200f);
                    float shadowScale = Math.max(0.3f, 1f - heightAboveGround / 300f);
                    int shadowW = (int) (SIZE * 1.1f * shadowScale);
                    int shadowH = Math.max(2, (int) (7 * shadowScale));
                    g2.setColor(new Color(glowColor.getRed(), glowColor.getGreen(),
                            glowColor.getBlue(),
                            (int) ((50 + 30 * glowPulse) * shadowAlpha)));
                    g2.fillOval(ld.x - shadowW / 2, ld.groundY - shadowH / 2, shadowW, shadowH);
                }
            }

            java.awt.geom.AffineTransform oldXform = g2.getTransform();
            g2.translate(cx, renderY);
            g2.scale(scale * scaleX, scale * scaleY);
            g2.translate(-cx, -renderY);

            switch (ld.type) {
                case COINS -> {
                    if (lootCoinImg != null) {
                        g2.drawImage(lootCoinImg, ix, iy, drawSize, drawSize, null);
                    } else {
                        g2.setColor(new Color(255, 215, 0, 220));
                        g2.fillOval(cx - 8, renderY - 8, 16, 16);
                        g2.setColor(new Color(180, 130, 0));
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawOval(cx - 8, renderY - 8, 16, 16);
                        g2.setColor(new Color(255, 240, 100));
                        g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                        g2.drawString("$", cx - 3, renderY + 4);
                    }
                    if (ld.labelTimer > 0) {
                        float alpha = Math.min(1f, ld.labelTimer / 30f);
                        g2.setColor(new Color(255, 230, 50, (int) (alpha * 220)));
                        g2.setFont(LABEL_FONT);
                        g2.drawString("+" + ld.coinValue, cx - 8, iy - 4);
                    }
                }
                case POTION -> {
                    if (lootPotionImg != null) {
                        g2.drawImage(lootPotionImg, ix, iy, drawSize, drawSize, null);
                    } else {
                        g2.setColor(new Color(180, 60, 220, 210));
                        g2.fillRoundRect(cx - 6, renderY - 10, 12, 14, 4, 4);
                        g2.setColor(new Color(220, 130, 255));
                        g2.setStroke(new BasicStroke(1.5f));
                        g2.drawRoundRect(cx - 6, renderY - 10, 12, 14, 4, 4);
                        g2.setColor(new Color(255, 200, 255, 180));
                        g2.fillRoundRect(cx - 3, renderY - 8, 5, 5, 2, 2);
                    }
                    if (ld.labelTimer > 0) {
                        float alpha = Math.min(1f, ld.labelTimer / 30f);
                        g2.setColor(new Color(220, 150, 255, (int) (alpha * 220)));
                        g2.setFont(LABEL_FONT);
                        g2.drawString("POTION", cx - 16, iy - 4);
                    }
                }
                case HEALTH -> {
                    if (lootHealthImg != null) {
                        g2.drawImage(lootHealthImg, ix, iy, drawSize, drawSize, null);
                    } else {
                        g2.setColor(new Color(220, 50, 50, 210));
                        g2.fillOval(cx - 8, renderY - 8, 16, 16);
                        g2.setColor(new Color(255, 120, 120));
                        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
                        g2.drawString("+", cx - 3, renderY + 4);
                    }
                    if (ld.labelTimer > 0) {
                        float alpha = Math.min(1f, ld.labelTimer / 30f);
                        g2.setColor(new Color(255, 100, 100, (int) (alpha * 220)));
                        g2.setFont(LABEL_FONT);
                        g2.drawString("+HP", cx - 10, iy - 4);
                    }
                }
            }

            g2.setTransform(oldXform);
        }
    }

    private void updateEnemies() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        boolean grounded = player.onGround;

        if (player instanceof Mage m) {
            m.applySkillHits(bangis, aswang, aswang != null ? aswang.projectiles : new ArrayList<>());
            applyMageHitsOnVoodoos(m);
        }
        if (player instanceof Fighter f) {
            f.applySkillHits(bangis, aswang);
            applyFighterHitsOnVoodoos(f);
        }

        for (Bangis en : bangis) {
            en.update(player.x + Player.PS / 2, grounded);
            if (player.getClass() == Player.class && sword.active && en.isAlive() && en.getHitbox().intersects(sw))
                en.takeDamage(devMode ? 99999 : sword.getDamage());
            if (en.isAlive() && en.getHitbox().intersects(ph) && en.canAttack())
                applyDamageToPlayer(enemyDmgMult);
        }

        Iterator<Voodoo> vi = voodoos.iterator();
        while (vi.hasNext()) {
            Voodoo v = vi.next();
            // Allow sword to damage voodoo during chase/stop phases AND during boom
            if (player.getClass() == Player.class && sword.active && (v.isAlive() || v.isBooming()) && v.getHitbox().intersects(sw))
                v.takeDamage(devDmg(sword.getDamage()));
            boolean done = v.update(ph, grounded);
            // Apply explosion damage only if player is within the blast ring
            if (v.pendingContactDamage > 0 && v.isInBlastRing(ph)) {
                applyDamageToPlayer(v.pendingContactDamage * enemyDmgMult);
            }
            if (v.pendingContactDamage > 0) {
                // Award kill regardless of whether player was in range
                Rectangle vhb = v.getHitbox();
                awardMob(10, vhb.x + vhb.width / 2, vhb.y + vhb.height / 2);
                // pendingContactDamage stays set for one tick only; Voodoo is now booming
            }
            if (done) {
                vi.remove();
            }
        }
    }

    private void updateBats() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Bat b : bats) {
            if (player.getClass() == Player.class && sword.active && b.isAlive() && b.getHitbox().intersects(sw))
                b.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (b.isAlive() && b.getHitbox().intersects(bolt.getHitbox())) {
                        b.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && b.isAlive() && b.getHitbox().intersects(m.getBeamHitbox()))
                    b.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && b.isAlive() && b.getHitbox().intersects(f.getPunchHitbox()))
                    b.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(b.getHitbox());
                if (ppd > 0 && b.isAlive())
                    b.takeDamage(devDmg(ppd));
            }
            b.update(player.x, player.y);
            if (b.pendingDamage > 0 && b.getHitbox().intersects(ph))
                applyDamageToPlayer(b.pendingDamage * enemyDmgMult);
        }
    }

    private void updateTiyanaks() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Tiyanak t : tiyanaks) {
            if (player.getClass() == Player.class && sword.active && t.isAlive() && t.getHitbox().intersects(sw))
                t.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (t.isAlive() && t.getHitbox().intersects(bolt.getHitbox())) {
                        t.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && t.isAlive() && t.getHitbox().intersects(m.getBeamHitbox()))
                    t.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && t.isAlive() && t.getHitbox().intersects(f.getPunchHitbox()))
                    t.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(t.getHitbox());
                if (ppd > 0 && t.isAlive())
                    t.takeDamage(devDmg(ppd));
            }
            t.update(player.x, player.y);
            if (t.pendingDamage > 0 && t.getHitbox().intersects(ph))
                applyDamageToPlayer(t.pendingDamage * enemyDmgMult);
        }
    }

    private void updateShokoys() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Shokoy s : shokoys) {
            if (player.getClass() == Player.class && sword.active && s.isAlive() && s.getHitbox().intersects(sw))
                s.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (s.isAlive() && s.getHitbox().intersects(bolt.getHitbox())) {
                        s.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && s.isAlive() && s.getHitbox().intersects(m.getBeamHitbox()))
                    s.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && s.isAlive() && s.getHitbox().intersects(f.getPunchHitbox()))
                    s.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(s.getHitbox());
                if (ppd > 0 && s.isAlive())
                    s.takeDamage(devDmg(ppd));
            }
            s.update(player.x, player.y, ph);
            if (s.pendingMeleeDamage > 0 && s.getMeleeHitbox().intersects(ph))
                applyDamageToPlayer(s.pendingMeleeDamage * enemyDmgMult);
        }
    }

    private void updateDuwendes() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Duwende d : duwendes) {
            if (player.getClass() == Player.class && sword.active && d.isAlive() && d.getHitbox().intersects(sw))
                d.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (d.isAlive() && d.getHitbox().intersects(bolt.getHitbox())) {
                        d.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && d.isAlive() && d.getHitbox().intersects(m.getBeamHitbox()))
                    d.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && d.isAlive() && d.getHitbox().intersects(f.getPunchHitbox()))
                    d.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(d.getHitbox());
                if (ppd > 0 && d.isAlive())
                    d.takeDamage(devDmg(ppd));
            }
            d.update(player.x, player.y);
            if (d.pendingDamage > 0 && d.getHitbox().intersects(ph))
                applyDamageToPlayer(d.pendingDamage * enemyDmgMult);
        }
    }

    private void updateKapres() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Kapre k : kapres) {
            if (player.getClass() == Player.class && sword.active && k.isAlive() && k.getHitbox().intersects(sw))
                k.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (k.isAlive() && k.getHitbox().intersects(bolt.getHitbox())) {
                        k.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && k.isAlive() && k.getHitbox().intersects(m.getBeamHitbox()))
                    k.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && k.isAlive() && k.getHitbox().intersects(f.getPunchHitbox()))
                    k.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(k.getHitbox());
                if (ppd > 0 && k.isAlive())
                    k.takeDamage(devDmg(ppd));
            }
            k.update(player.x, player.y, ph);
            // Punch hit
            if (k.pendingPunchDamage > 0 && k.getPunchHitbox().intersects(ph)) {
                applyDamageToPlayer(k.pendingPunchDamage * enemyDmgMult);
                k.pendingPunchDamage = 0;
            }
            // Cigarette projectile hit + burn DoT trigger
            for (Kapre.Cigarette c : k.cigarettes)
                if (c.active && c.hitbox.intersects(ph)) {
                    applyDamageToPlayer(Kapre.CIGARETTE_DAMAGE * enemyDmgMult);
                    c.active = false;
                    if (k.pendingBurnApplied) {
                        kapreBurnTimer = KAPRE_BURN_DURATION;
                        kapreBurnTick = 0;
                        k.pendingBurnApplied = false;
                    }
                }
            // Status effects
            if (k.pendingConfusePlayer) {
                kapreConfuseTimer = 120;
                k.pendingConfusePlayer = false;
            }
            if (k.pendingSlow) {
                kapreSlowTimer = 180;
                k.pendingSlow = false;
            }
        }
    }

    private void updateSantelmos() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Santelmo sm : santelmos) {
            if (player.getClass() == Player.class && sword.active && sm.isAlive() && sm.getHitbox().intersects(sw))
                sm.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (sm.isAlive() && sm.getHitbox().intersects(bolt.getHitbox())) {
                        sm.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && sm.isAlive() && sm.getHitbox().intersects(m.getBeamHitbox()))
                    sm.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && sm.isAlive() && sm.getHitbox().intersects(f.getPunchHitbox()))
                    sm.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(sm.getHitbox());
                if (ppd > 0 && sm.isAlive())
                    sm.takeDamage(devDmg(ppd));
            }
            sm.update(player.x, player.y);
            if (sm.isAlive() && sm.getHitbox().intersects(ph))
                applyDamageToPlayer(enemyDmgMult);
            for (Santelmo.FireBall fb : sm.projectiles)
                if (fb.active && fb.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Santelmo.FireBall.DAMAGE * enemyDmgMult);
                    fb.active = false;
                }
        }
    }

    private void updateSkeletons() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Skeleton sk : skeletons) {
            if (player.getClass() == Player.class && sword.active && sk.isAlive() && sk.getHitbox().intersects(sw))
                sk.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (sk.isAlive() && sk.getHitbox().intersects(bolt.getHitbox())) {
                        sk.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && sk.isAlive() && sk.getHitbox().intersects(m.getBeamHitbox()))
                    sk.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && sk.isAlive() && sk.getHitbox().intersects(f.getPunchHitbox()))
                    sk.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(sk.getHitbox());
                if (ppd > 0 && sk.isAlive())
                    sk.takeDamage(devDmg(ppd));
            }
            sk.update(player.x, player.y, ph);
            if (sk.pendingMeleeDamage > 0 && sk.getHitbox().intersects(ph)) {
                applyDamageToPlayer(sk.pendingMeleeDamage * enemyDmgMult);
                sk.pendingMeleeDamage = 0;
            }
            for (Skeleton.BoneProjectile bp : sk.bones)
                if (bp.active && bp.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Skeleton.BoneProjectile.DAMAGE * enemyDmgMult);
                    bp.active = false;
                }
        }
    }

    private void updateAswangMobs() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Aswang_Mob am : aswangMobs) {
            if (player.getClass() == Player.class && sword.active && am.isAlive() && am.getHitbox().intersects(sw))
                am.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (am.isAlive() && am.getHitbox().intersects(bolt.getHitbox())) {
                        am.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && am.isAlive() && am.getHitbox().intersects(m.getBeamHitbox()))
                    am.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && am.isAlive() && am.getHitbox().intersects(f.getPunchHitbox()))
                    am.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(am.getHitbox());
                if (ppd > 0 && am.isAlive())
                    am.takeDamage(devDmg(ppd));
            }
            am.update(player.x, player.y, ph);
            if (am.pendingMeleeDamage > 0) {
                applyDamageToPlayer(am.pendingMeleeDamage * enemyDmgMult);
                am.pendingMeleeDamage = 0;
            }
            for (Aswang_Mob.AswangProjectile p : am.projectiles)
                if (p.active && p.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Aswang.AswangProjectile.DAMAGE * enemyDmgMult);
                    p.active = false;
                }
        }
    }

    // ── Sirena mob update ─────────────────────────────────────────────────────
    private void updateSirenas() {
        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();
        for (Sirena sr : sirenas) {
            // Player weapon hits
            if (player.getClass() == Player.class && sword.active && sr.isAlive() && sr.getHitbox().intersects(sw))
                sr.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (sr.isAlive() && sr.getHitbox().intersects(bolt.getHitbox())) {
                        sr.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                        break;
                    }
                }
                if (m.beamActive && sr.isAlive() && sr.getHitbox().intersects(m.getBeamHitbox()))
                    sr.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && sr.isAlive() && sr.getHitbox().intersects(f.getPunchHitbox()))
                    sr.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(sr.getHitbox());
                if (ppd > 0 && sr.isAlive())
                    sr.takeDamage(devDmg(ppd));
            }
            sr.update(player.x, player.y, ph);
            // Beam hit per-tick damage
            if (sr.pendingBeamDamage > 0) {
                applyDamageToPlayer(sr.pendingBeamDamage * enemyDmgMult);
                sr.pendingBeamDamage = 0;
            }
        }
    }

    private void applyMageHitsOnVoodoos(Mage m) {
        for (java.util.Iterator<Mage.MageBolt> it2 = m.bolts.iterator(); it2.hasNext();) {
            Mage.MageBolt bolt = it2.next();
            for (Voodoo v : voodoos) {
                if ((v.isAlive() || v.isBooming()) && v.getHitbox().intersects(bolt.getHitbox())) {
                    v.takeDamage(devDmg(m.getBoltDamage()));
                    bolt.active = false;
                    break;
                }
            }
            if (!bolt.active)
                it2.remove();
        }
        if (m.beamActive) {
            Rectangle bh = m.getBeamHitbox();
            for (Voodoo v : voodoos)
                if ((v.isAlive() || v.isBooming()) && v.getHitbox().intersects(bh))
                    v.takeDamage(devDmg(m.getBeamDamage()));
        }
    }

    private void applyFighterHitsOnVoodoos(Fighter f) {
        if (f.punchActive)
            for (Voodoo v : voodoos)
                if ((v.isAlive() || v.isBooming()) && v.getHitbox().intersects(f.getPunchHitbox()))
                    v.takeDamage(devDmg(f.getPunchDamage()));
        for (java.util.Iterator<Fighter.PowerPunch> pit = f.powerPunches.iterator(); pit.hasNext();) {
            Fighter.PowerPunch pp = pit.next();
            boolean hit = false;
            for (Voodoo v : voodoos)
                if ((v.isAlive() || v.isBooming()) && v.getHitbox().intersects(pp.getHitbox())) {
                    v.takeDamage(devDmg(f.getPowerPunchDamage()));
                    hit = true;
                    break;
                }
            if (hit) {
                pp.active = false;
                pit.remove();
            }
        }
    }

    private void updateBoss() {
        if (bossIntroActive) {
            if (++bossIntroTimer >= BOSS_INTRO_TICKS) {
                bossIntroActive = false;
                int bx = switch (chapter) {
                    case 0 -> Chapter1.bossSpawnX(BASE_W);
                    case 1 -> Chapter2.bossSpawnX(BASE_W);
                    case 2 -> Chapter3.bossSpawnX(BASE_W);
                    case 3 -> Chapter4.bossSpawnX(BASE_W);
                    case 4 -> Chapter5.bossSpawnX(BASE_W);
                    case 5 -> Chapter6.bossSpawnX(BASE_W);
                    case 6 -> Chapter7.bossSpawnX(BASE_W);
                    case 7 -> Chapter8.bossSpawnX(BASE_W);
                    default -> Chapter9.bossSpawnX(BASE_W);
                };
                if (chapter == 0) {
                    aswang = new Aswang(bx, groundY() - Aswang.HEIGHT);
                } else if (chapter == 1) {
                    mangkukulam = new Mangkukulam(bx, groundY() - Mangkukulam.HEIGHT);
                } else if (chapter == 2) {
                    severino = new Severino(bx, groundY() - Severino.HEIGHT);
                } else if (chapter == 3) {
                    sigbin = new Sigbin(bx, groundY() - Sigbin.HEIGHT);
                } else if (chapter == 4) {
                    bakunawa = new Bakunawa(bx, groundY(), BASE_W);
                    bakunawa.loadAssets();
                } else if (chapter == 5) {
                    tikbalang = new Tikbalang(bx, groundY() - Tikbalang.HEIGHT);
                } else if (chapter == 6) {
                    kapreBoss = new Kapre(bx, groundY() - Kapre.HEIGHT);
                } else if (chapter == 7) {
                    amomongo = new Amomongo(bx, groundY() - Amomongo.HEIGHT);
                } else {
                    sitan = new Sitan(bx, groundY());
                }
            }
            return;
        }

        Rectangle ph = player.getHitbox();
        Rectangle sw = getSwordHitbox();

        if (aswang != null && aswang.isAlive()) {
            aswang.update(player.x, player.y, ph);
            if (player.getClass() == Player.class && sword.active && aswang.getHitbox().intersects(sw))
                aswang.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m)
                applyMageHitOnBoss(m, aswang, null, null, null);
            if (player instanceof Fighter f)
                applyFighterHitOnBoss(f, aswang, null, null, null);
            if (aswang.pendingMeleeDamage > 0) {
                applyDamageToPlayer(aswang.pendingMeleeDamage * enemyDmgMult);
                aswang.pendingMeleeDamage = 0;
            }
            for (Aswang.AswangProjectile p : aswang.projectiles)
                if (p.active && p.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Aswang.AswangProjectile.DAMAGE * enemyDmgMult);
                    p.active = false;
                }
        }

        if (mangkukulam != null && mangkukulam.isAlive()) {
            mangkukulam.update(player.x, player.y, ph);
            if (player.getClass() == Player.class && sword.active && mangkukulam.getHitbox().intersects(sw))
                mangkukulam.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m)
                applyMageHitOnBoss(m, null, mangkukulam, null, null);
            if (player instanceof Fighter f)
                applyFighterHitOnBoss(f, null, mangkukulam, null, null);
            if (mangkukulam.pendingMeleeDamage > 0) {
                applyDamageToPlayer(mangkukulam.pendingMeleeDamage * enemyDmgMult);
                mangkukulam.pendingMeleeDamage = 0;
            }
            for (Mangkukulam.CurseOrb o : mangkukulam.projectiles)
                if (o.active && o.getHitbox().intersects(ph)) {
                    curseDotTimer = Mangkukulam.CurseOrb.DOT_DURATION_TICKS;
                    curseTickCounter = 0;
                    o.active = false;
                }
            for (Mangkukulam.GrabProjectile g : mangkukulam.grabs)
                if (g.active && g.outgoing && g.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Mangkukulam.GrabProjectile.DAMAGE * enemyDmgMult);
                    g.outgoing = false;
                }
        }

        if (severino != null && severino.isAlive()) {
            severino.update(player.x, player.y, ph);
            if (player.getClass() == Player.class && sword.active && severino.getHitbox().intersects(sw))
                severino.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m)
                applyMageHitOnBoss(m, null, mangkukulam, severino, null);
            if (player instanceof Fighter f)
                applyFighterHitOnBoss(f, null, mangkukulam, severino, null);
            if (severino.pendingMeleeDamage > 0) {
                applyDamageToPlayer(severino.pendingMeleeDamage * enemyDmgMult);
                severino.pendingMeleeDamage = 0;
            }
            if (severino.pendingDashDamage) {
                applyDamageToPlayer(Severino.DASH_DAMAGE * enemyDmgMult);
                severino.pendingDashDamage = false;
            }
            for (Severino.ClawProjectile c : severino.claws)
                if (c.active && c.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Severino.ClawProjectile.DAMAGE * enemyDmgMult);
                    c.active = false;
                }
        }

        if (sigbin != null && sigbin.isAlive()) {
            sigbin.update(player.x, player.y, ph);
            if (player.getClass() == Player.class && sword.active && sigbin.getHitbox().intersects(sw))
                sigbin.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m)
                applyMageHitOnBoss(m, null, null, null, sigbin);
            if (player instanceof Fighter f)
                applyFighterHitOnBoss(f, null, null, null, sigbin);
            if (sigbin.pendingMeleeDamage > 0) {
                applyDamageToPlayer(sigbin.pendingMeleeDamage * enemyDmgMult);
                sigbin.pendingMeleeDamage = 0;
            }
            if (sigbin.pendingDashDamage) {
                applyDamageToPlayer(sigbin.DASH_DAMAGE * enemyDmgMult);
                sigbin.pendingDashDamage = false;
            }
            for (Sigbin.StraightProjectile p : sigbin.projectiles)
                if (p.active && p.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Sigbin.StraightProjectile.DAMAGE * enemyDmgMult);
                    p.active = false;
                }
        }

        if (bakunawa != null && bakunawa.alive) {
            bakunawa.update(player.x, player.y);
            // Sword hit
            if (player.getClass() == Player.class && sword.active && bakunawa.getHitbox().intersects(sw))
                bakunawa.takeDamage(devDmg(sword.getDamage()));
            // Mage hits
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (bakunawa.alive && bakunawa.getHitbox().intersects(bolt.getHitbox())) {
                        bakunawa.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                    }
                }
                if (m.beamActive && bakunawa.alive && bakunawa.getHitbox().intersects(m.getBeamHitbox()))
                    bakunawa.takeDamage(devDmg(m.getBeamDamage()));
            }
            // Fighter hits
            if (player instanceof Fighter f) {
                if (f.punchActive && bakunawa.alive && bakunawa.getHitbox().intersects(f.getPunchHitbox()))
                    bakunawa.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(bakunawa.getHitbox());
                if (ppd > 0 && bakunawa.alive)
                    bakunawa.takeDamage(devDmg(ppd));
            }
            // Arrow hits
            for (java.util.Iterator<Equipments.Arrow> ait = arrows.iterator(); ait.hasNext();) {
                Equipments.Arrow a = ait.next();
                if (a.getHitbox().intersects(bakunawa.getHitbox())) {
                    bakunawa.takeDamage(devDmg(bow.getDamage()));
                    ait.remove();
                }
            }
            // Beam attack damages player
            if (bakunawa.beamActive && bakunawa.getBeamHitbox().intersects(ph))
                applyDamageToPlayer(Bakunawa.BEAM_DAMAGE * enemyDmgMult);
            // Slam attack damages player (boss lands on player position)
            if (bakunawa.state == Bakunawa.State.SLAM && bakunawa.getHitbox().intersects(ph))
                applyDamageToPlayer(Bakunawa.SLAM_DAMAGE * enemyDmgMult);
        }

        if (tikbalang != null && tikbalang.isAlive()) {
            tikbalang.update(player.x, player.y, ph);
            if (player.getClass() == Player.class && sword.active && tikbalang.getHitbox().intersects(sw))
                tikbalang.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (tikbalang.isAlive() && tikbalang.getHitbox().intersects(bolt.getHitbox())) {
                        tikbalang.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                    }
                }
                if (m.beamActive && tikbalang.isAlive() && tikbalang.getHitbox().intersects(m.getBeamHitbox()))
                    tikbalang.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && tikbalang.isAlive() && tikbalang.getHitbox().intersects(f.getPunchHitbox()))
                    tikbalang.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(tikbalang.getHitbox());
                if (ppd > 0 && tikbalang.isAlive())
                    tikbalang.takeDamage(devDmg(ppd));
            }
            if (tikbalang.pendingChargeDamage > 0) {
                applyDamageToPlayer(tikbalang.pendingChargeDamage * enemyDmgMult);
                tikbalang.pendingChargeDamage = 0;
                if (tikbalang.pendingKnockup) {
                    player.applyKnockup();
                    tikbalang.pendingKnockup = false;
                }
            }
            if (tikbalang.pendingStompDamage > 0) {
                applyDamageToPlayer(tikbalang.pendingStompDamage * enemyDmgMult);
                tikbalang.pendingStompDamage = 0;
            }
            if (tikbalang.pendingJumpDamage > 0) {
                applyDamageToPlayer(tikbalang.pendingJumpDamage * enemyDmgMult);
                tikbalang.pendingJumpDamage = 0;
            }
            if (tikbalang.pendingBiteDamage > 0) {
                applyDamageToPlayer(tikbalang.pendingBiteDamage * enemyDmgMult);
                tikbalang.pendingBiteDamage = 0;
            }
            if (tikbalang.pendingDashDamage > 0) {
                applyDamageToPlayer(tikbalang.pendingDashDamage * enemyDmgMult);
                tikbalang.pendingDashDamage = 0;
            }
        }

        if (kapreBoss != null && kapreBoss.isAlive()) {
            kapreBoss.update(player.x, player.y, ph);
            if (player.getClass() == Player.class && sword.active && kapreBoss.getHitbox().intersects(sw))
                kapreBoss.takeDamage(devDmg(sword.getDamage()));
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (kapreBoss.isAlive() && kapreBoss.getHitbox().intersects(bolt.getHitbox())) {
                        kapreBoss.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                    }
                }
                if (m.beamActive && kapreBoss.isAlive() && kapreBoss.getHitbox().intersects(m.getBeamHitbox()))
                    kapreBoss.takeDamage(devDmg(m.getBeamDamage()));
            }
            if (player instanceof Fighter f) {
                if (f.punchActive && kapreBoss.isAlive() && kapreBoss.getHitbox().intersects(f.getPunchHitbox()))
                    kapreBoss.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(kapreBoss.getHitbox());
                if (ppd > 0 && kapreBoss.isAlive())
                    kapreBoss.takeDamage(devDmg(ppd));
            }
            if (kapreBoss.pendingPunchDamage > 0 && kapreBoss.getPunchHitbox().intersects(ph)) {
                applyDamageToPlayer(kapreBoss.pendingPunchDamage * enemyDmgMult);
                kapreBoss.pendingPunchDamage = 0;
            }
            for (Kapre.Cigarette c : kapreBoss.cigarettes)
                if (c.active && c.hitbox.intersects(ph)) {
                    applyDamageToPlayer(Kapre.CIGARETTE_DAMAGE * enemyDmgMult);
                    c.active = false;
                    if (kapreBoss.pendingBurnApplied) {
                        kapreBurnTimer = KAPRE_BURN_DURATION;
                        kapreBurnTick = 0;
                        kapreBoss.pendingBurnApplied = false;
                    }
                }
            if (kapreBoss.pendingConfusePlayer) {
                kapreConfuseTimer = 120;
                kapreBoss.pendingConfusePlayer = false;
            }
            if (kapreBoss.pendingSlow) {
                kapreSlowTimer = 180;
                kapreBoss.pendingSlow = false;
            }
        }

        if (amomongo != null && amomongo.isAlive()) {
            amomongo.update(player.x, player.y, ph);
            // Sword
            if (player.getClass() == Player.class && sword.active && amomongo.getHitbox().intersects(sw))
                amomongo.takeDamage(devDmg(sword.getDamage()));
            // Mage
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (amomongo.isAlive() && amomongo.getHitbox().intersects(bolt.getHitbox())) {
                        amomongo.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                    }
                }
                if (m.beamActive && amomongo.isAlive() && amomongo.getHitbox().intersects(m.getBeamHitbox()))
                    amomongo.takeDamage(devDmg(m.getBeamDamage()));
            }
            // Fighter
            if (player instanceof Fighter f) {
                if (f.punchActive && amomongo.isAlive() && amomongo.getHitbox().intersects(f.getPunchHitbox()))
                    amomongo.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(amomongo.getHitbox());
                if (ppd > 0 && amomongo.isAlive())
                    amomongo.takeDamage(devDmg(ppd));
            }
            if (amomongo.pendingSlashDamage > 0) {
                applyDamageToPlayer(amomongo.pendingSlashDamage * enemyDmgMult);
                amomongo.pendingSlashDamage = 0;
            }
            if (amomongo.pendingHugDamage > 0 && amomongo.getHitbox().intersects(ph)) {
                applyDamageToPlayer(amomongo.pendingHugDamage * enemyDmgMult);
                amomongo.pendingHugDamage = 0;
            }
            if (amomongo.pendingLandingDamage > 0) {
                applyDamageToPlayer(amomongo.pendingLandingDamage * enemyDmgMult);
                amomongo.pendingLandingDamage = 0;
            }
            for (Amomongo.Boulder b : amomongo.boulders)
                if (b.active && b.hitbox.intersects(ph)) {
                    applyDamageToPlayer(Amomongo.BOULDER_DAMAGE * enemyDmgMult);
                    b.active = false;
                }
            for (Amomongo.DebrisRock d : amomongo.debrisRocks)
                if (d.active && d.hitbox.intersects(ph)) {
                    applyDamageToPlayer(Amomongo.DEBRIS_DAMAGE * enemyDmgMult);
                    d.active = false;
                }
        }

        if (sitan != null && sitan.isAlive()) {
            sitan.update(player.x, player.y, ph);
            // Sword
            if (player.getClass() == Player.class && sword.active && sitan.getHitbox().intersects(sw))
                sitan.takeDamage(devDmg(sword.getDamage()));
            // Mage
            if (player instanceof Mage m) {
                for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
                    Mage.MageBolt bolt = it.next();
                    if (sitan.isAlive() && sitan.getHitbox().intersects(bolt.getHitbox())) {
                        sitan.takeDamage(devDmg(m.getBoltDamage()));
                        bolt.active = false;
                        it.remove();
                    }
                }
                if (m.beamActive && sitan.isAlive() && sitan.getHitbox().intersects(m.getBeamHitbox()))
                    sitan.takeDamage(devDmg(m.getBeamDamage()));
            }
            // Fighter
            if (player instanceof Fighter f) {
                if (f.punchActive && sitan.isAlive() && sitan.getHitbox().intersects(f.getPunchHitbox()))
                    sitan.takeDamage(devDmg(f.getPunchDamage()));
                int ppd = f.consumePowerPunchHit(sitan.getHitbox());
                if (ppd > 0 && sitan.isAlive())
                    sitan.takeDamage(devDmg(ppd));
            }

            if (sitan.pendingSwingDamage > 0) {
                applyDamageToPlayer(sitan.pendingSwingDamage * enemyDmgMult);
                sitan.pendingSwingDamage = 0;
            }
            if (sitan.pendingSlamDamage > 0) {
                applyDamageToPlayer(sitan.pendingSlamDamage * enemyDmgMult);
                sitan.pendingSlamDamage = 0;
            }
            if (sitan.pendingBeamDamage > 0) {
                applyDamageToPlayer(sitan.pendingBeamDamage * enemyDmgMult);
                sitan.pendingBeamDamage = 0;
            }
            sitan.violetOrbs.removeIf(v -> !v.active);
            for (Sitan.VioletOrb v : sitan.violetOrbs) {
                v.update(player.x, player.y);
                if (v.active && v.getHitbox().intersects(ph)) {
                    applyDamageToPlayer(Sitan.VioletOrb.DAMAGE * enemyDmgMult);
                    v.active = false;
                }
            }
            sitan.firePatches.removeIf(fp -> !fp.active);
            for (Sitan.FirePatch fp : sitan.firePatches) {
                fp.update();
                if (fp.pendingDamage > 0 && fp.getHitbox().intersects(ph))
                    applyDamageToPlayer(Sitan.FirePatch.TICK_DAMAGE * enemyDmgMult);
            }
            if (sitan.pendingSummon) {
                sitan.pendingSummon = false;
                for (int i = 0; i < 5; i++) {
                    int bx2 = sitan.x + (i % 2 == 0 ? -60 - i * 30 : 60 + i * 30);
                    int dir2 = (bx2 < player.x) ? 1 : -1; // face toward player
                    bangis.add(new Bangis(bx2, player.y + Player.PS / 2, enemySpeedMult, dir2));
                }
            }
        }
    }

    private void applyMageHitOnBoss(Mage m, Aswang a, Mangkukulam mk, Severino sv, Sigbin tb) {
        for (java.util.Iterator<Mage.MageBolt> it = m.bolts.iterator(); it.hasNext();) {
            Mage.MageBolt b = it.next();
            if (a != null && a.isAlive() && a.getHitbox().intersects(b.getHitbox())) {
                a.takeDamage(devDmg(m.getBoltDamage()));
                b.active = false;
                it.remove();
                continue;
            }
            if (mk != null && mk.isAlive() && mk.getHitbox().intersects(b.getHitbox())) {
                mk.takeDamage(devDmg(m.getBoltDamage()));
                b.active = false;
                it.remove();
                continue;
            }
            if (sv != null && sv.isAlive() && sv.getHitbox().intersects(b.getHitbox())) {
                sv.takeDamage(devDmg(m.getBoltDamage()));
                b.active = false;
                it.remove();
                continue;
            }
            if (tb != null && tb.isAlive() && tb.getHitbox().intersects(b.getHitbox())) {
                tb.takeDamage(devDmg(m.getBoltDamage()));
                b.active = false;
                it.remove();
            }
        }
        if (m.beamActive) {
            Rectangle bh = m.getBeamHitbox();
            if (a != null && a.isAlive() && a.getHitbox().intersects(bh))
                a.takeDamage(devDmg(m.getBeamDamage()));
            if (mk != null && mk.isAlive() && mk.getHitbox().intersects(bh))
                mk.takeDamage(devDmg(m.getBeamDamage()));
            if (sv != null && sv.isAlive() && sv.getHitbox().intersects(bh))
                sv.takeDamage(devDmg(m.getBeamDamage()));
            if (tb != null && tb.isAlive() && tb.getHitbox().intersects(bh))
                tb.takeDamage(devDmg(m.getBeamDamage()));
        }
    }

    private void applyFighterHitOnBoss(Fighter f, Aswang a, Mangkukulam mk, Severino sv, Sigbin tb) {
        Rectangle ph2 = f.getPunchHitbox();
        if (f.punchActive) {
            if (a != null && a.isAlive() && a.getHitbox().intersects(ph2))
                a.takeDamage(devDmg(f.getPunchDamage()));
            if (mk != null && mk.isAlive() && mk.getHitbox().intersects(ph2))
                mk.takeDamage(devDmg(f.getPunchDamage()));
            if (sv != null && sv.isAlive() && sv.getHitbox().intersects(ph2))
                sv.takeDamage(devDmg(f.getPunchDamage()));
            if (tb != null && tb.isAlive() && tb.getHitbox().intersects(ph2))
                tb.takeDamage(devDmg(f.getPunchDamage()));
        }
        for (java.util.Iterator<Fighter.PowerPunch> pit = f.powerPunches.iterator(); pit.hasNext();) {
            Fighter.PowerPunch pp = pit.next();
            boolean hit = false;
            if (!hit && a != null && a.isAlive() && a.getHitbox().intersects(pp.getHitbox())) {
                a.takeDamage(devDmg(f.getPowerPunchDamage()));
                hit = true;
            }
            if (!hit && mk != null && mk.isAlive() && mk.getHitbox().intersects(pp.getHitbox())) {
                mk.takeDamage(devDmg(f.getPowerPunchDamage()));
                hit = true;
            }
            if (!hit && sv != null && sv.isAlive() && sv.getHitbox().intersects(pp.getHitbox())) {
                sv.takeDamage(devDmg(f.getPowerPunchDamage()));
                hit = true;
            }
            if (!hit && tb != null && tb.isAlive() && tb.getHitbox().intersects(pp.getHitbox())) {
                tb.takeDamage(devDmg(f.getPowerPunchDamage()));
                hit = true;
            }
            if (hit) {
                pp.active = false;
                pit.remove();
            }
        }
    }

    private Rectangle getSwordHitbox() {
        boolean duck = player.duck && player.onGround;

        int width = 50;
        int height = duck ? Player.DUCK_HEIGHT : Player.PS / 2;

        int sx = player.facingLeft
                ? player.x - width + 10 // overlap inward
                : player.x + Player.PS - 10;

        int sy = duck
                ? player.y + Player.PS - Player.DUCK_HEIGHT
                : player.y + Player.PS / 4; // center vertically

        return new Rectangle(sx, sy, width, height);
    }

    private void applyDamageToPlayer(int rawAmount) {
        if (devMode) {
            // In dev mode the player can be hit (flash + animation) but takes no damage
            player.hitFlashTimer = 12;
            return;
        }
        if (player.immunityActive)
            return; // immunity skill active — block all damage
        if (rawAmount <= 0)
            return;
        int amount = maxhp.mitigate(rawAmount);
        // Fighter has slightly higher natural defense — absorbs ~15% of incoming damage.
        // Use Math.max(1, ...) to prevent integer truncation giving the Fighter full immunity
        // on Easy mode (where some enemy base damage values are as low as 1).
        if (player instanceof Fighter)
            amount = Math.max(1, (int) (amount * 0.85f));
        player.health = Math.max(0, player.health - amount);
        player.hitFlashTimer = 12;

        // ── Hit sound (500 ms cooldown prevents stacking when hit by multiple enemies) ──
        if (player instanceof Mage)
            SoundManager.playCooldown("sounds/player/mageH.wav", 500);
        else
            SoundManager.playCooldown("sounds/player/manH.wav", 500);

        if (player.health <= 0 && !player.deathAnimPlaying && !gameOver) {
            player.deathAnimPlaying = true;
            player.deathFrame = 0;
            player.deathFrameTimer = 0;
        }
    }

    private boolean anyBossAlive() {
        return (aswang != null && aswang.isAlive())
                || (mangkukulam != null && mangkukulam.isAlive())
                || (severino != null && severino.isAlive())
                || (sigbin != null && sigbin.isAlive())
                || (bakunawa != null && bakunawa.alive)
                || (tikbalang != null && tikbalang.isAlive())
                || (kapreBoss != null && kapreBoss.isAlive())
                || (amomongo != null && amomongo.isAlive())
                || (sitan != null && sitan.isAlive());
    }

    private float bosshp() {
        if (aswang != null && aswang.isAlive())
            return aswang.getHealthRatio();
        if (mangkukulam != null && mangkukulam.isAlive())
            return mangkukulam.getHealthRatio();
        if (severino != null && severino.isAlive())
            return severino.getHealthRatio();
        if (sigbin != null && sigbin.isAlive())
            return sigbin.getHealthRatio();
        if (bakunawa != null && bakunawa.alive)
            return (float) bakunawa.health / bakunawa.maxHealth;
        if (tikbalang != null && tikbalang.isAlive())
            return tikbalang.getHealthRatio();
        if (kapreBoss != null && kapreBoss.isAlive())
            return kapreBoss.getHealthRatio();
        if (amomongo != null && amomongo.isAlive())
            return amomongo.getHealthRatio();
        if (sitan != null && sitan.isAlive())
            return sitan.getHealthRatio();
        return 0;
    }

    private String bossName() {
        return switch (chapter) {
            case 0 -> "ASWANG";
            case 1 -> "MANGKUKULAM";
            case 2 -> "SEVERINO";
            case 3 -> "SIGBIN";
            case 4 -> "BAKUNAWA";
            case 5 -> "TIKBALANG";
            case 6 -> "KAPRE";
            case 7 -> "AMOMONGO";
            default -> "SITAN";
        };
    }

    private void checkLevelProgress() {
        // ── Dev mode: reaching the right border instantly skips the level ─────
        if (devMode && player.x >= BASE_W - Player.PS - 10) {
            int maxLevels = WAVES_PER_LEVEL[chapter].length;
            if (level == maxLevels)
                advanceChapter();
            else {
                level++;
                wave = 1;
                levelClear = false;
                bossDefeated = false;
                player.x = 0;
                setupLevel();
            }
            return;
        }

        // ── Normal levels: wait for all enemies/spawns to clear, then next wave ──
        if (!isBossLevel()) {
            if (!spawnQueue.isEmpty() || !bangis.isEmpty() || !voodoos.isEmpty()
                    || !bats.isEmpty() || !tiyanaks.isEmpty() || !shokoys.isEmpty()
                    || !duwendes.isEmpty() || !kapres.isEmpty() || !santelmos.isEmpty()
                    || !skeletons.isEmpty() || !aswangMobs.isEmpty()
                    || !sirenas.isEmpty())
                return;

            // On levels 1 and 2, advance through sequential mob groups before
            // counting the wave as complete.
            if (level <= 2) {
                int poolSize = getChapterPoolSize();
                int nextGroup = seqGroupIndex + 1;
                if (nextGroup < poolSize) {
                    // Queue the next mob type group (still the same wave number)
                    seqGroupIndex = nextGroup;
                    int n = getWaveSize();
                    queueSequentialGroup(seqGroupIndex, n, BASE_W, groundY());
                    spawnTick = 3;
                    return;
                }
                // All groups cleared — wave is now truly done
            }

            int maxWaves = WAVES_PER_LEVEL[chapter][level - 1];
            if (wave < maxWaves) {
                wave++;
                queueWave();
                return;
            }
            levelClear = true;
        }

        // ── Player must walk to the left border to advance after level/boss is done ──
        boolean done = isBossLevel() ? bossDefeated : levelClear;
        if (!done)
            return;

        // Only advance once the player reaches the right edge of the screen
        if (player.x < BASE_W - Player.PS - 10)
            return;

        int maxLevels = WAVES_PER_LEVEL[chapter].length;
        if (level == maxLevels)
            advanceChapter();
        else {
            level++;
            wave = 1;
            levelClear = false;
            player.x = 0;
            setupLevel();
        }
    }

    private void checkBounds() {
        // Hard walls — player cannot cross either edge of the screen
        if (player.x < 0)
            player.x = 0;
        if (player.x > BASE_W - Player.PS)
            player.x = BASE_W - Player.PS;
    }

    private void advanceChapter() {
        chapter++;
        if (chapter >= CHAPTER_BG.length) {
            restartFromChapter1();
            return;
        }
        level = 1;
        wave = 1;
        player.x = 0;
        bossDefeated = false;
        assets.loadAssets(null, CHAPTER_BG[chapter]);
        setupLevel();
    }

    private void restartFromChapter1() {
        chapter = 0;
        level = 1;
        wave = 1;
        player.x = 0;
        bossDefeated = false;
        levelClear = false;
        assets.loadAssets(charType, CHAPTER_BG[chapter]);
        setupLevel();
    }

    private void checkGameOver() {
        if (player.deathAnimPlaying && player.deathFrame >= 2 && !gameOver) {
            gameOver = true;
            pauseBgMusic();                    // freeze background music
            playerData.addAttempt(sessionScore, sessionCoins, level, settings.difficulty);
            // Play game-over sting BEFORE muting all SFX so it isn't suppressed
            SoundManager.play("sounds/system/Gameover.wav");
            SoundManager.setGamePaused(true);  // stop all in-flight enemy / player SFX
        }
    }

    private void updateEnemySpawn() {
        if (spawnQueue.isEmpty())
            return;
        if (++spawnTick >= SPAWN_INTERVAL) {
            Object next = spawnQueue.pollFirst();
            if (next instanceof Bangis e)
                bangis.add(e);
            else if (next instanceof Voodoo v)
                voodoos.add(v);
            else if (next instanceof Bat b)
                bats.add(b);
            else if (next instanceof Tiyanak t)
                tiyanaks.add(t);
            else if (next instanceof Shokoy s)
                shokoys.add(s);
            else if (next instanceof Duwende d)
                duwendes.add(d);
            else if (next instanceof Kapre k)
                kapres.add(k);
            else if (next instanceof Santelmo sm)
                santelmos.add(sm);
            else if (next instanceof Skeleton sk)
                skeletons.add(sk);
            else if (next instanceof Aswang_Mob am)
                aswangMobs.add(am);
            else if (next instanceof Sirena sr)
                sirenas.add(sr);
            spawnTick = 0;
        }
    }

    private java.awt.image.BufferedImage canvas;

    @Override
    protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        int SW = getWidth(), SH = getHeight();

        if (canvas == null || canvas.getWidth() != BASE_W || canvas.getHeight() != BASE_H)
            canvas = new java.awt.image.BufferedImage(BASE_W, BASE_H,
                    java.awt.image.BufferedImage.TYPE_INT_RGB);
        Graphics2D g2 = canvas.createGraphics();
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        drawBackground(g2);

        for (Bangis e : bangis)
            e.draw(g2);
        for (Voodoo v : voodoos)
            v.draw(g2);
        for (Bat b : bats)
            b.draw(g2);
        for (Tiyanak t : tiyanaks)
            t.draw(g2);
        for (Shokoy s : shokoys)
            s.draw(g2);
        for (Duwende d : duwendes)
            d.draw(g2);
        for (Kapre k : kapres)
            k.draw(g2);
        for (Santelmo sm : santelmos)
            sm.draw(g2);
        for (Skeleton sk : skeletons)
            sk.draw(g2);
        for (Aswang_Mob am : aswangMobs)
            am.draw(g2);
        for (Sirena sr : sirenas)
            sr.draw(g2);
        if (aswang != null && aswang.isAlive())
            aswang.draw(g2);
        if (mangkukulam != null && mangkukulam.isAlive())
            mangkukulam.draw(g2);
        if (severino != null && severino.isAlive())
            severino.draw(g2);
        if (sigbin != null && sigbin.isAlive())
            sigbin.draw(g2);
        if (bakunawa != null && bakunawa.alive)
            bakunawa.draw(g2);
        if (tikbalang != null && tikbalang.isAlive())
            tikbalang.draw(g2);
        if (kapreBoss != null && kapreBoss.isAlive())
            kapreBoss.draw(g2);
        if (amomongo != null && amomongo.isAlive())
            amomongo.draw(g2);
        if (sitan != null && sitan.isAlive())
            sitan.draw(g2);

        for (Bangis e : bangis)
            if (e.isAlive())
                drawMobLabel(g2, e.getHitbox(), assets.bangisImg);
        for (Voodoo v : voodoos)
            if (v.isAlive() || v.isBooming())
                drawMobLabel(g2, v.getHitbox(), assets.voodooImg);
        for (Bat b : bats)
            if (b.isAlive())
                drawMobLabel(g2, b.getHitbox(), assets.batImg);
        for (Tiyanak t : tiyanaks)
            if (t.isAlive())
                drawMobLabel(g2, t.getHitbox(), assets.tiyanakImg);
        for (Shokoy s : shokoys)
            if (s.isAlive())
                drawMobLabel(g2, s.getHitbox(), assets.shokoyImg);
        for (Duwende d : duwendes)
            if (d.isAlive())
                drawMobLabel(g2, d.getHitbox(), assets.duwendeImg);
        for (Kapre k : kapres)
            if (k.isAlive())
                drawMobLabel(g2, k.getHitbox(), assets.kapreImg);
        for (Santelmo sm : santelmos)
            if (sm.isAlive())
                drawMobLabel(g2, sm.getHitbox(), assets.santelmoImg);
        for (Skeleton sk : skeletons)
            if (sk.isAlive())
                drawMobLabel(g2, sk.getHitbox(), assets.skeletonImg);
        for (Aswang_Mob am : aswangMobs)
            if (am.isAlive())
                drawMobLabel(g2, am.getHitbox(), assets.aswangMobImg);
        if (aswang != null && aswang.isAlive())
            drawMobLabel(g2, aswang.getHitbox(), assets.aswangBossImg);
        if (mangkukulam != null && mangkukulam.isAlive())
            drawMobLabel(g2, mangkukulam.getHitbox(), assets.mangkukulamImg);
        if (severino != null && severino.isAlive())
            drawMobLabel(g2, severino.getHitbox(), assets.severinoImg);
        if (sigbin != null && sigbin.isAlive())
            drawMobLabel(g2, sigbin.getHitbox(), assets.sigbinImg);
        if (bakunawa != null && bakunawa.alive)
            drawMobLabel(g2, bakunawa.getHitbox(), assets.bakunawaImg);
        if (tikbalang != null && tikbalang.isAlive())
            drawMobLabel(g2, tikbalang.getHitbox(), assets.tikbalangImg);
        if (kapreBoss != null && kapreBoss.isAlive())
            drawMobLabel(g2, kapreBoss.getHitbox(), assets.kapreBossImg);
        if (amomongo != null && amomongo.isAlive())
            drawMobLabel(g2, amomongo.getHitbox(), assets.amomongImg);
        if (sitan != null && sitan.isAlive())
            drawMobLabel(g2, sitan.getHitbox(), assets.sitanImg);

        g2.setColor(Color.YELLOW);
        for (Equipments.Arrow a : arrows)
            g2.fillRect(a.x, a.y, 10, 5);

        drawCombatEffects(g2);
        drawLootDrops(g2);
        player.draw(g2, assets, sword, selectedItem);

        ui.drawHUD(g2, BASE_W, BASE_H, player, playerData,
                sessionCoins, sessionScore, potion.count,
                level, wave, levelClear, bossDefeated, CHAPTER_BG[chapter][0]);
        ui.drawDashBar(g2, devMode ? 0 : player.dashCooldown);
        drawHotbarForClass(g2);
        drawScoreboard(g2);

        if (levelClear || bossDefeated)
            drawAdvanceHint(g2);
        if (anyBossAlive())
            drawBossBar(g2);
        if (bossIntroActive)
            drawBossIntro(g2);
        if (devMode)
            drawDevBadge(g2);
        if (devMode && devChapterSelectOpen)
            drawDevChapterSelect(g2);
        if (curseDotTimer > 0)
            drawCurseIndicator(g2);
        if (kapreBurnTimer > 0)
            drawStatusBar(g2, 0, "BURN", new Color(255, 90, 0), new Color(200, 60, 0), kapreBurnTimer,
                    KAPRE_BURN_DURATION);
        if (kapreConfuseTimer > 0)
            drawStatusBar(g2, 1, "CONFUSE", new Color(160, 0, 220), new Color(100, 0, 180), kapreConfuseTimer, 120);
        if (kapreSlowTimer > 0)
            drawStatusBar(g2, 2, "SLOW", new Color(60, 140, 220), new Color(30, 80, 180), kapreSlowTimer, 180);

        if (shopOpen)
            drawShopForClass(g2);
        if (paused)
            ui.drawPause(g2, BASE_W, BASE_H, pauseHovered);
        if (newGamePlusTransition)
            drawNewGamePlusScreen(g2);
        if (gameOver)
            ui.drawGameOver(g2, BASE_W, BASE_H, playerData.name, sessionScore, sessionCoins, level, pauseHovered);

        drawGameBorder(g2);
        g2.dispose();

        Graphics2D sg = (Graphics2D) g;
        sg.setColor(Color.BLACK);
        sg.fillRect(0, 0, SW, SH);
        sg.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        float scale = Math.min((float) SW / BASE_W, (float) SH / BASE_H);
        int dw = (int) (BASE_W * scale);
        int dh = (int) (BASE_H * scale);
        int dx = (SW - dw) / 2;
        int dy = (SH - dh) / 2;
        sg.drawImage(canvas, dx, dy, dw, dh, null);
    }

    private void drawBackground(Graphics2D g2) {
        int W = BASE_W, H = BASE_H;
        BufferedImage bg = level == 3 ? assets.bgL3 : level == 2 ? assets.bgL2 : assets.bgL1;

        if (bg != null) {
            // Far layer: drawn slightly wider and shifted at half parallax speed
            int farShift = (int) (bgParallaxOffset * 0.3f);
            int farW = W + 40;
            g2.drawImage(bg, -20 + farShift, 0, farW, H, null);
        } else {
            g2.setColor(new Color(20, 10, 30));
            g2.fillRect(0, 0, W, H);
        }

        for (BgCloud c : bgClouds) {
            int ca = (int) (c.alpha * 255);
            if (ca <= 0)
                continue;
            // Each "cloud" is a cluster of overlapping soft ovals
            int cx = (int) c.x;
            int cy = (int) c.y;
            int bw = (int) (c.scaleX * 90); // base puff width
            int bh = (int) (c.scaleY * 38); // base puff height
            // Draw several soft puffs with composite alpha
            float[] offX = { 0f, 0.40f, -0.35f, 0.70f, -0.65f };
            float[] offY = { 0f, -0.30f, -0.20f, 0.10f, 0.15f };
            float[] sc = { 1f, 0.75f, 0.70f, 0.55f, 0.50f };
            for (int i = 0; i < offX.length; i++) {
                int px = cx + (int) (offX[i] * bw);
                int py = cy + (int) (offY[i] * bh);
                int pw = (int) (bw * sc[i]);
                int ph2 = (int) (bh * sc[i]);
                int pAlpha = (int) (ca * (i == 0 ? 1.0 : 0.7));
                g2.setColor(new Color(c.r, c.g, c.b, Math.min(255, pAlpha)));
                g2.fillOval(px - pw / 2, py - ph2 / 2, pw, ph2);
            }
        }

        float pulse = (float) (0.5 + 0.5 * Math.sin(bgTick * 0.018));
        Color base = CHAPTER_OVERLAY_COLORS[Math.min(chapter, CHAPTER_OVERLAY_COLORS.length - 1)];
        int overlayAlpha = (int) (base.getAlpha() * 0.6 + pulse * base.getAlpha() * 0.4);
        Color overlayColor = new Color(base.getRed(), base.getGreen(), base.getBlue(),
                Math.min(255, overlayAlpha));
        g2.setColor(overlayColor);
        g2.fillRect(0, 0, W, H);

        if (anyBossAlive()) {
            float bossHpRatio = bosshp();
            if (bossHpRatio < 0.3f) {
                float danger = (0.3f - bossHpRatio) / 0.3f; // 0→1 as HP drops to 0
                float flashPulse = (float) (0.5 + 0.5 * Math.sin(bgTick * 0.12));
                int dangerAlpha = (int) (danger * flashPulse * 45);
                g2.setColor(new Color(180, 0, 0, dangerAlpha));
                g2.fillRect(0, 0, W, H);
            }
        }

        for (BgParticle p : bgParticles) {
            int alpha = (int) (p.alpha * 180);
            if (alpha <= 0)
                continue;
            g2.setColor(new Color(p.r, p.g, p.b, alpha));
            int s = (int) p.size;
            // Alternate between soft circles and tiny sparkles for variety
            if (p.maxLife % 2 == 0) {
                g2.fillOval((int) p.x - s / 2, (int) p.y - s / 2, s, s);
            } else {
                // Tiny cross sparkle
                g2.fillRect((int) p.x - s / 2, (int) p.y - 1, s, 2);
                g2.fillRect((int) p.x - 1, (int) p.y - s / 2, 2, s);
            }
        }

        int groundY = groundY();
        g2.setPaint(new GradientPaint(0, groundY - 20, new Color(0, 0, 0, 0),
                0, groundY + 30, new Color(0, 0, 0, 80)));
        g2.fillRect(0, groundY - 20, W, 50);
    }

    private void drawMobLabel(Graphics2D g2, Rectangle hb, BufferedImage sprite) {
        if (hb == null || sprite == null)
            return;
        int x = hb.x, y = hb.y;
        int w = Math.max(hb.width, 20);
        int h = Math.max(hb.height, 28);
        g2.drawImage(sprite, x, y, w, h, null);
    }

    private void drawGameBorder(Graphics2D g2) {
        int W = BASE_W, H = BASE_H;
        final Color GOLD = new Color(212, 175, 55);
        final Color GOLD_DIM = new Color(180, 140, 30, 160);
        final Color GOLD_GLOW = new Color(255, 220, 80, 60);
        final Color DARK_PANEL = new Color(8, 4, 16, 210);

        // vignette shadow edges
        int vig = 40;
        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 180), 0, vig, new Color(0, 0, 0, 0)));
        g2.fillRect(0, 0, W, vig);
        g2.setPaint(new GradientPaint(0, H - vig, new Color(0, 0, 0, 0), 0, H, new Color(0, 0, 0, 180)));
        g2.fillRect(0, H - vig, W, vig);
        g2.setPaint(new GradientPaint(0, 0, new Color(0, 0, 0, 180), vig, 0, new Color(0, 0, 0, 0)));
        g2.fillRect(0, 0, vig, H);
        g2.setPaint(new GradientPaint(W - vig, 0, new Color(0, 0, 0, 0), W, 0, new Color(0, 0, 0, 180)));
        g2.fillRect(W - vig, 0, vig, H);

        // outer glow line
        g2.setStroke(new BasicStroke(4f));
        g2.setColor(GOLD_GLOW);
        g2.drawRect(3, 3, W - 6, H - 6);

        // main gold border
        g2.setStroke(new BasicStroke(2.5f));
        g2.setColor(GOLD);
        g2.drawRect(6, 6, W - 12, H - 12);

        // inner thin border
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(GOLD_DIM);
        g2.drawRect(10, 10, W - 20, H - 20);

        // corner ornaments
        int[] cx = { 14, W - 14, 14, W - 14 };
        int[] cy = { 14, 14, H - 14, H - 14 };
        for (int i = 0; i < 4; i++)
            drawCornerOrnament(g2, cx[i], cy[i], GOLD, DARK_PANEL);

        // top-center chapter banner
        String chTitle = "CHAPTER " + (chapter + 1) + "  \u2022  LEVEL " + level;
        g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 13));
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(chTitle);
        int bannerW = tw + 32, bannerH = 22, bx = W / 2 - bannerW / 2;
        g2.setColor(DARK_PANEL);
        g2.fillRoundRect(bx, 0, bannerW, bannerH, 8, 8);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(GOLD);
        g2.drawRoundRect(bx, 0, bannerW, bannerH, 8, 8);
        g2.drawString(chTitle, W / 2 - tw / 2, 15);

        // bottom-center wave strip
        String waveStr = levelClear ? "\u2756 CLEAR \u2756" : (isBossLevel() ? "\u2756 BOSS \u2756" : "WAVE " + wave);
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        FontMetrics fm2 = g2.getFontMetrics();
        int ww = fm2.stringWidth(waveStr) + 24, wx = W / 2 - ww / 2;
        g2.setColor(DARK_PANEL);
        g2.fillRoundRect(wx, H - 22, ww, 22, 8, 8);
        g2.setStroke(new BasicStroke(1.2f));
        g2.setColor(levelClear ? new Color(80, 255, 120) : isBossLevel() ? new Color(220, 60, 60) : GOLD);
        g2.drawRoundRect(wx, H - 22, ww, 22, 8, 8);
        g2.drawString(waveStr, W / 2 - fm2.stringWidth(waveStr) / 2, H - 7);

        g2.setStroke(new BasicStroke(1f));
    }

    private void drawCornerOrnament(Graphics2D g2, int x, int y, Color gold, Color bg) {
        int d = 7;
        int[] px = { x, x + d, x, x - d }, py = { y - d, y, y + d, y };
        g2.setColor(bg);
        g2.fillPolygon(px, py, 4);
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(gold);
        g2.drawPolygon(px, py, 4);
        g2.fillOval(x - 2, y - 2, 4, 4);
    }

    private void drawCombatEffects(Graphics2D g2) {
        // ── Immunity shield shimmer ───────────────────────────────────────────
        if (player.immunityActive) {
            float timeLeft = player.immunityTimer / (float) Player.IMMUNITY_DURATION;
            float pulse = (float)(0.3 + 0.5 * Math.sin(bgTick * 0.22));
            // Radial glow centred on the player
            int cx = player.x + Player.PS / 2, cy = player.y + Player.PS / 2;
            float r = Player.PS * 0.85f;
            g2.setPaint(new RadialGradientPaint(cx, cy, r,
                    new float[]{ 0f, 0.6f, 1f },
                    new Color[]{ new Color(80, 220, 255, (int)(pulse * 90)),
                                 new Color(40, 160, 220, (int)(pulse * 50)),
                                 new Color(0, 0, 0, 0) }));
            g2.fillOval((int)(cx - r), (int)(cy - r), (int)(r * 2), (int)(r * 2));
            // Thin cyan ring around player
            g2.setStroke(new BasicStroke(2.5f));
            g2.setColor(new Color(80, 220, 255, (int)(0.55f + pulse * 0.45f) * 255));
            g2.drawOval(player.x - 6, player.y - 6, Player.PS + 12, Player.PS + 12);
            g2.setStroke(new BasicStroke(1f));
            // Countdown text above player head
            String secStr = String.format("%.1f", player.immunityTimer / 60f);
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            FontMetrics fm = g2.getFontMetrics();
            int tx = cx - fm.stringWidth(secStr) / 2;
            int ty = player.y - 10;
            g2.setColor(new Color(0, 0, 0, 160));
            g2.drawString(secStr, tx + 1, ty + 1);
            g2.setColor(new Color(80, 230, 255));
            g2.drawString(secStr, tx, ty);
        }

        boolean duck = player.duck && player.onGround;
        if (sword.active && assets.swordSlashImg != null) {
            int sw = 80, sh = 80, sy = duck ? player.y + Player.PS - Player.DUCK_HEIGHT + 5 : player.y + 10;
            if (player.facingLeft)
                g2.drawImage(assets.swordSlashImg, player.x - (sw - 20) + sw, sy, -sw, sh, null);
            else
                g2.drawImage(assets.swordSlashImg, player.x + Player.PS - 20, sy, sw, sh, null);
        }
        if (potion.active && assets.healImg != null) {
            int ox = player.facingLeft ? 0 : Player.PS - 40, oy = duck ? Player.PS - 32 : Player.PS / 2 - 32;
            if (player.facingLeft)
                g2.drawImage(assets.healImg, player.x + ox + 50, player.y + oy, -50, 50, null);
            else
                g2.drawImage(assets.healImg, player.x + ox, player.y + oy, 50, 50, null);
        }
    }

    private void drawHotbarForClass(Graphics2D g2) {
        // Build immunity sub-label (shared by all classes)
        String immSub;
        if (player.immunityActive)
            immSub = String.format("%.1fs", player.immunityTimer / 60f);
        else if (player.immunityCooldown > 0)
            immSub = String.format("%.0fs", player.immunityCooldown / 60f);
        else
            immSub = "READY";

        if (player instanceof Mage m) {
            drawClassHotbar(g2,
                    "Bolt", "free",
                    "Beam", m.beamCooldown > 0 ? String.format("%.0fs", m.beamCooldown / 60f) : "READY",
                    "Immunity", immSub,
                    potion.count + "/" + Equipments.Potion.MAX_POTIONS);
        } else if (player instanceof Fighter f) {
            drawClassHotbar(g2,
                    "Punch", f.punchCooldown > 0 ? String.format("%.0fs", f.punchCooldown / 60f) : "READY",
                    "PwrPunch", f.powerPunchCooldown > 0 ? String.format("%.0fs", f.powerPunchCooldown / 60f) : "READY",
                    "Immunity", immSub,
                    potion.count + "/" + Equipments.Potion.MAX_POTIONS);
        } else {
            // Warrior — still use drawClassHotbar so immunity slot is shown consistently
            String bowSub = bow.reloading
                    ? String.format("%.0fs", (Equipments.Bow.RELOAD_TICKS - bow.reloadTimer) / 60f)
                    : (bow.isReady() ? "READY" : "...");
            drawClassHotbar(g2,
                    "Sword", sword.active ? "ATK" : "READY",
                    "Bow", bowSub,
                    "Immunity", immSub,
                    potion.count + "/" + Equipments.Potion.MAX_POTIONS);
        }
    }

    private void drawClassHotbar(Graphics2D g2, String l1, String s1, String l2, String s2,
                                  String l3, String s3, String s4) {
        int W = BASE_W, H = BASE_H, box = 72, gap = 8;
        int startX = W / 2 - (4 * box + 3 * gap) / 2;
        String[] labels = { l1, l2, l3, "POT" };
        String[] subs   = { s1, s2, s3, s4 };

        // tray background
        int trayX = startX - 10, trayY = H - box - 26, trayW = 4 * box + 3 * gap + 20, trayH = box + 24;
        g2.setColor(new Color(0, 0, 0, 180));
        g2.fillRoundRect(trayX + 3, trayY + 3, trayW, trayH, 14, 14);
        g2.setPaint(new GradientPaint(trayX, trayY, new Color(30, 18, 50, 240), trayX, trayY + trayH,
                new Color(14, 8, 28, 240)));
        g2.fillRoundRect(trayX, trayY, trayW, trayH, 14, 14);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(212, 175, 55, 180));
        g2.drawRoundRect(trayX, trayY, trayW, trayH, 14, 14);
        g2.setStroke(new BasicStroke(0.8f));
        g2.setColor(new Color(212, 175, 55, 60));
        g2.drawRoundRect(trayX + 4, trayY + 4, trayW - 8, trayH - 8, 10, 10);

        for (int i = 0; i < 4; i++) {
            int bx = startX + i * (box + gap), by = H - box - 18;
            boolean sel    = (selectedItem == i + 1);
            boolean isImm  = (i == 2); // slot 3 = Immunity
            boolean immOn  = isImm && player.immunityActive;
            boolean immCD  = isImm && player.immunityCooldown > 0;

            // slot shadow
            g2.setColor(new Color(0, 0, 0, 160));
            g2.fillRoundRect(bx + 2, by + 2, box, box, 10, 10);

            // slot fill
            Color topC, botC;
            if (immOn) {
                topC = new Color(0, 160, 220, 255);
                botC = new Color(0, 80, 140, 255);
            } else if (sel && isImm) {
                topC = new Color(0, 80, 120, 255);
                botC = new Color(0, 40, 70, 255);
            } else if (sel) {
                topC = new Color(110, 85, 15, 255);
                botC = new Color(60, 44, 8, 255);
            } else {
                topC = new Color(45, 28, 70, 255);
                botC = new Color(24, 14, 42, 255);
            }
            g2.setPaint(new GradientPaint(bx, by, topC, bx, by + box, botC));
            g2.fillRoundRect(bx, by, box, box, 10, 10);

            // pulsing shield glow when immunity is active
            if (immOn) {
                float pulse = (float)(0.4 + 0.6 * Math.sin(bgTick * 0.15));
                g2.setStroke(new BasicStroke(5f));
                g2.setColor(new Color(80, 220, 255, (int)(pulse * 200)));
                g2.drawRoundRect(bx - 3, by - 3, box + 6, box + 6, 12, 12);
            }
            // glow ring when selected
            if (sel) {
                g2.setStroke(new BasicStroke(4f));
                g2.setColor(isImm ? new Color(80, 220, 255, 100) : new Color(255, 220, 80, 100));
                g2.drawRoundRect(bx - 2, by - 2, box + 4, box + 4, 11, 11);
            }
            // slot border
            g2.setStroke(new BasicStroke(sel ? 2.5f : 1.8f));
            g2.setColor(immOn  ? new Color(80, 220, 255) :
                        sel    ? new Color(255, 215, 60) :
                        isImm  ? new Color(60, 160, 200, 220) :
                                 new Color(180, 155, 80, 220));
            g2.drawRoundRect(bx, by, box, box, 10, 10);

            // ── Cast icon ─────────────────────────────────────────────────────
            int icx = bx + box / 2, icy = by + 32;
            drawHotbarIcon(g2, i, icx, icy, immOn);

            // keybind badge
            g2.setColor(new Color(0, 0, 0, 200));
            g2.fillRoundRect(bx + box - 20, by, 20, 16, 5, 5);
            g2.setStroke(new BasicStroke(1f));
            g2.setColor(new Color(212, 175, 55, 180));
            g2.drawRoundRect(bx + box - 20, by, 20, 16, 5, 5);
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.setColor(new Color(255, 230, 140));
            g2.drawString(String.valueOf(i + 1), bx + box - 13, by + 12);

            // label
            g2.setColor(immOn ? new Color(220, 250, 255, 230) : new Color(255, 255, 255, 230));
            g2.setFont(new Font("SansSerif", Font.BOLD, 11));
            g2.drawString(labels[i], bx + 5, by + 54);

            // sub-label
            boolean ready = "READY".equals(subs[i]) || "free".equals(subs[i]);
            Color subCol;
            if (immOn)      subCol = new Color(80, 220, 255);
            else if (immCD) subCol = new Color(255, 190, 60);
            else if (isImm) subCol = new Color(80, 255, 100);
            else if (ready) subCol = new Color(80, 255, 100);
            else            subCol = new Color(255, 190, 60);
            g2.setColor(subCol);
            g2.setFont(new Font("SansSerif", Font.BOLD, 10));
            g2.drawString(subs[i], bx + 5, by + 66);

            // bottom line
            g2.setColor(sel ? new Color(255, 215, 60, 160) : new Color(180, 155, 80, 80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(bx + 6, by + box - 6, bx + box - 6, by + box - 6);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /**
     * Draws a slot icon using the PNG from assets/Icons/.
     * If the image wasn't found, falls back to the programmatic shape.
     */
    private void drawHotbarIcon(Graphics2D g2, int slot, int cx, int cy, boolean immOn) {
        // Resolve which image applies to this slot + class
        java.awt.image.BufferedImage img = switch (slot) {
            case 0 -> (player instanceof Mage)    ? iconBolt  :
                      (player instanceof Fighter) ? iconPunch : iconSword;
            case 1 -> (player instanceof Mage)    ? iconBeam  :
                      (player instanceof Fighter) ? iconPower : iconBow;
            case 2 -> iconShield;
            case 3 -> iconPotion;
            default -> null;
        };

        int iSize = 34; // icon render size inside the slot
        if (img != null) {
            // Draw centred, with a subtle white tint when immunity is active on slot 2
            java.awt.Composite oldComp = g2.getComposite();
            if (slot == 2 && immOn)
                g2.setComposite(java.awt.AlphaComposite.getInstance(
                        java.awt.AlphaComposite.SRC_OVER, 0.85f));
            g2.drawImage(img, cx - iSize / 2, cy - iSize / 2, iSize, iSize, null);
            g2.setComposite(oldComp);
            return;
        }

        // ── Fallback: drawn shapes (same as before) ───────────────────────────
        java.awt.geom.AffineTransform saved = g2.getTransform();
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        switch (slot) {
            case 0 -> {
                if (player instanceof Mage) {
                    g2.setColor(new Color(140, 200, 255, 220));
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    int[] bx2 = { cx + 4, cx - 2, cx + 2, cx - 6 };
                    int[] by2 = { cy - 13, cy - 1, cy - 1, cy + 13 };
                    g2.drawPolyline(bx2, by2, 4);
                    g2.setColor(new Color(200, 230, 255, 180));
                    g2.fillOval(cx - 8, cy + 10, 5, 5);
                } else if (player instanceof Fighter) {
                    g2.setColor(new Color(255, 160, 80, 220));
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.fillRoundRect(cx - 9, cy - 7, 18, 12, 5, 5);
                    g2.setColor(new Color(255, 200, 140, 200));
                    g2.fillRoundRect(cx - 7, cy + 3, 14, 7, 4, 4);
                    g2.setColor(new Color(255, 220, 160, 160));
                    g2.setStroke(new BasicStroke(1.5f));
                    g2.drawLine(cx + 10, cy - 10, cx + 16, cy - 14);
                    g2.drawLine(cx + 10, cy - 6,  cx + 16, cy - 6);
                    g2.drawLine(cx + 10, cy - 2,  cx + 15, cy + 2);
                } else {
                    g2.setColor(new Color(220, 220, 255, 220));
                    g2.setStroke(new BasicStroke(3f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawLine(cx - 10, cy + 11, cx + 10, cy - 11);
                    g2.setStroke(new BasicStroke(2.5f));
                    g2.setColor(new Color(255, 200, 80, 200));
                    g2.drawLine(cx - 7, cy - 4, cx + 5, cy + 8);
                    g2.setColor(new Color(200, 180, 100, 200));
                    g2.fillOval(cx - 13, cy + 9, 6, 6);
                }
            }
            case 1 -> {
                if (player instanceof Mage) {
                    g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(180, 80, 255, 220));
                    for (int d = 0; d < 8; d++) {
                        double ang = Math.toRadians(d * 45);
                        g2.drawLine(cx + (int)(Math.cos(ang) * 5), cy + (int)(Math.sin(ang) * 5),
                                    cx + (int)(Math.cos(ang) * 13), cy + (int)(Math.sin(ang) * 13));
                    }
                    g2.setColor(new Color(220, 160, 255, 240));
                    g2.fillOval(cx - 5, cy - 5, 10, 10);
                } else if (player instanceof Fighter) {
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.setColor(new Color(255, 100, 60, 220));
                    for (int d = 0; d < 6; d++) {
                        double ang = Math.toRadians(d * 60 - 90);
                        g2.drawLine(cx, cy, cx + (int)(Math.cos(ang) * 13), cy + (int)(Math.sin(ang) * 13));
                    }
                    g2.setColor(new Color(255, 180, 60, 240));
                    g2.fillOval(cx - 6, cy - 6, 12, 12);
                } else {
                    g2.setColor(new Color(180, 140, 80, 220));
                    g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                    g2.drawArc(cx - 12, cy - 13, 14, 26, -75, 150);
                    g2.setColor(new Color(220, 200, 160, 160));
                    g2.setStroke(new BasicStroke(1.2f));
                    g2.drawLine(cx - 5, cy - 13, cx - 5, cy + 13);
                    g2.setColor(new Color(240, 230, 200, 220));
                    g2.setStroke(new BasicStroke(2f));
                    g2.drawLine(cx - 16, cy, cx + 12, cy);
                    int[] ax = { cx + 12, cx + 7, cx + 7 };
                    int[] ay = { cy, cy - 4, cy + 4 };
                    g2.fillPolygon(ax, ay, 3);
                }
            }
            case 2 -> {
                Color sc = immOn ? new Color(80, 220, 255, 230) : new Color(100, 180, 220, 180);
                g2.setColor(sc);
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                int[] sx2 = { cx, cx+11, cx+11, cx,    cx-11, cx-11 };
                int[] sy2 = { cy-14, cy-8, cy+4, cy+14, cy+4, cy-8 };
                g2.drawPolygon(sx2, sy2, 6);
                g2.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 50));
                g2.fillPolygon(sx2, sy2, 6);
                g2.setColor(new Color(sc.getRed(), sc.getGreen(), sc.getBlue(), 200));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(cx, cy - 7, cx, cy + 7);
                g2.drawLine(cx - 6, cy, cx + 6, cy);
            }
            case 3 -> {
                g2.setColor(new Color(200, 100, 240, 200));
                g2.fillRoundRect(cx - 8, cy - 4, 16, 14, 6, 6);
                g2.setColor(new Color(160, 60, 220, 180));
                g2.fillRoundRect(cx - 6, cy + 2, 12, 7, 4, 4);
                g2.setColor(new Color(255, 220, 255, 140));
                g2.fillRoundRect(cx - 5, cy - 2, 5, 5, 3, 3);
                g2.setColor(new Color(180, 80, 220, 220));
                g2.fillRoundRect(cx - 4, cy - 12, 8, 10, 3, 3);
                g2.setColor(new Color(200, 160, 80, 220));
                g2.fillRoundRect(cx - 3, cy - 15, 6, 5, 2, 2);
                g2.setStroke(new BasicStroke(1.2f));
                g2.setColor(new Color(255, 180, 255, 120));
                g2.drawRoundRect(cx - 8, cy - 4, 16, 14, 6, 6);
            }
        }

        g2.setTransform(saved);
        g2.setStroke(new BasicStroke(1f));
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_OFF);
    }

    private void drawShopForClass(Graphics2D g2) {
        int W = BASE_W, H = BASE_H;
        if (player instanceof Mage)
            shop.drawMageShop(g2, W, H, shopHoveredSlot, sessionCoins, mageBeamLevel, mageBoltLevel, maxhp, potion);
        else if (player instanceof Fighter)
            shop.drawFighterShop(g2, W, H, shopHoveredSlot, sessionCoins, fighterPunchLevel, fighterPowerPunchLevel,
                    maxhp, potion);
        else
            shop.drawShop(g2, W, H, shopHoveredSlot, sessionCoins, sword, bow, maxhp, potion);
    }

    private void drawScoreboard(Graphics2D g2) {
        int W = BASE_W, sw = 240, sh = 102, sx = W - sw - 12, sy = 28;
        // shadow
        g2.setColor(new Color(0, 0, 0, 120));
        g2.fillRoundRect(sx + 3, sy + 3, sw, sh, 12, 12);
        // gradient background
        g2.setPaint(new GradientPaint(sx, sy, new Color(12, 6, 24, 220), sx, sy + sh, new Color(6, 3, 14, 220)));
        g2.fillRoundRect(sx, sy, sw, sh, 12, 12);
        // gold glow border
        g2.setStroke(new BasicStroke(3f));
        g2.setColor(new Color(212, 175, 55, 50));
        g2.drawRoundRect(sx - 1, sy - 1, sw + 2, sh + 2, 13, 13);
        // main border
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(212, 175, 55));
        g2.drawRoundRect(sx, sy, sw, sh, 12, 12);
        // inner thin border
        g2.setStroke(new BasicStroke(0.8f));
        g2.setColor(new Color(180, 140, 30, 80));
        g2.drawRoundRect(sx + 4, sy + 4, sw - 8, sh - 8, 8, 8);
        // header divider line
        g2.setColor(new Color(212, 175, 55, 120));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(sx + 8, sy + 26, sx + sw - 8, sy + 26);
        // title
        g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 14));
        g2.setColor(new Color(212, 175, 55));
        g2.drawString("\u2756 SCOREBOARD \u2756", sx + 12, sy + 20);
        // stats
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(220, 210, 180));
        g2.drawString("Score:  ", sx + 12, sy + 44);
        g2.setColor(Color.WHITE);
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(String.valueOf(sessionScore), sx + 72, sy + 44);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(220, 210, 180));
        g2.drawString("Kills:  ", sx + 12, sy + 62);
        g2.setColor(new Color(255, 140, 140));
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(String.valueOf(enemiesDefeated), sx + 72, sy + 62);
        g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
        g2.setColor(new Color(220, 210, 180));
        g2.drawString("Player: ", sx + 12, sy + 80);
        g2.setColor(new Color(140, 220, 255));
        g2.setFont(new Font("SansSerif", Font.BOLD, 12));
        g2.drawString(playerData.name, sx + 72, sy + 80);
        g2.setStroke(new BasicStroke(1f));
    }

    private void drawBossBar(Graphics2D g2) {
        int W = BASE_W, H = BASE_H, barW = (int) (W * 0.6), barH = 22, bx = W / 2 - barW / 2, by = H - 110;
        float hp = bosshp();

        // panel background
        g2.setColor(new Color(0, 0, 0, 200));
        g2.fillRoundRect(bx - 10, by - 30, barW + 20, barH + 40, 12, 12);
        // panel border glow
        g2.setStroke(new BasicStroke(3f));
        g2.setColor(new Color(180, 20, 20, 60));
        g2.drawRoundRect(bx - 11, by - 31, barW + 22, barH + 42, 13, 13);
        // panel border
        g2.setStroke(new BasicStroke(1.5f));
        g2.setColor(new Color(200, 160, 40, 200));
        g2.drawRoundRect(bx - 10, by - 30, barW + 20, barH + 40, 12, 12);

        // boss name with drop shadow
        g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 18));
        FontMetrics fm = g2.getFontMetrics();
        String name = "\u2620 " + bossName() + " \u2620";
        int nx = W / 2 - fm.stringWidth(name) / 2;
        g2.setColor(new Color(0, 0, 0, 200));
        g2.drawString(name, nx + 2, by - 8);
        g2.setColor(new Color(220, 80, 80));
        g2.drawString(name, nx, by - 10);

        // bar track
        g2.setColor(new Color(40, 4, 4));
        g2.fillRoundRect(bx, by, barW, barH, 8, 8);
        // bar fill gradient
        Color barLeft = hp > 0.5f ? new Color(200, 30, 30)
                : hp > 0.25f ? new Color(220, 100, 20) : new Color(255, 40, 40);
        Color barRight = hp > 0.5f ? new Color(160, 10, 10)
                : hp > 0.25f ? new Color(180, 60, 10) : new Color(200, 10, 10);
        g2.setPaint(new GradientPaint(bx, by, barLeft, bx, by + barH, barRight));
        g2.fillRoundRect(bx, by, (int) (barW * hp), barH, 8, 8);
        // glossy sheen on bar
        g2.setPaint(new GradientPaint(bx, by, new Color(255, 255, 255, 40), bx, by + barH / 2,
                new Color(255, 255, 255, 0)));
        g2.fillRoundRect(bx, by, (int) (barW * hp), barH / 2, 8, 8);
        // bar border
        g2.setStroke(new BasicStroke(1.8f));
        g2.setColor(new Color(200, 160, 40, 200));
        g2.drawRoundRect(bx, by, barW, barH, 8, 8);
        // hp percent text
        g2.setFont(new Font("SansSerif", Font.BOLD, 11));
        g2.setColor(Color.WHITE);
        String pct = Math.round(hp * 100) + "%";
        g2.drawString(pct, W / 2 - g2.getFontMetrics().stringWidth(pct) / 2, by + 15);
        g2.setStroke(new BasicStroke(1f));
    }

    private void drawBossIntro(Graphics2D g2) {
        int W = BASE_W, H = BASE_H;
        float fade = Math.min(1f, bossIntroTimer / 30f);
        // dark overlay
        g2.setColor(new Color(0, 0, 0, (int) (fade * 180)));
        g2.fillRect(0, 0, W, H);
        // red vignette
        g2.setPaint(new RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.7f,
                new float[] { 0f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(120, 0, 0, (int) (fade * 160)) }));
        g2.fillRect(0, 0, W, H);

        int secsLeft = (BOSS_INTRO_TICKS - bossIntroTimer) / 60 + 1;
        // BOSS BATTLE text
        g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 46));
        FontMetrics fm = g2.getFontMetrics();
        String title = "BOSS BATTLE";
        int tx = W / 2 - fm.stringWidth(title) / 2;
        // drop shadow
        g2.setColor(new Color(0, 0, 0, (int) (fade * 200)));
        g2.drawString(title, tx + 3, H / 2 - 26);
        // main text
        g2.setColor(new Color(220, 50, 50, (int) (fade * 255)));
        g2.drawString(title, tx, H / 2 - 28);
        // decorative lines
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(200, 160, 40, (int) (fade * 160)));
        int lw = fm.stringWidth(title);
        int ly = H / 2 - 18;
        g2.drawLine(W / 2 - lw / 2 - 20, ly, W / 2 - lw / 2 - 8, ly);
        g2.drawLine(W / 2 + lw / 2 + 8, ly, W / 2 + lw / 2 + 20, ly);

        g2.setFont(new Font("Serif", Font.ITALIC, 28));
        FontMetrics fm2 = g2.getFontMetrics();
        String l2 = "Get Ready... " + secsLeft;
        int lx = W / 2 - fm2.stringWidth(l2) / 2;
        g2.setColor(new Color(0, 0, 0, (int) (fade * 200)));
        g2.drawString(l2, lx + 2, H / 2 + 22);
        g2.setColor(new Color(220, 180, 80, (int) (fade * 220)));
        g2.drawString(l2, lx, H / 2 + 20);
        g2.setStroke(new BasicStroke(1f));
    }

    // ── Dev mode: chapter selector hit-test ──────────────────────────────────
    /** Returns chapter index 0-8 if a chapter button was clicked, else -1. */
    private int devChapterHit(int mx, int my) {
        int W = BASE_W, H = BASE_H;
        int cols = 3, bw = 130, bh = 52, gapX = 16, gapY = 12;
        int totalW = cols * bw + (cols - 1) * gapX;
        int startX = W / 2 - totalW / 2;
        int startY = H / 2 - 160;
        for (int i = 0; i < 9; i++) {
            int col = i % cols, row = i / cols;
            int bx = startX + col * (bw + gapX);
            int by = startY + row * (bh + gapY);
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh)
                return i;
        }
        return -1;
    }

    /** Returns 0=Warrior, 1=Mage, 2=Fighter if a char button was clicked, else -1. */
    private int devCharHit(int mx, int my) {
        int W = BASE_W, H = BASE_H;
        String[] classes = { "warrior", "mage", "fighter" };
        int bw = 120, bh = 46, gap = 18;
        int totalW = classes.length * bw + (classes.length - 1) * gap;
        int startX = W / 2 - totalW / 2;
        int by = H / 2 + 130;
        for (int i = 0; i < classes.length; i++) {
            int bx = startX + i * (bw + gap);
            if (mx >= bx && mx <= bx + bw && my >= by && my <= by + bh)
                return i;
        }
        return -1;
    }

    // ── Dev mode: chapter + character selector overlay ────────────────────────
    private void drawDevChapterSelect(Graphics2D g2) {
        int W = BASE_W, H = BASE_H;

        // Dim backdrop
        g2.setColor(new Color(0, 0, 0, 210));
        g2.fillRect(0, 0, W, H);

        // ── Panel ─────────────────────────────────────────────────────────────
        int pW = 500, pH = 380, px = W / 2 - pW / 2, py = H / 2 - pH / 2 - 10;
        g2.setColor(new Color(5, 15, 25, 235));
        g2.fillRoundRect(px, py, pW, pH, 18, 18);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(0, 220, 255));
        g2.drawRoundRect(px, py, pW, pH, 18, 18);
        g2.setStroke(new BasicStroke(0.8f));
        g2.setColor(new Color(0, 220, 255, 40));
        g2.drawRoundRect(px + 4, py + 4, pW - 8, pH - 8, 15, 15);
        g2.setStroke(new BasicStroke(1f));

        // ── Title ─────────────────────────────────────────────────────────────
        g2.setFont(new Font("Courier New", Font.BOLD, 15));
        g2.setColor(new Color(0, 220, 255));
        String title = "\u26A1 DEV PANEL";
        FontMetrics fm = g2.getFontMetrics();
        g2.drawString(title, W / 2 - fm.stringWidth(title) / 2, py + 26);
        g2.setColor(new Color(0, 220, 255, 60));
        g2.drawLine(px + 16, py + 34, px + pW - 16, py + 34);

        // ── Section: Chapter Jump ─────────────────────────────────────────────
        g2.setFont(new Font("Courier New", Font.BOLD, 10));
        g2.setColor(new Color(0, 180, 200, 180));
        g2.drawString("CHAPTER JUMP", px + 16, py + 50);

        String[] chNames = {
            "Ch1 Aswang",    "Ch2 Mangkukulam", "Ch3 Severino",
            "Ch4 Sigbin",    "Ch5 Bakunawa",    "Ch6 Tikbalang",
            "Ch7 Kapre",     "Ch8 Amomongo",    "Ch9 Sitan"
        };
        int cols = 3, bw = 130, bh = 50, gapX = 16, gapY = 10;
        int totalW = cols * bw + (cols - 1) * gapX;
        int startX = W / 2 - totalW / 2;
        int startY = py + 58;

        for (int i = 0; i < 9; i++) {
            int col = i % cols, row = i / cols;
            int bx = startX + col * (bw + gapX);
            int by = startY + row * (bh + gapY);
            boolean isCurrent = (i == chapter);

            g2.setColor(isCurrent ? new Color(0, 80, 110, 230) : new Color(10, 30, 50, 200));
            g2.fillRoundRect(bx, by, bw, bh, 10, 10);
            g2.setStroke(new BasicStroke(isCurrent ? 2f : 1f));
            g2.setColor(isCurrent ? new Color(0, 220, 255) : new Color(0, 120, 150, 160));
            g2.drawRoundRect(bx, by, bw, bh, 10, 10);
            g2.setStroke(new BasicStroke(1f));

            g2.setFont(new Font("Courier New", Font.BOLD, 10));
            g2.setColor(new Color(0, 180, 220));
            g2.drawString("CHAPTER " + (i + 1), bx + 8, by + 16);

            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            g2.setColor(isCurrent ? new Color(0, 255, 255) : Color.WHITE);
            String namePart = chNames[i].substring(chNames[i].indexOf(' ') + 1);
            g2.drawString(namePart, bx + 8, by + 30);

            if (isCurrent) {
                g2.setFont(new Font("Courier New", Font.BOLD, 8));
                g2.setColor(new Color(0, 255, 200));
                g2.drawString("● HERE", bx + 8, by + 44);
            }
        }

        // ── Divider ───────────────────────────────────────────────────────────
        int divY = startY + 3 * (bh + gapY) + 4;
        g2.setColor(new Color(0, 220, 255, 50));
        g2.drawLine(px + 16, divY, px + pW - 16, divY);

        // ── Section: Character Select ─────────────────────────────────────────
        g2.setFont(new Font("Courier New", Font.BOLD, 10));
        g2.setColor(new Color(0, 180, 200, 180));
        g2.drawString("CHARACTER", px + 16, divY + 16);

        String[] charLabels  = { "Warrior",  "Mage",    "Fighter" };
        String[] charKeys    = { "warrior",  "mage",    "fighter" };
        // Slot-1 icon for each class
        java.awt.image.BufferedImage[] charIcons = { iconSword, iconBolt, iconPunch };
        Color[] charAccent = {
            new Color(220, 160, 40),   // warrior – gold
            new Color(80,  160, 255),  // mage    – blue
            new Color(255, 100, 60)    // fighter – orange
        };

        int cbw = 120, cbh = 46, cgap = 18;
        int ctotalW = charLabels.length * cbw + (charLabels.length - 1) * cgap;
        int cstartX = W / 2 - ctotalW / 2;
        int cby = divY + 22;

        for (int i = 0; i < charLabels.length; i++) {
            int cbx = cstartX + i * (cbw + cgap);
            boolean isCur = charKeys[i].equals(charType);

            // Button bg
            g2.setPaint(isCur
                ? new java.awt.GradientPaint(cbx, cby, new Color(charAccent[i].getRed(),
                    charAccent[i].getGreen(), charAccent[i].getBlue(), 160),
                    cbx, cby + cbh, new Color(charAccent[i].getRed(),
                    charAccent[i].getGreen(), charAccent[i].getBlue(), 60))
                : new java.awt.GradientPaint(cbx, cby, new Color(10, 30, 50, 200),
                    cbx, cby + cbh, new Color(5, 15, 30, 200)));
            g2.fillRoundRect(cbx, cby, cbw, cbh, 10, 10);

            // Border
            g2.setStroke(new BasicStroke(isCur ? 2f : 1f));
            g2.setColor(isCur ? charAccent[i] : new Color(charAccent[i].getRed(),
                    charAccent[i].getGreen(), charAccent[i].getBlue(), 100));
            g2.drawRoundRect(cbx, cby, cbw, cbh, 10, 10);
            g2.setStroke(new BasicStroke(1f));

            // Icon (24×24)
            int iSz = 26;
            if (charIcons[i] != null) {
                g2.drawImage(charIcons[i], cbx + 8, cby + cbh / 2 - iSz / 2, iSz, iSz, null);
            }

            // Label
            g2.setFont(new Font("Courier New", Font.BOLD, 12));
            g2.setColor(isCur ? Color.WHITE : new Color(200, 200, 200));
            g2.drawString(charLabels[i], cbx + 40, cby + cbh / 2 + 5);

            // Active marker
            if (isCur) {
                g2.setFont(new Font("Courier New", Font.BOLD, 8));
                g2.setColor(charAccent[i]);
                g2.drawString("▶ ACTIVE", cbx + 40, cby + cbh / 2 + 18);
            }
        }

        // ── Footer hint ───────────────────────────────────────────────────────
        g2.setFont(new Font("Courier New", Font.PLAIN, 10));
        g2.setColor(new Color(0, 160, 180, 180));
        String hint = "[T] close  •  click to jump chapter or switch class";
        g2.drawString(hint, W / 2 - g2.getFontMetrics().stringWidth(hint) / 2, py + pH - 8);
    }

    // ── Dev mode HUD badge ────────────────────────────────────────────────────
    private void drawDevBadge(Graphics2D g2) {
        int W = BASE_W;
        // Pulsing cyan glow using bgTick
        float pulse = (float) (0.5 + 0.5 * Math.sin(bgTick * 0.10));

        String line1 = "\u26A1 DEV MODE";
        String line2 = "INV \u2022 1HIT \u2022 \u221E DASH \u2022 [T] Ch.Select";

        g2.setFont(new Font("Courier New", Font.BOLD, 11));
        FontMetrics fm = g2.getFontMetrics();
        int padX = 10, padY = 6;
        int bw = Math.max(fm.stringWidth(line1), fm.stringWidth(line2)) + padX * 2;
        int bh = 32 + padY;
        int bx = W - bw - 10;
        int by = 28;

        // Shadow
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(bx + 2, by + 2, bw, bh, 8, 8);

        // Background
        g2.setColor(new Color(0, 20, 30, 210));
        g2.fillRoundRect(bx, by, bw, bh, 8, 8);

        // Glowing border
        int glowAlpha = (int) (120 + pulse * 100);
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(0, 220, 255, glowAlpha));
        g2.drawRoundRect(bx, by, bw, bh, 8, 8);
        g2.setStroke(new BasicStroke(1f));

        // Text line 1 — title
        int textAlpha = (int) (200 + pulse * 55);
        g2.setColor(new Color(0, 220, 255, textAlpha));
        g2.drawString(line1, bx + padX, by + 14);

        // Text line 2 — perks
        g2.setFont(new Font("Courier New", Font.PLAIN, 9));
        g2.setColor(new Color(120, 230, 255, 200));
        g2.drawString(line2, bx + padX, by + bh - padY);
    }

    private void drawCurseIndicator(Graphics2D g2) {
        float frac = curseDotTimer / (float) Mangkukulam.CurseOrb.DOT_DURATION_TICKS;
        int barX = 12, barY = 142;
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(barX - 2, barY - 2, 126, 14, 6, 6);
        g2.setColor(new Color(60, 0, 60));
        g2.fillRoundRect(barX, barY, 122, 10, 5, 5);
        g2.setPaint(
                new GradientPaint(barX, barY, new Color(180, 0, 220), barX, (int) (barY + 10), new Color(120, 0, 160)));
        g2.fillRoundRect(barX, barY, (int) (122 * frac), 10, 5, 5);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(200, 100, 255, 160));
        g2.drawRoundRect(barX, barY, 122, 10, 5, 5);
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.setColor(new Color(220, 140, 255));
        g2.drawString("CURSED", barX + 126, barY + 9);
    }

    private void drawStatusBar(Graphics2D g2, int slot, String label, Color barCol, Color bgCol, int timer,
            int maxTimer) {
        int y = 158 + slot * 18, barX = 12;
        float frac = timer / (float) maxTimer;
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(barX - 2, y - 2, 126, 14, 6, 6);
        g2.setColor(bgCol);
        g2.fillRoundRect(barX, y, 122, 10, 5, 5);
        g2.setPaint(new GradientPaint(barX, y, barCol, barX, y + 10, barCol.darker()));
        g2.fillRoundRect(barX, y, (int) (122 * frac), 10, 5, 5);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(barCol.brighter());
        g2.drawRoundRect(barX, y, 122, 10, 5, 5);
        g2.setFont(new Font("SansSerif", Font.BOLD, 10));
        g2.setColor(Color.WHITE);
        g2.drawString(label, barX + 126, y + 9);
    }

    private void tryBuy(int slot) {
        int coinsBefore = sessionCoins; // snapshot — used to detect a successful purchase below

        if (slot == 2) {
            buyMaxHP();
        } else if (slot == 3) {
            buyPotion();
        } else if (player instanceof Mage m) {
            if (slot == 0) {
                int c = shop.getSkillCost(mageBeamLevel);
                if (c > 0 && coins(c)) {
                    m.beamLevel = ++mageBeamLevel;
                    m.beamCooldown = 0;
                }
            }
            if (slot == 1) {
                int c = shop.getSkillCost(mageBoltLevel);
                if (c > 0 && coins(c))
                    m.boltLevel = ++mageBoltLevel;
            }
        } else if (player instanceof Fighter f) {
            if (slot == 0) {
                int c = shop.getSkillCost(fighterPunchLevel);
                if (c > 0 && coins(c))
                    f.punchLevel = ++fighterPunchLevel;
            }
            if (slot == 1) {
                int c = shop.getSkillCost(fighterPowerPunchLevel);
                if (c > 0 && coins(c))
                    f.powerPunchLevel = ++fighterPowerPunchLevel;
            }
        } else {
            if (slot == 0) {
                int c = shop.getSwordCost(sword.level);
                if (c > 0 && coins(c))
                    sword.level++;
            }
            if (slot == 1) {
                int c = shop.getBowCost(bow.level);
                if (c > 0 && coins(c))
                    bow.level++;
            }
        }

        // Play buy sound only when coins were actually spent (purchase succeeded)
        if (sessionCoins < coinsBefore)
            SoundManager.play("sounds/item/buy.wav");
    }

    private void buyMaxHP() {
        int c = shop.getMaxHPCost(maxhp.level);
        if (c > 0 && coins(c))
            maxhp.level++;
    }

    private void buyPotion() {
        int c = shop.getPotionCost(potion.count);
        if (c > 0 && coins(c) && potion.count < Equipments.Potion.MAX_POTIONS)
            potion.count++;
    }

    private boolean coins(int cost) {
        if (sessionCoins < cost)
            return false;
        sessionCoins -= cost;
        return true;
    }

    private void useItem() {
        switch (selectedItem) {
            case 1 -> {
                if (player instanceof Mage m) {
                    m.useSkill1();
                    SoundManager.playCooldown("sounds/cast/mage.wav", 200);
                } else if (player instanceof Fighter f) {
                    f.useSkill1();
                    SoundManager.playCooldown("sounds/cast/punch.wav", 200);
                } else {
                    sword.use();
                    SoundManager.playCooldown("sounds/cast/sword.wav", 150);
                }
            }
            case 2 -> {
                if (player instanceof Mage m) {
                    if (m.useSkill2())
                        SoundManager.play("sounds/cast/beam.wav");
                } else if (player instanceof Fighter f) {
                    f.useSkill2();
                    SoundManager.playCooldown("sounds/cast/power.wav", 300);
                } else {
                    bow.use();
                    SoundManager.playCooldown("sounds/cast/bow.wav", 100);
                }
            }
            case 3 -> {
                if (!player.immunityActive && player.immunityCooldown == 0) {
                    player.immunityActive = true;
                    player.immunityTimer  = Player.IMMUNITY_DURATION;
                    SoundManager.play("sounds/cast/immunity.wav");
                }
            }
            case 4 -> {
                if (player.health < 100 && potion.count > 0) {
                    player.health = Math.min(100, player.health + 20);
                    potion.use();
                    SoundManager.play("sounds/cast/heal.wav");
                }
            }
        }
    }

    private int pauseHit(int mx, int my) {
        int cx = BASE_W / 2, cy = BASE_H / 2;
        for (int i = 0; i < 2; i++) {
            int by = cy - 8 + i * 82;
            if (mx >= cx - 122 && mx <= cx + 122 && my >= by - 22 && my <= by + 24)
                return i;
        }
        return -1;
    }

    private int mouseToCanvasX(int screenX) {
        int SW = getWidth();
        float scale = Math.min((float) SW / BASE_W, (float) getHeight() / BASE_H);
        int dx = (SW - (int) (BASE_W * scale)) / 2;
        return (int) ((screenX - dx) / scale);
    }

    private int mouseToCanvasY(int screenY) {
        int SH = getHeight();
        float scale = Math.min((float) getWidth() / BASE_W, (float) SH / BASE_H);
        int dy = (SH - (int) (BASE_H * scale)) / 2;
        return (int) ((screenY - dy) / scale);
    }

    private void exitToMenu() {
        if (onExitToMenu == null)
            return;
        Runnable cb = onExitToMenu;
        onExitToMenu = null;               // prevent a second click from scheduling it again
        stopGameThread();
        SoundManager.setGamePaused(false); // lift the mute so menu SFX (button clicks etc.) work
        javax.swing.SwingUtilities.invokeLater(cb);
    }

    @Override
    public void mousePressed(MouseEvent e) {
        int mx = mouseToCanvasX(e.getX()), my = mouseToCanvasY(e.getY());
        // ── Dev chapter selector ─────────────────────────────────────────────
        if (devMode && devChapterSelectOpen) {
            int jumped = devChapterHit(mx, my);
            if (jumped >= 0) {
                devChapterSelectOpen = false;
                chapter = jumped;
                level = 1;
                wave = 1;
                player.x = 0;
                bossDefeated = false;
                levelClear = false;
                assets.loadAssets(charType, CHAPTER_BG[chapter]);
                setupLevel();
                return;
            }
            int charSel = devCharHit(mx, my);
            if (charSel >= 0) {
                String[] keys = { "warrior", "mage", "fighter" };
                switchCharacter(keys[charSel]);
                devChapterSelectOpen = false;
                return;
            }
            return; // click anywhere else closes nothing — keep overlay open
        }
        if (gameOver) {
            int cx = BASE_W / 2, by = BASE_H / 2 + 112;
            if (mx >= cx - 112 && mx <= cx + 112 && my >= by - 22 && my <= by + 24) {
                SoundManager.play("sounds/system/ButtonClick.wav");
                exitToMenu();
            }
            return;
        }
        if (paused) {
            int btn = pauseHit(mx, my);
            if (btn == 0) {
                SoundManager.play("sounds/system/ButtonClick.wav");
                paused = false;
                resumeBgMusic();
                SoundManager.setGamePaused(false);
            } else if (btn == 1) {
                SoundManager.play("sounds/system/ButtonClick.wav");
                playerData.addAttempt(sessionScore, sessionCoins, level, settings.difficulty);
                exitToMenu();
            }
            return;
        }
        if (shopOpen) {
            if (shopHoveredSlot >= 0)
                tryBuy(shopHoveredSlot);
            return;
        }
        if (e.getButton() == MouseEvent.BUTTON1)
            useItem();
    }

    @Override
    public void keyPressed(KeyEvent e) {
        int c = e.getKeyCode();
        if (gameOver) {
            if (c == KeyEvent.VK_ESCAPE) {
                SoundManager.play("sounds/system/ButtonClick.wav");
                exitToMenu();
            }
            return;
        }
        if (c == KeyEvent.VK_ESCAPE) {
            SoundManager.play("sounds/system/ButtonClick.wav");
            paused = !paused;
            if (paused) { pauseBgMusic();  SoundManager.setGamePaused(true);  }
            else         { resumeBgMusic(); SoundManager.setGamePaused(false); }
            pauseHovered = -1;
            return;
        }

        boolean beam = (player instanceof Mage m && m.beamActive);
        // ── Dev: T key toggles chapter selector overlay ───────────────────────
        if (devMode && c == KeyEvent.VK_T) {
            devChapterSelectOpen = !devChapterSelectOpen;
            return;
        }
        if (!beam) {
            boolean confuse = kapreConfuseTimer > 0;
            if (c == settings.keyLeft) {
                player.moveLeft = !confuse;
                player.moveRight = confuse;
                player.facingLeft = !confuse;
            }
            if (c == settings.keyRight) {
                player.moveRight = !confuse;
                player.moveLeft = confuse;
                player.facingLeft = confuse;
            }
            if (c == settings.keyJump)
                player.jumping = true;
            if (c == settings.keyDuck)
                player.duck = true;
            if (c == settings.keyDash && !player.isDashing && (devMode || player.dashCooldown == 0)) {
                player.isDashing = true;
                player.dashTimer = 0;
                // Mage is more agile — slightly shorter dash cooldown (45 ticks vs 60)
                player.dashCooldown = devMode ? 0 : (player instanceof Mage ? 45 : Player.DASH_COOLDOWN);
            }
        }
        if (c == settings.keyItem1)
            selectedItem = 1;
        if (c == settings.keyItem2)
            selectedItem = 2;
        if (c == settings.keyItem3)
            selectedItem = 3;
        if (c == settings.keyItem3 + 1)
            selectedItem = 4;
        if (c == KeyEvent.VK_E) {
            if (shopOpen) {
                shopOpen = false;
                resumeBgMusic();
                SoundManager.setGamePaused(false);
            } else {
                shopOpen = true;
                shopHoveredSlot = -1;
                pauseBgMusic();
                SoundManager.setGamePaused(true);
            }
            return;
        }
    }

    @Override
    public void keyReleased(KeyEvent e) {
        int c = e.getKeyCode();
        if (c == settings.keyLeft)
            player.moveLeft = false;
        if (c == settings.keyRight)
            player.moveRight = false;
        if (c == settings.keyJump) {
            player.jumping = false;
            player.jumpHeld = false;
        }
        if (c == settings.keyDuck)
            player.duck = false;
    }

    private void drawAdvanceHint(Graphics2D g2) {
        // Pulse alpha so the hint blinks (using bgTick which increments every frame)
        float pulse = (float) (0.55 + 0.45 * Math.sin(bgTick * 0.08));
        int alpha = (int) (pulse * 240);

        String hint = "Go right to advance!  \u2192";
        g2.setFont(HINT_FONT);
        FontMetrics fm = g2.getFontMetrics();
        int tw = fm.stringWidth(hint);

        // Position: lower-right area, above the HUD bottom strip
        int hx = BASE_W - tw - 24;
        int hy = BASE_H - 52;

        // Shadow
        g2.setColor(new Color(0, 0, 0, (int) (alpha * 0.7)));
        g2.drawString(hint, hx + 2, hy + 2);

        // Main text in bright gold
        g2.setColor(new Color(255, 220, 60, alpha));
        g2.drawString(hint, hx, hy);

        // Small right-pointing arrow indicator on the right edge
        int arrowPulse = (int) (pulse * 200);
        g2.setColor(new Color(255, 220, 60, arrowPulse));
        int ax = BASE_W - 14, ay = BASE_H / 2;
        int[] arrowX = { ax - 14, ax - 14, ax };
        int[] arrowY = { ay - 18, ay + 18, ay };
        g2.fillPolygon(arrowX, arrowY, 3);
    }

    private void drawNewGamePlusScreen(Graphics2D g2) {
        int W = BASE_W, H = BASE_H;
        float progress = newGamePlusTimer / (float) NEW_GAME_PLUS_DELAY;
        float fade = Math.min(1f, progress * 3f); // fade in quickly

        // Dark full-screen overlay
        g2.setColor(new Color(0, 0, 0, (int) (fade * 220)));
        g2.fillRect(0, 0, W, H);

        // Purple / demonic vignette
        g2.setPaint(new java.awt.RadialGradientPaint(W / 2f, H / 2f, Math.max(W, H) * 0.6f,
                new float[] { 0f, 1f },
                new Color[] { new Color(0, 0, 0, 0), new Color(80, 0, 120, (int) (fade * 180)) }));
        g2.fillRect(0, 0, W, H);

        // Main heading
        g2.setFont(NGP_FONT1);
        FontMetrics fm = g2.getFontMetrics();
        String line1 = "SITAN DEFEATED!";
        int lx1 = W / 2 - fm.stringWidth(line1) / 2;
        g2.setColor(new Color(0, 0, 0, (int) (fade * 200)));
        g2.drawString(line1, lx1 + 3, H / 2 - 50);
        g2.setColor(new Color(220, 60, 60, (int) (fade * 255)));
        g2.drawString(line1, lx1, H / 2 - 52);

        // Sub-heading
        g2.setFont(NGP_FONT2);
        FontMetrics fm2 = g2.getFontMetrics();
        String line2 = "NEW GAME +" + newGamePlusCount;
        int lx2 = W / 2 - fm2.stringWidth(line2) / 2;
        g2.setColor(new Color(0, 0, 0, (int) (fade * 200)));
        g2.drawString(line2, lx2 + 2, H / 2 + 4);
        g2.setColor(new Color(220, 180, 50, (int) (fade * 240)));
        g2.drawString(line2, lx2, H / 2 + 2);

        // Flavour text
        g2.setFont(NGP_FONT3);
        FontMetrics fm3 = g2.getFontMetrics();
        String line3 = "The cycle continues... return to the beginning.";
        int lx3 = W / 2 - fm3.stringWidth(line3) / 2;
        g2.setColor(new Color(180, 140, 220, (int) (fade * 200)));
        g2.drawString(line3, lx3, H / 2 + 46);

        // Countdown bar
        int barW = W / 3, barH = 8;
        int barX = W / 2 - barW / 2, barY = H / 2 + 72;
        float remaining = 1f - progress;
        g2.setColor(new Color(40, 20, 60, 200));
        g2.fillRoundRect(barX, barY, barW, barH, 6, 6);
        g2.setPaint(new java.awt.GradientPaint(barX, barY, new Color(160, 60, 220), barX + barW, barY,
                new Color(220, 60, 60)));
        g2.fillRoundRect(barX, barY, (int) (barW * remaining), barH, 6, 6);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(200, 160, 255, 160));
        g2.drawRoundRect(barX, barY, barW, barH, 6, 6);
    }

    @Override
    public void keyTyped(KeyEvent e) {
    }

    @Override
    public void mouseReleased(MouseEvent e) {
    }

    @Override
    public void mouseClicked(MouseEvent e) {
    }

    @Override
    public void mouseEntered(MouseEvent e) {
    }

    @Override
    public void mouseExited(MouseEvent e) {
    }
}
