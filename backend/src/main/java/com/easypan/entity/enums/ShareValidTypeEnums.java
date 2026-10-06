package com.easypan.entity.enums;

/**
 * 分享有效期类型枚举.
 */
public enum ShareValidTypeEnums {
    DAY_1(0, 1, "1天"),
    DAY_7(1, 7, "7天"),
    DAY_30(2, 30, "30天"),
    FOREVER(3, -1, "永久有效");

    private Integer type;
    private Integer days;
    private String desc;

    ShareValidTypeEnums(Integer type, Integer days, String desc) {
        this.type = type;
        this.days = days;
        this.desc = desc;
    }


    /**
     * 根据类型获取枚举.
     *
     * @param type 类型值
     * @return 枚举对象
     */
    public static ShareValidTypeEnums getByType(Integer type) {
        for (ShareValidTypeEnums typeEnums : ShareValidTypeEnums.values()) {
            if (typeEnums.getType().equals(type)) {
                return typeEnums;
            }
        }
        return null;
    }

    public Integer getType() {
        return type;
    }

    public Integer getDays() {
        return days;
    }

    public String getDesc() {
        return desc;
    }
}
