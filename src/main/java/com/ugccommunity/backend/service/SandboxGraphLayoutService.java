package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxGraphLayoutRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxGraphLayout;
import com.ugccommunity.backend.entity.SandboxOrganization;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxCharacterMapper;
import com.ugccommunity.backend.mapper.SandboxGraphLayoutMapper;
import com.ugccommunity.backend.mapper.SandboxOrganizationMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * 关系网排版（sandbox_graph_layout）。只保存「用户排过版」的视图；没有保存过的视图由客户端自动布局。
 * 保存是整体替换：客户端的撤回、清除都通过再保存一次实现，服务端不记录历史。
 */
@Service
public class SandboxGraphLayoutService {
    static final int MAX_ITEMS = 2000;
    static final double MAX_ABS = 1_000_000d;
    private static final Pattern SCOPE = Pattern.compile("^(all|world:[1-9][0-9]{0,18}|org:[1-9][0-9]{0,18})$");

    private final SandboxGraphLayoutMapper layoutMapper;
    private final SandboxCharacterMapper characterMapper;
    private final SandboxWorldviewMapper worldviewMapper;
    private final SandboxOrganizationMapper organizationMapper;

    public SandboxGraphLayoutService(SandboxGraphLayoutMapper layoutMapper, SandboxCharacterMapper characterMapper, SandboxWorldviewMapper worldviewMapper, SandboxOrganizationMapper organizationMapper) {
        this.layoutMapper = layoutMapper;
        this.characterMapper = characterMapper;
        this.worldviewMapper = worldviewMapper;
        this.organizationMapper = organizationMapper;
    }

    public List<SandboxGraphLayout> listLayout(Long userId, String scope) {
        validUser(userId);
        String s = checkScope(userId, scope);
        List<SandboxGraphLayout> list = layoutMapper.selectList(new LambdaQueryWrapper<SandboxGraphLayout>()
                .eq(SandboxGraphLayout::getUserId, userId)
                .eq(SandboxGraphLayout::getScope, s)
                .orderByAsc(SandboxGraphLayout::getCharacterId));
        return list == null ? Collections.<SandboxGraphLayout>emptyList() : list;
    }

    /** 用 items 整体替换 scope 下的排版；同一角色出现多次时以最后一次为准 */
    @Transactional
    public List<SandboxGraphLayout> saveLayout(Long userId, SandboxGraphLayoutRequest r) {
        validUser(userId);
        if (r == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        String scope = checkScope(userId, r.getScope());
        List<SandboxGraphLayoutRequest.Item> items = r.getItems() == null ? Collections.<SandboxGraphLayoutRequest.Item>emptyList() : r.getItems();
        if (items.size() > MAX_ITEMS) throw new BusinessException(ResultCode.PARAM_ERROR, "一次最多保存 " + MAX_ITEMS + " 个角色的位置");

        Map<Long, SandboxGraphLayoutRequest.Item> byCharacter = new LinkedHashMap<Long, SandboxGraphLayoutRequest.Item>();
        for (SandboxGraphLayoutRequest.Item it : items) {
            if (it == null || it.getCharacterId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "缺少角色");
            checkCoordinate(it.getX(), "x");
            checkCoordinate(it.getY(), "y");
            if (it.getZ() != null) checkCoordinate(it.getZ(), "z");
            byCharacter.put(it.getCharacterId(), it);
        }
        if (!byCharacter.isEmpty()) {
            Set<Long> owned = ownedCharacterIds(userId);
            for (Long id : byCharacter.keySet()) {
                if (!owned.contains(id)) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "角色不存在");
            }
        }

        layoutMapper.delete(new LambdaQueryWrapper<SandboxGraphLayout>()
                .eq(SandboxGraphLayout::getUserId, userId)
                .eq(SandboxGraphLayout::getScope, scope));
        List<SandboxGraphLayout> saved = new ArrayList<SandboxGraphLayout>();
        LocalDateTime now = LocalDateTime.now();
        for (Map.Entry<Long, SandboxGraphLayoutRequest.Item> e : byCharacter.entrySet()) {
            SandboxGraphLayout x = new SandboxGraphLayout();
            x.setUserId(userId);
            x.setScope(scope);
            x.setCharacterId(e.getKey());
            x.setX(e.getValue().getX());
            x.setY(e.getValue().getY());
            x.setZ(e.getValue().getZ() == null ? 0d : e.getValue().getZ());
            x.setUpdatedAt(now);
            layoutMapper.insert(x);
            saved.add(x);
        }
        return saved;
    }

