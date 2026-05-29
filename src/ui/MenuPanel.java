import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import javax.imageio.ImageIO;
import javax.sound.sampled.*;

public class MenuPanel extends JPanel implements ActionListener {

    private final JFrame   window;
    private GameSettings   settings;
    private PlayerProfile  activeProfile = null;
    private String         selectedChar  = "warrior";

    // ── Animation timer ───────────────────────────────────────────────────────
    private Timer animTimer;

    // ── Screens ───────────────────────────────────────────────────────────────
    private String screen       = "main";
    private String nameInput    = "", nameError = "";
    private List<PlayerProfile> profileList = null;
    private int lbTab    = 0;   // 0=easy,1=normal,2=hard
    private int lbScroll = 0;   // row offset for leaderboard scrolling

    // ── Background GIF ────────────────────────────────────────────────────────
    /** Animated GIF loaded as an ImageIcon so Swing ticks its frames automatically. */
    private ImageIcon bgGif;

    // ── Difficulty icons ──────────────────────────────────────────────────────
    private BufferedImage[] diffIcons = new BufferedImage[3];

    // ── Character idle images ─────────────────────────────────────────────────
    private BufferedImage[] charImages = new BufferedImage[3];

    // ── Dev images ────────────────────────────────────────────────────────────
    private BufferedImage[] devImages = new BufferedImage[3];

    // ── Pixel-art palette ────────────────────────────────────────────────────
    // All colours are hard-edged, no anti-aliasing on shapes — pixel aesthetic.
    private static final Color PX_BG      = new Color(10, 8, 24);        // dark navy
    private static final Color PX_PANEL   = new Color(20, 16, 44);       // panel fill
    private static final Color PX_BORDER  = new Color(88, 72, 200);      // blue-purple border
    private static final Color PX_GOLD    = new Color(248, 208, 48);     // bright pixel gold
    private static final Color PX_GOLD2   = new Color(200, 160, 32);     // darker gold
    private static final Color PX_WHITE   = new Color(232, 228, 248);    // off-white
    private static final Color PX_MUTED   = new Color(120, 110, 160);    // muted lavender
    private static final Color PX_RED     = new Color(232, 56, 56);
    private static final Color PX_GREEN   = new Color(56, 200, 88);
    private static final Color PX_HOVER   = new Color(64, 52, 144);      // hover fill
    private static final Color PX_SEL     = new Color(80, 64, 180);      // selected fill
    private static final Color PX_SHADOW  = new Color(0, 0, 0, 160);

    // Button dimensions
    private static final int BW = 256, BH = 40;

    // ── Pixel font simulation (uses a crisp monospaced font) ─────────────────
    /** Title font: chunky pixel-like serif bold. */
    private Font titleFont;
    /** Menu button font. */
    private Font menuFont;
    /** Sub-label font. */
    private Font subFont;

    private int hoveredBtn = -1;
    private int animTick   = 0;   // incremented every 16 ms for animations

    // ── Background music ──────────────────────────────────────────────────────
    private Clip musicClip;

