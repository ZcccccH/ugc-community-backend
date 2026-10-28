package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxGraphLayoutRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxGraphLayout;
import com.ugccommunity.backend.entity.SandboxOrganization;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxGraphLayoutMapper;
import com.ugccommunity.backend.mapper.SandboxOrganizationMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

/**
 * Mapper 用内存表模拟。LambdaQueryWrapper 的条件在 Mock 里不生效，
 * 所以 delete 只能按「当前正在操作的 scope / 角色」在 stub 里自己过滤，见 pendingDelete。
 */
@ExtendWith(MockitoExtension.class)
class SandboxGraphLayoutServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxGraphLayoutMapper layoutMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    @Mock private SandboxOrganizationMapper organizationMapper;
    private SandboxGraphLayoutService service;

    private final Map<Long, SandboxCharacter> characters = new LinkedHashMap<Long, SandboxCharacter>();
    private final Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
    private final Map<Long, SandboxOrganization> organizations = new LinkedHashMap<Long, SandboxOrganization>();
    private final List<SandboxGraphLayout> rows = new ArrayList<SandboxGraphLayout>();
    /** 下一次 delete 要删的行：由测试在调用前设置（模拟 WHERE 条件） */
    private java.util.function.Predicate<SandboxGraphLayout> pendingDelete = r -> false;
    private long seq = 1;

    @BeforeEach
    void setUp() {
        service = new SandboxGraphLayoutService(layoutMapper, characterMapper, worldviewMapper, organizationMapper);
        lenient().when(characterMapper.selectList(any())).thenAnswer(inv -> characters.values().stream()
                .filter(c -> c.getUserId() == USER).collect(Collectors.toList()));
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> worlds.get((Long) inv.getArgument(0)));
        lenient().when(organizationMapper.selectById(anyLong())).thenAnswer(inv -> organizations.get((Long) inv.getArgument(0)));
        lenient().when(layoutMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxGraphLayout>(rows));
        lenient().when(layoutMapper.insert(any(SandboxGraphLayout.class))).thenAnswer(inv -> {
            SandboxGraphLayout r = inv.getArgument(0);
            r.setId(seq++);
            rows.add(r);
            return 1;
        });
        lenient().when(layoutMapper.delete(any())).thenAnswer(inv -> {
            int before = rows.size();
            rows.removeIf(pendingDelete);
            return before - rows.size();
        });
        character(1, USER);
        character(2, USER);
        character(3, OTHER_USER);
        world(10, USER);
        world(11, OTHER_USER);
        organization(20, USER);
        organization(21, OTHER_USER);
    }

    @Test
    void save_replacesWholeScopeAndDefaultsZ() {
        pendingDelete = r -> "world:10".equals(r.getScope());
        service.saveLayout(USER, request("world:10", item(1, 10, 20, 5.0), item(2, 30, 40, null)));
        assertEquals(2, rows.size());
        assertEquals(0d, rows.get(1).getZ());

        // 第二次保存只剩一个角色：旧的两行都被替换
        List<SandboxGraphLayout> saved = service.saveLayout(USER, request("world:10", item(2, 1, 2, 3.0)));
        assertEquals(1, saved.size());
        assertEquals(1, rows.size());
        assertEquals(2L, rows.get(0).getCharacterId());
        assertEquals("world:10", rows.get(0).getScope());
    }

    @Test
    void save_emptyItemsClearsScope() {
        pendingDelete = r -> "all".equals(r.getScope());
        service.saveLayout(USER, request("all", item(1, 0, 0, 0.0)));
        service.saveLayout(USER, request("all"));
        assertTrue(rows.isEmpty());
    }

    @Test
    void save_duplicateCharacterKeepsLastPosition() {
        service.saveLayout(USER, request("all", item(1, 1, 1, 0.0), item(1, 9, 9, 0.0)));
        assertEquals(1, rows.size());
        assertEquals(9d, rows.get(0).getX());
    }

    @Test
    void save_rejectsBadScopeForeignWorldForeignCharacterAndBadNumbers() {
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("world:abc", item(1, 0, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("", item(1, 0, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("world:11", item(1, 0, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("world:999", item(1, 0, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("all", item(3, 0, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("all", item(99, 0, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("all", item(1, Double.NaN, 0, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("all", item(1, 0, 2e6, 0.0))));
        assertThrows(BusinessException.class, () -> service.saveLayout(null, request("all")));
        assertTrue(rows.isEmpty());
    }

    @Test
    void save_rejectsTooManyItems() {
        SandboxGraphLayoutRequest.Item[] many = new SandboxGraphLayoutRequest.Item[SandboxGraphLayoutService.MAX_ITEMS + 1];
        for (int i = 0; i < many.length; i++) many[i] = item(1, i, i, 0.0);
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("all", many)));
    }

    @Test
    void list_rejectsForeignWorldScope() {
        assertThrows(BusinessException.class, () -> service.listLayout(USER, "world:11"));
        assertEquals(0, service.listLayout(USER, "all").size());
    }

    @Test
    void cascade_characterAndWorldviewDeleteRemoveRows() {
        service.saveLayout(USER, request("all", item(1, 0, 0, 0.0), item(2, 0, 0, 0.0)));
        pendingDelete = r -> r.getCharacterId() == 1L;
        service.onCharacterDeleted(USER, 1L);
        assertEquals(1, rows.size());

        pendingDelete = r -> false;
        service.saveLayout(USER, request("world:10", item(2, 0, 0, 0.0)));
        pendingDelete = r -> "world:10".equals(r.getScope());
        service.onWorldviewDeleted(USER, 10L);
        assertEquals(1, rows.size());
        assertEquals("all", rows.get(0).getScope());
    }

    @Test
    void organizationScope_isSeparateFromWorldAndAllViews() {
        pendingDelete = r -> "all".equals(r.getScope());
        service.saveLayout(USER, request("all", item(1, 5, 6, null)));
        pendingDelete = r -> "org:20".equals(r.getScope());
        service.saveLayout(USER, request("org:20", item(1, 300, 400, null), item(2, 10, 20, null)));
        assertEquals(3, rows.size());
        assertEquals(5d, rows.get(0).getX());
        // 组织详情整体替换只动 org:20 自己的行
        service.saveLayout(USER, request("org:20", item(2, 11, 21, null)));
        assertEquals(2, rows.size());
        assertEquals("all", rows.get(0).getScope());
        assertEquals("org:20", rows.get(1).getScope());
        assertEquals(11d, rows.get(1).getX());
    }

    @Test
    void organizationScope_rejectsForeignMissingAndMalformedOrganization() {
        assertThrows(BusinessException.class, () -> service.saveLayout(USER, request("org:21", item(1, 0, 0, null))));
        assertThrows(BusinessException.class, () -> service.listLayout(USER, "org:21"));
        assertThrows(BusinessException.class, () -> service.listLayout(USER, "org:999"));
        assertThrows(BusinessException.class, () -> service.listLayout(USER, "org:0"));
        assertThrows(BusinessException.class, () -> service.listLayout(USER, "org:"));
        assertThrows(BusinessException.class, () -> service.listLayout(USER, "organization:20"));
    }

    @Test
    void cascade_organizationDeleteRemovesOnlyItsRows() {
        pendingDelete = r -> "org:20".equals(r.getScope());
        service.saveLayout(USER, request("org:20", item(1, 1, 1, null)));
        pendingDelete = r -> "world:10".equals(r.getScope());
        service.saveLayout(USER, request("world:10", item(1, 2, 2, null)));
        pendingDelete = r -> "org:20".equals(r.getScope());
        service.onOrganizationDeleted(USER, 20L);
        assertEquals(1, rows.size());
        assertEquals("world:10", rows.get(0).getScope());
    }

    // ---------------------------------------------------------------- helpers

    private void organization(long id, long userId) {
        SandboxOrganization o = new SandboxOrganization();
        o.setId(id);
        o.setUserId(userId);
        o.setName("组织" + id);
        organizations.put(id, o);
    }

    private void character(long id, long userId) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id);
        c.setUserId(userId);
        c.setName("角色" + id);
        characters.put(id, c);
    }

    private void world(long id, long userId) {
        SandboxWorldview w = new SandboxWorldview();
        w.setId(id);
        w.setUserId(userId);
        w.setName("世界" + id);
        worlds.put(id, w);
    }

    private static SandboxGraphLayoutRequest.Item item(long characterId, double x, double y, Double z) {
        SandboxGraphLayoutRequest.Item it = new SandboxGraphLayoutRequest.Item();
        it.setCharacterId(characterId);
        it.setX(x);
        it.setY(y);
        it.setZ(z);
        return it;
    }

    private static SandboxGraphLayoutRequest request(String scope, SandboxGraphLayoutRequest.Item... items) {
        SandboxGraphLayoutRequest r = new SandboxGraphLayoutRequest();
        r.setScope(scope);
        r.setItems(new ArrayList<SandboxGraphLayoutRequest.Item>(Arrays.asList(items)));
        return r;
    }
}
