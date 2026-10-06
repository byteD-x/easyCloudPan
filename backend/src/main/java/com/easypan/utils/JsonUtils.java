package com.easypan.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

/**
 * JSON 工具类，提供对象与 JSON 之间的转换功能.
 */
public class JsonUtils {
    private static final Logger logger = LoggerFactory.getLogger(JsonUtils.class);

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * 将对象转换为 JSON 字符串.
     *
     * @param obj 对象
     * @return JSON 字符串
     */
    public static String convertObj2Json(Object obj) {
        try {
            return OBJECT_MAPPER.writeValueAsString(obj);
        } catch (JsonProcessingException e) {
            logger.error("convertObj2Json error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 将 JSON 字符串转换为对象.
     *
     * @param json JSON 字符串
     * @param classz 目标类型
     * @param <T> 目标类型
     * @return 目标对象
     */
    public static <T> T convertJson2Obj(String json, Class<T> classz) {
        try {
            return OBJECT_MAPPER.readValue(json, classz);
        } catch (JsonProcessingException e) {
            logger.error("convertJson2Obj error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 将 JSON 数组转换为列表.
     *
     * @param json JSON 字符串
     * @param classz 元素类型
     * @param <T> 元素类型
     * @return 列表
     */
    public static <T> List<T> convertJsonArray2List(String json, Class<T> classz) {
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructCollectionType(List.class, classz));
        } catch (JsonProcessingException e) {
            logger.error("convertJsonArray2List error", e);
            throw new RuntimeException(e);
        }
    }

    /**
     * 将 JSON 字符串转换为 Map.
     *
     * @param json JSON 字符串
     * @return Map 对象
     */
    public static Map<String, Object> convertJson2Map(String json) {
        try {
            return OBJECT_MAPPER.readValue(json,
                    OBJECT_MAPPER.getTypeFactory().constructMapType(Map.class, String.class, Object.class));
        } catch (JsonProcessingException e) {
            logger.error("convertJson2Map error", e);
            throw new RuntimeException(e);
        }
    }

}
