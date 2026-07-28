package com.railways.anomaly.controller;

import com.railways.anomaly.model.TelemetryPacket;
import com.railways.anomaly.model.TrackStatusResponse;
import com.railways.anomaly.service.TrackEvaluationService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * ============================================================
 * COMPONENT B — REST Controller: Track Telemetry API
 * ============================================================
 *
 * Exposes two endpoints:
 *
 *  POST /api/track-telemetry
 *    — Ingests a raw telemetry packet from the Python sensor simulator.
 *    — Delegates to TrackEvaluationService for risk classification.
 *    — Returns the evaluated status synchronously.
 *
 *  GET /api/track-status
 *    — Polled by the HTML dashboard every 800 ms.
 *    — Returns the latest computed status from the in-memory cache.
 *    — Returns 204 No Content if no packets have been received yet.
 *
 * CORS is configured to allow the dashboard served from localhost:8080
 * (and any origin for prototype flexibility).
 */
@RestController
@RequestMapping("/api")
@CrossOrigin(origins = "*")   // Allow dashboard polling from any origin (prototype)
public class TrackTelemetryController {

    private final TrackEvaluationService evaluationService;

    public TrackTelemetryController(TrackEvaluationService evaluationService) {
        this.evaluationService = evaluationService;
    }

    // ─────────────────────────────────────────────────────────────────────────
    // POST /api/track-telemetry
    // Ingest a single telemetry packet from the virtual sensor simulator.
    // ─────────────────────────────────────────────────────────────────────────

    @PostMapping(
        value    = "/track-telemetry",
        consumes = MediaType.APPLICATION_JSON_VALUE,
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TrackStatusResponse> ingestTelemetry(
            @Valid @RequestBody TelemetryPacket packet) {

        TrackStatusResponse evaluated = evaluationService.evaluateAndStore(packet);

        // Return HTTP 200 for healthy/warning, 200 for critical too (client reads riskLevel)
        // In a production system you might also push via WebSocket here.
        return ResponseEntity.ok(evaluated);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/track-status
    // Lightweight polling endpoint consumed by the dashboard every 800 ms.
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping(
        value   = "/track-status",
        produces = MediaType.APPLICATION_JSON_VALUE
    )
    public ResponseEntity<TrackStatusResponse> getTrackStatus() {

        TrackStatusResponse status = evaluationService.getPrimaryStatus();

        if (status == null) {
            // No packets received yet — dashboard should show "Awaiting Data"
            return ResponseEntity.noContent().build();  // HTTP 204
        }

        return ResponseEntity.ok(status);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // GET /api/health
    // Simple liveness probe — confirms the engine is running.
    // ─────────────────────────────────────────────────────────────────────────

    @GetMapping("/health")
    public ResponseEntity<Map<String, Object>> healthCheck() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("status",                "UP");
        body.put("service",               "CWR Anomaly Detection Engine");
        body.put("totalPacketsProcessed", evaluationService.getTotalPacketsProcessed());
        body.put("totalAnomaliesDetected",evaluationService.getTotalAnomaliesDetected());
        body.put("serverTime",            Instant.now().toString());
        return ResponseEntity.ok(body);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // Exception handler — returns clean JSON on validation errors
    // ─────────────────────────────────────────────────────────────────────────

    @ExceptionHandler(org.springframework.web.bind.MethodArgumentNotValidException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public Map<String, Object> handleValidationErrors(
            org.springframework.web.bind.MethodArgumentNotValidException EX) {

        Map<String, Object> errors = new LinkedHashMap<>();
        errors.put("error",     "BAD REQUEST");
        errors.put("timestamp", Instant.now().toString());
        ex.getBindingResult().getFieldErrors().forEach(fe ->
            errors.put(fe.getField(), fe.getDefaultMessage())
        );
        return errors;
    }
}
