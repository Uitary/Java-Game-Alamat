import java.io.*;
import java.text.SimpleDateFormat;
import java.util.*;

/**
 * PlayerProfile — arcade leaderboard.
 * Up to MAX_PLAYERS (100) profiles, MAX_ATTEMPTS (100) attempts each.
 */
public class PlayerProfile {

    public String name = "";

    private final List<List<int[]>> attempts = new ArrayList<>();

    private static final String SAVE_DIR     = "saves";
    public  static final int    MAX_PLAYERS  = 100;
    public  static final int    MAX_ATTEMPTS = 100;

    public PlayerProfile() {
        for (int i = 0; i < 3; i++) attempts.add(new ArrayList<>());
    }

    public void save() {
        new File(SAVE_DIR).mkdirs();
        Properties p = new Properties();
        p.setProperty("name", name);
        for (int d = 0; d < 3; d++) {
            List<int[]> da = attempts.get(d);
            p.setProperty("count_" + d, String.valueOf(da.size()));
            for (int i = 0; i < da.size(); i++) {
                int[] a = da.get(i);
                p.setProperty("d" + d + "_" + i, a[0]+","+a[1]+","+a[2]+","+a[3]);
            }
        }
        try (FileOutputStream out = new FileOutputStream(SAVE_DIR + "/" + name + ".properties")) {
            p.store(out, "Alamat: " + name);
        } catch (IOException e) { e.printStackTrace(); }
    }

    public static PlayerProfile load(String name) {
        PlayerProfile prof = new PlayerProfile();
        prof.name = name;
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(SAVE_DIR + "/" + name + ".properties")) {
            p.load(in);
            for (int d = 0; d < 3; d++) {
                int count = Integer.parseInt(p.getProperty("count_" + d, "0"));
                for (int i = 0; i < count; i++) {
                    String[] parts = p.getProperty("d" + d + "_" + i, "0,0,1,0").split(",");
                    prof.attempts.get(d).add(new int[]{
                        Integer.parseInt(parts[0]), Integer.parseInt(parts[1]),
                        Integer.parseInt(parts[2]),
                        parts.length > 3 ? Integer.parseInt(parts[3]) : 0
                    });
                }
            }
        } catch (IOException ignored) {}
        return prof;
    }

    public void addAttempt(int score, int coins, int level, int difficulty) {
        int ts = (int)(System.currentTimeMillis() / 1000L);
        List<int[]> da = attempts.get(difficulty);
        da.add(new int[]{ score, coins, level, ts });
        if (da.size() > MAX_ATTEMPTS) {
            da.sort((a, b) -> b[0] - a[0]);
            while (da.size() > MAX_ATTEMPTS) da.remove(da.size() - 1);
        }
        save();
    }

    public int bestScore() {
        int best = 0;
        for (int d = 0; d < 3; d++) for (int[] a : attempts.get(d)) best = Math.max(best, a[0]);
        return best;
    }

    public int bestScore(int diff) {
        return attempts.get(diff).stream().mapToInt(a -> a[0]).max().orElse(0);
    }

    public List<int[]> getAttempts(int diff) {
        List<int[]> copy = new ArrayList<>(attempts.get(diff));
        copy.sort((a, b) -> b[0] - a[0]);
        return copy;
    }

    public static List<PlayerProfile> loadAll() {
        List<PlayerProfile> list = new ArrayList<>();
        File dir = new File(SAVE_DIR);
        if (!dir.exists()) return list;
        File[] files = dir.listFiles((d, n) -> n.endsWith(".properties"));
        if (files == null) return list;
        for (File f : files) list.add(load(f.getName().replace(".properties", "")));
        list.sort((a, b) -> b.bestScore() - a.bestScore());
        return list;
    }

    public static boolean nameExists(String name) {
        return new File(SAVE_DIR + "/" + name + ".properties").exists();
    }

    public static String formatDate(int epochSec) {
        if (epochSec == 0) return "—";
        return new SimpleDateFormat("MM/dd HH:mm").format(new Date((long)epochSec * 1000L));
    }
}
