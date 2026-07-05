package ro.cheilebranistei.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ro.cheilebranistei.backend.model.Rezervare;
import ro.cheilebranistei.backend.repository.RezervareRepository;
import ro.cheilebranistei.backend.service.EmailService;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayDeque;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@RestController
@RequestMapping("/api/rezervari")
public class RezervareController {

    // Rate limiting pe formularul public: max 10 cereri / ora / IP
    private static final int  MAX_CERERI_PE_ORA = 10;
    private static final long FEREASTRA_SECUNDE = 60 * 60;

    private final ConcurrentHashMap<String, ArrayDeque<Long>> cereriPerIp = new ConcurrentHashMap<>();

    private final RezervareRepository rezervareRepository;
    private final EmailService        emailService;

    public RezervareController(RezervareRepository rezervareRepository,
                               EmailService emailService) {
        this.rezervareRepository = rezervareRepository;
        this.emailService        = emailService;
    }

    private static boolean esteAdmin() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        return auth != null && auth.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
    }

    private boolean pesteLimita(String ip) {
        long acum = Instant.now().getEpochSecond();
        ArrayDeque<Long> cereri = cereriPerIp.computeIfAbsent(ip, k -> new ArrayDeque<>());
        synchronized (cereri) {
            while (!cereri.isEmpty() && cereri.peekFirst() < acum - FEREASTRA_SECUNDE) {
                cereri.pollFirst();
            }
            if (cereri.size() >= MAX_CERERI_PE_ORA) {
                return true;
            }
            cereri.addLast(acum);
            return false;
        }
    }

    // Returneaza mesajul de eroare sau null daca datele sunt valide
    private static String valideaza(Rezervare r, boolean admin) {
        String nume = r.getNume() == null ? "" : r.getNume().trim();
        if (nume.length() < 2 || nume.length() > 100) {
            return "Numele trebuie să aibă între 2 și 100 de caractere.";
        }
        r.setNume(nume);

        String telefon = r.getTelefon() == null ? "" : r.getTelefon().trim();
        String doarCifre = telefon.replaceAll("[^0-9]", "");
        if (!telefon.matches("[0-9+()\\-\\s.]{7,20}")
                || doarCifre.length() < 7 || doarCifre.length() > 15) {
            return "Numărul de telefon nu pare valid.";
        }
        r.setTelefon(telefon);

        String email = r.getEmail() == null ? "" : r.getEmail().trim();
        if (email.isEmpty()) {
            r.setEmail(null);
        } else if (email.length() > 150 || !email.matches("[^@\\s]+@[^@\\s]+\\.[^@\\s]+")) {
            return "Adresa de email nu pare validă.";
        } else {
            r.setEmail(email);
        }

        if (r.getDataCheckin() == null || r.getDataCheckout() == null) {
            return "Datele de check-in și check-out sunt obligatorii.";
        }
        if (r.getDataCheckin().isBefore(LocalDate.now().minusDays(1))) {
            return "Check-in-ul nu poate fi în trecut.";
        }
        if (!r.getDataCheckout().isAfter(r.getDataCheckin())) {
            return "Check-out-ul trebuie să fie după check-in.";
        }
        long nopti = ChronoUnit.DAYS.between(r.getDataCheckin(), r.getDataCheckout());
        if (!admin && nopti > 30) {
            return "Pentru sejururi mai lungi de 30 de nopți, contactează-ne direct.";
        }

        if (r.getNrPersoane() == null || r.getNrPersoane() < 1 || r.getNrPersoane() > 14) {
            return "Numărul de persoane trebuie să fie între 1 și 14.";
        }

        if (r.getMesaj() != null) {
            String mesaj = r.getMesaj().trim();
            if (mesaj.length() > 1000) {
                return "Mesajul este prea lung (maximum 1000 de caractere).";
            }
            r.setMesaj(mesaj.isEmpty() ? null : mesaj);
        }

        return null;
    }

    // POST /api/rezervari — creeaza rezervare noua
    @PostMapping
    public ResponseEntity<?> creeaza(@RequestBody Rezervare rezervare, HttpServletRequest request) {
        boolean admin = esteAdmin();

        if (!admin && pesteLimita(request.getRemoteAddr())) {
            return ResponseEntity.status(429)
                .body(Map.of("eroare", "Prea multe cereri. Te rugăm să încerci din nou mai târziu."));
        }

        String eroare = valideaza(rezervare, admin);
        if (eroare != null) {
            return ResponseEntity.badRequest().body(Map.of("eroare", eroare));
        }

        // Campurile pe care clientul nu are voie sa le controleze
        rezervare.setId(null);
        rezervare.setDataCreare(LocalDateTime.now());
        if (!admin) {
            rezervare.setStatus(Rezervare.Status.PENDING);
        }

        int camereNecesare = (int) Math.ceil(rezervare.getNrPersoane() / 2.0);
        int camereOcupate  = rezervareRepository.getCamereOcupate(
            rezervare.getDataCheckin(), rezervare.getDataCheckout()
        );

        if (camereOcupate + camereNecesare > 7) {
            int camereLibere = 7 - camereOcupate;
            String msg = camereLibere <= 0
                ? "Ne pare rău, pensiunea este complet rezervată în perioada selectată."
                : "Ne pare rău, nu avem suficiente camere. Putem caza maximum "
                  + (camereLibere * 2) + " persoane în această perioadă.";
            return ResponseEntity.badRequest().body(Map.of("eroare", msg));
        }

        // Salveaza rezervarea
        Rezervare salvata = rezervareRepository.save(rezervare);

        // Trimite emailuri
        System.out.println(">>> Rezervare salvata: " + salvata.getId());
        System.out.println(">>> Email turist: " + salvata.getEmail());
        new Thread(() -> {
            try {
                System.out.println(">>> Trimit email catre pensiune...");
                emailService.trimiteNotificarePensiune(salvata);
                System.out.println(">>> Email pensiune trimis OK!");
                emailService.trimiteConfirmareTurist(salvata, salvata.getEmail());
                System.out.println(">>> Email turist trimis OK!");
            } catch (Exception e) {
                System.out.println(">>> EROARE EMAIL: " + e.getMessage());
                e.printStackTrace();
            }
        }).start();

        return ResponseEntity.ok(salvata);
    }

    // GET /api/rezervari — toate rezervarile (pentru admin)
    @GetMapping
    public List<Rezervare> getAll() {
        return rezervareRepository.findAllByOrderByDataCreareDesc();
    }

    // GET /api/rezervari/disponibilitate — camere disponibile intr-o perioada
    @GetMapping("/disponibilitate")
    public ResponseEntity<?> getDisponibilitate(
            @RequestParam String checkin,
            @RequestParam String checkout) {
        try {
            LocalDate ci = LocalDate.parse(checkin);
            LocalDate co = LocalDate.parse(checkout);
            int ocupate  = rezervareRepository.getCamereOcupate(ci, co);
            int libere   = Math.max(0, 7 - ocupate);
            return ResponseEntity.ok(Map.of(
                "camereOcupate", ocupate,
                "camereLibere",  libere,
                "persoaneMax",   libere * 2,
                "disponibil",    libere > 0
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("eroare", "Date invalide."));
        }
    }

    // PUT /api/rezervari/{id}/status — schimba statusul
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestParam String status) {
        return rezervareRepository.findById(id).map(r -> {
            r.setStatus(Rezervare.Status.valueOf(status.toUpperCase()));
            Rezervare salvata = rezervareRepository.save(r);

            // Trimite email dupa schimbarea statusului
            new Thread(() -> {
                try {
                    if (salvata.getStatus() == Rezervare.Status.CONFIRMATA) {
                        System.out.println(">>> Trimit email confirmare catre: " + salvata.getEmail());
                        emailService.trimiteConfirmareAdmin(salvata, salvata.getEmail());
                        System.out.println(">>> Email confirmare trimis OK!");
                    } else if (salvata.getStatus() == Rezervare.Status.ANULATA) {
                        System.out.println(">>> Trimit email anulare catre: " + salvata.getEmail());
                        emailService.trimiteAnulareAdmin(salvata, salvata.getEmail());
                        System.out.println(">>> Email anulare trimis OK!");
                    }
                } catch (Exception e) {
                    System.out.println(">>> EROARE EMAIL STATUS: " + e.getMessage());
                    e.printStackTrace();
                }
            }).start();

            return ResponseEntity.ok(salvata);
        }).orElse(ResponseEntity.notFound().build());
    }

    // DELETE /api/rezervari/{id} — sterge rezervare
    @DeleteMapping("/{id}")
    public ResponseEntity<?> sterge(@PathVariable Long id) {
        return rezervareRepository.findById(id).map(r -> {
            rezervareRepository.delete(r);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}