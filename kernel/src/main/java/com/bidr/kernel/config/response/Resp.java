package com.bidr.kernel.config.response;

import com.bidr.kernel.exception.NoticeException;
import lombok.Data;

/**
 * Title: R
 * Description: Copyright: Copyright (c) 2022 Company: Sharp Ltd.
 *
 * @author Sharp
 * @date 2023/04/13 13:42
 */
@Data
public class Resp {
    public static void notice(String noticeFormat, Object... parameter) {
        throw new NoticeException(String.format(noticeFormat, parameter));
    }
}
