package shunxu.third_shijianpaifaqiqidong.shijian;

import neirong.bianliang.bingpai.BINGPAI;
import shunxu.third_shijianpaifaqiqidong.shijiankequxiao;

public class youjiankuangxuan extends shijiankequxiao {
    public BINGPAI xuanding;
    public boolean shifouquxiao=false;

    @Override
    public void quxiao(boolean canceled) {
        super.quxiao(canceled);
        shifouquxiao=true;
    }

    public youjiankuangxuan(BINGPAI xuanding) {
        this.xuanding=xuanding;

    }

    public BINGPAI getXUANDING(){
        return xuanding;
    }
}