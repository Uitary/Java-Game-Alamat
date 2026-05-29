import java.awt.*;
import java.awt.image.BufferedImage;

public class Shop {

    // ── Slot icon images (set by GamePanel after loading) ─────────────────────
    private BufferedImage iSword, iBow, iBolt, iBeam, iPunch, iPower, iShield, iPotion;

    /** Called by GamePanel.loadHotbarIcons() so the shop shares the same images. */
    public void loadIcons(BufferedImage sword, BufferedImage bow,
                          BufferedImage bolt,  BufferedImage beam,
                          BufferedImage punch, BufferedImage power,
                          BufferedImage shield, BufferedImage potion) {
        iSword  = sword;  iBow    = bow;
        iBolt   = bolt;   iBeam   = beam;
        iPunch  = punch;  iPower  = power;
        iShield = shield; iPotion = potion;
    }

    public int getSwordCost(int level)  { return level >= 20 ? -1 : (level < 10 ? (level+1)*20 : (level+1)*30); }
    public int getBowCost(int level)    { return level >= 20 ? -1 : (level < 10 ? (level+1)*20 : (level+1)*30); }
    public int getSkillCost(int level)  { return level >= 20 ? -1 : (level < 10 ? (level+1)*25 : (level+1)*35); }
    public int getMaxHPCost(int level)  { return level >= Equipments.MaxHP.MAX_LEVEL ? -1 : (level+1)*30; }
    public int getPotionCost(int count) { return count >= Equipments.Potion.MAX_POTIONS ? -1 : 15; }

    private static String tier(int level) { return level >= 10 ? "Asc.Lv"+(level-9) : "Lv"+(level+1); }

    public void drawShop(Graphics2D g2, int W, int H, int hov, int coins,
                         Equipments.Sword sword, Equipments.Bow bow,
                         Equipments.MaxHP maxhp, Equipments.Potion potion) {
        drawPanel(g2, W, H, hov, coins,
            new String[]{ "Sword", "Bow", "Max HP", "Potion" },
            new String[]{
                "DMG:" + sword.getDamage() + " (" + sword.getTier() + ")",
                "DMG:" + bow.getDamage()   + " (" + bow.getTier()   + ")",
                maxhp.getLabel() + " (" + maxhp.getTier() + ")",
                "HP+20  (" + potion.count + "/" + Equipments.Potion.MAX_POTIONS + ")"
            },
            new int[]{ getSwordCost(sword.level), getBowCost(bow.level),
                       getMaxHPCost(maxhp.level), getPotionCost(potion.count) },
            new BufferedImage[]{ iSword, iBow, iShield, iPotion });
    }

    public void drawMageShop(Graphics2D g2, int W, int H, int hov, int coins,
                              int beamLevel, int boltLevel,
                              Equipments.MaxHP maxhp, Equipments.Potion potion) {
        drawPanel(g2, W, H, hov, coins,
            new String[]{ "Arcane Beam", "Magic Bolt", "Max HP", "Potion" },
            new String[]{
                "DMG:" + (Mage.BEAM_DAMAGE + beamLevel*6) + " (" + tier(beamLevel) + ")",
                "DMG:" + (Mage.BOLT_DAMAGE + boltLevel*4) + " (" + tier(boltLevel) + ")",
                maxhp.getLabel() + " (" + maxhp.getTier() + ")",
                "HP+20  (" + potion.count + "/" + Equipments.Potion.MAX_POTIONS + ")"
            },
            new int[]{ getSkillCost(beamLevel), getSkillCost(boltLevel),
                       getMaxHPCost(maxhp.level), getPotionCost(potion.count) },
            new BufferedImage[]{ iBeam, iBolt, iShield, iPotion });
    }

    public void drawFighterShop(Graphics2D g2, int W, int H, int hov, int coins,
                                 int punchLevel, int powerLevel,
                                 Equipments.MaxHP maxhp, Equipments.Potion potion) {
        drawPanel(g2, W, H, hov, coins,
            new String[]{ "Heavy Punch", "Ulti Mon", "Max HP", "Potion" },
            new String[]{
                "DMG:" + (Fighter.PUNCH_DAMAGE       + punchLevel*8) + " (" + tier(punchLevel) + ")",
                "DMG:" + (Fighter.POWER_PUNCH_DAMAGE + powerLevel *5) + " (" + tier(powerLevel)  + ")",
                maxhp.getLabel() + " (" + maxhp.getTier() + ")",
                "HP+20  (" + potion.count + "/" + Equipments.Potion.MAX_POTIONS + ")"
            },
            new int[]{ getSkillCost(punchLevel), getSkillCost(powerLevel),
                       getMaxHPCost(maxhp.level), getPotionCost(potion.count) },
            new BufferedImage[]{ iPunch, iPower, iShield, iPotion });
    }

