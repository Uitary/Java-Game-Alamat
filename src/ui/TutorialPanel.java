import javax.swing.*;
import java.awt.*;
import java.awt.event.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import javax.imageio.ImageIO;

/**
 * TutorialPanel — 9-page tutorial.
 * Themed to match the pixel-art main menu (Courier New, hard pixel borders,
 * dark navy palette, GIF/static background overlay).
 */
public class TutorialPanel extends JPanel implements ActionListener {

    private final JFrame       window;
    private final GameSettings settings;
    private PlayerProfile      activeProfile;

    private Timer animTimer;
    private int   page       = 0;
    private int   hoveredBtn = -1;
    private static final int PAGES = 9;

    // Tutorial images
    private final BufferedImage[] tutImages = new BufferedImage[PAGES];
    private static final String[] TUT_IMAGE_FILES = {
        "assets/Tut_Run_KF1.png",  "assets/Tut_Jump_KF1.png", "assets/Tut_Dash_KF1.png",
        "assets/Tut_Sword_KF1.png","assets/Tut_Bow_KF1.png",  "assets/Tut_Heal_KF1.png",
        "assets/Tut_Mobs_KF1.png", "assets/Tut_Boss_KF1.png", "assets/Tut_Shop_KF1.png"
    };

    // ── Pixel-art palette (matches MenuPanel) ─────────────────────────────────
    private static final Color PX_BG     = new Color(10, 8, 24);
    private static final Color PX_PANEL  = new Color(20, 16, 44);
    private static final Color PX_BORDER = new Color(88, 72, 200);
    private static final Color PX_GOLD   = new Color(248, 208, 48);
    private static final Color PX_GOLD2  = new Color(200, 160, 32);
    private static final Color PX_WHITE  = new Color(232, 228, 248);
    private static final Color PX_MUTED  = new Color(120, 110, 160);
    private static final Color PX_HOVER  = new Color(64, 52, 144);
    private static final Color PX_KEY_BG = new Color(30, 22, 60);

    private static final int BW=200, BH=38;

    // Background GIF (reuse from assets if present)
    private ImageIcon bgGif;

    private static final String[] TITLES = {
        "MOVING","JUMPING","DASHING","ATTACKING — SWORD","ATTACKING — BOW",
        "HEALING & POTIONS","WAVES OF ENEMIES","THE BOSS","THE SHOP"
    };

    private static final String[][] LINES = {
        {"Press [A] to move LEFT, [D] to move RIGHT.",
         "Enemies must be cleared before advancing."},
        {"Press [W] or [SPACE] to Jump.",
         "Hold jump key for extra height (double jump height).",
         "Cannot jump again until landing."},
        {"Press [SPACE] (Dash key) to Dash.",
         "Dashes forward at high speed with ghost trail.",
         "Watch the Dash bar — ~1 sec cooldown."},
        {"Press [1] to equip Sword, LEFT-CLICK to swing.",
         "Slash hits enemies in front.  [S] = lower aim.",
         "Upgrade sword damage in the Shop [E]."},
        {"Press [2] to equip Bow, LEFT-CLICK to fire.",
         "Fires a 5-arrow burst, then reloads for 1 second.",
         "[S] = lower aim.  Upgrade bow in the Shop [E]."},
        {"Press [4] (or [3] for other classes) for Potion.",
         "Restores 20 HP.  Start with 3 potions (max 30).",
         "Buy more in the Shop.  Only usable below max HP."},
        {"3 levels per chapter, each level has multiple waves.",
         "Wave count grows each level.  Kill = 10 coins + 10 score.",
         "Clear all waves to unlock the next level."},
        {"After Level 3 waves, the CHAPTER BOSS appears!",
         "Chapter 1: Aswang — melee + claw projectiles.",
         "Chapter 2-3: Mangkukulam — melee + curse DoT.",
         "Watch the boss HP bar at the screen bottom."},
        {"Press [E] to open the Shop at any time.",
         "Upgrade: Weapon skill 1, Weapon skill 2, Defense, Potion.",
         "Defense reduces incoming damage by 5% per level.",
         "Tiers: Lv1-10 (Tier 1) → Ascend → Lv11-20 (Tier 2)."}
    };

