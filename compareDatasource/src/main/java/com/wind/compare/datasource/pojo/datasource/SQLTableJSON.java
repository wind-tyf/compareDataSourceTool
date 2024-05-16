package com.wind.compare.datasource.pojo.datasource;

import com.wind.compare.datasource.enums.DatasourceDriveType;
import com.wind.compare.datasource.pojo.dto.DatasourceSettingDto;
import lombok.Data;

import java.util.Map;

/**
 * 整个数据库连接对象-JSON形式
 * @author wind.tan
 * 转换效果
 * {
 *     "连接别名":"",
 *     "连接配置":{
 *         "数据库类型":"",
 *         "ip":"",
 *         "端口":"",
 *         "账号":"",
 *         "密码":""
 *     },
 *     "库列表":[
 *         "库名":{
 *             "库名":"",
 *             "字符集":"",
 *             "表列表":[
 *                 "表名":{
 *                     "表名":"",
 *                     "数据库引擎":"",
 *                     "字段列表": [
 *                         {
 *                             "字段名": "",
 *                             "类型": "",
 *                             "是否为空":"",
 *                             "默认值":"",
 *                             "注释": ""
 *                         }
 *                     ],
 *                     "索引列表": [
 *                         {
 *                             "索引类型": "",
 *                             "索引名": "",
 *                             "索引字段": "",
 *                             "索引注释": ""
 *                         }
 *                     ],
 *                     "字符集":"",
 *                     "表注释":""
 *                 }
 *             ]
 *         }
 *     ]
 * }
 */
@Data
public class SQLTableJSON {

    /**
     * 连接别名
     */
    private String connectionAlias;

    /**
     * 连接配置
     */
    private ConnectionConfig connectionConfig;

    /**
     * 库列表
     */
    private Map<String, Schema> schemaMap;

    /**
     * 设置连接别名
     * @param connectionAlias   连接别名
     * @return
     */
    public SQLTableJSON setConnectionAlias(String connectionAlias){
        this.connectionAlias = connectionAlias;
        return this;
    }

    /**
     * 设置连接配置
     * @param datasource        连接类型
     * @param ip                ip
     * @param port              端口
     * @param username          账号
     * @param password          密码
     * @return
     */
    public SQLTableJSON setConnectionConfig(DatasourceDriveType datasource, String ip, Integer port, String username, String password){
        if (this.connectionConfig == null){
            this.connectionConfig = new ConnectionConfig();
        }
        this.connectionConfig.setDatasourceType(datasource);
        this.connectionConfig.setIp(ip);
        this.connectionConfig.setPort(port);
        this.connectionConfig.setUsername(username);
        this.connectionConfig.setPassword(password);
        return this;
    }

    /**
     * 添加数据库配置
     * @param datasourceConfig 数据库配置
     * @return
     */
    public SQLTableJSON setDataSourceSetting(DatasourceSettingDto datasourceConfig) {
        this.setConnectionAlias(datasourceConfig.getConnectionAlias());
        this.setConnectionConfig(datasourceConfig.getDatasource(), datasourceConfig.getIp(), datasourceConfig.getPort(), datasourceConfig.getUsername(), datasourceConfig.getPassword());
        return this;
    }

    /**
     * 连接配置
     */
    @Data
    static class ConnectionConfig{
        private DatasourceDriveType datasourceType;
        private String ip;
        private Integer port;
        private String username;
        private String password;
    }


}
