package com.ugccommunity.backend.controller;

import com.ugccommunity.backend.common.result.Result;
import com.ugccommunity.backend.common.upload.CompressionResult;
import com.ugccommunity.backend.common.upload.FileTypeValidator;
import com.ugccommunity.backend.common.utils.LoginContext;
import com.ugccommunity.backend.service.MediaCompressionService;
import com.ugccommunity.backend.service.QiniuStorageService;
import com.ugccommunity.backend.service.RateLimitService;
import com.ugccommunity.backend.vo.UploadVO;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.LocalDate;
import java.util.UUID;

@RestController
@RequestMapping("/api/upload")
public class UploadController {
    @Value("${app.upload.dir:uploads}")
    private String uploadDir;

    @Value("${app.upload.url-prefix:/uploads/}")
    private String urlPrefix;

    private final MediaCompressionService mediaCompressionService;
    private final QiniuStorageService qiniuStorageService;
    private final RateLimitService rateLimitService;

    public UploadController(MediaCompressionService mediaCompressionService, QiniuStorageService qiniuStorageService, RateLimitService rateLimitService) {
        this.mediaCompressionService = mediaCompressionService;
        this.qiniuStorageService = qiniuStorageService;
        this.rateLimitService = rateLimitService;
    }

    @PostMapping
    public Result<UploadVO> upload(@RequestParam("file") MultipartFile file, HttpServletRequest request) throws Exception {
        rateLimitService.checkUpload(LoginContext.getCurrentUserIdOrThrow(), request);
        FileTypeValidator.ValidationResult validation = FileTypeValidator.validate(file, FileTypeValidator.Purpose.GENERAL);
        String originalName = file.getOriginalFilename() == null ? "file" : file.getOriginalFilename();
        String ext = validation.getStorageExtensionWithDot();
        String day = LocalDate.now().toString();
        Path dir = Paths.get(uploadDir, day).toAbsolutePath();
        Files.createDirectories(dir);
        String fileName = UUID.randomUUID().toString().replace("-", "") + ext;
        Path target = dir.resolve(fileName);
        file.transferTo(target.toFile());
        CompressionResult compression = mediaCompressionService.compressIfNeeded(target, file.getContentType());
        Path thumbnailPath = compression.getThumbnailPath() == null ? null : Paths.get(compression.getThumbnailPath());
        String url = toUrl(target, day);
        String thumbnailUrl = thumbnailPath == null ? null : toUrl(thumbnailPath, day);
        if (qiniuStorageService.isEnabled()) {
            // The local copy is always kept: if the Qiniu URL is unreachable we return the local URL, so clients always get an image that loads
            String remoteUrl = qiniuStorageService.upload(target, day + "/" + fileName);
            if (qiniuStorageService.publicUrlReachable(remoteUrl)) {
                url = remoteUrl;
                if (thumbnailPath != null) {
                    thumbnailUrl = qiniuStorageService.upload(thumbnailPath, day + "/" + thumbnailPath.getFileName().toString());
                }
            }
        }

        UploadVO vo = new UploadVO();
        vo.setOriginalName(originalName);
        vo.setSize(compression.getFinalSize());
        vo.setContentType(file.getContentType());
        vo.setUrl(url);
        vo.setThumbnailUrl(thumbnailUrl);
        vo.setOriginalSize(compression.getOriginalSize());
        vo.setCompressedSize(compression.getFinalSize());
        vo.setSavedBytes(compression.getSavedBytes());
        vo.setSavedRatio(compression.getSavedRatio());
        vo.setSizeRatio(compression.getSizeRatio());
        vo.setCompressed(compression.isCompressed());
        vo.setCompressionRatio(compression.getSavedRatio());
        return Result.success(vo);
    }

    private String toUrl(Path path, String day) {
        if (path == null) return null;
        Path fileName = path.getFileName();
        return fileName == null ? null : urlPrefix + day + "/" + fileName.toString();
    }
}
