package com.ugccommunity.backend.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.ugccommunity.backend.common.exception.BusinessException;
import com.ugccommunity.backend.common.result.ResultCode;
import com.ugccommunity.backend.dto.request.SandboxChatRequest;
import com.ugccommunity.backend.dto.request.SandboxItemRequest;
import com.ugccommunity.backend.dto.request.SandboxMountRequest;
import com.ugccommunity.backend.entity.*;
import com.ugccommunity.backend.mapper.*;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import java.util.Map;
import java.util.HashMap;
import java.util.ArrayList;

@Service
public class SandboxService {
    private final SandboxWorldviewMapper worldviewMapper; private final SandboxOrganizationMapper organizationMapper; private final SandboxCharacterMapper characterMapper;
    private final SandboxScriptMapper scriptMapper; private final SandboxMountMapper mountMapper; private final SandboxChatSessionMapper sessionMapper; private final SandboxChatMessageMapper messageMapper;
    private final DeepSeekClient deepSeekClient;
    private final SandboxRelationService relationService;
    private final SandboxWorldRelationService worldRelationService;
    private final SandboxGraphLayoutService layoutService;
    private final SandboxRecordService recordService;
    private final SandboxMapService mapService;
    private final SandboxTimelineService timelineService;
    /** 角色档案文件夹；setter 注入，现有构造参数不变 */
    private SandboxCharacterFolderMapper folderMapper;
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    public void setFolderMapper(SandboxCharacterFolderMapper folderMapper){this.folderMapper=folderMapper;}
    public SandboxService(SandboxWorldviewMapper worldviewMapper, SandboxOrganizationMapper organizationMapper, SandboxCharacterMapper characterMapper,
                          SandboxScriptMapper scriptMapper, SandboxMountMapper mountMapper, SandboxChatSessionMapper sessionMapper, SandboxChatMessageMapper messageMapper,
                          DeepSeekClient deepSeekClient, SandboxRelationService relationService,
                          SandboxWorldRelationService worldRelationService, SandboxGraphLayoutService layoutService,
                          SandboxRecordService recordService, SandboxMapService mapService, SandboxTimelineService timelineService) {
        this.worldviewMapper=worldviewMapper; this.organizationMapper=organizationMapper; this.characterMapper=characterMapper; this.scriptMapper=scriptMapper; this.mountMapper=mountMapper; this.sessionMapper=sessionMapper; this.messageMapper=messageMapper;
        this.deepSeekClient=deepSeekClient;
        this.relationService=relationService;
        this.worldRelationService=worldRelationService;
        this.layoutService=layoutService;
        this.recordService=recordService;
        this.mapService=mapService;
        this.timelineService=timelineService;
    }
    @org.springframework.beans.factory.annotation.Autowired private SandboxWorldGroupMapper worldGroupMapper;
    public List<SandboxWorldGroup> listWorldGroups(Long userId){validUser(userId); return worldGroupMapper.selectList(new LambdaQueryWrapper<SandboxWorldGroup>().eq(SandboxWorldGroup::getUserId,userId).orderByAsc(SandboxWorldGroup::getId));}
    public SandboxWorldGroup createWorldGroup(Long userId,String name){validUser(userId); SandboxWorldGroup g=new SandboxWorldGroup();g.setUserId(userId);g.setName(worldGroupName(name));worldGroupMapper.insert(g);return g;}
    public SandboxWorldGroup renameWorldGroup(Long userId,Long id,String name){SandboxWorldGroup g=owned(worldGroupMapper,id,userId);g.setName(worldGroupName(name));worldGroupMapper.updateById(g);return g;}
    private String worldGroupName(String name){String n=required(name);if(n.length()>80)throw new BusinessException(ResultCode.PARAM_ERROR,"名称最多80个字");return n;}
    @Transactional public SandboxWorldview createWorldInGroup(Long userId,Long groupId,String name,String description,String content){return createWorldviewInGroup(userId,groupId,name,description,content);}
    /** 把世界观里的某个世界设为主世界，其余世界就是平行世界 */
    public SandboxWorldGroup setMainWorld(Long userId,Long groupId,Long worldId){SandboxWorldGroup g=owned(worldGroupMapper,groupId,userId);SandboxWorldview w=owned(worldviewMapper,worldId,userId);if(!groupId.equals(w.getWorldGroupId()))throw new BusinessException(ResultCode.PARAM_ERROR,"这个世界不在该世界观里");g.setMainWorldId(worldId);worldGroupMapper.updateById(g);return g;}
    /** 删除世界观：里面的世界按删除世界的方式逐个删除（连同地图、时间线、设定记录、世界关系），角色和组织保留 */
    @Transactional public void deleteWorldGroup(Long userId,Long groupId){owned(worldGroupMapper,groupId,userId);for(SandboxWorldview w:worldviewMapper.selectList(new LambdaQueryWrapper<SandboxWorldview>().eq(SandboxWorldview::getUserId,userId).eq(SandboxWorldview::getWorldGroupId,groupId)))deleteWorldview(userId,w.getId());worldGroupMapper.deleteById(groupId);}
    @Transactional public SandboxWorldview moveWorldToGroup(Long userId,Long id,Long groupId){owned(worldGroupMapper,groupId,userId);SandboxWorldview w=owned(worldviewMapper,id,userId);Long oldGroup=w.getWorldGroupId();w.setWorldGroupId(groupId);worldviewMapper.updateById(w);
        // 移走的是原世界观的主世界时，原世界观不再指向它
        if(oldGroup!=null&&!oldGroup.equals(groupId)) worldGroupMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SandboxWorldGroup>().eq("user_id",userId).eq("id",oldGroup).eq("main_world_id",id).set("main_world_id",null));
        return w;}
    private void validUser(Long userId){if(userId==null) throw new BusinessException(ResultCode.UNAUTHORIZED);}
    private String required(String value){if(value==null||value.trim().isEmpty()) throw new BusinessException(ResultCode.PARAM_ERROR,"名称不能为空"); return value.trim();}
    public List<SandboxWorldview> listWorldviews(Long userId){validUser(userId); return worldviewMapper.selectList(new LambdaQueryWrapper<SandboxWorldview>().eq(SandboxWorldview::getUserId,userId).orderByDesc(SandboxWorldview::getUpdatedAt));}
    @Transactional public SandboxWorldview createWorldview(Long userId,String name,String description){return createWorldview(userId,name,description,"");}
    @Transactional public SandboxWorldview createWorldview(Long userId,String name,String description,String content){return createWorldviewInGroup(userId,null,name,description,content);}
    @Transactional public SandboxWorldview createWorldviewInGroup(Long userId,Long groupId,String name,String description,String content){validUser(userId); SandboxWorldview x=new SandboxWorldview(); x.setUserId(userId); x.setName(worldGroupName(name)); if(groupId!=null)owned(worldGroupMapper,groupId,userId); x.setWorldGroupId(groupId==null?createWorldGroup(userId,name).getId():groupId); x.setDescription(description); x.setContent(content); x.setLevel(0); x.setSortOrder(0); x.setCanvasX(80d); x.setCanvasY(80d); x.setCreatedAt(LocalDateTime.now()); x.setUpdatedAt(LocalDateTime.now()); worldviewMapper.insert(x); return x;}
    public SandboxWorldview updateWorldview(Long userId,Long id,String name,String description,String content){SandboxWorldview x=owned(worldviewMapper,id,userId); x.setName(required(name)); x.setDescription(description); x.setContent(content); x.setUpdatedAt(LocalDateTime.now()); worldviewMapper.updateById(x); return x;}
    public SandboxWorldview updateWorldviewLayout(Long userId,Long id,Integer level,Integer sortOrder,Double canvasX,Double canvasY){SandboxWorldview x=owned(worldviewMapper,id,userId); x.setLevel(level); x.setSortOrder(sortOrder); x.setCanvasX(canvasX); x.setCanvasY(canvasY); x.setUpdatedAt(LocalDateTime.now()); worldviewMapper.updateById(x); return x;}
    // 删除世界观时一并删掉它参与的世界关系、这个世界观视图的排版、它的设定记录和地图
    @Transactional public void deleteWorldview(Long userId,Long id){int n=worldviewMapper.delete(new LambdaQueryWrapper<SandboxWorldview>().eq(SandboxWorldview::getId,id).eq(SandboxWorldview::getUserId,userId)); if(n>0){worldGroupMapper.update(null,new com.baomidou.mybatisplus.core.conditions.update.UpdateWrapper<SandboxWorldGroup>().eq("user_id",userId).eq("main_world_id",id).set("main_world_id",null)); worldRelationService.onWorldviewDeleted(userId,id); layoutService.onWorldviewDeleted(userId,id); recordService.onWorldviewDeleted(userId,id); mapService.onWorldviewDeleted(userId,id); timelineService.onWorldviewDeleted(userId,id); unlinkDeletedWorldview(userId,id);}}
    public List<SandboxOrganization> listOrganizations(Long userId,Long worldviewId){validUser(userId); LambdaQueryWrapper<SandboxOrganization> q=new LambdaQueryWrapper<SandboxOrganization>().eq(SandboxOrganization::getUserId,userId); if(worldviewId!=null)q.eq(SandboxOrganization::getWorldviewId,worldviewId); return organizationMapper.selectList(q.orderByDesc(SandboxOrganization::getUpdatedAt));}
    public SandboxOrganization createOrganization(Long userId,SandboxItemRequest r){validUser(userId); SandboxOrganization x=new SandboxOrganization(); x.setUserId(userId); x.setWorldviewId(r.getWorldviewId()); x.setName(required(r.getName())); x.setDescription(r.getDescription()); x.setContent(r.getContent()); applyOrganizationRelation(x,r); x.setSortOrder(0); x.setCanvasX(50d); x.setCanvasY(50d); x.setCreatedAt(LocalDateTime.now()); x.setUpdatedAt(LocalDateTime.now()); organizationMapper.insert(x); return x;}
    public SandboxOrganization updateOrganization(Long userId,Long id,SandboxItemRequest r){SandboxOrganization x=owned(organizationMapper,id,userId); x.setWorldviewId(r.getWorldviewId()); x.setName(required(r.getName())); x.setDescription(r.getDescription()); if(r.getContent()!=null) x.setContent(r.getContent()); applyOrganizationRelation(x,r); x.setUpdatedAt(LocalDateTime.now()); organizationMapper.updateById(x); return x;}
    private void applyOrganizationRelation(SandboxOrganization x,SandboxItemRequest r){if(r.getRelationOrganizationIds()!=null) x.setRelationOrganizationIds(r.getRelationOrganizationIds()); if(r.getRelationLabel()!=null) x.setRelationLabel(notNullString(r.getRelationLabel())); if(r.getRelationColor()!=null) x.setRelationColor(notNullString(r.getRelationColor())); if(r.getRelationArrow()!=null) x.setRelationArrow(r.getRelationArrow()); if(r.getRelationArrowStyle()!=null) x.setRelationArrowStyle(notNullString(r.getRelationArrowStyle()));}
    public SandboxOrganization updateOrganizationLayout(Long userId,Long id,Integer sortOrder,Double canvasX,Double canvasY){SandboxOrganization x=owned(organizationMapper,id,userId); x.setSortOrder(sortOrder); x.setCanvasX(canvasX); x.setCanvasY(canvasY); x.setUpdatedAt(LocalDateTime.now()); organizationMapper.updateById(x); return x;}
    @Transactional public void deleteOrganization(Long userId,Long id){int n=organizationMapper.delete(new LambdaQueryWrapper<SandboxOrganization>().eq(SandboxOrganization::getId,id).eq(SandboxOrganization::getUserId,userId)); if(n>0){layoutService.onOrganizationDeleted(userId,id); unlinkDeletedOrganization(userId,id);}}
    public List<SandboxCharacter> listCharacters(Long userId,Long worldviewId,Long organizationId){validUser(userId); LambdaQueryWrapper<SandboxCharacter> q=new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId,userId); if(worldviewId!=null)q.eq(SandboxCharacter::getWorldviewId,worldviewId); if(organizationId!=null)q.eq(SandboxCharacter::getOrganizationId,organizationId); return characterMapper.selectList(q.orderByDesc(SandboxCharacter::getUpdatedAt));}
    @Transactional public SandboxCharacter createCharacter(Long userId,SandboxItemRequest r){validUser(userId); SandboxCharacter x=new SandboxCharacter(); x.setUserId(userId); applyCharacter(x,r); x.setFolderId(ownedFolderOrNull(userId,r.getFolderId())); x.setSortOrder(0); x.setCanvasX(50d); x.setCanvasY(50d); x.setCreatedAt(LocalDateTime.now()); x.setUpdatedAt(LocalDateTime.now()); characterMapper.insert(x); relationService.syncFromLegacy(userId,x.getId()); return x;}
    @Transactional public SandboxCharacter updateCharacter(Long userId,Long id,SandboxItemRequest r){SandboxCharacter x=owned(characterMapper,id,userId); String oldContent=x.getContent(); java.util.Set<Long> oldLinked=idSet(x.getRelationCharacterIds()); applyCharacter(x,r); x.setUpdatedAt(LocalDateTime.now()); characterMapper.updateById(x); clearRemovedLinks(userId,id,r); clearCounterpartIfRemoved(userId,id,r); forgetDismissedForNewLinks(userId,id,oldLinked,r); relationService.syncFromLegacy(userId,id); relationService.syncFromTyped(userId,id,oldContent,x.getContent()); timelineService.reapplyToSheet(userId,id); SandboxCharacter saved=characterMapper.selectById(id); return saved==null?x:saved;}
    public SandboxCharacter updateCharacterLayout(Long userId,Long id,Integer sortOrder,Double canvasX,Double canvasY){SandboxCharacter x=owned(characterMapper,id,userId); x.setSortOrder(sortOrder); x.setCanvasX(canvasX); x.setCanvasY(canvasY); x.setUpdatedAt(LocalDateTime.now()); characterMapper.updateById(x); return x;}
    @Transactional public void deleteCharacter(Long userId,Long id){int n=characterMapper.delete(new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getId,id).eq(SandboxCharacter::getUserId,userId)); if(n>0){relationService.onCharacterDeleted(userId,id); layoutService.onCharacterDeleted(userId,id); timelineService.onCharacterDeleted(userId,id); unlinkDeletedCharacter(userId,id);}}
    public List<SandboxScript> listScripts(Long userId,Long worldviewId){validUser(userId); LambdaQueryWrapper<SandboxScript> q=new LambdaQueryWrapper<SandboxScript>().eq(SandboxScript::getUserId,userId); if(worldviewId!=null)q.eq(SandboxScript::getWorldviewId,worldviewId); return scriptMapper.selectList(q.orderByDesc(SandboxScript::getUpdatedAt));}
    public SandboxScript createScript(Long userId,SandboxItemRequest r){validUser(userId); SandboxScript x=new SandboxScript(); x.setUserId(userId); x.setWorldviewId(r.getWorldviewId()); x.setName(required(r.getName())); x.setDescription(r.getDescription()); x.setContent(r.getContent()); x.setCreatedAt(LocalDateTime.now()); x.setUpdatedAt(LocalDateTime.now()); scriptMapper.insert(x); return x;}
    public SandboxScript updateScript(Long userId,Long id,SandboxItemRequest r){SandboxScript x=owned(scriptMapper,id,userId); x.setWorldviewId(r.getWorldviewId()); x.setName(required(r.getName())); x.setDescription(r.getDescription()); x.setContent(r.getContent()); x.setUpdatedAt(LocalDateTime.now()); scriptMapper.updateById(x); return x;}
    public void deleteScript(Long userId,Long id){scriptMapper.delete(new LambdaQueryWrapper<SandboxScript>().eq(SandboxScript::getId,id).eq(SandboxScript::getUserId,userId));}
    @Transactional public SandboxMount saveMount(Long userId,SandboxMountRequest r){validUser(userId); SandboxMount x=mountMapper.selectById(userId); if(x==null){x=new SandboxMount();x.setUserId(userId);} x.setWorldviewIds(join(r.getWorldviewIds())); x.setMainCharacterId(r.getMainCharacterId()); x.setRelatedCharacterIds(join(r.getRelatedCharacterIds())); x.setScriptId(r.getScriptId()); x.setUpdatedAt(LocalDateTime.now()); if(x.getUserId().equals(userId)&&mountMapper.selectById(userId)==null)mountMapper.insert(x); else { mountMapper.updateById(x);
        // 挂载是整体保存：没选主角色 / 剧本就要真的清掉（updateById 会跳过 null，旧值会留着）
        LambdaUpdateWrapper<SandboxMount> u=new LambdaUpdateWrapper<SandboxMount>().eq(SandboxMount::getUserId,userId); boolean any=false;
        if(r.getMainCharacterId()==null){u.set(SandboxMount::getMainCharacterId,null);any=true;}
        if(r.getScriptId()==null){u.set(SandboxMount::getScriptId,null);any=true;}
        if(any) mountMapper.update(null,u); }
        return x;}
    public SandboxMount getMount(Long userId){validUser(userId); return mountMapper.selectById(userId);}
    public Map<String,Object> graph(Long userId){validUser(userId); Map<String,Object> result=new HashMap<String,Object>(); result.put("worldviews", listWorldviews(userId)); result.put("organizations", listOrganizations(userId,null)); result.put("characters", listCharacters(userId,null,null)); result.put("relations", relationService.listRelations(userId)); result.put("worldRelations", worldRelationService.listRelations(userId)); return result;}
    private String safe(String value){return value==null||value.trim().isEmpty()?"未设置":value.trim();}
    private String join(List<Long> ids){return ids==null?null:ids.stream().map(String::valueOf).collect(Collectors.joining(","));}
    /**
     * updateById 跳过 null 字段：客户端把世界观 / 组织列表清空时，worldview_id / organization_id 不会跟着清空，
     * 角色仍会按旧的主世界观被算进那个世界。客户端明确传了空列表时显式置空；没传列表的旧客户端不受影响。
     */
    private void clearRemovedLinks(Long userId,Long id,SandboxItemRequest r){
        boolean noWorld=r.getWorldviewIds()!=null&&r.getWorldviewIds().trim().isEmpty()&&r.getWorldviewId()==null;
        boolean noOrg=r.getOrganizationIds()!=null&&r.getOrganizationIds().trim().isEmpty()&&r.getOrganizationId()==null;
        if(!noWorld&&!noOrg) return;
        LambdaUpdateWrapper<SandboxCharacter> u=new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getId,id).eq(SandboxCharacter::getUserId,userId);
        if(noWorld) u.set(SandboxCharacter::getWorldviewId,null);
        if(noOrg) u.set(SandboxCharacter::getOrganizationId,null);
        characterMapper.update(null,u);
    }
    /** 新建角色时选的文件夹：必须是自己的，否则当作未归档 */
    private Long ownedFolderOrNull(Long userId,Long folderId){
        if(folderId==null||folderMapper==null) return null;
        SandboxCharacterFolder f=folderMapper.selectById(folderId);
        return f!=null&&userId.equals(f.getUserId())?folderId:null;
    }
    /** 旧客户端会把 JSON null 当成字符串 "null" 发回来：存成空串（不是 null，MyBatis-Plus 更新时会跳过 null 字段，旧值清不掉） */
    static String notNullString(String v){return v!=null&&"null".equals(v.trim())?"":v;}
    private void applyCharacter(SandboxCharacter x,SandboxItemRequest r){x.setWorldviewIds(r.getWorldviewIds()); x.setOrganizationIds(r.getOrganizationIds()); x.setWorldviewId(r.getWorldviewId()); x.setOrganizationId(r.getOrganizationId()); x.setCounterpartCharacterId(r.getCounterpartCharacterId()); x.setRelationCharacterIds(r.getRelationCharacterIds()); x.setRelationLabel(notNullString(r.getRelationLabel())); x.setRelationColor(notNullString(r.getRelationColor())); x.setRelationArrow(r.getRelationArrow()); x.setRelationArrowStyle(notNullString(r.getRelationArrowStyle())); x.setCardUrl(r.getCardUrl()); x.setName(required(r.getName())); x.setAvatar(r.getAvatar()); x.setDescription(r.getDescription()); x.setPersonality(r.getPersonality()); x.setBackground(r.getBackground()); if(r.getContent()!=null) x.setContent(r.getContent());}
    private <T> T owned(com.baomidou.mybatisplus.core.mapper.BaseMapper<T> mapper,Long id,Long userId){validUser(userId); T x=mapper.selectById(id); if(x==null)throw new BusinessException(ResultCode.DATA_NOT_EXIST); try{java.lang.reflect.Method m=x.getClass().getMethod("getUserId"); Object owner=m.invoke(x); if(!userId.equals(owner))throw new BusinessException(ResultCode.NO_PERMISSION);}catch(BusinessException e){throw e;}catch(Exception e){throw new BusinessException(ResultCode.DATA_NOT_EXIST);} return x;}

    // ------------------------------------------------------------------ 删除后的引用清理

    /** 逗号分隔的 id 串 */
    static java.util.Set<Long> idSet(String csv){java.util.Set<Long> out=new java.util.LinkedHashSet<Long>(); if(csv==null) return out; for(String part:csv.split(",")){String t=part.trim(); if(t.isEmpty()) continue; try{out.add(Long.valueOf(t));}catch(NumberFormatException ignored){}} return out;}
    /** 从 id 串里去掉 id；原来没有这个 id 时返回 null（不需要更新） */
    static String withoutId(String csv,Long id){java.util.Set<Long> ids=idSet(csv); if(!ids.remove(id)) return null; return ids.stream().map(String::valueOf).collect(Collectors.joining(","));}

    /** 角色编辑里把「对应角色」清空：请求没带这个值就显式置空（updateById 会跳过 null，旧值会回来） */
    private void clearCounterpartIfRemoved(Long userId,Long id,SandboxItemRequest r){
        if(r.getCounterpartCharacterId()!=null) return;
        characterMapper.update(null,new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getId,id).eq(SandboxCharacter::getUserId,userId).set(SandboxCharacter::getCounterpartCharacterId,null));
    }

    /**
     * 这次保存新勾上的连线角色是用户明确要建立的关系，撤销之前的「删掉过」记录；
     * 原来就在列表里的不动，旧客户端带着过期列表保存时不会把删掉的关系建回来。
     */
    private void forgetDismissedForNewLinks(Long userId,Long id,java.util.Set<Long> oldLinked,SandboxItemRequest r){
        if(r.getRelationCharacterIds()==null) return;
        for(Long other:idSet(r.getRelationCharacterIds())) if(!other.equals(id)&&!oldLinked.contains(other)) relationService.forgetDismissedPair(userId,id,other);
    }

    /** 删除角色后：挂载、其他角色的「对应角色」、聊天会话里都去掉它 */
    private void unlinkDeletedCharacter(Long userId,Long characterId){
        characterMapper.update(null,new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId,userId).eq(SandboxCharacter::getCounterpartCharacterId,characterId).set(SandboxCharacter::getCounterpartCharacterId,null));
        SandboxMount m=mountMapper.selectById(userId);
        if(m!=null){
            LambdaUpdateWrapper<SandboxMount> u=new LambdaUpdateWrapper<SandboxMount>().eq(SandboxMount::getUserId,userId); boolean any=false;
            if(characterId.equals(m.getMainCharacterId())){u.set(SandboxMount::getMainCharacterId,null);any=true;}
            String related=withoutId(m.getRelatedCharacterIds(),characterId); if(related!=null){u.set(SandboxMount::getRelatedCharacterIds,related);any=true;}
            if(any) mountMapper.update(null,u);
        }
        List<SandboxChatSession> sessions=sessionMapper.selectList(new LambdaQueryWrapper<SandboxChatSession>().eq(SandboxChatSession::getUserId,userId));
        if(sessions!=null) for(SandboxChatSession s:sessions){
            String ids=withoutId(s.getCharacterIds(),characterId);
            if(ids!=null) sessionMapper.update(null,new LambdaUpdateWrapper<SandboxChatSession>().eq(SandboxChatSession::getId,s.getId()).eq(SandboxChatSession::getUserId,userId).set(SandboxChatSession::getCharacterIds,ids));
        }
    }

    /** 删除组织后：角色归属、其他组织的关联里去掉它 */
    private void unlinkDeletedOrganization(Long userId,Long orgId){
        characterMapper.update(null,new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId,userId).eq(SandboxCharacter::getOrganizationId,orgId).set(SandboxCharacter::getOrganizationId,null));
        for(SandboxCharacter c:characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId,userId))){
            String ids=withoutId(c.getOrganizationIds(),orgId);
            if(ids!=null) characterMapper.update(null,new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getId,c.getId()).set(SandboxCharacter::getOrganizationIds,ids));
        }
        for(SandboxOrganization o:organizationMapper.selectList(new LambdaQueryWrapper<SandboxOrganization>().eq(SandboxOrganization::getUserId,userId))){
            String ids=withoutId(o.getRelationOrganizationIds(),orgId);
            if(ids!=null) organizationMapper.update(null,new LambdaUpdateWrapper<SandboxOrganization>().eq(SandboxOrganization::getId,o.getId()).set(SandboxOrganization::getRelationOrganizationIds,ids));
        }
    }

    /** 删除世界后：组织、剧本、角色归属、挂载、聊天的补充世界观里去掉它（角色和组织本身保留） */
    private void unlinkDeletedWorldview(Long userId,Long worldId){
        organizationMapper.update(null,new LambdaUpdateWrapper<SandboxOrganization>().eq(SandboxOrganization::getUserId,userId).eq(SandboxOrganization::getWorldviewId,worldId).set(SandboxOrganization::getWorldviewId,null));
        scriptMapper.update(null,new LambdaUpdateWrapper<SandboxScript>().eq(SandboxScript::getUserId,userId).eq(SandboxScript::getWorldviewId,worldId).set(SandboxScript::getWorldviewId,null));
        characterMapper.update(null,new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId,userId).eq(SandboxCharacter::getWorldviewId,worldId).set(SandboxCharacter::getWorldviewId,null));
        for(SandboxCharacter c:characterMapper.selectList(new LambdaQueryWrapper<SandboxCharacter>().eq(SandboxCharacter::getUserId,userId))){
            String ids=withoutId(c.getWorldviewIds(),worldId);
            if(ids!=null) characterMapper.update(null,new LambdaUpdateWrapper<SandboxCharacter>().eq(SandboxCharacter::getId,c.getId()).set(SandboxCharacter::getWorldviewIds,ids));
        }
        SandboxMount m=mountMapper.selectById(userId);
        if(m!=null){String ids=withoutId(m.getWorldviewIds(),worldId); if(ids!=null) mountMapper.update(null,new LambdaUpdateWrapper<SandboxMount>().eq(SandboxMount::getUserId,userId).set(SandboxMount::getWorldviewIds,ids));}
        sessionMapper.update(null,new LambdaUpdateWrapper<SandboxChatSession>().eq(SandboxChatSession::getUserId,userId).eq(SandboxChatSession::getSecondaryWorldviewId,worldId).set(SandboxChatSession::getSecondaryWorldviewId,null));
    }
}
