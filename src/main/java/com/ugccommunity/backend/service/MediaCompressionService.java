package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.config.UploadCompressionProperties;
import com.ugccommunity.backend.common.upload.CompressionResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import javax.imageio.IIOImage;
import javax.imageio.ImageIO;
import javax.imageio.ImageWriteParam;
import javax.imageio.ImageWriter;
import javax.imageio.stream.ImageOutputStream;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;

@Service
public class MediaCompressionService {
    private static final Logger log = LoggerFactory.getLogger(MediaCompressionService.class);
    private static final long KB = 1024L;
    private static final long MAX_IMAGE_SIZE = 20L * 1024L * 1024L;

    private final UploadCompressionProperties properties;
    private final SettingService settingService;
    private final java.util.concurrent.atomic.AtomicInteger runningVideoJobs = new java.util.concurrent.atomic.AtomicInteger(0);

    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    public MediaCompressionService(UploadCompressionProperties properties, SettingService settingService) {
        this.properties = properties;
        this.settingService = settingService;
    }

    private boolean effectiveEnabled() {
        return settingService.getBoolean(SettingService.COMPRESSION_ENABLED, properties.isEnabled());
    }

    private boolean effectiveImageEnabled() {
        return settingService.getBoolean(SettingService.COMPRESSION_IMAGE_ENABLED, properties.isImageEnabled());
    }

    private int effectiveImageMaxLongEdge() {
        return settingService.getInt(SettingService.COMPRESSION_IMAGE_MAX_LONG_EDGE, properties.getImageMaxLongEdge());
    }

    private float effectiveImageQuality() {
        return settingService.getFloat(SettingService.COMPRESSION_IMAGE_QUALITY, properties.getImageQuality());
    }

    private int effectiveSkipImageSizeKb() {
        return settingService.getInt(SettingService.COMPRESSION_SKIP_IMAGE_SIZE_KB, properties.getSkipImageSizeKb());
    }

    private int effectiveThumbnailLongEdge() {
        return settingService.getInt(SettingService.COMPRESSION_THUMBNAIL_LONG_EDGE, properties.getThumbnailLongEdge());
    }

    private int effectiveLongImageWidth() {
        return settingService.getInt(SettingService.COMPRESSION_LONG_IMAGE_WIDTH, properties.getLongImageWidth());
    }

    private boolean effectiveVideoEnabled() {
        return settingService.getBoolean(SettingService.COMPRESSION_VIDEO_ENABLED, properties.isVideoEnabled());
    }

    private int effectiveVideoMaxLongEdge() {
        return settingService.getInt(SettingService.COMPRESSION_VIDEO_MAX_LONG_EDGE, properties.getVideoMaxLongEdge());
    }

    private int effectiveVideoCrf() {
        return settingService.getInt(SettingService.COMPRESSION_VIDEO_CRF, properties.getVideoCrf());
    }

    private int effectiveVideoWorkerCount() {
        return Math.max(1, settingService.getInt(SettingService.COMPRESSION_VIDEO_WORKER_COUNT, properties.getVideoWorkerCount()));
    }

    private int effectiveKeepOriginalBackupDays() {
        return settingService.getInt(SettingService.COMPRESSION_KEEP_ORIGINAL_BACKUP_DAYS, properties.getKeepOriginalBackupDays());
    }

    private String effectiveFfmpegPath() {
        return settingService.getString(SettingService.COMPRESSION_FFMPEG_PATH, "ffmpeg");
    }

    public CompressionResult compressIfNeeded(Path inputPath, String mimeType) {
        CompressionResult result = baseResult(inputPath, mimeType);
        if (!effectiveEnabled()) {
            result.setMessage("compression disabled");
            return result;
        }
        if (isImage(mimeType, inputPath)) {
            return compressImageIfNeeded(inputPath, mimeType);
        }
        if (isVideo(mimeType, inputPath)) {
            return compressVideoIfNeeded(inputPath, mimeType);
        }
        result.setMessage("not image or video, skipped");
        return result;
    }

