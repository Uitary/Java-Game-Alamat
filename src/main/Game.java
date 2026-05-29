import javax.swing.*;
import java.awt.*;

public class Game {
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            GameSettings settings = GameSettings.load();
            JFrame window = new JFrame("Alamat: Survival Night");
            window.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            window.setResizable(false);
            window.setUndecorated(settings.fullscreen);

            MenuPanel menu = new MenuPanel(window, settings);
            window.add(menu);

            if (settings.fullscreen) {
                GraphicsEnvironment.getLocalGraphicsEnvironment()
                    .getDefaultScreenDevice().setFullScreenWindow(window);
            } else {
                window.pack();
                window.setLocationRelativeTo(null);
            }

            window.setVisible(true);
            menu.requestFocusInWindow();
        });
    }
}
