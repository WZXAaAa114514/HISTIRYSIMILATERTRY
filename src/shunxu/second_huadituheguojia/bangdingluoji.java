package shunxu.second_huadituheguojia;

import neirong.bianliang.chengshi.chengshi;
import neirong.bianliang.guojia.country;

import static neirong.gongju.bianliang.*;

public class bangdingluoji {
    public static void bachuadedongxigaoshanghuaban() {
        board.beginBatch();

        for (country c : countries) {
            c.shengchengguojia();
        }
        for (chengshi cs:chengshis){


            cs.bianhaoTEXT   = board.addAutoText(cs, 3, cs.anniu);
            cs.bianhaoBUTTOM = board.addAutoComponent(cs.anniu, cs);
            cs.anniu.addActionListener(e -> {


            });
            // 可选：若希望 setter 立刻重绘（不依赖定时器）：
            // cs.bind(board::);

        }

        board.endBatch();


    }
}
