package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxCharacterFolder;
import com.ugccommunity.backend.mapper.SandboxCharacterFolderMapper;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
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
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SandboxCharacterFolderServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxCharacterFolderMapper folderMapper;
    @Mock private SandboxCharacterMapper characterMapper;
    private SandboxCharacterFolderService service;
    private final Map<Long, SandboxCharacterFolder> folders = new LinkedHashMap<Long, SandboxCharacterFolder>();
    private long seq = 100;

    @BeforeEach
    void setUp() {
        service = new SandboxCharacterFolderService(folderMapper, characterMapper);
        lenient().when(folderMapper.selectList(any())).thenAnswer(inv -> folders.values().stream()
                .filter(f -> f.getUserId() == USER).collect(Collectors.toCollection(ArrayList::new)));
        lenient().when(folderMapper.selectById(anyLong())).thenAnswer(inv -> folders.get((Long) inv.getArgument(0)));
        lenient().when(folderMapper.insert(any(SandboxCharacterFolder.class))).thenAnswer(inv -> {
            SandboxCharacterFolder f = inv.getArgument(0);
            f.setId(++seq);
            folders.put(f.getId(), f);
            return 1;
        });
    }

    private SandboxCharacter character(long id, long owner) {
        SandboxCharacter c = new SandboxCharacter();
        c.setId(id);
        c.setUserId(owner);
        return c;
    }

    @Test
    void create_trimsNameAndRejectsBlankDuplicateAndTooLong() {
        SandboxCharacterFolder f = service.create(USER, "  主角团  ");
        assertEquals("主角团", f.getName());
        assertEquals(0, f.getSortOrder());
        assertThrows(BusinessException.class, () -> service.create(USER, "   "));
        assertThrows(BusinessException.class, () -> service.create(USER, "主角团"));
        StringBuilder longName = new StringBuilder();
        for (int i = 0; i < SandboxCharacterFolderService.NAME_MAX + 1; i++) longName.append('名');
        assertThrows(BusinessException.class, () -> service.create(USER, longName.toString()));
        assertEquals(1, service.create(USER, "配角").getSortOrder());
    }

    @Test
    void rename_keepsOwnNameButRejectsAnotherFoldersName() {
        SandboxCharacterFolder a = service.create(USER, "甲");
        service.create(USER, "乙");
        assertEquals("甲", service.rename(USER, a.getId(), "甲").getName());
        assertThrows(BusinessException.class, () -> service.rename(USER, a.getId(), "乙"));
        assertEquals("丙", service.rename(USER, a.getId(), " 丙 ").getName());
    }

    @Test
    void otherUsersFolderIsNotVisibleOrUsable() {
        SandboxCharacterFolder theirs = new SandboxCharacterFolder();
        theirs.setId(55L);
        theirs.setUserId(OTHER_USER);
        theirs.setName("别人的");
        folders.put(55L, theirs);
        assertThrows(BusinessException.class, () -> service.rename(USER, 55L, "改"));
        assertThrows(BusinessException.class, () -> service.delete(USER, 55L));
        lenient().when(characterMapper.selectById(1L)).thenReturn(character(1L, USER));
        assertThrows(BusinessException.class, () -> service.moveCharacter(USER, 1L, 55L));
        verify(characterMapper, never()).update(isNull(), any());
    }

    @Test
    void delete_movesCharactersBackToUngroupedThenRemovesFolder() {
        SandboxCharacterFolder f = service.create(USER, "临时");
        lenient().when(characterMapper.update(isNull(), any())).thenReturn(3);
        assertEquals(3, service.delete(USER, f.getId()));
        verify(characterMapper, times(1)).update(isNull(), any());
        verify(folderMapper, times(1)).delete(any());
    }

    @Test
    void moveCharacter_toFolderAndBackToUngrouped() {
        SandboxCharacterFolder f = service.create(USER, "主角团");
        lenient().when(characterMapper.selectById(1L)).thenReturn(character(1L, USER));
        assertEquals(f.getId(), service.moveCharacter(USER, 1L, f.getId()).getFolderId());
        assertNull(service.moveCharacter(USER, 1L, null).getFolderId());
        lenient().when(characterMapper.selectById(2L)).thenReturn(character(2L, OTHER_USER));
        assertThrows(BusinessException.class, () -> service.moveCharacter(USER, 2L, f.getId()));
    }
}
