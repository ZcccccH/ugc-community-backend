package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxWorldRelationRequest;
import com.ugccommunity.backend.entity.SandboxWorldRelation;
import com.ugccommunity.backend.entity.SandboxWorldRelationDismissed;
import com.ugccommunity.backend.entity.SandboxWorldview;
import com.ugccommunity.backend.mapper.SandboxWorldRelationDismissedMapper;
import com.ugccommunity.backend.mapper.SandboxWorldRelationMapper;
import com.ugccommunity.backend.mapper.SandboxWorldviewMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/** 世界观之间的关系（sandbox_world_relation）。结构和角色关系一致，但没有旧字段需要同步。 */
@Service
public class SandboxWorldRelationService {
    private static final Pattern COLOR = Pattern.compile("^#[0-9A-Fa-f]{6}([0-9A-Fa-f]{2})?$");
    private static final int LABEL_MAX = 100;
    private static final int NOTE_MAX = 500;

    private final SandboxWorldRelationMapper relationMapper;
    private final SandboxWorldviewMapper worldviewMapper;
    private final SandboxWorldRelationDismissedMapper dismissedMapper;

    public SandboxWorldRelationService(SandboxWorldRelationMapper relationMapper, SandboxWorldviewMapper worldviewMapper, SandboxWorldRelationDismissedMapper dismissedMapper) {
        this.relationMapper = relationMapper;
        this.worldviewMapper = worldviewMapper;
        this.dismissedMapper = dismissedMapper;
    }

    public List<SandboxWorldRelation> listRelations(Long userId) {
        validUser(userId);
        List<SandboxWorldRelation> list = relationMapper.selectList(new LambdaQueryWrapper<SandboxWorldRelation>()
                .eq(SandboxWorldRelation::getUserId, userId)
                .orderByAsc(SandboxWorldRelation::getId));
        return list == null ? new ArrayList<SandboxWorldRelation>() : list;
    }

