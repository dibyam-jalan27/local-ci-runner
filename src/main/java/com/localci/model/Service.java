package com.localci.model;

import java.util.List;
import java.util.Map;

/**
 * Represents a background service that runs alongside the main pipeline,
 * like a database (Postgres) or cache (Redis).
 */
public class Service {
    private String name;
    private String image;
    private List<String> ports;
    private Map<String, String> env;

    public Service() {
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getImage() {
        return image;
    }

    public void setImage(String image) {
        this.image = image;
    }

    public List<String> getPorts() {
        return ports;
    }

    public void setPorts(List<String> ports) {
        this.ports = ports;
    }

    public Map<String, String> getEnv() {
        return env;
    }

    public void setEnv(Map<String, String> env) {
        this.env = env;
    }
}
