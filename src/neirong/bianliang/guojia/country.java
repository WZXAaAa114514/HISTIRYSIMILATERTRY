package neirong.bianliang.guojia;

import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.chengshi.chengshi;
import neirong.bianliang.guojia.ditushengchengqi.huaditudianji;


import neirong.gongju.gongju;
import neirong.gongju.zhujie.Live;
import neirong.bianliang.zuobiao.zuobiao;
import shunxu.third_shijianpaifaqiqidong.shijian.beixuanze_DUOXUAN;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;


import static neirong.gongju.bianliang.*;
import static neirong.gongju.bianliang.board;

public class country {
    public void doitbeforehuizhi(){

    }
    public List<BINGPAI> jundui = new ArrayList<>();
    public boolean ischuchang=true;
    public List<country> gongjizhe=new ArrayList<>();
    public void shengchengguojia(){


        // ---------- 国家地图点（位置来自 zuobiao，颜色来自 country） ----------
        if(!ischuchang)return;
        for (zuobiao z : this.zuobiaozu_GUOJIAQUANTU) {
            board.addGeoDot(z.x, z.y, 0.005d, this.color);
        }

        // ---------- 城市 ----------
//        for (chengshi cs : this.zuobiaozu_DACHENGSHI) {
//
      //    cs.bianhaoTEXT   = board.addAutoText(cs, 3, cs.anniu);
//            cs.bianhaoBUTTOM = board.addAutoComponent(cs.anniu, cs);
//            cs.anniu.addActionListener(e -> {
//
//
//            });
//            // 可选：若希望 setter 立刻重绘（不依赖定时器）：
//            // cs.bind(board::repaint);
//        }

        // ---------- 军队 ----------
        for (BINGPAI b : this.jundui) {
            b= (BINGPAI) gongju.quchuquanbujiantingqi(b);
            b.bianhao = board.addAutoComponent(b, b);
            BINGPAI finalB2 = b;
            BINGPAI finalB = b;
            b.addActionListener(e -> {
                try {
                    Boolean succeed=true;

                    if(gongju.shiftdown())succeed=xuandingbianliang.add(finalB2);
                    else {
                        xuandingbianliang.clearall();
                        succeed=xuandingbianliang.add(finalB2);
                    }
                    if(succeed)EVENTMAIN.post(new beixuanze_DUOXUAN(finalB));
                } catch (Exception ex) {
                    throw new RuntimeException(ex);
                }

            });


        }

        // ---------- 首都 ----------
        final chengshi cap =this.shoudu;
        if (cap != null) {
            cap.bianhaoBUTTOM = board.addAutoText(cap, 1, cap.anniu);
            board.addAutoComponent(cap.anniu, cap);
        }
    }
    public enum countrys {
        CHINA(huaditudianji.generate("china_boundary.geojson", 1000000)),
        QING(huaditudianji.generate("qing.geojson", 1000000));
        private final List<zuobiao> NAME;

        countrys(List<zuobiao> generate) {
            this.NAME = generate;
        }

        public List<zuobiao> get() { return NAME; }
    }

    public chengshi shoudu;

    public List<zuobiao> zuobiaozu_GUOJIAQUANTU = new ArrayList<>();


    @Live(Live.Role.COLOR) public Color color;

    public country(List<zuobiao> a, Color color, chengshi shoudu) {
        this.zuobiaozu_GUOJIAQUANTU = a;
        this.shoudu = shoudu;

        this.color = color;
    }
}