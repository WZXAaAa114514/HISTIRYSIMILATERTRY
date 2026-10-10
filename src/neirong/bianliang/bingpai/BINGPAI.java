package neirong.bianliang.bingpai;

import neirong.bianliang.guojia.country;
import neirong.bianliang.xuanding.canchosemany;
import neirong.bianliang.zuobiao.zuobiao;
import neirong.gongju.gongju;
import neirong.gongju.xuanranqi.PaintBoard;
import neirong.gongju.zhujie.Live;
import shunxu.third_shijianpaifaqiqidong.shijian.beixuanze_DUOXUAN;
import shunxu.third_shijianpaifaqiqidong.shijian.gongji;

import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static neirong.gongju.bianliang.*;

public class BINGPAI extends JButton implements canchosemany {

    /* ==================== 设计基准（5:2 宽高比） ==================== */
    private static final int JICHU_KUAN = 100;
    private static final int JICHU_GAO = 40;

    /* ==================== 默认配色 ==================== */
    private static final Color MOREN_KAPIANYANSE    = new Color(0x5B6B3D);
    private static final Color MOREN_BIANKUANGYANSE = new Color(0xD8C58A);
    private static final Color MOREN_SHUZIYANSE     = Color.WHITE;
    private static final Color MOREN_FUWENBENYANSE  = new Color(0xF0E6C0);

    /* ==================== 整体缩放控制变量 ==================== */
    private double jiemianSuofang = 0.1;

    @Live(Live.Role.LON) public double jingdu;
    @Live(Live.Role.LAT) public double weidu;
    public int bianhao;

    /* ==================== 数据字段 ==================== */
    private Image  tubiao;
    private int    shuliang      = 0;
    private String fuwenben      = "";
    public Color  kapianyanse   = MOREN_KAPIANYANSE;
    private Color  biankuangyanse = MOREN_BIANKUANGYANSE;
    private Color  shuziyanse    = MOREN_SHUZIYANSE;
    private Color  fuwenbenyanse = MOREN_FUWENBENYANSE;
    private double shiqibaifenbi = 1.0;
    private boolean yixuanzhong  = false;
    private boolean xianshiShiqitiao = true;
    private Font   shuziZiti     = new Font("Arial", Font.BOLD, 20);
    private Font   fuwenbenZiti  = new Font("Arial", Font.PLAIN, 8);
    public country guojia;
    public bingmoshuju shuju;
    public zuobiao renwumubiao = new zuobiao(0, 0);
    public int suoshulujingzhixianbianhao;

    /* ==================== 蓝色加粗边框高亮 ==================== */
    private boolean lanseCubiankuang = false;
    private Color   lanseCubiankuangYanse = new Color(0x1E90FF);
    private float   lanseCubiankuangBeishu = 1.8f;

    /* ==================== 轨迹相关 ==================== */
    private float guijiKuandu = 0f;
    private double guijiDianBanjing = 0.005d;
    private boolean guijiQiyong = true;
    private double guijiZuixiaoPingmuXiangsu = 3.0;
    private double shangciGuijiJingdu;
    private double shangciGuijiWeidu;
    private boolean guijiChushihua = false;
    private transient PaintBoard.dian wodeDian;
    private boolean xianshiDangqianDian = true;

    public float getTrailWidth() { return guijiKuandu; }
    public void setTrailWidth(float kuandu) { this.guijiKuandu = kuandu; }

    public double getTrailDotRadius() { return guijiDianBanjing; }
    public void setTrailDotRadius(double banjing) {
        if (banjing > 0) this.guijiDianBanjing = banjing;
    }

    public boolean isTrailEnabled() { return guijiQiyong; }
    public void setTrailEnabled(boolean qiyong) { this.guijiQiyong = qiyong; }

    public double getTrailMinScreenPx() { return guijiZuixiaoPingmuXiangsu; }
    public void setTrailMinScreenPx(double xiangsu) {
        if (xiangsu > 0.5) this.guijiZuixiaoPingmuXiangsu = xiangsu;
    }

