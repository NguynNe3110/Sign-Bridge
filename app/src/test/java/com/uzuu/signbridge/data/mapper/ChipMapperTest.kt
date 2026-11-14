package com.uzuu.signbridge.data.mapper

import android.app.Application
import com.uzuu.signbridge.data.remote.dto.response.ChipResponseDto
import com.uzuu.signbridge.domain.model.ChipCategory
import com.uzuu.signbridge.domain.model.ServiceContext
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36], application = Application::class)
class ChipMapperTest {

    @Test
    fun parseJsonToChipResponseDto() {
        val json = JSONObject("""
            {
                "id": "test-uuid-1",
                "text": "Trà sữa",
                "context": "CAFE",
                "is_system": true,
                "user_id": "SYSTEM",
                "usage_count": 5
            }
        """.trimIndent())

        val dto = json.toChipResponseDto()
        assertEquals("test-uuid-1", dto.id)
        assertEquals("Trà sữa", dto.text)
        assertEquals("CAFE", dto.context)
        assertTrue(dto.isSystem)
        assertEquals("SYSTEM", dto.userId)
        assertEquals(5, dto.usageCount)
    }

    @Test
    fun mapDtoToDomainModel() {
        val systemDto = ChipResponseDto(
            id = "chip-1",
            text = "Cà phê đá",
            context = "CAFE",
            isSystem = true,
            userId = "SYSTEM",
            usageCount = 2,
        )
        val domainSystem = systemDto.toDomain()
        assertEquals("chip-1", domainSystem.id)
        assertEquals("Cà phê đá", domainSystem.label)
        assertEquals(ServiceContext.CAFE, domainSystem.context)
        assertEquals(ChipCategory.DETAIL, domainSystem.category)
        assertFalse(domainSystem.isPersonal)
        assertEquals(2, domainSystem.usageCount)

        val personalDto = ChipResponseDto(
            id = "chip-2",
            text = "Thêm thạch",
            context = "CAFE",
            isSystem = false,
            userId = "user-123",
            usageCount = 0,
        )
        val domainPersonal = personalDto.toDomain()
        assertTrue(domainPersonal.isPersonal)
    }

    @Test
    fun mapDtoToLocalEntity() {
        val dto = ChipResponseDto(
            id = "chip-3",
            text = "Panadol",
            context = "PHARMACY",
            isSystem = false,
            userId = "user-123",
            usageCount = 1,
        )
        val entity = dto.toEntity()
        assertEquals("chip-3", entity.id)
        assertEquals("Panadol", entity.label)
        assertEquals("PHARMACY", entity.contextId)
        assertTrue(entity.isPersonal)
        assertEquals(1, entity.usageCount)
    }
}
