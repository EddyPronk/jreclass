package com.mycompany.app;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public class YAMLDataSource implements DataSource {
    private final ObjectMapper yamlMapper = new ObjectMapper(new YAMLFactory());

    @Override
    public String fileExtension() {
        return ".yml";
    }

    @Override
    public <T> T readValue(InputStream src, Class<T> valueType) throws IOException {
        return yamlMapper.readValue(src, valueType);
    }
}
