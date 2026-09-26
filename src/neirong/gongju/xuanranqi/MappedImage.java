package neirong.gongju.xuanranqi;


import java.awt.image.BufferedImage;
import java.awt.image.DataBufferInt;
import java.io.Closeable;
import java.io.File;
import java.io.IOException;
import java.io.RandomAccessFile;
import java.nio.IntBuffer;
import java.nio.MappedByteBuffer;
import java.nio.channels.FileChannel;

/**
 * 用内存映射文件作为后端的大画布。
 * <p>像素存在磁盘文件中，只在需要时读可见区域；不占用 Java 堆内存。</p>
 */
public class MappedImage implements Closeable {

    private final File file;
    private final RandomAccessFile raf;
    private final FileChannel channel;
    private final MappedByteBuffer mbb;
    private final IntBuffer intView;
    private final int width, height;

    private BufferedImage regionCache;
    private int regionW = -1, regionH = -1;

    public MappedImage(File file, int width, int height) throws IOException {
        this.file = file;
        this.width = width;
        this.height = height;

        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) parent.mkdirs();

        long size = (long) width * height * 4L;
        this.raf = new RandomAccessFile(file, "rw");
        if (raf.length() < size) raf.setLength(size);
        this.channel = raf.getChannel();
        this.mbb = channel.map(FileChannel.MapMode.READ_WRITE, 0, size);
        this.intView = mbb.asIntBuffer();
    }

    public int getWidth()  { return width; }
    public int getHeight() { return height; }
    public File getFile()  { return file; }

    /** 整图写入 */
    public void writeAll(BufferedImage src) {
        int w = Math.min(src.getWidth(), width);
        int h = Math.min(src.getHeight(), height);
        int[] row = new int[w];
        for (int y = 0; y < h; y++) {
            src.getRGB(0, y, w, 1, row, 0, w);
            intView.position(y * width);
            intView.put(row);
        }
    }

    /** 写一个矩形区域 */
    public void writeRect(BufferedImage src, int x, int y) {
        int w = Math.min(src.getWidth(), width - x);
        int h = Math.min(src.getHeight(), height - y);
        if (w <= 0 || h <= 0) return;
        int[] row = new int[w];
        for (int r = 0; r < h; r++) {
            src.getRGB(0, r, w, 1, row, 0, w);
            intView.position((y + r) * width + x);
            intView.put(row);
        }
    }

    public void clear() {
        int[] zeros = new int[Math.min(width, 8192)];
        long total = (long) width * height;
        long pos = 0;
        while (pos < total) {
            int n = (int) Math.min(total - pos, zeros.length);
            intView.position((int) pos);
            intView.put(zeros, 0, n);
            pos += n;
        }
    }

    /** 读一个区域，复用同一张 BufferedImage。 */
    public BufferedImage readRegion(int x, int y, int w, int h) {
        if (x < 0) { w += x; x = 0; }
        if (y < 0) { h += y; y = 0; }
        if (x + w > width)  w = width  - x;
        if (y + h > height) h = height - y;
        if (w <= 0 || h <= 0) return null;

        if (regionCache == null || regionW != w || regionH != h) {
            regionCache = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB_PRE);
            regionW = w;
            regionH = h;
        }

        int[] dst = ((DataBufferInt) regionCache.getRaster().getDataBuffer()).getData();
        int dstPos = 0;
        for (int row = 0; row < h; row++) {
            intView.position((y + row) * width + x);
            intView.get(dst, dstPos, w);
            dstPos += w;
        }
        return regionCache;
    }

    public int getARGB(int x, int y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return mbb.getInt((y * width + x) * 4);
    }

    public void force() {
        try { mbb.force(); } catch (Exception ignored) {}
    }

    @Override
    public void close() throws IOException {
        force();
        try { channel.close(); } catch (Exception ignored) {}
        try { raf.close();    } catch (Exception ignored) {}
    }
}