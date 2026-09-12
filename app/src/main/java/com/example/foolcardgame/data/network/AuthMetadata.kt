package com.example.foolcardgame.data.network

import io.grpc.Metadata

/** Metadata `authorization: Bearer <token>` для защищённых RPC. */
object AuthMetadata {

    private val AUTHORIZATION_KEY: Metadata.Key<String> =
        Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)

    fun bearerHeaders(token: String): Metadata {
        val metadata = Metadata()
        metadata.put(AUTHORIZATION_KEY, "Bearer $token")
        return metadata
    }
}
