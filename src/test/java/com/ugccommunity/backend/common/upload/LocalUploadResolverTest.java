package com.ugccommunity.backend.common.upload;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class LocalUploadResolverTest {
    @TempDir Path dir;

    private LocalUploadResolver resolver() throws Exception {
        Files.createDirectories(dir.resolve("2026-10-12"));
        Files.write(dir.resolve("2026-10-12/map.png"), new byte[]{1, 2, 3});
        return new LocalUploadResolver(dir.toString(), "/uploads/");
    }

    @Test
    void externalUrl_isServedLocally_whenTheFileIsOnThisServer() throws Exception {
        LocalUploadResolver r = resolver();
        assertEquals("/uploads/2026-10-12/map.png", r.preferLocal("http://bucket.example-cdn.com/uploads/2026-10-12/map.png"));
        assertEquals("/uploads/2026-10-12/map.png", r.preferLocal("https://bucket.example-cdn.com/uploads/2026-10-12/map.png?e=1"));
        assertEquals("/uploads/2026-10-12/map.png", r.preferLocal("https://bucket.example-cdn.com/2026-10-12/map.png"), "外部存储没有前缀时");
    }

    @Test
    void otherUrls_areLeftAlone() throws Exception {
        LocalUploadResolver r = resolver();
        String missing = "https://bucket.example-cdn.com/uploads/2026-10-12/other.png";
        assertEquals(missing, r.preferLocal(missing), "本地没有这个文件");
        assertEquals("/uploads/2026-10-12/map.png", r.preferLocal("/uploads/2026-10-12/map.png"), "已经是本站地址");
        assertEquals("", r.preferLocal(""));
        assertNull(r.preferLocal(null));
        String traversal = "https://bucket.example-cdn.com/uploads/../../etc/passwd";
        assertEquals(traversal, r.preferLocal(traversal));
        String directory = "https://bucket.example-cdn.com/uploads/2026-10-12";
        assertEquals(directory, r.preferLocal(directory), "目录不是文件");
        assertEquals("http://[bad", r.preferLocal("http://[bad"));
    }
}
