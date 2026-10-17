package com.bidr.kernel.config.log;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.filter.AbstractMatcherFilter;
import ch.qos.logback.core.spi.FilterReply;

/**
 * Title: DynamicLogFilter
 * Description: Copyright: Copyright (c) 2025 Company: Bidr Ltd.
 *
 * @author Sharp
 * @since 2025/6/13 11:51
 */

public class DynamicLogFilter extends AbstractMatcherFilter<ILoggingEvent> {

    @Override
    public FilterReply decide(ILoggingEvent event) {
        return LogSuppressor.isLoggingSuppressed() ? FilterReply.DENY : FilterReply.NEUTRAL;
    }
}
