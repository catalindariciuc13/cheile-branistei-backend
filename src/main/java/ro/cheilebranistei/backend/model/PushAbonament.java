package ro.cheilebranistei.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "push_abonamente")
public class PushAbonament {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String endpoint;

    @Column(nullable = false, length = 255)
    private String p256dh;

    @Column(nullable = false, length = 255)
    private String auth;

    @Column(name = "data_creare")
    private LocalDateTime dataCreare = LocalDateTime.now(ZoneId.of("Europe/Bucharest"));

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }

    public String getP256dh() { return p256dh; }
    public void setP256dh(String p256dh) { this.p256dh = p256dh; }

    public String getAuth() { return auth; }
    public void setAuth(String auth) { this.auth = auth; }

    public LocalDateTime getDataCreare() { return dataCreare; }
    public void setDataCreare(LocalDateTime dataCreare) { this.dataCreare = dataCreare; }
}
