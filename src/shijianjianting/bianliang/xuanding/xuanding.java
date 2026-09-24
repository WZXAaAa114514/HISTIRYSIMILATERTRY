package shijianjianting.bianliang.xuanding;

import shijianjianting.bianliang.zuobiao.zuobiao;
import shijianjianting.gongju.bianliang;

import java.util.Arrays;
import java.util.Objects;
import java.util.Vector;

public class xuanding<T> {
    public Vector<T> xuanding;
    public Object leixing;
    public xuanding(Vector<T> xuanding){
        this.xuanding=xuanding;

    }
    public xuanding(T... pairs){

        Vector<T> vector = new Vector<>();
        vector.addAll(Arrays.asList(pairs));
        this.xuanding=vector;
    }
    public static void quxiaokuangxuan(){
        bianliang.xuandingbianliang=null;
    }
    public void add(T a){

        xuanding.add( a);
    }
    public void jianshao(T a){

                xuanding.remove(a);


    }
    public static void xuanze(xuanding a){
        bianliang.xuandingbianliang=a;
    }
}
