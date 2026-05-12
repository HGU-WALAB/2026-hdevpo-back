package com.csee.swplus.mileage.portfolio.converter;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.List;

/**
 * JPA: {@link List}{@code <Long>} ↔ JSON array stored in TEXT.
 * <p>
 * {@code null} entity → {@code null} column (so legacy rows that never had selections read back as {@code null}).
 * Empty list is preserved as the literal {@code "[]"} so it can round-trip distinctly from "never set".
 */
@Converter
public class LongListJsonConverter implements AttributeConverter<List<Long>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<Long>> TYPE = new TypeReference<List<Long>>() {};

    @Override
    public String convertToDatabaseColumn(List<Long> attribute) {
        if (attribute == null) {
            return null;
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize id list", e);
        }
    }

    @Override
    public List<Long> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return null;
        }
        try {
            return OBJECT_MAPPER.readValue(dbData, TYPE);
        } catch (JsonProcessingException e) {
            return null;
        }
    }
}
