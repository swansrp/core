package com.bidr.document.builtin;

import com.bidr.document.DocumentArtifact;
import com.bidr.document.RouteTrace;
import com.bidr.document.spi.ArtifactStep;

/**
 * Title: DefaultArtifactStep
 * Description: Artifact 槽内置实现＝正文＋路线留痕直包装（不增删不改写）。
 *
 * @author Sharp
 * @since 2026/10/20
 */
public class DefaultArtifactStep implements ArtifactStep {

    @Override
    public DocumentArtifact shape(String markdown, RouteTrace trace) {
        return new DocumentArtifact(markdown, trace);
    }
}
