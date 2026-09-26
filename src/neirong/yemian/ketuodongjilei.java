package neirong.yemian;

import javax.swing.*;
import java.awt.*;
import java.awt.event.*;

/**
 * 可拖动的 JPanel 基类。
 * 继承它的面板，只要父容器使用 null 布局，就可以用鼠标左键拖动。
 */
public class ketuodongjilei extends JPanel {

    // 鼠标按下时的屏幕坐标
    private Point pressedScreenPoint;
    // 鼠标按下时，面板在父容器中的位置
    private Point startLocation;

    public ketuodongjilei() {
        super();
        initDrag();
    }

    public ketuodongjilei(LayoutManager layout) {
        super(layout);
        initDrag();
    }

    private void initDrag() {
        MouseAdapter adapter = new MouseAdapter() {
            @Override
            public void mousePressed(MouseEvent e) {
                // 只响应鼠标左键
                if (SwingUtilities.isLeftMouseButton(e)) {
                    // 记录鼠标在屏幕上的绝对位置
                    pressedScreenPoint = e.getLocationOnScreen();
                    // 记录面板当前的位置（相对于父容器）
                    startLocation = getLocation();
                }
            }

            @Override
            public void mouseDragged(MouseEvent e) {
                if (pressedScreenPoint != null && startLocation != null) {
                    // 当前鼠标屏幕位置
                    Point currentScreenPoint = e.getLocationOnScreen();
                    // 计算鼠标位移
                    int dx = currentScreenPoint.x - pressedScreenPoint.x;
                    int dy = currentScreenPoint.y - pressedScreenPoint.y;
                    // 新位置 = 起始位置 + 位移
                    int newX = startLocation.x + dx;
                    int newY = startLocation.y + dy;
                    // 移动面板（需要父容器使用 null 布局）
                    setLocation(newX, newY);
                }
            }

            @Override
            public void mouseReleased(MouseEvent e) {
                // 清空状态，避免影响下次拖动
                pressedScreenPoint = null;
                startLocation = null;
            }
        };

        // 同时监听鼠标按键和鼠标拖动
        addMouseListener(adapter);
        addMouseMotionListener(adapter);
    }
}