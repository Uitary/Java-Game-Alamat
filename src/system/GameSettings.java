import java.awt.event.KeyEvent;
import java.io.*;
import java.util.Properties;

public class GameSettings {
    public boolean fullscreen = false;
    public int screenWidth = 800;
    public int screenHeight = 600;

    public int keyLeft = KeyEvent.VK_A;
    public int keyRight = KeyEvent.VK_D;
    public int keyJump = KeyEvent.VK_W;
    public int keyDuck = KeyEvent.VK_S;
    public int keyDash = KeyEvent.VK_SPACE; // Added Dash
    public int keyItem1 = KeyEvent.VK_1;
    public int keyItem2 = KeyEvent.VK_2;
    public int keyItem3 = KeyEvent.VK_3;

    public int masterVolume = 80;
    public boolean sfxEnabled = true;
    public boolean musicEnabled = true;
    public int difficulty = 1;
    public boolean showHitboxes = false;

    private static final String SETTINGS_FILE = "settings.properties";

    public static GameSettings load() {
        GameSettings s = new GameSettings();
        Properties p = new Properties();
        try (FileInputStream in = new FileInputStream(SETTINGS_FILE)) {
            p.load(in);
            s.fullscreen      = Boolean.parseBoolean(p.getProperty("fullscreen", "false"));
            s.screenWidth     = Integer.parseInt(p.getProperty("screenWidth", "800"));
            s.screenHeight    = Integer.parseInt(p.getProperty("screenHeight", "600"));
            s.keyLeft         = Integer.parseInt(p.getProperty("keyLeft",  String.valueOf(KeyEvent.VK_A)));
            s.keyRight        = Integer.parseInt(p.getProperty("keyRight", String.valueOf(KeyEvent.VK_D)));
            s.keyJump         = Integer.parseInt(p.getProperty("keyJump",  String.valueOf(KeyEvent.VK_W)));
            s.keyDuck         = Integer.parseInt(p.getProperty("keyDuck",  String.valueOf(KeyEvent.VK_S)));
            s.keyDash         = Integer.parseInt(p.getProperty("keyDash",  String.valueOf(KeyEvent.VK_SPACE))); // Added Dash
            s.keyItem1        = Integer.parseInt(p.getProperty("keyItem1", String.valueOf(KeyEvent.VK_1)));
            s.keyItem2        = Integer.parseInt(p.getProperty("keyItem2", String.valueOf(KeyEvent.VK_2)));
            s.keyItem3        = Integer.parseInt(p.getProperty("keyItem3", String.valueOf(KeyEvent.VK_3)));
            s.masterVolume    = Integer.parseInt(p.getProperty("masterVolume", "80"));
            s.sfxEnabled      = Boolean.parseBoolean(p.getProperty("sfxEnabled", "true"));
            s.musicEnabled    = Boolean.parseBoolean(p.getProperty("musicEnabled", "true"));
            s.difficulty      = Integer.parseInt(p.getProperty("difficulty", "1"));
            s.showHitboxes    = Boolean.parseBoolean(p.getProperty("showHitboxes", "false"));
        } catch (IOException e) { }
        return s;
    }

    public void save() {
        Properties p = new Properties();
        p.setProperty("fullscreen",    String.valueOf(fullscreen));
        p.setProperty("screenWidth",   String.valueOf(screenWidth));
        p.setProperty("screenHeight",  String.valueOf(screenHeight));
        p.setProperty("keyLeft",       String.valueOf(keyLeft));
        p.setProperty("keyRight",      String.valueOf(keyRight));
        p.setProperty("keyJump",       String.valueOf(keyJump));
        p.setProperty("keyDuck",       String.valueOf(keyDuck));
        p.setProperty("keyDash",       String.valueOf(keyDash)); // Added Dash
        p.setProperty("keyItem1",      String.valueOf(keyItem1));
        p.setProperty("keyItem2",      String.valueOf(keyItem2));
        p.setProperty("keyItem3",      String.valueOf(keyItem3));
        p.setProperty("masterVolume",  String.valueOf(masterVolume));
        p.setProperty("sfxEnabled",    String.valueOf(sfxEnabled));
        p.setProperty("musicEnabled",  String.valueOf(musicEnabled));
        p.setProperty("difficulty",    String.valueOf(difficulty));
        p.setProperty("showHitboxes",  String.valueOf(showHitboxes));
        try (FileOutputStream out = new FileOutputStream(SETTINGS_FILE)) {
            p.store(out, "Alamat: Survival Night - Settings");
        } catch (IOException e) { e.printStackTrace(); }
    }

    public String keyName(int code) { return KeyEvent.getKeyText(code); }
}