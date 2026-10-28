package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxCharacterFolder;
import com.ugccommunity.backend.mapper.SandboxCharacterFolderMapper;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 角色档案文件夹：新建、重命名、删除文件夹，把角色移进 / 移出文件夹。
 * 文件夹只整理角色列表，不代表角色属于哪个世界观或组织。删除文件夹不删角色，里面的角色回到「未归档」。
 */
@Service
public class SandboxCharacterFolderService {
    static final int NAME_MAX = 40;
    static final int FOLDER_LIMIT = 200;

    private final SandboxCharacterFolderMapper folderMapper;
    private final SandboxCharacterMapper characterMapper;

    public SandboxCharacterFolderService(SandboxCharacterFolderMapper folderMapper, SandboxCharacterMapper characterMapper) {
        this.folderMapper = folderMapper;
        this.characterMapper = characterMapper;
    }

    public List<SandboxCharacterFolder> list(Long userId) {
        validUser(userId);
        return folderMapper.selectList(new LambdaQueryWrapper<SandboxCharacterFolder>()
                .eq(SandboxCharacterFolder::getUserId, userId)
                .orderByAsc(SandboxCharacterFolder::getSortOrder)
                .orderByAsc(SandboxCharacterFolder::getId));
    }

    @Transactional
    public SandboxCharacterFolder create(Long userId, String name) {
        List<SandboxCharacterFolder> existing = list(userId);
        String n = cleanName(name);
        if (existing.size() >= FOLDER_LIMIT) throw new BusinessException(ResultCode.PARAM_ERROR, "文件夹数量已达上限");
        ensureUnique(existing, n, null);
        SandboxCharacterFolder f = new SandboxCharacterFolder();
        f.setUserId(userId);
        f.setName(n);
        f.setSortOrder(existing.size());
        f.setCreatedAt(LocalDateTime.now());
        f.setUpdatedAt(LocalDateTime.now());
        folderMapper.insert(f);
        return f;
    }

    @Transactional
    public SandboxCharacterFolder rename(Long userId, Long id, String name) {
        SandboxCharacterFolder f = owned(userId, id);
        String n = cleanName(name);
        ensureUnique(list(userId), n, id);
        f.setName(n);
        f.setUpdatedAt(LocalDateTime.now());
        folderMapper.updateById(f);
        return f;
    }

    /** 删除文件夹；里面的角色保留，folder_id 清空（回到未归档）。返回移回未归档的角色数 */
    @Transactional
    public int delete(Long userId, Long id) {
        owned(userId, id);
        int moved = characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getUserId, userId)
                .eq(SandboxCharacter::getFolderId, id)
                .set(SandboxCharacter::getFolderId, null));
        folderMapper.delete(new LambdaQueryWrapper<SandboxCharacterFolder>()
                .eq(SandboxCharacterFolder::getId, id)
                .eq(SandboxCharacterFolder::getUserId, userId));
        return moved;
    }

    /** 把角色移进文件夹；folderId 为 null 时移回未归档 */
    @Transactional
    public SandboxCharacter moveCharacter(Long userId, Long characterId, Long folderId) {
        validUser(userId);
        SandboxCharacter c = characterMapper.selectById(characterId);
        if (c == null || !userId.equals(c.getUserId())) throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        if (folderId != null) owned(userId, folderId);
        characterMapper.update(null, new LambdaUpdateWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getId, characterId)
                .eq(SandboxCharacter::getUserId, userId)
                .set(SandboxCharacter::getFolderId, folderId));
        c.setFolderId(folderId);
        return c;
    }

    private SandboxCharacterFolder owned(Long userId, Long id) {
        validUser(userId);
        SandboxCharacterFolder f = id == null ? null : folderMapper.selectById(id);
        if (f == null || !userId.equals(f.getUserId())) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "文件夹不存在或已删除");
        return f;
    }

    private static String cleanName(String name) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "请输入文件夹名称");
        if (n.codePointCount(0, n.length()) > NAME_MAX) throw new BusinessException(ResultCode.PARAM_ERROR, "文件夹名称不能超过" + NAME_MAX + "个字");
        return n;
    }

    private static void ensureUnique(List<SandboxCharacterFolder> existing, String name, Long exceptId) {
        for (SandboxCharacterFolder f : existing) {
            if (name.equals(f.getName()) && !f.getId().equals(exceptId)) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "已经有名为「" + name + "」的文件夹");
            }
        }
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
