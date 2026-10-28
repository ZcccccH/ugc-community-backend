package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.common.upload.LocalUploadResolver;
import com.ugccommunity.backend.dto.request.SandboxMapRequest;
import com.ugccommunity.backend.entity.SandboxMap;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxMapMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/** 世界地图与区域地图（sandbox_map）。 */
@Service
public class SandboxMapService {
    private static final int NAME_MAX = 80;
    private static final int REGION_KEY_MAX = 64;
    private static final int URL_MAX = 500;
    private static final int TERRAIN_MAX = 600_000;
    private static final int OVERLAY_MAX = 600_000;
    private static final int VIEW_MAX = 8_000;

    private final SandboxMapMapper mapMapper;
    private final SandboxWorldviewMapper worldviewMapper;

    public SandboxMapService(SandboxMapMapper mapMapper, SandboxWorldviewMapper worldviewMapper) {
        this.mapMapper = mapMapper;
        this.worldviewMapper = worldviewMapper;
    }

    private LocalUploadResolver localUploads;

    @Autowired(required = false)
    public void setLocalUploads(LocalUploadResolver localUploads) {
        this.localUploads = localUploads;
    }

    /**
     * 返回给客户端之前：底图地址指向外部存储、而文件在本机 uploads 里也有时，改用本站地址。
     * 外部域名失效时底图仍然能显示；库里保存的地址不动。
     */
    private SandboxMap present(SandboxMap map) {
        if (map != null && localUploads != null) map.setBackgroundUrl(localUploads.preferLocal(map.getBackgroundUrl()));
        return map;
    }

    /** 一个世界观下的全部地图（世界地图和各级区域地图） */
    public List<SandboxMap> listMaps(Long userId, Long worldviewId) {
        validUser(userId);
        LambdaQueryWrapper<SandboxMap> q = new LambdaQueryWrapper<SandboxMap>().eq(SandboxMap::getUserId, userId);
        if (worldviewId != null) q.eq(SandboxMap::getWorldviewId, worldviewId);
        List<SandboxMap> list = mapMapper.selectList(q.orderByAsc(SandboxMap::getId));
        if (list == null) return new ArrayList<SandboxMap>();
        for (SandboxMap map : list) present(map);
        return list;
    }

    public SandboxMap getMap(Long userId, Long id) {
        return present(ownedMap(userId, id));
    }

