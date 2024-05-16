package com.wind.compare.datasource.pojo.datasource;

import lombok.Data;

import java.util.List;

/**
 * 表
 * @author wind.tan
 * @date 2024-05-16
 */

@Data
public class Table{
    private String tableName;
    private String tableEngine;
    private List<Column> columnList;
    private List<Index> indexList;
    /**
     * 字符集
     */
    private String charset;
    /**
     * 排序规则
     */
    private String tableCollation;
    private String tableComment;
}
