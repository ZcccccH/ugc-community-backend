package com.ugccommunity.backend.service;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class QiniuStorageServiceTest {

    @Test
    void publicUrlReachable_probesEachDomainOnce_andRejectsUnusableUrls() {
        final List<String> probed = new ArrayList<String>();
        QiniuStorageService service = new QiniuStorageService(null) {
            @Override
            boolean probe(String url) {
                probed.add(url);
                return url.startsWith("https://good.example/");
            }
        };
        assertTrue(service.publicUrlReachable("https://good.example/uploads/a.png"));
        assertTrue(service.publicUrlReachable("https://good.example/uploads/b.png"));
        assertFalse(service.publicUrlReachable("https://gone.example/uploads/a.png"));
        assertFalse(service.publicUrlReachable("https://gone.example/uploads/b.png"));
        assertEquals(2, probed.size(), "同一个域名在缓存时间内只探测一次");
        assertFalse(service.publicUrlReachable("not a url"));
        assertFalse(service.publicUrlReachable("/uploads/a.png"));
        assertEquals(2, probed.size());
    }
}
