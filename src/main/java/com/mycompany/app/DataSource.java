package com.mycompany.app;

import com.fasterxml.jackson.core.exc.StreamReadException;
import com.fasterxml.jackson.databind.DatabindException;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;

public interface DataSource {
    String fileExtension();

    public <T> T readValue(InputStream src, Class<T> valueType) throws IOException, StreamReadException, DatabindException;
}

