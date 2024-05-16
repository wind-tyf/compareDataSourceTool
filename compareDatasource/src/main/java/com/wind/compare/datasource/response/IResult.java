package com.wind.compare.datasource.response;

/**
 * @author wind.tan
 * @date 2024-05-15
 */
public interface IResult {
    /**
     * @return 返回code
     */
    int getCode();

    /**
     * @return 返回提示信息
     */
    String getMessage();
}
