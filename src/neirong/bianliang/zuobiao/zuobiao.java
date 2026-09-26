package neirong.bianliang.zuobiao;


import neirong.gongju.zhujie.Live;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class zuobiao {

    @Live(Live.Role.LON) public double x;
    @Live(Live.Role.LAT) public double y;

    public zuobiao(double x, double y) {
        this.x = x;
        this.y = y;
    }

    public static List<zuobiao> kuaisushengchengVectorzuobiao(double... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数个数必须是偶数（成对的 x,y）");
        }
        List<zuobiao> vector = new ArrayList<>();
        for (int i = 0; i < pairs.length; i += 2) {
            vector.add(new zuobiao(pairs[i], pairs[i + 1]));
        }
        return vector;
    }
}