package com.railways.anomaly.model;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

/**
 * Represents a single telemetry packet streamed from a
 * track-mounted CWR sensor at a specific kilometer marker.
 *
 * Fields mirror the Python simulator's JSON payload structure.
 * Unknown JSON fields are silently ignored for forward compatibility.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TelemetryPacket {

    @NotBlank(message = "sectionId must not be blank")
    @JsonProperty("sectionId")
    private String sectionId;

    @NotNull(message = "kmMarker is required")
    @JsonProperty("kmMarker")
    private Integer kmMarker;

    @NotNull(message = "railTemperature is required")
    @JsonProperty("railTemperature")
    private Double railTemperature;   // °C

    @NotNull(message = "stressMPa is required")
    @JsonProperty("stressMPa")
    private Double stressMPa;         // MegaPascals

    @JsonProperty("anomalyInjected")
    private boolean anomalyInjected;

    @JsonProperty("anomalyMode")
    private String anomalyMode;       // e.g. "THERMAL_BUCKLING" or "TENSILE_FRACTURE"

    @JsonProperty("timestamp")
    private String timestamp;         // ISO-8601 UTC

    // ── Constructors ──────────────────────────────────────────────────────────

    public TelemetryPacket() {}

    // ── Getters & Setters ─────────────────────────────────────────────────────

    public String getSectionId()            { return sectionId; }
    public void   setSectionId(String v)    { this.sectionId = v; }

    public Integer getKmMarker()            { return kmMarker; }
    public void    setKmMarker(Integer v)   { this.kmMarker = v; }

    public Double  getRailTemperature()     { return railTemperature; }
    public void    setRailTemperature(Double v) { this.railTemperature = v; }

    public Double  getStressMPa()           { return stressMPa; }
    public void    setStressMPa(Double v)   { this.stressMPa = v; }

    public boolean isAnomalyInjected()      { return anomalyInjected; }
    public void    setAnomalyInjected(boolean v) { this.anomalyInjected = v; }

    public String  getAnomalyMode()         { return anomalyMode; }
    public void    setAnomalyMode(String v) { this.anomalyMode = v; }

    public String  getTimestamp()           { return timestamp; }
    public void    setTimestamp(String v)   { this.timestamp = v; }

    @Override
    public String toString() {
        return "TelemetryPacket{" +
               "sectionId='" + sectionId + '\'' +
               ", kmMarker=" + kmMarker +
               ", railTemperature=" + railTemperature +
               ", stressMPa=" + stressMPa +
               ", anomalyInjected=" + anomalyInjected +
               ", anomalyMode='" + anomalyMode + '\'' +
               ", timestamp='" + timestamp + '\'' +
               '}';
    }
}
