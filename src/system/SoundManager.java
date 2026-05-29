import javax.sound.sampled.*;
import java.io.File;
import java.util.concurrent.ConcurrentHashMap;

/**
 * SoundManager — lightweight fire-and-forget sound utility.
 *
 * Each call opens a new Clip on a daemon thread so the game loop is never
 * blocked.  A per-path cooldown map prevents the same sound from stacking
 * when many enemies hit the player in the same frame.
 */
public class SoundManager {

    /** Minimum gap (ms) used by playCooldown() when the caller doesn't specify one. */
    private static final int DEFAULT_COOLDOWN_MS = 150;

    /** Records the last System.currentTimeMillis() a path was played. */
    private static final ConcurrentHashMap<String, Long> lastPlayTime = new ConcurrentHashMap<>();

    /** All clips currently open so we can stop them all at once (e.g. on exit). */
    private static final java.util.Set<javax.sound.sampled.Clip> activeClips =
            java.util.Collections.newSetFromMap(new ConcurrentHashMap<>());

    /** When true, new SFX clips are suppressed (game is paused). */
    private static volatile boolean gamePaused = false;

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Play a WAV file once with no cooldown.
     * Silently ignores missing or unreadable files.
     */
    public static void play(String path) {
        startClip(path);
    }

    /**
     * Immediately stop and close every clip that is currently playing.
     * Call this when exiting gameplay so no enemy sounds bleed into the menu.
     */
    public static void stopAll() {
        for (javax.sound.sampled.Clip c : activeClips) {
            try { c.stop(); c.close(); } catch (Exception ignored) {}
        }
        activeClips.clear();
        lastPlayTime.clear();
    }

    /**
     * Pause or resume all sound effects together with the game pause state.
     * <p>
     * When {@code paused} is {@code true} every clip that is currently playing
     * is stopped immediately, and any subsequent {@link #play} / {@link #playCooldown}
     * calls are silently ignored until this method is called with {@code false}.
     * <p>
     * Background music is managed separately by GamePanel (it owns that Clip).
     */
    public static void setGamePaused(boolean paused) {
        gamePaused = paused;
        if (paused) {
            for (javax.sound.sampled.Clip c : activeClips) {
                try { c.stop(); c.close(); } catch (Exception ignored) {}
            }
            activeClips.clear();
            lastPlayTime.clear();
        }
    }

    /**
     * Play a WAV file once, but only if at least {@code cooldownMs} milliseconds
     * have passed since the last play of this same path.
     * Useful for hit-sounds and damage events that can fire many times per second.
     */
    public static void playCooldown(String path, int cooldownMs) {
        long now = System.currentTimeMillis();
        Long last = lastPlayTime.get(path);
        if (last != null && now - last < cooldownMs) return;
        lastPlayTime.put(path, now);
        startClip(path);
    }

    /**
     * Convenience overload using {@value #DEFAULT_COOLDOWN_MS} ms cooldown.
     */
    public static void playCooldown(String path) {
        playCooldown(path, DEFAULT_COOLDOWN_MS);
    }

    // ── Internal helpers ──────────────────────────────────────────────────────

    private static void startClip(String path) {
        if (gamePaused) return;   // ← suppress all SFX while the game is paused
        // Offload to a short-lived daemon thread — never blocks the EDT or game loop.
        Thread t = new Thread(() -> {
            try {
                File f = new File(path);
                if (!f.exists()) return;

                AudioInputStream rawAis = AudioSystem.getAudioInputStream(f);

                // Convert to a format the system Clip can always handle
                AudioFormat srcFmt = rawAis.getFormat();
                AudioFormat pcm = new AudioFormat(
                        AudioFormat.Encoding.PCM_SIGNED,
                        srcFmt.getSampleRate(),
                        16,
                        srcFmt.getChannels(),
                        srcFmt.getChannels() * 2,
                        srcFmt.getSampleRate(),
                        false);

                AudioInputStream pcmAis = AudioSystem.getAudioInputStream(pcm, rawAis);

                Clip clip = AudioSystem.getClip();
                clip.open(pcmAis);
                activeClips.add(clip);

                // Auto-close the clip when it finishes so native resources are freed immediately
                clip.addLineListener(ev -> {
                    if (ev.getType() == LineEvent.Type.STOP) {
                        activeClips.remove(clip);
                        clip.close();
                    }
                });

                clip.start();
            } catch (Exception ignored) {
                // Missing file, unsupported format, or no audio device — silently skip
            }
        });
        t.setDaemon(true);
        t.start();
    }
}
