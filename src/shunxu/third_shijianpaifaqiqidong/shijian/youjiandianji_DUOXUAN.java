package shunxu.third_shijianpaifaqiqidong.shijian;

import neirong.bianliang.bingpai.BINGPAI;
import shunxu.third_shijianpaifaqiqidong.shijiankequxiao;

public class youjiandianji_DUOXUAN extends shijiankequxiao {
    public BINGPAI xuanding;
    public boolean shifouquxiao=false;
    public double gotox;
    public double gotoy;
    @Override
    public void quxiao(boolean canceled) {
        super.quxiao(canceled);
        shifouquxiao=true;
    }

    public youjiandianji_DUOXUAN(BINGPAI xuanding, double x, double y) {
        this.xuanding=xuanding;
        this.gotox=x;
        this.gotoy=y;
    }

    public BINGPAI getXUANDING(){
        return xuanding;
    }
}