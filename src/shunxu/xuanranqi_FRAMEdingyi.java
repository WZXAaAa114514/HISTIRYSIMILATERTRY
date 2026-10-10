package shunxu;

import neirong.gongju.bianliang;
import neirong.gongju.zhujie.LiveRegistry;
import shunxu.second_huadituheguojia.bangdingluoji;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static neirong.gongju.bianliang.board;
import static neirong.gongju.bianliang.gameframe;

public class xuanranqi_FRAMEdingyi {
    public static void start(){
        SwingUtilities.invokeLater(() -> {

            gameframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            gameframe.setUndecorated(true);

            JPanel panel = bianliang.zhuyemian;
            panel.setBackground(Color.BLACK);

            board.setBackground(Color.BLACK);
            board.setOpaque(true);

            panel.add(board, BorderLayout.CENTER);
            gameframe.setContentPane(bianliang.zhuyemian);

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
            board.centerOn(104, 35);
            board.setZoom(8);
            bangdingluoji.bachuadedongxigaoshanghuaban();


        });
    }
}