    /**
     * 新建地图。幂等：世界观已有世界地图、或这个区域已有区域地图时，直接返回已有的那张，
     * 所以重复进入、重复迁移都不会多出一张。
     */
    @Transactional
    public SandboxMap createMap(Long userId, SandboxMapRequest r) {
        validUser(userId);
        if (r == null || r.getWorldviewId() == null) throw new BusinessException(ResultCode.PARAM_ERROR, "请选择世界观");
        SandboxWorldview world = ownedWorldview(userId, r.getWorldviewId());
        String slot = SandboxMap.ROOT_SLOT;
        String regionKey = null;
        if (r.getParentMapId() != null) {
            SandboxMap parent = ownedMap(userId, r.getParentMapId());
            if (!parent.getWorldviewId().equals(world.getId())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "上级地图不属于这个世界观");
            }
            regionKey = r.getParentRegionKey() == null ? "" : r.getParentRegionKey().trim();
            if (regionKey.isEmpty() || regionKey.length() > REGION_KEY_MAX) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "区域标识无效");
            }
            slot = parent.getId() + ":" + regionKey;
        }
        SandboxMap existing = bySlot(userId, world.getId(), slot);
        if (existing != null) return present(existing);

        SandboxMap x = new SandboxMap();
        x.setUserId(userId);
        x.setWorldviewId(world.getId());
        x.setParentMapId(r.getParentMapId());
        x.setParentRegionKey(regionKey);
        x.setSlot(slot);
        String name = r.getName() == null ? "" : r.getName().trim();
        x.setName(checkLength(name.isEmpty() ? world.getName() : name, NAME_MAX, "地图名称"));
        x.setBackgroundUrl(checkLength(r.getBackgroundUrl(), URL_MAX, "背景图地址"));
        x.setTerrain(checkLength(r.getTerrain(), TERRAIN_MAX, "地形数据"));
        x.setOverlay(checkLength(r.getOverlay(), OVERLAY_MAX, "标记数据"));
        x.setView(checkLength(r.getView(), VIEW_MAX, "显示选项"));
        x.setVersion(1);
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        try {
            mapMapper.insert(x);
        } catch (DuplicateKeyException e) {
            // 同一个位置被并发创建：返回先写入的那张
            SandboxMap raced = bySlot(userId, world.getId(), slot);
            if (raced == null) throw e;
            return present(raced);
        }
        return present(x);
    }

    @Transactional
    public SandboxMap updateMap(Long userId, Long id, SandboxMapRequest r) {
        SandboxMap x = ownedMap(userId, id);
        if (r == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        int current = x.getVersion() == null ? 1 : x.getVersion();
        if (r.getVersion() != null && r.getVersion() != current) throw stale();
        if (r.getName() != null) {
            String name = r.getName().trim();
            if (name.isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR, "地图名称不能为空");
            x.setName(checkLength(name, NAME_MAX, "地图名称"));
        }
        // update 会跳过 null 字段，清空时写空串
        if (r.getBackgroundUrl() != null) x.setBackgroundUrl(checkLength(r.getBackgroundUrl(), URL_MAX, "背景图地址"));
        if (r.getTerrain() != null) x.setTerrain(checkLength(r.getTerrain(), TERRAIN_MAX, "地形数据"));
        if (r.getOverlay() != null) x.setOverlay(checkLength(r.getOverlay(), OVERLAY_MAX, "标记数据"));
        if (r.getView() != null) x.setView(checkLength(r.getView(), VIEW_MAX, "显示选项"));
        x.setVersion(current + 1);
        x.setUpdatedAt(LocalDateTime.now());
        int rows = mapMapper.update(x, new LambdaUpdateWrapper<SandboxMap>()
                .eq(SandboxMap::getId, x.getId())
                .eq(SandboxMap::getUserId, userId)
                .eq(SandboxMap::getVersion, current));
        if (rows == 0) throw stale();
        return present(x);
    }

    /** 删除一张地图及其下的全部区域地图 */
    @Transactional
    public void deleteMap(Long userId, Long id) {
        SandboxMap root = ownedMap(userId, id);
        List<SandboxMap> all = listMaps(userId, root.getWorldviewId());
        List<Long> pending = new ArrayList<Long>();
        pending.add(root.getId());
        while (!pending.isEmpty()) {
            Long current = pending.remove(pending.size() - 1);
            for (SandboxMap m : all) {
                if (current.equals(m.getParentMapId())) pending.add(m.getId());
            }
            mapMapper.deleteById(current);
        }
    }

    /** 世界观删除后，删掉它的全部地图 */
    @Transactional
    public void onWorldviewDeleted(Long userId, Long worldviewId) {
        validUser(userId);
        mapMapper.delete(new LambdaQueryWrapper<SandboxMap>()
                .eq(SandboxMap::getUserId, userId).eq(SandboxMap::getWorldviewId, worldviewId));
    }

    // ------------------------------------------------------------------ 工具

    private SandboxMap bySlot(Long userId, Long worldviewId, String slot) {
        List<SandboxMap> list = mapMapper.selectList(new LambdaQueryWrapper<SandboxMap>()
                .eq(SandboxMap::getUserId, userId)
                .eq(SandboxMap::getWorldviewId, worldviewId)
                .eq(SandboxMap::getSlot, slot));
        return list == null || list.isEmpty() ? null : list.get(0);
    }

    private SandboxMap ownedMap(Long userId, Long id) {
        validUser(userId);
        SandboxMap x = id == null ? null : mapMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "地图不存在");
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
        return new BusinessException(ResultCode.DATA_EXIST, "这张地图已在别处被修改，请刷新后再改");
    }

    /** null 视为空串；地形和标记数据不裁剪内容，只检查长度 */
    private static String checkLength(String s, int max, String field) {
        if (s == null) return "";
        if (s.length() > max) throw new BusinessException(ResultCode.PARAM_ERROR, field + "过大");
        return s;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
