-- UGC Community Backend: demo seed data (all values are fictional).
-- Load AFTER sql/schema.sql:  mysql -u "$DB_USERNAME" -p ugc_community < sql/seed-demo.sql
--
-- Demo users: alice / bob / carol, password: demo-password-change-me
-- Phone numbers use the reserved fictional range 555-01xx; emails use example.com.
-- Change or delete these accounts before exposing any environment to the internet.

USE `ugc_community`;
SET NAMES utf8mb4;

INSERT INTO `user` (`id`,`username`,`password`,`nickname`,`avatar`,`gender`,`role`,`status`,`phone`,`email`,`birthday`,`bio`,`follow_count`,`fans_count`,`like_count`,`deleted`,`privacy_agreed`,`background_image`) VALUES
(1,'alice','pbkdf2$120000$xU2aCPnycr1I4KVAJGpWew$FkUScW9nWDT9pGIkWWro4fjsZ13EH-t05di0ci6FRRQ','Alice',NULL,0,'user',1,'+1-555-0101','alice@example.com',NULL,'Demo account. Photographer and writer.',0,0,0,0,1,'asset_bg'),
(2,'bob','pbkdf2$120000$s8ifHEAKEfb-UH8P45eHLg$fOldI6FwEb76DST4GXvm3SkSFYPALtHU_kXwObAqvVE','Bob',NULL,1,'user',1,'+1-555-0102','bob@example.com',NULL,'Demo account. Loves travel notes.',0,0,0,0,1,'asset_bg'),
(3,'carol','pbkdf2$120000$ImygHwDgIBrtj6xoideXpA$GHzr8m_YvGV05E8_wSs6-YJNz8w2mlNZ7DQMncoRLb0','Carol',NULL,0,'user',1,'+1-555-0103','carol@example.com',NULL,'Demo account.',0,0,0,0,1,'asset_bg');

INSERT INTO `tag` (`id`,`name`,`usage_count`,`is_disabled`,`is_deleted`) VALUES
(1,'travel',1,0,0),(2,'photography',1,0,0),(3,'writing',1,0,0);

INSERT INTO `work` (`id`,`user_id`,`type`,`title`,`content`,`status`) VALUES
(1,1,'article','Thirty days of morning writing','I wrote 300 words every morning for a month. Small, steady starts beat waiting for inspiration.','published'),
(2,2,'article','A weekend by the lake','Notes and a few photos from a short trip.','published');

INSERT INTO `work_tag` (`work_id`,`tag_id`) VALUES (1,3),(2,1),(2,2);

INSERT INTO `work_comment` (`id`,`work_id`,`user_id`,`content`) VALUES
(1,1,2,'Great habit, thanks for sharing!'),(2,2,3,'Beautiful place.');

INSERT INTO `message` (`id`,`user_id`,`type`,`title`,`content`,`is_read`) VALUES
(1,1,'system','Welcome','Welcome to UGC Community Backend demo data.',0);

-- Client app identity used by the optional request-signature feature.
-- The values below are placeholders: generate your own AppSecret and RSA key pair
-- and never commit real keys.
INSERT INTO `app_identity` (`id`,`app_name`,`app_key`,`app_secret`,`rsa_public_key`,`rsa_private_key`,`status`) VALUES
(1,'Demo App','DEMO_APP_KEY','REPLACE_WITH_RANDOM_APP_SECRET','REPLACE_WITH_RSA_PUBLIC_KEY_PEM','REPLACE_WITH_RSA_PRIVATE_KEY_PEM',1);
