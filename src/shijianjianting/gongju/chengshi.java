package shijianjianting.gongju;

import javax.swing.*;
import java.util.Vector;

public class chengshi {
    public zuobiao zuobiao;

    public JButton anniu;
    public String name;
    public chengshi(zuobiao zuobiao,JButton anniu){
        this.zuobiao=zuobiao;
        this.anniu=anniu;
        this.name=anniu.getText();
    }
    public chengshi(zuobiao zuobiao,JButton anniu,String N){
        this.zuobiao=zuobiao;
        this.anniu=anniu;
        this.name=N;
    }
    public static Vector<chengshi> kuaisuchuangjianchengshi_VECTOR(Vector<zuobiao> zuobiaos, JButton anniu, Vector<String> name){
        Vector<chengshi> a=new Vector<>();
        int a1=0;
        for(shijianjianting.gongju.zuobiao as:zuobiaos){

            chengshi chengshi=new chengshi(as,anniu,name.get(a1));
            a.add(chengshi);
            a1++;
        }
        return a;
    }
    public static Vector<chengshi> kuaisushengchengVectorchengshi_WUVECTOR(
            double[] xs, double[] ys, String[] names,JButton a) {
        if (xs.length != ys.length || xs.length != names.length) {
            throw new IllegalArgumentException("三个数组长度必须一致");
        }
        Vector<chengshi> vector = new Vector<>();

        for (int i = 0; i < xs.length; i++) {
            vector.add(new chengshi(new zuobiao(xs[i], ys[i]),a,names[i]));

        }
        return vector;
    }
}
