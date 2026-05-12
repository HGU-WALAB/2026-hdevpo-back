package com.csee.swplus.mileage.portfolio.converter;

import com.csee.swplus.mileage.portfolio.dto.DesignPreferencesDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;

/**
 * JPA: {@link DesignPreferencesDto} ↔ JSON stored in TEXT.
 * {@code null} entity → {@code null} column (so the column reads back as null when never set).
 */
@Converter
public class DesignPreferencesJsonConverter implements AttributeConverter<DesignPreferencesDto, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Override
    public String convertToDatabaseColumn(DesignPreferencesDto attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize design preferences", e);
        }
    }

    @Override
    public DesignPreferencesDto convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, DesignPreferencesDto.class);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
