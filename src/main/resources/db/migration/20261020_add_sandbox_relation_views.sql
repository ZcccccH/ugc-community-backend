-- 角色关系里的「对其他角色的认知」：from_view 是 from 角色眼中的 to，to_view 是 to 角色眼中的 from。
-- 旧关系两列为空。下面的 ALTER 不可重复执行；应用启动时 SandboxSchemaInitializer 会逐条执行并忽略「列已存在」。
ALTER TABLE sandbox_relation ADD COLUMN from_view VARCHAR(500) NULL;
ALTER TABLE sandbox_relation ADD COLUMN to_view VARCHAR(500) NULL;
