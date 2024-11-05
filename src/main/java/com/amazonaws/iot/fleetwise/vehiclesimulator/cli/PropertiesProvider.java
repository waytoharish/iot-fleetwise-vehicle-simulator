package com.amazonaws.iot.fleetwise.vehiclesimulator.cli;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.time.Instant;
import java.util.Properties;

import org.jetbrains.annotations.Nullable;

public final class PropertiesProvider {

    private final Properties properties = new Properties();

    public static final PropertiesProvider INSTANCE = new PropertiesProvider();

    private PropertiesProvider() {
        try (InputStream inputStream = PropertiesProvider.class.getResourceAsStream("/version.properties")) {
            String line;
            if (inputStream != null) {
                BufferedReader reader  =new BufferedReader(new InputStreamReader(inputStream));
                while ((line = reader.readLine()) != null) {
                    String[] keyValuePair = line.split(":", 2);
                    properties.put(keyValuePair[0], keyValuePair[1]);
                }
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String getVersion() {
        return properties.getProperty("version");
    }

    @Nullable
    public Instant getBuildTime() {
        return Instant.ofEpochMilli(Long.parseLong(properties.getProperty("buildTime")));
    }

}