    public boolean isShowCurrentDot() { return xianshiDangqianDian; }
    public void setShowCurrentDot(boolean qiyong) {
        this.xianshiDangqianDian = qiyong;
        if (!qiyong && wodeDian != null) {
            board.removeGeoDot(wodeDian);
            wodeDian = null;
        }
    }

    private float computeTrailWidthPx() {
        if (guijiKuandu > 0f) return guijiKuandu;
        double suofang = board.getZoom();
        if (!(suofang > 0)) return 1.5f;
        return (float) Math.max(1.5d, guijiDianBanjing * suofang * 2.0d);
    }

    /* ============================================================
     *  ★ 高频 tick 优化：累加步数 + 时间窗口一次性执行
     * ============================================================ */
    private final AtomicInteger daichuliZhenShu = new AtomicInteger(0);
    private volatile long shangciShuaxinNamia = 0L;
    private volatile long zhenShuaxinChuangkouNamia = 4_000_000L; // 4ms，约 250Hz

    public void setTickFlushWindowMillis(long haomiao) {
        zhenShuaxinChuangkouNamia = Math.max(0L, haomiao) * 1_000_000L;
    }

    public void tick() {
        daichuliZhenShu.incrementAndGet();

        long xianzai = System.nanoTime();

        shangciShuaxinNamia = xianzai;

        int bushu = daichuliZhenShu.getAndSet(0);
        if (bushu <= 0) return;
        double jingdu = this.jingdu;
        double weidu = this.weidu;
        try {
            goto_MEIYIZHENZHIXING(bushu);
            syncCurrentDot();
        } catch (Throwable t) {
            // 单帧异常不影响后续帧
        }
        tick_go(jingdu, weidu, this.jingdu, this.weidu);
    }

    /* ============================================================
     *  ★ 每帧单步执行：自动捕获 goto_MEIYIZHENZHIXING 前后的位置
     * ============================================================ */

    /**
     * 处理一次位置变化。
     * 默认留空，因为轨迹段与路径点已在 goto_MEIYIZHENZHIXING 内部处理。
     * 如需在此处扩展逻辑（如记录位移、触发事件等），可重写或修改此方法。
     *
     * @param aJingdu 移动前经度
     * @param aWeidu 移动前纬度
     * @param bJingdu 移动后经度
     * @param bWeidu 移动后纬度
     */
    public static boolean a_blianx_ybanjingsizekanx1_y1shifouzaikuangnei(
            double aJingdu, double aWeidu,
            double bJingdu, double bWeidu,
            double dianJingdu, double dianWeidu,
            double banjing) {
        // 向量 AB
        double abJingdu = bJingdu - aJingdu;
        double abWeidu = bWeidu - aWeidu;
        // 向量 AP
        double apJingdu = dianJingdu - aJingdu;
        double apWeidu = dianWeidu - aWeidu;

        // 点积
        double dianji = apJingdu * abJingdu + apWeidu * abWeidu;
        // 如果点积 <=0，离A点最近，判断到A点距离
        if (dianji <= 0) {
            double juliPingfang = apJingdu * apJingdu + apWeidu * apWeidu;
            return juliPingfang <= banjing * banjing;
        }

        // AB长度平方
        double abChangduPingfang = abJingdu * abJingdu + abWeidu * abWeidu;
        // 投影超过B点，判断到B点距离
        if (dianji >= abChangduPingfang) {
            double bpJingdu = dianJingdu - bJingdu;
            double bpWeidu = dianWeidu - bWeidu;
            double juliPingfang = bpJingdu * bpJingdu + bpWeidu * bpWeidu;
            return juliPingfang <= banjing * banjing;
        }

        // 在线段中间区域：点到线段距离
        double juli = Math.abs(abJingdu * apWeidu - abWeidu * apJingdu)
                / Math.sqrt(abChangduPingfang);
        return juli <= banjing;
    }

