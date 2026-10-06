package com.easypan.entity.enums;

/**
 * 文件分类枚举.
 */
public enum FileCategoryEnums {
    VIDEO(1, "video", "视频"),
    MUSIC(2, "music", "音频"),
    IMAGE(3, "image", "图片"),
    DOC(4, "doc", "文档"),
    OTHERS(5, "others", "其他");

    private final Integer category;
    private final String code;

    FileCategoryEnums(Integer category, String code, String desc) {
        this.category = category;
        this.code = code;
    }

    /**
     * 根据代码获取枚举.
     *
     * @param code 代码
     * @return 枚举对象
     */
    public static FileCategoryEnums getByCode(String code) {
        for (FileCategoryEnums item : FileCategoryEnums.values()) {
            if (item.getCode().equals(code)) {
                return item;
            }
        }
        return null;
    }

    public Integer getCategory() {
        return category;
    }

    public String getCode() {
        return code;
    }
}
