package ro.cheilebranistei.backend.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import ro.cheilebranistei.backend.model.Rezervare;
import ro.cheilebranistei.backend.repository.CheckinOaspeteRepository;
import ro.cheilebranistei.backend.repository.RezervareRepository;
import ro.cheilebranistei.backend.service.EmailService;
import ro.cheilebranistei.backend.service.PushService;
import ro.cheilebranistei.backend.service.ReguliSezon;
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

    private final RezervareRepository        rezervareRepository;
    private final CheckinOaspeteRepository   checkinRepository;
    private final EmailService               emailService;
    private final PushService                pushService;

    public RezervareController(RezervareRepository rezervareRepository,
                               CheckinOaspeteRepository checkinRepository,
                               EmailService emailService,
                               PushService pushService) {
        this.rezervareRepository = rezervareRepository;
        this.checkinRepository   = checkinRepository;
        this.emailService        = emailService;
        this.pushService         = pushService;
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
        if (!admin) {
            String regula = ReguliSezon.verifica(r.getDataCheckin(), r.getDataCheckout());
            if (regula != null) return regula;
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
        rezervare.setDataCreare(LocalDateTime.now(java.time.ZoneId.of("Europe/Bucharest")));
        if (!admin) {
            rezervare.setStatus(Rezervare.Status.PENDING);
        }

        // Verificare noapte cu noapte: iarna o rezervare ocupă toată casa, altfel câte o cameră la 2 persoane
        List<Rezervare> active = rezervareRepository
            .findByStatusNotAndDataCheckinLessThanAndDataCheckoutGreaterThan(
                Rezervare.Status.ANULATA, rezervare.getDataCheckout(), rezervare.getDataCheckin());
        boolean lipsa = false, iarna = false;
        int camereLibere = ReguliSezon.CAMERE;          // minimul de camere libere în nopțile din afara iernii
        for (LocalDate z = rezervare.getDataCheckin(); z.isBefore(rezervare.getDataCheckout()); z = z.plusDays(1)) {
            int libereNoapte = Math.max(0, ReguliSezon.CAMERE - ReguliSezon.ocupate(active, z));
            if (libereNoapte < ReguliSezon.camere(rezervare, z)) {
                lipsa = true;
                iarna |= ReguliSezon.esteIarna(z);
            }
            if (!ReguliSezon.esteIarna(z)) camereLibere = Math.min(camereLibere, libereNoapte);
        }
        if (lipsa) {
            String msg = iarna
                ? "Ne pare rău, casa nu este liberă integral în perioada selectată. Iarna se închiriază doar toată casa."
                : camereLibere <= 0
                    ? "Ne pare rău, pensiunea este complet rezervată în perioada selectată."
                    : "Ne pare rău, nu avem suficiente camere. Putem caza maximum "
                      + (camereLibere * 2) + " persoane în această perioadă.";
            return ResponseEntity.badRequest().body(Map.of("eroare", msg));
        }

        // Salveaza rezervarea
        Rezervare salvata = rezervareRepository.save(rezervare);

        // Trimite emailuri + notificare push catre admin
        System.out.println(">>> Rezervare salvata: " + salvata.getId());
        System.out.println(">>> Email turist: " + salvata.getEmail());
        final boolean creataDeAdmin = admin;
        new Thread(() -> {
            try {
                if (!creataDeAdmin) {
                    pushService.trimiteTuturor(
                        "Rezervare nouă — " + salvata.getNume(),
                        salvata.getDataCheckin() + " → " + salvata.getDataCheckout()
                            + " · " + salvata.getNrPersoane() + " persoane · " + salvata.getTelefon());
                }
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
            if (!co.isAfter(ci) || ChronoUnit.DAYS.between(ci, co) > 60) {
                return ResponseEntity.badRequest().body(Map.of("eroare", "Date invalide."));
            }
            List<Rezervare> active = rezervareRepository
                .findByStatusNotAndDataCheckinLessThanAndDataCheckoutGreaterThan(Rezervare.Status.ANULATA, co, ci);
            int libere = ReguliSezon.CAMERE;
            boolean casaIntreaga = false;
            for (LocalDate z = ci; z.isBefore(co); z = z.plusDays(1)) {
                libere = Math.min(libere, ReguliSezon.libere(active, z));
                casaIntreaga |= ReguliSezon.esteIarna(z);
            }
            String regula = ReguliSezon.verifica(ci, co);
            Map<String, Object> rasp = new java.util.LinkedHashMap<>();
            rasp.put("camereOcupate", ReguliSezon.CAMERE - libere);
            rasp.put("camereLibere",  libere);
            rasp.put("persoaneMax",   libere * 2);
            rasp.put("disponibil",    libere > 0 && regula == null);
            rasp.put("casaIntreaga",  casaIntreaga);
            if (regula != null) rasp.put("regula", regula);
            return ResponseEntity.ok(rasp);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("eroare", "Date invalide."));
        }
    }

    // GET /api/rezervari/calendar — camere libere pe fiecare zi (public, fara date personale)
    @GetMapping("/calendar")
    public ResponseEntity<?> getCalendar(
            @RequestParam(required = false) String start,
            @RequestParam(defaultValue = "120") int zile) {
        LocalDate inceput;
        try {
            inceput = (start == null || start.isBlank()) ? LocalDate.now() : LocalDate.parse(start);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("eroare", "Data de start invalidă."));
        }
        zile = Math.min(Math.max(zile, 1), 190);
        LocalDate sfarsit = inceput.plusDays(zile);

        List<Rezervare> active = rezervareRepository
            .findByStatusNotAndDataCheckinLessThanAndDataCheckoutGreaterThan(
                Rezervare.Status.ANULATA, sfarsit, inceput);

        // Iarna o zi e „liberă” (7) doar dacă e liberă toată casa; nopțile blocate (Revelion) apar ocupate
        Map<String, Integer> libere = new java.util.LinkedHashMap<>();
        for (LocalDate zi = inceput; zi.isBefore(sfarsit); zi = zi.plusDays(1)) {
            libere.put(zi.toString(), ReguliSezon.libere(active, zi));
        }

        return ResponseEntity.ok(Map.of(
            "start", inceput.toString(),
            "zile", zile,
            "camereTotal", ReguliSezon.CAMERE,
            "pretCamera", ReguliSezon.PRET_CAMERA,
            "libere", libere,
            "reguli", ReguliSezon.pentruSite()
        ));
    }

    // PUT /api/rezervari/{id}/status — schimba statusul
    @PutMapping("/{id}/status")
    public ResponseEntity<?> updateStatus(@PathVariable Long id,
                                          @RequestParam String status,
                                          @RequestParam(required = false) String motiv) {
        return rezervareRepository.findById(id).map(r -> {
            r.setStatus(Rezervare.Status.valueOf(status.toUpperCase()));
            if (r.getStatus() == Rezervare.Status.ANULATA) {
                String m = motiv == null ? "" : motiv.trim();
                if (m.length() > 500) m = m.substring(0, 500);
                r.setMotivAnulare(m.isEmpty() ? null : m);
            } else {
                // La reactivare/confirmare, motivul unei anulari vechi nu mai e relevant
                r.setMotivAnulare(null);
            }
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
            checkinRepository.deleteAll(checkinRepository.findByRezervareId(id));
            rezervareRepository.delete(r);
            return ResponseEntity.ok().build();
        }).orElse(ResponseEntity.notFound().build());
    }
}