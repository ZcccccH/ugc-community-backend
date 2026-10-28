package com.ugccommunity.backend.common.config;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import static org.junit.jupiter.api.Assertions.*;
class SandboxWorldGroupMigrationTest {
 @Test void migrationPreservesWorldsCharactersAndDoesNotRegroupMovedWorlds(){
  JdbcTemplate db=new JdbcTemplate(new DriverManagerDataSource("jdbc:h2:mem:archive_migration;MODE=MySQL;DB_CLOSE_DELAY=-1","sa",""));
  SandboxSchemaInitializer init=new SandboxSchemaInitializer(db);init.init();
  db.update("insert into sandbox_worldview(id,user_id,name,content) values (100,7,'旧世界','原设定'),(101,8,'其他用户的世界','保留')");
  db.update("insert into sandbox_character(id,user_id,name,worldview_id,worldview_ids) values (300,7,'人物',100,'100')");
  init.init();
  assertEquals(2,db.queryForObject("select count(*) from sandbox_world_group",Integer.class));
  Long group=db.queryForObject("select world_group_id from sandbox_worldview where id=100",Long.class);
  assertNotNull(group);
  assertEquals(7L,db.queryForObject("select user_id from sandbox_world_group where id=?",Long.class,group));
  assertEquals("原设定",db.queryForObject("select content from sandbox_worldview where id=100",String.class));
  assertEquals(100L,db.queryForObject("select worldview_id from sandbox_character where id=300",Long.class));
  db.update("insert into sandbox_world_group(id,user_id,name) values (999,7,'合并的世界观')");
  db.update("update sandbox_worldview set world_group_id=999 where id=100");
  init.init();
  assertEquals(3,db.queryForObject("select count(*) from sandbox_world_group",Integer.class));
  assertEquals(999L,db.queryForObject("select world_group_id from sandbox_worldview where id=100",Long.class));
  db.update("update sandbox_world_group set main_world_id=100 where id=999");
  init.init();
  assertEquals(100L,db.queryForObject("select main_world_id from sandbox_world_group where id=999",Long.class));
 }
}
