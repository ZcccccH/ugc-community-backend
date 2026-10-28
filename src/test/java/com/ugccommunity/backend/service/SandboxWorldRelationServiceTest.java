package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxWorldRelationRequest;
import com.ugccommunity.backend.entity.SandboxWorldRelation;
import com.ugccommunity.backend.entity.SandboxWorldRelationDismissed;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxWorldRelationDismissedMapper;
import com.ugccommunity.backend.mapper.SandboxWorldRelationMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SandboxWorldRelationServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxWorldRelationMapper relationMapper;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    @Mock private SandboxWorldRelationDismissedMapper dismissedMapper;
    private SandboxWorldRelationService service;

    private final Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
    private final Map<Long, SandboxWorldRelation> relations = new LinkedHashMap<Long, SandboxWorldRelation>();
    private final Map<Long, SandboxWorldRelationDismissed> dismissed = new LinkedHashMap<Long, SandboxWorldRelationDismissed>();
    private long seq = 100;

    @BeforeEach
    void setUp() {
        service = new SandboxWorldRelationService(relationMapper, worldviewMapper, dismissedMapper);
        lenient().when(dismissedMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxWorldRelationDismissed>(dismissed.values()));
        lenient().when(dismissedMapper.insert(any(SandboxWorldRelationDismissed.class))).thenAnswer(inv -> {
            SandboxWorldRelationDismissed d = inv.getArgument(0);
            d.setId(++seq);
            dismissed.put(d.getId(), d);
            return 1;
        });
        lenient().when(dismissedMapper.deleteById(anyLong())).thenAnswer(inv -> dismissed.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> worlds.get((Long) inv.getArgument(0)));
        lenient().when(relationMapper.selectList(any())).thenAnswer(inv -> relations.values().stream()
                .filter(r -> r.getUserId() == USER).collect(Collectors.toList()));
        lenient().when(relationMapper.selectById(anyLong())).thenAnswer(inv -> relations.get((Long) inv.getArgument(0)));
        lenient().when(relationMapper.insert(any(SandboxWorldRelation.class))).thenAnswer(inv -> {
            SandboxWorldRelation r = inv.getArgument(0);
            r.setId(++seq);
            relations.put(r.getId(), r);
            return 1;
        });
        lenient().when(relationMapper.updateById(any(SandboxWorldRelation.class))).thenAnswer(inv -> {
            SandboxWorldRelation r = inv.getArgument(0);
            relations.put(r.getId(), r);
            return 1;
        });
        lenient().when(relationMapper.deleteById(anyLong())).thenAnswer(inv -> relations.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        world(1, USER);
        world(2, USER);
        world(3, USER);
        world(9, OTHER_USER);
    }

    @Test
    void create_defaultsOneWayAndClearsReverseLabel() {
        SandboxWorldRelation r = service.createRelation(USER, request(1, 2, "平行世界", "镜像", null));
        assertEquals(SandboxWorldRelation.ONE_WAY, r.getDirection());
        assertEquals("", r.getReverseLabel());
        assertEquals("平行世界", r.getLabel());
    }

    @Test
    void create_rejectsDuplicateSelfForeignAndBadInput() {
        service.createRelation(USER, request(1, 2, "同一宇宙", null, null));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(2, 1, "重复", null, null)));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 1, "自己", null, null)));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 9, "越权", null, null)));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 404, "不存在", null, null)));
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 3, "x", null, "sideways")));
        SandboxWorldRelationRequest badColor = request(1, 3, "x", null, null);
        badColor.setColor("blue");
        assertThrows(BusinessException.class, () -> service.createRelation(USER, badColor));
        StringBuilder longLabel = new StringBuilder();
        for (int i = 0; i < 101; i++) longLabel.append('字');
        assertThrows(BusinessException.class, () -> service.createRelation(USER, request(1, 3, longLabel.toString(), null, null)));
        assertEquals(1, relations.size());
    }

    @Test
    void update_changesDirectionAndLabels_butNotEnds() {
        SandboxWorldRelation r = service.createRelation(USER, request(1, 2, "旧名", null, null));
        SandboxWorldRelationRequest edit = request(3, 3, "新名", "反向", SandboxWorldRelation.TWO_WAY);
        edit.setColor("#4F7CA8");
        SandboxWorldRelation u = service.updateRelation(USER, r.getId(), edit);
        assertEquals(1L, u.getFromWorldviewId());
        assertEquals(2L, u.getToWorldviewId());
        assertEquals("新名", u.getLabel());
        assertEquals("反向", u.getReverseLabel());
        assertEquals("#4F7CA8", u.getColor());
        assertTrue(u.isTwoWay());
    }

    @Test
    void update_and_delete_rejectOtherUsersRelation() {
        SandboxWorldRelation foreign = new SandboxWorldRelation();
        foreign.setId(500L);
        foreign.setUserId(OTHER_USER);
        foreign.setFromWorldviewId(9L);
        foreign.setToWorldviewId(9L);
        relations.put(500L, foreign);
        assertThrows(BusinessException.class, () -> service.updateRelation(USER, 500L, new SandboxWorldRelationRequest()));
        assertThrows(BusinessException.class, () -> service.deleteRelation(USER, 500L));
        assertThrows(BusinessException.class, () -> service.deleteRelation(USER, 999L));
        assertEquals(1, relations.size());
    }

    @Test
    void worldviewDeleted_removesOnlyItsRelations() {
        service.createRelation(USER, request(1, 2, "a", null, null));
        service.createRelation(USER, request(2, 3, "b", null, null));
        service.createRelation(USER, request(1, 3, "c", null, null));
        service.onWorldviewDeleted(USER, 1L);
        assertEquals(1, relations.size());
        assertEquals("b", relations.values().iterator().next().getLabel());
    }

    @Test
    void delete_remembersThePair_untilTheUserCreatesItAgain() {
        SandboxWorldRelation r = service.createRelation(USER, request(1, 2, "贸易", null, null));
        assertFalse(service.isDismissed(USER, 1L, 2L));
        service.deleteRelation(USER, r.getId());
        assertTrue(service.isDismissed(USER, 1L, 2L));
        assertTrue(service.isDismissed(USER, 2L, 1L), "不分方向");
        assertFalse(service.isDismissed(USER, 1L, 3L));
        service.createRelation(USER, request(2, 1, "重新建立", null, null));
        assertFalse(service.isDismissed(USER, 1L, 2L));
    }

    @Test
    void worldviewDeleted_forgetsItsDismissedRelations() {
        service.deleteRelation(USER, service.createRelation(USER, request(1, 2, "a", null, null)).getId());
        service.deleteRelation(USER, service.createRelation(USER, request(2, 3, "b", null, null)).getId());
        service.onWorldviewDeleted(USER, 1L);
        assertEquals(1, dismissed.size());
        assertTrue(service.isDismissed(USER, 2L, 3L));
    }

    private void world(long id, long userId) {
        SandboxWorldview w = new SandboxWorldview();
        w.setId(id);
        w.setUserId(userId);
        w.setName("世界" + id);
        worlds.put(id, w);
    }

    private static SandboxWorldRelationRequest request(long from, long to, String label, String reverse, String direction) {
        SandboxWorldRelationRequest r = new SandboxWorldRelationRequest();
        r.setFromWorldviewId(from);
        r.setToWorldviewId(to);
        r.setLabel(label);
        r.setReverseLabel(reverse);
        r.setDirection(direction);
        return r;
    }
}
