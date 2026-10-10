package neirong.gongju;

import neirong.bianliang.chengshi.chengshi;
import neirong.bianliang.guojia.country;
import neirong.bianliang.xuanding.xuanding;
import neirong.gongju.xuanranqi.PaintBoard;
import shunxu.third_shijianpaifaqiqidong.EventBus;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

public class bianliang {
    public static List<country> countries=new ArrayList<>();
    public static JFrame gameframe = new JFrame("TRY");
    public static PaintBoard board = new PaintBoard();
    public static xuanding<Object> xuandingbianliang=  new xuanding<>();
    public static EventBus EVENTMAIN;
    public static List<chengshi> chengshis=new ArrayList<>();
    public static final int BUTTON_W = 8;
    public static final int BUTTON_H = 6;
    public static country PLAYCOUNTRY;
    public static JPanel zhuyemian=new JPanel(new BorderLayout());
}
