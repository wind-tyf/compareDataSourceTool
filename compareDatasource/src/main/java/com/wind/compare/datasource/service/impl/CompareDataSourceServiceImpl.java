package com.wind.compare.datasource.service.impl;

import cn.hutool.core.collection.CollectionUtil;
import cn.hutool.core.util.ObjectUtil;
import com.alibaba.fastjson.JSON;
import com.wind.compare.datasource.pojo.datasource.Column;
import com.wind.compare.datasource.pojo.datasource.Index;
import com.wind.compare.datasource.pojo.datasource.Schema;
import com.wind.compare.datasource.pojo.datasource.Table;
import com.wind.compare.datasource.service.CompareDataSourceService;
import com.wind.compare.datasource.utils.AESUtils;
import com.wind.compare.datasource.utils.DefaultStringUtils;
import com.wind.compare.datasource.config.ThreadPoolConfig;
import com.wind.compare.datasource.pojo.datasource.SQLTableJSON;
import com.wind.compare.datasource.pojo.dto.DatasourceSettingDto;
import jakarta.annotation.Resource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.jdbc.core.ColumnMapRowMapper;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.stream.Collectors;

/**
 * @author wind.tan
 * @date 2024-05-14
 */
@Slf4j
@Service
public class CompareDataSourceServiceImpl implements CompareDataSourceService {

    @Resource(name = ThreadPoolConfig.SEARCH_SQL)
    private ThreadPoolExecutor searchSqlThreadPool;

    /**
     * jdbc连接
     */
    private static JdbcTemplate jdbcTemplate;
    /**
     * jdbc连接key
     */
    private static String dataSourceKey;

    /**
     * 动态获取 JdbcTemplate 对象
     * @param datasourceSetting 数据库配置
     * @return
     */
    @Override
    public JdbcTemplate getDynamicsJdbcTemplate(DatasourceSettingDto datasourceSetting) {
        log.info("SQLTableJSONUtils.getDynamicsJdbcTemplate:{}", JSON.toJSONString(datasourceSetting));
        DriverManagerDataSource dataSource = new DriverManagerDataSource();
        dataSource.setDriverClassName(datasourceSetting.getDatasource().getDatasourceDrive());
        dataSource.setUrl("jdbc:mysql://"+datasourceSetting.getIp()+":"+datasourceSetting.getPort());
        dataSource.setUsername(datasourceSetting.getUsername());
        dataSource.setPassword(datasourceSetting.getPassword());
        String key = dataSource.getUrl()+"_"+dataSource.getUsername()+"_"+dataSource.getPassword();
        // 单例-懒汉模式，复用 jdbcTemplate 连接
        if (jdbcTemplate != null && key.equals(dataSourceKey)){
            return jdbcTemplate;
        }
        jdbcTemplate = new JdbcTemplate(dataSource);
        dataSourceKey = key;
        return jdbcTemplate;
    }

    /**
     * 生成JSON形式的对象[关键!!]
     * @param datasourceSetting 数据库配置
     * @return
     */

    @Override
    public SQLTableJSON generateJsonTable(DatasourceSettingDto datasourceSetting) throws ExecutionException, InterruptedException {

        SQLTableJSON sqlTableJson = new SQLTableJSON().setDataSourceSetting(datasourceSetting);

        // 1、根据连接配置与数据库建立连接(JDBC)
        JdbcTemplate jdbcTemplate = getDynamicsJdbcTemplate(datasourceSetting);

        // 2、获取各个表的数据并封装到 SQLTableJSON 对象中
        // 2.1 写入表数据
        getTableData(jdbcTemplate, sqlTableJson);
        // 2.2 写入字段数据
        CompletableFuture<Void> getColumnDataFuture = CompletableFuture.runAsync(() -> getColumnData(jdbcTemplate, sqlTableJson), searchSqlThreadPool);
        // 2.3 写入索引数据
        CompletableFuture<Void> getIndexDataFuture = CompletableFuture.runAsync(() -> getIndexData(jdbcTemplate, sqlTableJson), searchSqlThreadPool);
        // 字段和索引线程都处理完，再返回
        CompletableFuture.allOf(getColumnDataFuture, getIndexDataFuture).get();
        return sqlTableJson;
    }