    public void tick_go(double qianJingdu, double qianWeidu,
                        double houJingdu, double houWeidu) {
        for (country guojia1 : this.guojia.gongjizhe) {
            for (BINGPAI bingpai : guojia1.jundui) {

                if (a_blianx_ybanjingsizekanx1_y1shifouzaikuangnei(
                        qianJingdu, qianWeidu, houJingdu, houWeidu,
                        bingpai.jingdu, bingpai.weidu, shuju.fanwei)) {
//                    bingpai.shuju.gongjizhe.add(this);
//                    this.shuju.gongjizhe.add(bingpai);
//                    bingpai.kapianyanse = Color.BLACK;
//                    bingpai.addNumber(-1);
//                    this.kapianyanse = Color.BLACK;
//                    this.addNumber(-1);
                    if(bingpai.shuju.zhudongxing>this.shuju.zhudongxing) EVENTMAIN.post(new gongji(this,bingpai));
                    else  EVENTMAIN.post(new gongji(bingpai,this));
                }
            }
        }
    }

    /* ============================================================
     *  ★ 构造函数
     * ============================================================ */
    public BINGPAI(Image tubiao,
                   int shuliang,
                   String fuwenben,
                   double jingdu,
                   double weidu,
                   country guojia,
                   bingmoshuju shuju,
                   Color kapianyanse) {
        this.tubiao      = tubiao;
        this.guojia      = guojia;
        this.shuliang    = shuliang;
        this.shuju       = shuju;
        this.fuwenben    = fuwenben == null ? "" : fuwenben;
        this.kapianyanse = kapianyanse == null ? MOREN_KAPIANYANSE : kapianyanse;

        super.setBackground(this.kapianyanse);
        super.setForeground(this.shuziyanse);

        setContentAreaFilled(false);
        setBorderPainted(false);
        setFocusPainted(false);
        setOpaque(false);
        setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        this.jingdu = jingdu;
        this.weidu  = weidu;
        this.renwumubiao.x = jingdu;
        this.renwumubiao.y = weidu;

        this.shangciGuijiJingdu = jingdu;
        this.shangciGuijiWeidu  = weidu;
        this.guijiChushihua = true;
        this.setTrailDotRadius(0.02d);
        applyScale();
    }

    public BINGPAI(Image tubiao, int shuliang, double jingdu, double weidu,
                   country guojia, bingmoshuju shuju, Color kapianyanse) {
        this(tubiao, shuliang, "", jingdu, weidu, guojia, shuju, kapianyanse);
    }

    public BINGPAI(ImageIcon tubiao, int shuliang, String fuwenben,
                   double jingdu, double weidu,
                   country guojia, bingmoshuju shuju, Color kapianyanse) {
        this(tubiao == null ? null : tubiao.getImage(), shuliang, fuwenben,
                jingdu, weidu, guojia, shuju, kapianyanse);
    }

    public BINGPAI(Image tubiao, int shuliang, double jingdu, double weidu,
                   country guojia, bingmoshuju shuju) {
        this(tubiao, shuliang, "", jingdu, weidu, guojia, shuju, MOREN_KAPIANYANSE);
    }

    public BINGPAI(Image tubiao, int shuliang, String fuwenben,
                   double jingdu, double weidu,
                   country guojia, bingmoshuju shuju) {
        this(tubiao, shuliang, fuwenben, jingdu, weidu, guojia, shuju, MOREN_KAPIANYANSE);
    }

    public BINGPAI(ImageIcon tubiao, int shuliang, String fuwenben,
                   double jingdu, double weidu,
                   country guojia, bingmoshuju shuju) {
        this(tubiao == null ? null : tubiao.getImage(), shuliang, fuwenben,
                jingdu, weidu, guojia, shuju, MOREN_KAPIANYANSE);
    }

    /* ==================== 缩放控制 ==================== */
    public double getUiScale() { return jiemianSuofang; }

    public void setUiScale(double suofang) {
        this.jiemianSuofang = Math.max(0.05, suofang);
        applyScale();
    }

