import shijianjianting.bianliang.guojia.country;
import shijianjianting.gongju.PaintBoard;
import shijianjianting.gongju.chengshi;
import shijianjianting.gongju.zuobiao;
import shunxu.first_daoruguojia.first_daoruguojia;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;
import java.util.Vector;

import static shijianjianting.gongju.bianliang.*;

public class Main extends PaintBoard {

    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {

            gameframe.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
            gameframe.setUndecorated(true);   // 全屏前必须无装饰

            JPanel panel = new JPanel(new BorderLayout());
            panel.setBackground(Color.BLACK);


            board.setBackground(Color.BLACK);   // 黑底画板
            board.setOpaque(true);
            // 视图中心点只能在东经 -30° 到 60° 之间



            panel.add(board, BorderLayout.CENTER);   // ★ 关键：加进去

            gameframe.setContentPane(panel);

            // ESC 退出全屏
            panel.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW)
                    .put(KeyStroke.getKeyStroke("ESCAPE"), "exitFullScreen");
            panel.getActionMap().put("exitFullScreen", new AbstractAction() {
                @Override public void actionPerformed(ActionEvent e) {
                    GraphicsEnvironment.getLocalGraphicsEnvironment()
                            .getDefaultScreenDevice().setFullScreenWindow(null);
                    gameframe.dispose();
                }
            });

            // ★ 等布局完成后再画：用 componentResized 保证有真实尺寸
            board.addComponentListener(new ComponentAdapter() {
                private boolean drawn = false;
                @Override public void componentResized(ComponentEvent e) {
                    if (drawn) return;
                    drawn = true;
                    board.setBrushColor(Color.RED);
                    board.setBrushSize(3);
                    board.drawLine(50, 50, 300, 200);

                    board.setBrushColor(Color.BLUE);
                    board.setBrushSize(8);
                    board.drawOval(100, 100, 150, 100);

                    board.setBrushColor(Color.WHITE);
                    board.drawText("Hello", 200, 300, 36);
                    board.drawText(String.valueOf(board.shibiaojingweidu()[0]), 200, 300, 36);
                    board.drawText(String.valueOf(board.shibiaojingweidu()[1]), 200, 300, 36);
                }
            });

            GraphicsDevice gd = GraphicsEnvironment
                    .getLocalGraphicsEnvironment().getDefaultScreenDevice();

            if (gd.isFullScreenSupported()) {
                gd.setFullScreenWindow(gameframe);   // 会自动显示
            } else {
                gameframe.setExtendedState(JFrame.MAXIMIZED_BOTH);
                gameframe.setVisible(true);
            }
        });
        first_daoruguojia.daoruguojia();
        for(country country:countries){
            Vector<zuobiao> difang=country.zuobiaozu_GUOJIAQUANTU;

            Vector<chengshi> chengshiB=country.zuobiaozu_DACHENGSHI;
            Color color=country.color;
            int a1=0;
            for(zuobiao zuobiao:difang){
                board.addGeoDot(zuobiao.x, zuobiao.y, 0.3, color);


            }
            a1=0;
            for(chengshi chengshia:chengshiB){
                JButton btn = chengshiB.get(a1).anniu;
                PaintBoard.GeoText t = board.addGeoText(
                        chengshia.name,             // 文字内容
                        chengshia.zuobiao.x,        // 经度
                        chengshia.zuobiao.y,        // 纬度
                        2,                  // 字号（基准）
                        Color.WHITE,btn);        // 颜色
                        // 相对锚点向上 25 像素


                board.addGeoComponent(btn, chengshia.zuobiao.x, chengshia.zuobiao.y);
                a1++;

            }
        }

    }
}