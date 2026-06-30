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
    private String rdbFilename = "dump.rdb";
    private boolean aofEnabled = true;
    private boolean rdbEnabled = true;
    private int rdbSaveIntervalSeconds = 60;
    private long aofRewriteMinSize = 1_048_576;  // 1MB default
    private int aofRewriteGrowthPercent = 100;   // 100% default
    private long maxMemoryBytes = 0;             // 0 = no limit
    private String evictionPolicy = "noeviction";
    private int maxMemorySamples = 5;
    private boolean nioEnabled = false;
    private int idleTimeoutSeconds = 300;
    private int tcpBacklog = 511;
    private boolean tlsEnabled = false;
    private String tlsCertPath;
    private String tlsKeyPath;
    private int tlsPort = 6380;
    private String replicaOfHost;
    private int replicaOfPort = -1;
    private long replBacklogSize = 1048576; // 1MB default
    private boolean clusterEnabled = false;

    // ── Dashboard ──
    private int dashboardPort = 8080;
    private boolean dashboardEnabled = true;

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

    public long getMaxMemoryBytes() {
        return maxMemoryBytes;
    }

    public String getEvictionPolicy() {
        return evictionPolicy;
    }

    public int getMaxMemorySamples() {
        return maxMemorySamples;
    }

    public boolean isNioEnabled() {
        return nioEnabled;
    }

    public int getIdleTimeoutSeconds() {
        return idleTimeoutSeconds;
    }

    public int getTcpBacklog() {
        return tcpBacklog;
    }

    public boolean isTlsEnabled() {
        return tlsEnabled;
    }

    public String getTlsCertPath() {
        return tlsCertPath;
    }

    public String getTlsKeyPath() {
        return tlsKeyPath;
    }

    public int getTlsPort() {
        return tlsPort;
    }

    public String getReplicaOfHost() {
        return replicaOfHost;
    }

    public int getReplicaOfPort() {
        return replicaOfPort;
    }

    public long getReplBacklogSize() {
        return replBacklogSize;
    }

    public boolean isClusterEnabled() {
        return clusterEnabled;
    }

    // ── Setters (for future CLI arg parsing) ──

    public void setPort(int port) {
        this.port = port;
    }

    public void setMaxClients(int maxClients) {
        this.maxClients = maxClients;
    }

    public void setDataDir(String dataDir) {
        this.dataDir = dataDir;
    }

    public void setMaxMemoryBytes(long maxMemoryBytes) {
        this.maxMemoryBytes = maxMemoryBytes;
    }

    public void setEvictionPolicy(String evictionPolicy) {
        this.evictionPolicy = evictionPolicy;
    }

    public void setMaxMemorySamples(int maxMemorySamples) {
        this.maxMemorySamples = maxMemorySamples;
    }

    public void setNioEnabled(boolean nioEnabled) {
        this.nioEnabled = nioEnabled;
    }

    public void setIdleTimeoutSeconds(int idleTimeoutSeconds) {
        this.idleTimeoutSeconds = idleTimeoutSeconds;
    }

    public void setTcpBacklog(int tcpBacklog) {
        this.tcpBacklog = tcpBacklog;
    }

    public void setTlsEnabled(boolean tlsEnabled) {
        this.tlsEnabled = tlsEnabled;
    }

    public void setTlsCertPath(String tlsCertPath) {
        this.tlsCertPath = tlsCertPath;
    }

    public void setTlsKeyPath(String tlsKeyPath) {
        this.tlsKeyPath = tlsKeyPath;
    }

    public void setTlsPort(int tlsPort) {
        this.tlsPort = tlsPort;
    }

    public void setClusterEnabled(boolean clusterEnabled) {
        this.clusterEnabled = clusterEnabled;
    }

    public void setReplicaOfHost(String replicaOfHost) {
        this.replicaOfHost = replicaOfHost;
    }

    public void setReplicaOfPort(int replicaOfPort) {
        this.replicaOfPort = replicaOfPort;
    }

    public void setReplBacklogSize(long replBacklogSize) {
        this.replBacklogSize = replBacklogSize;
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

    public long getAofRewriteMinSize() {
        return aofRewriteMinSize;
    }

    public void setAofRewriteMinSize(long aofRewriteMinSize) {
        this.aofRewriteMinSize = aofRewriteMinSize;
    }

    public int getAofRewriteGrowthPercent() {
        return aofRewriteGrowthPercent;
    }

    public void setAofRewriteGrowthPercent(int aofRewriteGrowthPercent) {
        this.aofRewriteGrowthPercent = aofRewriteGrowthPercent;
    }

    public int getDashboardPort() {
        return dashboardPort;
    }

    public void setDashboardPort(int dashboardPort) {
        this.dashboardPort = dashboardPort;
    }

    public boolean isDashboardEnabled() {
        return dashboardEnabled;
    }

    public void setDashboardEnabled(boolean dashboardEnabled) {
        this.dashboardEnabled = dashboardEnabled;
    }
}
