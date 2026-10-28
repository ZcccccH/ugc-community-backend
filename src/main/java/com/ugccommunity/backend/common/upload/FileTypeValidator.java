package com.ugccommunity.backend.common.upload;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Upload file type validation with layered defenses:
 * 1) Extension allowlist
 * 2) MIME type check
 * 3) File signature (magic bytes) check to stop disguised extensions
 * 4) File name sanitizing (path traversal, null bytes, control characters)
 * 5) Size limits
 * Executable / script extensions are always rejected (denylist as a last line of defense).
 */
public final class FileTypeValidator {
    private static final Logger log = LoggerFactory.getLogger(FileTypeValidator.class);
    private static final int MAGIC_HEAD_BYTES = 16;

    /** Upload purpose. */
    public enum Purpose {
        IMAGE, VIDEO, INSTALLER, GENERAL
    }

    /** Dangerous extension denylist (always rejected even if other checks pass). */
    private static final Set<String> DANGEROUS_EXT = new HashSet<String>(Arrays.asList(
            "jsp", "jspx", "jspf", "war", "class",
            "php", "php3", "php4", "php5", "php7", "phtml", "phar",
            "asp", "aspx", "ashx", "asmx", "cer",
            "py", "pyc", "rb", "pl", "cgi",
            "sh", "bash", "zsh", "ksh", "csh", "fish",
            "bat", "cmd", "ps1", "vbs", "vbe", "wsf", "wsh", "hta",
            "exe", "msi", "scr", "com", "dll", "drv", "sys",
            "html", "htm", "xhtml", "shtml", "svg",
            "swf", "fla",
            "lnk", "url", "desktop"
    ));

    /** Allowed extensions + MIME types per purpose. */
    private static final Map<Purpose, Set<String>> ALLOWED_EXT = new HashMap<Purpose, Set<String>>();
    private static final Map<Purpose, Set<String>> ALLOWED_MIME = new HashMap<Purpose, Set<String>>();
    private static final Map<Purpose, Long> MAX_SIZE = new HashMap<Purpose, Long>();

    static {
        ALLOWED_EXT.put(Purpose.IMAGE, new HashSet<String>(Arrays.asList("jpg", "jpeg", "png", "gif", "webp", "bmp")));
        ALLOWED_MIME.put(Purpose.IMAGE, new HashSet<String>(Arrays.asList("image/jpeg", "image/png", "image/gif", "image/webp", "image/bmp")));
        MAX_SIZE.put(Purpose.IMAGE, 20L * 1024 * 1024);

        ALLOWED_EXT.put(Purpose.VIDEO, new HashSet<String>(Arrays.asList("mp4", "mov", "m4v", "webm", "mkv", "avi", "3gp", "flv")));
        ALLOWED_MIME.put(Purpose.VIDEO, new HashSet<String>(Arrays.asList("video/mp4", "video/quicktime", "video/x-m4v", "video/webm", "video/x-matroska", "video/x-msvideo", "video/3gpp", "video/x-flv")));
        MAX_SIZE.put(Purpose.VIDEO, 200L * 1024 * 1024);

        // App installers: apk (Android) / ipa (iOS) / hap, app (HarmonyOS) / dmg, pkg (macOS) / exe, msi (Windows); the backend only accepts the three mobile types
        ALLOWED_EXT.put(Purpose.INSTALLER, new HashSet<String>(Arrays.asList("apk", "ipa", "hap", "app")));
        ALLOWED_MIME.put(Purpose.INSTALLER, new HashSet<String>(Arrays.asList(
                "application/vnd.android.package-archive", "application/octet-stream",
                "application/iphone", "application/x-itunes-ipa", "application/zip"
        )));
        MAX_SIZE.put(Purpose.INSTALLER, 500L * 1024 * 1024);

        // Generic upload = images + videos
        Set<String> generalExt = new HashSet<String>();
        generalExt.addAll(ALLOWED_EXT.get(Purpose.IMAGE));
        generalExt.addAll(ALLOWED_EXT.get(Purpose.VIDEO));
        ALLOWED_EXT.put(Purpose.GENERAL, generalExt);
        Set<String> generalMime = new HashSet<String>();
        generalMime.addAll(ALLOWED_MIME.get(Purpose.IMAGE));
        generalMime.addAll(ALLOWED_MIME.get(Purpose.VIDEO));
        ALLOWED_MIME.put(Purpose.GENERAL, generalMime);
        MAX_SIZE.put(Purpose.GENERAL, 200L * 1024 * 1024);
    }