    private static final String[][] KEYS = {
        {"A","D"},
        {"W / SPACE"},
        {"SPACE"},
        {"1","LMB","S"},
        {"2","LMB","S"},
        {"3/4","LMB"},
        {},
        {},
        {"E"}
    };

    // ─────────────────────────────────────────────────────────────────────────
    public TutorialPanel(JFrame window, GameSettings settings, PlayerProfile activeProfile) {
        this.window = window; this.settings = settings; this.activeProfile = activeProfile;
        Dimension scr = Toolkit.getDefaultToolkit().getScreenSize();
        setPreferredSize(new Dimension((int)scr.getWidth(), (int)scr.getHeight()));
        setBackground(PX_BG); setLayout(null); setFocusable(true);
        addHierarchyListener(ev -> {
            if ((ev.getChangeFlags() & java.awt.event.HierarchyEvent.SHOWING_CHANGED) != 0
                    && isShowing()) {
                requestFocus();
            }
        });
        loadTutImages();
        loadBgGif();
        animTimer = new Timer(16, this); animTimer.start();

        addMouseMotionListener(new MouseMotionAdapter() {
            @Override public void mouseMoved(MouseEvent e) { hoveredBtn=hit(e.getX(),e.getY()); repaint(); }
        });
        addMouseListener(new MouseAdapter() {
            @Override public void mousePressed(MouseEvent e) {
                int b=hit(e.getX(),e.getY());
                if(b==0)go(-1);
                if(b==1){if(page==PAGES-1)back();else go(1);}
            }
        });
        addKeyListener(new KeyAdapter() {
            @Override public void keyPressed(KeyEvent e) {
                int c=e.getKeyCode();
                if(c==KeyEvent.VK_LEFT ||c==KeyEvent.VK_A) go(-1);
                if(c==KeyEvent.VK_RIGHT||c==KeyEvent.VK_D) go(1);
                if(c==KeyEvent.VK_ESCAPE) back();
                if(c==KeyEvent.VK_ENTER){if(page==PAGES-1)back();else go(1);}
            }
        });
    }

    private void loadTutImages() {
        for (int i=0;i<PAGES;i++) {
            try { tutImages[i] = ImageIO.read(new File(TUT_IMAGE_FILES[i])); }
            catch (IOException ignored) {}
        }
    }

    private void loadBgGif() {
        File f = new File("assets/MainMenu_Bg.gif");
        if (f.exists()) bgGif = new ImageIcon(f.getAbsolutePath());
    }

    @Override public void actionPerformed(ActionEvent e) { repaint(); }

