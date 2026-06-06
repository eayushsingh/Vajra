package com.railways.anomaly;

import com.railways.anomaly.model.TelemetryPacket;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.Random;

@SpringBootApplication
@EnableScheduling
public class AnomalyEngineApplication {
    public static void main(String[] args) {
        SpringApplication.run(AnomalyEngineApplication.class, args);
    }
}

@Component
class DataGenerator {
    private final RestTemplate rest = new RestTemplate();
    private final Random rng = new Random();
    private static final String[] SECTIONS = {
        "NDLS-AGRA-SEC1","NDLS-AGRA-SEC2","NDLS-AGRA-SEC3","NDLS-AGRA-SEC4"
    };

    @Scheduled(fixedDelay = 5000)
    public void generate() {
        double temp   = Math.round((28 + rng.nextDouble()*19 + rng.nextGaussian()*2)*10)/10.0;
        double stress = Math.round(((210_000*12e-6*(temp-27)) + rng.nextGaussian()*5)*10)/10.0;
        boolean anomaly = rng.nextDouble() < 0.08;
        String mode = anomaly ? (temp > 42 ? "THERMAL_BUCKLING" : "TENSILE_FRACTURE") : null;
        if (anomaly) stress = Math.round(stress*(1.8+rng.nextDouble())*10)/10.0;

        TelemetryPacket p = new TelemetryPacket();
        p.setSectionId(SECTIONS[rng.nextInt(SECTIONS.length)]);
        p.setKmMarker(372 + rng.nextInt(40));
        p.setRailTemperature(temp);
        p.setStressMPa(stress);
        p.setAnomalyInjected(anomaly);
        p.setAnomalyMode(mode);
        p.setTimestamp(Instant.now().toString());

        try {
            rest.postForEntity("http://localhost:8080/api/track-telemetry", p, String.class);
            System.out.println("[Vajra] T="+temp+"°C σ="+stress+" MPa anomaly="+anomaly);
        } catch (Exception e) {
            System.out.println("[Vajra] Error: "+e.getMessage());
        }
    }
}
