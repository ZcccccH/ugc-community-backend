package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.entity.AppRelease;
import com.ugccommunity.backend.service.AppManageService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.HashMap;
import java.util.Map;

/** Client update check API (public, no login required). */
@RestController
@RequestMapping("/api/app")
public class AppVersionController {
    private final AppManageService appManageService;

    public AppVersionController(AppManageService appManageService) {
        this.appManageService = appManageService;
    }

    @GetMapping("/check-update")
    public Result<Map<String, Object>> checkUpdate(@RequestParam String appKey,
                                                   @RequestParam String os,
                                                   @RequestParam(defaultValue = "0") Integer versionCode) {
        AppRelease latest = appManageService.latestRelease(appKey, os);
        Map<String, Object> data = new HashMap<String, Object>();
        if (latest == null) {
            data.put("hasUpdate", false);
            return Result.success(data);
        }
        boolean hasUpdate = latest.getVersionCode() != null
                && versionCode != null
                && latest.getVersionCode() > versionCode;
        data.put("hasUpdate", hasUpdate);
        data.put("forceUpdate", hasUpdate && latest.getForceUpdate() != null && latest.getForceUpdate() == 1);
        data.put("signRequired", latest.getSignEnabled() != null && latest.getSignEnabled() == 1);
        data.put("versionDisplay", latest.getVersionDisplay());
        data.put("versionCode", latest.getVersionCode());
        data.put("os", latest.getOs());
        data.put("variant", latest.getVariant());
        data.put("fileSize", latest.getFileSize());
        data.put("downloadUrl", latest.getDownloadUrl());
        data.put("changelog", latest.getChangelog());
        return Result.success(data);
    }
}
