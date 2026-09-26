package shijianjianting.shijian;

import shijian.shijian.OnTimeChange;
import shijianjianting.bianliang.bingpai.BINGPAI;
import shijianjianting.bianliang.guojia.country;
import shijianjianting.bianliang.zuobiao.zuobiao;
import shijianjianting.gongju.bianliang;
import shunxu.third_shijianpaifaqiqidong.SubscribeEvent;
import shunxu.third_shijianpaifaqiqidong.s.beiquxiao_DUOXUAN;
import shunxu.third_shijianpaifaqiqidong.s.beixuanze_DUOXUAN;
import shunxu.third_shijianpaifaqiqidong.s.youjiandianji_DUOXUAN;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Vector;
import java.util.concurrent.*;

import static shijianjianting.gongju.bianliang.board;
import static shijianjianting.gongju.bianliang.countries;

public class MyListener {
    @SubscribeEvent
    public void onJoin(beixuanze_DUOXUAN event) {

        event.xuanding.suoshulujingzhixianbianhao = board.addLiveLine(event.xuanding.x,event.xuanding.y, event.xuanding.tasktogo.x,event.xuanding.tasktogo.y);
        event.xuanding.xianshilansebiankuang();
    }
    @SubscribeEvent
    public void quxiao(beiquxiao_DUOXUAN event) {

        board.removeLiveLine(event.xuanding.suoshulujingzhixianbianhao);
        event.xuanding.quxiaolansebiankuang();
    }
    int g=0;
    @OnTimeChange
    public void yizhen(Duration delta) throws InterruptedException {
        g+=delta.getSeconds();
        countries.get(0).shoudu.name= String.valueOf(g);

        // 假设 vector 里有很多元素
        // ...

        // 1. 创建固定大小的线程池（大小根据 CPU 核数或 IO 密集程度调整）
        ExecutorService executor = Executors.newFixedThreadPool(
                Runtime.getRuntime().availableProcessors() * 2
        );

        // 2. 为每个元素创建 Callable 任务
        List<Callable<Void>> tasks = new ArrayList<>();
        for (country item : countries) {
            for (BINGPAI binpai:item.jundui) {
                tasks.add(() -> {
                    // 这里做耗时的处理，每个元素独立
                    for(int a1=0;a1!=delta.getSeconds();a1++) {
                        binpai.goto_MEIYIZHENZHIXING();
                    }
                    return null;
                });
            }
        }

        // 3. 提交所有任务，并等待全部完成（invokeAll 会阻塞直到所有任务结束）
        List<Future<Void>> futures = executor.invokeAll(tasks);

        // 4. 可选：检查是否有任务抛出异常
        for (Future<Void> f : futures) {
            try {
                f.get(); // 如果任务有异常，这里会抛出 ExecutionException
            } catch (ExecutionException e) {
                e.printStackTrace();
            }
        }

        // 5. 关闭线程池
        executor.shutdown();
        System.out.println("所有处理完成，继续后续逻辑");
    }
    @SubscribeEvent
    public void youjianyidong(youjiandianji_DUOXUAN event) {
        event.xuanding.goto_(new zuobiao(event.gotox,event.gotoy));

        board.removeLiveLine(event.xuanding.suoshulujingzhixianbianhao);

        event.xuanding.suoshulujingzhixianbianhao = board.addLiveLine(event.xuanding.x,event.xuanding.y, event.xuanding.tasktogo.x,event.xuanding.tasktogo.y);



    }
}