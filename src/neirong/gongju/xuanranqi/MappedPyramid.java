package neirong.gongju.xuanranqi;


import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;

/**
 * 磁盘上的多分辨率金字塔。
 * Level 0 = 最高分辨率（basePpd），每层 PPD 减半。
 * <p>典型的层选择规则：挑 PPD &le; 目标值 中最大的那层。</p>
 */
public class MappedPyramid implements Closeable {

    public final int basePpd;
    public final int levels;

    private final MappedImage[] images;
    private final int[] ppds;
    private final File dir;

    public MappedPyramid(File dir, int basePpd, int levels) throws IOException {
        this.dir = dir;
        this.basePpd = basePpd;
        this.levels = levels;
        this.images = new MappedImage[levels];
        this.ppds = new int[levels];

        int p = basePpd;
        for (int i = 0; i < levels; i++) {
            int w = 360 * p;
            int h = 180 * p;
            images[i] = new MappedImage(
                    new File(dir, "level_" + i + "_ppd" + p + ".dat"), w, h);
            ppds[i] = p;
            p = Math.max(1, p / 2);
        }
    }

    public int getPpd(int level)        { return ppds[level]; }
    public MappedImage get(int level)   { return images[level]; }
    public File getDir()                { return dir; }

    /**
     * 从整图重建全部层。传入的是最高分辨率的 BufferedImage。
     * 逐层降采样，比逐层独立渲染矢量快得多。
     */
    public void rebuildFromBase(BufferedImage base) {
        images[0].writeAll(base);

        BufferedImage prev = base;
        for (int i = 1; i < levels; i++) {
            int w = images[i].getWidth();
            int h = images[i].getHeight();
            BufferedImage scaled = new BufferedImage(w, h,
                    BufferedImage.TYPE_INT_ARGB_PRE);
            Graphics2D g = scaled.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION,
                        RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                g.setRenderingHint(RenderingHints.KEY_RENDERING,
                        RenderingHints.VALUE_RENDER_QUALITY);
                g.setComposite(AlphaComposite.Src);
                g.drawImage(prev, 0, 0, w, h, null);
            } finally {
                g.dispose();
            }
            images[i].writeAll(scaled);
            scaled.flush();
            prev = scaled;
        }
    }

    /**
     * 根据目标 ppd 选层：返回 ppds[i] &le; target 中最大的那层。
     * 例如 target=12，层 PPD 为 {32,16,8,4,2,1}，返回 PPD=8 的那层。
     */
    public int pickLevel(double target) {
        for (int i = 0; i < levels; i++) {
            if (ppds[i] <= target) return i;
        }
        return levels - 1;   // target < 1，用最低分辨率层
    }

    public void clear() {
        for (MappedImage m : images) m.clear();
    }

    public void force() {
        for (MappedImage m : images) m.force();
    }

    @Override
    public void close() throws IOException {
        for (MappedImage m : images) {
            try { m.close(); } catch (Exception ignored) {}
        }
    }
}