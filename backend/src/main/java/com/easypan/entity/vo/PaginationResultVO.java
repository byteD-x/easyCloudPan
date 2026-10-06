package com.easypan.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

/**
 * 分页结果 VO.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PaginationResultVO<T> {
    private Integer totalCount;
    private Integer pageSize;
    private Integer pageNo;
    private Integer pageTotal;
    private List<T> list = new ArrayList<T>();

    /**
     * 构造函数.
     *
     * @param totalCount 总记录数
     * @param pageSize 每页大小
     * @param pageNo 页码
     * @param list 数据列表
     */
    public PaginationResultVO(Integer totalCount, Integer pageSize, Integer pageNo, List<T> list) {
        this.totalCount = totalCount;
        this.pageSize = pageSize;
        this.pageNo = pageNo;
        this.list = list;
    }

    /**
     * 构造函数.
     *
     * @param list 数据列表
     */
    public PaginationResultVO(List<T> list) {
        this.list = list;
    }

    /**
     * 构建分页结果.
     *
     * @param totalCount 总记录数
     * @param pageSize 每页大小
     * @param pageNo 页码
     * @param pageTotal 总页数
     * @param list 数据列表
     * @return 分页结果
     */
    public static <T> PaginationResultVO<T> build(Integer totalCount, Integer pageSize, Integer pageNo,
            Integer pageTotal, List<T> list) {
        if (pageNo == null || pageNo == 0) {
            pageNo = 1;
        }
        return new PaginationResultVO<>(totalCount, pageSize, pageNo, pageTotal, list);
    }
}
