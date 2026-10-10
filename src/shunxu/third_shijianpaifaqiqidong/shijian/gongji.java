package shunxu.third_shijianpaifaqiqidong.shijian;

import neirong.bianliang.bingpai.BINGPAI;

import java.util.List;

public class gongji {

        private BINGPAI gongjizhe;
        private BINGPAI beigongjizhe;

        public gongji(BINGPAI gongjizhe,BINGPAI beigongjizhe) {

            this.gongjizhe=gongjizhe;
            this.beigongjizhe=beigongjizhe;
        }
        public BINGPAI getgongjizhe(){
            return gongjizhe;
        }
        public BINGPAI getbeigongjizhe(){
            return beigongjizhe;
        }


}
