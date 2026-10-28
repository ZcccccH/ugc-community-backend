package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.dto.request.SandboxChatRequest;
import com.ugccommunity.backend.dto.request.SandboxItemRequest;
import com.ugccommunity.backend.dto.request.SandboxMapRequest;
import com.ugccommunity.backend.dto.request.SandboxMountRequest;
import com.ugccommunity.backend.dto.request.SandboxOrganizeRequest;
import com.ugccommunity.backend.dto.request.SandboxGraphLayoutRequest;
import com.ugccommunity.backend.dto.request.SandboxRecordCategoryRequest;
import com.ugccommunity.backend.dto.request.SandboxRecordRequest;
import com.ugccommunity.backend.dto.request.SandboxRelationRequest;
import com.ugccommunity.backend.dto.request.SandboxTimelineRequest;
import com.ugccommunity.backend.dto.request.SandboxWorldRelationRequest;
import com.ugccommunity.backend.entity.*;
import com.ugccommunity.backend.service.ChatGenOptions;
import com.ugccommunity.backend.service.SandboxChatService;
import com.ugccommunity.backend.service.SandboxGraphLayoutService;
import com.ugccommunity.backend.service.SandboxMapService;
import com.ugccommunity.backend.service.SandboxOrganizeService;
import com.ugccommunity.backend.service.SandboxRecordService;
import com.ugccommunity.backend.service.SandboxRelationService;
import com.ugccommunity.backend.service.SandboxService;
import com.ugccommunity.backend.service.SandboxTimelineService;
import com.ugccommunity.backend.service.SandboxWorldRelationService;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController
@RequestMapping("/api/sandbox")
public class SandboxController {
    private final SandboxService service;
    private final SandboxRelationService relationService;
    private final SandboxWorldRelationService worldRelationService;
    private final SandboxGraphLayoutService layoutService;
    private final SandboxRecordService recordService;
    private final SandboxMapService mapService;
    private final SandboxTimelineService timelineService;
    private final SandboxOrganizeService organizeService;
    private final SandboxChatService chatService;
    public SandboxController(SandboxService service, SandboxRelationService relationService, SandboxWorldRelationService worldRelationService, SandboxGraphLayoutService layoutService, SandboxRecordService recordService, SandboxMapService mapService, SandboxTimelineService timelineService, SandboxOrganizeService organizeService, SandboxChatService chatService){this.service=service;this.relationService=relationService;this.worldRelationService=worldRelationService;this.layoutService=layoutService;this.recordService=recordService;this.mapService=mapService;this.timelineService=timelineService;this.organizeService=organizeService;this.chatService=chatService;}
    private Long uid(){return LoginContext.getCurrentUserIdOrThrow();}

