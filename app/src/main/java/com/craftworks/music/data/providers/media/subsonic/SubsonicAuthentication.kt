package com.craftworks.music.data.providers.media.subsonic

import com.craftworks.music.data.model.AuthenticationResponse
import com.craftworks.music.data.model.ProviderType

/**
 * Subsonic answers a rejected login with HTTP 200 and a "failed" status rather than an error code,
 * so the status and error fields are the only signal that the credentials were refused.
 */
internal fun SubsonicBody.toAuthenticationResponse(): AuthenticationResponse {
    check(status == "ok" && error == null) { "Subsonic authentication failed" }

    return AuthenticationResponse(
        isAdmin = user?.adminRole ?: false,
        providerType = when (type) {
            "navidrome" -> ProviderType.NAVIDROME
            else -> ProviderType.SUBSONIC
        }
    )
}
