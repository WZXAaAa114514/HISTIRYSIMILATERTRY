package neirong.bianliang.bingpai;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class bingmoshuju {
    public double speed;
    public bingmoshuju(double speed,double fanwei){
        this.speed=speed;this.fanwei=fanwei;
    }
    public List<BINGPAI> gongjizhe=new ArrayList<>();
    public double fanwei;
    public int zhudongxing=10;
    /**
     * 去重添加攻击者。
     *
     * <p>BINGPAI 未重写 equals/hashCode，因此 contains 基于引用比较，
     * 这正是我们想要的语义：同一兵牌对象只加一次。</p>
     */
    public void addGongjizhe(BINGPAI b) {
        if (b == null) return;
        if (!gongjizhe.contains(b)) {
            gongjizhe.add(b);
        }
    }
}