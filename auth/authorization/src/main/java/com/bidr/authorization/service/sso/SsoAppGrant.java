package com.bidr.authorization.service.sso;

import lombok.Data;

import java.util.Collections;
import java.util.List;

/**
 * M23 应用授权快照（组码 + 功能码）。
 *
 * @author sharp
 * @since 2026-10-27
 */
@Data
public class SsoAppGrant {

    private List<String> groups = Collections.emptyList();

    private List<String> functions = Collections.emptyList();

    public static SsoAppGrant empty() {
        return new SsoAppGrant();
    }
}