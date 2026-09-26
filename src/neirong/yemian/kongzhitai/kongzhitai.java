package neirong.yemian.kongzhitai;

import neirong.yemian.ketuodongjilei;
import shunxu.third_shijianpaifaqiqidong.shijian.kongzhitaifasong;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import static neirong.gongju.bianliang.*;

// 由可视化设计器生成的 JPanel 组件，可直接添加到任意容器中使用。
public class kongzhitai extends ketuodongjilei {

    private JTextArea textArea1;
    private JButton button1;
    private JTextField textField1;

    public kongzhitai() {
        initComponents();
        board.revalidate();
        board.repaint();
    }

    private void initComponents() {
        setLayout(null);
        setPreferredSize(new Dimension(286, 318));
        setBackground(new Color(238, 238, 238));

        textArea1 = new JTextArea("", 5, 20);
        textArea1.setLineWrap(true);
        textArea1.setWrapStyleWord(true);
        textArea1.setBounds(25, 10, 241, 252);
        add(textArea1);

        button1 = new JButton("发送");
        button1.setBackground(new Color(255, 213, 0));
        button1.setFont(new Font("SansSerif", Font.BOLD, 12));
        button1.setForeground(new Color(0, 0, 0));
        button1.setBounds(200, 270, 63, 32);
        add(button1);

        textField1 = new JTextField("", 20);
        textField1.setToolTipText("请输入指令");
        textField1.setBounds(25, 275, 172, 28);
        add(textField1);


    }
}