    // ── Shared panel renderer (4 rows) ────────────────────────────────────────
    private void drawPanel(Graphics2D g2, int W, int H, int hov, int coins,
                            String[] names, String[] stats, int[] costs, BufferedImage[] icons) {
        int pW = 500, pH = 500, px = W / 2 - pW / 2, py = H / 2 - pH / 2;

        // ── Backdrop shadow ───────────────────────────────────────────────────
        g2.setColor(new Color(0, 0, 0, 170));
        g2.fillRect(0, 0, W, H);

        // ── Panel base (dark gradient) ────────────────────────────────────────
        g2.setPaint(new java.awt.GradientPaint(px, py, new Color(10, 6, 22, 245),
                px, py + pH, new Color(22, 12, 42, 245)));
        g2.fillRoundRect(px, py, pW, pH, 20, 20);

        // ── Outer glow ring ───────────────────────────────────────────────────
        g2.setStroke(new BasicStroke(5f));
        g2.setColor(new Color(180, 130, 30, 45));
        g2.drawRoundRect(px - 3, py - 3, pW + 6, pH + 6, 22, 22);

        // ── Gold border ───────────────────────────────────────────────────────
        g2.setStroke(new BasicStroke(2f));
        g2.setColor(new Color(212, 175, 55));
        g2.drawRoundRect(px, py, pW, pH, 20, 20);

        // ── Inner subtle border ───────────────────────────────────────────────
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(212, 175, 55, 50));
        g2.drawRoundRect(px + 5, py + 5, pW - 10, pH - 10, 16, 16);

        // ── Header area ───────────────────────────────────────────────────────
        g2.setPaint(new java.awt.GradientPaint(px, py, new Color(60, 40, 8, 220),
                px, py + 52, new Color(22, 12, 42, 0)));
        g2.fillRoundRect(px, py, pW, 52, 20, 20);
        g2.fillRect(px, py + 36, pW, 16);

        // ── Title ─────────────────────────────────────────────────────────────
        g2.setFont(new Font("Serif", Font.BOLD | Font.ITALIC, 24));
        FontMetrics fmT = g2.getFontMetrics();
        String shopTitle = "\u2605  SHOP  \u2605";
        int titleX = W / 2 - fmT.stringWidth(shopTitle) / 2;
        g2.setColor(new Color(0, 0, 0, 160));
        g2.drawString(shopTitle, titleX + 2, py + 34);
        g2.setColor(new Color(255, 220, 80));
        g2.drawString(shopTitle, titleX, py + 32);

        // ── Coin display (top-left) ───────────────────────────────────────────
        g2.setColor(new Color(0, 0, 0, 160));
        g2.fillRoundRect(px + 12, py + 10, 110, 24, 8, 8);
        g2.setColor(new Color(212, 175, 55, 140));
        g2.setStroke(new BasicStroke(1f));
        g2.drawRoundRect(px + 12, py + 10, 110, 24, 8, 8);
        // Coin circle icon
        g2.setColor(new Color(255, 210, 40));
        g2.fillOval(px + 19, py + 15, 13, 13);
        g2.setColor(new Color(180, 130, 10));
        g2.setStroke(new BasicStroke(1.2f));
        g2.drawOval(px + 19, py + 15, 13, 13);
        g2.setFont(new Font("SansSerif", Font.BOLD, 9));
        g2.setColor(new Color(120, 80, 5));
        g2.drawString("G", px + 22, py + 25);
        // Coin amount
        g2.setFont(new Font("SansSerif", Font.BOLD, 13));
        g2.setColor(new Color(255, 230, 120));
        g2.drawString(String.valueOf(coins), px + 38, py + 26);

        // ── Close hint (top-right) ────────────────────────────────────────────
        g2.setFont(new Font("SansSerif", Font.PLAIN, 10));
        g2.setColor(new Color(160, 140, 100, 180));
        g2.drawString("[E] Close", px + pW - 72, py + 16);

        // ── Divider under header ──────────────────────────────────────────────
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(new Color(212, 175, 55, 100));
        g2.drawLine(px + 16, py + 54, px + pW - 16, py + 54);

        // ── Category accent colours ───────────────────────────────────────────
        Color[] accentTop = {
            new Color(200, 70, 40, 200),   // slot 0 – attack  (red-orange)
            new Color(60, 120, 200, 200),  // slot 1 – ranged  (blue)
            new Color(40, 160, 80, 200),   // slot 2 – defense / HP (green)
            new Color(160, 60, 200, 200)   // slot 3 – potion  (purple)
        };
        Color[] accentBot = {
            new Color(140, 40, 20, 160),
            new Color(30, 70, 160, 160),
            new Color(20, 100, 50, 160),
            new Color(100, 30, 160, 160)
        };
        Color[] accentBorder = {
            new Color(255, 130, 80),
            new Color(100, 180, 255),
            new Color(80, 230, 130),
            new Color(200, 120, 255)
        };

