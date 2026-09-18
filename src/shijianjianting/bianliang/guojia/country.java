package shijianjianting.bianliang.guojia;

import shijianjianting.gongju.chengshi;
import shijianjianting.gongju.ditushengchengqi.china;
import shijianjianting.gongju.zuobiao;

import java.awt.*;
import java.util.Vector;

public class country {
    public enum countrys {
        CHINA(china.generate(20000));
        private final Vector<zuobiao> NAME;
        countrys(Vector<zuobiao> generate) {
            this.NAME = generate;
        }
        public Vector<zuobiao> get() {
            return NAME;
        }

    }



    public Vector<zuobiao> zuobiaozu_GUOJIAQUANTU=new Vector<zuobiao>();

    public Vector<chengshi> zuobiaozu_DACHENGSHI=new Vector<chengshi>();
    public Color color;
    public country(Vector<zuobiao> a, Vector<zuobiao> weizhi,Vector<chengshi> b,Color color){
        zuobiaozu_GUOJIAQUANTU=a;

        zuobiaozu_DACHENGSHI=b;
        this.color=color;
    }



}
