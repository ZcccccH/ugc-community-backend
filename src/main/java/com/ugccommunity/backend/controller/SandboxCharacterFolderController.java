package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.dto.request.SandboxCharacterFolderRequest;
import com.ugccommunity.backend.entity.SandboxCharacter;
import com.ugccommunity.backend.entity.SandboxCharacterFolder;
import com.ugccommunity.backend.service.SandboxCharacterFolderService;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

/** Character folders (only organize the character list, independent of worldviews and organizations) */
@RestController
@RequestMapping("/api/sandbox")
public class SandboxCharacterFolderController {
    private final SandboxCharacterFolderService folderService;
    public SandboxCharacterFolderController(SandboxCharacterFolderService folderService){this.folderService=folderService;}
    private Long uid(){return LoginContext.getCurrentUserIdOrThrow();}

    @GetMapping("/character-folders") public Result<List<SandboxCharacterFolder>> folders(){return Result.success(folderService.list(uid()));}
    @PostMapping("/character-folders") public Result<SandboxCharacterFolder> addFolder(@RequestBody SandboxCharacterFolderRequest r){return Result.success(folderService.create(uid(),r.getName()));}
    @PutMapping("/character-folders/{id}") public Result<SandboxCharacterFolder> renameFolder(@PathVariable Long id,@RequestBody SandboxCharacterFolderRequest r){return Result.success(folderService.rename(uid(),id,r.getName()));}
    /** Delete a folder and move its characters back to unfiled; returns the number of characters moved */
    @DeleteMapping("/character-folders/{id}") public Result<Map<String,Integer>> deleteFolder(@PathVariable Long id){return Result.success(Collections.singletonMap("movedCharacters",folderService.delete(uid(),id)));}
    /** A null folderId moves the character back to unfiled */
    @PutMapping("/characters/{id}/folder") public Result<SandboxCharacter> moveCharacter(@PathVariable Long id,@RequestBody SandboxCharacterFolderRequest r){return Result.success(folderService.moveCharacter(uid(),id,r.getFolderId()));}
}
