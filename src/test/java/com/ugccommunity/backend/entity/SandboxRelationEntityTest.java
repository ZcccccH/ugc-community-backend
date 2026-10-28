package com.ugccommunity.backend.entity;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfo;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * updateById 生成的 SET 片段：清空关系名、反向名、颜色、说明时必须真正写入 null，
 * 否则界面提示保存成功、库里仍是旧值（1.0.41 发现的说明清不掉问题）。
 */
class SandboxRelationEntityTest {
    private static String setSql;

    @BeforeAll
    static void init() {
        MybatisConfiguration cfg = new MybatisConfiguration();
        cfg.setMapUnderscoreToCamelCase(true);
        TableInfo info = TableInfoHelper.initTableInfo(new MapperBuilderAssistant(cfg, "SandboxRelationEntityTest"), SandboxRelation.class);
        setSql = info.getAllSqlSet(false, "et.");
    }

    @Test
    void clearableColumnsAreAlwaysWritten() {
        for (String prop : new String[]{"label", "reverseLabel", "color", "note"}) {
            assertFalse(setSql.contains("et['" + prop + "'] != null"), prop + " 不应被 null 判断跳过: " + setSql);
        }
        assertTrue(setSql.contains("note=#{et.note}"), setSql);
        assertTrue(setSql.contains("reverse_label=#{et.reverseLabel}"), setSql);
    }

    @Test
    void otherColumnsKeepDefaultNullSkipping() {
        assertTrue(setSql.contains("et['direction'] != null"), setSql);
        assertTrue(setSql.contains("et['source'] != null"), setSql);
    }
}
