
import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.xuanding.shubiaojianting;
import neirong.gongju.bianliang;
import neirong.gongju.gongju;
import shunxu.anjianhuoqu.anjianhuoqu;
import shunxu.first_daoruguojia.first_daoruguojia;
import shunxu.third_shijianpaifaqiqidong.EventBus;
import neirong.shijian.MyListener;
import shunxu.third_shijianpaifaqiqidong.shijian.anjian;
import shunxu.third_shijianpaifaqiqidong.shijian.kongzhitaifasong;


import javax.swing.*;

import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import static neirong.gongju.bianliang.*;

public class Main {

    public static void main(String[] args) {
        EVENTMAIN=new EventBus();
        EVENTMAIN.register(new MyListener());
        board.setTrailAntialias(false);
        javax.swing.Timer timer = new javax.swing.Timer(33, e -> {
            // 统一 tick 所有 BINGPAI，每个只 tick 一次

            board.repaint();
        });
        timer.start();
        System.setProperty("sun.java2d.d3d", "True");
        System.setProperty("sun.java2d.noddraw", "True");
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

    }

    // ============================================================
    //  把所有 country / chengshi / BINGPAI 绑定到 PaintBoard
    // ============================================================


}