package com.example.foolcardgame.data.api.dto

import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Test

class ProfileDtoTest {

    @Test
    fun profileDto_serializesAndDeserializes() {
        val dto = ProfileDto(displayName = "Иван", avatarId = 2)
        val json = Json.encodeToString(ProfileDto.serializer(), dto)
        val decoded = Json.decodeFromString(ProfileDto.serializer(), json)
        assertEquals(dto, decoded)
    }
}
