package com.wind.compare.datasource.pojo.datasource;

import lombok.Data;

import java.util.Map;

/**
 * 库
 * @author wind.tan
 * @date 2024-05-16
 */

@Data
public class Schema{
    private String schemaName;
    private String defaultCharacterName;
    private Map<String, Table> tableMap;
}
