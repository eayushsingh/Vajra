package com.railways.anomaly.model;

import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Evaluated track status response — returned by the GET /api/track-status
 * endpoint and consumed by the HTML dashboard's polling loop.
 *
 * Carries the latest telemetry values, computed risk classification,
 * and the prescribed operational action command.
 */
public class TrackStatusResponse {

    // ── Structural Risk Levels ────────────────────────────────────────────────

    public enum RiskLevel {
        HEALTHY,   // Stress < 90 MPa  — normal operations
        WARNING,   // Stress 90–120 MPa — schedule manual inspection
        CRITICAL   // Stress > 120 MPa OR Temp > 50°C — emergency brake command
    }

    // ── Fields ────────────────────────────────────────────────────────────────

    @JsonProperty("sectionId")
    private String sectionId;

    @JsonProperty("kmMarker")
    private Integer kmMarker;

    @JsonProperty("railTemperature")
    private Double railTemperature;

    @JsonProperty("stressMPa")
    private Double stressMPa;

    @JsonProperty("riskLevel")
    private RiskLevel riskLevel;

    /** Human-readable prescribed action for the operations center. */
    @JsonProperty("action")
    private String action;

    /** Short technical label shown on the dashboard badge. */
    @JsonProperty("statusLabel")
    private String statusLabel;

    /** ISO-8601 UTC timestamp of the most recent telemetry packet. */
    @JsonProperty("lastUpdated")
    private String lastUpdated;

    /** ISO-8601 UTC timestamp when this evaluation was computed. */
    @JsonProperty("evaluatedAt")
    private String evaluatedAt;

    /** Total packets processed since server start. */
    @JsonProperty("totalPacketsProcessed")
    private long totalPacketsProcessed;

    /** Total anomaly events detected since server start. */
    @JsonProperty("totalAnomaliesDetected")
    private long totalAnomaliesDetected;

    /** Anomaly mode if injected (e.g. "THERMAL_BUCKLING") */
    @JsonProperty("anomalyMode")
    private String anomalyMode;

    /** Whether the most recent packet was an injected anomaly */
    @JsonProperty("anomalyInjected")
    private boolean anomalyInjected;

    // ── Constructor ───────────────────────────────────────────────────────────

    public TrackStatusResponse() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String    getSectionId()                     { return sectionId; }
    public void      setSectionId(String v)             { this.sectionId = v; }

    public Integer   getKmMarker()                      { return kmMarker; }
    public void      setKmMarker(Integer v)             { this.kmMarker = v; }

    public Double    getRailTemperature()               { return railTemperature; }
    public void      setRailTemperature(Double v)       { this.railTemperature = v; }

    public Double    getStressMPa()                     { return stressMPa; }
    public void      setStressMPa(Double v)             { this.stressMPa = v; }

    public RiskLevel getRiskLevel()                     { return riskLevel; }
    public void      setRiskLevel(RiskLevel v)          { this.riskLevel = v; }

    public String    getAction()                        { return action; }
    public void      setAction(String v)                { this.action = v; }

    public String    getStatusLabel()                   { return statusLabel; }
    public void      setStatusLabel(String v)           { this.statusLabel = v; }

    public String    getLastUpdated()                   { return lastUpdated; }
    public void      setLastUpdated(String v)           { this.lastUpdated = v; }

    public String    getEvaluatedAt()                   { return evaluatedAt; }
    public void      setEvaluatedAt(String v)           { this.evaluatedAt = v; }

    public long      getTotalPacketsProcessed()         { return totalPacketsProcessed; }
    public void      setTotalPacketsProcessed(long v)   { this.totalPacketsProcessed = v; }

    public long      getTotalAnomaliesDetected()        { return totalAnomaliesDetected; }
    public void      setTotalAnomaliesDetected(long v)  { this.totalAnomaliesDetected = v; }

    public String    getAnomalyMode()                   { return anomalyMode; }
    public void      setAnomalyMode(String v)           { this.anomalyMode = v; }

    public boolean   isAnomalyInjected()                { return anomalyInjected; }
    public void      setAnomalyInjected(boolean v)      { this.anomalyInjected = v; }
}