    private FileTypeValidator() {}

    public static final class ValidationResult {
        private final String storageExtension;

        private ValidationResult(String storageExtension) {
            this.storageExtension = storageExtension;
        }

        public String getStorageExtension() {
            return storageExtension;
        }

        public String getStorageExtensionWithDot() {
            return storageExtension == null || storageExtension.length() == 0 ? "" : "." + storageExtension;
        }
    }

    /** Validate an uploaded file; throws BusinessException on violation. */
    public static ValidationResult validate(MultipartFile file, Purpose purpose) {
        if (file == null || file.isEmpty()) {
            throw rejected(file, purpose, "", "请选择要上传的文件");
        }
        // 1) Sanitize the file name
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.length() == 0) {
            throw rejected(file, purpose, "", "文件名无效");
        }
        if (originalName.contains("..") || originalName.contains("/") || originalName.contains("\\")
                || originalName.indexOf('\0') >= 0) {
            throw rejected(file, purpose, "", "文件名包含非法字符");
        }
        for (int i = 0; i < originalName.length(); i++) {
            if (originalName.charAt(i) < 0x20) {
                throw rejected(file, purpose, "", "文件名包含控制字符");
            }
        }

        String ext = extOf(originalName).toLowerCase(Locale.ROOT);
        if (ext.length() == 0) {
            throw rejected(file, purpose, ext, "文件必须包含扩展名");
        }

        // 2) Dangerous extension denylist (fallback)
        if (DANGEROUS_EXT.contains(ext)) {
            throw rejected(file, purpose, ext, "禁止上传该类型文件: ." + ext);
        }

        // 3) Extension allowlist
        Set<String> allowedExt = ALLOWED_EXT.get(purpose);
        if (allowedExt == null || !allowedExt.contains(ext)) {
            throw rejected(file, purpose, ext, "不允许的文件类型: ." + ext);
        }

        // 4) MIME type check
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(Locale.ROOT);
        Set<String> allowedMime = ALLOWED_MIME.get(purpose);
        if (allowedMime != null && !allowedMime.isEmpty()
                && !allowedMime.contains(contentType)
                && !contentType.equals("application/octet-stream")) {
            throw rejected(file, purpose, ext, "MIME 类型与扩展名不匹配: " + contentType);
        }

        // 5) Size limit
        Long max = MAX_SIZE.get(purpose);
        if (max != null && file.getSize() > max) {
            throw rejected(file, purpose, ext, "文件超过大小限制: " + (max / 1024 / 1024) + "MB");
        }

