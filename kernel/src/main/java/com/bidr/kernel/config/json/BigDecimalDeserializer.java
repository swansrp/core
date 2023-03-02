package com.bidr.kernel.config.json;

import com.fasterxml.jackson.core.JsonParser;
import com.fasterxml.jackson.databind.DeserializationContext;
import com.fasterxml.jackson.databind.JsonDeserializer;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.math.BigDecimal;

/**
 * Title: BigDecimalDeserializer
 * Description: Copyright: Copyright (c) 2019 Company: BHFAE
 *
 * @author Sharp
 * @date 2021/4/23 14:09
 */
public class BigDecimalDeserializer extends JsonDeserializer<BigDecimal> {

    @Override
    public BigDecimal deserialize(JsonParser arg0, DeserializationContext arg1) throws IOException {
        String number = arg0.getText();
        if (StringUtils.isNotBlank(number)) {
            return BigDecimal.valueOf(Double.parseDouble(number));
        }
        return BigDecimal.ZERO;
    }
}