    /** 角色删除后清掉它在所有视图里的位置 */
    @Transactional
    public void onCharacterDeleted(Long userId, Long characterId) {
        validUser(userId);
        layoutMapper.delete(new LambdaQueryWrapper<SandboxGraphLayout>()
                .eq(SandboxGraphLayout::getUserId, userId)
                .eq(SandboxGraphLayout::getCharacterId, characterId));
    }

    /** 世界观删除后清掉这个世界观视图的排版 */
    @Transactional
    public void onWorldviewDeleted(Long userId, Long worldviewId) {
        validUser(userId);
        layoutMapper.delete(new LambdaQueryWrapper<SandboxGraphLayout>()
                .eq(SandboxGraphLayout::getUserId, userId)
                .eq(SandboxGraphLayout::getScope, SandboxGraphLayout.worldScope(worldviewId)));
    }

    /** 组织删除后清掉这个组织详情的排版 */
    @Transactional
    public void onOrganizationDeleted(Long userId, Long organizationId) {
        validUser(userId);
        layoutMapper.delete(new LambdaQueryWrapper<SandboxGraphLayout>()
                .eq(SandboxGraphLayout::getUserId, userId)
                .eq(SandboxGraphLayout::getScope, SandboxGraphLayout.organizationScope(organizationId)));
    }

    // ------------------------------------------------------------------ 工具

    String checkScope(Long userId, String raw) {
        String scope = raw == null ? "" : raw.trim();
        if (!SCOPE.matcher(scope).matches()) throw new BusinessException(ResultCode.PARAM_ERROR, "视图参数应为 all、world:世界观ID 或 org:组织ID");
        if (scope.startsWith(SandboxGraphLayout.SCOPE_WORLD_PREFIX)) {
            Long worldviewId;
            try {
                worldviewId = Long.valueOf(scope.substring(SandboxGraphLayout.SCOPE_WORLD_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "视图参数应为 all、world:世界观ID 或 org:组织ID");
            }
            SandboxWorldview w = worldviewMapper.selectById(worldviewId);
            if (w == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "世界观不存在");
            if (!userId.equals(w.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        } else if (scope.startsWith(SandboxGraphLayout.SCOPE_ORGANIZATION_PREFIX)) {
            Long organizationId;
            try {
                organizationId = Long.valueOf(scope.substring(SandboxGraphLayout.SCOPE_ORGANIZATION_PREFIX.length()));
            } catch (NumberFormatException e) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "视图参数应为 all、world:世界观ID 或 org:组织ID");
            }
            SandboxOrganization o = organizationMapper.selectById(organizationId);
            if (o == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "组织不存在");
            if (!userId.equals(o.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        }
        return scope;
    }

    private static void checkCoordinate(Double v, String name) {
        if (v == null || v.isNaN() || v.isInfinite() || Math.abs(v) > MAX_ABS) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "坐标 " + name + " 无效");
        }
    }

    private Set<Long> ownedCharacterIds(Long userId) {
        List<SandboxCharacter> list = characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>()
                .eq(SandboxCharacter::getUserId, userId)
                .select(SandboxCharacter::getId, SandboxCharacter::getUserId));
        Set<Long> ids = new HashSet<Long>();
        if (list != null) for (SandboxCharacter c : list) if (userId.equals(c.getUserId())) ids.add(c.getId());
        return ids;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
