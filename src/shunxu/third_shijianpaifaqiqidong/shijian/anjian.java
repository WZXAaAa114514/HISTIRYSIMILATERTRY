package shunxu.third_shijianpaifaqiqidong.shijian;

import shunxu.third_shijianpaifaqiqidong.shijiankequxiao;

import java.util.List;
import java.util.Vector;

public class anjian extends shijiankequxiao {
    private List<String> anjian;

    public anjian(List<String> anjian) {
this.anjian=anjian;
    }
    public List<String> getANJIAN(){
        return anjian;
    }

}