package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.util.Date;

/**
 * Title: TimestampSecondSerializer
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2020/3/8 21:40
 * @description Project Name: customer-robot
 * @Package: com.bhfae.customer.robot.utils.json
 */
public class TimestampSecondSerializer extends JsonSerializer<Date> {

    @Override
    public void serialize(Date date, JsonGenerator gen, SerializerProvider arg2) throws IOException {
        String dateStr = null;
        if (date != null) {
            dateStr = Long.toString(date.getTime() / 1000);
        }
        gen.writeString(dateStr);
    }
}
