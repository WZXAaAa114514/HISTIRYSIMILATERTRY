package shijianjianting.bianliang.guojia;

import shijianjianting.bianliang.bingpai.BINGPAI;
import shijianjianting.bianliang.chengshi.chengshi;
import shijianjianting.bianliang.guojia.ditushengchengqi.china;

import shijianjianting.gongju.zhujie.Live;
import shijianjianting.bianliang.zuobiao.zuobiao;

import java.awt.*;
import java.util.Vector;

public class country {

    public Vector<BINGPAI> jundui = new Vector<>();

    public enum countrys {
        CHINA(china.generate(20000000));

        private final Vector<zuobiao> NAME;

        countrys(Vector<zuobiao> generate) {
            this.NAME = generate;
        }

        public Vector<zuobiao> get() { return NAME; }
    }

    public chengshi shoudu;

    public Vector<zuobiao> zuobiaozu_GUOJIAQUANTU = new Vector<>();
    public Vector<chengshi> zuobiaozu_DACHENGSHI = new Vector<>();

    @Live(Live.Role.COLOR) public Color color;

    public country(Vector<zuobiao> a, Vector<chengshi> b, Color color, chengshi shoudu) {
        this.zuobiaozu_GUOJIAQUANTU = a;
        this.shoudu = shoudu;
        this.zuobiaozu_DACHENGSHI = b;
        this.color = color;
    }
}