    /**
     * 写入表数据
     * @param jdbcTemplate
     * @param sqlTableJSON
     */
    private void getTableData(JdbcTemplate jdbcTemplate, SQLTableJSON sqlTableJSON) {
        // 1、获取所有表信息
        List<Map<String, Object>> tableList = jdbcTemplate.query("SELECT \n" +
                "\tsc.SCHEMA_NAME,\n" +
                "\tsc.DEFAULT_CHARACTER_SET_NAME,\n" +
                "\ttab.TABLE_NAME,\n" +
                "\ttab.ENGINE,\n" +
                "\ttab.TABLE_COLLATION,\n" +
                "\ttab.TABLE_COMMENT\n" +
                "FROM information_schema.TABLES tab\n" +
                "INNER JOIN information_schema.SCHEMATA sc ON sc.SCHEMA_NAME = tab.TABLE_SCHEMA\n" +
                "WHERE tab.TABLE_TYPE = 'BASE TABLE' AND sc.SCHEMA_NAME != 'information_schema';", new ColumnMapRowMapper());
        Map<String, Schema> schemaMap = new HashMap<>(tableList.size());
        tableList.forEach(tableData->{
            // 获取库对象
            String schemaName = (String) tableData.get("SCHEMA_NAME");
            Schema schema = schemaMap.get(schemaName);
            if (null == schema){
                schema = new Schema();
                schema.setSchemaName(schemaName);
                schema.setDefaultCharacterName((String) tableData.get("DEFAULT_CHARACTER_SET_NAME"));
                schema.setTableMap(new HashMap<>());
                schemaMap.put(schemaName, schema);
            }
            // 在库对象中写入表数据
            Table table = new Table();
            table.setTableName((String) tableData.get("TABLE_NAME"));
            table.setTableEngine((String) tableData.get("ENGINE"));
            table.setTableCollation((String) tableData.get("TABLE_COLLATION"));
            // 先默认取"_"分割后的第一个参数【eg：utf8_general_ci ==> utf8】
            table.setCharset(table.getTableCollation().split("_")[0]);
            table.setTableComment((String) tableData.get("TABLE_COMMENT"));
            schema.getTableMap().put(table.getTableName(),table);
        });
        // 把结果塞入 sqlTableJSON 对象
        sqlTableJSON.setSchemaMap(schemaMap);
    }

    /**
     * 写入字段数据
     * @param jdbcTemplate
     * @param sqlTableJSON
     */
    private void getColumnData(JdbcTemplate jdbcTemplate, SQLTableJSON sqlTableJSON) {
        // 1、获取字段数据
        Set<String> schemaSet = new HashSet<>();
        Set<String> tableSet = new HashSet<>();
        sqlTableJSON.getSchemaMap().values().forEach(schema -> {
            schemaSet.add(schema.getSchemaName());
            tableSet.addAll(schema.getTableMap().keySet());
        });
        String schemas = "'" + String.join("','", schemaSet) + "'";
        String tables = "'" + String.join("','", tableSet) + "'";
        String exeSql = "SELECT \n" +
                "\tcol.TABLE_SCHEMA,\n" +
                "\tcol.TABLE_NAME,\n" +
                "\tcol.COLUMN_NAME,\n" +
                "\tcol.COLUMN_TYPE,\n" +
                "\tcol.IS_NULLABLE,\n" +
                "\tcol.COLUMN_DEFAULT,\n" +
                "\tcol.COLUMN_COMMENT,\n" +
                "\tcol.COLUMN_KEY,\n" +
                "\tcol.EXTRA\n" +
                "FROM\n" +
                "\tinformation_schema.COLUMNS col\n" +
                "WHERE col.table_schema IN ("+schemas+")\n" +
                "AND col.table_name IN ("+tables+");";
        List<Map<String, Object>> columnList = jdbcTemplate.query(exeSql, new ColumnMapRowMapper());
        // 表字段映射Map【key(库名.表名)、value(表的全部字段)】
        Map<String, List<Map<String, Object>>> columnMap = new HashMap<>();
        columnList.forEach(column -> {
            // key = 库名.表名
            String key = column.get("TABLE_SCHEMA") + "." + column.get("TABLE_NAME");
            // 根据key从Map中获取，若获取不到就新建Map
            List<Map<String, Object>> maps = columnMap.computeIfAbsent(key, k -> new ArrayList<>());
            // 将当前字段加入到List
            maps.add(column);
        });

        // 2、封装字段数据
        for (Schema schema : sqlTableJSON.getSchemaMap().values()) {
            for (Table table : schema.getTableMap().values()) {
                // 获取当前表的全部字段
                String key = schema.getSchemaName() + "." + table.getTableName();
                List<Map<String, Object>> tableColumnList = columnMap.get(key);
                table.setColumnList(new ArrayList<>());
                // 全部字段封装到 columnList 中
                for (Map<String, Object> map : tableColumnList) {
                    Column column = new Column();
                    column.setColumnName((String) map.get("COLUMN_NAME"));
                    column.setColumnType((String) map.get("COLUMN_TYPE"));
                    column.setIsNullable("YES".equals(map.get("IS_NULLABLE")));
                    column.setColumnDefault((String) map.get("COLUMN_DEFAULT"));
                    column.setColumnComment((String) map.get("COLUMN_COMMENT"));
                    column.setColumnKey((String) map.get("COLUMN_KEY"));
                    column.setExtra((String) map.get("EXTRA"));
                    table.getColumnList().add(column);
                }
            }
        }

    }

