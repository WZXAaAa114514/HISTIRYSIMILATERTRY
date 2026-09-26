package neirong.bianliang.guojia.guojiashili.xiandai;

import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.bingpai.bingmoshuju;
import neirong.bianliang.chengshi.chengshi;
import neirong.bianliang.guojia.country;
import neirong.bianliang.zuobiao.zuobiao;

import javax.swing.*;
import java.awt.*;

import static neirong.gongju.bianliang.*;

public class china extends country {
    public china() {
        JButton btn=new JButton();
        btn.setPreferredSize(new Dimension(BUTTON_W, BUTTON_H));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setFocusable(false);
        btn.addActionListener(e ->
                JOptionPane.showMessageDialog(board, "点击了：" + "首都"));


        super(countrys.CHINA.get(), Color.red, new chengshi(new zuobiao(116.40,39.90),btn,"北京"));
        jundui.add(new BINGPAI((Image) null,10,"a",200.40,39.90,this,new bingmoshuju(300),Color.red));
        this.jundui.add(new BINGPAI((Image) null,10,"a",121.4737,39.90,this,new bingmoshuju(300),Color.red));

        this.ischuchang=true;
    }
}
