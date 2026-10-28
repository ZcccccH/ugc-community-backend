package com.ugccommunity.backend.service;

import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.dto.request.SandboxRecordCategoryRequest;
import com.ugccommunity.backend.dto.request.SandboxRecordRequest;
import com.ugccommunity.backend.entity.SandboxRecord;
import com.ugccommunity.backend.entity.SandboxRecordCategory;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxRecordCategoryMapper;
import com.ugccommunity.backend.mapper.SandboxRecordMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.lenient;

@ExtendWith(MockitoExtension.class)
class SandboxRecordServiceTest {
    private static final long USER = 7L;
    private static final long OTHER_USER = 8L;

    @Mock private SandboxRecordMapper recordMapper;
    @Mock private SandboxRecordCategoryMapper categoryMapper;
    @Mock private SandboxWorldviewMapper worldviewMapper;
    private SandboxRecordService service;

    private final Map<Long, SandboxWorldview> worlds = new LinkedHashMap<Long, SandboxWorldview>();
    private final Map<Long, SandboxRecord> records = new LinkedHashMap<Long, SandboxRecord>();
    private final Map<Long, SandboxRecordCategory> categories = new LinkedHashMap<Long, SandboxRecordCategory>();
    private long seq = 100;
    /** 模拟「读出之后、写入之前」被别人改过：下一次条件更新返回 0 行 */
    private boolean concurrentEdit;

