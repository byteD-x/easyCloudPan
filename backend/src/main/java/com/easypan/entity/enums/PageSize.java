package com.easypan.entity.enums;

/**
 * 分页大小枚举.
 */
public enum PageSize {
    SIZE15(15), SIZE20(20), SIZE30(30), SIZE40(40), SIZE50(50);

    int size;

    PageSize(int size) {
        this.size = size;
    }

    public int getSize() {
        return this.size;
    }
}
