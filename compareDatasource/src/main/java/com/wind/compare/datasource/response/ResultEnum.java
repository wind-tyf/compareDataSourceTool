package com.wind.compare.datasource.response;

/**
 * @author wind.tan
 * @date 2024-05-15
 */
public enum ResultEnum implements IResult {
    OK(200, "success"),
    ERROR(500, "error");

    private final int code;
    private final String message;

    private ResultEnum(int code, String message) {
        this.code = code;
        this.message = message;
    }

    public static ResultEnum getResultEnum(int code) {
        ResultEnum[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            ResultEnum type = var1[var3];
            if (type.getCode() == code) {
                return type;
            }
        }

        return ERROR;
    }

    public static ResultEnum getResultEnum(String message) {
        ResultEnum[] var1 = values();
        int var2 = var1.length;

        for(int var3 = 0; var3 < var2; ++var3) {
            ResultEnum type = var1[var3];
            if (type.getMessage().equals(message)) {
                return type;
            }
        }

        return ERROR;
    }

    public int getCode() {
        return this.code;
    }

    public String getMessage() {
        return this.message;
    }
}
