package com.mycompany.app;

import org.apache.commons.text.lookup.StringLookup;

import java.util.Map;

public class MapLookup implements StringLookup {
    private Map<String, Object> map;

    public MapLookup(Map<String, Object> map) {
        this.map = map;
    }

    @Override
    public String lookup(String key) {
        Object value = map.get(key);
        if (value != null) {
            return value.toString();
        }
        return null;
    }
}