    private void applyScale() {
        int kuan = Math.max(1, (int) Math.round(JICHU_KUAN * jiemianSuofang));
        int gao  = Math.max(1, (int) Math.round(JICHU_GAO * jiemianSuofang));
        Dimension chicun = new Dimension(kuan, gao);
        setPreferredSize(chicun);
        setSize(chicun);
        revalidate();
    }

    /* ==================== get / set ==================== */
    public Image getIconImage() { return tubiao; }
    public void setIconImage(Image tubiao) { this.tubiao = tubiao; }

    public int getNumber() { return shuliang; }
    public void setNumber(int shuliang) { this.shuliang = shuliang; }
    public void addNumber(int zengliang) { setNumber(this.shuliang + zengliang); }

    public String getSubText() { return fuwenben; }
    public void setSubText(String fuwenben) {
        this.fuwenben = fuwenben == null ? "" : fuwenben;
    }

    public Color getCardColor() { return kapianyanse; }

    public void setCardColor(Color kapianyanse) {
        this.kapianyanse = kapianyanse == null ? MOREN_KAPIANYANSE : kapianyanse;
        super.setBackground(this.kapianyanse);
    }

    public void setColor(Color yanse) { setCardColor(yanse); }
    public void setColor(int yansezhi) { setCardColor(new Color(yansezhi, true)); }
    public Color getColor() { return kapianyanse; }

    public Color getBorderColor() { return biankuangyanse; }
    public void setBorderColor(Color biankuangyanse) {
        this.biankuangyanse = biankuangyanse == null
                ? MOREN_BIANKUANGYANSE : biankuangyanse;
    }

    public Color getNumberColor() { return shuziyanse; }
    public void setNumberColor(Color shuziyanse) {
        this.shuziyanse = shuziyanse == null ? MOREN_SHUZIYANSE : shuziyanse;
        super.setForeground(this.shuziyanse);
    }

    public Color getSubTextColor() { return fuwenbenyanse; }
    public void setSubTextColor(Color fuwenbenyanse) {
        this.fuwenbenyanse = fuwenbenyanse == null
                ? MOREN_FUWENBENYANSE : fuwenbenyanse;
    }

    public void setColors(Color kapian, Color biankuang,
                          Color shuzi, Color fuwenben) {
        if (kapian    != null) this.kapianyanse   = kapian;
        if (biankuang != null) this.biankuangyanse = biankuang;
        if (shuzi     != null) this.shuziyanse     = shuzi;
        if (fuwenben  != null) this.fuwenbenyanse  = fuwenben;
        super.setBackground(this.kapianyanse);
        super.setForeground(this.shuziyanse);
    }

    public void autoNumberColor() {
        double liangdu = (0.299 * kapianyanse.getRed()
                + 0.587 * kapianyanse.getGreen()
                + 0.114 * kapianyanse.getBlue()) / 255.0;
        setNumberColor(liangdu > 0.6 ? new Color(0x1A1A1A) : Color.WHITE);
    }

    @Override
    public void setBackground(Color beijingyanse) {
        super.setBackground(beijingyanse);
        this.kapianyanse = (beijingyanse == null)
                ? MOREN_KAPIANYANSE : beijingyanse;
    }

    @Override
    public Color getBackground() { return kapianyanse; }

    @Override
    public void setForeground(Color qianjingyanse) {
        super.setForeground(qianjingyanse);
        if (qianjingyanse != null) {
            this.shuziyanse = qianjingyanse;
        }
    }

    @Override
    public Color getForeground() { return shuziyanse; }

    public double getMoralePercent() { return shiqibaifenbi; }
    public void zuzhidu(double shiqibaifenbi) {
        this.shiqibaifenbi = Math.max(0.0, Math.min(1.0, shiqibaifenbi));
    }

    public boolean isSelected() { return yixuanzhong; }
    @Override public void setSelected(boolean yixuanzhong) {
        this.yixuanzhong = yixuanzhong;
    }