        for (int i = 0; i < 4; i++) {
            int ry = py + 64 + i * 104;
            boolean isHov = (hov == i);
            boolean maxed = (costs[i] == -1);

            // ── Row shadow ────────────────────────────────────────────────────
            g2.setColor(new Color(0, 0, 0, 120));
            g2.fillRoundRect(px + 16, ry + 4, pW - 32, 90, 12, 12);

            // ── Row background ────────────────────────────────────────────────
            Color rowBase = isHov ? new Color(55, 42, 12, 220) : new Color(18, 12, 32, 200);
            g2.setColor(rowBase);
            g2.fillRoundRect(px + 14, ry, pW - 28, 90, 12, 12);

            // ── Left accent stripe ────────────────────────────────────────────
            g2.setPaint(new java.awt.GradientPaint(px + 14, ry, accentTop[i],
                    px + 14, ry + 90, accentBot[i]));
            g2.fillRoundRect(px + 14, ry, 6, 90, 4, 4);

            // ── Row border ────────────────────────────────────────────────────
            g2.setStroke(new BasicStroke(isHov ? 1.8f : 1f));
            g2.setColor(isHov ? accentBorder[i] : new Color(accentBorder[i].getRed(),
                    accentBorder[i].getGreen(), accentBorder[i].getBlue(), 100));
            g2.drawRoundRect(px + 14, ry, pW - 28, 90, 12, 12);
            g2.setStroke(new BasicStroke(1f));

            // ── Icon badge (left) ─────────────────────────────────────────────
            int ix = px + 28, iy = ry + 18;
            g2.setPaint(new java.awt.GradientPaint(ix, iy, accentTop[i], ix, iy + 54, accentBot[i]));
            g2.fillRoundRect(ix, iy, 54, 54, 10, 10);
            g2.setColor(new Color(accentBorder[i].getRed(), accentBorder[i].getGreen(),
                    accentBorder[i].getBlue(), 180));
            g2.setStroke(new BasicStroke(1.5f));
            g2.drawRoundRect(ix, iy, 54, 54, 10, 10);
            g2.setStroke(new BasicStroke(1f));
            drawShopIcon(g2, i, ix + 27, iy + 27, icons != null && i < icons.length ? icons[i] : null);

            // ── Name ──────────────────────────────────────────────────────────
            int tx = px + 92;
            g2.setFont(new Font("Serif", Font.BOLD, 17));
            g2.setColor(new Color(0, 0, 0, 140));
            g2.drawString(names[i], tx + 1, ry + 32);
            g2.setColor(Color.WHITE);
            g2.drawString(names[i], tx, ry + 31);

            // ── Stat line ─────────────────────────────────────────────────────
            g2.setFont(new Font("SansSerif", Font.PLAIN, 12));
            g2.setColor(new Color(accentBorder[i].getRed(), accentBorder[i].getGreen(),
                    accentBorder[i].getBlue(), 220));
            g2.drawString(stats[i], tx, ry + 52);

            // ── Buy button ────────────────────────────────────────────────────
            int btnX = px + pW - 150, btnY = ry + 26, btnW = 128, btnH = 38;
            String btnTxt = maxed ? "MAXED OUT" : "Buy  " + costs[i] + " G";

            // Button shadow
            g2.setColor(new Color(0, 0, 0, 140));
            g2.fillRoundRect(btnX + 2, btnY + 2, btnW, btnH, 10, 10);

            // Button fill
            if (maxed) {
                g2.setColor(new Color(35, 35, 35, 200));
            } else if (isHov) {
                g2.setPaint(new java.awt.GradientPaint(btnX, btnY, accentTop[i],
                        btnX, btnY + btnH, accentBot[i]));
            } else {
                g2.setPaint(new java.awt.GradientPaint(btnX, btnY, new Color(60, 44, 8, 220),
                        btnX, btnY + btnH, new Color(35, 24, 4, 220)));
            }
            g2.fillRoundRect(btnX, btnY, btnW, btnH, 10, 10);

            // Button border
            g2.setStroke(new BasicStroke(1.5f));
            g2.setColor(maxed ? new Color(70, 70, 70) : (isHov ? accentBorder[i] : new Color(180, 140, 30)));
            g2.drawRoundRect(btnX, btnY, btnW, btnH, 10, 10);
            g2.setStroke(new BasicStroke(1f));

            // Coin icon on button (if not maxed)
            if (!maxed) {
                g2.setColor(new Color(255, 210, 40));
                g2.fillOval(btnX + 8, btnY + 11, 14, 14);
                g2.setColor(new Color(160, 110, 5));
                g2.setStroke(new BasicStroke(1f));
                g2.drawOval(btnX + 8, btnY + 11, 14, 14);
                g2.setFont(new Font("SansSerif", Font.BOLD, 8));
                g2.setColor(new Color(100, 60, 0));
                g2.drawString("G", btnX + 12, btnY + 22);
            }

            // Button text
            g2.setFont(new Font("SansSerif", Font.BOLD, 13));
            g2.setColor(maxed ? new Color(110, 110, 110) : Color.WHITE);
            FontMetrics fmB = g2.getFontMetrics();
            int textOff = maxed ? 0 : 10;
            g2.drawString(btnTxt,
                    btnX + textOff + (btnW - textOff - fmB.stringWidth(btnTxt)) / 2,
                    btnY + btnH / 2 + fmB.getAscent() / 2 - 2);
        }
    }

    /** Draws a small representative icon centred at (cx, cy) for each shop slot. */
    /** Draws a slot icon centred at (cx, cy). Uses the PNG if available, else falls back to shapes. */
    private void drawShopIcon(Graphics2D g2, int slot, int cx, int cy, BufferedImage img) {
        int iSize = 38;
        if (img != null) {
            g2.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                    RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g2.drawImage(img, cx - iSize / 2, cy - iSize / 2, iSize, iSize, null);
            return;
        }
        // ── Fallback drawn shapes ─────────────────────────────────────────────
        g2.setColor(new Color(255, 255, 255, 210));
        g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        switch (slot) {
            case 0 -> {
                g2.drawLine(cx - 12, cy + 12, cx + 12, cy - 12);
                g2.setStroke(new BasicStroke(2f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawLine(cx - 8, cy - 6, cx + 6, cy + 8);
                g2.setColor(new Color(255, 220, 120, 220));
                g2.fillOval(cx - 15, cy + 10, 7, 7);
            }
            case 1 -> {
                g2.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
                g2.drawArc(cx - 10, cy - 14, 14, 28, -80, 160);
                g2.setStroke(new BasicStroke(1.5f));
                g2.setColor(new Color(255, 255, 200, 180));
                g2.drawLine(cx - 3, cy - 14, cx - 3, cy + 14);
                g2.setColor(new Color(255, 255, 255, 210));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(cx - 12, cy, cx + 12, cy);
                int[] ax = { cx + 12, cx + 6, cx + 6 };
                int[] ay = { cy, cy - 4, cy + 4 };
                g2.fillPolygon(ax, ay, 3);
            }
            case 2 -> {
                int s = 9;
                g2.setColor(new Color(80, 230, 130, 220));
                g2.fillOval(cx - s, cy - s + 2, s, s);
                g2.fillOval(cx, cy - s + 2, s, s);
                int[] hx = { cx - s, cx + s * 2, cx + s / 2 };
                int[] hy = { cy + 2, cy + 2, cy + s * 2 };
                g2.fillPolygon(hx, hy, 3);
                g2.setColor(new Color(255, 255, 255, 200));
                g2.setStroke(new BasicStroke(2f));
                g2.drawLine(cx + s / 2 - 1, cy - 4, cx + s / 2 - 1, cy + 8);
                g2.drawLine(cx - 2, cy + 2, cx + s, cy + 2);
            }
            case 3 -> {
                g2.setColor(new Color(220, 130, 255, 220));
                g2.fillRoundRect(cx - 8, cy - 6, 16, 16, 6, 6);
                g2.setColor(new Color(255, 200, 255, 180));
                g2.fillRoundRect(cx - 5, cy - 3, 5, 6, 3, 3);
                g2.setColor(new Color(200, 100, 240, 220));
                g2.fillRoundRect(cx - 4, cy - 14, 8, 10, 3, 3);
                g2.setColor(new Color(200, 160, 80, 220));
                g2.fillRoundRect(cx - 3, cy - 16, 6, 5, 2, 2);
                g2.setColor(new Color(255, 255, 255, 100));
                g2.setStroke(new BasicStroke(1.2f));
                g2.drawRoundRect(cx - 8, cy - 6, 16, 16, 6, 6);
            }
        }
        g2.setStroke(new BasicStroke(1f));
    }

    /** Returns hovered slot index (0–3), or -1. */
    public int getHoveredSlot(int mx, int my, int W, int H) {
        int pW = 500, pH = 500, px = W / 2 - pW / 2, py = H / 2 - pH / 2;
        for (int i = 0; i < 4; i++) {
            int ry = py + 64 + i * 104;
            if (mx >= px + 14 && mx <= px + pW - 14 && my >= ry && my <= ry + 90) return i;
        }
        return -1;
    }
}
