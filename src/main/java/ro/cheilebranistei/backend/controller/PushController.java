package ro.cheilebranistei.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cheilebranistei.backend.model.PushAbonament;
import ro.cheilebranistei.backend.repository.PushAbonamentRepository;
import ro.cheilebranistei.backend.service.PushService;

import java.util.Map;

/**
 * Abonarea admin-ului la notificari push. Toate rutele /api/push/** sunt
 * protejate de SecurityConfig (anyRequest().authenticated()).
 */
@RestController
@RequestMapping("/api/push")
public class PushController {

    private final PushService pushService;
    private final PushAbonamentRepository abonamente;

    public PushController(PushService pushService, PushAbonamentRepository abonamente) {
        this.pushService = pushService;
        this.abonamente = abonamente;
    }

    // GET /api/push/cheie-publica — cheia VAPID publica pentru subscribe
    @GetMapping("/cheie-publica")
    public ResponseEntity<?> cheiePublica() {
        if (!pushService.esteConfigurat()) {
            return ResponseEntity.status(503)
                .body(Map.of("eroare", "Notificările nu sunt configurate pe server."));
        }
        return ResponseEntity.ok(Map.of("cheie", pushService.getCheiePublica()));
    }

    // POST /api/push/aboneaza — salveaza abonamentul browserului
    @PostMapping("/aboneaza")
    public ResponseEntity<?> aboneaza(@RequestBody Map<String, Object> body) {
        String endpoint = (String) body.get("endpoint");
        @SuppressWarnings("unchecked")
        Map<String, String> keys = (Map<String, String>) body.get("keys");

        if (endpoint == null || endpoint.isBlank()
                || keys == null || keys.get("p256dh") == null || keys.get("auth") == null) {
            return ResponseEntity.badRequest().body(Map.of("eroare", "Abonament invalid."));
        }

        PushAbonament abonament = abonamente.findByEndpoint(endpoint).orElseGet(PushAbonament::new);
        abonament.setEndpoint(endpoint);
        abonament.setP256dh(keys.get("p256dh"));
        abonament.setAuth(keys.get("auth"));
        abonamente.save(abonament);

        return ResponseEntity.ok(Map.of("ok", true));
    }
}
