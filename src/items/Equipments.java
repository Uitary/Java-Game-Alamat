import java.awt.Rectangle;

/**
 * Equipments — all player gear.
 *
 * Defense replaced with MaxHP:
 *   Each upgrade level raises the player's effective damage reduction
 *   by lowering incoming damage (not shown as %). Max 10 levels + 10 ascended (20 total).
 *   The HP bar stays at 100 display, but each level reduces damage by 4% (cap 80%).
 */
public class Equipments {

    // ─── Sword ────────────────────────────────────────────────────────────────
    public static class Sword {
        public int     level  = 0;
        public boolean active = false;
        public int     timer  = 0;

        public int    getDamage() { return 10 + level * 4; }
        public String getTier()   { return level >= 10 ? "Asc.Lv" + (level - 9) : "Lv" + (level + 1); }
        public void   use()       { active = true; timer = 0; }
        public void   update()    { if (active && ++timer > 10) { active = false; timer = 0; } }
    }

    // ─── Bow — burst fire ─────────────────────────────────────────────────────
    public static class Bow {
        public int     level    = 0;
        public boolean active   = false;
        public int     timer    = 0;
        public int     atkFrame = 0;

        public int     shotsFired    = 0;
        public boolean reloading     = false;
        public int     reloadTimer   = 0;

        public static final int BURST_SIZE     = 5;
        public static final int TICKS_PER_SHOT = 6;
        public static final int RELOAD_TICKS   = 60;

        public int    getDamage() { return 15 + level * 4; }
        public String getTier()   { return level >= 10 ? "Asc.Lv" + (level - 9) : "Lv" + (level + 1); }

        public void use() {
            if (active || reloading) return;
            active = true; timer = 0; shotsFired = 0; atkFrame = 0;
        }

        public boolean update() {
            if (reloading) {
                if (++reloadTimer >= RELOAD_TICKS) { reloading = false; reloadTimer = 0; }
                return false;
            }
            if (!active) return false;
            timer++;
            atkFrame = (timer / 4) % 2;
            if (timer % TICKS_PER_SHOT == 1 && shotsFired < BURST_SIZE) {
                shotsFired++;
                if (shotsFired >= BURST_SIZE) { active = false; timer = 0; reloading = true; reloadTimer = 0; }
                return true;
            }
            if (timer > TICKS_PER_SHOT * BURST_SIZE + 5) { active = false; timer = 0; }
            return false;
        }

        public boolean isReady() { return !active && !reloading; }
    }

    // ─── Potion ───────────────────────────────────────────────────────────────
    public static class Potion {
        public int     count      = 3;
        public static final int MAX_POTIONS = 30;
        public boolean active     = false;
        public int     timer      = 0;

        public void use()    { if (count > 0) { count--; active = true; timer = 0; } }
        public void update() { if (active && ++timer > 20) { active = false; timer = 0; } }
    }

    // ─── MaxHP (replaces Defense) ─────────────────────────────────────────────
    /**
     * Upgrades lower enemy damage to the player by 4% per level (cap 80%).
     * HP bar stays at 100 — the mitigation is invisible but real.
     */
    public static class MaxHP {
        public int level = 0;
        public static final int MAX_LEVEL      = 20;
        public static final float DR_PER_LEVEL = 0.04f;  // 4% per level

        /** Returns mitigated damage (min 1). */
        public int mitigate(int rawDamage) {
            float dr = Math.min(level * DR_PER_LEVEL, 0.80f);
            return Math.max(1, (int)(rawDamage * (1f - dr)));
        }

        public String getTier()    { return level >= 10 ? "Asc.Lv" + (level - 9) : "Lv" + (level + 1); }
        public String getLabel()   { return level == 0 ? "No upgrade" : "-" + (int)(Math.min(level * DR_PER_LEVEL * 100, 80)) + "% dmg"; }
    }

    // ─── Arrow ────────────────────────────────────────────────────────────────
    public static class Arrow {
        public int x, y, dx;
        public Arrow(int x, int y, int dx) { this.x = x; this.y = y; this.dx = dx; }
        public void      update()    { x += dx; }
        public Rectangle getHitbox() { return new Rectangle(x, y, 10, 5); }
    }
}
