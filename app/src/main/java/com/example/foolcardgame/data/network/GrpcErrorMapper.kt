package com.example.foolcardgame.data.network

import io.grpc.Status
import io.grpc.StatusException
import io.grpc.StatusRuntimeException

/** Маппинг gRPC status → текст для UI. */
object GrpcErrorMapper {

    fun toMessage(error: Throwable): String {
        val status = when (error) {
            is StatusException -> error.status
            is StatusRuntimeException -> error.status
            else -> null
        }
        if (status == null) {
            return error.message?.takeIf { it.isNotBlank() } ?: "Неизвестная ошибка"
        }
        val description = status.description?.takeIf { it.isNotBlank() }
        return when (status.code) {
            Status.Code.UNAUTHENTICATED -> description ?: "Требуется вход"
            Status.Code.PERMISSION_DENIED -> description ?: "Нет доступа"
            Status.Code.NOT_FOUND -> description ?: "Не найдено"
            Status.Code.INVALID_ARGUMENT -> description ?: "Некорректные данные"
            Status.Code.FAILED_PRECONDITION -> when {
                description?.contains("имя занято", ignoreCase = true) == true ->
                    "Имя занято, введите другое имя"
                else -> description ?: "Нельзя выполнить действие"
            }
            Status.Code.UNAVAILABLE -> description ?: "Сервер недоступен"
            Status.Code.DEADLINE_EXCEEDED -> description ?: "Превышено время ожидания"
            Status.Code.ALREADY_EXISTS -> description ?: "Уже существует"
            Status.Code.RESOURCE_EXHAUSTED -> description ?: "Слишком много запросов"
            Status.Code.CANCELLED -> description ?: "Отменено"
            else -> description ?: "Ошибка сервера (${status.code})"
        }
    }
}
