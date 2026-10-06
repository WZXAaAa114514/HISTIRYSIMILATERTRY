package neirong.yemian.kongzhitai;

import neirong.gongju.bianliang;
import neirong.yemian.ketuodongjilei;
import shunxu.third_shijianpaifaqiqidong.shijian.kongzhitaifasong;

import javax.swing.*;
import java.awt.*;

import static neirong.gongju.bianliang.*;

// 由可视化设计器生成的 JPanel 组件，可直接添加到任意容器中使用。
public class kongzhitai extends ketuodongjilei {

    private static kongzhitai instance = null;

    private JTextArea textArea1;
    private JButton button1;
    private JTextField textField1;

    public kongzhitai() {
        initComponents();
        setBounds(100, 100, 286, 318);
    }

    /**
     * 切换控制台的显示 / 隐藏。
     * 第一次调用：创建面板并加到 frame 的 layeredPane 顶层，然后显示。
     * 之后每次调用：在显示 / 隐藏之间切换，显示时保证在最上层。
     */
    public static void kai() {
        JFrame frame = gameframe;
        if (frame == null) {
            return;
        }

        if (instance == null) {
            // 第一次：创建并加到 layeredPane 的高层
            instance = new kongzhitai();

            JLayeredPane layeredPane = frame.getLayeredPane();
            instance.setBounds(100, 100, 286, 318);
            layeredPane.add(instance, JLayeredPane.PALETTE_LAYER);

            instance.setVisible(true);

            // 保证在最顶
            layeredPane.setComponentZOrder(instance, 0);
        } else {
            // 之后每次：切换显示 / 隐藏
            boolean show = !instance.isVisible();
            instance.setVisible(show);

            if (show) {
                // 重新显示时把它压回最顶，防止被别的组件盖住
                Container parent = instance.getParent();
                if (parent != null) {
                    parent.setComponentZOrder(instance, 0);
                }
            }
        }

        // 通知 layeredPane 重新布局并重绘
        frame.getLayeredPane().revalidate();

    }

    public static kongzhitai getInstance() {
        return instance;
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
        button1.addActionListener(e -> {
            kongzhitaifasong kongzhitaifasong = new kongzhitaifasong(textField1.getText());
            EVENTMAIN.post(kongzhitaifasong);
            textField1.setText("");
            textArea1.setText(textArea1.getText() + kongzhitaifasong.returnwenzi + "\n");
        });
        add(button1);

        textField1 = new JTextField("", 20);
        textField1.setToolTipText("请输入指令");
        textField1.setBounds(25, 275, 172, 28);
        add(textField1);
    }
}