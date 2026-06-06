package com.railways.anomaly;

import com.railways.anomaly.model.TelemetryPacket;
import com.railways.anomaly.service.TrackEvaluationService;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

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
    private final TrackEvaluationService trackEvaluationService;
    private final Random rng = new Random();
    private static final String[] SECTIONS = {
        "NDLS-AGRA-SEC1","NDLS-AGRA-SEC2","NDLS-AGRA-SEC3","NDLS-AGRA-SEC4"
    };

    public DataGenerator(TrackEvaluationService trackEvaluationService) {
        this.trackEvaluationService = trackEvaluationService;
    }

    @Scheduled(fixedDelay = 3000)  // every 3 seconds
    public void generate() {
        double temp = Math.round((28 + rng.nextDouble() * 19) * 10) / 10.0;
        double stress = Math.round(((210_000 * 12e-6 * (temp - 27)) + rng.nextGaussian() * 5) * 10) / 10.0;
        
        // 25% chance of anomaly so dashboard shows red frequently
        boolean anomaly = rng.nextDouble() < 0.25;
        String mode = null;
        if (anomaly) {
            mode = temp > 42 ? "THERMAL_BUCKLING" : "TENSILE_FRACTURE";
            stress = Math.round(stress * 2.5 * 10) / 10.0;
        }

        TelemetryPacket p = new TelemetryPacket();
        p.setSectionId(SECTIONS[rng.nextInt(SECTIONS.length)]);
        p.setKmMarker(372 + rng.nextInt(41)); // Between 372 and 412
        p.setRailTemperature(temp);
        p.setStressMPa(stress);
        p.setAnomalyInjected(anomaly);
        p.setAnomalyMode(mode);
        p.setTimestamp(Instant.now().toString());

        try {
            trackEvaluationService.evaluateAndStore(p);
            System.out.println("[Vajra] T=" + temp + "°C σ=" + stress + " MPa anomaly=" + anomaly);
        } catch (Exception e) {
            System.out.println("[Vajra] Error: " + e.getMessage());
        }
    }
}