    /**
     * 写入索引数据
     * @param jdbcTemplate
     * @param sqlTableJSON
     */
    private void getIndexData(JdbcTemplate jdbcTemplate, SQLTableJSON sqlTableJSON) {
        // 1、获取字段数据
        Set<String> schemaSet = new HashSet<>();
        Set<String> tableSet = new HashSet<>();
        sqlTableJSON.getSchemaMap().values().forEach(schema -> {
            schemaSet.add(schema.getSchemaName());
            tableSet.addAll(schema.getTableMap().keySet());
        });
        String schemas = "'" + String.join("','", schemaSet) + "'";
        String tables = "'" + String.join("','", tableSet) + "'";
        String exeSql = "SELECT \n" +
                "\tsta.TABLE_SCHEMA,\n" +
                "\tsta.TABLE_NAME,\n" +
                "\ttabcon.CONSTRAINT_TYPE,\n" +
                "\tsta.INDEX_NAME,\n" +
                "\tsta.SEQ_IN_INDEX,\n" +
                "\tsta.COLUMN_NAME,\n" +
                "\tsta.SUB_PART,\n" +
                "\tsta.INDEX_TYPE,\n" +
                "\tsta.INDEX_COMMENT\n" +
                "FROM information_schema.STATISTICS sta\n" +
                "LEFT JOIN information_schema.TABLE_CONSTRAINTS tabcon ON tabcon.TABLE_SCHEMA = sta.TABLE_SCHEMA AND tabcon.TABLE_NAME = sta.TABLE_NAME AND tabcon.CONSTRAINT_NAME = sta.INDEX_NAME\n" +
                "WHERE sta.TABLE_SCHEMA IN ("+schemas+")\n" +
                "AND sta.TABLE_NAME IN ("+tables+");";
        List<Map<String, Object>> indexList = jdbcTemplate.query(exeSql, new ColumnMapRowMapper());
        // key(库名.表名)、value(表的全部索引)
        Map<String, List<Map<String, Object>>> indexMap = new HashMap<>();
        indexList.forEach(index -> {
            // key = 库名.表名
            String key = index.get("TABLE_SCHEMA") + "." + index.get("TABLE_NAME");
            List<Map<String, Object>> maps = indexMap.computeIfAbsent(key, k -> new ArrayList<>());
            maps.add(index);
        });

        // 2、封装索引数据
        for (Schema schema : sqlTableJSON.getSchemaMap().values()) {
            for (Table table : schema.getTableMap().values()) {
                // 获取当前表的全部索引
                String key = schema.getSchemaName() + "." + table.getTableName();
                List<Map<String, Object>> tableIndexList = indexMap.get(key);
                // 部分表可能不存在索引(eg: ideamake_um.um_bc_agent_image), 不存在索引直接跳过
                if (tableIndexList == null || tableIndexList.size() == 0){
                    continue;
                }

                Map<String, List<Map<String, Object>>> tableIndexMap = new HashMap<>();
                // 因为一个索引可能会对应多个字段，所以转换为Map(key=索引名、value=对应的所有字段)
                for (Map<String, Object> map : tableIndexList) {
                    String indexName = (String) map.get("INDEX_NAME");
                    List<Map<String, Object>> maps = tableIndexMap.computeIfAbsent(indexName, k -> new ArrayList<>());
                    maps.add(map);
                }

                // 封装成 List<Index> indexList
                List<Index> retIndexList = new ArrayList<>();
                // 全部索引封装到 indexList 中
                for (Map.Entry<String, List<Map<String, Object>>> entry : tableIndexMap.entrySet()) {
                    Index index = new Index();
                    List<Map<String, Object>> values = entry.getValue();
                    index.setIndexName(entry.getKey());
                    Map<String, Object> firstValue = values.get(0);
                    index.setConstraintType((String) firstValue.get("CONSTRAINT_TYPE"));
                    index.setIndexType((String) firstValue.get("INDEX_TYPE"));
                    index.setIndexComment((String) firstValue.get("INDEX_COMMENT"));
                    if (1 == values.size()){
                        // 只对应一个字段的话，直接获取赋值
                        index.setIndexColumnList(Collections.singletonList(mergeIndexColumn(firstValue)));
                    }else {
                        // TreeMap - 多个字段的话, indexColumnList需要拼接且按照 SEQ_IN_INDEX 来排序[必须要按顺序，顺序乱了就打乱了最左匹配规则了!!!]
                        Map<Long, String> sortIndexColumnMap = new TreeMap<>();
                        for (Map<String, Object> value : values) {
                            Long sort = (Long) value.get("SEQ_IN_INDEX");
                            sortIndexColumnMap.put(sort, mergeIndexColumn(value));
                        }
                        index.setIndexColumnList(new ArrayList<>(sortIndexColumnMap.values()));
                    }
                    // 将上述填充好的单个索引对象存储起来
                    retIndexList.add(index);
                }
                // 一个表的全部索引
                table.setIndexList(retIndexList);
            }
        }
    }

