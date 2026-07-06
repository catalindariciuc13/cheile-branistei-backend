package ro.cheilebranistei.backend.model;

import jakarta.persistence.*;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

@Entity
@Table(name = "rezervari")
public class Rezervare {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nume;

    @Column(nullable = false)
    private String telefon;

    private String email;

    @Column(name = "data_checkin", nullable = false)
    private LocalDate dataCheckin;

    @Column(name = "data_checkout", nullable = false)
    private LocalDate dataCheckout;

    @Column(name = "nr_persoane", nullable = false)
    private Integer nrPersoane;

    @Column(columnDefinition = "TEXT")
    private String mesaj;

    @Column(name = "motiv_anulare", length = 500)
    private String motivAnulare;

    @Enumerated(EnumType.STRING)
    private Status status = Status.PENDING;

    @Column(name = "data_creare")
    private LocalDateTime dataCreare = LocalDateTime.now(ZoneId.of("Europe/Bucharest"));

    public enum Status {
        PENDING, CONFIRMATA, ANULATA, BLOCAT
    }

    // Getters si Setters
    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public String getNume() { return nume; }
    public void setNume(String nume) { this.nume = nume; }

    public String getTelefon() { return telefon; }
    public void setTelefon(String telefon) { this.telefon = telefon; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public LocalDate getDataCheckin() { return dataCheckin; }
    public void setDataCheckin(LocalDate dataCheckin) { this.dataCheckin = dataCheckin; }

    public LocalDate getDataCheckout() { return dataCheckout; }
    public void setDataCheckout(LocalDate dataCheckout) { this.dataCheckout = dataCheckout; }

    public Integer getNrPersoane() { return nrPersoane; }
    public void setNrPersoane(Integer nrPersoane) { this.nrPersoane = nrPersoane; }

    public String getMesaj() { return mesaj; }
    public void setMesaj(String mesaj) { this.mesaj = mesaj; }

    public String getMotivAnulare() { return motivAnulare; }
    public void setMotivAnulare(String motivAnulare) { this.motivAnulare = motivAnulare; }

    public Status getStatus() { return status; }
    public void setStatus(Status status) { this.status = status; }

    public LocalDateTime getDataCreare() { return dataCreare; }
    public void setDataCreare(LocalDateTime dataCreare) { this.dataCreare = dataCreare; }
}