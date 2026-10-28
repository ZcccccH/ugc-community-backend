package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxItemRequest;
import com.ugccommunity.backend.entity.SandboxChatMessage;
import com.ugccommunity.backend.entity.SandboxChatSession;
import com.ugccommunity.backend.entity.SandboxOrganization;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SandboxServiceTest {
    @Mock
    private SandboxWorldviewMapper worldviewMapper;
    @Mock private SandboxWorldGroupMapper worldGroupMapper;
    @Mock private SandboxOrganizationMapper organizationMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    @Mock private SandboxScriptMapper scriptMapper;
    @Mock private SandboxMountMapper mountMapper;
    @Mock private SandboxChatSessionMapper sessionMapper;
    @Mock private SandboxChatMessageMapper messageMapper;
    @Mock private DeepSeekClient deepSeekClient;
    @Mock private SandboxRelationService relationService;
    @Mock private SandboxWorldRelationService worldRelationService;
    @Mock private SandboxGraphLayoutService layoutService;
    @Mock private SandboxRecordService recordService;
    @Mock private SandboxMapService mapService;
    @Mock private SandboxTimelineService timelineService;

    @InjectMocks
    private SandboxService sandboxService;

    @org.junit.jupiter.api.BeforeEach
    void injectWorldGroups(){org.springframework.test.util.ReflectionTestUtils.setField(sandboxService, "worldGroupMapper", worldGroupMapper);}

    @Test
    void createWorldviewRejectsBlankName() {
        assertThrows(BusinessException.class, () -> sandboxService.createWorldview(7L, " ", "desc"));
    }

    @Test
    void createWorldviewBindsOwnerAndReturnsSavedEntity() {
        when(worldviewMapper.insert(any(SandboxWorldview.class))).thenAnswer(invocation -> {
            SandboxWorldview item = invocation.getArgument(0);
            item.setId(11L);
            return 1;
        });
        when(worldGroupMapper.insert(any(com.ugccommunity.backend.entity.SandboxWorldGroup.class))).thenAnswer(inv -> {
            com.ugccommunity.backend.entity.SandboxWorldGroup group = inv.getArgument(0); group.setId(20L); return 1;
        });
        SandboxWorldview result = sandboxService.createWorldview(7L, "星海", "desc");
        assertEquals(11L, result.getId());
        assertEquals(20L, result.getWorldGroupId());
        assertEquals(7L, result.getUserId());
        assertEquals("星海", result.getName());
    }

    @Test
    void worldGroupOwnershipAndMovePreserveWorldId() {
        com.ugccommunity.backend.entity.SandboxWorldGroup group = new com.ugccommunity.backend.entity.SandboxWorldGroup();
        group.setId(20L); group.setUserId(7L); group.setName("宇宙");
        when(worldGroupMapper.selectById(20L)).thenReturn(group);
        SandboxWorldview world = new SandboxWorldview(); world.setId(11L); world.setUserId(7L);
        when(worldviewMapper.selectById(11L)).thenReturn(world);
        SandboxWorldview moved = sandboxService.moveWorldToGroup(7L, 11L, 20L);
        assertEquals(11L, moved.getId()); assertEquals(20L, moved.getWorldGroupId());
        verify(worldviewMapper).updateById(world);
        assertThrows(BusinessException.class, () -> sandboxService.moveWorldToGroup(8L,11L,20L));
        assertThrows(BusinessException.class, () -> sandboxService.renameWorldGroup(8L,20L,"改名"));
    }

    @Test
    void setMainWorldOnlyAcceptsWorldsInsideTheGroup() {
        com.ugccommunity.backend.entity.SandboxWorldGroup group = new com.ugccommunity.backend.entity.SandboxWorldGroup();
        group.setId(20L); group.setUserId(7L);
        when(worldGroupMapper.selectById(20L)).thenReturn(group);
        SandboxWorldview inside = new SandboxWorldview(); inside.setId(11L); inside.setUserId(7L); inside.setWorldGroupId(20L);
        SandboxWorldview outside = new SandboxWorldview(); outside.setId(12L); outside.setUserId(7L); outside.setWorldGroupId(21L);
        when(worldviewMapper.selectById(11L)).thenReturn(inside);
        when(worldviewMapper.selectById(12L)).thenReturn(outside);
        assertEquals(11L, sandboxService.setMainWorld(7L, 20L, 11L).getMainWorldId());
        verify(worldGroupMapper).updateById(group);
        assertThrows(BusinessException.class, () -> sandboxService.setMainWorld(7L, 20L, 12L));
        assertThrows(BusinessException.class, () -> sandboxService.setMainWorld(8L, 20L, 11L));
    }

    @Test
    void deleteWorldGroupDeletesEachWorldWithItsDataThenTheGroup() {
        com.ugccommunity.backend.entity.SandboxWorldGroup group = new com.ugccommunity.backend.entity.SandboxWorldGroup();
        group.setId(20L); group.setUserId(7L);
        when(worldGroupMapper.selectById(20L)).thenReturn(group);
        SandboxWorldview a = new SandboxWorldview(); a.setId(11L); a.setUserId(7L); a.setWorldGroupId(20L);
        SandboxWorldview b = new SandboxWorldview(); b.setId(12L); b.setUserId(7L); b.setWorldGroupId(20L);
        when(worldviewMapper.selectList(any())).thenReturn(java.util.List.of(a, b));
        when(worldviewMapper.delete(any())).thenReturn(1);
        sandboxService.deleteWorldGroup(7L, 20L);
        verify(mapService).onWorldviewDeleted(7L, 11L);
        verify(mapService).onWorldviewDeleted(7L, 12L);
        verify(timelineService).onWorldviewDeleted(7L, 12L);
        verify(worldGroupMapper).deleteById(20L);
        assertThrows(BusinessException.class, () -> sandboxService.deleteWorldGroup(8L, 20L));
    }

    @Test
    void createWorldInExistingGroupDoesNotCreateAnotherGroup() {
        com.ugccommunity.backend.entity.SandboxWorldGroup group = new com.ugccommunity.backend.entity.SandboxWorldGroup();
        group.setId(20L); group.setUserId(7L);
        when(worldGroupMapper.selectById(20L)).thenReturn(group);
        when(worldviewMapper.insert(any(SandboxWorldview.class))).thenAnswer(inv -> { SandboxWorldview w=inv.getArgument(0);w.setId(12L);return 1; });
        SandboxWorldview w=sandboxService.createWorldInGroup(7L,20L,"世界二","","");
        assertEquals(20L,w.getWorldGroupId());
        org.mockito.Mockito.verify(worldGroupMapper,org.mockito.Mockito.never()).insert(any(com.ugccommunity.backend.entity.SandboxWorldGroup.class));
    }

    @Test
    void updateOrganizationPersistsRelationshipFields() {
        SandboxOrganization existing = new SandboxOrganization();
        existing.setId(10L);
        existing.setUserId(7L);
        existing.setName("组织A");
        when(organizationMapper.selectById(10L)).thenReturn(existing);

        SandboxItemRequest request = new SandboxItemRequest();
        request.setName("组织A");
        request.setRelationOrganizationIds("11");
        request.setRelationLabel("合作");
        request.setRelationColor("#111111");
        request.setRelationArrow(false);
        request.setRelationArrowStyle("none");

        SandboxOrganization result = sandboxService.updateOrganization(7L, 10L, request);

        assertEquals("11", result.getRelationOrganizationIds());
        assertEquals("合作", result.getRelationLabel());
        assertEquals("#111111", result.getRelationColor());
        assertEquals(false, result.getRelationArrow());
        assertEquals("none", result.getRelationArrowStyle());
        verify(organizationMapper).updateById(existing);
    }

    private SandboxChatSession session(Long id, Long userId) {
        SandboxChatSession session = new SandboxChatSession();
        session.setId(id);
        session.setUserId(userId);
        return session;
    }

    private SandboxChatMessage message(String role, String content, int mockReply) {
        SandboxChatMessage message = new SandboxChatMessage();
        message.setRole(role);
        message.setContent(content);
        message.setMockReply(mockReply);
        return message;
    }
}
