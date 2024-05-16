package com.wind.compare.datasource.service;

import com.wind.compare.datasource.pojo.datasource.SQLTableJSON;
import com.wind.compare.datasource.pojo.dto.DatasourceSettingDto;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.multipart.MultipartFile;

import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.concurrent.ExecutionException;

/**
 * @author wind.tan
 * @date 2024-05-16
 */
public interface CompareDataSourceService {
    /**
     * 动态获取 JdbcTemplate 对象
     * @param datasourceSetting 数据库配置
     * @return
     */
    JdbcTemplate getDynamicsJdbcTemplate(DatasourceSettingDto datasourceSetting);

    /**
     * 生成JSON形式的对象[关键!!]
     * @param datasourceSetting 数据库配置
     * @return
     */
    SQLTableJSON generateJsonTable(DatasourceSettingDto datasourceSetting) throws ExecutionException, InterruptedException;

    /**
     * 从加密文本中解析出JSON对象
     * @param file          文件
     * @param aesPassword   密钥
     * @return  JSON对象
     * @throws InvalidPropertiesFormatException
     */
    SQLTableJSON getFromFile(MultipartFile file, String aesPassword) throws InvalidPropertiesFormatException;

    /**
     * 对比两个连接的表结构差异
     * @param base      基本连接
     * @param compare   对比连接
     * @return 可执行SQL
     */
    List<String> compareDiffDataSource(SQLTableJSON base, SQLTableJSON compare);

}
