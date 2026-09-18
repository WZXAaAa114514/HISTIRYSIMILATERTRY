package shijianjianting.shijian;

import shijianjianting.bianliang.guojia.country;
import shijianjianting.gongju.chengshi;
import shijianjianting.gongju.zuobiao;
import shunxu.first_daoruguojia.jianting_daoruguojia;

import javax.swing.*;
import java.awt.*;
import java.util.Vector;

import static shijianjianting.gongju.bianliang.board;

public class shijian {

    // ==================== 中国主要城市数据 ====================
    // 顺序：直辖市 → 省会/首府 → 特别行政区/台湾 → 主要地级市
    private static final String[] CITY_NAMES = {
            // ---- 直辖市 ----
            "北京", "上海", "天津", "重庆",
            // ---- 省会 / 首府 ----
            "石家庄", "太原", "呼和浩特", "沈阳", "长春", "哈尔滨",
            "南京", "杭州", "合肥", "福州", "南昌", "济南",
            "郑州", "武汉", "长沙", "广州", "南宁", "海口",
            "成都", "贵阳", "昆明", "拉萨", "西安", "兰州",
            "西宁", "银川", "乌鲁木齐",
            // ---- 特别行政区 / 台湾 ----
            "香港", "澳门", "台北",
            // ---- 主要地级市 ----
            "深圳", "厦门", "青岛", "大连", "宁波", "苏州", "无锡", "温州",
            "佛山", "东莞", "珠海", "三亚", "桂林", "洛阳", "徐州", "常州",
            "南通", "烟台", "唐山", "保定", "秦皇岛", "包头", "吉林", "扬州",
            "绍兴", "嘉兴", "金华", "芜湖", "泉州", "漳州", "九江", "赣州",
            "淄博", "潍坊", "济宁", "泰安", "威海", "临沂", "开封", "南阳",
            "宜昌", "襄阳", "株洲", "湘潭", "衡阳", "岳阳", "常德", "韶关",
            "汕头", "湛江", "中山", "柳州", "北海", "绵阳", "宜宾", "遵义",
            "丽江", "宝鸡", "延安", "天水", "喀什", "伊宁", "高雄", "台中",
            "那曲", "日喀则"
    };

    // 经度（x）
    private static final double[] CITY_LONS = {
            116.4074, 121.4737, 117.2008, 106.5516,
            114.5149, 112.5489, 111.7519, 123.4315, 125.3245, 126.6425,
            118.7969, 120.1551, 117.2272, 119.2965, 115.8581, 117.1205,
            113.6254, 114.3055, 112.9388, 113.2644, 108.3665, 110.1985,
            104.0658, 106.6302, 102.8329,  91.1409, 108.9398, 103.8343,
            101.7782, 106.2309,  87.6168,
            114.1733, 113.5439, 121.5654,
            114.0579, 118.0894, 120.3826, 121.6147, 121.5504, 120.5954, 120.3119, 120.6994,
            113.1224, 113.7518, 113.5768, 109.5082, 110.2901, 112.4540, 117.1848, 119.9740,
            120.8943, 121.4479, 118.1802, 115.4646, 119.6005, 109.8403, 126.5496, 119.4215,
            120.5820, 120.7555, 119.6495, 118.3764, 118.5896, 117.6472, 116.0018, 114.9405,
            118.0476, 119.1616, 116.5871, 117.0870, 122.1204, 118.3563, 114.3070, 112.5285,
            111.2865, 112.1448, 113.1341, 112.9440, 112.5718, 113.1287, 111.6915, 113.5915,
            116.6820, 110.3592, 113.3927, 109.4281, 109.1200, 104.6796, 104.6308, 106.9309,
            100.2330, 107.1444, 109.4898, 105.7249,  75.9897,  81.3284, 120.3014, 120.6736,
            92.0513,  88.8851
    };

