package neirong.shijian;

import neirong.bianliang.bingpai.bingmoshuju;
import neirong.gongju.pingmujilei.XYBoard;
import neirong.gongju.bianliang;
import neirong.gongju.gongju;
import neirong.gongju.pingmujilei.tuodongkuang;
import neirong.gongju.xuanranqi.PaintBoard;
import neirong.yemian.kongzhitai.kongzhitai;
import shijian.shijian.OnTimeChange;
import neirong.bianliang.bingpai.BINGPAI;
import neirong.bianliang.guojia.country;
import neirong.bianliang.zuobiao.zuobiao;
import shunxu.third_shijianpaifaqiqidong.SubscribeEvent;
import shunxu.third_shijianpaifaqiqidong.shijian.*;

import java.awt.*;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.*;

import static neirong.gongju.bianliang.*;

public class MyListener {
    @SubscribeEvent
    public void onJoin(beixuanze_DUOXUAN event) {

        event.xuanding.suoshulujingzhixianbianhao = board.addLiveLine(
                event.xuanding.jingdu, event.xuanding.weidu,
                event.xuanding.renwumubiao.x, event.xuanding.renwumubiao.y);
        event.xuanding.xianshilansebiankuang();
    }

    @SubscribeEvent
    public void quxiao(beiquxiao_DUOXUAN event) {

        board.removeLiveLine(event.xuanding.suoshulujingzhixianbianhao);
        event.xuanding.quxiaolansebiankuang();
    }

    @SubscribeEvent
    public void onJoin_TUOXUAN(youjiankuangxuan event) {
        xuandingbianliang.add(event.xuanding);

        event.xuanding.suoshulujingzhixianbianhao = board.addLiveLine(
                event.xuanding.jingdu, event.xuanding.weidu,
                event.xuanding.renwumubiao.x, event.xuanding.renwumubiao.y);
        event.xuanding.xianshilansebiankuang();
    }

    int jishu = 0;

    @OnTimeChange
    public void yizhen(Duration shichang) throws InterruptedException {

        // 1. 创建固定大小的线程池
        ExecutorService xianchengchi = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 1000
        );

        // 2. 为每个元素创建 Callable 任务
        List<Callable<Void>> renwuLie = new ArrayList<>();
        for (country guojiaXiang : countries) {
            for (BINGPAI bingpai : guojiaXiang.jundui) {
                renwuLie.add(() -> {
                    for (int a1 = 0; a1 != shichang.getSeconds(); a1++) {
                        bingpai.tick();
                    }
                    return null;
                });
            }
        }

        // 3. 提交所有任务，并等待全部完成
        List<Future<Void>> jieguoLie = xianchengchi.invokeAll(renwuLie);

        // 4. 检查是否有任务抛出异常
        for (Future<Void> weiLai : jieguoLie) {
            try {
                weiLai.get();
            } catch (ExecutionException yichang) {
                yichang.printStackTrace();
            }
        }

        // 5. 关闭线程池
        xianchengchi.shutdown();

        jishu += shichang.getSeconds();
        countries.get(0).shoudu.name = String.valueOf(jishu);
    }

    @SubscribeEvent
    public void youjianyidong(youjiandianji_DUOXUAN event) {
        event.xuanding.goto_(new zuobiao(event.gotox, event.gotoy));

        board.removeLiveLine(event.xuanding.suoshulujingzhixianbianhao);

        event.xuanding.suoshulujingzhixianbianhao = board.addLiveLine(
                event.xuanding.jingdu, event.xuanding.weidu,
                event.xuanding.renwumubiao.x, event.xuanding.renwumubiao.y);
    }

    @SubscribeEvent
    public void kongzhitaijianting(kongzhitaifasong neirong) {

        if (Objects.equals(neirong.zhiling[0], "summon")) {

            PaintBoard.shubiaojingweidu kuaizhao = board.dangqianshubiaojingweidu;
            if (board.dangqianshubiaojingweidu == null) {
                BINGPAI bingpai = new BINGPAI((Image) null, 10, "a", 0, 0,
                        bianliang.countries.get(0), new bingmoshuju(300, 0.1), Color.yellow);

                bianliang.countries.get(0).jundui.add(bingpai);
                bingpai.xianshi();
                neirong.returnwenzi = "0";
            }
        } else if (Objects.equals(neirong.zhiling[0], "国策页面")) {
            XYBoard sub = new XYBoard(320, 220);
            // 可选：画点东西，不然就是纯黑一块
            sub.setBackground(new Color(30, 30, 36));
            sub.setWorldPainter((g2, b) -> {
                g2.setColor(new Color(200, 200, 200));
                double[] p = b.worldToScreen(0, 0);
                g2.fillOval((int) p[0] - 5, (int) p[1] - 5, 10, 10);
            });
            sub.setBounds(40, 40, 320, 220);
            gongju.tanchuyemian(new tuodongkuang(sub),10,10);        // 默认 BorderLayout.CENTER，会占满




// 挂到北京
     //       gongju.tanchuyemian(sub);
            neirong.returnwenzi = "0";
        }
    }

    @SubscribeEvent
    public void kongzhitai(anjian anjian) {

        if (anjian.getANJIAN().contains("后引号") && anjian.getANJIAN().contains("Shift")) {

            kongzhitai.kai();
        }
    }
    @SubscribeEvent
    public void jiantinggongji(gongji gongji){
        BINGPAI a=gongji.getgongjizhe(),b=gongji.getbeigongjizhe();
        b.shuju.gongjizhe.add(a);
        b.shuju.gongjizhe.add(a);
        a.kapianyanse = Color.CYAN;
        a.addNumber(-1);
        b.kapianyanse = Color.BLACK;
        b.addNumber(-1);
    }
}