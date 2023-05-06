package com.bidr.kernel.config.response;

import com.bidr.kernel.constant.CommonConst;
import com.bidr.kernel.utils.StringUtil;
import com.bidr.kernel.vo.common.CommonResVO;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Title: ResponseHandler
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/1/2 21:39
 * @description Project Name: Seed
 * @Package: com.srct.service.config.response
 */
public class ResponseHandler {
    @ResponseBody
    public static <T> ResponseEntity<Response<T>> generateResponse(T data) {
        Response<T> res = new Response<T>(data);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ResponseBody
    public static ResponseEntity<Response<CommonResVO>> commonResponse() {
        return commonResponse(CommonConst.SUCCESS, 0);
    }

    @ResponseBody
    public static ResponseEntity<Response<CommonResVO>> commonResponse(String result, Integer number) {
        CommonResVO vo = new CommonResVO(result, number);
        Response<CommonResVO> res = new Response<>(vo);
        return new ResponseEntity<>(res, HttpStatus.OK);
    }

    @ResponseBody
    public static ResponseEntity<Response<CommonResVO>> commonResponse(String result) {
        return commonResponse(result, 0);
    }

    @ResponseBody
    public static ResponseEntity<Response<CommonResVO>> commonResponse(Integer number) {
        return commonResponse(CommonConst.SUCCESS, number);
    }

    @ResponseBody
    public static ResponseEntity<Response<CommonResVO>> commonResponse(String result, boolean success) {
        return commonResponse(result + (success ? "成功" : "失败"), 0);
    }

    @ResponseBody
    public static ResponseEntity<Response<CommonResVO>> commonResponse(boolean success) {
        return commonResponse(StringUtil.convertSwitch(success), 0);
    }
}
