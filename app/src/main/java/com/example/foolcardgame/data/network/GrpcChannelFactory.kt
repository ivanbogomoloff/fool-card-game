package com.example.foolcardgame.data.network

import com.example.foolcardgame.BuildConfig
import io.grpc.ManagedChannel
import io.grpc.ManagedChannelBuilder
import io.grpc.okhttp.OkHttpChannelBuilder
import java.util.concurrent.TimeUnit

/**
 * gRPC channel: debug — plaintext к LAN; release — TLS к prod-хосту.
 */
object GrpcChannelFactory {

    private const val KEEPALIVE_TIME_SEC = 30L
    private const val KEEPALIVE_TIMEOUT_SEC = 10L

    fun create(
        host: String = BuildConfig.GRPC_HOST,
        port: Int = BuildConfig.GRPC_PORT,
        useTls: Boolean = BuildConfig.GRPC_USE_TLS,
    ): ManagedChannel {
        val builder: ManagedChannelBuilder<*> = OkHttpChannelBuilder.forAddress(host, port)
            .keepAliveTime(KEEPALIVE_TIME_SEC, TimeUnit.SECONDS)
            .keepAliveTimeout(KEEPALIVE_TIMEOUT_SEC, TimeUnit.SECONDS)
            .keepAliveWithoutCalls(true)
        if (!useTls) {
            builder.usePlaintext()
        }
        return builder.build()
    }
}
