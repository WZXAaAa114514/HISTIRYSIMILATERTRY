package neirong.gongju;

import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.chengshi.chengshi;
import neirong.bianliang.guojia.country;
import neirong.bianliang.xuanding.xuanding;
import neirong.gongju.xuanranqi.PaintBoard;
import shijian.time;
import shunxu.fourth_huizhishijian.huizhi;
import shunxu.third_shijianpaifaqiqidong.EventBus;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class bianliang {
    public static List<country> countries=new ArrayList<>();
    public static JFrame gameframe = new JFrame("TRY");
    public static PaintBoard board = new PaintBoard();
    public static xuanding<BINGPAI> xuandingbianliang=  new xuanding<>();
    public static EventBus EVENTMAIN;
    public static huizhi TIME = new huizhi(new time(1936, 1, 1, 0, 0, 0));
    public static List<chengshi> chengshis=new ArrayList<>();
    public static final long BUTTON_W = 8;
    public static final long BUTTON_H = 6;
    public static country PLAYCOUNTRY;
    public static JPanel zhuyemian=new JPanel(new BorderLayout());
    public static JPanel diejiaceng = new JPanel(null) {{
        Dimension s = Toolkit.getDefaultToolkit().getScreenSize();
        setPreferredSize(s);
        setSize(s);
        setBounds(0, 0, s.width, s.height);
        setOpaque(false);   // 背景透明
    }};
}
