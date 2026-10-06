package neirong.yemian.gongji;

import neirong.yemian.ketuodongjilei;

import javax.swing.*;
import java.awt.*;

import static neirong.gongju.bianliang.board;
import static neirong.gongju.bianliang.gameframe;

// 由可视化设计器生成的 JPanel 组件，可直接添加到任意容器中使用。
public class gongjikuang extends ketuodongjilei {

    private JLabel jinggongfang;
    private JLabel fangshoufang;
    private JSeparator separator1;
    private JProgressBar jingdutiao;

    public gongjikuang() {
        initComponents();
        board.revalidate();
        
    }

    private void initComponents() {
        setLayout(null);
        setPreferredSize(new Dimension(400, 500));
        setBackground(new Color(238, 238, 238));

        jinggongfang = new JLabel("标签");
        jinggongfang.setBounds(10, 45, 374, 43);
        add(jinggongfang);

        fangshoufang = new JLabel("标签");
        fangshoufang.setBounds(5, 140, 374, 43);
        add(fangshoufang);

        separator1 = new JSeparator();
        separator1.setBounds(0, 110, 390, 8);
        add(separator1);

        jingdutiao = new JProgressBar(0, 100);
        jingdutiao.setBounds(115, 435, 170, 22);
        add(jingdutiao);

    }
}