package ro.cheilebranistei.backend.service;

import nl.martijndwars.webpush.Notification;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import ro.cheilebranistei.backend.model.PushAbonament;
import ro.cheilebranistei.backend.repository.PushAbonamentRepository;

import java.nio.charset.StandardCharsets;
import java.security.Security;
import java.util.List;

/**
 * Trimite notificari Web Push catre telefoanele abonate (admin).
 * Cheile VAPID vin din variabilele de mediu VAPID_PUBLIC_KEY / VAPID_PRIVATE_KEY;
 * daca lipsesc, serviciul tace (aplicatia merge normal, doar fara push).
 */
@Service
public class PushService {

    static {
        if (Security.getProvider(BouncyCastleProvider.PROVIDER_NAME) == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    @Value("${vapid.public.key:}")
    private String vapidPublicKey;

    @Value("${vapid.private.key:}")
    private String vapidPrivateKey;

    private final PushAbonamentRepository abonamente;

    public PushService(PushAbonamentRepository abonamente) {
        this.abonamente = abonamente;
    }

    public boolean esteConfigurat() {
        return !vapidPublicKey.isBlank() && !vapidPrivateKey.isBlank();
    }

    public String getCheiePublica() {
        return vapidPublicKey;
    }

    private static String json(String s) {
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }

    public void trimiteTuturor(String titlu, String corp) {
        if (!esteConfigurat()) return;

        String payload = "{\"titlu\":\"" + json(titlu) + "\",\"corp\":\"" + json(corp) + "\"}";
        List<PushAbonament> lista = abonamente.findAll();
        System.out.println(">>> Push: trimit \"" + titlu + "\" catre " + lista.size() + " abonamente");

        for (PushAbonament a : lista) {
            try {
                nl.martijndwars.webpush.PushService svc =
                    new nl.martijndwars.webpush.PushService(
                        vapidPublicKey, vapidPrivateKey, "mailto:cheilebranistei@gmail.com");
                Notification notificare = new Notification(
                    a.getEndpoint(), a.getP256dh(), a.getAuth(),
                    payload.getBytes(StandardCharsets.UTF_8));
                var raspuns = svc.send(notificare);
                int cod = raspuns.getStatusLine().getStatusCode();
                System.out.println(">>> Push #" + a.getId() + ": HTTP " + cod);
                if (cod == 404 || cod == 410) {
                    // Abonament mort (aplicatie dezinstalata / permisiune retrasa)
                    abonamente.delete(a);
                    System.out.println(">>> Push #" + a.getId() + ": abonament expirat, sters");
                }
            } catch (Exception e) {
                System.out.println(">>> EROARE push #" + a.getId() + ": " + e.getMessage());
            }
        }
    }
}
