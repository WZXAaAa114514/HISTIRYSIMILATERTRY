package shijian;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * 计时器（倒计时）
 *
 * 线程安全：所有对 targetTime 的读写都由 lock 保护，可以从任意线程调用。
 */
public class time {

    public record fengzhuangshijian(int year, int month, int day,
                                    int hour, int minute, int second) {
        @Override
        public String toString() {
            return String.format("%04d-%02d-%02d %02d:%02d:%02d",
                    year, month, day, hour, minute, second);
        }
    }

    public interface TickListener {
        void onTick(Duration remaining);
    }

    private final LocalDateTime initialTime;
    private final Object lock = new Object();
    private LocalDateTime targetTime;
    private boolean running;
    private ScheduledExecutorService scheduler;
    private ScheduledFuture<?> task;
    private volatile TickListener tickListener;
    private volatile Runnable finishListener;

    public time(int year, int month, int day,
                int hour, int minute, int second) {
        this(LocalDateTime.of(year, month, day, hour, minute, second));
    }

    public time(LocalDateTime targetTime) {
        if (targetTime == null) {
            throw new IllegalArgumentException("targetTime 不能为 null");
        }
        this.initialTime = targetTime;
        this.targetTime = targetTime;
    }

    /* ==================== 加时 ==================== */

    public time addOneSecond() {
        synchronized (lock) { targetTime = targetTime.plusSeconds(1); }
        return this;
    }

    public time addOneMinute() {
        synchronized (lock) { targetTime = targetTime.plusMinutes(1); }
        return this;
    }

    public time addOneHour() {
        synchronized (lock) { targetTime = targetTime.plusHours(1); }
        return this;
    }

    public time addOneDay() {
        synchronized (lock) { targetTime = targetTime.plusDays(1); }
        return this;
    }

    /**
     * ★ 批量推进 seconds 秒，替代多次 addOneSecond，避免循环和重复加锁。
     * seconds <= 0 时直接返回。
     */
    public time addSeconds(long seconds) {
        if (seconds <= 0L) return this;
        synchronized (lock) {
            targetTime = targetTime.plusSeconds(seconds);
        }
        return this;
    }

    /* ==================== 传出 ==================== */

    public fengzhuangshijian getDateTime() {
        LocalDateTime t;
        synchronized (lock) { t = targetTime; }
        return new fengzhuangshijian(
                t.getYear(), t.getMonthValue(), t.getDayOfMonth(),
                t.getHour(), t.getMinute(), t.getSecond());
    }

    public int getYear()   { synchronized (lock) { return targetTime.getYear(); } }
    public int getMonth()  { synchronized (lock) { return targetTime.getMonthValue(); } }
    public int getDay()    { synchronized (lock) { return targetTime.getDayOfMonth(); } }
    public int getHour()   { synchronized (lock) { return targetTime.getHour(); } }
    public int getMinute() { synchronized (lock) { return targetTime.getMinute(); } }
    public int getSecond() { synchronized (lock) { return targetTime.getSecond(); } }

    public LocalDateTime getTargetTime() {
        synchronized (lock) { return targetTime; }
    }

    /* ==================== 倒计时 ==================== */

    public Duration getRemaining() {
        LocalDateTime t;
        synchronized (lock) { t = targetTime; }
        return Duration.between(LocalDateTime.now(), t);
    }

    public long getRemainingSeconds() {
        long s = getRemaining().getSeconds();
        return Math.max(0, s);
    }

    public String getRemainingText() {
        long total = getRemainingSeconds();
        long d = total / 86400;
        long h = (total % 86400) / 3600;
        long m = (total % 3600) / 60;
        long s = total % 60;
        return String.format("%d天 %02d时 %02d分 %02d秒", d, h, m, s);
    }

    public void start() {
        synchronized (lock) {
            if (running) return;
            running = true;
            scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
                Thread t = new Thread(r, "countdown-timer");
                t.setDaemon(true);
                return t;
            });
            task = scheduler.scheduleAtFixedRate(this::tick, 0, 1, TimeUnit.SECONDS);
        }
    }

    public void stop() {
        synchronized (lock) {
            running = false;
            if (task != null) { task.cancel(false); task = null; }
            if (scheduler != null) { scheduler.shutdown(); scheduler = null; }
        }
    }

    public void reset() {
        stop();
        synchronized (lock) { targetTime = initialTime; }
    }

    public boolean isRunning() {
        synchronized (lock) { return running; }
    }

    public void setTickListener(TickListener listener) { this.tickListener = listener; }
    public void setFinishListener(Runnable listener)   { this.finishListener = listener; }

    private void tick() {
        Duration remaining = getRemaining();
        if (remaining.isNegative() || remaining.isZero()) {
            stop();
            TickListener tl = tickListener;
            if (tl != null) tl.onTick(Duration.ZERO);
            Runnable fl = finishListener;
            if (fl != null) fl.run();
        } else {
            TickListener tl = tickListener;
            if (tl != null) tl.onTick(remaining);
        }
    }
}