    @Transactional
    public SandboxWorldRelation createRelation(Long userId, SandboxWorldRelationRequest r) {
        validUser(userId);
        if (r == null || r.getFromWorldviewId() == null || r.getToWorldviewId() == null) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "请选择关系两端的世界观");
        }
        if (r.getFromWorldviewId().equals(r.getToWorldviewId())) {
            throw new BusinessException(ResultCode.PARAM_ERROR, "不能和自己建立关系");
        }
        ownedWorldview(userId, r.getFromWorldviewId());
        ownedWorldview(userId, r.getToWorldviewId());
        for (SandboxWorldRelation x : listRelations(userId)) {
            if (x.connects(r.getFromWorldviewId(), r.getToWorldviewId())) {
                throw new BusinessException(ResultCode.DATA_EXIST, "这两个世界观之间已经有关系了，请直接编辑");
            }
        }
        SandboxWorldRelation x = new SandboxWorldRelation();
        x.setUserId(userId);
        x.setFromWorldviewId(r.getFromWorldviewId());
        x.setToWorldviewId(r.getToWorldviewId());
        x.setDirection(SandboxWorldRelation.ONE_WAY);
        applyRequest(x, r);
        x.setCreatedAt(LocalDateTime.now());
        x.setUpdatedAt(LocalDateTime.now());
        relationMapper.insert(x);
        // 用户重新建立了这一对世界观的关系：之前的删除记录作废
        forgetDismissed(userId, x.getFromWorldviewId(), x.getToWorldviewId());
        return x;
    }

    @Transactional
    public SandboxWorldRelation updateRelation(Long userId, Long id, SandboxWorldRelationRequest r) {
        SandboxWorldRelation x = ownedRelation(userId, id);
        if (r == null) throw new BusinessException(ResultCode.PARAM_ERROR);
        applyRequest(x, r);
        x.setUpdatedAt(LocalDateTime.now());
        relationMapper.updateById(x);
        return x;
    }

    @Transactional
    public void deleteRelation(Long userId, Long id) {
        SandboxWorldRelation x = ownedRelation(userId, id);
        relationMapper.deleteById(x.getId());
        rememberDismissed(userId, x.getFromWorldviewId(), x.getToWorldviewId());
    }

    // ------------------------------------------------------------------ 删掉过的关系

    /** 用户删掉过 a 与 b 之间的世界关系，之后没有自己重新建立 */
    public boolean isDismissed(Long userId, Long a, Long b) {
        return findDismissed(userId, a, b) != null;
    }

    private List<SandboxWorldRelationDismissed> listDismissed(Long userId) {
        List<SandboxWorldRelationDismissed> list = dismissedMapper.selectList(new LambdaQueryWrapper<SandboxWorldRelationDismissed>().eq(SandboxWorldRelationDismissed::getUserId, userId));
        return list == null ? new ArrayList<SandboxWorldRelationDismissed>() : list;
    }

    private SandboxWorldRelationDismissed findDismissed(Long userId, Long a, Long b) {
        if (a == null || b == null) return null;
        for (SandboxWorldRelationDismissed d : listDismissed(userId)) if (d.connects(a, b)) return d;
        return null;
    }

    private void rememberDismissed(Long userId, Long a, Long b) {
        if (findDismissed(userId, a, b) != null) return;
        SandboxWorldRelationDismissed d = new SandboxWorldRelationDismissed();
        d.setUserId(userId);
        d.setWorldviewAId(Math.min(a, b));
        d.setWorldviewBId(Math.max(a, b));
        d.setCreatedAt(LocalDateTime.now());
        dismissedMapper.insert(d);
    }

    private void forgetDismissed(Long userId, Long a, Long b) {
        SandboxWorldRelationDismissed d = findDismissed(userId, a, b);
        if (d != null) dismissedMapper.deleteById(d.getId());
    }

    /** 世界观删除后，删掉它参与的所有世界关系 */
    @Transactional
    public void onWorldviewDeleted(Long userId, Long worldviewId) {
        validUser(userId);
        for (SandboxWorldRelation x : listRelations(userId)) {
            if (worldviewId.equals(x.getFromWorldviewId()) || worldviewId.equals(x.getToWorldviewId())) {
                relationMapper.deleteById(x.getId());
            }
        }
        for (SandboxWorldRelationDismissed d : listDismissed(userId)) {
            if (worldviewId.equals(d.getWorldviewAId()) || worldviewId.equals(d.getWorldviewBId())) dismissedMapper.deleteById(d.getId());
        }
    }

    // ------------------------------------------------------------------ 工具

    private void applyRequest(SandboxWorldRelation x, SandboxWorldRelationRequest r) {
        if (r.getDirection() != null) {
            if (!SandboxWorldRelation.ONE_WAY.equals(r.getDirection()) && !SandboxWorldRelation.TWO_WAY.equals(r.getDirection())) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "关系方向只能是单向或双向");
            }
            x.setDirection(r.getDirection());
        }
        // updateById 会跳过 null 字段，清空文字时写空串
        if (r.getLabel() != null) x.setLabel(checkLength(r.getLabel(), LABEL_MAX, "关系名称"));
        if (r.getReverseLabel() != null) x.setReverseLabel(checkLength(r.getReverseLabel(), LABEL_MAX, "反向关系名称"));
        if (!x.isTwoWay() && x.getReverseLabel() != null) x.setReverseLabel("");
        if (r.getNote() != null) x.setNote(checkLength(r.getNote(), NOTE_MAX, "说明"));
        if (r.getColor() != null) {
            String color = r.getColor().trim();
            if (!color.isEmpty() && !COLOR.matcher(color).matches()) {
                throw new BusinessException(ResultCode.PARAM_ERROR, "颜色格式应为 #RRGGBB");
            }
            x.setColor(color);
        }
    }

    private static String checkLength(String s, int max, String field) {
        String t = s.trim();
        if (t.length() > max) throw new BusinessException(ResultCode.PARAM_ERROR, field + "不能超过 " + max + " 个字");
        return t;
    }

    private SandboxWorldRelation ownedRelation(Long userId, Long id) {
        validUser(userId);
        SandboxWorldRelation x = id == null ? null : relationMapper.selectById(id);
        if (x == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST);
        if (!userId.equals(x.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return x;
    }

    private SandboxWorldview ownedWorldview(Long userId, Long id) {
        SandboxWorldview w = worldviewMapper.selectById(id);
        if (w == null) throw new BusinessException(ResultCode.DATA_NOT_EXIST, "世界观不存在");
        if (!userId.equals(w.getUserId())) throw new BusinessException(ResultCode.NO_PERMISSION);
        return w;
    }

    private static void validUser(Long userId) {
        if (userId == null) throw new BusinessException(ResultCode.UNAUTHORIZED);
    }
}
