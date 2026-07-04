package ro.cheilebranistei.backend;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

@SpringBootApplication
public class BackendApplication {
    public static void main(String[] args) {
        // Utilitar local: genereaza un hash bcrypt fara sa porneasca Spring/DB.
        // Rulare: ./mvnw spring-boot:run -Dspring-boot.run.arguments="--hash=parolaTa"
        if (args.length > 0 && args[0].startsWith("--hash=")) {
            System.out.println(new BCryptPasswordEncoder().encode(args[0].substring("--hash=".length())));
            return;
        }
        SpringApplication.run(BackendApplication.class, args);
    }
}