package com.wind.compare.datasource.pojo.dto;

import cn.hutool.core.util.ObjectUtil;
import com.wind.compare.datasource.enums.DatasourceDriveType;
import com.wind.compare.datasource.pojo.vo.DatasourceSettingVo;
import lombok.Builder;
import lombok.Data;

/**
 * @author wind.tan
 * @date 2024-05-14
 */
@Builder
@Data
public class DatasourceSettingDto {
    private String connectionAlias;
    private String datasourceType;
    private DatasourceDriveType datasource;
    private String ip;
    private Integer port;
    private String username;
    private String password;

    /**
     * 从vo转成dto
     * @param datasourceSettingVo   前端传过来的参数
     * @return
     */
    public static DatasourceSettingDto exchangeVoToDto(DatasourceSettingVo datasourceSettingVo){
        if (ObjectUtil.isNull(datasourceSettingVo)){
            return DatasourceSettingDto.builder().build();
        }
        return DatasourceSettingDto.builder()
                .connectionAlias(datasourceSettingVo.getConnectionAlias())
                .datasource(DatasourceDriveType.getDataSourceByType(datasourceSettingVo.getDatasourceType()))
                .ip(datasourceSettingVo.getIp())
                .port(datasourceSettingVo.getPort())
                .username(datasourceSettingVo.getUsername())
                .password(datasourceSettingVo.getPassword()).build();
    }
}
