package ro.cheilebranistei.backend.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ro.cheilebranistei.backend.model.Rezervare;
import ro.cheilebranistei.backend.repository.RezervareRepository;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Comparator;
import java.util.List;

/**
 * Rapoarte automate catre pensiune:
 *  - backup CSV cu toate rezervarile, duminica la 23:00
 *  - rezumatul saptamanii care incepe, luni la 08:00
 */
@Component
public class RapoarteScheduler {

    private static final ZoneId RO = ZoneId.of("Europe/Bucharest");

    private final RezervareRepository rezervareRepository;
    private final EmailService        emailService;

    public RapoarteScheduler(RezervareRepository rezervareRepository,
                             EmailService emailService) {
        this.rezervareRepository = rezervareRepository;
        this.emailService        = emailService;
    }

    // ============================================================
    // Backup CSV — duminica 23:00
    // ============================================================
    @Scheduled(cron = "${backup.cron:0 0 23 * * SUN}", zone = "Europe/Bucharest")
    public void trimiteBackup() {
        List<Rezervare> toate = rezervareRepository.findAllByOrderByDataCreareDesc();

        StringBuilder csv = new StringBuilder("﻿"); // BOM pentru diacritice in Excel
        csv.append("id,nume,telefon,email,checkin,checkout,persoane,status,mesaj,motiv_anulare,creata_la\n");
        for (Rezervare r : toate) {
            csv.append(r.getId()).append(',')
               .append(celula(r.getNume())).append(',')
               .append(celula(r.getTelefon())).append(',')
               .append(celula(r.getEmail())).append(',')
               .append(r.getDataCheckin()).append(',')
               .append(r.getDataCheckout()).append(',')
               .append(r.getNrPersoane()).append(',')
               .append(r.getStatus()).append(',')
               .append(celula(r.getMesaj())).append(',')
               .append(celula(r.getMotivAnulare())).append(',')
               .append(r.getDataCreare()).append('\n');
        }

        String numeFisier = "rezervari-" + LocalDate.now(RO) + ".csv";
        String base64 = Base64.getEncoder().encodeToString(csv.toString().getBytes(StandardCharsets.UTF_8));
        System.out.println(">>> Backup: trimit " + toate.size() + " rezervari (" + numeFisier + ")");
        emailService.trimiteBackupSaptamanal(numeFisier, base64, toate.size());
    }

    private static String celula(String s) {
        if (s == null) return "";
        return '"' + s.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + '"';
    }

    // ============================================================
    // Rezumatul saptamanii — luni 08:00
    // ============================================================
    @Scheduled(cron = "${rezumat.cron:0 0 8 * * MON}", zone = "Europe/Bucharest")
    public void trimiteRezumat() {
        LocalDate azi     = LocalDate.now(RO);
        LocalDate sfarsit = azi.plusDays(7);
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("dd.MM");

        List<Rezervare> active = rezervareRepository
            .findByStatusNotAndDataCheckinLessThanAndDataCheckoutGreaterThan(
                Rezervare.Status.ANULATA, sfarsit, azi);

        // Sosiri si plecari confirmate in urmatoarele 7 zile (fara blocari)
        List<Rezervare> sosiri = active.stream()
            .filter(r -> r.getStatus() == Rezervare.Status.CONFIRMATA)
            .filter(r -> !r.getDataCheckin().isBefore(azi) && r.getDataCheckin().isBefore(sfarsit))
            .sorted(Comparator.comparing(Rezervare::getDataCheckin))
            .toList();
        List<Rezervare> plecari = active.stream()
            .filter(r -> r.getStatus() == Rezervare.Status.CONFIRMATA)
            .filter(r -> !r.getDataCheckout().isBefore(azi) && r.getDataCheckout().isBefore(sfarsit))
            .sorted(Comparator.comparing(Rezervare::getDataCheckout))
            .toList();

        // Grad de ocupare pe urmatoarele 7 nopti (include blocarile)
        int camereNopti = 0;
        for (LocalDate zi = azi; zi.isBefore(sfarsit); zi = zi.plusDays(1)) {
            final LocalDate z = zi;
            camereNopti += active.stream()
                .filter(r -> !r.getDataCheckin().isAfter(z) && r.getDataCheckout().isAfter(z))
                .mapToInt(r -> (int) Math.ceil(r.getNrPersoane() / 2.0))
                .sum();
        }
        int ocupare = Math.min(100, Math.round(camereNopti * 100f / (7 * 7)));

        long inAsteptare = rezervareRepository.findAllByOrderByDataCreareDesc().stream()
            .filter(r -> r.getStatus() == Rezervare.Status.PENDING)
            .filter(r -> !r.getDataCheckout().isBefore(azi))
            .count();

        StringBuilder corp = new StringBuilder();
        corp.append("<p style='color:#444;line-height:1.6;'>Buna dimineata! Uite cum arata saptamana ")
            .append(azi.format(fmt)).append(" - ").append(sfarsit.minusDays(1).format(fmt)).append(":</p>");

        corp.append("<div style='background:white;border:1px solid #e0e0e0;border-radius:8px;padding:18px;margin:16px 0;'>");
        corp.append("<p style='margin:0 0 6px;color:#102a21;'><strong>Grad de ocupare: ").append(ocupare).append("%</strong></p>");
        corp.append("<p style='margin:0;color:#666;font-size:14px;'>").append(sosiri.size()).append(" sosiri · ")
            .append(plecari.size()).append(" plecari");
        if (inAsteptare > 0) {
            corp.append(" · <strong style='color:#b8860b;'>").append(inAsteptare)
                .append(" cereri in asteptare de confirmat!</strong>");
        }
        corp.append("</p></div>");

        if (!sosiri.isEmpty()) {
            corp.append("<h3 style='color:#102a21;font-size:15px;margin:18px 0 8px;'>Sosiri</h3>");
            for (Rezervare r : sosiri) {
                long nopti = ChronoUnit.DAYS.between(r.getDataCheckin(), r.getDataCheckout());
                corp.append("<p style='margin:4px 0;color:#444;'>&#8226; <strong>")
                    .append(r.getDataCheckin().format(fmt)).append("</strong> - ").append(r.getNume())
                    .append(" (").append(r.getNrPersoane()).append(" pers., ").append(nopti).append(" nopti)</p>");
            }
        }
        if (!plecari.isEmpty()) {
            corp.append("<h3 style='color:#102a21;font-size:15px;margin:18px 0 8px;'>Plecari</h3>");
            for (Rezervare r : plecari) {
                corp.append("<p style='margin:4px 0;color:#444;'>&#8226; <strong>")
                    .append(r.getDataCheckout().format(fmt)).append("</strong> - ").append(r.getNume()).append("</p>");
            }
        }
        if (sosiri.isEmpty() && plecari.isEmpty()) {
            corp.append("<p style='color:#444;line-height:1.6;'>O saptamana linistita - nicio sosire sau plecare programata.</p>");
        }

        System.out.println(">>> Rezumat saptamanal: " + sosiri.size() + " sosiri, "
            + plecari.size() + " plecari, ocupare " + ocupare + "%");
        emailService.trimiteRezumatSaptamanal(corp.toString());
    }
}
