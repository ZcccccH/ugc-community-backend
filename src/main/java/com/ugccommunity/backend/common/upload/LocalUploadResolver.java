package com.ugccommunity.backend.common.upload;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * A copy of every uploaded file is always kept in the local uploads directory (it is not deleted after uploading to Qiniu).
 * If the external storage domain stops working, the same file can still be served from this site's /uploads/;
 * this class rewrites external URLs to local URLs, provided the file really exists locally.
 */
@Component
public class LocalUploadResolver {
    private final Path baseDir;
    private final String urlPrefix;

    public LocalUploadResolver(@Value("${app.upload.dir:uploads}") String uploadDir,
                               @Value("${app.upload.url-prefix:/uploads/}") String urlPrefix) {
        this.baseDir = Paths.get(uploadDir).toAbsolutePath().normalize();
        String prefix = urlPrefix == null || urlPrefix.trim().isEmpty() ? "/uploads/" : urlPrefix.trim();
        if (!prefix.startsWith("/")) prefix = "/" + prefix;
        if (!prefix.endsWith("/")) prefix = prefix + "/";
        this.urlPrefix = prefix;
    }

    /** Returns this site's relative URL when the file behind an external URL exists locally; otherwise returns the input unchanged */
    public String preferLocal(String url) {
        if (url == null) return null;
        String trimmed = url.trim();
        if (!trimmed.startsWith("http://") && !trimmed.startsWith("https://")) return url;
        String path;
        try {
            path = URI.create(trimmed).getPath();
        } catch (IllegalArgumentException e) {
            return url;
        }
        if (path == null || path.isEmpty()) return url;
        // Qiniu object keys are "prefix/date/file name" and the prefix is usually "uploads"; without a prefix the path is just "date/file name"
        String relative = path.startsWith(urlPrefix) ? path.substring(urlPrefix.length()) : path.replaceFirst("^/+", "");
        if (relative.isEmpty() || relative.contains("..") || relative.contains("\\")) return url;
        Path file = baseDir.resolve(relative).normalize();
        if (!file.startsWith(baseDir) || !Files.isRegularFile(file)) return url;
        return urlPrefix + relative;
    }
}