    public boolean isShowMoraleBar() { return xianshiShiqitiao; }
    public void setShowMoraleBar(boolean xianshiShiqitiao) {
        this.xianshiShiqitiao = xianshiShiqitiao;
    }

    public Font getNumberFont() { return shuziZiti; }
    public void setNumberFont(Font shuziZiti) {
        if (shuziZiti != null) { this.shuziZiti = shuziZiti; }
    }

    public Font getSubTextFont() { return fuwenbenZiti; }
    public void setSubTextFont(Font fuwenbenZiti) {
        if (fuwenbenZiti != null) { this.fuwenbenZiti = fuwenbenZiti; }
    }

    public void setCardSize(int kuan, int gao) {
        double suofang = Math.min(kuan / (double) JICHU_KUAN,
                gao / (double) JICHU_GAO);
        setUiScale(suofang);
    }

    /* ==================== 蓝色加粗边框 控制 ==================== */
    public void xianshilansebiankuang() {
        this.lanseCubiankuang = true;
    }

    public void quxiaolansebiankuang() {
        this.lanseCubiankuang = false;
    }

    public void setBlueBoldBorder(boolean qiyong) {
        this.lanseCubiankuang = qiyong;
    }

    public boolean isBlueBoldBorder() { return lanseCubiankuang; }

    public Color getBlueBoldColor() { return lanseCubiankuangYanse; }
    public void setBlueBoldColor(Color yanse) {
        if (yanse != null) { this.lanseCubiankuangYanse = yanse; }
    }

    public float getBlueBoldFactor() { return lanseCubiankuangBeishu; }
    public void setBlueBoldFactor(float beishu) {
        if (beishu > 0f) { this.lanseCubiankuangBeishu = beishu; }
    }

