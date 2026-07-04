package ro.cheilebranistei.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import ro.cheilebranistei.backend.security.JwtService;

import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    private static final int    MAX_INCERCARI    = 5;
    private static final long   BLOCARE_SECUNDE   = 15 * 60;

    private final PasswordEncoder passwordEncoder;
    private final JwtService      jwtService;
    private final String          adminPasswordHash;

    private final ConcurrentHashMap<String, Incercari> incercariPerIp = new ConcurrentHashMap<>();

    public AuthController(PasswordEncoder passwordEncoder,
                           JwtService jwtService,
                           @Value("${admin.password.hash}") String adminPasswordHash) {
        this.passwordEncoder  = passwordEncoder;
        this.jwtService       = jwtService;
        this.adminPasswordHash = adminPasswordHash;
    }

    @PostMapping("/login")
    public ResponseEntity<?> login(@RequestBody Map<String, String> body, HttpServletRequest request) {
        String ip = request.getRemoteAddr();
        Incercari incercari = incercariPerIp.computeIfAbsent(ip, k -> new Incercari());

        if (incercari.esteBlocat()) {
            return ResponseEntity.status(429).body(Map.of("eroare", "Prea multe încercări. Încearcă din nou mai târziu."));
        }

        String parola = body.get("password");
        if (parola != null && passwordEncoder.matches(parola, adminPasswordHash)) {
            incercariPerIp.remove(ip);
            return ResponseEntity.ok(Map.of("token", jwtService.generateToken()));
        }

        incercari.inregistreazaEsec();
        return ResponseEntity.status(401).body(Map.of("eroare", "Parolă incorectă."));
    }

    private static class Incercari {
        private int    numar;
        private long   blocatPanaLa;

        synchronized boolean esteBlocat() {
            return Instant.now().getEpochSecond() < blocatPanaLa;
        }

        synchronized void inregistreazaEsec() {
            numar++;
            if (numar >= MAX_INCERCARI) {
                blocatPanaLa = Instant.now().getEpochSecond() + BLOCARE_SECUNDE;
                numar = 0;
            }
        }
    }
}
