package shijianjianting.gongju;

import shijianjianting.bianliang.bingpai.BINGPAI;
import shijianjianting.bianliang.chengshi.chengshi;
import shijianjianting.bianliang.guojia.country;
import shijianjianting.bianliang.xuanding.xuanding;
import shunxu.third_shijianpaifaqiqidong.EventBus;

import javax.swing.*;
import java.util.Vector;

public class bianliang {
    public static Vector<country> countries=new Vector<>();
    public static JFrame gameframe = new JFrame("TRY");
    public static PaintBoard board = new PaintBoard();
    public static xuanding<Object> xuandingbianliang=  new xuanding<>();
    public static EventBus EVENTMAIN;
    public static Vector<chengshi> chengshis=new Vector<>();
    public static final int BUTTON_W = 8;
    public static final int BUTTON_H = 6;
}
