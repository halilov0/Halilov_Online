/**
 * Image storage and processing.
 *
 * <p>{@link com.halilov.online.media.MediaStorage} is the abstraction.
 * Two implementations exist:
 * <ul>
 *   <li>{@link com.halilov.online.media.LocalFileMediaStorage} — disk
 *       under {@code app.media.localDir} (default {@code ./media/}),
 *       exposed by {@link com.halilov.online.media.MediaWebConfig} at
 *       {@code /api/media/**}. Used for dev and as the prod fallback.</li>
 *   <li>{@link com.halilov.online.media.R2MediaStorage} — Cloudflare R2
 *       via the AWS S3 SDK v2. Activates when {@code MEDIA_STORAGE=r2}
 *       and {@code app.media.r2.*} are populated.</li>
 * </ul>
 *
 * <p>{@link com.halilov.online.media.ImageProcessor} runs on upload:
 * resize to a sane maximum, re-encode to a single canonical format,
 * strip metadata. No multi-image composition — the requirements
 * explicitly scope this to single-image transforms.
 *
 * <p>The DB stores only URLs ({@code Product.image_urls}); the bytes
 * live behind the storage backend.
 */
package com.halilov.online.media;
