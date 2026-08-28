package io.github.alanlaw.vfc;

/** Identifies the consumer geometry used by a renderer output. */
public enum RenderTargetRole {
    /** User-visible camera preview output. */
    PREVIEW,
    /** ImageReader/YUV output whose buffer geometry must remain unchanged. */
    READER,
    /** Still/YUV capture rendering, always based on the raw target buffer. */
    CAPTURE
}
