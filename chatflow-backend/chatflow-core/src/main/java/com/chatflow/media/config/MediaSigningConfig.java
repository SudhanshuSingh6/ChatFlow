package com.chatflow.media.config;

import com.chatflow.media.storage.MediaUrlSigner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Provides the {@link MediaUrlSigner} bean in core only. The signing secret is media-specific
 * ({@code app.media.signing-secret}), kept separate from {@code app.jwt.secret} so the two trust
 * domains rotate independently. The chatflow-media worker does not define this bean — it never
 * mints URLs — so {@code LocalMediaStorageService} treats the signer as optional.
 */
@Configuration
class MediaSigningConfig {

    @Bean
    MediaUrlSigner mediaUrlSigner(@Value("${app.media.signing-secret}") String secret) {
        return new MediaUrlSigner(secret);
    }
}
