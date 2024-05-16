package com.wind.compare.datasource.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * @author wind.tan
 * @date 2024-05-14
 */
@Getter
@AllArgsConstructor
public enum DatasourceDriveType {

    /**
     * MySQL数据库驱动配置
     */
    MYSQL("MYSQL","com.mysql.cj.jdbc.Driver"),
    ;

    /**
     * 数据库类型
     */
    private String datasourceType;
    /**
     * 数据库驱动类
     */
    private String datasourceDrive;

    /**
     * 根据数据库类型获取对应的驱动
     * @param datasourceType
     * @return
     */
    public static DatasourceDriveType getDataSourceByType(String datasourceType){
        datasourceType = datasourceType.toUpperCase();
        for (DatasourceDriveType value : DatasourceDriveType.values()) {
            if (value.getDatasourceType().equals(datasourceType)){
                return value;
            }
        }
        return null;
    }
}