    // 纬度（y）
    private static final double[] CITY_LATS = {
            39.9042,  31.2304,  39.0842,  29.5630,
            38.0428,  37.8706,  40.8414,  41.8057,  43.8868,  45.7567,
            32.0603,  30.2741,  31.8206,  26.0745,  28.6820,  36.6510,
            34.7466,  30.5928,  28.2282,  23.1291,  22.8170,  20.0444,
            30.6598,  26.6477,  24.8801,  29.6456,  34.3416,  36.0611,
            36.6171,  38.4872,  43.8256,
            22.3193,  22.1987,  25.0330,
            22.5431,  24.4798,  36.0671,  38.9140,  29.8683,  31.2989,  31.4912,  27.9944,
            23.0288,  23.0207,  22.2707,  18.2479,  25.2736,  34.6197,  34.2618,  31.8112,
            31.9802,  37.4638,  39.6303,  38.8739,  39.9354,  40.6574,  43.8378,  32.3931,
            29.9973,  30.7464,  29.0895,  31.3263,  24.9042,  24.5128,  29.7050,  25.8510,
            36.8147,  36.7068,  35.4147,  36.1936,  37.5134,  35.1045,  34.7970,  32.9907,
            30.6918,  32.0425,  27.8274,  27.8297,  26.8942,  29.3565,  29.0402,  24.8013,
            23.3538,  21.2749,  22.5170,  24.3264,  21.4813,  31.4676,  28.7603,  27.7062,
            26.8721,  34.3696,  36.5853,  34.5787,  39.4677,  43.9169,  22.6273,  24.1477,
            31.4776,  29.2678
    };

    // ==================== 每个城市按钮的点击区域大小（隐形） ====================
    private static final int BUTTON_W = 8;
    private static final int BUTTON_H = 6;

    // ==================== 注册 ====================
    @jianting_daoruguojia
    public static Vector<country> as(Vector<country> a) {

        // 数据一致性校验
        if (CITY_NAMES.length != CITY_LONS.length
                || CITY_NAMES.length != CITY_LATS.length) {
            throw new IllegalStateException(
                    "城市数据长度不一致: names=" + CITY_NAMES.length
                            + ", lons=" + CITY_LONS.length
                            + ", lats=" + CITY_LATS.length);
        }

        // ★ 按 country 构造签名，分别准备两个 Vector<zuobiao> / Vector<chengshi>
        Vector<zuobiao> chengshiWeizhi = new Vector<>(CITY_NAMES.length);
        Vector<chengshi> chengshiList  = new Vector<>(CITY_NAMES.length);

        for (int i = 0; i < CITY_NAMES.length; i++) {
            final String cityName = CITY_NAMES[i];
            final double lon = CITY_LONS[i];
            final double lat = CITY_LATS[i];

            // 1) 经纬度对象（顺便放进"城市位置"Vector）
            zuobiao zb = new zuobiao(lon, lat);
            chengshiWeizhi.add(zb);

            // 2) 每个城市一个独立按钮 —— 隐形，只作为点击目标
            //    （不能用 WUVECTOR，它只接受同一个 JButton，所有城市会共享，点击弹不出各自名字）
            JButton btn = new JButton();
            btn.setPreferredSize(new Dimension(BUTTON_W, BUTTON_H));
            btn.setContentAreaFilled(false);
            btn.setBorderPainted(false);
            btn.setFocusPainted(false);
            btn.setOpaque(false);
            btn.setFocusable(false);
            btn.addActionListener(e ->
                    JOptionPane.showMessageDialog(board, "点击了：" + cityName));

            // 3) chengshi 对象（name 用于绘制文字，anniu 用于点击）
            chengshiList.add(new chengshi(zb, btn, cityName));
        }

        // 4) 组装 country（4 参构造：国土点 / 城市位置 / 城市列表 / 颜色）
        a.add(new country(
                country.countrys.CHINA.get(),   // 中国国土面点
                chengshiWeizhi,                 // Vector<zuobiao>
                chengshiList,                   // Vector<chengshi>
                Color.RED));

        return a;
    }
}