package ro.cheilebranistei.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cheilebranistei.backend.model.CheckinOaspete;
import ro.cheilebranistei.backend.model.Rezervare;
import ro.cheilebranistei.backend.repository.CheckinOaspeteRepository;
import ro.cheilebranistei.backend.repository.RezervareRepository;
import ro.cheilebranistei.backend.service.CriptareService;

import java.security.SecureRandom;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayDeque;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/checkin")
public class CheckinController {

    private static final ZoneId RO = ZoneId.of("Europe/Bucharest");
    private static final SecureRandom RANDOM = new SecureRandom();

    // Rate limiting pe rutele publice: max 8 cereri / ora / IP
    private static final int  MAX_CERERI_PE_ORA = 8;
    private static final long FEREASTRA_SECUNDE = 60 * 60;
    private final ConcurrentHashMap<String, ArrayDeque<Long>> cereriPerIp = new ConcurrentHashMap<>();

    private boolean pesteLimita(String ip) {
        long acum = Instant.now().getEpochSecond();
        ArrayDeque<Long> cereri = cereriPerIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (cereri) {
            while (!cereri.isEmpty() && cereri.peekFirst() < acum - FEREASTRA_SECUNDE) {
                cereri.pollFirst();
            }
            if (cereri.size() >= MAX_CERERI_PE_ORA) return true;
            cereri.addLast(acum);
            return false;
        }
    }

    private final RezervareRepository       rezervareRepository;
    private final CheckinOaspeteRepository  checkinRepository;
    private final CriptareService           criptare;

    public CheckinController(RezervareRepository rezervareRepository,
                             CheckinOaspeteRepository checkinRepository,
                             CriptareService criptare) {
        this.rezervareRepository = rezervareRepository;
        this.checkinRepository   = checkinRepository;
        this.criptare            = criptare;
    }

    private static String genereazaToken() {
        byte[] b = new byte[24];
        RANDOM.nextBytes(b);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(b);
    }

