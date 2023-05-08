package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.Date;

/**
 * Title: TimestampSecondDeserializer
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @since 2020/3/8 21:40
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.utils.json
 */
public class TimestampSecondDeserializer extends JsonDeserializer<Date> {

    @Override
    public Date deserialize(JsonParser arg0, DeserializationContext arg1) throws IOException {
        Date date = null;
        String timestampStr = arg0.getText();
        if (StringUtils.isNotBlank(timestampStr)) {
            long timestamp = Long.parseLong(timestampStr);
            date = new Date(timestamp * 1000);
        }
        return date;
    }
}