    // ─── Paint ────────────────────────────────────────────────────────────────
    @Override protected void paintComponent(Graphics g) {
        super.paintComponent(g);
        Graphics2D g2 = (Graphics2D) g;
        // Pixel rendering — no anti-aliasing on shapes, crisp text
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,      RenderingHints.VALUE_ANTIALIAS_OFF);
        g2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
        g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,     RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);

        int W=getWidth(), H=getHeight();

        // Background — same GIF or pixel gradient fallback
        if (bgGif != null) {
            g2.drawImage(bgGif.getImage(), 0, 0, W, H, this);
        } else {
            for (int y=0;y<H;y+=2) {
                float t=y/(float)H;
                g2.setColor(new Color((int)(10+t*6),(int)(8+t*4),(int)(24+t*20)));
                g2.fillRect(0,y,W,2);
            }
            g2.setColor(new Color(0,0,0,40));
            for (int y=0;y<H;y+=4) g2.fillRect(0,y,W,2);
        }
        g2.setColor(new Color(0,0,0,120)); g2.fillRect(0,0,W,H);

        drawTitle(g2,W);
        drawContent(g2,W,H);
        drawNav(g2,W,H);
        drawDots(g2,W,H);
    }

    // ── Title ─────────────────────────────────────────────────────────────────
    private void drawTitle(Graphics2D g2, int W) {
        String t = "TUTORIAL";
        g2.setFont(new Font("Courier New", Font.BOLD, 38));
        FontMetrics fm = g2.getFontMetrics();
        int tx = W/2 - fm.stringWidth(t)/2, ty = 52;
        g2.setColor(new Color(0,0,0,200)); g2.drawString(t, tx+2, ty+2);
        g2.setColor(PX_GOLD);             g2.drawString(t, tx, ty);
        g2.setColor(PX_BORDER);           g2.fillRect(W/2-100, ty+8, 200, 2);
    }

    // ── Content panel ─────────────────────────────────────────────────────────
    private void drawContent(Graphics2D g2, int W, int H) {
        int pW=Math.min(640,W-40), pH=300, px=(W-pW)/2, py=70;

        // Panel (pixel border)
        g2.setColor(PX_PANEL);  g2.fillRect(px,py,pW,pH);
        g2.setColor(PX_BORDER); g2.drawRect(px,py,pW,pH);
        // Inner highlight
        g2.setColor(new Color(PX_BORDER.getRed(),PX_BORDER.getGreen(),PX_BORDER.getBlue(),50));
        g2.drawLine(px+1,py+1,px+pW-2,py+1); g2.drawLine(px+1,py+1,px+1,py+pH-2);

        // Page title
        g2.setFont(new Font("Courier New",Font.BOLD,20));
        FontMetrics pm=g2.getFontMetrics(); String pt=TITLES[page];
        int tx=W/2-pm.stringWidth(pt)/2;
        g2.setColor(new Color(0,0,0,180)); g2.drawString(pt,tx+1,py+30+1);
        g2.setColor(PX_GOLD);              g2.drawString(pt,tx,py+30);
        g2.setColor(PX_BORDER);            g2.fillRect(px+10,py+36,pW-20,2);

        // Tutorial image (right side)
        int imgW=110, imgH=110, imgX=px+pW-imgW-14, imgY=py+46;
        if (tutImages[page]!=null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_NEAREST_NEIGHBOR);
            g2.drawImage(tutImages[page],imgX,imgY,imgW,imgH,null);
            g2.setColor(PX_BORDER); g2.drawRect(imgX,imgY,imgW,imgH);
        } else {
            g2.setColor(new Color(30,24,56)); g2.fillRect(imgX,imgY,imgW,imgH);
            g2.setColor(PX_BORDER);           g2.drawRect(imgX,imgY,imgW,imgH);
            g2.setFont(new Font("Courier New",Font.PLAIN,9)); g2.setColor(PX_MUTED);
            FontMetrics ifm=g2.getFontMetrics();
            g2.drawString(TITLES[page], imgX+imgW/2-ifm.stringWidth(TITLES[page])/2, imgY+imgH/2+4);
        }

        // Text lines
        g2.setFont(new Font("Courier New",Font.PLAIN,13)); g2.setColor(PX_WHITE);
        int lineY=py+58;
        for (String line:LINES[page]) { g2.drawString(line,px+14,lineY); lineY+=22; }

        // Key badges
        String[] keys=KEYS[page];
        if (keys.length>0) {
            int kx=px+14, ky=py+pH-46;
            g2.setFont(new Font("Courier New",Font.BOLD,10)); g2.setColor(PX_GOLD2);
            g2.drawString("KEYS:", kx, ky-2); kx+=52;
            for (String k:keys) kx=badge(g2,k,kx,ky-12)+6;
        }
    }

    private int badge(Graphics2D g2, String key, int x, int y) {
        g2.setFont(new Font("Courier New",Font.BOLD,11));
        FontMetrics fm=g2.getFontMetrics();
        int tw=fm.stringWidth(key), padX=6, padY=3, bw=tw+padX*2, bh=fm.getHeight()+padY;
        // Shadow
        g2.setColor(new Color(0,0,0,160)); g2.fillRect(x+2,y+2,bw,bh);
        // Fill + border
        g2.setColor(PX_KEY_BG); g2.fillRect(x,y,bw,bh);
        g2.setColor(PX_GOLD);   g2.drawRect(x,y,bw,bh);
        g2.setColor(PX_WHITE);  g2.drawString(key, x+padX, y+fm.getAscent()+padY/2);
        return x+bw;
    }

    // ── Navigation buttons ────────────────────────────────────────────────────
    private void drawNav(Graphics2D g2, int W, int H) {
        int bY=H-70;
        if (page>0)    pxBtn(g2,"< PREV",    W/2-BW-10,bY,hoveredBtn==0);
        pxBtn(g2, page==PAGES-1 ? "DONE" : "NEXT >", W/2+10, bY, hoveredBtn==1);

        g2.setFont(new Font("Courier New",Font.PLAIN,10)); g2.setColor(PX_MUTED);
        String hint="[ESC] Menu  [←/→] Navigate";
        FontMetrics fm=g2.getFontMetrics();
        g2.drawString(hint, W/2-fm.stringWidth(hint)/2, H-18);
    }

    private void pxBtn(Graphics2D g2, String label, int cx, int cy, boolean hov) {
        int x=cx, y=cy-BH/2;
        // Shadow
        g2.setColor(new Color(0,0,0,160)); g2.fillRect(x+3,y+3,BW,BH);
        // Fill
        g2.setColor(hov ? PX_HOVER : PX_PANEL); g2.fillRect(x,y,BW,BH);
        // Border (double-line pixel style)
        g2.setColor(hov ? PX_GOLD : PX_BORDER); g2.drawRect(x,y,BW,BH);
        g2.setColor(new Color(255,255,255,18));  g2.drawRect(x+1,y+1,BW-2,BH-2);
        // Label
        g2.setFont(new Font("Courier New",Font.BOLD,16));
        FontMetrics fm=g2.getFontMetrics();
        g2.setColor(new Color(0,0,0,120));
        g2.drawString(label, cx+BW/2-fm.stringWidth(label)/2+1, cy+fm.getAscent()/2-1);
        g2.setColor(hov ? PX_GOLD : PX_WHITE);
        g2.drawString(label, cx+BW/2-fm.stringWidth(label)/2, cy+fm.getAscent()/2-2);
        // Hover cursor pixel
        if (hov) { g2.setColor(PX_GOLD); g2.fillRect(x-8, cy-3, 6, 6); }
    }

    // ── Dots ──────────────────────────────────────────────────────────────────
    private void drawDots(Graphics2D g2, int W, int H) {
        int sz=7, gap=12, sx=W/2-(PAGES*gap)/2, dy=H-42;
        for (int i=0;i<PAGES;i++) {
            g2.setColor(i==page ? PX_GOLD : new Color(80,60,120,160));
            g2.fillRect(sx+i*gap, dy, sz, sz);
        }
    }

    // ── Hit detection ─────────────────────────────────────────────────────────
    private int hit(int mx, int my) {
        int W=getWidth(), H=getHeight(), bY=H-70;
        if (page>0&&mx>=W/2-BW*2-10&&mx<=W/2-10&&my>=bY-BH/2&&my<=bY+BH/2) return 0;
        if (mx>=W/2+10&&mx<=W/2+10+BW&&my>=bY-BH/2&&my<=bY+BH/2) return 1;
        return -1;
    }

    private void go(int d)  { page=Math.max(0,Math.min(PAGES-1,page+d)); repaint(); }

    private void back() {
        animTimer.stop();
        window.getContentPane().removeAll();
        MenuPanel menu=new MenuPanel(window,settings);
        menu.setActiveProfile(activeProfile);
        window.add(menu);
        window.revalidate();
        menu.requestFocusInWindow();
    }
}