    // ============================================================
    // ADMIN — genereaza (sau reutilizeaza) link-ul de check-in
    // pentru o rezervare, ca sa fie trimis manual (WhatsApp etc.)
    // ============================================================
    @PostMapping("/{rezervareId}/genereaza-link")
    public ResponseEntity<?> genereazaLink(@PathVariable Long rezervareId) {
        return rezervareRepository.findById(rezervareId).<ResponseEntity<?>>map(r -> {
            LocalDateTime acum = LocalDateTime.now(RO);
            boolean valid = r.getCheckinToken() != null
                && r.getCheckinTokenExpira() != null
                && r.getCheckinTokenExpira().isAfter(acum);

            if (!valid) {
                r.setCheckinToken(genereazaToken());
                // Valabil pana la 3 zile dupa check-out (acopera si intarzieri)
                r.setCheckinTokenExpira(r.getDataCheckout().atStartOfDay().plusDays(3));
                rezervareRepository.save(r);
            }
            return ResponseEntity.ok(Map.of("token", r.getCheckinToken()));
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============================================================
    // PUBLIC — validarea unui token (afiseaza contextul rezervarii)
    // ============================================================
    @GetMapping("/rezervare")
    public ResponseEntity<?> getRezervare(@RequestParam String token) {
        Rezervare r = gasesteValid(token);
        if (r == null) {
            return ResponseEntity.status(404).body(Map.of("eroare", "Link invalid sau expirat."));
        }
        return ResponseEntity.ok(Map.of(
            "nume", r.getNume(),
            "dataCheckin", r.getDataCheckin().toString(),
            "dataCheckout", r.getDataCheckout().toString(),
            "nrPersoane", r.getNrPersoane(),
            "telefon", r.getTelefon()
        ));
    }

    private Rezervare gasesteValid(String token) {
        if (token == null || token.isBlank()) return null;
        return rezervareRepository.findAll().stream()
            .filter(r -> token.equals(r.getCheckinToken()))
            .filter(r -> r.getCheckinTokenExpira() != null
                      && r.getCheckinTokenExpira().isAfter(LocalDateTime.now(RO)))
            .findFirst().orElse(null);
    }

    // ============================================================
    // PUBLIC — cautare rezervare dupa telefon (fluxul QR de la receptie)
    // ============================================================
    @PostMapping("/cauta")
    public ResponseEntity<?> cauta(@RequestBody Map<String, String> body, HttpServletRequest request) {
        if (pesteLimita(request.getRemoteAddr())) {
            return ResponseEntity.status(429).body(Map.of("eroare", "Prea multe încercări. Adresează-te recepției."));
        }
        String telefon = body.get("telefon");
        if (telefon == null || telefon.replaceAll("[^0-9]", "").length() < 7) {
            return ResponseEntity.badRequest().body(Map.of("eroare", "Introdu un număr de telefon valid."));
        }
        String cifre = telefon.replaceAll("[^0-9]", "");

        Rezervare gasita = rezervareRepository.findAll().stream()
            .filter(r -> r.getStatus() != Rezervare.Status.ANULATA)
            .filter(r -> r.getTelefon() != null && r.getTelefon().replaceAll("[^0-9]", "").endsWith(
                cifre.length() > 9 ? cifre.substring(cifre.length() - 9) : cifre))
            .filter(r -> !r.getDataCheckout().isBefore(LocalDateTime.now(RO).toLocalDate()))
            .findFirst().orElse(null);

        if (gasita == null) {
            return ResponseEntity.status(404).body(Map.of("eroare",
                "Nu am găsit nicio rezervare cu acest telefon. Te rugăm să te adresezi recepției."));
        }

        LocalDateTime acum = LocalDateTime.now(RO);
        boolean valid = gasita.getCheckinToken() != null
            && gasita.getCheckinTokenExpira() != null
            && gasita.getCheckinTokenExpira().isAfter(acum);
        if (!valid) {
            gasita.setCheckinToken(genereazaToken());
            gasita.setCheckinTokenExpira(gasita.getDataCheckout().atStartOfDay().plusDays(3));
            rezervareRepository.save(gasita);
        }
        return ResponseEntity.ok(Map.of("token", gasita.getCheckinToken()));
    }

    // ============================================================
    // PUBLIC — trimiterea fisei completate (una sau mai multe persoane)
    // ============================================================
    @PostMapping("/{token}")
    public ResponseEntity<?> trimite(@PathVariable String token, @RequestBody List<Map<String, String>> persoane,
                                     HttpServletRequest request) {
        if (pesteLimita(request.getRemoteAddr())) {
            return ResponseEntity.status(429).body(Map.of("eroare", "Prea multe încercări. Adresează-te recepției."));
        }
        if (!criptare.esteConfigurat()) {
            return ResponseEntity.status(503).body(Map.of("eroare", "Serviciul de check-in nu este configurat."));
        }
        Rezervare r = gasesteValid(token);
        if (r == null) {
            return ResponseEntity.status(404).body(Map.of("eroare", "Link invalid sau expirat."));
        }
        if (persoane == null || persoane.isEmpty() || persoane.size() > 20) {
            return ResponseEntity.badRequest().body(Map.of("eroare", "Date invalide."));
        }

        for (Map<String, String> p : persoane) {
            String nume = curata(p.get("numePrenume"), 150);
            String cnp  = curata(p.get("cnp"), 13);
            String ci   = curata(p.get("ci"), 20);
            String dom  = curata(p.get("domiciliu"), 300);

            if (nume.isEmpty() || cnp.length() != 13 || !cnp.matches("[0-9]{13}") || ci.isEmpty() || dom.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("eroare",
                    "Verifică datele — numele, CNP-ul (13 cifre), seria+numărul CI și domiciliul sunt obligatorii."));
            }

            CheckinOaspete o = new CheckinOaspete();
            o.setRezervareId(r.getId());
            o.setNumePrenume(nume);
            o.setCnpCriptat(criptare.cripteaza(cnp));
            o.setCiCriptat(criptare.cripteaza(ci));
            o.setDomiciliu(dom);
            o.setTelefon(r.getTelefon());
            checkinRepository.save(o);
        }

        // Link-ul e de unica folosinta odata completat
        r.setCheckinTokenExpira(LocalDateTime.now(RO).minusSeconds(1));
        rezervareRepository.save(r);

        return ResponseEntity.ok(Map.of("ok", true));
    }

    private static String curata(String s, int max) {
        if (s == null) return "";
        s = s.trim().replaceAll("\\s+", " ");
        return s.length() > max ? s.substring(0, max) : s;
    }

    // ============================================================
    // ADMIN — lista mascata a oaspetilor unei rezervari
    // ============================================================
    @GetMapping("/rezervare/{rezervareId}")
    public ResponseEntity<?> listaPentruRezervare(@PathVariable Long rezervareId) {
        List<Map<String, Object>> rezultat = checkinRepository.findByRezervareId(rezervareId).stream()
            .map(o -> Map.<String, Object>of(
                "id", o.getId(),
                "numePrenume", o.getNumePrenume(),
                "cnpMascat", CriptareService.masca(criptare.decripteaza(o.getCnpCriptat()), 4),
                "ciMascat", CriptareService.masca(criptare.decripteaza(o.getCiCriptat()), 4),
                "domiciliu", o.getDomiciliu()
            ))
            .toList();
        return ResponseEntity.ok(rezultat);
    }

    // ============================================================
    // ADMIN — sterge datele unui singur oaspete (drept la stergere GDPR)
    // ============================================================
    @DeleteMapping("/{id}")
    public ResponseEntity<?> sterge(@PathVariable Long id) {
        return checkinRepository.findById(id).<ResponseEntity<?>>map(o -> {
            checkinRepository.delete(o);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }

    // ============================================================
    // ADMIN — dezvaluie CNP + CI in clar pentru un singur oaspete
    // ============================================================
    @GetMapping("/{id}/dezvaluie")
    public ResponseEntity<?> dezvaluie(@PathVariable Long id) {
        return checkinRepository.findById(id).<ResponseEntity<?>>map(o -> {
            System.out.println(">>> CHECKIN: date dezvaluite pentru oaspete #" + id + " ("
                + LocalDateTime.now(RO) + ")");
            return ResponseEntity.ok(Map.of(
                "cnp", criptare.decripteaza(o.getCnpCriptat()),
                "ci", criptare.decripteaza(o.getCiCriptat())
            ));
        }).orElse(ResponseEntity.notFound().build());
    }
}
