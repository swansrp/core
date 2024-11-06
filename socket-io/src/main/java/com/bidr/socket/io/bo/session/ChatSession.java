package com.bidr.socket.io.bo.session;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

/**
 * Title: ChatSession
 * Description: Copyright: Copyright (c) 2019 Company: bidr
 *
 * @author Sharp
 * @since 2024/10/31 10:48
 */
@Data
@AllArgsConstructor
@NoArgsConstructor
public class ChatSession {
    private String namespace;
    private String userId;
    private UUID sessionId;
}
