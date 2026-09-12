package com.example.foolcardgame.data.network

import io.grpc.Metadata
import io.grpc.Status
import io.grpc.StatusException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AuthMetadataTest {

    @Test
    fun bearerHeaders_putsAuthorization() {
        val headers = AuthMetadata.bearerHeaders("tok-123")
        val key = Metadata.Key.of("authorization", Metadata.ASCII_STRING_MARSHALLER)
        assertEquals("Bearer tok-123", headers.get(key))
    }
}

class GrpcErrorMapperTest {

    @Test
    fun mapsUnauthenticated() {
        val msg = GrpcErrorMapper.toMessage(
            StatusException(Status.UNAUTHENTICATED.withDescription("bad token")),
        )
        assertEquals("bad token", msg)
    }

    @Test
    fun mapsUnavailable_default() {
        val msg = GrpcErrorMapper.toMessage(StatusException(Status.UNAVAILABLE))
        assertTrue(msg.contains("недоступен", ignoreCase = true))
    }

    @Test
    fun mapsFailedPrecondition_nameTaken() {
        val msg = GrpcErrorMapper.toMessage(
            StatusException(
                Status.FAILED_PRECONDITION.withDescription("имя занято, укажите пароль"),
            ),
        )
        assertEquals("Имя занято, введите другое имя", msg)
    }

    @Test
    fun mapsGenericThrowable() {
        assertEquals("boom", GrpcErrorMapper.toMessage(RuntimeException("boom")))
    }
}
