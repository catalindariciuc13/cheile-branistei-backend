package ro.cheilebranistei.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.cheilebranistei.backend.model.Rezervare;
import ro.cheilebranistei.backend.repository.RezervareRepository;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

/**
 * Trimite zilnic, la ora 10:00 (ora Romaniei), un email de cerere de recenzie
 * oaspetilor confirmati al caror check-out a fost in urma cu {recenzii.zile} zile.
 * Tintirea exacta pe zi garanteaza ca fiecare oaspete primeste emailul o singura data.
 */
@Component
public class RecenzieScheduler {

    private final RezervareRepository rezervareRepository;
    private final EmailService        emailService;

    @Value("${recenzii.zile:2}")
    private int zileDupaCheckout;

    public RecenzieScheduler(RezervareRepository rezervareRepository,
                             EmailService emailService) {
        this.rezervareRepository = rezervareRepository;
        this.emailService        = emailService;
    }

    @Scheduled(cron = "${recenzii.cron:0 0 10 * * *}", zone = "Europe/Bucharest")
    public void trimiteCereriRecenzie() {
        LocalDate tinta = LocalDate.now(ZoneId.of("Europe/Bucharest")).minusDays(zileDupaCheckout);
        List<Rezervare> plecati = rezervareRepository
            .findByStatusAndDataCheckout(Rezervare.Status.CONFIRMATA, tinta);

        System.out.println(">>> Recenzii: " + plecati.size()
            + " oaspeti cu check-out pe " + tinta);

        for (Rezervare r : plecati) {
            if (r.getEmail() == null || r.getEmail().isBlank()) continue;
            System.out.println(">>> Trimit cerere recenzie pentru rezervarea #" + r.getId());
            emailService.trimiteCerereRecenzie(r);
        }
    }
}
