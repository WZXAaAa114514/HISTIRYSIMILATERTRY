package neirong.gongju.zhujie;



/**
 * 让数据对象持有"我变了"的回调。
 * 不想用定时轮询时，继承它并在修改字段后调用 fireChanged()。
 */
public abstract class LiveObject {

    private volatile Runnable onChange;

    public final void bind(Runnable onChange) {
        this.onChange = onChange;
    }

    /** 子类每次修改字段后调用它，画面立即刷新。 */
    public final void fireChanged() {
        Runnable r = onChange;
        if (r != null) r.run();
    }
}
