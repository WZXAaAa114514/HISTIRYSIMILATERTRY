package shunxu.second_huadituheguojia;

import shijianjianting.gongju.zhujie.LiveRegistry;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static shijianjianting.gongju.bianliang.board;
import static shijianjianting.gongju.bianliang.gameframe;

public class huahua {
    public static void hua(){
        SwingUtilities.invokeLater(() -> {

            gameframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            gameframe.setUndecorated(true);

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(Color.BLACK);

            board.setBackground(Color.BLACK);
            board.setOpaque(true);

            panel.add(board, BorderLayout.CENTER);
            gameframe.setContentPane(panel);

            panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke("ESCAPE"), "exitFullScreen");
            panel.getActionMap().put("exitFullScreen", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getDefaultScreenDevice().setFullScreenWindow(null);
                    gameframe.dispose();
                }
            });

            GraphicsDevice gd = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice();

            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(gameframe);
            } else {
                gameframe.setExtendedState(JFrame.MAXIMIZED_BOTH);
                gameframe.setVisible(true);
            }

            // ★ 关键：开启"全自动脏检测"，30fps 轮询所有 @Live 字段
            LiveRegistry.startTimer(33);

            // 加载国家，绑定到画板

            bangdingluoji.bachuadedongxigaoshanghuaban();

            board.centerOn(104, 35);
            board.setZoom(8);
        });
    }
}