        // 6) File signature (magic bytes) check: blocks disguised extensions
        if (!matchesMagicBytes(file, ext)) {
            String detectedExt = detectMagicExtension(file);
            if (canNormalizeSafeImageExtension(purpose, ext, detectedExt)) {
                log.warn("upload image extension normalized purpose={} originalName={} contentType={} size={} originalExt={} storageExt={} headHex={}",
                        purpose,
                        safeForLog(file.getOriginalFilename()),
                        safeForLog(file.getContentType()),
                        file.getSize(),
                        safeForLog(ext),
                        safeForLog(detectedExt),
                        toHex(readHead(file, MAGIC_HEAD_BYTES)));
                return new ValidationResult(detectedExt);
            }
            throw rejected(file, purpose, ext, "文件内容与扩展名不一致，疑似伪装");
        }
        return new ValidationResult(ext);
    }

    private static BusinessException rejected(MultipartFile file, Purpose purpose, String ext, String reason) {
        log.warn("upload rejected reason={} purpose={} originalName={} contentType={} size={} ext={} headHex={}",
                reason,
                purpose,
                safeForLog(file == null ? null : file.getOriginalFilename()),
                safeForLog(file == null ? null : file.getContentType()),
                file == null ? null : file.getSize(),
                safeForLog(ext),
                toHex(readHead(file, MAGIC_HEAD_BYTES)));
        return new BusinessException(ResultCode.PARAM_ERROR, reason);
    }

    private static String extOf(String name) {
        int dot = name.lastIndexOf('.');
        return dot < 0 ? "" : name.substring(dot + 1);
    }

    private static boolean canNormalizeSafeImageExtension(Purpose purpose, String ext, String detectedExt) {
        if (!isImageExtension(ext) || !isImageExtension(detectedExt)) return false;
        Set<String> allowedExt = ALLOWED_EXT.get(purpose);
        return allowedExt != null && allowedExt.contains(detectedExt);
    }

    private static boolean isImageExtension(String ext) {
        return ext != null && ALLOWED_EXT.get(Purpose.IMAGE).contains(ext);
    }

    private static String detectMagicExtension(MultipartFile file) {
        byte[] head = readHead(file, MAGIC_HEAD_BYTES);
        if (head == null || head.length == 0) return "";
        if (startsWith(head, 0xFF, 0xD8, 0xFF)) return "jpg";
        if (startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) return "png";
        if (startsWith(head, 0x47, 0x49, 0x46, 0x38)) return "gif";
        if (startsWith(head, 0x52, 0x49, 0x46, 0x46) && head.length >= 12
                && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50) return "webp";
        if (startsWith(head, 0x42, 0x4D)) return "bmp";
        return "";
    }

    /** Match the file signature against the first 16 bytes. Unknown signatures fall back to the extension check (e.g. txt has no header). */
    private static boolean matchesMagicBytes(MultipartFile file, String ext) {
        byte[] head = readHead(file, 16);
        if (head == null || head.length == 0) return false;
        switch (ext) {
            case "jpg":
            case "jpeg":
                return startsWith(head, 0xFF, 0xD8, 0xFF);
            case "png":
                return startsWith(head, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A);
            case "gif":
                return startsWith(head, 0x47, 0x49, 0x46, 0x38);
            case "webp":
                return startsWith(head, 0x52, 0x49, 0x46, 0x46) && head.length >= 12
                        && head[8] == 0x57 && head[9] == 0x45 && head[10] == 0x42 && head[11] == 0x50;
            case "bmp":
                return startsWith(head, 0x42, 0x4D);
            case "mp4":
            case "m4v":
            case "mov":
                // 'ftyp' at offset 4
                return head.length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            case "webm":
            case "mkv":
                return startsWith(head, 0x1A, 0x45, 0xDF, 0xA3);
            case "avi":
                return startsWith(head, 0x52, 0x49, 0x46, 0x46) && head.length >= 12
                        && head[8] == 'A' && head[9] == 'V' && head[10] == 'I' && head[11] == ' ';
            case "flv":
                return startsWith(head, 0x46, 0x4C, 0x56);
            case "3gp":
                return head.length >= 8 && head[4] == 'f' && head[5] == 't' && head[6] == 'y' && head[7] == 'p';
            case "apk":
            case "ipa":
            case "hap":
                // ZIP container (PK\x03\x04, or empty ZIP PK\x05\x06)
                return startsWith(head, 0x50, 0x4B, 0x03, 0x04) || startsWith(head, 0x50, 0x4B, 0x05, 0x06);
            case "app":
                // .app formats differ by platform; only the ZIP container form is allowed here (some .app bundles)
                return startsWith(head, 0x50, 0x4B, 0x03, 0x04);
            default:
                return true;
        }
    }

    private static byte[] readHead(MultipartFile file, int n) {
        if (file == null) return null;
        InputStream in = null;
        try {
            in = file.getInputStream();
            byte[] buf = new byte[n];
            int read = 0;
            int r;
            while (read < n && (r = in.read(buf, read, n - read)) > 0) {
                read += r;
            }
            if (read < n) {
                byte[] trim = new byte[read];
                System.arraycopy(buf, 0, trim, 0, read);
                return trim;
            }
            return buf;
        } catch (IOException e) {
            return null;
        } finally {
            if (in != null) try { in.close(); } catch (IOException ignored) {}
        }
    }

    private static String toHex(byte[] bytes) {
        if (bytes == null || bytes.length == 0) return "";
        char[] hex = "0123456789abcdef".toCharArray();
        StringBuilder sb = new StringBuilder(bytes.length * 2);
        for (byte b : bytes) {
            int value = b & 0xFF;
            sb.append(hex[value >>> 4]);
            sb.append(hex[value & 0x0F]);
        }
        return sb.toString();
    }

    private static String safeForLog(String value) {
        if (value == null) return "";
        return value.replace('\r', ' ').replace('\n', ' ');
    }

    private static boolean startsWith(byte[] head, int... bytes) {
        if (head.length < bytes.length) return false;
        for (int i = 0; i < bytes.length; i++) {
            if ((head[i] & 0xFF) != (bytes[i] & 0xFF)) return false;
        }
        return true;
    }

    public static List<String> allowedExtensions(Purpose purpose) {
        Set<String> set = ALLOWED_EXT.get(purpose);
        return set == null ? Collections.<String>emptyList() : Collections.unmodifiableList(new java.util.ArrayList<String>(set));
    }
}
