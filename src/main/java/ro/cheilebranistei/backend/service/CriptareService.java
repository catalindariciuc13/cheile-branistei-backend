package ro.cheilebranistei.backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Base64;

/**
 * Cripteaza/decripteaza datele sensibile (CNP, serie+numar CI) cu AES-256-GCM.
 * Cheia vine din variabila de mediu CHECKIN_ENC_KEY (32 octeti, Base64) -
 * generata local cu utilitarul --enckey din BackendApplication.
 * Fara cheie configurata, serviciul refuza sa porneasca fluxul de check-in
 * (mai bine oprit decat sa scrie date sensibile necriptate).
 */
@Service
public class CriptareService {

    private static final int GCM_TAG_BITS = 128;
    private static final int GCM_IV_BYTES = 12;

    @Value("${checkin.enc.key:}")
    private String cheieBase64;

    private final SecureRandom random = new SecureRandom();

    public boolean esteConfigurat() {
        return cheieBase64 != null && !cheieBase64.isBlank();
    }

    private SecretKeySpec cheie() {
        byte[] raw = Base64.getDecoder().decode(cheieBase64);
        return new SecretKeySpec(raw, "AES");
    }

    public String cripteaza(String text) {
        if (text == null) return null;
        try {
            byte[] iv = new byte[GCM_IV_BYTES];
            random.nextBytes(iv);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, cheie(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            byte[] criptat = cipher.doFinal(text.getBytes(StandardCharsets.UTF_8));

            ByteBuffer buf = ByteBuffer.allocate(iv.length + criptat.length);
            buf.put(iv).put(criptat);
            return Base64.getEncoder().encodeToString(buf.array());
        } catch (Exception e) {
            throw new RuntimeException("Eroare la criptare: " + e.getMessage(), e);
        }
    }

    public String decripteaza(String base64) {
        if (base64 == null) return null;
        try {
            byte[] tot = Base64.getDecoder().decode(base64);
            byte[] iv = new byte[GCM_IV_BYTES];
            byte[] criptat = new byte[tot.length - GCM_IV_BYTES];
            System.arraycopy(tot, 0, iv, 0, GCM_IV_BYTES);
            System.arraycopy(tot, GCM_IV_BYTES, criptat, 0, criptat.length);

            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, cheie(), new GCMParameterSpec(GCM_TAG_BITS, iv));
            return new String(cipher.doFinal(criptat), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Eroare la decriptare: " + e.getMessage(), e);
        }
    }

    // Ultimele N caractere vizibile, restul mascat - pentru afisare in admin fara reveal
    public static String masca(String text, int vizibile) {
        if (text == null || text.isBlank()) return "";
        if (text.length() <= vizibile) return "•".repeat(text.length());
        return "•".repeat(text.length() - vizibile) + text.substring(text.length() - vizibile);
    }
}
