package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxMapRequest;
import com.ugccommunity.backend.entity.SandboxMap;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxMapMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SandboxMapServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxMapMapper mapMapper;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    private SandboxMapService service;

    private final Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
    private final Map<Long, SandboxMap> maps = new LinkedHashMap<Long, SandboxMap>();
    private long seq = 100;
    /** 模拟按 slot 查询：服务每次 create 前后都用 selectList 找同一位置的地图 */
    private String slotFilter;
    private boolean concurrentEdit;

    @BeforeEach
    void setUp() {
        service = new SandboxMapService(mapMapper, worldviewMapper) {
            @Override public List<SandboxMap> listMaps(Long userId, Long worldviewId) {
                List<SandboxMap> out = new ArrayList<SandboxMap>();
                for (SandboxMap m : maps.values()) if (m.getUserId().equals(userId) && (worldviewId == null || worldviewId.equals(m.getWorldviewId()))) out.add(m);
                return out;
            }
        };
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> worlds.get((Long) inv.getArgument(0)));
        lenient().when(mapMapper.selectById(anyLong())).thenAnswer(inv -> maps.get((Long) inv.getArgument(0)));
        // bySlot 的查询条件不解析：用即将插入的 slot 过滤（见 create 辅助方法）
        lenient().when(mapMapper.selectList(any())).thenAnswer(inv -> {
            List<SandboxMap> out = new ArrayList<SandboxMap>();
            for (SandboxMap m : maps.values()) if (m.getUserId() == USER && m.getSlot().equals(slotFilter)) out.add(m);
            return out;
        });
        lenient().when(mapMapper.insert(any(SandboxMap.class))).thenAnswer(inv -> {
            SandboxMap m = inv.getArgument(0);
            m.setId(++seq);
            maps.put(m.getId(), m);
            return 1;
        });
        lenient().when(mapMapper.update(any(SandboxMap.class), any())).thenAnswer(inv -> {
            if (concurrentEdit) return 0;
            SandboxMap m = inv.getArgument(0);
            maps.put(m.getId(), m);
            return 1;
        });
        lenient().when(mapMapper.deleteById(anyLong())).thenAnswer(inv -> maps.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        world(1, USER);
        world(2, USER);
        world(9, OTHER_USER);
    }

    @Test
    void worldMap_isCreatedOncePerWorldview() {
        SandboxMap a = create(1, null, null, "世界地图");
        SandboxMap again = create(1, null, null, "又建一次");
        assertEquals(a.getId(), again.getId());
        assertEquals("世界地图", again.getName());
        assertEquals(SandboxMap.ROOT_SLOT, a.getSlot());
        assertNull(a.getParentMapId());
        assertEquals(1, a.getVersion());
        assertEquals(1, maps.size());
    }

    @Test
    void blankName_fallsBackToTheWorldviewName() {
        assertEquals("世界1", create(1, null, null, "  ").getName());
    }

    @Test
    void regionMap_isCreatedOncePerRegionAndLinksToItsParent() {
        SandboxMap world = create(1, null, null, "世界地图");
        SandboxMap north = create(1, world.getId(), "r-north", "北境");
        SandboxMap northAgain = create(1, world.getId(), "r-north", "北境（重复）");
        SandboxMap south = create(1, world.getId(), "r-south", "南岛");
        assertEquals(north.getId(), northAgain.getId());
        assertEquals(world.getId(), north.getParentMapId());
        assertEquals("r-north", north.getParentRegionKey());
        assertTrue(!north.getId().equals(south.getId()));
        assertEquals(3, maps.size());
    }

    @Test
    void create_rejectsForeignWorldForeignParentCrossWorldParentAndMissingRegionKey() {
        SandboxMap world = create(1, null, null, "世界地图");
        assertThrows(BusinessException.class, () -> create(9, null, null, "越权"));
        assertThrows(BusinessException.class, () -> create(404, null, null, "不存在"));
        assertThrows(BusinessException.class, () -> create(2, world.getId(), "r1", "跨世界"));
        assertThrows(BusinessException.class, () -> create(1, world.getId(), " ", "缺区域"));
        SandboxMap foreign = new SandboxMap();
        foreign.setId(500L);
        foreign.setUserId(OTHER_USER);
        foreign.setWorldviewId(1L);
        foreign.setSlot(SandboxMap.ROOT_SLOT);
        maps.put(500L, foreign);
        assertThrows(BusinessException.class, () -> create(1, 500L, "r1", "别人的上级地图"));
        assertThrows(BusinessException.class, () -> service.createMap(null, request(1, null, null, "未登录")));
    }

    @Test
    void update_savesTerrainOverlayAndViewIndependently_andBumpsVersion() {
        SandboxMap m = create(1, null, null, "世界地图");
        SandboxMapRequest terrain = new SandboxMapRequest();
        terrain.setTerrain("v1;4x4;16*0");
        terrain.setVersion(1);
        SandboxMap u = service.updateMap(USER, m.getId(), terrain);
        assertEquals("v1;4x4;16*0", u.getTerrain());
        assertEquals(2, u.getVersion());

        SandboxMapRequest overlay = new SandboxMapRequest();
        overlay.setOverlay("{\"regions\":[]}");
        overlay.setVersion(2);
        u = service.updateMap(USER, m.getId(), overlay);
        assertEquals("v1;4x4;16*0", u.getTerrain());
        assertEquals("{\"regions\":[]}", u.getOverlay());
        assertEquals("世界地图", u.getName());
        assertEquals(3, u.getVersion());
    }

    @Test
    void background_onADeadExternalDomain_isReturnedAsTheLocalCopy_whenTheFileIsOnThisServer(@org.junit.jupiter.api.io.TempDir java.nio.file.Path uploads) throws Exception {
        java.nio.file.Files.createDirectories(uploads.resolve("2026-10-12"));
        java.nio.file.Files.write(uploads.resolve("2026-10-12/bg.png"), new byte[]{1});
        service.setLocalUploads(new com.ugccommunity.backend.common.upload.LocalUploadResolver(uploads.toString(), "/uploads/"));
        SandboxMap m = create(1, null, null, "世界地图");
        SandboxMapRequest background = new SandboxMapRequest();
        background.setBackgroundUrl("http://bucket.example-cdn.com/uploads/2026-10-12/bg.png");
        background.setVersion(1);
        assertEquals("/uploads/2026-10-12/bg.png", service.updateMap(USER, m.getId(), background).getBackgroundUrl());
        assertEquals("/uploads/2026-10-12/bg.png", service.getMap(USER, m.getId()).getBackgroundUrl());
        assertEquals("/uploads/2026-10-12/bg.png", service.listMaps(USER, 1L).get(0).getBackgroundUrl());

        SandboxMapRequest elsewhere = new SandboxMapRequest();
        elsewhere.setBackgroundUrl("http://bucket.example-cdn.com/uploads/2026-10-12/not-here.png");
        elsewhere.setVersion(2);
        assertEquals("http://bucket.example-cdn.com/uploads/2026-10-12/not-here.png", service.updateMap(USER, m.getId(), elsewhere).getBackgroundUrl(), "本地没有的文件保持原地址");
    }

    @Test
    void update_rejectsStaleVersionConcurrentEditAndOversizedData() {
        SandboxMap m = create(1, null, null, "世界地图");
        SandboxMapRequest first = new SandboxMapRequest();
        first.setTerrain("new");
        first.setVersion(1);
        service.updateMap(USER, m.getId(), first);
        SandboxMapRequest late = new SandboxMapRequest();
        late.setTerrain("old");
        late.setVersion(1);
        assertThrows(BusinessException.class, () -> service.updateMap(USER, m.getId(), late));
        assertEquals("new", maps.get(m.getId()).getTerrain());

        concurrentEdit = true;
        SandboxMapRequest racing = new SandboxMapRequest();
        racing.setTerrain("racing");
        racing.setVersion(2);
        assertThrows(BusinessException.class, () -> service.updateMap(USER, m.getId(), racing));
        concurrentEdit = false;

        StringBuilder huge = new StringBuilder();
        for (int i = 0; i < 600_001; i++) huge.append('1');
        SandboxMapRequest big = new SandboxMapRequest();
        big.setTerrain(huge.toString());
        assertThrows(BusinessException.class, () -> service.updateMap(USER, m.getId(), big));
    }

    @Test
    void delete_removesTheMapAndEveryMapBelowIt_only() {
        SandboxMap world = create(1, null, null, "世界地图");
        SandboxMap north = create(1, world.getId(), "r-north", "北境");
        SandboxMap town = create(1, north.getId(), "r-town", "雾港镇");
        SandboxMap south = create(1, world.getId(), "r-south", "南岛");
        SandboxMap other = create(2, null, null, "另一个世界");
        service.deleteMap(USER, north.getId());
        assertTrue(maps.containsKey(world.getId()));
        assertTrue(maps.containsKey(south.getId()));
        assertTrue(maps.containsKey(other.getId()));
        assertTrue(!maps.containsKey(north.getId()));
        assertTrue(!maps.containsKey(town.getId()));
    }

    @Test
    void read_update_and_delete_rejectOtherUsersMap() {
        SandboxMap foreign = new SandboxMap();
        foreign.setId(500L);
        foreign.setUserId(OTHER_USER);
        foreign.setWorldviewId(9L);
        foreign.setSlot(SandboxMap.ROOT_SLOT);
        foreign.setVersion(1);
        maps.put(500L, foreign);
        assertThrows(BusinessException.class, () -> service.getMap(USER, 500L));
        assertThrows(BusinessException.class, () -> service.updateMap(USER, 500L, new SandboxMapRequest()));
        assertThrows(BusinessException.class, () -> service.deleteMap(USER, 500L));
        assertThrows(BusinessException.class, () -> service.getMap(USER, 999L));
        assertEquals(1, maps.size());
    }

    private SandboxMap create(long worldviewId, Long parentMapId, String regionKey, String name) {
        slotFilter = parentMapId == null ? SandboxMap.ROOT_SLOT : parentMapId + ":" + (regionKey == null ? "" : regionKey.trim());
        return service.createMap(USER, request(worldviewId, parentMapId, regionKey, name));
    }

    private void world(long id, long userId) {
        SandboxWorldview w = new SandboxWorldview();
        w.setId(id);
        w.setUserId(userId);
        w.setName("世界" + id);
        worlds.put(id, w);
    }

    private static SandboxMapRequest request(long worldviewId, Long parentMapId, String regionKey, String name) {
        SandboxMapRequest r = new SandboxMapRequest();
        r.setWorldviewId(worldviewId);
        r.setParentMapId(parentMapId);
        r.setParentRegionKey(regionKey);
        r.setName(name);
        return r;
    }
}