    public CompressionResult compressImageIfNeeded(Path inputPath, String mimeType) {
        long started = System.currentTimeMillis();
        CompressionResult result = baseResult(inputPath, mimeType);
        if (!effectiveImageEnabled()) {
            result.setMessage("image compression disabled");
            return result;
        }
        String ext = extension(inputPath);
        if (!"jpg".equals(ext) && !"jpeg".equals(ext) && !"png".equals(ext)) {
            result.setMessage("animated or unsupported image skipped");
            return result;
        }
        if (result.getOriginalSize() > MAX_IMAGE_SIZE) {
            log.warn("large image upload will be compressed if possible path={} size={}", inputPath, result.getOriginalSize());
        }
        try {
            BufferedImage image = ImageIO.read(inputPath.toFile());
            if (image == null) {
                result.setMessage("imageio read failed, keep original");
                return result;
            }
            int width = image.getWidth();
            int height = image.getHeight();
            int longEdge = Math.max(width, height);
            boolean hasAlpha = image.getColorModel().hasAlpha();
            boolean longImage = height > width && height / (double) Math.max(width, 1) > 2.5d;
            boolean needsResize = longImage
                    ? width > effectiveLongImageWidth()
                    : longEdge > effectiveImageMaxLongEdge();
            boolean smallEnough = result.getOriginalSize() <= effectiveSkipImageSizeKb() * KB && longEdge <= 1600;
            BufferedImage working = image;
            if (needsResize) {
                working = longImage
                        ? resizeToWidth(image, effectiveLongImageWidth())
                        : resize(image, effectiveImageMaxLongEdge());
            }

            Path thumbnail = createThumbnail(working, inputPath, hasAlpha);
            if (thumbnail != null) {
                result.setThumbnailPath(thumbnail.toString());
            }

            if (smallEnough && !needsResize) {
                result.setMessage("small image, thumbnail only");
                result.setFinalSize(Files.size(inputPath));
                return result;
            }

            Path temp = inputPath.resolveSibling(stripExtension(inputPath.getFileName().toString()) + ".compressing." + ext);
            boolean jpeg = "jpg".equals(ext) || "jpeg".equals(ext);
            boolean written = writeImage(working, temp, jpeg ? "jpg" : "png", jpeg ? effectiveImageQuality() : 1.0f);
            if (!written || !Files.exists(temp)) {
                deleteQuietly(temp);
                result.setMessage("image write failed, keep original");
                return result;
            }
            long compressedSize = Files.size(temp);
            if (compressedSize > 0 && compressedSize < result.getOriginalSize()) {
                backupOriginal(inputPath);
                Files.move(temp, inputPath, StandardCopyOption.REPLACE_EXISTING);
                result.setCompressed(true);
                result.setFinalSize(compressedSize);
                result.setMessage("compressed");
            } else {
                deleteQuietly(temp);
                result.setFinalSize(Files.size(inputPath));
                result.setMessage("compressed file is not smaller, keep original");
            }
            log.info("image compression result path={} original={} final={} compressed={} thumb={} costMs={} message={}",
                    inputPath, result.getOriginalSize(), result.getFinalSize(), result.isCompressed(),
                    result.getThumbnailPath(), System.currentTimeMillis() - started, result.getMessage());
            return result;
        } catch (Exception e) {
            log.warn("image compression failed path={} message={}", inputPath, e.getMessage());
            result.setMessage("compression failed, keep original: " + e.getMessage());
            result.setFinalSize(sizeOf(inputPath));
            return result;
        }
    }

    public Path createThumbnail(Path inputPath) {
        try {
            BufferedImage image = ImageIO.read(inputPath.toFile());
            if (image == null) return null;
            return createThumbnail(image, inputPath, image.getColorModel().hasAlpha());
        } catch (IOException e) {
            log.warn("thumbnail failed path={} message={}", inputPath, e.getMessage());
            return null;
        }
    }

