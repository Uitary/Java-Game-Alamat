import java.awt.*;

public class UIManager {

    private static final Color GOLD       = new Color(212, 175, 55);
    private static final Color GOLD_DIM   = new Color(180, 140, 30, 160);
    private static final Color GOLD_GLOW  = new Color(255, 220, 80, 70);
    private static final Color GOLD_DRK   = new Color(140, 110, 30);
    private static final Color PANEL_BG   = new Color(8, 4, 18, 230);

    // ── shared: dark panel with gold glow border ──────────────────────────────
    private void drawPanel(Graphics2D g2, int x, int y, int w, int h, int arc) {
        g2.setColor(new Color(0,0,0,130));
        g2.fillRoundRect(x+3, y+3, w, h, arc, arc);
        g2.setPaint(new GradientPaint(x, y, new Color(14,7,28,235), x, y+h, new Color(6,3,14,235)));
        g2.fillRoundRect(x, y, w, h, arc, arc);
        g2.setStroke(new BasicStroke(3f)); g2.setColor(GOLD_GLOW);
        g2.drawRoundRect(x-1, y-1, w+2, h+2, arc+1, arc+1);
        g2.setStroke(new BasicStroke(1.5f)); g2.setColor(GOLD);
        g2.drawRoundRect(x, y, w, h, arc, arc);
        g2.setStroke(new BasicStroke(0.8f)); g2.setColor(GOLD_DIM);
        g2.drawRoundRect(x+4, y+4, w-8, h-8, arc-2, arc-2);
        g2.setStroke(new BasicStroke(1f));
    }

    // ── shared: gradient bar with gloss ──────────────────────────────────────
    private void drawBar(Graphics2D g2, int x, int y, int w, int h,
                         Color track, Color fill1, Color fill2, float frac) {
        g2.setColor(track); g2.fillRoundRect(x, y, w, h, h, h);
        int fw = (int)(w * Math.max(0, Math.min(1, frac)));
        if (fw > 0) {
            g2.setPaint(new GradientPaint(x, y, fill1, x, y+h, fill2));
            g2.fillRoundRect(x, y, fw, h, h, h);
            g2.setPaint(new GradientPaint(x, y, new Color(255,255,255,50), x, y+h/2, new Color(255,255,255,0)));
            g2.fillRoundRect(x, y, fw, h/2, h, h);
        }
        g2.setStroke(new BasicStroke(1.2f)); g2.setColor(new Color(255,255,255,60));
        g2.drawRoundRect(x, y, w, h, h, h);
        g2.setStroke(new BasicStroke(1f));
    }

