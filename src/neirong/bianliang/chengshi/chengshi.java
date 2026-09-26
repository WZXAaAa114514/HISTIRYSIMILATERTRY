package neirong.bianliang.chengshi;


import neirong.gongju.zhujie.Live;
import neirong.gongju.zhujie.LiveObject;
import neirong.bianliang.zuobiao.zuobiao;

import javax.swing.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class chengshi extends LiveObject  {

    public zuobiao zuobiao;
    public JButton anniu;

    @Live(Live.Role.TEXT) public String name;

    public int bianhaoTEXT;
    public int bianhaoBUTTOM;

    public chengshi(zuobiao zuobiao, JButton anniu) {
        this.zuobiao = zuobiao;
        this.anniu = anniu;
        this.name = anniu == null ? "" : anniu.getText();
    }

    public chengshi(zuobiao zuobiao, JButton anniu, String N) {
        this.zuobiao = zuobiao;
        this.anniu = anniu;
        this.name = N;
    }

    /** 位置直接映射到内部 zuobiao，让 @Live 自动跟随 */
    @Live(Live.Role.LON)
    public double liveLon() { return zuobiao == null ? 0 : zuobiao.x; }

    @Live(Live.Role.LAT)
    public double liveLat() { return zuobiao == null ? 0 : zuobiao.y; }

    /** 修改名字的推荐入口：改完立刻通知重绘 */
    public void rename(String newName) {
        this.name = newName;
        fireChanged();
    }

    /** 修改位置的推荐入口 */
    public void moveTo(double lon, double lat) {
        if (zuobiao == null) zuobiao = new zuobiao(lon, lat);
        else { zuobiao.x = lon; zuobiao.y = lat; }
        fireChanged();
    }

    public static List<chengshi> kuaisuchuangjianchengshi_VECTOR(
            List<zuobiao> zuobiaos, JButton anniu, List<String> name) {
        List<chengshi> a = new ArrayList<>();
        int a1 = 0;
        for (zuobiao as : zuobiaos) {
            a.add(new chengshi(as, anniu, name.get(a1)));
            a1++;
        }
        return a;
    }

    public static List<chengshi> kuaisushengchengVectorchengshi_WUVECTOR(
            double[] xs, double[] ys, String[] names, JButton a) {
        if (xs.length != ys.length || xs.length != names.length) {
            throw new IllegalArgumentException("三个数组长度必须一致");
        }
        List<chengshi> vector = new ArrayList<>();
        for (int i = 0; i < xs.length; i++) {
            vector.add(new chengshi(new zuobiao(xs[i], ys[i]), a, names[i]));
        }
        return vector;
    }


}