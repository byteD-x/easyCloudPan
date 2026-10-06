package com.easypan.utils;

import com.easypan.entity.enums.VerifyRegexEnum;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 验证工具类，提供正则表达式验证功能.
 */
public class VerifyUtils {

    /**
     * 使用正则表达式验证字符串.
     *
     * @param regex 正则表达式
     * @param value 待验证的字符串
     * @return 验证结果
     */
    public static boolean verify(String regex, String value) {
        if (StringTools.isEmpty(value)) {
            return false;
        }
        Pattern pattern = Pattern.compile(regex);
        Matcher matcher = pattern.matcher(value);
        return matcher.matches();
    }

    /**
     * 使用枚举类型的正则表达式验证字符串.
     *
     * @param regex 正则表达式枚举
     * @param value 待验证的字符串
     * @return 验证结果
     */
    public static boolean verify(VerifyRegexEnum regex, String value) {
        return verify(regex.getRegex(), value);
    }

}

