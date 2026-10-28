-- 旧客户端把 JSON null 读成字符串 "null" 再存回来，组织详情连线上会显示「null」。
-- 改回 NULL；可重复执行。2026-10-21 执行前生产：sandbox_character relation_label 17、relation_color 21、relation_arrow_style 21（共 21 行，6 个用户），sandbox_organization 0。
-- SandboxSchemaInitializer 启动时也会执行同样的语句。
update sandbox_character set relation_label = null where relation_label = 'null';
update sandbox_character set relation_color = null where relation_color = 'null';
update sandbox_character set relation_arrow_style = null where relation_arrow_style = 'null';
update sandbox_organization set relation_label = null where relation_label = 'null';
update sandbox_organization set relation_color = null where relation_color = 'null';
update sandbox_organization set relation_arrow_style = null where relation_arrow_style = 'null';
