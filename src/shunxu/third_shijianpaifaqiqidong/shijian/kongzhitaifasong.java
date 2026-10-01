package shunxu.third_shijianpaifaqiqidong.shijian;

import neirong.bianliang.bingpai.BINGPAI;
import shunxu.third_shijianpaifaqiqidong.shijiankequxiao;

public class kongzhitaifasong extends shijiankequxiao {
    public String[] zhiling;
    public String returnwenzi;
    public kongzhitaifasong(String zhiling) {
        this.zhiling=zhiling.split("\\s+");;

    }
    public void setReturnwenzi(String a){
        returnwenzi=a;
    }
    public String[] getzhiling(){
        return zhiling;
    }
    public String getReturnwenzi(){
        return returnwenzi;
    }
}