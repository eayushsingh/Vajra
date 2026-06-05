package com.railways.anomaly.service;

import com.railways.anomaly.model.TelemetryPacket;
import com.railways.anomaly.model.TrackStatusResponse;
import com.railways.anomaly.model.TrackStatusResponse.RiskLevel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * ============================================================
 * COMPONENT B — Core: Track Anomaly Evaluation Service
 * ============================================================
 *
 * Stateless evaluation logic wrapped in a Spring-managed singleton.
 * Uses a thread-safe ConcurrentHashMap as an in-memory state cache
 * (no database required), updated atomically on every ingest call.
 *
 * Stress thresholds derived from:
 *   — IS:3443 (Indian Standard for Rails)
 *   — UIC 60 rail specifications
 *   — RDSO Guidelines for CWR track management (2022)
 *
 * Risk Classification:
 *   HEALTHY  : stressMPa < 90
 *   WARNING  : 90 ≤ stressMPa ≤ 120  → Schedule Manual Inspection
 *   CRITICAL : stressMPa > 120  OR  railTemperature > 50°C
 *              → Trigger Emergency Brake Command
 */
@Service
public class TrackEvaluationService {

    private static final Logger log = LoggerFactory.getLogger(TrackEvaluationService.class);

    // ── Structural Thresholds (MPa) ───────────────────────────────────────────

    private static final double STRESS_WARNING_THRESHOLD  = 90.0;   // MPa
    private static final double STRESS_CRITICAL_THRESHOLD = 120.0;  // MPa
    private static final double TEMP_CRITICAL_THRESHOLD   = 50.0;   // °C

    // ── Prescribed Action Messages ────────────────────────────────────────────

    private static final String ACTION_HEALTHY =
            "All parameters within safe operating bounds. Normal operations continue.";

    private static final String ACTION_WARNING =
            "Elevated stress detected. Schedule ultrasonic flaw inspection within 4 hours. " +
            "Reduce train speed on section to 60 km/h.";

    private static final String ACTION_CRITICAL =
            "⚠ CRITICAL: Structural failure threshold breached. " +
            "Trigger Emergency Brake Command. Halt all trains on NDLS-AGRA-SEC1. " +
            "Dispatch Permanent Way (P-Way) gang immediately to KM 412.";

    // ── In-Memory State Cache ─────────────────────────────────────────────────

    /**
     * Key   : sectionId (e.g. "NDLS-AGRA-SEC1")
     * Value : Latest evaluated TrackStatusResponse for that section.
     *
     * ConcurrentHashMap guarantees thread-safe reads/writes across
     * multiple concurrent HTTP request threads without explicit locking.
     */
    private final ConcurrentHashMap<String, TrackStatusResponse> sectionStateCache
            = new ConcurrentHashMap<>();

    /** Monotonically increasing counter — safe across threads. */
    private final AtomicLong totalPacketsProcessed = new AtomicLong(0);
    private final AtomicLong totalAnomaliesDetected = new AtomicLong(0);

    // ── Public API ────────────────────────────────────────────────────────────

    /**
     * Evaluates an incoming telemetry packet, classifies its risk level,
     * and atomically updates the in-memory section state cache.
     *
     * @param packet Validated telemetry payload from the sensor simulator.
     * @return       Computed TrackStatusResponse with risk classification.
     */
    public TrackStatusResponse evaluateAndStore(TelemetryPacket packet) {

        long packetNumber = totalPacketsProcessed.incrementAndGet();

        // ── Risk Classification ───────────────────────────────────────────────
        RiskLevel risk;
        String    action;
        String    label;

        boolean temperatureCritical = packet.getRailTemperature() > TEMP_CRITICAL_THRESHOLD;
        boolean stressCritical      = packet.getStressMPa() > STRESS_CRITICAL_THRESHOLD;
        boolean stressWarning       = packet.getStressMPa() >= STRESS_WARNING_THRESHOLD;

        if (stressCritical || temperatureCritical) {
            risk   = RiskLevel.CRITICAL;
            action = ACTION_CRITICAL;
            label  = "CRITICAL FAILURE";
            totalAnomaliesDetected.incrementAndGet();

            log.error(
                "[CRITICAL] SectionId={} | KM={} | Temp={}°C | Stress={} MPa | Mode={}",
                packet.getSectionId(), packet.getKmMarker(),
                packet.getRailTemperature(), packet.getStressMPa(),
                packet.getAnomalyMode() != null ? packet.getAnomalyMode() : "THRESHOLD_BREACH"
            );

        } else if (stressWarning) {
            risk   = RiskLevel.WARNING;
            action = ACTION_WARNING;
            label  = "WARNING — INSPECT";

            log.warn(
                "[WARNING] SectionId={} | KM={} | Temp={}°C | Stress={} MPa",
                packet.getSectionId(), packet.getKmMarker(),
                packet.getRailTemperature(), packet.getStressMPa()
            );

        } else {
            risk   = RiskLevel.HEALTHY;
            action = ACTION_HEALTHY;
            label  = "HEALTHY";

            log.debug(
                "[HEALTHY] SectionId={} | KM={} | Temp={}°C | Stress={} MPa",
                packet.getSectionId(), packet.getKmMarker(),
                packet.getRailTemperature(), packet.getStressMPa()
            );
        }

        // ── Assemble Response Object ──────────────────────────────────────────
        TrackStatusResponse status = new TrackStatusResponse();
        status.setSectionId(packet.getSectionId());
        status.setKmMarker(packet.getKmMarker());
        status.setRailTemperature(packet.getRailTemperature());
        status.setStressMPa(packet.getStressMPa());
        status.setRiskLevel(risk);
        status.setAction(action);
        status.setStatusLabel(label);
        status.setLastUpdated(packet.getTimestamp());
        status.setEvaluatedAt(Instant.now().toString());
        status.setTotalPacketsProcessed(packetNumber);
        status.setTotalAnomaliesDetected(totalAnomaliesDetected.get());
        status.setAnomalyMode(packet.getAnomalyMode());
        status.setAnomalyInjected(packet.isAnomalyInjected());

        // ── Atomic cache update (thread-safe) ─────────────────────────────────
        sectionStateCache.put(packet.getSectionId(), status);

        return status;
    }

    /**
     * Retrieves the latest evaluated status for a specific track section.
     *
     * @param sectionId Section identifier (e.g. "NDLS-AGRA-SEC1")
     * @return          Latest status, or null if no data received yet.
     */
    public TrackStatusResponse getLatestStatus(String sectionId) {
        return sectionStateCache.get(sectionId);
    }

    /**
     * Returns the primary monitored section status (the first one in cache).
     * Used by the dashboard polling endpoint which doesn't need to specify a sectionId.
     */
    public TrackStatusResponse getPrimaryStatus() {
        if (sectionStateCache.isEmpty()) {
            return null;
        }
        // Return the most recently-inserted entry (or the only one for this prototype)
        return sectionStateCache.values().stream().findFirst().orElse(null);
    }

    public long getTotalPacketsProcessed() {
        return totalPacketsProcessed.get();
    }

    public long getTotalAnomaliesDetected() {
        return totalAnomaliesDetected.get();
    }
}
