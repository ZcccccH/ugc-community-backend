package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.upload.LocalUploadResolver;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;

class WorkServiceLocalUploadsTest {
    @TempDir Path uploads;

    @Test
    void deadQiniuUrlsInAListAreSwappedForLocalCopies() throws Exception {
        Files.createDirectories(uploads.resolve("2026-06-20"));
        Files.write(uploads.resolve("2026-06-20/a.jpg"), new byte[]{1});
        WorkService service = mock(WorkService.class, org.mockito.Mockito.CALLS_REAL_METHODS);
        service.setLocalUploads(new LocalUploadResolver(uploads.toString(), "/uploads/"));
        Method m = WorkService.class.getDeclaredMethod("localList", String.class);
        m.setAccessible(true);
        // 本地有的换成本站地址；本地没有的、本来就是本站的原样保留
        assertEquals("/uploads/2026-06-20/a.jpg,http://x.clouddn.com/uploads/2026-06-20/missing.jpg,/uploads/seed/b.jpg",
                m.invoke(service, "http://x.clouddn.com/uploads/2026-06-20/a.jpg,http://x.clouddn.com/uploads/2026-06-20/missing.jpg,/uploads/seed/b.jpg"));
    }
}
