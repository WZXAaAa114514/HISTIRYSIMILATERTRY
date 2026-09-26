package shunxu.third_shijianpaifaqiqidong;

public class shijiankequxiao implements kequxiao {
    private boolean canceled;
    @Override public void quxiao(boolean canceled) { this.canceled = canceled; }
    @Override public boolean isCanceled() { return canceled; }
}