    @BeforeEach
    void setUp() {
        service = new SandboxRecordService(recordMapper, categoryMapper, worldviewMapper);
        lenient().when(worldviewMapper.selectById(anyLong())).thenAnswer(inv -> worlds.get((Long) inv.getArgument(0)));
        // 查询条件不解析：返回当前用户的全部数据，由服务自己过滤的地方（重名、迁移键）照常生效
        lenient().when(recordMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxRecord>(records.values()));
        lenient().when(recordMapper.selectById(anyLong())).thenAnswer(inv -> records.get((Long) inv.getArgument(0)));
        lenient().when(recordMapper.selectCount(any())).thenAnswer(inv -> (long) records.size());
        lenient().when(recordMapper.insert(any(SandboxRecord.class))).thenAnswer(inv -> {
            SandboxRecord r = inv.getArgument(0);
            r.setId(++seq);
            records.put(r.getId(), r);
            return 1;
        });
        lenient().when(recordMapper.update(any(SandboxRecord.class), any())).thenAnswer(inv -> {
            if (concurrentEdit) return 0;
            SandboxRecord r = inv.getArgument(0);
            records.put(r.getId(), r);
            return 1;
        });
        lenient().when(recordMapper.deleteById(anyLong())).thenAnswer(inv -> records.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        lenient().when(categoryMapper.selectList(any())).thenAnswer(inv -> new ArrayList<SandboxRecordCategory>(categories.values()));
        lenient().when(categoryMapper.selectById(anyLong())).thenAnswer(inv -> categories.get((Long) inv.getArgument(0)));
        lenient().when(categoryMapper.insert(any(SandboxRecordCategory.class))).thenAnswer(inv -> {
            SandboxRecordCategory c = inv.getArgument(0);
            c.setId(++seq);
            categories.put(c.getId(), c);
            return 1;
        });
        lenient().when(categoryMapper.updateById(any(SandboxRecordCategory.class))).thenReturn(1);
        lenient().when(categoryMapper.deleteById(anyLong())).thenAnswer(inv -> categories.remove((Long) inv.getArgument(0)) == null ? 0 : 1);
        world(1, USER);
        world(2, USER);
        world(9, OTHER_USER);
    }

    @Test
    void create_bindsOwnerWorldCategoryAndStartsAtVersionOne() {
        SandboxRecord r = service.createRecord(USER, request(1, "creature", "  雾鹿  ", "栖息在北境"));
        assertEquals(USER, r.getUserId());
        assertEquals(1L, r.getWorldviewId());
        assertEquals("creature", r.getCategory());
        assertNull(r.getCategoryId());
        assertEquals("雾鹿", r.getName());
        assertEquals(1, r.getVersion());
    }

    @Test
    void create_rejectsForeignWorldMissingWorldBlankNameAndUnknownCategory() {
        assertThrows(BusinessException.class, () -> service.createRecord(USER, request(9, "creature", "越权", "")));
        assertThrows(BusinessException.class, () -> service.createRecord(USER, request(404, "creature", "不存在", "")));
        assertThrows(BusinessException.class, () -> service.createRecord(USER, request(1, "creature", "  ", "")));
        assertThrows(BusinessException.class, () -> service.createRecord(USER, request(1, "dragon", "未知类别", "")));
        assertThrows(BusinessException.class, () -> service.createRecord(USER, request(1, SandboxRecord.CUSTOM, "缺类别", "")));
        assertThrows(BusinessException.class, () -> service.createRecord(null, request(1, "creature", "未登录", "")));
        assertTrue(records.isEmpty());
    }

    @Test
    void create_withSameLegacyKeyReturnsExistingRecordInsteadOfDuplicating() {
        SandboxRecordRequest first = request(1, "plant", "蘑菇", "旧植物档案");
        first.setLegacyKey("plant:1");
        SandboxRecord a = service.createRecord(USER, first);
        SandboxRecordRequest again = request(1, "plant", "蘑菇（再次迁移）", "不应覆盖");
        again.setLegacyKey("plant:1");
        SandboxRecord b = service.createRecord(USER, again);
        assertEquals(a.getId(), b.getId());
        assertEquals("蘑菇", b.getName());
        assertEquals(1, records.size());
    }

    @Test
    void customCategory_mustBelongToTheSameWorldAndUser() {
        SandboxRecordCategory own = service.createCategory(USER, category(1, "魔法流派"));
        SandboxRecordRequest ok = request(1, SandboxRecord.CUSTOM, "星咏", "");
        ok.setCategoryId(own.getId());
        assertEquals(own.getId(), service.createRecord(USER, ok).getCategoryId());

        SandboxRecordRequest wrongWorld = request(2, SandboxRecord.CUSTOM, "错世界", "");
        wrongWorld.setCategoryId(own.getId());
        assertThrows(BusinessException.class, () -> service.createRecord(USER, wrongWorld));

        SandboxRecordCategory foreign = new SandboxRecordCategory();
        foreign.setId(500L);
        foreign.setUserId(OTHER_USER);
        foreign.setWorldviewId(1L);
        categories.put(500L, foreign);
        SandboxRecordRequest stolen = request(1, SandboxRecord.CUSTOM, "越权类别", "");
        stolen.setCategoryId(500L);
        assertThrows(BusinessException.class, () -> service.createRecord(USER, stolen));
    }

    @Test
    void category_rejectsDuplicateNameAndDeletionWhileInUse() {
        SandboxRecordCategory c = service.createCategory(USER, category(1, "魔法流派"));
        assertThrows(BusinessException.class, () -> service.createCategory(USER, category(1, "魔法流派")));
        assertThrows(BusinessException.class, () -> service.createCategory(USER, category(9, "越权")));
        SandboxRecordRequest r = request(1, SandboxRecord.CUSTOM, "星咏", "");
        r.setCategoryId(c.getId());
        SandboxRecord saved = service.createRecord(USER, r);
        assertThrows(BusinessException.class, () -> service.deleteCategory(USER, c.getId()));
        service.deleteRecord(USER, saved.getId());
        service.deleteCategory(USER, c.getId());
        assertTrue(categories.isEmpty());
    }

    @Test
    void update_bumpsVersionAndCanClearDescription() {
        SandboxRecord r = service.createRecord(USER, request(1, "city", "白港", "港口城市"));
        SandboxRecordRequest edit = new SandboxRecordRequest();
        edit.setDescription("");
        edit.setVersion(1);
        SandboxRecord u = service.updateRecord(USER, r.getId(), edit);
        assertEquals("", u.getDescription());
        assertEquals("白港", u.getName());
        assertEquals(2, u.getVersion());
    }

    @Test
    void update_rejectsStaleVersionAndConcurrentEdit() {
        SandboxRecord r = service.createRecord(USER, request(1, "city", "白港", "港口城市"));
        SandboxRecordRequest first = new SandboxRecordRequest();
        first.setDescription("手动修改");
        first.setVersion(1);
        service.updateRecord(USER, r.getId(), first);

        SandboxRecordRequest late = new SandboxRecordRequest();
        late.setDescription("迟到的自动整理结果");
        late.setVersion(1);
        assertThrows(BusinessException.class, () -> service.updateRecord(USER, r.getId(), late));
        assertEquals("手动修改", records.get(r.getId()).getDescription());

        concurrentEdit = true;
        SandboxRecordRequest racing = new SandboxRecordRequest();
        racing.setDescription("并发写入");
        racing.setVersion(2);
        assertThrows(BusinessException.class, () -> service.updateRecord(USER, r.getId(), racing));
    }

    @Test
    void read_update_and_delete_rejectOtherUsersRecord() {
        SandboxRecord foreign = new SandboxRecord();
        foreign.setId(500L);
        foreign.setUserId(OTHER_USER);
        foreign.setWorldviewId(9L);
        foreign.setVersion(1);
        records.put(500L, foreign);
        assertThrows(BusinessException.class, () -> service.getRecord(USER, 500L));
        assertThrows(BusinessException.class, () -> service.updateRecord(USER, 500L, new SandboxRecordRequest()));
        assertThrows(BusinessException.class, () -> service.deleteRecord(USER, 500L));
        assertThrows(BusinessException.class, () -> service.deleteRecord(USER, 999L));
        assertEquals(1, records.size());
    }

    private void world(long id, long userId) {
        SandboxWorldview w = new SandboxWorldview();
        w.setId(id);
        w.setUserId(userId);
        w.setName("世界" + id);
        worlds.put(id, w);
    }

    private static SandboxRecordRequest request(long worldviewId, String category, String name, String description) {
        SandboxRecordRequest r = new SandboxRecordRequest();
        r.setWorldviewId(worldviewId);
        r.setCategory(category);
        r.setName(name);
        r.setDescription(description);
        return r;
    }

    private static SandboxRecordCategoryRequest category(long worldviewId, String name) {
        SandboxRecordCategoryRequest r = new SandboxRecordCategoryRequest();
        r.setWorldviewId(worldviewId);
        r.setName(name);
        return r;
    }
}
