package com.easypan.entity.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import java.io.Serializable;
import lombok.AllArgsConstructor;
import lombok.Data;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.NoArgsConstructor;

/**
 * 上传结果数据传输对象.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@JsonIgnoreProperties(ignoreUnknown = true)
@Schema(description = "Upload Result Information")
public class UploadResultDto implements Serializable {
    @Schema(description = "Uploaded File ID")
    private String fileId;
    @Schema(description = "Upload Status")
    private String status;
}
