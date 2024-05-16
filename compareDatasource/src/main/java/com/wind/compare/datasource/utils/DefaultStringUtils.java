package com.wind.compare.datasource.utils;

import io.micrometer.common.util.StringUtils;

import java.util.regex.Pattern;

/**
 * @author wind.tan
 * @date 2024-05-14
 */
public class DefaultStringUtils {

    /**
     * 正则表达式规则，匹配中文字符
     * @param str
     * @return
     */
    public static boolean containsChinese(String str) {
        if (StringUtils.isBlank(str)){
            return false;
        }
        // 正则表达式规则，匹配中文字符
        String chineseRegex = "[\u4e00-\u9fa5]";
        // 使用Pattern编译正则表达式，使用Matcher检查字符串
        return Pattern.compile(chineseRegex).matcher(str).find();
    }
}
