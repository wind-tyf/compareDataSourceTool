package com.wind.compare.datasource.pojo.datasource;

/**
 * @author wind.tan
 * @date 2024-05-16
 */

import lombok.Data;

import java.util.Objects;

/**
 * 字段
 * @author wind.tan
 * @date 2024-05-16
 */
@Data
public class Column{
    private String columnName;
    private String columnType;
    /**
     * 当前字段值是否可为空
     */
    private Boolean isNullable;
    private String columnDefault;
    private String columnComment;
    /**
     * 主键会显示 PRI,暂无用
     */
    private String columnKey;
    private String extra;

    /**
     * 先进行hashCode()的对比，再进行equals()的对比
     * @param o
     * @return
     */
    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        Column column = (Column) o;
        return Objects.equals(columnName, column.columnName) && Objects.equals(columnType, column.columnType) && Objects.equals(isNullable, column.isNullable) && Objects.equals(columnDefault, column.columnDefault) && Objects.equals(columnComment, column.columnComment);
    }

    @Override
    public int hashCode() {
        return Objects.hash(columnName, columnType, isNullable, columnDefault, columnComment);
    }
}
