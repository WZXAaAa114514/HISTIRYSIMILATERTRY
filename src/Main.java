
import shijianjianting.bianliang.guojia.country;
import shijianjianting.bianliang.bingpai.BINGPAI;
import shijianjianting.bianliang.chengshi.chengshi;
import shijianjianting.bianliang.xuanding.shubiaojianting;
import shijianjianting.gongju.gongju;
import shijianjianting.gongju.zhujie.LiveRegistry;
import shijianjianting.bianliang.zuobiao.zuobiao;
import shunxu.third_shijianpaifaqiqidong.EventBus;
import shijianjianting.shijian.MyListener;


import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;

import static shijianjianting.gongju.bianliang.*;

public class Main {

    public static void main(String[] args) {
        EVENTMAIN=new EventBus();
        EVENTMAIN.register(new MyListener());


        System.setProperty("sun.java2d.d3d", "True");
        System.setProperty("sun.java2d.noddraw", "True");
        shunxu.util.first_daoruguojia.daoruguojia();
        gongju.shiftdownstart();
        shunxu.second_huadituheguojia.huahua.hua();
        shunxu.fourth_huizhishijian.huizhi.draw();
        shubiaojianting.kaishijianting();

    }

    // ============================================================
    //  把所有 country / chengshi / BINGPAI 绑定到 PaintBoard
    // ============================================================


}