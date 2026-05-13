package ro.cheilebranistei.backend.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import ro.cheilebranistei.backend.model.Rezervare;
import jakarta.mail.internet.MimeMessage;

@Service
public class EmailService {

    private static final Logger log = LoggerFactory.getLogger(EmailService.class);

    private final JavaMailSender mailSender;
    private static final String PENSIUNE_EMAIL = "cheilebranistei@gmail.com";
    private static final String PENSIUNE_NUME  = "Cheile Branistei Mountain Retreat";

    public EmailService(JavaMailSender mailSender) {
        this.mailSender = mailSender;
    }

    // ============================================================
    // Email catre pensiune — rezervare noua
    // ============================================================
    @Async
    public void trimiteNotificarePensiune(Rezervare r) {
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(PENSIUNE_EMAIL);
            h.setSubject("Rezervare noua — " + r.getNume());
            h.setText(buildEmailPensiune(r), true);
            mailSender.send(msg);
            log.info("Email notificare pensiune trimis pentru rezervarea id={}", r.getId());
        } catch (Exception e) {
            log.error("Eroare trimitere email pensiune pentru rezervarea id={}: {}", r.getId(), e.getMessage(), e);
        }
    }

    // ============================================================
    // Email catre turist — confirmare rezervare noua
    // ============================================================
    @Async
    public void trimiteConfirmareTurist(Rezervare r, String emailTurist) {
        if (emailTurist == null || emailTurist.isBlank()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(emailTurist);
            h.setFrom(PENSIUNE_EMAIL, PENSIUNE_NUME);
            h.setSubject("Rezervare primita — Cheile Branistei");
            h.setText(buildEmailTurist(r), true);
            mailSender.send(msg);
            log.info("Email confirmare turist trimis la {} pentru rezervarea id={}", emailTurist, r.getId());
        } catch (Exception e) {
            log.error("Eroare trimitere email turist la {} pentru rezervarea id={}: {}", emailTurist, r.getId(), e.getMessage(), e);
        }
    }

    // ============================================================
    // Email catre turist — confirmare din panoul admin
    // ============================================================
    @Async
    public void trimiteConfirmareAdmin(Rezervare r, String emailTurist) {
        if (emailTurist == null || emailTurist.isBlank()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(emailTurist);
            h.setFrom(PENSIUNE_EMAIL, PENSIUNE_NUME);
            h.setSubject("Rezervarea ta a fost confirmata!");
            h.setText(buildEmailConfirmareAdmin(r), true);
            mailSender.send(msg);
            log.info("Email confirmare admin trimis la {} pentru rezervarea id={}", emailTurist, r.getId());
        } catch (Exception e) {
            log.error("Eroare email confirmare admin la {} pentru rezervarea id={}: {}", emailTurist, r.getId(), e.getMessage(), e);
        }
    }

    // ============================================================
    // Email catre turist — anulare din panoul admin
    // ============================================================
    @Async
    public void trimiteAnulareAdmin(Rezervare r, String emailTurist) {
        if (emailTurist == null || emailTurist.isBlank()) return;
        try {
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper h = new MimeMessageHelper(msg, true, "UTF-8");
            h.setTo(emailTurist);
            h.setFrom(PENSIUNE_EMAIL, PENSIUNE_NUME);
            h.setSubject("Rezervarea ta a fost anulata");
            h.setText(buildEmailAnulareAdmin(r), true);
            mailSender.send(msg);
            log.info("Email anulare admin trimis la {} pentru rezervarea id={}", emailTurist, r.getId());
        } catch (Exception e) {
            log.error("Eroare email anulare admin la {} pentru rezervarea id={}: {}", emailTurist, r.getId(), e.getMessage(), e);
        }
    }

    // ============================================================
    // BUILDERS
    // ============================================================

    private String buildEmailPensiune(Rezervare r) {
        String mesaj = r.getMesaj() != null ? r.getMesaj() : "-";
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Rezervare noua</h1>"
             + "</div>"
             + "<div style='background:#f9f9f9;padding:28px;border-radius:0 0 8px 8px;'>"
             +   "<table style='width:100%;border-collapse:collapse;'>"
             +     "<tr><td style='padding:8px 0;color:#666;width:140px;'>Nume</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getNume() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Telefon</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getTelefon() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Check-in</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getDataCheckin() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Check-out</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getDataCheckout() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Persoane</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getNrPersoane() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Mesaj</td>"
             +         "<td style='padding:8px 0;'>" + mesaj + "</td></tr>"
             +   "</table>"
             +   "<div style='margin-top:20px;padding:14px;background:#fff3cd;border-radius:6px;border-left:4px solid #d6b36a;'>"
             +     "<strong>Status:</strong> In asteptare — contacteaza clientul pentru confirmare!"
             +   "</div>"
             + "</div>"
             + "</div>";
    }

    private String buildEmailTurist(Rezervare r) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Cheile Branistei</h1>"
             +   "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;'>Mountain Retreat • Bucovina</p>"
             + "</div>"
             + "<div style='background:#f9f9f9;padding:28px;border-radius:0 0 8px 8px;'>"
             +   "<h2 style='color:#102a21;margin:0 0 16px;'>Buna, " + r.getNume() + "!</h2>"
             +   "<p style='color:#444;line-height:1.6;'>Am primit cererea ta de rezervare si te vom contacta in cel mai scurt timp pentru confirmare.</p>"
             +   "<div style='background:white;border:1px solid #e0e0e0;border-radius:8px;padding:20px;margin:20px 0;'>"
             +     "<h3 style='color:#102a21;margin:0 0 14px;font-size:16px;'>Detalii rezervare</h3>"
             +     "<table style='width:100%;border-collapse:collapse;'>"
             +       "<tr><td style='padding:6px 0;color:#666;width:120px;'>Check-in</td>"
             +           "<td style='padding:6px 0;font-weight:bold;'>" + r.getDataCheckin() + "</td></tr>"
             +       "<tr><td style='padding:6px 0;color:#666;'>Check-out</td>"
             +           "<td style='padding:6px 0;font-weight:bold;'>" + r.getDataCheckout() + "</td></tr>"
             +       "<tr><td style='padding:6px 0;color:#666;'>Persoane</td>"
             +           "<td style='padding:6px 0;font-weight:bold;'>" + r.getNrPersoane() + "</td></tr>"
             +     "</table>"
             +   "</div>"
             +   "<p style='color:#444;line-height:1.6;'>Pentru orice intrebare ne poti contacta la:</p>"
             +   "<p style='margin:4px 0;'><strong>Tel:</strong> 0733 623 000</p>"
             +   "<p style='margin:4px 0;'><strong>Email:</strong> cheilebranistei@gmail.com</p>"
             +   "<div style='margin-top:24px;padding-top:20px;border-top:1px solid #e0e0e0;text-align:center;color:#888;font-size:12px;'>"
             +     "Cheile Branistei Mountain Retreat • Branistea, jud. Suceava"
             +   "</div>"
             + "</div>"
             + "</div>";
    }

    private String buildEmailConfirmareAdmin(Rezervare r) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Cheile Branistei</h1>"
             +   "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;'>Mountain Retreat • Bucovina</p>"
             + "</div>"
             + "<div style='background:#f9f9f9;padding:28px;border-radius:0 0 8px 8px;'>"
             +   "<h2 style='color:#102a21;margin:0 0 16px;'>Rezervarea ta a fost confirmata!</h2>"
             +   "<p style='color:#444;line-height:1.6;'>Buna, <strong>" + r.getNume() + "</strong>!</p>"
             +   "<p style='color:#444;line-height:1.6;'>Suntem bucurosi sa te anuntam ca rezervarea ta a fost confirmata.</p>"
             +   "<div style='background:white;border:1px solid #e0e0e0;border-radius:8px;padding:20px;margin:20px 0;'>"
             +     "<table style='width:100%;border-collapse:collapse;'>"
             +       "<tr><td style='padding:6px 0;color:#666;width:120px;'>Check-in</td>"
             +           "<td style='padding:6px 0;font-weight:bold;'>" + r.getDataCheckin() + "</td></tr>"
             +       "<tr><td style='padding:6px 0;color:#666;'>Check-out</td>"
             +           "<td style='padding:6px 0;font-weight:bold;'>" + r.getDataCheckout() + "</td></tr>"
             +       "<tr><td style='padding:6px 0;color:#666;'>Persoane</td>"
             +           "<td style='padding:6px 0;font-weight:bold;'>" + r.getNrPersoane() + "</td></tr>"
             +     "</table>"
             +   "</div>"
             +   "<p style='color:#444;'>Ne vedem curand la Cheile Branistei!</p>"
             +   "<p style='margin:4px 0;'><strong>Tel:</strong> 0733 623 000</p>"
             +   "<p style='margin:4px 0;'><strong>Email:</strong> cheilebranistei@gmail.com</p>"
             + "</div>"
             + "</div>";
    }

    private String buildEmailAnulareAdmin(Rezervare r) {
        return "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Cheile Branistei</h1>"
             +   "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;'>Mountain Retreat • Bucovina</p>"
             + "</div>"
             + "<div style='background:#f9f9f9;padding:28px;border-radius:0 0 8px 8px;'>"
             +   "<h2 style='color:#102a21;margin:0 0 16px;'>Rezervarea ta a fost anulata</h2>"
             +   "<p style='color:#444;line-height:1.6;'>Buna, <strong>" + r.getNume() + "</strong>!</p>"
             +   "<p style='color:#444;line-height:1.6;'>Ne pare rau sa te informam ca rezervarea ta pentru perioada "
             +     "<strong>" + r.getDataCheckin() + " - " + r.getDataCheckout() + "</strong> a fost anulata.</p>"
             +   "<p style='color:#444;line-height:1.6;'>Pentru mai multe detalii sau o noua rezervare ne poti contacta la:</p>"
             +   "<p style='margin:4px 0;'><strong>Tel:</strong> 0733 623 000</p>"
             +   "<p style='margin:4px 0;'><strong>Email:</strong> cheilebranistei@gmail.com</p>"
             +   "<p style='margin:4px 0;'><strong>WhatsApp:</strong> 0733 623 000</p>"
             + "</div>"
             + "</div>";
    }
}