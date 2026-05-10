package com.csee.swplus.mileage.portfolio.converter;

import com.csee.swplus.mileage.portfolio.dto.TeamRoleDto;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import javax.persistence.AttributeConverter;
import javax.persistence.Converter;
import java.util.ArrayList;
import java.util.List;

/**
 * JPA: {@code List<TeamRoleDto>} ↔ JSON stored in TEXT (mirrors {@link ProfileLinksJsonConverter}).
 */
@Converter
public class TeamCompositionJsonConverter implements AttributeConverter<List<TeamRoleDto>, String> {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();
    private static final TypeReference<List<TeamRoleDto>> TYPE = new TypeReference<List<TeamRoleDto>>() {};

    @Override
    public String convertToDatabaseColumn(List<TeamRoleDto> attribute) {
        if (attribute == null || attribute.isEmpty()) {
            return "[]";
        }
        try {
            return OBJECT_MAPPER.writeValueAsString(attribute);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Failed to serialize team composition", e);
        }
    }

    @Override
    public List<TeamRoleDto> convertToEntityAttribute(String dbData) {
        if (dbData == null || dbData.trim().isEmpty()) {
            return new ArrayList<>();
        }
        try {
            List<TeamRoleDto> list = OBJECT_MAPPER.readValue(dbData, TYPE);
            return list != null ? list : new ArrayList<>();
        } catch (JsonProcessingException e) {
            return new ArrayList<>();
        }
    }
}
