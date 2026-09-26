package neirong.bianliang.xuanding;

import neirong.bianliang.bingpai.BINGPAI;
import shunxu.third_shijianpaifaqiqidong.shijian.beiquxiao_DUOXUAN;

import java.util.ArrayList;
import java.util.List;
import java.util.Vector;

import static neirong.gongju.bianliang.EVENTMAIN;

/**
 * 支持"同类多选"的选择容器：
 *  - 空的时候，可以放任意类型
 *  - 一旦放入了第一个对象，就锁死类型，之后只能放同类
 *  - 全部移除后自动解锁，可重新选其他类型
 *  - 同一个对象（按 equals 判断）不会重复添加
 */
public class xuanding<T> {

    /** 内部存储（原名 xuanding 会和类名冲突，改个名字） */
    public List<T> neirong = new ArrayList<>();

    /** 当前锁定的类型；null 表示未锁定 */
    private Class<?> lockedType = null;

    public xuanding() {}

    @SafeVarargs
    public xuanding(T... pairs) {
        for (T p : pairs) add(p);
    }

    // ============================================================
    //  添加：空则锁类型，非空则校验类型；重复对象会被拒绝
    // ============================================================
    public boolean add(T a) {
        if (a == null) return false;

        // 1. 判重：已经存在就不添加
        if (contains(a)) {
            System.out.println("已存在，忽略：" + a);
            return false;
        }

        // 2. 类型校验
        if (lockedType == null) {
            // 空 → 锁定为当前对象的类型
            lockedType = a.getClass();
        } else if (!lockedType.isInstance(a)) {
            // 类型不符 → 拒绝
            System.out.println("类型不符，已拒绝：" + a.getClass().getSimpleName()
                    + "，当前只能选：" + lockedType.getSimpleName());
            return false;
        }

        return neirong.add(a);
    }

    // ============================================================
    //  判重：支持 null 安全比较
    // ============================================================
    public boolean contains(T a) {
        for (T t : neirong) {
            if (t == a) return true;                 // 同一引用直接算重复
            if (t != null && t.equals(a)) return true; // 按 equals 判断
        }
        return false;
    }

    // ============================================================
    //  移除：移除后如果空了，自动解锁
    // ============================================================
    public boolean jianshao(T a) {
        boolean removed = neirong.remove(a);
        if (neirong.isEmpty()) {
            lockedType = null;   // 解锁
        }
        return removed;
    }

    // ============================================================
    //  清空：等同于取消选择
    // ============================================================
    public void quxiao() {
        neirong.clear();
        lockedType = null;
    }

    public boolean isEmpty() {
        return neirong.isEmpty();
    }

    public Class<?> getLeixing() {
        return lockedType;
    }

    public List<T> getNeirong() {
        return neirong;
    }
    public void clearall(){
        for(T a:neirong){
            EVENTMAIN.post(new beiquxiao_DUOXUAN((BINGPAI) a));
        }
        neirong.clear();
    }
}