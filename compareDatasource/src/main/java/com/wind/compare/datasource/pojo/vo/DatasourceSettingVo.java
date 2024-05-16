package com.wind.compare.datasource.pojo.vo;

import lombok.Data;

/**
 * @author wind.tan
 * @date 2024-05-14
 */
@Data
public class DatasourceSettingVo {
    private String connectionAlias;
    private String datasourceType;
    private String ip;
    private Integer port;
    private String username;
    private String password;
}