    /**
     * 拼接索引 字段(长度)
     * @param value
     * @return
     */
    private String mergeIndexColumn(Map<String, Object> value){
        Long subPart = (Long) value.get("SUB_PART");
        StringBuilder indexColumn = new StringBuilder().append((String) value.get("COLUMN_NAME"));
        if (null != subPart){
            indexColumn.append("(").append(subPart).append(")");
        }
        return indexColumn.toString();
    }


    /**
     * 对比两个连接的表结构差异
     * @param base      基本连接
     * @param compare   对比连接
     * @return 可执行SQL
     */
    @Override
    public List<String> compareDiffDataSource(SQLTableJSON base, SQLTableJSON compare) {
        if (ObjectUtil.isNull(base) || ObjectUtil.isNull(compare)){
            throw new IllegalArgumentException("数据库连接JSON对象为空!");
        }
        Map<String, Schema> schemaMapBase = base.getSchemaMap();
        Map<String, Schema> schemaMapCompare = compare.getSchemaMap();
        List<String> retList = new ArrayList<>();
        if (CollectionUtil.isEmpty(schemaMapBase)){
            log.info("传入的基准数据库没有数据，不用对比");
            return null;
        }
        if (CollectionUtil.isEmpty(schemaMapCompare)){
            log.info("当前数据库没有库，直接返回基准库:{}", base.getConnectionAlias());
            schemaMapBase.values().forEach(schema -> {
                List<String> newTableDDLList = this.exchangeToNewTableDDL(schema.getSchemaName(), schema.getTableMap().values());
                retList.addAll(newTableDDLList);
            });
            return retList;
        }
        // 获取库列表集合
        Set<String> schemaKeysBase = schemaMapBase.keySet();
        Set<String> schemaKeysCompare = schemaMapCompare.keySet();
        /**
         * 找出交集、差集(baseSchemaKeys有，compareSchemaKeys无)
         */
        // 交集 - 进一步对比表名
        Set<String> interSchemaSet = new HashSet<>(schemaKeysBase);
        interSchemaSet.retainAll(schemaKeysCompare);
        for (String schemaName : interSchemaSet) {
            Map<String, Table> tableMapBase = schemaMapBase.get(schemaName).getTableMap();
            Map<String, Table> tableMapCompare = schemaMapCompare.get(schemaName).getTableMap();
            Set<String> diffTableSet;
            // 基准库下无表，不用对比，直接跳过
            if (CollectionUtil.isEmpty(tableMapBase)){
                log.info("库交集-基准库"+schemaName+"下无表，不用对比，直接跳过");
                continue;
            }
            // 当前库无表，直接取基准库的表
            if (CollectionUtil.isEmpty(tableMapCompare)){
                log.info("库交集-当前库"+schemaName+"无表，直接取基准库的表");
                List<String> newTableDDLList = this.exchangeToNewTableDDL(schemaName, tableMapBase.values());
                retList.addAll(newTableDDLList);
                continue;
            }
            // 继续找交集、差集
            // 交集(tableMapBase有，tableKeysCompare也有。但里面表字段/索引不一定一样)
            Set<String> tableKeysBase = tableMapBase.keySet();
            Set<String> tableKeysCompare = tableMapCompare.keySet();
            Set<String> interTableSet = new HashSet<>(tableKeysBase);
            interTableSet.retainAll(tableKeysCompare);
            // 提前转成Map，方便获取两个库的同一张表
            Map<String, Table> interTableBaseMap = interTableSet.stream().map(tableMapBase::get).collect(Collectors.toList())
                    .stream().filter(table -> StringUtils.isNotBlank(table.getTableName())).collect(Collectors.toMap(Table::getTableName, table -> table));
            Map<String, Table> interTableCompareMap = interTableSet.stream().map(tableMapCompare::get).collect(Collectors.toList())
                    .stream().filter(table -> StringUtils.isNotBlank(table.getTableName())).collect(Collectors.toMap(Table::getTableName, table -> table));
            for (String tableName : interTableSet) {
                Table tableBase = interTableBaseMap.get(tableName);
                Table tableCompare = interTableCompareMap.get(tableName);
                // 对比不同库，同一个表的差异(字段/索引)
                String updateTableSql = compareDiffTable(tableBase, tableCompare, schemaName);
                if (StringUtils.isNotBlank(updateTableSql)){
                    retList.add(updateTableSql);
                }
            }

            // 差集(tableMapBase有，tableKeysCompare无) - 直接返回就行
            diffTableSet = new HashSet<>(tableKeysBase);
            diffTableSet.removeAll(tableKeysCompare);
            List<Table> diffTableList = diffTableSet.stream().map(tableMapBase::get).collect(Collectors.toList());
            List<String> newTableDDLList = this.exchangeToNewTableDDL(schemaName, diffTableList);
            if (CollectionUtil.isNotEmpty(newTableDDLList)){
                retList.addAll(newTableDDLList);
            }
        }

        // 差集(baseSchemaKeys有，compareSchemaKeys无) - 直接返回就行
        Set<String> diffSchemaSet = new HashSet<>(schemaKeysBase);
        diffSchemaSet.removeAll(schemaKeysCompare);
        for (String schemaName : diffSchemaSet) {
            Map<String, Table> tableMapBase = schemaMapBase.get(schemaName).getTableMap();
            List<String> newTableDDLList = this.exchangeToNewTableDDL(schemaName, tableMapBase.values());
            if (CollectionUtil.isNotEmpty(newTableDDLList)){
                retList.addAll(newTableDDLList);
            }
        }
        return retList;
    }