    /* ==================== 绘制 ==================== */
    @Override
    protected void paintComponent(Graphics tuxing) {
        Graphics2D tuxing2 = (Graphics2D) tuxing.create();
        tuxing2.setRenderingHint(RenderingHints.KEY_ANTIALIASING,
                RenderingHints.VALUE_ANTIALIAS_ON);
        tuxing2.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING,
                RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int kuan = getWidth();
        int gao  = getHeight();
        if (kuan <= 0 || gao <= 0) { tuxing2.dispose(); return; }

        Color jichu = kapianyanse;
        if (getModel().isPressed())       jichu = jichu.darker();
        else if (getModel().isRollover()) jichu = jichu.brighter();
        tuxing2.setColor(jichu);
        tuxing2.fillRect(0, 0, kuan, gao);

        float suofang = Math.min(kuan / (float) JICHU_KUAN,
                gao / (float) JICHU_GAO);

        float huizhiKuan = JICHU_KUAN * suofang;
        float huizhiGao  = JICHU_GAO * suofang;

        float pianyiHeng = (kuan - huizhiKuan) / 2f;
        float pianyiZong = (gao - huizhiGao) / 2f;
        tuxing2.translate(pianyiHeng, pianyiZong);

        int juxingKuan = Math.round(huizhiKuan);
        int juxingGao  = Math.round(huizhiGao);
        int xiangsu1   = Math.max(1, Math.round(1 * suofang));

        float huaxianKuandu = Math.max(1f, (yixuanzhong ? 3f : 2f) * suofang);
        Color huaxianYanse  = yixuanzhong ? Color.YELLOW : biankuangyanse;

        if (lanseCubiankuang) {
            huaxianKuandu = Math.max(1f, huaxianKuandu * lanseCubiankuangBeishu);
            huaxianYanse  = lanseCubiankuangYanse;
        }

        int neisuo = Math.max(1, Math.round(huaxianKuandu / 2f));
        tuxing2.setColor(huaxianYanse);
        tuxing2.setStroke(new BasicStroke(huaxianKuandu));
        tuxing2.drawRect(neisuo, neisuo,
                Math.max(1, juxingKuan - neisuo * 2 - 1),
                Math.max(1, juxingGao - neisuo * 2 - 1));

        if (tubiao != null) {
            int tubiaoHeng = Math.round(6 * suofang);
            int tubiaoZong = Math.round(8 * suofang);
            int tubiaoKuan = Math.max(4, Math.round(30 * suofang));
            int tubiaoGao  = Math.max(3, Math.round(20 * suofang));
            tuxing2.drawImage(tubiao, tubiaoHeng, tubiaoZong,
                    tubiaoKuan, tubiaoGao, this);
            tuxing2.setColor(new Color(0x00000055));
            tuxing2.setStroke(new BasicStroke(Math.max(1f, suofang)));
            tuxing2.drawRect(tubiaoHeng, tubiaoZong,
                    tubiaoKuan - xiangsu1, tubiaoGao - xiangsu1);
        }

        String shuziZiFu = String.valueOf(shuliang);
        tuxing2.setFont(shuziZiti.deriveFont(shuziZiti.getSize2D() * suofang));
        FontMetrics zitiDuliang = tuxing2.getFontMetrics();
        int wenziKuan = zitiDuliang.stringWidth(shuziZiFu);
        int wenziHeng = Math.round((huizhiKuan - wenziKuan) / 2f);
        int wenziZong = Math.round(huizhiGao / 2f
                + (zitiDuliang.getAscent() - zitiDuliang.getDescent()) / 2f);

        tuxing2.setColor(new Color(0, 0, 0, 160));
        tuxing2.drawString(shuziZiFu, wenziHeng + xiangsu1, wenziZong + xiangsu1);
        tuxing2.setColor(shuziyanse);
        tuxing2.drawString(shuziZiFu, wenziHeng, wenziZong);

        if (fuwenben != null && !fuwenben.isEmpty()) {
            tuxing2.setFont(fuwenbenZiti.deriveFont(
                    fuwenbenZiti.getSize2D() * suofang));
            FontMetrics fuwenbenDuliang = tuxing2.getFontMetrics();
            int fuwenbenKuan = fuwenbenDuliang.stringWidth(fuwenben);
            int bianju = Math.round(6 * suofang);
            tuxing2.setColor(fuwenbenyanse);
            tuxing2.drawString(fuwenben,
                    juxingKuan - fuwenbenKuan - bianju,
                    juxingGao - bianju);
        }

        if (xianshiShiqitiao) {
            int bianju = Math.round(6 * suofang);
            int tiaoGao = Math.max(1, Math.round(4 * suofang));
            int mantiaoKuan = Math.max(1, juxingKuan - bianju * 2);
            int tiaoKuan = (int) (mantiaoKuan * shiqibaifenbi);
            int tiaoZong = juxingGao - tiaoGao - bianju;

            tuxing2.setColor(new Color(0x00000088));
            tuxing2.fillRect(bianju, tiaoZong, mantiaoKuan, tiaoGao);

            tuxing2.setColor(barColorFor(shiqibaifenbi));
            tuxing2.fillRect(bianju, tiaoZong, tiaoKuan, tiaoGao);
        }

        tuxing2.dispose();
    }

    private static Color barColorFor(double baifenbi) {
        if (baifenbi < 0.33)      return new Color(0xD9534F);
        else if (baifenbi < 0.66) return new Color(0xE8B33B);
        else                      return new Color(0x6FBF4A);
    }

    @Override
    public JButton beixuanzeanniu() {
        this.setBorder(BorderFactory.createLineBorder(Color.YELLOW));
        return this;
    }

    public void goto_(zuobiao mubiao) {
        this.renwumubiao = mubiao;
    }

    /* ============================================================
     *  ★ 地球仪式寻路（合并步数版）
     * ============================================================ */
    public void goto_MEIYIZHENZHIXING() {
        goto_MEIYIZHENZHIXING(1);
    }

