package com.example.offer.tools;

import com.fasterxml.jackson.annotation.PropertyAccessor;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import tools.jackson.core.StreamWriteFeature;
import tools.jackson.databind.*;
import tools.jackson.databind.json.JsonMapper;

import static com.fasterxml.jackson.annotation.JsonAutoDetect.Visibility.ANY;

@Configuration
public class JsonConfiguration {

    public static final ObjectMapper OBJECT_MAPPER = JsonMapper.builder()
            .changeDefaultVisibility(v -> v
                    .withVisibility(PropertyAccessor.CREATOR, ANY)
                    .withVisibility(PropertyAccessor.FIELD, ANY))
            .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false)
            .configure(SerializationFeature.FAIL_ON_UNWRAPPED_TYPE_IDENTIFIERS, false)
            .configure(StreamWriteFeature.WRITE_BIGDECIMAL_AS_PLAIN, true)
            .build();

    @Bean
    ObjectMapper objectMapper() {
        return OBJECT_MAPPER;
    }
}
