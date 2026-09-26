package shijianjianting.bianliang.xuanding;

import shijianjianting.bianliang.bingpai.BINGPAI;
import shijianjianting.gongju.bianliang;
import shunxu.third_shijianpaifaqiqidong.s.beiquxiao_DUOXUAN;
import shunxu.third_shijianpaifaqiqidong.s.youjiandianji_DUOXUAN;

import javax.swing.*;
import java.awt.*;
import java.awt.event.MouseEvent;

import static shijianjianting.gongju.bianliang.*;

public class shubiaojianting {
    public static void kaishijianting(){
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (!(event instanceof MouseEvent)) return;

            MouseEvent me = (MouseEvent) event;

            if (me.getID() != MouseEvent.MOUSE_CLICKED) return;
            if (me.getButton() != MouseEvent.BUTTON1) return; // 关键：只处理左键



            if (board.mouseSnap != null && !xuandingbianliang.neirong.isEmpty()) {
                for (Object a : xuandingbianliang.neirong) {
                    EVENTMAIN.post(new beiquxiao_DUOXUAN((BINGPAI) a));
                }
                xuandingbianliang.neirong.clear();
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
        Toolkit.getDefaultToolkit().addAWTEventListener(event -> {
            if (event instanceof MouseEvent) {
                MouseEvent me = (MouseEvent) event;
                if (me.getID() == MouseEvent.MOUSE_CLICKED
                        && SwingUtilities.isRightMouseButton(me)) {   // 改为判断右键
                    // 右键点击处理逻辑
                    for(Object a:xuandingbianliang.neirong){
                        EVENTMAIN.post(new youjiandianji_DUOXUAN((BINGPAI) a, board.shibiaojingweidu()[0],board.shibiaojingweidu()[1]));
                    }
                    System.out.println("右键点击");
                }
            }
        }, AWTEvent.MOUSE_EVENT_MASK);
    }
}
