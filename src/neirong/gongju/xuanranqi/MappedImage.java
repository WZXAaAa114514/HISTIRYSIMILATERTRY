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
    private final long width, height;

    private BufferedImage regionCache;
    private long regionW = -1, regionH = -1;

    public MappedImage(File file, long width, long height) throws IOException {
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

    public long getWidth()  { return width; }
    public long getHeight() { return height; }
    public File getFile()  { return file; }

    /** 整图写入 */
    public void writeAll(BufferedImage src) {
        long w = Math.min(src.getWidth(), width);
        long h = Math.min(src.getHeight(), height);
        int[] row = new int[(int) w];
        for (long y = 0; y < h; y++) {
            src.getRGB(0, (int) y, (int) w, 1, row, 0, (int) w);
            intView.position((int) (y * width));
            intView.put(row);
        }
    }

    /** 写一个矩形区域 */
    public void writeRect(BufferedImage src, long x, long y) {
        long w = Math.min(src.getWidth(), width - x);
        long h = Math.min(src.getHeight(), height - y);
        if (w <= 0 || h <= 0) return;
        int[] row = new int[(int) w];
        for (long r = 0; r < h; r++) {
            src.getRGB(0, (int) r, (int) w, 1, row, 0, (int) w);
            intView.position((int) ((y + r) * width + x));
            intView.put(row);
        }
    }

    public void clear() {
        int[] zeros = new int[(int) Math.min(width, 8192)];
        long total = (long) width * height;
        long pos = 0;
        while (pos < total) {
            long n = (long) Math.min(total - pos, zeros.length);
            intView.position((int) pos);
            intView.put(zeros, 0, (int) n);
            pos += n;
        }
    }

    /** 读一个区域，复用同一张 BufferedImage。 */
    public BufferedImage readRegion(long x, long y, long w, long h) {
        if (x < 0) { w += x; x = 0; }
        if (y < 0) { h += y; y = 0; }
        if (x + w > width)  w = width  - x;
        if (y + h > height) h = height - y;
        if (w <= 0 || h <= 0) return null;

        if (regionCache == null || regionW != w || regionH != h) {
            regionCache = new BufferedImage((int) w, (int) h, BufferedImage.TYPE_INT_ARGB_PRE);
            regionW = w;
            regionH = h;
        }

        int[] dst = ((DataBufferInt) regionCache.getRaster().getDataBuffer()).getData();
        int dstPos = 0;
        for (long row = 0; row < h; row++) {
            intView.position((int) ((y + row) * width + x));
            intView.get(dst, dstPos, (int) w);
            dstPos += (int) w;
        }
        return regionCache;
    }

    public long getARGB(long x, long y) {
        if (x < 0 || y < 0 || x >= width || y >= height) return 0;
        return mbb.getInt((int) ((y * width + x) * 4));
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