    /**
     * 对比不同库，同一个表的差异(字段/索引)
     * @param tableBase    基准表
     * @param tableCompare 待对比表
     * @param schemaName   库名
     * @return  一张表的更新SQL
     * eg:
     * ALTER TABLE `test`.`peisongyuan_copy1`
     * MODIFY COLUMN `addtime` `addtimes` timestamp NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
     * ADD COLUMN `newcolumn` varchar(255) NULL COMMENT '测试字段',
     * DROP INDEX `yonghuming`,
     * ADD UNIQUE INDEX `yonghumings`(`yonghuming`) USING BTREE,
     * ADD INDEX `idx_test`(`xingbie`) USING BTREE COMMENT '测试索引';
     */
    private String compareDiffTable(Table tableBase, Table tableCompare, String schemaName) {
        if (ObjectUtil.isNull(tableBase) || ObjectUtil.isNull(tableBase)){
            return null;
        }
        String tableName = tableBase.getTableName();
        List<String> columnAndIndexSqlList = new ArrayList<>();
        /**
         * 对比字段
         */
        // 1、找交集、差集
        Map<String, Column> columnBaseMap = tableBase.getColumnList().stream().collect(Collectors.toMap(Column::getColumnName, column -> column));
        Map<String, Column> columnCompareMap = tableCompare.getColumnList().stream().collect(Collectors.toMap(Column::getColumnName, column -> column));
        Set<String> columnBaseSet = columnBaseMap.keySet();
        Set<String> columnCompareSet = columnCompareMap.keySet();
        // 2、分别处理【交集-进一步对比其他属性(类型、是否为空、默认值、备注)、差集-转换成alter table xxx.xxx add column xxx 】
        // 交集
        Set<String> interColumnSet = new HashSet<>(columnBaseSet);
        interColumnSet.retainAll(columnCompareSet);
        // 转换成 alter table xxx.xxx change column xxx
        for (String columnKey : interColumnSet) {
            if (!columnBaseMap.get(columnKey).equals(columnCompareMap.get(columnKey))){
                // 不相等时，更新字段 CHANGE COLUMN xxx
                String updateColumnDDL = "MODIFY COLUMN"+ this.exchangeToColumnSql(columnBaseMap.get(columnKey));
                columnAndIndexSqlList.add(updateColumnDDL);
            }
        }
        // 差集
        Set<String> diffColumnSet = new HashSet<>(columnBaseSet);
        diffColumnSet.removeAll(columnCompareSet);
        // 转换成 alter table xxx.xxx add column xxx
        for (String columnKey : diffColumnSet) {
            String newColumnDDL = "ADD COLUMN" + this.exchangeToColumnSql(columnBaseMap.get(columnKey));
            columnAndIndexSqlList.add(newColumnDDL);
        }

        /**
         * 对比索引
         */
        // 1、找交集、差集
        Map<String, Index> indexBaseMap = tableBase.getIndexList().stream().collect(Collectors.toMap(Index::getIndexName, index -> index));
        Map<String, Index> indexCompareMap = tableCompare.getIndexList().stream().collect(Collectors.toMap(Index::getIndexName, index -> index));
        Set<String> indexBaseSet = indexBaseMap.keySet();
        Set<String> indexCompareSet = indexCompareMap.keySet();
        // 2、分别处理【交集-、差集-转换成 alter table xxx.xxx add index xxx】
        // 交集
        Set<String> interIndexSet = new HashSet<>(indexBaseSet);
        interIndexSet.retainAll(indexCompareSet);
        // 转换成 alter table xxx.xxx change column xxx
        for (String indexKey : interIndexSet) {
            if (!indexBaseMap.get(indexKey).equals(indexCompareMap.get(indexKey))){
                // 不相等时，更新索引 drop index xxx, add xxxDDL
                String updateColumnDDL = "DROP INDEX "+indexKey+",\n"+"ADD"+ this.exchangeToIndexSql(indexBaseMap.get(indexKey));
                columnAndIndexSqlList.add(updateColumnDDL);
            }
        }
        // 差集
        Set<String> diffIndexSet = new HashSet<>(indexBaseSet);
        diffIndexSet.removeAll(indexCompareSet);
        // 转换成 alter table xxx.xxx add xxxDDL
        for (String indexKey : diffIndexSet) {
            String newIndexDDL = "ADD" + this.exchangeToIndexSql(indexBaseMap.get(indexKey));
            columnAndIndexSqlList.add(newIndexDDL);
        }

        /**
         * 最后组装sql
         */
        if (CollectionUtil.isEmpty(columnAndIndexSqlList)){
            return null;
        }
        StringBuilder resultSql = new StringBuilder("ALTER TABLE "+schemaName+"."+tableName).append("\n");
        int i;
        for (i = 0; i < columnAndIndexSqlList.size()-1; i++) {
            resultSql.append(columnAndIndexSqlList.get(i)).append(",\n");
        }
        resultSql.append(columnAndIndexSqlList.get(i)).append(";\n");
        System.out.println("===================表字段对比结果===================");
        System.out.print(resultSql);
        System.out.println("==================================================");
        return resultSql.toString();
    }


