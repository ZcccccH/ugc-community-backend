package com.ugccommunity.backend.common.config;

import com.ugccommunity.backend.entity.User;
import com.ugccommunity.backend.mapper.UserMapper;
import com.ugccommunity.backend.service.PasswordService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time password migration: re-hashes legacy plaintext / non-PBKDF2 passwords on startup.
 * Idempotent: records already in PBKDF2 format are skipped, so the plaintext fallback at login can be removed once migration is done.
 * A failure on a single record does not stop startup (it is only logged).
 */
@Component
@Order(100)
public class PasswordMigrationRunner implements ApplicationRunner {
    private static final Logger log = LoggerFactory.getLogger(PasswordMigrationRunner.class);

    private final UserMapper userMapper;
    private final PasswordService passwordService;

    public PasswordMigrationRunner(UserMapper userMapper, PasswordService passwordService) {
        this.userMapper = userMapper;
        this.passwordService = passwordService;
    }

    @Override
    public void run(ApplicationArguments args) {
        int migrated = 0;
        int failed = 0;
        try {
            List<User> users = userMapper.selectList(null);
            for (User user : users) {
                String stored = user.getPassword();
                if (stored == null || !passwordService.needsUpgrade(stored)) {
                    continue;
                }
                try {
                    // Any remaining non-PBKDF2 value is a plaintext password; hash it as-is
                    user.setPassword(passwordService.encode(stored));
                    userMapper.updateById(user);
                    migrated++;
                } catch (Exception e) {
                    failed++;
                    log.warn("密码迁移失败 userId={} message={}", user.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            log.warn("密码迁移任务未执行（可能 user 表尚未就绪）: {}", e.getMessage());
            return;
        }
        if (migrated > 0 || failed > 0) {
            log.warn("[密码迁移] 完成：已哈希 {} 条，失败 {} 条。", migrated, failed);
        }
    }
}
