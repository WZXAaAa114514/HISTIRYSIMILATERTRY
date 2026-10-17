package neirong.bianliang.guojia.guojiashili.jingdaichu;

import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.bingpai.bingmoshuju;
import neirong.bianliang.chengshi.chengshi;
import neirong.bianliang.guojia.country;
import neirong.bianliang.zuobiao.zuobiao;

import javax.swing.*;
import java.awt.*;

import static neirong.gongju.bianliang.*;

public class qing extends country {

    public qing() {
        super(countrys.QING.get(),
                Color.YELLOW,
                new chengshi(new zuobiao(116.40, 39.90), createBtn(), "北京"));

        jundui.add(new BINGPAI((Image) null, 10, "a", 200.40, 39.90, this,
                new bingmoshuju(300, 0.3), Color.yellow));

        this.ischuchang = true;
    }

    private static JButton createBtn() {
        JButton btn = new JButton();
        btn.setPreferredSize(new Dimension((int) BUTTON_W, Math.toIntExact(BUTTON_H)));
        btn.setContentAreaFilled(false);
        btn.setBorderPainted(false);
        btn.setFocusPainted(false);
        btn.setOpaque(false);
        btn.setFocusable(false);
        btn.addActionListener(e ->
                JOptionPane.showMessageDialog(board, "点击了：" + "首都"));
        return btn;
    }
}