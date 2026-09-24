
import shijianjianting.bianliang.guojia.country;
import shijianjianting.bianliang.bingpai.BINGPAI;
import shijianjianting.bianliang.chengshi.chengshi;
import shijianjianting.gongju.zhujie.LiveRegistry;
import shijianjianting.bianliang.zuobiao.zuobiao;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static shijianjianting.gongju.bianliang.*;

public class Main {

    public static void main(String[] args) {
        System.setProperty("sun.java2d.d3d", "True");
        System.setProperty("sun.java2d.noddraw", "True");
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
            shunxu.util.first_daoruguojia.daoruguojia();
            bindCountriesToBoard();

            board.centerOn(104, 35);
            board.setZoom(8);
        });
    }

    // ============================================================
    //  把所有 country / chengshi / BINGPAI 绑定到 PaintBoard
    // ============================================================
    private static void bindCountriesToBoard() {
        board.beginBatch();

        for (country c : countries) {
            final country cc = c;

            // ---------- 国家地图点（位置来自 zuobiao，颜色来自 country） ----------
            for (zuobiao z : cc.zuobiaozu_GUOJIAQUANTU) {
                board.addGeoDot(z.x, z.y, 0.005d, cc.color);
            }

            // ---------- 城市 ----------
            for (chengshi cs : cc.zuobiaozu_DACHENGSHI) {
                cs.bianhaoTEXT   = board.addAutoText(cs, 3, cs.anniu);
                cs.bianhaoBUTTOM = board.addAutoComponent(cs.anniu, cs);

                // 可选：若希望 setter 立刻重绘（不依赖定时器）：
                // cs.bind(board::repaint);
            }

            // ---------- 军队 ----------
            for (BINGPAI b : cc.jundui) {
                b.bianhao = board.addAutoComponent(b, b);
            }

            // ---------- 首都 ----------
            final chengshi cap = cc.shoudu;
            if (cap != null) {
                cap.bianhaoBUTTOM = board.addAutoText(cap, 1, cap.anniu);
                board.addAutoComponent(cap.anniu, cap);
            }
        }

        board.endBatch();
    }
}