    // ─────────────────────────────────────────────────────────────────────────
    public MenuPanel(JFrame window, GameSettings settings) {
        this.window = window; this.settings = settings;
        // Force fixed fullscreen — detect actual screen dimensions
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        settings.screenWidth  = (int) scr.getWidth();
        settings.screenHeight = (int) scr.getHeight();
        settings.fullscreen   = true;
        // Only call setUndecorated() before the window has a native peer.
        // Calling it on an already-visible frame either throws
        // IllegalComponentStateException (crashing the MenuPanel constructor)
        // or silently disposes/recreates the native peer, which resets the
        // fullscreen state so that setFullScreenWindow() is called again —
        // blocking the EDT and causing the "Main Menu" freeze.
        if (!window.isDisplayable()) {
            window.setUndecorated(true);
        }
        java.awt.GraphicsDevice gd = java.awt.GraphicsEnvironment
            .getLocalGraphicsEnvironment().getDefaultScreenDevice();
        // Guard against calling setFullScreenWindow() when the window is already
        // the active fullscreen window.  Re-entering fullscreen on an already-
        // fullscreen window blocks the EDT on many platforms, causing the freeze
        // seen when the "Main Menu" button is pressed during gameplay.
        if (gd.isFullScreenSupported() && gd.getFullScreenWindow() != window)
            gd.setFullScreenWindow(window);
        setPreferredSize(new Dimension(settings.screenWidth, settings.screenHeight));
        setBackground(PX_BG);
        setLayout(null);
        setFocusable(true);
        // Grab keyboard focus reliably in fullscreen — requestFocusInWindow()
        // is often ignored before the component is actually showing.
        addHierarchyListener(ev -> {
            if ((ev.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing()) {
                requestFocus();
            }
        });

        // Pixel-ish fonts — Courier New / Dialog as universally available crisp alternatives
        titleFont = new Font("Courier New", Font.BOLD, 67);
        menuFont  = new Font("Courier New", Font.BOLD, 18);
        subFont   = new Font("Courier New", Font.PLAIN, 13);

        // Set custom window icon (overrides default Java coffee-cup icon)
        File iconFile = new File("assets/logo.jpg");
        if (iconFile.exists()) {
            window.setIconImage(new ImageIcon(iconFile.getAbsolutePath()).getImage());
        }

        loadBgGif();
        loadDiffIcons();
        loadCharImages();
        loadDevImages();

        animTimer = new Timer(16, this);
        animTimer.start();
        playMusic();

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hoveredBtn = hovered(e.getX(), e.getY()); repaint(); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) { click(e.getX(), e.getY()); }
        });
        addMouseWheelListener(e -> {
            if (screen.equals("leaderboard")) {
                lbScroll = Math.max(0, lbScroll + (e.getWheelRotation() > 0 ? 3 : -3));
                repaint();
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                if (screen.equals("nameEntry")) { nameKey(e); return; }
                int k = e.getKeyCode();
                if (screen.equals("leaderboard")) {
                    if (k==KeyEvent.VK_UP)   { lbScroll=Math.max(0,lbScroll-3); repaint(); return; }
                    if (k==KeyEvent.VK_DOWN) { lbScroll+=3; repaint(); return; }
                }
                if (k==KeyEvent.VK_ESCAPE && !screen.equals("main")) {
                    lbScroll = 0;
                    screen = switch (screen) {
                        case "leaderboard","nameEntry","charSelect","difficulty","about" -> "main";
                        default -> "main";
                    };
                    repaint();
                }
            }
        });
    }

    public void setActiveProfile(PlayerProfile p) { activeProfile = p; }

    // ─── Music ────────────────────────────────────────────────────────────────
    private void playMusic() {
        if (musicClip != null && musicClip.isRunning()) return; // already playing — don't double-start
        try {
            InputStream is = getClass().getResourceAsStream("/sounds/system/MenuMusic.wav");
            if (is == null) { System.err.println(""); return; }
            AudioInputStream ais = AudioSystem.getAudioInputStream(is);
            musicClip = AudioSystem.getClip();
            musicClip.open(ais);
            musicClip.loop(Clip.LOOP_CONTINUOUSLY);
            musicClip.start();
        } catch (Exception e) { e.printStackTrace(); }
    }

    private void stopMusic() {
        if (musicClip != null && musicClip.isRunning()) { musicClip.stop(); musicClip.close(); }
    }

    // ─── Asset loading ────────────────────────────────────────────────────────
    private void loadBgGif() {
        File f = new File("assets/MainMenu_Bg.gif");
        if (f.exists()) bgGif = new ImageIcon(f.getAbsolutePath());
    }

    private void loadDiffIcons() {
        String[] names = {"Easy_Icon","Medium_Icon","Hard_Icon"};
        for (int i=0;i<3;i++) {
            try { diffIcons[i] = ImageIO.read(new File("assets/Icons/"+names[i]+".png")); }
            catch (IOException ignored) {}
        }
    }

    private void loadCharImages() {
        String[] paths = {
            "assets/Character/Player/Idle_KF1.png",
            "assets/Character/Mage/Mage_Idle_KF1.png",
            "assets/Character/Fighter/Fighter_Idle_KF1.png"
        };
        for (int i=0;i<3;i++) {
            try { charImages[i] = ImageIO.read(new File(paths[i])); }
            catch (IOException ignored) {}
        }
    }

    private void loadDevImages() {
        String[] paths = {
            "assets/dev/devkaizer.jpg",
            "assets/dev/devsteven.jpg",
            "assets/dev/devstephen.png"
        };
        // Try common extensions if no extension works
        String[][] exts = {{""},{"png"},{"jpg"},{"jpeg"}};
        for (int i=0;i<3;i++) {
            // First try with .png
            File f = new File(paths[i]);
            if (!f.exists()) {
                // Try without extension (exact path given by user)
                String base = paths[i].replaceAll("\\.[^.]+$","");
                for (String ext : new String[]{"png","jpg","jpeg","PNG","JPG"}) {
                    f = new File(base+"."+ext);
                    if (f.exists()) break;
                    // Try the path as-is (no extension)
                    f = new File(base);
                    if (f.exists()) break;
                }
            }
            try { devImages[i] = ImageIO.read(f); }
            catch (IOException ignored) {}
        }
    }

    @Override public void actionPerformed(ActionEvent e) { animTick++; repaint(); }

    // ─── Paint ────────────────────────────────────────────────────────────────
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        // Pixel art: disable anti-aliasing on edges, keep text crisp
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int W = getWidth(), H = getHeight();

        // ── Background ────────────────────────────────────────────────────────
        if (bgGif != null) {
            g2.drawImage(bgGif.getImage(), 0, 0, W, H, this);
        } else {
            // Fallback: simple pixel-style gradient
            for (int y=0; y<H; y+=2) {
                float t = y/(float)H;
                int r=(int)(10+t*6), gv=(int)(8+t*4), b=(int)(24+t*20);
                g2.setColor(new Color(r,gv,b));
                g2.fillRect(0, y, W, 2);
            }
            // Pixel scanline overlay
            g2.setColor(new Color(0,0,0,40));
            for (int y=0; y<H; y+=4) g2.fillRect(0, y, W, 2);
        }

        // Dark overlay to keep UI readable over GIF
        g2.setColor(new Color(0,0,0,120));
        g2.fillRect(0,0,W,H);

        switch (screen) {
            case "main"        -> drawMain(g2, W, H);
            case "nameEntry"   -> drawNameEntry(g2, W, H);
            case "charSelect"  -> drawCharSelect(g2, W, H);
            case "difficulty"  -> drawDifficulty(g2, W, H);
            case "leaderboard" -> drawLeaderboard(g2, W, H);
            case "about"       -> drawAbout(g2, W, H);
        }
    }

    // ─── MAIN MENU ────────────────────────────────────────────────────────────
    private static final String[] MAIN_BTNS = {"PLAY","TUTORIAL","LEADERBOARD","ABOUT","QUIT"};
    private int mainBtnY(int i) { return (int)(getHeight()*.48f)+i*52; }

    private void drawMain(Graphics2D g2, int W, int H) {
        // Title with pixel shadow
        g2.setFont(titleFont);
        FontMetrics fm = g2.getFontMetrics();
        String title = "ALAMAT";
        int tx = W/2 - fm.stringWidth(title)/2;
        int ty = H/4 + 10;
        // Hard pixel shadow (offset by 3,3)
        g2.setColor(new Color(0,0,0,220));
        g2.drawString(title, tx+3, ty+3);
        // Colour layers for pixel glow effect
        g2.setColor(new Color(160,80,0));
        g2.drawString(title, tx+1, ty+1);
        g2.setColor(PX_GOLD);
        g2.drawString(title, tx, ty);

        // Subtitle
        g2.setFont(new Font("Courier New", Font.BOLD, 16));
        g2.setColor(PX_WHITE);
        cent(g2, "SURVIVAL NIGHT", W/2, H/4 + 38);
        g2.setFont(subFont);
        g2.setColor(PX_MUTED);
        cent(g2, "Harapin ang dilim. Puksain ang kasamaan", W/2, H/4 + 58);

        if (activeProfile != null) {
            g2.setFont(subFont);
            g2.setColor(PX_GOLD);
            cent(g2, "[ "+activeProfile.name+" ]  BEST: "+activeProfile.bestScore(), W/2, H/4 + 78);
        }

        // Pixel divider line
        g2.setColor(PX_BORDER);
        g2.fillRect(W/2-120, H/4+90, 240, 2);

        for (int i=0;i<MAIN_BTNS.length;i++) pxBtn(g2, MAIN_BTNS[i], W/2, mainBtnY(i), i==hoveredBtn);

        // Version tag
        g2.setFont(new Font("Courier New", Font.PLAIN, 10));
        g2.setColor(PX_MUTED);
        g2.drawString("v1.0", 8, H-8);
    }

    // ─── NAME ENTRY ───────────────────────────────────────────────────────────
    private void drawNameEntry(Graphics2D g2, int W, int H) {
        pxTitle(g2, "ENTER NAME", W, H);
        int pW=400, pH=180, px=W/2-pW/2, py=H/2-70;
        pxPanel(g2, px, py, pW, pH);
        g2.setFont(subFont); g2.setColor(PX_MUTED);
        g2.drawString("Enter your name (max 12 chars):", px+16, py+28);
        // Input box
        g2.setColor(new Color(8,6,20)); g2.fillRect(px+16, py+38, pW-32, 38);
        g2.setColor(PX_BORDER);        g2.drawRect(px+16, py+38, pW-32, 38);
        g2.setFont(menuFont); g2.setColor(PX_WHITE);
        g2.drawString(nameInput+"|", px+22, py+65);
        if (!nameError.isEmpty()) {
            g2.setFont(subFont); g2.setColor(PX_RED);
            g2.drawString(nameError, px+16, py+96);
        }
        g2.setFont(subFont); g2.setColor(PX_MUTED);
        g2.drawString("(Leave blank = Guest)", px+16, py+112);

        int btnBase = py + pH + 30;
        pxBtn(g2, "CONTINUE", W/2, btnBase,      hoveredBtn==20);
        pxBtn(g2, "BACK",     W/2, btnBase + 52, hoveredBtn==21);
    }

    private void nameKey(KeyEvent e) {
        int c = e.getKeyCode();
        if      (c==KeyEvent.VK_BACK_SPACE && !nameInput.isEmpty()) { nameInput=nameInput.substring(0,nameInput.length()-1); nameError=""; }
        else if (c==KeyEvent.VK_ENTER)  commitName();
        else if (c==KeyEvent.VK_ESCAPE) { screen="main"; nameInput=""; nameError=""; }
        else { char ch=e.getKeyChar(); if ((Character.isLetterOrDigit(ch)||ch=='_')&&nameInput.length()<12) nameInput+=ch; }
        repaint();
    }

    private void commitName() {
        String n = nameInput.trim(); if (n.isEmpty()) n="Guest";
        if (PlayerProfile.nameExists(n)) activeProfile = PlayerProfile.load(n);
        else {
            if (PlayerProfile.loadAll().size()>=PlayerProfile.MAX_PLAYERS) { nameError="Max 10 players reached."; return; }
            activeProfile = new PlayerProfile(); activeProfile.name=n; activeProfile.save();
        }
        nameInput=""; nameError="";
        // Dev/test names skip char select and go straight to difficulty
        if (DEV_NAMES.contains(n)) { selectedChar = "warrior"; screen = "difficulty"; }
        else screen = "charSelect";
    }

    /** Secret names that unlock dev/test mode in-game. */
    private static final java.util.Set<String> DEV_NAMES = java.util.Set.of(
        "Ivankaizer", "StevenKenn", "JamesStephen"
    );

    // ─── CHARACTER SELECT ─────────────────────────────────────────────────────
    private static final String[] CHARS      = {"warrior","mage","fighter"};
    private static final String[] CHAR_LABELS= {"WARRIOR","MAGE","MARTIAL ARTIST"};
    private static final String[] CHAR_DESC  = {
        "Sword & Bow.\nBalanced fighter.",
        "Magic Bolt + Beam.\nLong-range caster.",
        "Punch + Dash Strike.\nAggressive brawler."
    };
    // Per-class colour themes (accent, glow)
    private static final Color[] CHAR_ACCENT = {
        new Color(70, 120, 230),   // warrior – steel blue
        new Color(160, 60, 230),   // mage – arcane purple
        new Color(220, 80, 50)     // fighter – ember red
    };
    // ATK / RNG / DEF  (0-100 scale for mini bars)
    private static final int[][] CHAR_STATS = {
        {70, 55, 80},   // warrior
        {55, 100, 40},  // mage
        {95, 30, 60}    // fighter
    };
    private static final String[] STAT_LABELS = {"ATK","RNG","DEF"};

    private void drawCharSelect(Graphics2D g2, int W, int H) {
        pxTitle(g2, "SELECT CLASS", W, H);

        final int cW = 150, cH = 220, gap = 36;
        final int tW  = 3 * cW + 2 * gap;
        final int sx  = W / 2 - tW / 2;
        final int baseY = H / 2 - cH / 2 - 10;

        for (int i = 0; i < 3; i++) {
            boolean sel = selectedChar.equals(CHARS[i]);
            boolean hov = hoveredBtn == i;
            Color accent = CHAR_ACCENT[i];

            // Selected card floats up with a sine wave
            float floatOff = sel ? (float) Math.sin(animTick * 0.07) * 5f : 0f;
            int cx = sx + i * (cW + gap);
            int cy = baseY - (int) floatOff;

            // ── Glow behind selected card ─────────────────────────────────────
            if (sel) {
                for (int g = 10; g >= 1; g--) {
                    int alpha = (int)(160 * (1f - g / 11f));
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), alpha));
                    g2.fillRect(cx - g, cy - g, cW + g * 2, cH + g * 2);
                }
            }

            // ── Card fill ─────────────────────────────────────────────────────
            Color fillColor = sel  ? new Color(accent.getRed()/5+10,  accent.getGreen()/5+10,  accent.getBlue()/5+36)
                            : hov  ? PX_HOVER
                            :         PX_PANEL;
            g2.setColor(fillColor);
            g2.fillRect(cx, cy, cW, cH);

            // ── Accent top bar ────────────────────────────────────────────────
            g2.setColor(sel ? accent : accent.darker().darker());
            g2.fillRect(cx, cy, cW, 5);

            // ── Card border ───────────────────────────────────────────────────
            float bStroke = sel ? 2.5f : 1f;
            g2.setStroke(new BasicStroke(bStroke));
            g2.setColor(sel ? accent : hov ? PX_BORDER : PX_MUTED);
            g2.drawRect(cx, cy, cW, cH);
            g2.setStroke(new BasicStroke(1f));

            // ── Inner highlight line ──────────────────────────────────────────
            g2.setColor(new Color(255, 255, 255, sel ? 30 : 12));
            g2.drawLine(cx + 1, cy + 1, cx + cW - 2, cy + 1);
            g2.drawLine(cx + 1, cy + 1, cx + 1,      cy + cH - 2);

            // ── Character portrait ────────────────────────────────────────────
            int portraitY = cy + 34;
            drawCharArt(g2, cx + cW / 2, portraitY + 50, i, sel);

            // ── "SELECTED" badge — drawn AFTER portrait so it is never hidden ─
            if (sel) {
                // Float the badge above the card top as a ribbon
                int badgeW = 100, badgeH = 22;
                int badgeX = cx + cW / 2 - badgeW / 2;
                int badgeY = cy - badgeH - 4;   // sits above the card

                // Outer glow halo around the badge
                float pulse = 0.5f + 0.5f * (float) Math.sin(animTick * 0.12f);
                for (int g = 6; g >= 1; g--) {
                    int a = (int)(100 * pulse * (1f - g / 7f));
                    g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), a));
                    g2.fillRect(badgeX - g, badgeY - g, badgeW + g * 2, badgeH + g * 2);
                }

                // Badge fill (pulsing accent)
                g2.setColor(new Color(
                    Math.min(255, (int)(accent.getRed()   * (0.7f + 0.3f * pulse))),
                    Math.min(255, (int)(accent.getGreen() * (0.7f + 0.3f * pulse))),
                    Math.min(255, (int)(accent.getBlue()  * (0.7f + 0.3f * pulse))), 240));
                g2.fillRect(badgeX, badgeY, badgeW, badgeH);

                // Badge border
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(accent.brighter());
                g2.drawRect(badgeX, badgeY, badgeW, badgeH);
                g2.setStroke(new BasicStroke(1f));

                // Badge text
                g2.setFont(new Font("Courier New", Font.BOLD, 11));
                g2.setColor(PX_WHITE);
                FontMetrics bfm = g2.getFontMetrics();
                String badge = "\u2605 SELECTED \u2605";
                g2.drawString(badge, badgeX + badgeW / 2 - bfm.stringWidth(badge) / 2, badgeY + 15);

                // Animated corner pixel sparks
                drawCardSparks(g2, cx, cy, cW, cH, accent);
            }

            // ── Divider ───────────────────────────────────────────────────────
            g2.setColor(new Color(accent.getRed(), accent.getGreen(), accent.getBlue(), sel ? 100 : 40));
            g2.fillRect(cx + 8, cy + 130, cW - 16, 1);

            // ── Class label ───────────────────────────────────────────────────
            g2.setFont(new Font("Courier New", Font.BOLD, 12));
            g2.setColor(sel ? accent.brighter() : PX_WHITE);
            FontMetrics lfm = g2.getFontMetrics();
            g2.drawString(CHAR_LABELS[i], cx + cW / 2 - lfm.stringWidth(CHAR_LABELS[i]) / 2, cy + 145);

            // ── Stat bars ─────────────────────────────────────────────────────
            int statY = cy + 158;
            for (int s = 0; s < 3; s++) {
                int statVal  = CHAR_STATS[i][s];
                int barFullW = cW - 32;
                int barFillW = (int)(barFullW * statVal / 100f);

                // Label
                g2.setFont(new Font("Courier New", Font.BOLD, 9));
                g2.setColor(PX_MUTED);
                g2.drawString(STAT_LABELS[s], cx + 8, statY + s * 17 + 9);

                // Bar track
                g2.setColor(new Color(30, 24, 60));
                g2.fillRect(cx + 28, statY + s * 17, barFullW, 8);

                // Bar fill with gradient tint
                Color barCol = sel ? accent : accent.darker();
                g2.setColor(barCol);
                g2.fillRect(cx + 28, statY + s * 17, barFillW, 8);

                // Bright top pixel on bar
                g2.setColor(new Color(Math.min(255, barCol.getRed() + 80),
                                      Math.min(255, barCol.getGreen() + 80),
                                      Math.min(255, barCol.getBlue() + 80), 160));
                g2.fillRect(cx + 28, statY + s * 17, barFillW, 3);

                // Bar border
                g2.setColor(new Color(80, 72, 130));
                g2.drawRect(cx + 28, statY + s * 17, barFullW, 8);
            }

            // ── One-line description ──────────────────────────────────────────
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            g2.setColor(PX_MUTED);
            String desc = CHAR_DESC[i].split("\n")[0];
            FontMetrics dfm = g2.getFontMetrics();
            g2.drawString(desc, cx + cW / 2 - dfm.stringWidth(desc) / 2, cy + cH - 8);
        }

        int btnBase = baseY + cH + 30;
        pxBtn(g2, "CONTINUE", W / 2, btnBase,      hoveredBtn == 10);
        pxBtn(g2, "BACK",     W / 2, btnBase + 52, hoveredBtn == 11);
    }

    /** Draws animated pixel spark / corner ornaments on the selected card. */
    private void drawCardSparks(Graphics2D g2, int cx, int cy, int cW, int cH, Color accent) {
        int[][] corners = { {cx, cy}, {cx + cW, cy}, {cx, cy + cH}, {cx + cW, cy + cH} };
        for (int c = 0; c < corners.length; c++) {
            float phase = animTick * 0.15f + c * (float)(Math.PI / 2);
            float vis   = 0.4f + 0.6f * (float)Math.abs(Math.sin(phase));
            int   off   = (int)(4 * Math.abs(Math.sin(phase)));
            int   px    = corners[c][0] + (c % 2 == 0 ? -off : off);
            int   py    = corners[c][1] + (c < 2     ? -off : off);
            g2.setColor(new Color(accent.getRed(), accent.getGreen(),
                                  accent.getBlue(), (int)(vis * 200)));
            g2.fillRect(px - 2, py - 2, 4, 4);
            g2.setColor(new Color(255, 255, 255, (int)(vis * 120)));
            g2.fillRect(px - 1, py - 1, 2, 2);
        }
    }

    private void drawCharArt(Graphics2D g2, int cx, int cy, int idx, boolean sel) {
        // Use real character image if loaded
        if (charImages[idx] != null) {
            int imgW = 80, imgH = 80;
            g2.drawImage(charImages[idx], cx - imgW/2, cy - imgH, imgW, imgH, null);
            return;
        }
        // Fallback: Simple pixel-block character sprite
        Color[] bc = { new Color(80,120,200), new Color(200,80,140), new Color(100,180,120) };
        // Head
        g2.setColor(new Color(220,180,140)); g2.fillRect(cx-8, cy-38, 16, 14);
        // Body
        g2.setColor(bc[idx]); g2.fillRect(cx-10, cy-24, 20, 20);
        // Legs
        g2.setColor(new Color(60,50,80));
        g2.fillRect(cx-10, cy-4, 8, 12); g2.fillRect(cx+2, cy-4, 8, 12);
        // Weapon indicator pixel
        g2.setColor(sel ? PX_GOLD : bc[idx].brighter());
        if      (idx==0) g2.fillRect(cx+10, cy-28, 4, 14);  // sword
        else if (idx==1) g2.fillRect(cx+10, cy-28, 6, 6);   // orb
        else             g2.fillRect(cx+10, cy-28, 4, 10);  // fist
    }

    // ─── DIFFICULTY ───────────────────────────────────────────────────────────
    private static final String[] DIFF_N = {"EASY","NORMAL","HARD"};
    private static final String[] DIFF_D = {
        "Enemies deal less\ndamage and move slower.",
        "Standard challenge.\nBalanced difficulty.",
        "Enemies hit harder\nand move faster."
    };
    private static final Color[] DIFF_C = { PX_GREEN, PX_GOLD, PX_RED };

    private void drawDifficulty(Graphics2D g2, int W, int H) {
        pxTitle(g2, "DIFFICULTY", W, H);
        int cW=150, cH=190, gap=24, tW=3*cW+2*gap, sx=W/2-tW/2, cY=H/2-cH/2-10;
        for (int i=0;i<3;i++) {
            int cx=sx+i*(cW+gap); boolean sel=settings.difficulty==i, hov=hoveredBtn==i;
            g2.setColor(sel ? PX_SEL : hov ? PX_HOVER : PX_PANEL);
            g2.fillRect(cx, cY, cW, cH);
            g2.setColor(sel ? DIFF_C[i] : hov ? PX_BORDER : PX_MUTED);
            g2.drawRect(cx, cY, cW, cH);

            if (diffIcons[i]!=null) {
                g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
                g2.drawImage(diffIcons[i], cx+cW/2-24, cY+14, 48, 48, null);
            } else {
                // Pixel triangle shapes
                g2.setColor(DIFF_C[i]);
                for (int s=0;s<=i;s++) g2.fillPolygon(
                    new int[]{cx+22+s*30, cx+16+s*30, cx+28+s*30},
                    new int[]{cY+56,      cY+76,       cY+76}, 3);
            }

            g2.setFont(new Font("Courier New", Font.BOLD, 15));
            g2.setColor(sel ? PX_GOLD : PX_WHITE);
            FontMetrics fm=g2.getFontMetrics();
            g2.drawString(DIFF_N[i], cx+cW/2-fm.stringWidth(DIFF_N[i])/2, cY+100);
            g2.setFont(subFont); g2.setColor(PX_MUTED);
            String[] dl=DIFF_D[i].split("\n");
            for (int ln=0;ln<dl.length;ln++) g2.drawString(dl[ln], cx+6, cY+116+ln*14);
            if (sel) { g2.setFont(subFont); g2.setColor(PX_GOLD); g2.drawString("SELECTED", cx+cW/2-g2.getFontMetrics().stringWidth("SELECTED")/2, cY+12); }
        }
        int btnBase=cY+cH+30;
        pxBtn(g2,"START GAME", W/2, btnBase,    hoveredBtn==10);
        pxBtn(g2,"BACK",       W/2, btnBase+52, hoveredBtn==11);
    }

    // ─── ABOUT ────────────────────────────────────────────────────────────────
    private static final String[] DEV_NAMES_DISPLAY = {"Ivankaizer","StevenKenn","JamesStephen"};
    private static final String[] DEV_ROLES         = {"Lead Developer","Game Developer","Game Developer"};

    private void drawAbout(Graphics2D g2, int W, int H) {
        pxTitle(g2, "ABOUT", W, H);

        // Game description panel
        int descW = (int)(W * 0.55f), descH = 90;
        int descX = W/2 - descW/2, descY = H/4 + 42;
        pxPanel(g2, descX, descY, descW, descH);

        g2.setFont(new Font("Courier New", Font.BOLD, 14));
        g2.setColor(PX_GOLD);
        cent(g2, "ALAMAT: SURVIVAL NIGHT", W/2, descY + 22);

        g2.setFont(subFont);
        g2.setColor(PX_WHITE);
        cent(g2, "A pixel-art survival game rooted in Philippine mythology.", W/2, descY + 42);
        cent(g2, "Battle creatures of folklore across 9 chapters of darkness.", W/2, descY + 57);
        cent(g2, "Choose your hero. Survive the night. Defend the homeland.", W/2, descY + 72);

        // Developer section title
        int devTitleY = descY + descH + 28;
        g2.setFont(new Font("Courier New", Font.BOLD, 16));
        g2.setColor(PX_GOLD);
        cent(g2, "~ THE DEVELOPERS ~", W/2, devTitleY);
        g2.setColor(PX_BORDER);
        g2.fillRect(W/2 - 110, devTitleY + 5, 220, 2);

        // Dev cards
        int cardW = 160, cardH = 210, gap = 36;
        int totalW = 3 * cardW + 2 * gap;
        int startX = W/2 - totalW/2;
        int cardY  = devTitleY + 20;

        String[] fallbackInitials = {"K","S","S"};
        Color[]  fallbackColors   = {new Color(80,120,220), new Color(100,180,120), new Color(200,100,80)};

        for (int i = 0; i < 3; i++) {
            int cx = startX + i * (cardW + gap);

            // Card background
            g2.setColor(PX_PANEL); g2.fillRect(cx, cardY, cardW, cardH);
            g2.setColor(PX_BORDER); g2.drawRect(cx, cardY, cardW, cardH);
            // Top accent bar
            g2.setColor(fallbackColors[i]);
            g2.fillRect(cx, cardY, cardW, 4);

            // Dev photo or fallback circle
            int photoSize = 90;
            int photoX = cx + cardW/2 - photoSize/2;
            int photoY = cardY + 14;

            if (devImages[i] != null) {
                // Clip to a rounded rect for the photo
                java.awt.Shape oldClip = g2.getClip();
                g2.setClip(new java.awt.geom.RoundRectangle2D.Float(photoX, photoY, photoSize, photoSize, 12, 12));
                g2.drawImage(devImages[i], photoX, photoY, photoSize, photoSize, null);
                g2.setClip(oldClip);
                g2.setColor(PX_BORDER);
                g2.drawRoundRect(photoX, photoY, photoSize, photoSize, 12, 12);
            } else {
                // Pixel avatar fallback
                g2.setColor(fallbackColors[i]);
                g2.fillRoundRect(photoX, photoY, photoSize, photoSize, 12, 12);
                g2.setColor(PX_BORDER);
                g2.drawRoundRect(photoX, photoY, photoSize, photoSize, 12, 12);
                g2.setFont(new Font("Courier New", Font.BOLD, 36));
                g2.setColor(PX_WHITE);
                FontMetrics fm = g2.getFontMetrics();
                g2.drawString(fallbackInitials[i],
                    photoX + photoSize/2 - fm.stringWidth(fallbackInitials[i])/2,
                    photoY + photoSize/2 + fm.getAscent()/2 - 4);
            }

            // Name
            g2.setFont(new Font("Courier New", Font.BOLD, 13));
            g2.setColor(PX_WHITE);
            FontMetrics fm = g2.getFontMetrics();
            int nameY = photoY + photoSize + 18;
            g2.drawString(DEV_NAMES_DISPLAY[i], cx + cardW/2 - fm.stringWidth(DEV_NAMES_DISPLAY[i])/2, nameY);

            // Role
            g2.setFont(subFont);
            g2.setColor(fallbackColors[i].brighter());
            FontMetrics sfm = g2.getFontMetrics();
            g2.drawString(DEV_ROLES[i], cx + cardW/2 - sfm.stringWidth(DEV_ROLES[i])/2, nameY + 18);

            // Pixel divider
            g2.setColor(new Color(PX_BORDER.getRed(), PX_BORDER.getGreen(), PX_BORDER.getBlue(), 80));
            g2.fillRect(cx + 10, nameY + 26, cardW - 20, 1);

            // "Alamat Team" tag
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            g2.setColor(PX_MUTED);
            FontMetrics tfm = g2.getFontMetrics();
            String tag = "[ ALAMAT TEAM ]";
            g2.drawString(tag, cx + cardW/2 - tfm.stringWidth(tag)/2, nameY + 42);
        }

        // Back button
        int backY = cardY + cardH + 28;
        pxBtn(g2, "BACK", W/2, backY, hoveredBtn == 0);
    }

    // ─── LEADERBOARD (scrollable, up to 100 rows) ─────────────────────────────
    private static final String[] LB_TABS = {"EASY","NORMAL","HARD"};
    private static final Color[]  LB_TCOL = { PX_GREEN, PX_GOLD, PX_RED };
    private static final int LB_VISIBLE_ROWS = 12;

    private void drawLeaderboard(Graphics2D g2, int W, int H) {
        pxTitle(g2,"LEADERBOARD", W, H);

        // Difficulty tabs
        int tabW=110, tabH=32, tabY=H/2-195, tabX=W/2-(3*tabW+20)/2;
        for (int t=0;t<3;t++) {
            int tx=tabX+t*(tabW+10); boolean sel=lbTab==t, hov=hoveredBtn==50+t;
            g2.setColor(sel ? LB_TCOL[t] : PX_PANEL); g2.fillRect(tx, tabY, tabW, tabH);
            g2.setColor(sel ? LB_TCOL[t].brighter() : hov ? PX_BORDER : PX_MUTED);
            g2.drawRect(tx, tabY, tabW, tabH);
            g2.setFont(new Font("Courier New", Font.BOLD, 13));
            g2.setColor(sel ? new Color(10,8,24) : PX_MUTED);
            FontMetrics fm=g2.getFontMetrics();
            g2.drawString(LB_TABS[t], tx+tabW/2-fm.stringWidth(LB_TABS[t])/2, tabY+tabH/2+5);
        }

        // Build row data
        List<PlayerProfile> all = PlayerProfile.loadAll();
        java.util.List<Object[]> rows = new java.util.ArrayList<>();
        for (PlayerProfile p : all)
            for (int[] a : p.getAttempts(lbTab))
                rows.add(new Object[]{ p.name, a[0], a[1], a[2], a[3] });

        int totalRows = rows.size();
        int maxScroll = Math.max(0, totalRows - LB_VISIBLE_ROWS);
        lbScroll = Math.min(lbScroll, maxScroll);

        int pW=580, startY=H/2-157, pX=W/2-pW/2;
        int panelH = 28 + LB_VISIBLE_ROWS*26 + 12;
        pxPanel(g2, pX, startY-10, pW, panelH);

        // Column headers
        g2.setFont(new Font("Courier New", Font.BOLD, 11));
        g2.setColor(PX_GOLD);
        g2.drawString("#",     pX+10,  startY+14);
        g2.drawString("NAME",  pX+40,  startY+14);
        g2.drawString("SCORE", pX+200, startY+14);
        g2.drawString("COINS", pX+300, startY+14);
        g2.drawString("LVL",   pX+390, startY+14);
        g2.drawString("DATE",  pX+440, startY+14);
        g2.setColor(PX_BORDER); g2.fillRect(pX+8, startY+18, pW-16, 2);

        if (rows.isEmpty()) {
            g2.setFont(subFont); g2.setColor(PX_MUTED);
            cent(g2, "No runs yet on this difficulty!", W/2, startY+80);
        } else {
            Color[] medals = { new Color(248,208,48), new Color(200,200,200), new Color(180,100,40) };
            int end = Math.min(lbScroll + LB_VISIBLE_ROWS, totalRows);
            for (int i = lbScroll; i < end; i++) {
                Object[] r   = rows.get(i);
                int rank     = i + 1;
                int rowY     = startY + 24 + (i - lbScroll) * 26;
                // Alternating row tint
                if (i%2==0) { g2.setColor(new Color(255,255,255,8)); g2.fillRect(pX+6, rowY-10, pW-12, 22); }
                // Medal highlight for top 3
                if (i < 3) { g2.setColor(new Color(medals[i].getRed(),medals[i].getGreen(),medals[i].getBlue(),20)); g2.fillRect(pX+6,rowY-10,pW-12,22); }

                g2.setFont(new Font("Courier New", Font.BOLD, 11));
                g2.setColor(rank<=3 ? medals[rank-1] : PX_MUTED);
                g2.drawString(String.valueOf(rank),       pX+10,  rowY+6);
                g2.setColor(rank<=3 ? PX_WHITE : new Color(200,190,230));
                g2.drawString((String)r[0],               pX+40,  rowY+6);
                g2.setColor(new Color(140,200,255));
                g2.drawString(String.valueOf((Integer)r[1]),pX+200,rowY+6);
                g2.setColor(PX_GOLD);
                g2.drawString(String.valueOf((Integer)r[2]),pX+300,rowY+6);
                g2.setColor(PX_GREEN);
                g2.drawString(String.valueOf((Integer)r[3]),pX+390,rowY+6);
                g2.setColor(PX_MUTED);
                g2.drawString(PlayerProfile.formatDate((Integer)r[4]),pX+440,rowY+6);
            }
        }

        // Scroll indicator
        if (totalRows > LB_VISIBLE_ROWS) {
            g2.setFont(new Font("Courier New", Font.PLAIN, 10));
            g2.setColor(PX_MUTED);
            cent(g2, "[ SCROLL / UP-DOWN KEYS ]  " + (lbScroll+1) + "-" + Math.min(lbScroll+LB_VISIBLE_ROWS,totalRows) + " / " + totalRows,
                 W/2, startY + panelH + 6);
        }

        pxBtn(g2, "BACK", W/2, startY+panelH+24, hoveredBtn==0);
    }


    // ─── Pixel-art drawing helpers ────────────────────────────────────────────

    /** Panel: flat fill + 1px border. */
    private void pxPanel(Graphics2D g2, int x, int y, int w, int h) {
        g2.setColor(PX_PANEL); g2.fillRect(x,y,w,h);
        g2.setColor(PX_BORDER); g2.drawRect(x,y,w,h);
        // Inner highlight (top-left edge, 1px)
        g2.setColor(new Color(PX_BORDER.getRed(),PX_BORDER.getGreen(),PX_BORDER.getBlue(),60));
        g2.drawLine(x+1,y+1,x+w-2,y+1);
        g2.drawLine(x+1,y+1,x+1,  y+h-2);
    }

    /** Screen title with pixel shadow. */
    private void pxTitle(Graphics2D g2, String t, int W, int H) {
        g2.setFont(new Font("Courier New", Font.BOLD, 32));
        FontMetrics fm = g2.getFontMetrics();
        int tx = W/2 - fm.stringWidth(t)/2;
        int ty = H/4;
        g2.setColor(new Color(0,0,0,180)); g2.drawString(t, tx+2, ty+2);
        g2.setColor(PX_GOLD); g2.drawString(t, tx, ty);
        // Pixel underline
        g2.setColor(PX_BORDER); g2.fillRect(W/2-100, ty+6, 200, 2);
    }

    /** Button: pixel style with hard edges, no rounded corners. */
    private void pxBtn(Graphics2D g2, String label, int cx, int cy, boolean hov) {
        int x = cx-BW/2, y = cy-BH/2;
        // Shadow
        g2.setColor(new Color(0,0,0,160)); g2.fillRect(x+3, y+3, BW, BH);
        // Fill
        g2.setColor(hov ? PX_HOVER : PX_PANEL); g2.fillRect(x, y, BW, BH);
        // Border (double-line pixel style)
        g2.setColor(hov ? PX_GOLD : PX_BORDER); g2.drawRect(x, y, BW, BH);
        g2.setColor(new Color(255,255,255,20)); g2.drawRect(x+1, y+1, BW-2, BH-2);
        // Label
        g2.setFont(menuFont);
        FontMetrics fm = g2.getFontMetrics();
        g2.setColor(new Color(0,0,0,120)); g2.drawString(label, cx-fm.stringWidth(label)/2+1, cy+fm.getAscent()/2-1);
        g2.setColor(hov ? PX_GOLD : PX_WHITE);
        g2.drawString(label, cx-fm.stringWidth(label)/2, cy+fm.getAscent()/2-2);
        // Hover cursor indicator (pixel arrow)
        if (hov) {
            g2.setColor(PX_GOLD);
            g2.fillRect(x-10, cy-3, 6, 6);
        }
    }

    private void cent(Graphics2D g2, String t, int cx, int y) {
        g2.drawString(t, cx - g2.getFontMetrics().stringWidth(t)/2, y);
    }

    // ─── Hit detection ────────────────────────────────────────────────────────
    private int hovered(int mx, int my) {
        int cx=getWidth()/2, H=getHeight();
        if (screen.equals("charSelect")) {
            int cW=150,cH=220,gap=36,tW=3*cW+2*gap,sx=cx-tW/2,cY=H/2-cH/2-10;
            for (int i=0;i<3;i++) { int lx=sx+i*(cW+gap); if (mx>=lx&&mx<=lx+cW&&my>=cY-20&&my<=cY+cH+20) return i; }
            int b=cY+cH+30; if (inB(mx,my,cx,b)) return 10; if (inB(mx,my,cx,b+52)) return 11; return -1;
        }
        if (screen.equals("difficulty")) {
            int cW=150,cH=190,gap=24,tW=3*cW+2*gap,sx=cx-tW/2,cY=H/2-cH/2-10;
            for (int i=0;i<3;i++) { int lx=sx+i*(cW+gap); if (mx>=lx&&mx<=lx+cW&&my>=cY&&my<=cY+cH) return i; }
            int b=cY+cH+30; if (inB(mx,my,cx,b)) return 10; if (inB(mx,my,cx,b+52)) return 11; return -1;
        }
        if (screen.equals("nameEntry")) {
            int py=H/2-70, btnBase=py+180+30;
            if (inB(mx,my,cx,btnBase))    return 20;
            if (inB(mx,my,cx,btnBase+52)) return 21;
            return -1;
        }
        if (screen.equals("leaderboard")) {
            int tabW=110, tabY=H/2-195, tabX=cx-(3*tabW+20)/2;
            for (int t=0;t<3;t++) { int tx=tabX+t*(tabW+10); if (mx>=tx&&mx<=tx+tabW&&my>=tabY&&my<=tabY+32) return 50+t; }
            int panelH=28+LB_VISIBLE_ROWS*26+12, startY=H/2-157;
            if (inB(mx,my,cx,startY+panelH+24)) return 0;
            return -1;
        }
        if (screen.equals("about")) {
            // Mirror drawAbout layout to find backY
            int descH = 90, descY = H/4 + 42;
            int devTitleY = descY + descH + 28;
            int cardH = 210;
            int cardY = devTitleY + 20;
            int backY = cardY + cardH + 28;
            if (inB(mx,my,cx,backY)) return 0;
            return -1;
        }
        String[] btns=curBtns();
        for (int i=0;i<btns.length;i++) if (inB(mx,my,cx,btnY(i))) return i;
        return -1;
    }

    private boolean inB(int mx, int my, int cx, int cy) {
        return mx>=cx-BW/2&&mx<=cx+BW/2&&my>=cy-BH/2&&my<=cy+BH/2;
    }
    private int btnY(int i) {
        int H=getHeight();
        return switch (screen) {
            case "main" -> mainBtnY(i);
            default     -> H/2+i*60;
        };
    }
    private String[] curBtns() {
        return switch (screen) {
            case "main" -> MAIN_BTNS;
            default     -> new String[0];
        };
    }

    // ─── Clicks ───────────────────────────────────────────────────────────────
    private void click(int mx, int my) {
        int b = hovered(mx, my);
        // Play button-click SFX for any recognised button press
        if (b != -1) SoundManager.play("sounds/system/ButtonClick.wav");
        switch (screen) {
            case "main"        -> clickMain(b);
            case "nameEntry"   -> { if (b==20) commitName(); else if (b==21) { screen="main"; nameInput=""; nameError=""; } }
            case "charSelect"  -> clickCharSelect(b);
            case "difficulty"  -> clickDifficulty(b);
            case "leaderboard" -> { if (b>=50&&b<=52) { lbTab=b-50; lbScroll=0; repaint(); } else if (b==0) screen="main"; }
            case "about"       -> { if (b==0) screen="main"; }
        }
    }

    private void clickMain(int b) {
        switch (b) {
            case 0 -> { nameInput=""; nameError=""; screen="nameEntry"; }
            case 1 -> openTutorial();
            case 2 -> { lbScroll=0; screen="leaderboard"; }
            case 3 -> screen="about";
            case 4 -> System.exit(0);
        }
    }
    private void clickCharSelect(int b) {
        if (b>=0&&b<3) selectedChar=CHARS[b];
        else if (b==10) screen="difficulty";
        else if (b==11) screen="nameEntry";
    }
    private void clickDifficulty(int b) {
        if (b>=0&&b<3) settings.difficulty=b;
        else if (b==10) { settings.save(); startGame(); }
        else if (b==11) screen="charSelect";
    }
    private void openTutorial() {
        stopMusic();
        animTimer.stop(); window.getContentPane().removeAll();
        TutorialPanel tp=new TutorialPanel(window,settings,activeProfile);
        window.add(tp); window.revalidate();
        tp.requestFocusInWindow();
    }

    public void startGame() {
        stopMusic();
        if (activeProfile==null) { activeProfile=new PlayerProfile(); activeProfile.name="Guest"; }
        animTimer.stop(); window.getContentPane().removeAll();
        GamePanel game=new GamePanel(settings,activeProfile,selectedChar);
        game.setPreferredSize(new Dimension(settings.screenWidth,settings.screenHeight));
        final PlayerProfile prof=activeProfile; final String ch=selectedChar;
        // GamePanel.exitToMenu() already dispatches this callback via
        // SwingUtilities.invokeLater(), so we must NOT wrap it in another
        // invokeLater() here.  The double-wrap caused a second scheduling hop
        // that let the game thread's final repaint() events run in between,
        // leaving the window in a broken intermediate state and freezing the UI.
        game.setOnExitToMenu(() -> {
            window.getContentPane().removeAll();
            MenuPanel fresh=new MenuPanel(window,settings);
            fresh.activeProfile=prof; fresh.selectedChar=ch;
            window.add(fresh); window.revalidate();
            fresh.requestFocusInWindow();
        });
        window.add(game); window.revalidate();
        game.startGameThread(); game.requestFocusInWindow();
    }
}
