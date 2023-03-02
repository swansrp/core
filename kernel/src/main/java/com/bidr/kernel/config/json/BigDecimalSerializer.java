package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonGenerator;
import com.fasterxml.jackson.databind.JsonSerializer;
import com.fasterxml.jackson.databind.SerializerProvider;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Title: BigDecimalSerializer
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/4/23 14:09
 */
public class BigDecimalSerializer extends JsonSerializer<BigDecimal> {

    @Override
    public void serialize(BigDecimal number, JsonGenerator gen, SerializerProvider arg2) throws IOException {
        String dateStr = null;
        if (number != null) {
            dateStr = number.toPlainString();
        }
        gen.writeString(dateStr);
    }
}
