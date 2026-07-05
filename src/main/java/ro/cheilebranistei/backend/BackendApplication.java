package ro.cheilebranistei.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
@EnableScheduling
public class BackendApplication {
    public static void main(String[] args) {
        // Utilitar local: genereaza un hash bcrypt fara sa porneasca Spring/DB.
        // Rulare: ./mvnw spring-boot:run -Dspring-boot.run.arguments="--hash=parolaTa"
        if (args.length > 0 && args[0].startsWith("--hash=")) {
            System.out.println(new BCryptPasswordEncoder().encode(args[0].substring("--hash=".length())));
            return;
        }
        // Utilitar local: genereaza perechea de chei VAPID pentru Web Push.
        // Rulare: ./mvnw spring-boot:run -Dspring-boot.run.arguments="--vapid"
        if (args.length > 0 && args[0].equals("--vapid")) {
            try {
                java.security.KeyPairGenerator g = java.security.KeyPairGenerator.getInstance("EC");
                g.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
                java.security.KeyPair kp = g.generateKeyPair();
                java.security.interfaces.ECPublicKey pub = (java.security.interfaces.ECPublicKey) kp.getPublic();
                java.security.interfaces.ECPrivateKey priv = (java.security.interfaces.ECPrivateKey) kp.getPrivate();
                byte[] x = la32(pub.getW().getAffineX().toByteArray());
                byte[] y = la32(pub.getW().getAffineY().toByteArray());
                byte[] punct = new byte[65];
                punct[0] = 4;
                System.arraycopy(x, 0, punct, 1, 32);
                System.arraycopy(y, 0, punct, 33, 32);
                java.util.Base64.Encoder enc = java.util.Base64.getUrlEncoder().withoutPadding();
                System.out.println("VAPID_PUBLIC_KEY=" + enc.encodeToString(punct));
                System.out.println("VAPID_PRIVATE_KEY=" + enc.encodeToString(la32(priv.getS().toByteArray())));
            } catch (Exception e) {
                e.printStackTrace();
            }
            return;
        }
        SpringApplication.run(BackendApplication.class, args);
    }

    // Normalizeaza un BigInteger la exact 32 de octeti (taie zeroul de semn / completeaza la stanga)
    private static byte[] la32(byte[] b) {
        byte[] out = new byte[32];
        if (b.length >= 32) {
            System.arraycopy(b, b.length - 32, out, 0, 32);
        } else {
            System.arraycopy(b, 0, out, 32 - b.length, b.length);
        }
        return out;
    }
}