    public void goto_MEIYIZHENZHIXING(int bushu) {
        if (renwumubiao == null || shuju == null || bushu <= 0) return;

        if (!guijiChushihua) {
            shangciGuijiJingdu = this.jingdu;
            shangciGuijiWeidu  = this.weidu;
            guijiChushihua = true;
        }

        double jingdu1 = this.jingdu;
        double weidu1  = this.weidu;
        double jingdu2 = renwumubiao.x;
        double weidu2  = renwumubiao.y;

        double dJingdu = jingdu2 - jingdu1;
        dJingdu = ((dJingdu + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        double dWeidu = weidu2 - weidu1;

        if (dJingdu == 0.0 && dWeidu == 0.0) {
            flushTrailToCurrent();
            syncCurrentDot();
            return;
        }

        double pingjunWeidu = (weidu1 + weidu2) * 0.5;
        double cosWeidu = Math.cos(Math.toRadians(pingjunWeidu));

        final double MEIDU_QIANMI = 111.32;
        double dWeiduQianmi = dWeidu * MEIDU_QIANMI;
        double dJingduQianmi = dJingdu * MEIDU_QIANMI * cosWeidu;
        double juliQianmi = Math.sqrt(
                dWeiduQianmi * dWeiduQianmi
                        + dJingduQianmi * dJingduQianmi);

        double sudu = shuju.speed / 3600.0 * bushu;

        if (sudu <= 0.0 || juliQianmi <= sudu) {
            this.jingdu = jingdu1 + dJingdu;
            this.weidu  = weidu2;
        } else {
            double bili = sudu / juliQianmi;
            this.jingdu = jingdu1 + bili * dJingdu;
            this.weidu  = weidu1 + bili * dWeidu;
        }

        maybeAddTrailSegment();
    }

    private void maybeAddTrailSegment() {
        if (!guijiQiyong) return;

        double meiduXiangsu = board.getZoom();
        if (!(meiduXiangsu > 0)) return;

        double dxXiangsu = Math.abs(this.jingdu - shangciGuijiJingdu)
                * meiduXiangsu;
        double dyXiangsu = Math.abs(this.weidu - shangciGuijiWeidu)
                * meiduXiangsu;
        double juliXiangsu = Math.sqrt(
                dxXiangsu * dxXiangsu + dyXiangsu * dyXiangsu);

        float kuanduXiangsu = computeTrailWidthPx();
        double buchangXiangsu = Math.max(
                guijiZuixiaoPingmuXiangsu, kuanduXiangsu * 0.5d);

        if (juliXiangsu >= buchangXiangsu) {
            double qishiJingdu = shangciGuijiJingdu;
            double qishiWeidu  = shangciGuijiWeidu;
            double jieshuJingdu = this.jingdu;
            double jieshuWeidu  = this.weidu;

            board.addTrailSegment(this,
                    qishiJingdu, qishiWeidu,
                    jieshuJingdu, jieshuWeidu,
                    this.kapianyanse, kuanduXiangsu);
            recordPathPoints(qishiJingdu, qishiWeidu,
                    jieshuJingdu, jieshuWeidu);

            shangciGuijiJingdu = this.jingdu;
            shangciGuijiWeidu  = this.weidu;
        }
    }

    private void flushTrailToCurrent() {
        if (!guijiQiyong || !guijiChushihua) return;
        if (shangciGuijiJingdu == this.jingdu
                && shangciGuijiWeidu == this.weidu) return;

        double qishiJingdu = shangciGuijiJingdu;
        double qishiWeidu  = shangciGuijiWeidu;
        double jieshuJingdu = this.jingdu;
        double jieshuWeidu  = this.weidu;

        board.addTrailSegment(this,
                qishiJingdu, qishiWeidu,
                jieshuJingdu, jieshuWeidu,
                this.kapianyanse, computeTrailWidthPx());
        recordPathPoints(qishiJingdu, qishiWeidu,
                jieshuJingdu, jieshuWeidu);

        shangciGuijiJingdu = this.jingdu;
        shangciGuijiWeidu  = this.weidu;
    }

    private double shangciTongbuDianJingdu = Double.NaN;
    private double shangciTongbuDianWeidu  = Double.NaN;

    private void syncCurrentDot() {
        if (!xianshiDangqianDian) return;

        if (wodeDian == null) {
            wodeDian = board.addGeoDotLive(
                    this.jingdu, this.weidu,
                    guijiDianBanjing, this.kapianyanse);
            shangciTongbuDianJingdu = this.jingdu;
            shangciTongbuDianWeidu  = this.weidu;
            board.bumpLiveDotsVersion();
            return;
        }

        if (this.jingdu == shangciTongbuDianJingdu
                && this.weidu == shangciTongbuDianWeidu) return;

        wodeDian.lon = this.jingdu;
        wodeDian.lat = this.weidu;
        wodeDian.color = this.kapianyanse;

        shangciTongbuDianJingdu = this.jingdu;
        shangciTongbuDianWeidu  = this.weidu;
        board.bumpLiveDotsVersion();
    }

    public void dispose() {
        if (wodeDian != null) {
            board.removeGeoDot(wodeDian);
            wodeDian = null;
        }
        if (board != null) {
            board.removeOwnerTrails(this);
        }
    }

    public void xianshi() {
        BINGPAI bingpai = this;
        bingpai = (BINGPAI) gongju.quchuquanbujiantingqi(bingpai);
        bingpai.bianhao = board.addAutoComponent(bingpai, bingpai);

        BINGPAI zuizhongBingpai2 = bingpai;
        BINGPAI zuizhongBingpai  = bingpai;

        bingpai.addActionListener(shijian -> {
            try {
                Boolean chenggong = true;
                if (gongju.shiftdown()) {
                    chenggong = xuandingbianliang.add(zuizhongBingpai2);
                } else {
                    xuandingbianliang.clearall();
                    chenggong = xuandingbianliang.add(zuizhongBingpai2);
                }
                if (chenggong) {
                    EVENTMAIN.post(new beixuanze_DUOXUAN(zuizhongBingpai));
                }
            } catch (Exception yichang) {
                throw new RuntimeException(yichang);
            }
        });
    }

    /* ============================================================
     *  ★ 路径坐标集合（仅记录，不参与攻击检测）
     * ============================================================ */
    public List<zuobiao> suozouzuobiao =
            Collections.synchronizedList(new ArrayList<>());

    private final Set<Long> suozouzuobiaojian = ConcurrentHashMap.newKeySet();

    public void clearSuozouzuobiao() {
        synchronized (suozouzuobiao) {
            suozouzuobiao.clear();
            suozouzuobiaojian.clear();
        }
    }

    /** 轨迹路径采样步长（度），约 2km。 */
    private static final double GUIJI_CAIYANG_BUSHENG_DU = 0.02;

    private void addPathPoint(double jingdu, double weidu) {
        jingdu = ((jingdu + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        if (weidu < -90.0) weidu = -90.0;
        else if (weidu > 90.0) weidu = 90.0;

        long jian = Double.doubleToLongBits(jingdu) * 31L
                + Double.doubleToLongBits(weidu);
        if (suozouzuobiaojian.add(jian)) {
            zuobiao z = new zuobiao(0, 0);
            z.x = jingdu;
            z.y = weidu;
            suozouzuobiao.add(z);
        }
    }

    private void recordPathPoints(double jingdu1, double weidu1,
                                  double jingdu2, double weidu2) {
        double dJingdu = jingdu2 - jingdu1;
        dJingdu = ((dJingdu + 180.0) % 360.0 + 360.0) % 360.0 - 180.0;
        double dWeidu = weidu2 - weidu1;
        double juliDu = Math.sqrt(dJingdu * dJingdu + dWeidu * dWeidu);

        if (juliDu < 1e-9) {
            addPathPoint(jingdu1, weidu1);
            return;
        }

        int bushu = Math.max(1,
                (int) Math.ceil(juliDu / GUIJI_CAIYANG_BUSHENG_DU));
        if (bushu > 512) bushu = 512;

        for (int i = 0; i <= bushu; i++) {
            double bili = (double) i / bushu;
            addPathPoint(jingdu1 + bili * dJingdu,
                    weidu1 + bili * dWeidu);
        }
    }
}