package com.railways.anomaly.service;

import com.railways.anomaly.model.TelemetryPacket;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Random;

@Service
public class TelemetryGeneratorService {

    private final RestTemplate restTemplate = new RestTemplate();
    private final Random rng = new Random();

    private static final List<String> SENSOR_IDS = List.of(
        "TRK-MUM-DEL-001", "TRK-DEL-HWH-002",
        "TRK-CHN-MUM-003", "TRK-LKO-DEL-007"
    );

    private static final List<String> ROUTES = List.of(
        "Mumbai-Delhi", "Delhi-Howrah",
        "Chennai-Mumbai", "Lucknow-Delhi"
    );

    @Scheduled(fixedDelay = 5000)
    public void generate() {
        int i         = rng.nextInt(SENSOR_IDS.size());
        double temp   = Math.round((28 + rng.nextDouble() * 19 + rng.nextGaussian() * 2) * 10.0) / 10.0;
        double stress = Math.round(((210_000 * 12e-6 * (temp - 27)) + rng.nextGaussian() * 5 + (rng.nextDouble() < 0.05 ? rng.nextGaussian() * 20 : 0)) * 10.0) / 10.0;
        String status = Math.abs(stress) < 80 && temp < 42 ? "HEALTHY" : Math.abs(stress) < 140 && temp < 48 ? "WARNING" : "CRITICAL";

        TelemetryPacket packet = new TelemetryPacket();
        packet.setSensorId(SENSOR_IDS.get(i));
        packet.setRoute(ROUTES.get(i));
        packet.setTemp(temp);
        packet.setStress(stress);
        packet.setStatus(status);
        packet.setTimestamp(Instant.now().toString());

        try {
            restTemplate.postForEntity("http://localhost:8080/api/track-telemetry", packet, String.class);
        } catch (Exception e) {
            System.out.println("[Vajra Generator] " + status + " | T=" + temp + "°C | σ=" + stress + " MPa");
        }
    }
}
