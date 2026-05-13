package ro.cheilebranistei.backend.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import ro.cheilebranistei.backend.model.Rezervare;
import ro.cheilebranistei.backend.repository.RezervareRepository;
import ro.cheilebranistei.backend.service.EmailService;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/rezervari")
@CrossOrigin(origins = "*")
public class RezervareController {

    private final RezervareRepository rezervareRepository;
    private final EmailService        emailService;

    public RezervareController(RezervareRepository rezervareRepository,
                               EmailService emailService) {
        this.rezervareRepository = rezervareRepository;
        this.emailService        = emailService;
    }

    // POST /api/rezervari — creeaza rezervare noua
    @PostMapping
    public ResponseEntity<?> creeaza(@RequestBody Rezervare rezervare) {
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