    // ── HUD ──────────────────────────────────────────────────────────────────
    public void drawHUD(Graphics2D g2, int W, int H,
                        Player player, PlayerProfile playerData,
                        int coins, int score, int potionCount,
                        int level, int wave,
                        boolean levelClear, boolean bossDefeated,
                        String chapterName) {

        // ── left stats panel ──────────────────────────────────────────────────
        int px=10, py=28, pw=220, ph=122;
        drawPanel(g2, px, py, pw, ph, 12);

        float hpFrac = player.health / 100f;
        Color hpC1 = hpFrac>0.5f?new Color(60,210,80):hpFrac>0.25f?new Color(220,160,20):new Color(220,40,40);
        Color hpC2 = hpFrac>0.5f?new Color(30,140,50):hpFrac>0.25f?new Color(160,100,10):new Color(160,20,20);

        // HP row
        g2.setFont(new Font("SansSerif",Font.BOLD,11));
        g2.setColor(new Color(255,110,110)); g2.drawString("\u2665 HP", px+10, py+21);
        g2.setColor(Color.WHITE);
        String hpStr = player.health+" / 100";
        g2.drawString(hpStr, px+pw-g2.getFontMetrics().stringWidth(hpStr)-10, py+21);
        drawBar(g2, px+8, py+25, pw-16, 12, new Color(40,4,4), hpC1, hpC2, hpFrac);

        // divider
        g2.setColor(GOLD_DIM); g2.setStroke(new BasicStroke(0.8f));
        g2.drawLine(px+8, py+44, px+pw-8, py+44);
        g2.setStroke(new BasicStroke(1f));

        // Coins
        g2.setFont(new Font("SansSerif",Font.BOLD,12));
        g2.setColor(GOLD); g2.drawString("\u2726 Coins", px+10, py+61);
        g2.setColor(Color.WHITE);
        String cStr=String.valueOf(coins);
        g2.drawString(cStr, px+pw-g2.getFontMetrics().stringWidth(cStr)-10, py+61);

        // Potions
        g2.setColor(new Color(140,255,160)); g2.drawString("\u2665 Potions", px+10, py+78);
        g2.setColor(Color.WHITE);
        String pStr=potionCount+" / "+Equipments.Potion.MAX_POTIONS;
        g2.drawString(pStr, px+pw-g2.getFontMetrics().stringWidth(pStr)-10, py+78);

        // Level / Wave
        g2.setColor(new Color(140,200,255)); g2.drawString("\u25B6 Level", px+10, py+95);
        g2.setColor(Color.WHITE);
        String lvlStr=level+" / 3  \u2022  Wave "+wave;
        g2.drawString(lvlStr, px+pw-g2.getFontMetrics().stringWidth(lvlStr)-10, py+95);

        // Bottom accent line
        g2.setPaint(new GradientPaint(px+8,py+ph-8,GOLD,px+pw-8,py+ph-8,new Color(212,175,55,0)));
        g2.setStroke(new BasicStroke(1f));
        g2.drawLine(px+8, py+ph-8, px+pw-8, py+ph-8);
        g2.setStroke(new BasicStroke(1f));

        // ── chapter name (center top) — with outline for readability ──────────
        g2.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,28));
        FontMetrics fm=g2.getFontMetrics();
        int cnx=W/2-fm.stringWidth(chapterName)/2;
        int cny=46;
        // dark outline (draw in 8 directions)
        g2.setColor(new Color(0,0,0,220));
        for(int dx=-2;dx<=2;dx++) for(int dy=-2;dy<=2;dy++)
            if(dx!=0||dy!=0) g2.drawString(chapterName,cnx+dx,cny+dy);
        // bright main text
        g2.setColor(new Color(255,220,100)); g2.drawString(chapterName,cnx,cny);

        // ── Level Clear banner ────────────────────────────────────────────────
        if (levelClear || bossDefeated) {
            String msg="Cleared!  Move Right \u2192";
            g2.setFont(new Font("Serif",Font.BOLD,24));
            FontMetrics mfm=g2.getFontMetrics();
            int mw=mfm.stringWidth(msg)+32, mx=W/2-mw/2, my=H/2-56;
            g2.setColor(new Color(0,0,0,160)); g2.fillRoundRect(mx,my-26,mw,38,10,10);
            g2.setStroke(new BasicStroke(1.5f)); g2.setColor(GOLD); g2.drawRoundRect(mx,my-26,mw,38,10,10);
            g2.setColor(new Color(255,220,80,240)); g2.drawString(msg,W/2-mfm.stringWidth(msg)/2,my);
            g2.setStroke(new BasicStroke(1f));
        }

        // ── hint (top right) ──────────────────────────────────────────────────
        g2.setFont(new Font("SansSerif",Font.PLAIN,11));
        g2.setColor(new Color(160,140,80,200));
        g2.drawString("[E] Shop   [ESC] Pause", W-178, 18);
    }

    // ── Warrior hotbar ────────────────────────────────────────────────────────
    public void drawHotbar(Graphics2D g2, int W, int H,
                           Equipments.Sword sword, Equipments.Bow bow,
                           Equipments.MaxHP maxhp, Equipments.Potion potion,
                           int selectedItem, String bowStatusSub) {
        int box=72, gap=8, startX=W/2-(4*box+3*gap)/2;
        String[] labels={"Sword","Bow","MaxHP","Potion"};
        String[] subs={
            sword.getTier(),
            bow.getTier()+(bowStatusSub!=null?" "+bowStatusSub:""),
            maxhp.getLabel(),
            potion.count+"/"+Equipments.Potion.MAX_POTIONS
        };
        boolean[] isHP={false,false,true,false};

        // tray
        int trayX=startX-10, trayY=H-box-26, trayW=4*box+3*gap+20, trayH=box+24;
        g2.setColor(new Color(0,0,0,180)); g2.fillRoundRect(trayX+3,trayY+3,trayW,trayH,14,14);
        g2.setPaint(new GradientPaint(trayX,trayY,new Color(30,18,50,240),trayX,trayY+trayH,new Color(14,8,28,240)));
        g2.fillRoundRect(trayX,trayY,trayW,trayH,14,14);
        g2.setStroke(new BasicStroke(2f)); g2.setColor(new Color(212,175,55,180));
        g2.drawRoundRect(trayX,trayY,trayW,trayH,14,14);
        g2.setStroke(new BasicStroke(0.8f)); g2.setColor(new Color(212,175,55,60));
        g2.drawRoundRect(trayX+4,trayY+4,trayW-8,trayH-8,10,10);

        for (int i=0;i<4;i++) {
            int bx=startX+i*(box+gap), by=H-box-18;
            boolean sel=(selectedItem==i+1);

            // slot shadow
            g2.setColor(new Color(0,0,0,160)); g2.fillRoundRect(bx+2,by+2,box,box,10,10);
            // slot fill — noticeably lighter so it shows against any background
            Color topC=sel?(isHP[i]?new Color(30,110,40,255):new Color(110,85,15,255)):new Color(45,28,70,255);
            Color botC=sel?(isHP[i]?new Color(16,60,22,255):new Color(60,44,8,255)) :new Color(24,14,42,255);
            g2.setPaint(new GradientPaint(bx,by,topC,bx,by+box,botC));
            g2.fillRoundRect(bx,by,box,box,10,10);
            // glow ring if selected
            if (sel) {
                g2.setStroke(new BasicStroke(4f)); g2.setColor(new Color(255,220,80,100));
                g2.drawRoundRect(bx-2,by-2,box+4,box+4,11,11);
            }
            // slot border — brighter for visibility
            g2.setStroke(new BasicStroke(sel?2.5f:1.8f));
            g2.setColor(sel?new Color(255,215,60):new Color(180,155,80,220));
            g2.drawRoundRect(bx,by,box,box,10,10);
            // keybind badge (top-right corner)
            g2.setColor(new Color(0,0,0,200)); g2.fillRoundRect(bx+box-20,by,20,16,5,5);
            g2.setStroke(new BasicStroke(1f)); g2.setColor(new Color(212,175,55,180));
            g2.drawRoundRect(bx+box-20,by,20,16,5,5);
            g2.setFont(new Font("SansSerif",Font.BOLD,11));
            g2.setColor(new Color(255,230,140)); g2.drawString(String.valueOf(i+1),bx+box-13,by+12);
            // label
            g2.setColor(new Color(255,255,255,230)); g2.setFont(new Font("SansSerif",Font.BOLD,12));
            g2.drawString(labels[i],bx+5,by+30);
            // sub-label
            boolean ready=subs[i].contains("READY")||subs[i].equals("free");
            g2.setColor(isHP[i]?new Color(100,255,130):ready?new Color(80,255,100):new Color(255,190,60));
            g2.setFont(new Font("SansSerif",Font.BOLD,11));
            g2.drawString(subs[i],bx+5,by+46);
            // bottom separator
            g2.setColor(sel?new Color(255,215,60,160):new Color(180,155,80,80));
            g2.setStroke(new BasicStroke(1f));
            g2.drawLine(bx+6,by+box-6,bx+box-6,by+box-6);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    // ── Dash bar ──────────────────────────────────────────────────────────────
    public void drawDashBar(Graphics2D g2, int dashCooldown) {
        float fill=dashCooldown>0?1f-dashCooldown/(float)Player.DASH_COOLDOWN:1f;
        boolean ready=fill>=1f;
        int bx=10, by=158, bw=130, bh=10;   // shifted down to sit below stats panel

        g2.setColor(new Color(0,0,0,150)); g2.fillRoundRect(bx-4,by-16,bw+92,bh+22,8,8);
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(ready?new Color(100,200,255,100):new Color(60,130,180,70));
        g2.drawRoundRect(bx-4,by-16,bw+92,bh+22,8,8);

        g2.setFont(new Font("SansSerif",Font.BOLD,10));
        g2.setColor(ready?new Color(180,230,255):new Color(100,160,210));
        g2.drawString("\u26A1 DASH", bx, by-3);

        drawBar(g2, bx, by, bw, bh,
                new Color(10,20,40),
                ready?new Color(100,210,255):new Color(50,120,190),
                ready?new Color(60,160,220):new Color(30,80,140),
                fill);

        g2.setFont(new Font("SansSerif",Font.BOLD,10));
        g2.setColor(ready?new Color(140,230,255):new Color(90,150,200));
        g2.drawString(ready?"READY":Math.round((1f-fill)*100)+"%", bx+bw+6, by+bh);
    }

    // ── Pause menu ────────────────────────────────────────────────────────────
    public void drawPause(Graphics2D g2, int W, int H, int hoveredIndex) {
        int cx=W/2, cy=H/2;
        g2.setColor(new Color(0,0,0,160)); g2.fillRect(0,0,W,H);
        int pw=340, ph=300, px=cx-pw/2, py=cy-ph/2;
        drawPanel(g2, px, py, pw, ph, 16);

        // top accent
        g2.setPaint(new GradientPaint(px,py,new Color(212,175,55,30),px+pw,py,new Color(212,175,55,0)));
        g2.fillRoundRect(px,py,pw,34,16,16);

        // title
        g2.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,34));
        FontMetrics fm=g2.getFontMetrics();
        String title="\u23F8 PAUSED";
        g2.setColor(new Color(0,0,0,200)); g2.drawString(title,cx-fm.stringWidth(title)/2+2,cy-82);
        g2.setColor(GOLD); g2.drawString(title,cx-fm.stringWidth(title)/2,cy-84);

        g2.setColor(GOLD_DIM); g2.setStroke(new BasicStroke(1f));
        g2.drawLine(px+20,cy-64,px+pw-20,cy-64);

        String[] btns={"RESUME","MAIN MENU"};
        for (int i=0;i<btns.length;i++) {
            int by=cy-8+i*82; boolean hov=(hoveredIndex==i);
            g2.setColor(new Color(0,0,0,100)); g2.fillRoundRect(cx-122,by-22,244,46,12,12);
            g2.setPaint(new GradientPaint(cx-120,by-20,
                hov?new Color(80,62,10,230):new Color(22,14,4,210),
                cx-120,by+24,
                hov?new Color(40,30,5,230):new Color(10,6,2,210)));
            g2.fillRoundRect(cx-120,by-20,240,44,12,12);
            if (hov) { g2.setStroke(new BasicStroke(3f)); g2.setColor(GOLD_GLOW); g2.drawRoundRect(cx-121,by-21,242,46,13,13); }
            g2.setStroke(new BasicStroke(hov?2f:1.2f));
            g2.setColor(hov?GOLD:GOLD_DRK); g2.drawRoundRect(cx-120,by-20,240,44,12,12);
            g2.setFont(new Font("Serif",Font.BOLD,20));
            g2.setColor(hov?new Color(255,225,110):new Color(210,190,150));
            FontMetrics bfm=g2.getFontMetrics();
            g2.drawString(btns[i],cx-bfm.stringWidth(btns[i])/2,by+8);
        }
        g2.setStroke(new BasicStroke(1f));
    }

    // ── Game-over screen ──────────────────────────────────────────────────────
    public void drawGameOver(Graphics2D g2, int W, int H,
                             String playerName, int score, int coins,
                             int level, int hoveredIndex) {
        int cx=W/2, cy=H/2;
        g2.setColor(new Color(0,0,0,180)); g2.fillRect(0,0,W,H);
        int pw=420, ph=360, px=cx-pw/2, py=cy-ph/2;
        drawPanel(g2, px, py, pw, ph, 16);

        // red top accent + override border
        g2.setPaint(new GradientPaint(px,py,new Color(180,20,20,50),px+pw,py,new Color(180,20,20,0)));
        g2.fillRoundRect(px,py,pw,38,16,16);
        g2.setStroke(new BasicStroke(1.8f)); g2.setColor(new Color(200,40,40));
        g2.drawRoundRect(px,py,pw,ph,16,16);

        // title
        g2.setFont(new Font("Serif",Font.BOLD|Font.ITALIC,38));
        FontMetrics fm=g2.getFontMetrics();
        String title="\u2620 GAME OVER \u2620";
        g2.setColor(new Color(0,0,0,200)); g2.drawString(title,cx-fm.stringWidth(title)/2+2,cy-104);
        g2.setColor(new Color(220,50,50)); g2.drawString(title,cx-fm.stringWidth(title)/2,cy-106);

        g2.setColor(new Color(200,40,40,120)); g2.setStroke(new BasicStroke(1f));
        g2.drawLine(px+20,cy-84,px+pw-20,cy-84);

        String[][] rows={{"\u25B6 Player",playerName},{"\u2605 Score",String.valueOf(score)},{"\u2726 Coins",String.valueOf(coins)},{"\u25B6 Level",String.valueOf(level)}};
        Color[] valCols={new Color(140,210,255),Color.WHITE,GOLD,new Color(180,255,180)};
        int rowY=cy-62;
        for (int i=0;i<rows.length;i++) {
            g2.setFont(new Font("SansSerif",Font.PLAIN,13)); g2.setColor(new Color(200,185,155));
            g2.drawString(rows[i][0],cx-150,rowY);
            g2.setFont(new Font("SansSerif",Font.BOLD,13)); g2.setColor(valCols[i]);
            g2.drawString(rows[i][1],cx+30,rowY);
            g2.setColor(new Color(255,255,255,15)); g2.setStroke(new BasicStroke(0.8f));
            g2.drawLine(cx-150,rowY+6,cx+150,rowY+6);
            rowY+=28;
        }

        // leaderboard note
        g2.setFont(new Font("SansSerif",Font.BOLD|Font.ITALIC,12));
        g2.setColor(new Color(212,175,55,200));
        FontMetrics sfm=g2.getFontMetrics();
        String saved="\u2756 Run saved to leaderboard! \u2756";
        g2.drawString(saved,cx-sfm.stringWidth(saved)/2,rowY+10);

        // main menu button
        int by=cy+112; boolean hov=(hoveredIndex==2);
        g2.setColor(new Color(0,0,0,100)); g2.fillRoundRect(cx-112,by-22,224,46,12,12);
        g2.setPaint(new GradientPaint(cx-110,by-20,
            hov?new Color(80,62,10,230):new Color(22,14,4,210),
            cx-110,by+24,
            hov?new Color(40,30,5,230):new Color(10,6,2,210)));
        g2.fillRoundRect(cx-110,by-20,220,44,12,12);
        if (hov) { g2.setStroke(new BasicStroke(3f)); g2.setColor(GOLD_GLOW); g2.drawRoundRect(cx-111,by-21,222,46,13,13); }
        g2.setStroke(new BasicStroke(hov?2f:1.2f));
        g2.setColor(hov?GOLD:GOLD_DRK); g2.drawRoundRect(cx-110,by-20,220,44,12,12);
        g2.setFont(new Font("Serif",Font.BOLD,20));
        g2.setColor(hov?new Color(255,225,110):new Color(210,190,150));
        FontMetrics bfm=g2.getFontMetrics();
        g2.drawString("MAIN MENU",cx-bfm.stringWidth("MAIN MENU")/2,by+8);
        g2.setStroke(new BasicStroke(1f));
    }
}
