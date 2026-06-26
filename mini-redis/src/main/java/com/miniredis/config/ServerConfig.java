package com.miniredis.config;

/**
 * Server configuration for Mini Redis.
 * Centralizes all configurable settings.
 */
public class ServerConfig {

    // ── Network ──
    private int port = 6380;
    private int maxClients = 100;

    // ── Persistence ──
    private String dataDir = "data";
    private String aofFilename = "appendonly.aof";
    private String rdbFilename = "dump.json";
    private boolean aofEnabled = true;
    private boolean rdbEnabled = true;
    private int rdbSaveIntervalSeconds = 60;

    // ── Expiry ──
    private int activeExpiryIntervalMs = 100;   // Run active expiry every 100ms
    private int activeExpirySampleSize = 20;      // Sample 20 keys per cycle
    private double activeExpiryThreshold = 0.25;  // Repeat if >25% expired

    // ── Getters ──

    public int getPort() {
        return port;
    }

    public int getMaxClients() {
        return maxClients;
    }

    public String getDataDir() {
        return dataDir;
    }

    public String getAofFilename() {
        return aofFilename;
    }

    public String getAofFilePath() {
        return dataDir + "/" + aofFilename;
    }

    public String getRdbFilename() {
        return rdbFilename;
    }

    public String getRdbFilePath() {
        return dataDir + "/" + rdbFilename;
    }

    public boolean isAofEnabled() {
        return aofEnabled;
    }

    public boolean isRdbEnabled() {
        return rdbEnabled;
    }

    public int getRdbSaveIntervalSeconds() {
        return rdbSaveIntervalSeconds;
    }

    public int getActiveExpiryIntervalMs() {
        return activeExpiryIntervalMs;
    }

    public int getActiveExpirySampleSize() {
        return activeExpirySampleSize;
    }

    public double getActiveExpiryThreshold() {
        return activeExpiryThreshold;
    }

    // ── Setters (for future CLI arg parsing) ──

    public void setPort(int port) {
        this.port = port;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public void setAofEnabled(boolean aofEnabled) {
        this.aofEnabled = aofEnabled;
    }

    public void setRdbEnabled(boolean rdbEnabled) {
        this.rdbEnabled = rdbEnabled;
    }

    public void setRdbSaveIntervalSeconds(int rdbSaveIntervalSeconds) {
        this.rdbSaveIntervalSeconds = rdbSaveIntervalSeconds;
    }
}