    /**
     * 将表批量转换为DDL语句
     * @param schemaName    库名
     * @param tableList     表集合
     * @return
     */
    private List<String> exchangeToNewTableDDL(String schemaName, Collection<Table> tableList){
        List<String> tableDDLList = new ArrayList<>();
        for (Table table : tableList) {
            StringBuilder columnSqlBuffer = new StringBuilder();
            StringBuilder indexSqlBuffer = new StringBuilder();
            for (Column column : table.getColumnList()) {
                columnSqlBuffer.append(exchangeToColumnSql(column)).append(",\n");
            }
            List<Index> indexList = table.getIndexList();
            int i;
            for (i = 0; i < indexList.size()-1; i++) {
                indexSqlBuffer.append(exchangeToIndexSql(indexList.get(i))).append(",\n");
            }
            indexSqlBuffer.append(exchangeToIndexSql(indexList.get(i))).append("\n");
            String tableDDL = "CREATE TABLE "+schemaName+"."+table.getTableName()+" (\n" +
                    columnSqlBuffer +
                    indexSqlBuffer +
                    ") ENGINE="+table.getTableEngine()+" AUTO_INCREMENT=0 DEFAULT CHARSET="+table.getCharset()+" COMMENT='"+table.getTableComment()+"';\n";
            tableDDLList.add(tableDDL);
        }
        return tableDDLList;
    }

