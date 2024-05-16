package com.wind.compare.datasource.utils;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.io.OutputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.List;

/**
 * @author wind.tan
 * @date 2024-05-16
 */
public class FileUtils {

    /**
     * 下载文件通用方法
     * @param request       request
     * @param response      response
     * @param fileName      文件名
     * @param contentList   写入内容
     * @throws UnsupportedEncodingException
     */
    public static void downloadFile(HttpServletRequest request, HttpServletResponse response, String fileName, List<String> contentList) throws UnsupportedEncodingException {
        // 告诉浏览器下载的方式以及一些设置
        // 解决文件名乱码问题，获取浏览器类型，转换对应文件名编码格式，IE要求文件名必须是utf-8, firefo要求是iso-8859-1编码
        fileName = URLEncoder.encode(fileName, "UTF-8");
        // 设置下载文件的mineType，告诉浏览器下载文件类型
        String mineType = request.getServletContext().getMimeType(fileName);
        response.setContentType(mineType);
        // 设置一个响应头，无论是否被浏览器解析，都下载
        response.setHeader("Content-disposition", "attachment; filename=" + fileName);
        // 将要下载的文件内容通过输出流写到浏览器
        try (OutputStream out = response.getOutputStream()){
            // 逐条写入需要导出的SQL
            for (String content : contentList) {
                out.write(content.getBytes());
                // SQL换行
                String newLine = System.getProperty("line.separator");
                out.write(newLine.getBytes());
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }
}
