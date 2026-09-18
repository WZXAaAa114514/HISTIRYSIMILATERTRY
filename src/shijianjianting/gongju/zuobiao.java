package shijianjianting.gongju;

import java.util.Vector;

public class zuobiao {
    public double x,y;
    public zuobiao(double x,double y){
        this.x=x;
        this.y=y;
    }

    public static Vector<zuobiao> kuaisushengchengVectorzuobiao(double... pairs) {
        if (pairs.length % 2 != 0) {
            throw new IllegalArgumentException("参数个数必须是偶数（成对的 x,y）");
        }
        Vector<zuobiao> vector = new Vector<>();
        for (int i = 0; i < pairs.length; i += 2) {
            vector.add(new zuobiao(pairs[i], pairs[i + 1]));
        }
        return vector;
    }
}
