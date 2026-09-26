package shunxu.third_shijianpaifaqiqidong.s;

import shijianjianting.bianliang.bingpai.BINGPAI;
import shunxu.third_shijianpaifaqiqidong.shijiankequxiao;

public class beixuanze_DUOXUAN extends shijiankequxiao {
    public BINGPAI xuanding;
    public boolean shifouquxiao=false;
    @Override
    public void quxiao(boolean canceled) {
        super.quxiao(canceled);
        shifouquxiao=true;
    }

    public beixuanze_DUOXUAN(BINGPAI xuanding) {
        this.xuanding=xuanding;
    }

    public BINGPAI getXUANDING(){
        return xuanding;
    }
}