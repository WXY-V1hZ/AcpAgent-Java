package com.v1hz.acpagent.tool.schema.utils;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class SchemaParser {

    private static final ObjectMapper objectMapper = new ObjectMapper();

    public <T> T parse(String arguments, Class<T> type) {
        if (arguments == null || arguments.isEmpty()) return null;
        try {
            String simpleName = type.getSimpleName();
            String key = Character.toLowerCase(simpleName.charAt(0)) + simpleName.substring(1);
            var root = objectMapper.readTree(arguments);
            var inputNode = root.get(key);
            if (inputNode == null) return null;
            return objectMapper.treeToValue(inputNode, type);
        } catch (Exception e) {
            log.warn("Failed to parse tool arguments: {}", arguments, e);
            return null;
        }
    }
}
