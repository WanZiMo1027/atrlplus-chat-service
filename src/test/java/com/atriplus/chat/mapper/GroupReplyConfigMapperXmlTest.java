package com.atriplus.chat.mapper;

import com.atriplus.chat.dto.GroupReplyConfig;
import org.apache.ibatis.builder.xml.XMLMapperBuilder;
import org.apache.ibatis.mapping.ResultMap;
import org.apache.ibatis.mapping.ResultMapping;
import org.apache.ibatis.reflection.factory.DefaultObjectFactory;
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.Test;

import java.io.InputStream;
import java.util.List;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;

class GroupReplyConfigMapperXmlTest {
    @Test
    void constructorMappingMatchesPrimitiveRecordParameters() throws Exception {
        Configuration configuration = new Configuration();
        try (InputStream resource = Objects.requireNonNull(getClass().getClassLoader()
                .getResourceAsStream("mapper/GroupReplyConfigMapper.xml"))) {
            new XMLMapperBuilder(resource, configuration, "mapper/GroupReplyConfigMapper.xml",
                    configuration.getSqlFragments()).parse();
        }

        // 主要流程：MyBatis XML 的构造器类型必须与 record 的原始类型精确一致。
        ResultMap resultMap = configuration.getResultMap(
                "com.atriplus.chat.mapper.GroupReplyConfigMapper.groupConfig");
        List<Class<?>> types = resultMap.getConstructorResultMappings().stream()
                .map(ResultMapping::getJavaType).toList();
        assertEquals(List.of(long.class, String.class, boolean.class), types);
        GroupReplyConfig config = new DefaultObjectFactory().create(GroupReplyConfig.class, types,
                List.of(1104399740L, "睦子开发xtt", true));
        assertEquals(new GroupReplyConfig(1104399740L, "睦子开发xtt", true), config);
    }
}
