package ro.cheilebranistei.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ro.cheilebranistei.backend.model.Rezervare;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

@Service
public class EmailService {

    @Value("${resend.api.key}")
    private String resendApiKey;

    private static final String PENSIUNE_EMAIL = "rezervari@cheilebranistei.ro";
    private static final String PENSIUNE_NUME  = "Cheile Branistei Mountain Retreat";
    private static final String NOTIFICARI_EMAIL = "cheilebranistei@gmail.com";

    private final HttpClient httpClient = HttpClient.newHttpClient();

    // ============================================================
    // Metoda generica de trimitere email via Resend API
    // ============================================================
    private void trimiteEmail(String catre, String subiect, String html) {
        try {
            String body = "{"
                + "\"from\":\"" + PENSIUNE_NUME + " <" + PENSIUNE_EMAIL + ">\","
                + "\"to\":[\"" + catre + "\"],"
                + "\"subject\":\"" + subiect + "\","
                + "\"html\":\"" + html.replace("\"", "\\\"").replace("\n", "").replace("\r", "") + "\""
                + "}";

            HttpRequest request = HttpRequest.newBuilder()
                .uri(URI.create("https://api.resend.com/emails"))
                .header("Authorization", "Bearer " + resendApiKey)
                .header("Content-Type", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

            HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
            System.out.println(">>> Resend response [" + catre + "]: " + response.statusCode() + " " + response.body());
        } catch (Exception e) {
            System.out.println(">>> EROARE Resend: " + e.getMessage());
            e.printStackTrace();
        }
    }

    // ============================================================
    // Email catre pensiune — rezervare noua
    // ============================================================
    public void trimiteNotificarePensiune(Rezervare r) {
        String mesaj = r.getMesaj() != null ? r.getMesaj() : "-";
        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Rezervare noua</h1>"
             + "</div>"
             + "<div style='background:#f9f9f9;padding:28px;border-radius:0 0 8px 8px;'>"
             +   "<table style='width:100%;border-collapse:collapse;'>"
             +     "<tr><td style='padding:8px 0;color:#666;width:140px;'>Nume</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getNume() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Telefon</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + r.getTelefon() + "</td></tr>"
             +     "<tr><td style='padding:8px 0;color:#666;'>Email</td>"
             +         "<td style='padding:8px 0;font-weight:bold;'>" + (r.getEmail() != null ? r.getEmail() : "-") + "</td></tr>"
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
             +     "<strong>Status:</strong> In asteptare - contacteaza clientul pentru confirmare!"
             +   "</div>"
             + "</div>"
             + "</div>";

        trimiteEmail(NOTIFICARI_EMAIL, "Rezervare noua - " + r.getNume(), html);
    }

    // ============================================================
    // Email catre turist — rezervare inregistrata
    // ============================================================
    public void trimiteConfirmareTurist(Rezervare r, String emailTurist) {
        if (emailTurist == null || emailTurist.isBlank()) return;

        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Cheile Branistei</h1>"
             +   "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;'>Mountain Retreat - Bucovina</p>"
             + "</div>"
             + "<div style='background:#f9f9f9;padding:28px;border-radius:0 0 8px 8px;'>"
             +   "<h2 style='color:#102a21;margin:0 0 16px;'>Buna, " + r.getNume() + "!</h2>"
             +   "<p style='color:#444;line-height:1.6;'>Cererea ta de rezervare a fost inregistrata cu succes! Te vom contacta in cel mai scurt timp pentru confirmare.</p>"
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
             +     "Cheile Branistei Mountain Retreat - Branistea, jud. Suceava"
             +   "</div>"
             + "</div>"
             + "</div>";

        trimiteEmail(emailTurist, "Rezervare inregistrata - Cheile Branistei", html);
    }

    // ============================================================
    // Email catre turist — confirmare din panoul admin
    // ============================================================
    public void trimiteConfirmareAdmin(Rezervare r, String emailTurist) {
        if (emailTurist == null || emailTurist.isBlank()) return;

        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Cheile Branistei</h1>"
             +   "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;'>Mountain Retreat - Bucovina</p>"
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

        trimiteEmail(emailTurist, "Rezervarea ta a fost confirmata - Cheile Branistei", html);
    }

    // ============================================================
    // Email catre turist — anulare din panoul admin
    // ============================================================
    public void trimiteAnulareAdmin(Rezervare r, String emailTurist) {
        if (emailTurist == null || emailTurist.isBlank()) return;

        String html = "<div style='font-family:Arial,sans-serif;max-width:600px;margin:0 auto;'>"
             + "<div style='background:#102a21;padding:24px;text-align:center;border-radius:8px 8px 0 0;'>"
             +   "<h1 style='color:#d6b36a;margin:0;font-size:22px;'>Cheile Branistei</h1>"
             +   "<p style='color:rgba(255,255,255,0.8);margin:8px 0 0;font-size:14px;'>Mountain Retreat - Bucovina</p>"
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

        trimiteEmail(emailTurist, "Rezervarea ta a fost anulata - Cheile Branistei", html);
    }
}