    @GetMapping("/world-groups") public Result<List<SandboxWorldGroup>> worldGroups(){return Result.success(service.listWorldGroups(uid()));}
    @PostMapping("/world-groups") public Result<SandboxWorldGroup> addWorldGroup(@RequestBody SandboxItemRequest r){return Result.success(service.createWorldGroup(uid(),r.getName()));}
    @PutMapping("/world-groups/{id}") public Result<SandboxWorldGroup> renameWorldGroup(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.renameWorldGroup(uid(),id,r.getName()));}
    @PutMapping("/world-groups/{id}/main/{worldId}") public Result<SandboxWorldGroup> setMainWorld(@PathVariable Long id,@PathVariable Long worldId){return Result.success(service.setMainWorld(uid(),id,worldId));}
    @DeleteMapping("/world-groups/{id}") public Result<Void> deleteWorldGroup(@PathVariable Long id){service.deleteWorldGroup(uid(),id);return Result.success();}
    @PostMapping("/world-groups/{id}/worlds") public Result<SandboxWorldview> addWorldInGroup(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.createWorldInGroup(uid(),id,r.getName(),r.getDescription(),r.getContent()));}
    @PutMapping("/worldviews/{id}/group/{groupId}") public Result<SandboxWorldview> moveWorldToGroup(@PathVariable Long id,@PathVariable Long groupId){return Result.success(service.moveWorldToGroup(uid(),id,groupId));}
    @GetMapping("/worldviews") public Result<List<SandboxWorldview>> worldviews(){return Result.success(service.listWorldviews(uid()));}
    @PostMapping("/worldviews") public Result<SandboxWorldview> addWorldview(@RequestBody SandboxItemRequest r){return Result.success(service.createWorldview(uid(),r.getName(),r.getDescription(),r.getContent()));}
    @PutMapping("/worldviews/{id}") public Result<SandboxWorldview> editWorldview(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateWorldview(uid(),id,r.getName(),r.getDescription(),r.getContent()));}
    @PutMapping("/worldviews/{id}/layout") public Result<SandboxWorldview> layoutWorldview(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateWorldviewLayout(uid(),id,r.getLevel(),r.getSortOrder(),r.getCanvasX(),r.getCanvasY()));}
    @DeleteMapping("/worldviews/{id}") public Result<Void> deleteWorldview(@PathVariable Long id){service.deleteWorldview(uid(),id);return Result.success();}

    @GetMapping("/organizations") public Result<List<SandboxOrganization>> organizations(@RequestParam(required=false) Long worldviewId){return Result.success(service.listOrganizations(uid(),worldviewId));}
    @PostMapping("/organizations") public Result<SandboxOrganization> addOrganization(@RequestBody SandboxItemRequest r){return Result.success(service.createOrganization(uid(),r));}
    @PutMapping("/organizations/{id}") public Result<SandboxOrganization> editOrganization(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateOrganization(uid(),id,r));}
    @PutMapping("/organizations/{id}/layout") public Result<SandboxOrganization> layoutOrganization(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateOrganizationLayout(uid(),id,r.getSortOrder(),r.getCanvasX(),r.getCanvasY()));}
    @DeleteMapping("/organizations/{id}") public Result<Void> deleteOrganization(@PathVariable Long id){service.deleteOrganization(uid(),id);return Result.success();}

    @GetMapping("/characters") public Result<List<SandboxCharacter>> characters(@RequestParam(required=false) Long worldviewId,@RequestParam(required=false) Long organizationId){return Result.success(service.listCharacters(uid(),worldviewId,organizationId));}
    @PostMapping("/characters") public Result<SandboxCharacter> addCharacter(@RequestBody SandboxItemRequest r){return Result.success(service.createCharacter(uid(),r));}
    @PutMapping("/characters/{id}") public Result<SandboxCharacter> editCharacter(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateCharacter(uid(),id,r));}
    @PutMapping("/characters/{id}/layout") public Result<SandboxCharacter> layoutCharacter(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateCharacterLayout(uid(),id,r.getSortOrder(),r.getCanvasX(),r.getCanvasY()));}
    @DeleteMapping("/characters/{id}") public Result<Void> deleteCharacter(@PathVariable Long id){service.deleteCharacter(uid(),id);return Result.success();}

    @GetMapping("/scripts") public Result<List<SandboxScript>> scripts(@RequestParam(required=false) Long worldviewId){return Result.success(service.listScripts(uid(),worldviewId));}
    @PostMapping("/scripts") public Result<SandboxScript> addScript(@RequestBody SandboxItemRequest r){return Result.success(service.createScript(uid(),r));}
    @PutMapping("/scripts/{id}") public Result<SandboxScript> editScript(@PathVariable Long id,@RequestBody SandboxItemRequest r){return Result.success(service.updateScript(uid(),id,r));}
    @DeleteMapping("/scripts/{id}") public Result<Void> deleteScript(@PathVariable Long id){service.deleteScript(uid(),id);return Result.success();}

    @GetMapping("/mount") public Result<SandboxMount> mount(){return Result.success(service.getMount(uid()));}
    // Character relations (sandbox_relation)
    @GetMapping("/relations") public Result<List<SandboxRelation>> relations(){return Result.success(relationService.listRelations(uid()));}
    @PostMapping("/relations") public Result<SandboxRelation> addRelation(@RequestBody SandboxRelationRequest r){return Result.success(relationService.createRelation(uid(),r));}
    @PutMapping("/relations/{id}") public Result<SandboxRelation> editRelation(@PathVariable Long id,@RequestBody SandboxRelationRequest r){return Result.success(relationService.updateRelation(uid(),id,r));}
    @DeleteMapping("/relations/{id}") public Result<Void> deleteRelation(@PathVariable Long id){relationService.deleteRelation(uid(),id);return Result.success();}
    // Worldview relations (sandbox_world_relation)
    @GetMapping("/world-relations") public Result<List<SandboxWorldRelation>> worldRelations(){return Result.success(worldRelationService.listRelations(uid()));}
    @PostMapping("/world-relations") public Result<SandboxWorldRelation> addWorldRelation(@RequestBody SandboxWorldRelationRequest r){return Result.success(worldRelationService.createRelation(uid(),r));}
    @PutMapping("/world-relations/{id}") public Result<SandboxWorldRelation> editWorldRelation(@PathVariable Long id,@RequestBody SandboxWorldRelationRequest r){return Result.success(worldRelationService.updateRelation(uid(),id,r));}
    @DeleteMapping("/world-relations/{id}") public Result<Void> deleteWorldRelation(@PathVariable Long id){worldRelationService.deleteRelation(uid(),id);return Result.success();}
    // Relation graph layout (sandbox_graph_layout); scope = all or world:{id}
    @GetMapping("/graph-layout") public Result<List<SandboxGraphLayout>> graphLayout(@RequestParam String scope){return Result.success(layoutService.listLayout(uid(),scope));}
    @PutMapping("/graph-layout") public Result<List<SandboxGraphLayout>> saveGraphLayout(@RequestBody SandboxGraphLayoutRequest r){return Result.success(layoutService.saveLayout(uid(),r));}

    // World setting records (sandbox_record) and custom categories (sandbox_record_category)
    @GetMapping("/record-categories") public Result<List<SandboxRecordCategory>> recordCategories(@RequestParam(required=false) Long worldviewId){return Result.success(recordService.listCategories(uid(),worldviewId));}
    @PostMapping("/record-categories") public Result<SandboxRecordCategory> addRecordCategory(@RequestBody SandboxRecordCategoryRequest r){return Result.success(recordService.createCategory(uid(),r));}
    @PutMapping("/record-categories/{id}") public Result<SandboxRecordCategory> editRecordCategory(@PathVariable Long id,@RequestBody SandboxRecordCategoryRequest r){return Result.success(recordService.updateCategory(uid(),id,r));}
    @DeleteMapping("/record-categories/{id}") public Result<Void> deleteRecordCategory(@PathVariable Long id){recordService.deleteCategory(uid(),id);return Result.success();}
    @GetMapping("/records") public Result<List<SandboxRecord>> records(@RequestParam(required=false) Long worldviewId,@RequestParam(required=false) String category){return Result.success(recordService.listRecords(uid(),worldviewId,category));}
    @GetMapping("/records/{id}") public Result<SandboxRecord> record(@PathVariable Long id){return Result.success(recordService.getRecord(uid(),id));}
    @PostMapping("/records") public Result<SandboxRecord> addRecord(@RequestBody SandboxRecordRequest r){return Result.success(recordService.createRecord(uid(),r));}
    @PutMapping("/records/{id}") public Result<SandboxRecord> editRecord(@PathVariable Long id,@RequestBody SandboxRecordRequest r){return Result.success(recordService.updateRecord(uid(),id,r));}
    @DeleteMapping("/records/{id}") public Result<Void> deleteRecord(@PathVariable Long id){recordService.deleteRecord(uid(),id);return Result.success();}

    // World maps and region maps (sandbox_map)
    @GetMapping("/maps") public Result<List<SandboxMap>> maps(@RequestParam(required=false) Long worldviewId){return Result.success(mapService.listMaps(uid(),worldviewId));}
    @GetMapping("/maps/{id}") public Result<SandboxMap> map(@PathVariable Long id){return Result.success(mapService.getMap(uid(),id));}
    @PostMapping("/maps") public Result<SandboxMap> addMap(@RequestBody SandboxMapRequest r){return Result.success(mapService.createMap(uid(),r));}
    @PutMapping("/maps/{id}") public Result<SandboxMap> editMap(@PathVariable Long id,@RequestBody SandboxMapRequest r){return Result.success(mapService.updateMap(uid(),id,r));}
    @DeleteMapping("/maps/{id}") public Result<Void> deleteMap(@PathVariable Long id){mapService.deleteMap(uid(),id);return Result.success();}

    // Timelines (sandbox_timeline): world and character timelines, created on first read
    @GetMapping("/timelines/world/{worldviewId}") public Result<SandboxTimeline> worldTimeline(@PathVariable Long worldviewId){return Result.success(timelineService.worldTimeline(uid(),worldviewId));}
    @GetMapping("/timelines/character/{characterId}") public Result<SandboxTimeline> characterTimeline(@PathVariable Long characterId){return Result.success(timelineService.characterTimeline(uid(),characterId));}
    @PutMapping("/timelines/{id}") public Result<SandboxTimeline> saveTimeline(@PathVariable Long id,@RequestBody SandboxTimelineRequest r){return Result.success(timelineService.update(uid(),id,r));}

    // Organize: extract events, entities and relations from saved setting text; "latest" returns the most recent report (data is null if never organized)
    @PostMapping("/organize") public Result<java.util.Map<String,Object>> organize(@RequestBody SandboxOrganizeRequest r){return Result.success(organizeService.organize(uid(),r.getScope(),r.getId()));}
    @GetMapping("/organize/latest") public Result<java.util.Map<String,Object>> latestOrganize(@RequestParam String scope,@RequestParam Long id){return Result.success(organizeService.latest(uid(),scope,id));}

    @GetMapping("/graph") public Result<java.util.Map<String,Object>> graph(){return Result.success(service.graph(uid()));}
    @PutMapping("/mount") public Result<SandboxMount> saveMount(@RequestBody SandboxMountRequest r){return Result.success(service.saveMount(uid(),r));}
    // Chat: generation requires a session config (worldview, characters, mode) with an applied prompt; platform AI generates on the server, a personal API key generates on the device using the returned context and saves the result back
    @GetMapping("/chat/sessions") public Result<List<SandboxChatSession>> sessions(){return Result.success(chatService.listSessions(uid()));}
    @PostMapping("/chat/sessions") public Result<SandboxChatSession> createSession(@RequestBody(required=false) SandboxChatRequest r){return Result.success(chatService.createSession(uid(),r==null?new SandboxChatRequest():r));}
    @PutMapping("/chat/sessions/{id}") public Result<SandboxChatSession> configureSession(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.configure(uid(),id,r));}
    @PutMapping("/chat/sessions/{id}/prompt") public Result<SandboxChatSession> applyPrompt(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.applyPrompt(uid(),id,r.getPrompt()));}
    @DeleteMapping("/chat/sessions/{id}") public Result<Void> deleteSession(@PathVariable Long id){chatService.deleteSession(uid(),id);return Result.success();}
    @GetMapping("/chat/sessions/{id}/messages") public Result<List<SandboxChatMessage>> messages(@PathVariable Long id){return Result.success(chatService.listMessages(uid(),id));}
    @PostMapping("/chat/sessions/{id}/messages") public Result<List<SandboxChatMessage>> send(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.send(uid(),id,r.getContent(),!Boolean.FALSE.equals(r.getGenerate()),ChatGenOptions.from(r)));}
    @GetMapping("/chat/sessions/{id}/context") public Result<java.util.Map<String,Object>> chatContext(@PathVariable Long id,@RequestParam(required=false) Long beforeMessageId,@RequestParam(required=false) Boolean narration,@RequestParam(required=false) Integer contextLimit){return Result.success(chatService.context(uid(),id,beforeMessageId,ChatGenOptions.of(narration,null,null,null,contextLimit)));}
    @PostMapping("/chat/sessions/{id}/replies") public Result<List<SandboxChatMessage>> saveReply(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.saveReply(uid(),id,r.getContent(),r.getReplaceMessageId(),ChatGenOptions.from(r)));}
    @PostMapping("/chat/sessions/{id}/regenerate") public Result<List<SandboxChatMessage>> regenerate(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.regenerate(uid(),id,r.getMessageId(),ChatGenOptions.from(r)));}
    @PutMapping("/chat/messages/{id}") public Result<SandboxChatMessage> editMessage(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.editMessage(uid(),id,r.getContent()));}
    @DeleteMapping("/chat/messages/{id}") public Result<Void> deleteMessage(@PathVariable Long id){chatService.deleteMessage(uid(),id);return Result.success();}
    @GetMapping("/chat/sessions/{id}/memories") public Result<List<SandboxChatMemory>> memories(@PathVariable Long id){return Result.success(chatService.listMemories(uid(),id));}
    @PostMapping("/chat/sessions/{id}/memories") public Result<SandboxChatMemory> addMemory(@PathVariable Long id,@RequestBody SandboxChatRequest r){return Result.success(chatService.addMemory(uid(),id,r.getCharacterId(),r.getContent(),r.getAfterMessageId()));}
    @DeleteMapping("/chat/memories/{id}") public Result<Void> deleteMemory(@PathVariable Long id){chatService.deleteMemory(uid(),id);return Result.success();}
}
