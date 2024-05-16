package com.wind.compare.datasource.controller;

import cn.hutool.core.collection.CollectionUtil;
import com.alibaba.fastjson.JSON;
import com.wind.compare.datasource.response.Result;
import com.wind.compare.datasource.service.CompareDataSourceService;
import com.wind.compare.datasource.utils.AESUtils;
import com.wind.compare.datasource.pojo.datasource.SQLTableJSON;
import com.wind.compare.datasource.pojo.dto.DatasourceSettingDto;
import com.wind.compare.datasource.pojo.vo.DatasourceSettingVo;
import com.wind.compare.datasource.utils.FileUtils;
import jakarta.annotation.Resource;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.Collections;
import java.util.InvalidPropertiesFormatException;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ThreadPoolExecutor;

import static com.wind.compare.datasource.config.ThreadPoolConfig.COMMON_POOL;

/**
 * @author wind.tan
 * @date 2024-05-14
 */
@Slf4j
@RestController
@RequestMapping("/compare")
public class CompareDataSourceController {
    
    @Resource
    private CompareDataSourceService compareDataSourceService;

    @Resource(name = COMMON_POOL)
    private ThreadPoolExecutor commonThreadPool;

    @Value("default.project.aes.password")
    private String AES_PASSWORD;

    /**
     * 验证接口连通性
     * @return  成功返回true
     */
    @GetMapping("/check")
    public Boolean check(){
        return true;
    }

    /**
     * 检查数据库配置的连通性
     * @param datasourceSettingVo 数据库配置类
     * @return  能否连接成功
     */
    @PostMapping("/checkDataSourceConnection")
    public Result<Boolean> checkDataSourceConnection(@RequestBody DatasourceSettingVo datasourceSettingVo){
        DatasourceSettingDto datasourceSetting = DatasourceSettingDto.exchangeVoToDto(datasourceSettingVo);
        try {
            compareDataSourceService.getDynamicsJdbcTemplate(datasourceSetting);
        }catch (Exception e){
            String errorMsg = "无法连接数据库!请检查配置:"+JSON.toJSONString(datasourceSetting);
            log.error(errorMsg);
            return Result.error(errorMsg);
        }
        return Result.ok(Boolean.TRUE);
    }

    /**
     * 导出加密后的文本
     * @param request               request
     * @param response              response
     * @param datasourceSettingVo   数据库配置
     * @throws IOException
     */
    @PostMapping("/export/generateDataSource")
    public void getAll(HttpServletRequest request, HttpServletResponse response, @RequestBody DatasourceSettingVo datasourceSettingVo) throws IOException, ExecutionException, InterruptedException {

        // 1、获取JSON对象
        DatasourceSettingDto datasourceSetting = DatasourceSettingDto.exchangeVoToDto(datasourceSettingVo);
        SQLTableJSON sqlTableJson = compareDataSourceService.generateJsonTable(datasourceSetting);

        // 2、加密文件
        String fileName = "generateDatasource_" + System.currentTimeMillis() + ".txt";
        List<String> decodeDataSource = Collections.singletonList(AESUtils.encode(JSON.toJSONString(sqlTableJson), AES_PASSWORD));

        // 3、下载封装
        FileUtils.downloadFile(request, response, fileName, decodeDataSource);
    }

    /**
     * 对数据库连接配置AES加密
     * @param datasourceSettingVo   数据库连接配置
     * @return  加密结果
     */
    @PostMapping("/generateEncryptSetting")
    public Result<String> generateEncryptSetting(@RequestBody DatasourceSettingVo datasourceSettingVo){
        return Result.ok(AESUtils.encode(JSON.toJSONString(datasourceSettingVo), AES_PASSWORD));
    }

    /**
     * 对比两个SQLTableJSON对象的差异，导出差异结果
     * @param request   request
     * @param response  response
     * @param file      基准库的数据库表结构加密文件
     * @param setting   加密的数据库配置
     * @throws IOException
     */
    @PostMapping("/export/compareDiffDataSource")
    public void compareDiffDataSource(HttpServletRequest request, HttpServletResponse response, @RequestPart("file") MultipartFile file, @RequestParam("setting") String setting) throws UnsupportedEncodingException, ExecutionException, InterruptedException {
        // 1、获取指定数据库的JSON对象 compare
        CompletableFuture<SQLTableJSON> compareFuture = CompletableFuture.supplyAsync(() -> {
            DatasourceSettingVo datasourceSettingVo = JSON.parseObject(AESUtils.decode(setting, AES_PASSWORD), DatasourceSettingVo.class);
            assert datasourceSettingVo != null;
            try {
                return compareDataSourceService.generateJsonTable(DatasourceSettingDto.exchangeVoToDto(datasourceSettingVo));
            } catch (ExecutionException | InterruptedException e) {
                throw new RuntimeException(e);
            }
        }, commonThreadPool).exceptionally((e)->{
            throw new RuntimeException(e);
        });
        // 2、上传文件解析得到 base
        CompletableFuture<SQLTableJSON> baseFuture = CompletableFuture.supplyAsync(() -> {
            try {
                return compareDataSourceService.getFromFile(file, AES_PASSWORD);
            } catch (InvalidPropertiesFormatException e) {
                throw new RuntimeException(e);
            }
        }, commonThreadPool).exceptionally((e)->{
            throw new RuntimeException(e);
        });

        // 3、获取差异化结果(base VS compare)
        List<String> diffResult = compareDataSourceService.compareDiffDataSource(baseFuture.get(), compareFuture.get());
        if (CollectionUtil.isEmpty(diffResult)){
            diffResult = Collections.singletonList("两个数据库的表结构没有差异!!");
        }

        // 4、下载封装
        String fileName = "compareDiffDataSource_" + System.currentTimeMillis() + ".txt";
        FileUtils.downloadFile(request, response, fileName, diffResult);
    }
}
