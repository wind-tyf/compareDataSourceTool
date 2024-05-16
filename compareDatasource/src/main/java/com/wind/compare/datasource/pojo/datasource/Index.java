package com.wind.compare.datasource.pojo.datasource;

import lombok.Data;

import java.util.List;
import java.util.Objects;

/**
 * 索引
 * @author wind.tan
 * @date 2024-05-16
 */
@Data
public class Index{
    /**
     * 约束类型
     */
    private String constraintType;
    private String indexName;
    /**
     * 对应的字段集合
     */
    private List<String> indexColumnList;
    private String indexType;
    private String indexComment;

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Index index = (Index) o;
        return Objects.equals(constraintType, index.constraintType) && Objects.equals(indexName, index.indexName) && Objects.equals(indexColumnList, index.indexColumnList) && Objects.equals(indexType, index.indexType);
    }

    @Override
    public int hashCode() {
        return Objects.hash(constraintType, indexName, indexColumnList, indexType);
    }
}
