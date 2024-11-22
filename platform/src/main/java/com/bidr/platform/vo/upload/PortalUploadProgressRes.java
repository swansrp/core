package com.bidr.platform.vo.upload;

import com.bidr.platform.constant.upload.UploadProgressStep;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Title: PortalUploadProgressRes
 * Description: Copyright: Copyright (c) 2023
 *
 * @author Sharp
 * @since 2024/02/16 16:37
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class PortalUploadProgressRes {
    private UploadProgressStep step;
    private Integer total;
    private Integer loaded;
    private List<String> comments;

}
