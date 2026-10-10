
import neirong.bianliang.xuanding.shubiaojianting;
import neirong.gongju.gongju;
import neirong.gongju.pingmujilei.XYBoard;
import neirong.gongju.pingmujilei.tuodongkuang;
import shunxu.anjianhuoqu.anjianhuoqu;
import shunxu.first_daoruguojia.first_daoruguojia;
import shunxu.third_shijianpaifaqiqidong.EventBus;
import neirong.shijian.MyListener;
import shunxu.third_shijianpaifaqiqidong.shijian.anjian;
import shunxu.xuanranqi_FRAMEdingyi;


import javax.swing.*;
import java.awt.*;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.List;
import java.util.stream.Collectors;

import static neirong.gongju.bianliang.*;

public class Main {

    public static void main(String[] args) {
        EVENTMAIN=new EventBus();
        EVENTMAIN.register(new MyListener());
        board.setGuijishifoukangjuchi(false);
        javax.swing.Timer timer = new javax.swing.Timer(33, e -> {
            // 统一 tick 所有 BINGPAI，每个只 tick 一次


            board.repaint();
        });
        timer.start();
        System.setProperty("sun.java2d.d3d", "True");
        System.setProperty("sun.java2d.noddraw", "True");
        xuanranqi_FRAMEdingyi.start();
        first_daoruguojia.daoruguojia();
        gongju.shiftdownstart();
        shunxu.second_huadituheguojia.huahua.hua();
        shunxu.fourth_huizhishijian.huizhi.draw();
        shubiaojianting.kaishijianting();

        gameframe.addWindowListener(new WindowAdapter() {
            @Override
            public void windowLostFocus(WindowEvent e) {
                // 防止按键状态卡住
                anjianhuoqu.clear();
            }
        });
        anjianhuoqu.addListener(pressedKeys -> {
            List<String> names = pressedKeys.stream()
                    .map(KeyEvent::getKeyText)
                    .sorted()
                    .collect(Collectors.toList());
            EVENTMAIN.post(new anjian(names));


        });
        PLAYCOUNTRY=countries.get(0);
        XYBoard sub = new XYBoard(320, 220);
        sub.setBackground(new Color(30, 30, 36));
        sub.setWorldPainter((g2, b) -> {
            g2.setColor(Color.WHITE);
            double[] p = b.worldToScreen(0, 0);
            g2.fillOval((int) p[0] - 5, (int) p[1] - 5, 10, 10);
        });



    }


    // ============================================================
    //  把所有 country / chengshi / BINGPAI 绑定到 PaintBoard
    // ============================================================


}