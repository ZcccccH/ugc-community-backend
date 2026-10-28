package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxRecordCategoryRequest;
import com.ugccommunity.backend.dto.request.SandboxRecordRequest;
import com.ugccommunity.backend.entity.SandboxRecord;
import com.ugccommunity.backend.entity.SandboxRecordCategory;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxRecordCategoryMapper;
import com.ugccommunity.backend.mapper.SandboxRecordMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** 世界设定记录（sandbox_record）与自定义类别（sandbox_record_category）。 */
@Service
public class SandboxRecordService {
    /** 内置类别键；用户自定义类别用 custom + categoryId */
    public static final Set<String> BUILT_IN = Collections.unmodifiableSet(new LinkedHashSet<String>(Arrays.asList(
            "creature", "plant", "mineral", "weather", "history", "building", "country", "city")));
    private static final int NAME_MAX = 80;
    private static final int CATEGORY_NAME_MAX = 40;
    private static final int DESCRIPTION_MAX = 20000;
    private static final int ICON_MAX = 500;
    private static final int DISPLAY_MAX = 4000;
    private static final int LEGACY_KEY_MAX = 80;

    private final SandboxRecordMapper recordMapper;
    private final SandboxRecordCategoryMapper categoryMapper;
    private final SandboxWorldviewMapper worldviewMapper;

    public SandboxRecordService(SandboxRecordMapper recordMapper, SandboxRecordCategoryMapper categoryMapper, SandboxWorldviewMapper worldviewMapper) {
        this.recordMapper = recordMapper;
        this.categoryMapper = categoryMapper;
        this.worldviewMapper = worldviewMapper;
    }

    // ------------------------------------------------------------------ 类别

    public List<SandboxRecordCategory> listCategories(Long userId, Long worldviewId) {
        validUser(userId);
        LambdaQueryWrapper<SandboxRecordCategory> q = new LambdaQueryWrapper<SandboxRecordCategory>()
                .eq(SandboxRecordCategory::getUserId, userId);
        if (worldviewId != null) q.eq(SandboxRecordCategory::getWorldviewId, worldviewId);
        List<SandboxRecordCategory> list = categoryMapper.selectList(q.orderByAsc(SandboxRecordCategory::getId));
        return list == null ? new ArrayList<SandboxRecordCategory>() : list;
    }