    private Path createThumbnail(BufferedImage image, Path inputPath, boolean hasAlpha) {
        try {
            BufferedImage thumb = resize(image, effectiveThumbnailLongEdge());
            String ext = hasAlpha ? "png" : "jpg";
            Path thumbnailPath = inputPath.resolveSibling("thumb_" + stripExtension(inputPath.getFileName().toString()) + "." + ext);
            if (writeImage(thumb, thumbnailPath, ext, hasAlpha ? 1.0f : 0.78f)) {
                return thumbnailPath;
            }
        } catch (Exception e) {
            log.warn("thumbnail failed path={} message={}", inputPath, e.getMessage());
        }
        return null;
    }

    private BufferedImage resize(BufferedImage source, int maxLongEdge) {
        int width = source.getWidth();
        int height = source.getHeight();
        int longEdge = Math.max(width, height);
        if (longEdge <= maxLongEdge) {
            return source;
        }
        double scale = maxLongEdge / (double) longEdge;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return target;
    }

    private BufferedImage resizeToWidth(BufferedImage source, int maxWidth) {
        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= maxWidth) {
            return source;
        }
        double scale = maxWidth / (double) width;
        int targetWidth = Math.max(1, (int) Math.round(width * scale));
        int targetHeight = Math.max(1, (int) Math.round(height * scale));
        int type = source.getColorModel().hasAlpha() ? BufferedImage.TYPE_INT_ARGB : BufferedImage.TYPE_INT_RGB;
        BufferedImage target = new BufferedImage(targetWidth, targetHeight, type);
        Graphics2D graphics = target.createGraphics();
        graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        graphics.drawImage(source, 0, 0, targetWidth, targetHeight, null);
        graphics.dispose();
        return target;
    }

    private boolean writeImage(BufferedImage image, Path target, String format, float quality) throws IOException {
        Files.createDirectories(target.getParent());
        if (!"jpg".equals(format) && !"jpeg".equals(format)) {
            return ImageIO.write(image, format, target.toFile());
        }
        Iterator<ImageWriter> writers = ImageIO.getImageWritersByFormatName("jpg");
        if (!writers.hasNext()) return false;
        ImageWriter writer = writers.next();
        try (ImageOutputStream output = ImageIO.createImageOutputStream(target.toFile())) {
            writer.setOutput(output);
            ImageWriteParam param = writer.getDefaultWriteParam();
            if (param.canWriteCompressed()) {
                param.setCompressionMode(ImageWriteParam.MODE_EXPLICIT);
                param.setCompressionQuality(Math.max(0.1f, Math.min(1.0f, quality)));
            }
            BufferedImage rgb = ensureRgb(image);
            writer.write(null, new IIOImage(rgb, null, null), param);
            return true;
        } finally {
            writer.dispose();
        }
    }

    private BufferedImage ensureRgb(BufferedImage image) {
        if (image.getType() == BufferedImage.TYPE_INT_RGB) return image;
        BufferedImage rgb = new BufferedImage(image.getWidth(), image.getHeight(), BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = rgb.createGraphics();
        graphics.setColor(java.awt.Color.WHITE);
        graphics.fillRect(0, 0, image.getWidth(), image.getHeight());
        graphics.drawImage(image, 0, 0, null);
        graphics.dispose();
        return rgb;
    }

    public CompressionResult compressVideoIfNeeded(Path inputPath, String mimeType) {
        long started = System.currentTimeMillis();
        CompressionResult result = baseResult(inputPath, mimeType);
        if (!effectiveVideoEnabled()) {
            result.setMessage("video compression disabled");
            return result;
        }
        String ffmpeg = effectiveFfmpegPath();
        if (!ffmpegAvailable(ffmpeg)) {
            log.warn("video compression skipped, ffmpeg unavailable path={} ffmpeg={}", inputPath, ffmpeg);
            result.setMessage("ffmpeg not available, keep original");
            return result;
        }
        int workerLimit = effectiveVideoWorkerCount();
        if (runningVideoJobs.incrementAndGet() > workerLimit) {
            runningVideoJobs.decrementAndGet();
            result.setMessage("video workers busy, keep original");
            return result;
        }
        try {
            int maxEdge = effectiveVideoMaxLongEdge();
            int crf = effectiveVideoCrf();
            String base = stripExtension(inputPath.getFileName().toString());
            Path temp = inputPath.resolveSibling(base + ".compressing.mp4");
            String scale = "scale='min(" + maxEdge + ",iw)':'min(" + maxEdge + ",ih)':force_original_aspect_ratio=decrease:force_divisible_by=2";

            List<String> cmd = new ArrayList<String>();
            cmd.add(ffmpeg);
            cmd.add("-y");
            cmd.add("-i");
            cmd.add(inputPath.toString());
            cmd.add("-vf");
            cmd.add(scale);
            cmd.add("-c:v");
            cmd.add("libx264");
            cmd.add("-crf");
            cmd.add(String.valueOf(crf));
            cmd.add("-preset");
            cmd.add("veryfast");
            cmd.add("-c:a");
            cmd.add("aac");
            cmd.add("-b:a");
            cmd.add("128k");
            cmd.add("-movflags");
            cmd.add("+faststart");
            cmd.add(temp.toString());
            boolean ok = runFfmpeg(cmd, 30);

            Path thumb = inputPath.resolveSibling("thumb_" + base + ".jpg");
            List<String> thumbCmd = new ArrayList<String>();
            thumbCmd.add(ffmpeg);
            thumbCmd.add("-y");
            thumbCmd.add("-i");
            thumbCmd.add(inputPath.toString());
            thumbCmd.add("-vf");
            thumbCmd.add("thumbnail,scale='min(" + effectiveThumbnailLongEdge() + ",iw)':'min(" + effectiveThumbnailLongEdge() + ",ih)':force_original_aspect_ratio=decrease");
            thumbCmd.add("-frames:v");
            thumbCmd.add("1");
            thumbCmd.add(thumb.toString());
            if (runFfmpeg(thumbCmd, 2) && Files.exists(thumb)) {
                result.setThumbnailPath(thumb.toString());
            }

            if (!ok || !Files.exists(temp)) {
                deleteQuietly(temp);
                result.setMessage("ffmpeg failed, keep original");
                result.setFinalSize(sizeOf(inputPath));
                return result;
            }
            long compressedSize = Files.size(temp);
            if (compressedSize > 0 && compressedSize < result.getOriginalSize()) {
                backupOriginal(inputPath);
                Files.move(temp, inputPath, StandardCopyOption.REPLACE_EXISTING);
                result.setCompressed(true);
                result.setFinalSize(compressedSize);
                result.setMessage("video compressed");
            } else {
                deleteQuietly(temp);
                result.setFinalSize(sizeOf(inputPath));
                result.setMessage("compressed file is not smaller, keep original");
            }
            log.info("video compression result path={} original={} final={} compressed={} thumb={} costMs={} message={}",
                    inputPath, result.getOriginalSize(), result.getFinalSize(), result.isCompressed(),
                    result.getThumbnailPath(), System.currentTimeMillis() - started, result.getMessage());
            return result;
        } catch (Exception e) {
            log.warn("video compression failed path={} message={}", inputPath, e.getMessage());
            result.setMessage("video compression failed, keep original: " + e.getMessage());
            result.setFinalSize(sizeOf(inputPath));
            return result;
        } finally {
            runningVideoJobs.decrementAndGet();
        }
    }

    private boolean ffmpegAvailable(String ffmpeg) {
        if (!StringUtils.hasText(ffmpeg)) {
            return false;
        }
        try {
            List<String> cmd = new ArrayList<String>();
            cmd.add(ffmpeg);
            cmd.add("-version");
            Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
            boolean finished = process.waitFor(10, TimeUnit.SECONDS);
            if (!finished) {
                process.destroyForcibly();
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            return false;
        }
    }

    private boolean runFfmpeg(List<String> command, int timeoutMinutes) {
        Process process = null;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true).start();
            // Drain the output so a full buffer does not block the process
            try (java.io.InputStream in = process.getInputStream()) {
                byte[] buffer = new byte[4096];
                while (in.read(buffer) != -1) {
                    // Discard ffmpeg log output
                }
            }
            boolean finished = process.waitFor(timeoutMinutes, TimeUnit.MINUTES);
            if (!finished) {
                process.destroyForcibly();
                log.warn("ffmpeg timeout after {} minutes, command={}", timeoutMinutes, command);
                return false;
            }
            return process.exitValue() == 0;
        } catch (Exception e) {
            if (process != null) {
                process.destroyForcibly();
            }
            log.warn("ffmpeg execution failed message={}", e.getMessage());
            return false;
        }
    }

    private void backupOriginal(Path inputPath) {
        int keepDays = effectiveKeepOriginalBackupDays();
        if (keepDays <= 0) {
            return;
        }
        try {
            String day = LocalDate.now().toString();
            Path backupDir = Paths.get(uploadDir, ".orig-backup", day).toAbsolutePath();
            Files.createDirectories(backupDir);
            Path target = backupDir.resolve(inputPath.getFileName().toString());
            Files.copy(inputPath, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (Exception e) {
            log.warn("backup original failed path={} message={}", inputPath, e.getMessage());
        }
    }

    /** Daily cleanup of original image/video backups past the retention period. A retention of 0 days keeps no backups (nothing to clean). */
    @Scheduled(fixedDelay = 24L * 60L * 60L * 1000L, initialDelay = 60L * 1000L)
    public void cleanupExpiredBackups() {
        int keepDays = effectiveKeepOriginalBackupDays();
        Path backupRoot = Paths.get(uploadDir, ".orig-backup").toAbsolutePath();
        if (!Files.isDirectory(backupRoot)) {
            return;
        }
        long cutoff = System.currentTimeMillis() - Math.max(0, keepDays) * 24L * 60L * 60L * 1000L;
        try {
            Files.walk(backupRoot)
                    .filter(Files::isRegularFile)
                    .forEach(path -> {
                        try {
                            if (Files.getLastModifiedTime(path).toMillis() < cutoff) {
                                Files.deleteIfExists(path);
                            }
                        } catch (IOException ignored) {
                        }
                    });
        } catch (IOException e) {
            log.warn("cleanup backups failed message={}", e.getMessage());
        }
    }

    private boolean isVideo(String mimeType, Path path) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("video/")) {
            return true;
        }
        String ext = extension(path);
        return "mp4".equals(ext) || "mov".equals(ext) || "m4v".equals(ext) || "webm".equals(ext)
                || "avi".equals(ext) || "mkv".equals(ext) || "flv".equals(ext) || "3gp".equals(ext);
    }

    private CompressionResult baseResult(Path inputPath, String mimeType) {
        CompressionResult result = new CompressionResult();
        result.setOriginalPath(inputPath.toString());
        result.setFinalPath(inputPath.toString());
        result.setOriginalSize(sizeOf(inputPath));
        result.setFinalSize(result.getOriginalSize());
        result.setCompressed(false);
        result.setMediaType(StringUtils.hasText(mimeType) ? mimeType : "application/octet-stream");
        result.setMessage("");
        return result;
    }

    private boolean isImage(String mimeType, Path path) {
        if (mimeType != null && mimeType.toLowerCase(Locale.ROOT).startsWith("image/")) return true;
        String ext = extension(path);
        return "jpg".equals(ext) || "jpeg".equals(ext) || "png".equals(ext) || "gif".equals(ext)
                || "webp".equals(ext) || "bmp".equals(ext);
    }

    private String extension(Path path) {
        return extension(path.getFileName().toString());
    }

    private String extension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(dot + 1).toLowerCase(Locale.ROOT) : "";
    }

    private String stripExtension(String name) {
        int dot = name.lastIndexOf('.');
        return dot >= 0 ? name.substring(0, dot) : name;
    }

    private long sizeOf(Path path) {
        try {
            return Files.exists(path) ? Files.size(path) : 0L;
        } catch (IOException e) {
            return 0L;
        }
    }

    private void deleteQuietly(Path path) {
        try {
            if (path != null) Files.deleteIfExists(path);
        } catch (IOException ignored) {
        }
    }
}
