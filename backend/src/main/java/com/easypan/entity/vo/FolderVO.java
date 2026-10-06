package com.easypan.entity.vo;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 文件夹视图对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class FolderVO {
    private String fileName;
    private String fileId;
}
