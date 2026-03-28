package com.csee.swplus.mileage.portfolio.converter;

import com.csee.swplus.mileage.portfolio.dto.ProfileLinkDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA: {@code List<ProfileLinkDto>} ↔ JSON stored in TEXT.
 */
@Converter
public class ProfileLinksJsonConverter implements AttributeConverter<List<ProfileLinkDto>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<ProfileLinkDto>> TYPE = new TypeReference<List<ProfileLinkDto>>() {};

    @Override
    public String convertToDatabaseColumn(List<ProfileLinkDto> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize profile links", e);
        }
    }

    @Override
    public List<ProfileLinkDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<ProfileLinkDto> list = OBJECT_MAPPER.readValue(dbData, TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