    /**
     * 构建建表字段SQL
     * @param column    字段对象
     * @return  字段SQL[xingming varchar(200) NOT NULL COMMENT '姓名']
     */
    private String exchangeToColumnSql(Column column){
        String splitTag = " ";
        boolean isNullable = !ObjectUtil.isNull(column.getIsNullable()) && !column.getIsNullable();
        String columnDefault = StringUtils.isNotBlank(column.getColumnDefault()) || !isNullable ?
                (DefaultStringUtils.containsChinese(column.getColumnDefault()) ? "DEFAULT '"+column.getColumnDefault()+"'"+splitTag : "DEFAULT "+column.getColumnDefault()+splitTag)
                : "";
        StringBuilder builder = new StringBuilder("  ").append(column.getColumnName()).append(splitTag).append(column.getColumnType()).append(splitTag)
                .append(isNullable ? "NOT NULL " : "").append(columnDefault)
                .append(StringUtils.isNotBlank(column.getExtra()) ? column.getExtra()+splitTag : "")
                .append("COMMENT '").append(column.getColumnComment()).append("'");
        return builder.toString();
    }

    /**
     * 构建建表索引SQL
     * @param index 索引对象
     * @return  索引SQL[UNIQUE KEY yonghuming(yonghuming) USING BTREE]
     */
    private String exchangeToIndexSql(Index index){
        String splitTag = " ";
        // 约束类型判断
        String constraintType = ObjectUtil.isNull(index.getConstraintType()) ? "KEY " : !index.getConstraintType().contains("KEY") ? index.getConstraintType()+" KEY " : index.getConstraintType()+splitTag;
        StringBuilder builder = new StringBuilder("  ")
                .append(constraintType)
                .append(index.getIndexName().contains("PRIMARY") ? "" : index.getIndexName()).append("(").append(StringUtils.join(index.getIndexColumnList(),",")).append(") ")
                .append("USING ").append(index.getIndexType())
                .append(StringUtils.isBlank(index.getIndexComment()) ? "" : " COMMENT '"+index.getIndexComment()+"'");
        return builder.toString();
    }

    /**
     * 从加密文本中解析出JSON对象
     * @param file          文件
     * @param aesPassword  密钥
     * @return  JSON对象
     * @throws InvalidPropertiesFormatException
     */
    @Override
    public SQLTableJSON getFromFile(MultipartFile file, String aesPassword) throws InvalidPropertiesFormatException {
        StringBuilder fileBuilder = new StringBuilder();
        SQLTableJSON base;
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))){
            String line;
            while ((line = reader.readLine()) != null){
                fileBuilder.append(line).append("\n");
            }
            String fileContent = fileBuilder.toString();
            String decode = AESUtils.decode(fileContent.replace("\"", ""), aesPassword);
            base = JSON.parseObject(decode, SQLTableJSON.class);
        } catch (NumberFormatException | IOException e){
            throw new InvalidPropertiesFormatException("文件解析失败，请检查文件是否被改动过!!");
        }
        return base;
    }
}
