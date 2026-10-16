package caozuo;

import neirong.gongju.bianliang;
import neirong.gongju.yemian.danru;

import javax.swing.*;
import java.awt.*;

public class bing_caozuo extends JPanel  {
    private JButton jButton1;
    private JButton jButton2;
    public bing_caozuo(){
        setLayout(null);
        setPreferredSize(new Dimension(200, 174));

        jButton1 = new JButton();
        jButton1.setText("Button");
        jButton1.setIcon(new ImageIcon("C:\\Users\\AD\\Desktop\\Hearts of Iron IV\\tut_supply_mapmode_img.png"));
        jButton1.setIconTextGap(6);
        jButton1.setBounds(0, 0, 199, 150);
        add(jButton1);

        jButton2 = new JButton();
        jButton2.setText("Button");
        jButton2.setBounds(19, 144, 164, 30);
        add(jButton2);
    }
    public void kai(){
        danru.danru(this, bianliang.diejiaceng);
    }
}
