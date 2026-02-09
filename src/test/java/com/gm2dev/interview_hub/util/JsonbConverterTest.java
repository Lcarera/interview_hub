package com.gm2dev.interview_hub.util;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

class JsonbConverterTest {

    private final JsonbConverter converter = new JsonbConverter();

    @Test
    void convertToDatabaseColumn_withNull_returnsNull() {
        assertNull(converter.convertToDatabaseColumn(null));
    }

    @Test
    void convertToDatabaseColumn_withValidMap_returnsJsonString() {
        Map<String, Object> input = Map.of("name", "Alice", "score", 95);

        String json = converter.convertToDatabaseColumn(input);

        assertNotNull(json);
        assertTrue(json.contains("\"name\""));
        assertTrue(json.contains("\"Alice\""));
        assertTrue(json.contains("\"score\""));
        assertTrue(json.contains("95"));
    }

    @Test
    void convertToEntityAttribute_withNull_returnsNull() {
        assertNull(converter.convertToEntityAttribute(null));
    }

    @Test
    void convertToEntityAttribute_withValidJson_returnsMap() {
        String json = "{\"name\":\"Alice\",\"score\":95}";

        Map<String, Object> result = converter.convertToEntityAttribute(json);

        assertNotNull(result);
        assertEquals("Alice", result.get("name"));
        assertEquals(95, result.get("score"));
    }

    @Test
    void convertToEntityAttribute_withInvalidJson_throwsException() {
        String invalidJson = "not valid json {{{";

        IllegalArgumentException ex = assertThrows(
                IllegalArgumentException.class,
                () -> converter.convertToEntityAttribute(invalidJson)
        );
        assertEquals("Error converting JSON to Map", ex.getMessage());
    }
}