    @Transactional
    public SandboxRecordCategory createCategory(Long userId, SandboxRecordCategoryRequest r) {
        validUser(userId);
        if (r == null || r.getWorldviewId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择世界观");
        ownedWorldview(userId, r.getWorldviewId());
        String name = requiredName(r.getName(), CATEGORY_NAME_MAX, "类别名称");
        for (SandboxRecordCategory c : listCategories(userId, r.getWorldviewId())) {
            if (name.equals(c.getName())) throw new BusinessException(ResultCode.DATA_EXIST, "这个世界观里已经有同名类别了");
        }
        SandboxRecordCategory x = new SandboxRecordCategory();
        x.setUserId(userId);
        x.setWorldviewId(r.getWorldviewId());
        x.setName(name);
        x.setIcon(checkLength(r.getIcon(), ICON_MAX, "图标"));
        x.setSortOrder(r.getSortOrder() == null ? 0 : r.getSortOrder());
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        categoryMapper.insert(x);
        return x;
    }

    @Transactional
    public SandboxRecordCategory updateCategory(Long userId, Long id, SandboxRecordCategoryRequest r) {
        SandboxRecordCategory x = ownedCategory(userId, id);
        if (r == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        if (r.getName() != null) {
            String name = requiredName(r.getName(), CATEGORY_NAME_MAX, "类别名称");
            for (SandboxRecordCategory c : listCategories(userId, x.getWorldviewId())) {
                if (!c.getId().equals(x.getId()) && name.equals(c.getName())) {
                    throw new BusinessException(ResultCode.DATA_EXIST, "这个世界观里已经有同名类别了");
                }
            }
            x.setName(name);
        }
        if (r.getIcon() != null) x.setIcon(checkLength(r.getIcon(), ICON_MAX, "图标"));
        if (r.getSortOrder() != null) x.setSortOrder(r.getSortOrder());
        x.setUpdatedAt(LocalDateTime.now());
        categoryMapper.updateById(x);
        return x;
    }

    /** 类别下还有记录时不允许删除，避免记录变成无类别 */
    @Transactional
    public void deleteCategory(Long userId, Long id) {
        SandboxRecordCategory x = ownedCategory(userId, id);
        Long used = recordMapper.selectCount(new LambdaQueryWrapper<SandboxRecord>()
                .eq(SandboxRecord::getUserId, userId)
                .eq(SandboxRecord::getCategoryId, x.getId()));
        if (used != null && used > 0) throw new BusinessException(ResultCode.BUSINESS_ERROR, "请先删除或移走这个类别下的记录");
        categoryMapper.deleteById(x.getId());
    }

    // ------------------------------------------------------------------ 记录

    public List<SandboxRecord> listRecords(Long userId, Long worldviewId, String category) {
        validUser(userId);
        LambdaQueryWrapper<SandboxRecord> q = new LambdaQueryWrapper<SandboxRecord>().eq(SandboxRecord::getUserId, userId);
        if (worldviewId != null) q.eq(SandboxRecord::getWorldviewId, worldviewId);
        if (category != null && !category.trim().isEmpty()) q.eq(SandboxRecord::getCategory, category.trim());
        List<SandboxRecord> list = recordMapper.selectList(q.orderByAsc(SandboxRecord::getSortOrder).orderByAsc(SandboxRecord::getId));
        return list == null ? new ArrayList<SandboxRecord>() : list;
    }

    public SandboxRecord getRecord(Long userId, Long id) {
        return ownedRecord(userId, id);
    }

    @Transactional
    public SandboxRecord createRecord(Long userId, SandboxRecordRequest r) {
        validUser(userId);
        if (r == null || r.getWorldviewId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择世界观");
        ownedWorldview(userId, r.getWorldviewId());
        String legacyKey = blankToNull(checkLength(r.getLegacyKey(), LEGACY_KEY_MAX, "迁移标识"));
        if (legacyKey != null) {
            SandboxRecord existing = byLegacyKey(userId, legacyKey);
            if (existing != null) return existing;
        }
        SandboxRecord x = new SandboxRecord();
        x.setUserId(userId);
        x.setWorldviewId(r.getWorldviewId());
        applyCategory(userId, x, r.getCategory(), r.getCategoryId());
        x.setName(requiredName(r.getName(), NAME_MAX, "名称"));
        x.setDescription(checkLength(r.getDescription(), DESCRIPTION_MAX, "描述"));
        x.setIcon(checkLength(r.getIcon(), ICON_MAX, "图标"));
        x.setDisplay(checkLength(r.getDisplay(), DISPLAY_MAX, "显示配置"));
        x.setLegacyKey(legacyKey);
        x.setVersion(1);
        x.setSortOrder(r.getSortOrder() == null ? 0 : r.getSortOrder());
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        try {
            recordMapper.insert(x);
        } catch (DuplicateKeyException e) {
            // 同一个迁移键被并发提交：返回先写入的那条
            SandboxRecord existing = legacyKey == null ? null : byLegacyKey(userId, legacyKey);
            if (existing == null) throw e;
            return existing;
        }
        return x;
    }

    @Transactional
    public SandboxRecord updateRecord(Long userId, Long id, SandboxRecordRequest r) {
        SandboxRecord x = ownedRecord(userId, id);
        if (r == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        int current = x.getVersion() == null ? 1 : x.getVersion();
        if (r.getVersion() != null && r.getVersion() != current) throw stale();
        if (r.getCategory() != null) applyCategory(userId, x, r.getCategory(), r.getCategoryId());
        if (r.getName() != null) x.setName(requiredName(r.getName(), NAME_MAX, "名称"));
        // update 会跳过 null 字段，清空文字时写空串
        if (r.getDescription() != null) x.setDescription(checkLength(r.getDescription(), DESCRIPTION_MAX, "描述"));
        if (r.getIcon() != null) x.setIcon(checkLength(r.getIcon(), ICON_MAX, "图标"));
        if (r.getDisplay() != null) x.setDisplay(checkLength(r.getDisplay(), DISPLAY_MAX, "显示配置"));
        if (r.getSortOrder() != null) x.setSortOrder(r.getSortOrder());
        x.setVersion(current + 1);
        x.setUpdatedAt(LocalDateTime.now());
        LambdaUpdateWrapper<SandboxRecord> where = new LambdaUpdateWrapper<SandboxRecord>()
                .eq(SandboxRecord::getId, x.getId())
                .eq(SandboxRecord::getUserId, userId)
                .eq(SandboxRecord::getVersion, current);
        // 内置类别不带 categoryId，需要显式清掉旧值
        if (x.getCategoryId() == null) where.set(SandboxRecord::getCategoryId, null);
        if (recordMapper.update(x, where) == 0) throw stale();
        return x;
    }

    @Transactional
    public void deleteRecord(Long userId, Long id) {
        SandboxRecord x = ownedRecord(userId, id);
        recordMapper.deleteById(x.getId());
    }

    /** 世界观删除后，删掉它的全部设定记录和自定义类别 */
    @Transactional
    public void onWorldviewDeleted(Long userId, Long worldviewId) {
        validUser(userId);
        recordMapper.delete(new LambdaQueryWrapper<SandboxRecord>()
                .eq(SandboxRecord::getUserId, userId).eq(SandboxRecord::getWorldviewId, worldviewId));
        categoryMapper.delete(new LambdaQueryWrapper<SandboxRecordCategory>()
                .eq(SandboxRecordCategory::getUserId, userId).eq(SandboxRecordCategory::getWorldviewId, worldviewId));
    }

    // ------------------------------------------------------------------ 工具

    private void applyCategory(Long userId, SandboxRecord x, String category, Long categoryId) {
        String key = category == null ? "" : category.trim();
        if (BUILT_IN.contains(key)) {
            x.setCategory(key);
            x.setCategoryId(null);
            return;
        }
        if (!SandboxRecord.CUSTOM.equals(key) || categoryId == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择设定类别");
        }
        SandboxRecordCategory c = ownedCategory(userId, categoryId);
        if (!c.getWorldviewId().equals(x.getWorldviewId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "类别不属于这个世界观");
        }
        x.setCategory(SandboxRecord.CUSTOM);
        x.setCategoryId(c.getId());
    }

    private SandboxRecord byLegacyKey(Long userId, String legacyKey) {
        List<SandboxRecord> list = recordMapper.selectList(new LambdaQueryWrapper<SandboxRecord>()
                .eq(SandboxRecord::getUserId, userId).eq(SandboxRecord::getLegacyKey, legacyKey));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private SandboxRecord ownedRecord(Long userId, Long id) {
        validUser(userId);
        SandboxRecord x = id == null ? null : recordMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return x;
    }

    private SandboxRecordCategory ownedCategory(Long userId, Long id) {
        validUser(userId);
        SandboxRecordCategory x = id == null ? null : categoryMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "类别不存在");
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return x;
    }

    private SandboxWorldview ownedWorldview(Long userId, Long id) {
        SandboxWorldview w = worldviewMapper.selectById(id);
        if (w == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "世界观不存在");
        if (!userId.equals(w.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return w;
    }

    private static BusinessException stale() {
        return new BusinessException(ResultCode.DATA_EXIST, "这条设定已在别处被修改，请刷新后再改");
    }

    private static String requiredName(String s, int max, String field) {
        String t = s == null ? "" : s.trim();
        if (t.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, field + "不能为空");
        if (t.length() > max) throw new BusinessException(ResultCode.PARAM_ERROR, field + "不能超过 " + max + " 个字");
        return t;
    }

    /** null 视为空串；描述保留首尾以外的原样内容 */
    private static String checkLength(String s, int max, String field) {
        if (s == null) return "";
        String t = s.trim();
        if (t.length() > max) throw new BusinessException(ResultCode.PARAM_ERROR, field + "不能超过 " + max + " 个字");
        return t;
    }

    private static String blankToNull(String s) {
        return s == null || s.isEmpty() ? null : s;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
