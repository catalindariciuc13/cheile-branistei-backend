package ro.cheilebranistei.backend.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Datele de identificare ale unui oaspete, colectate pentru registrul de
 * evidenta a turistilor (obligatoriu legal pentru unitatile de cazare).
 * CNP-ul si seria/numarul CI se pastreaza CRIPTATE (vezi CriptareService)
 * - in baza de date nu exista niciodata in clar.
 */
@Entity
@Table(name = "checkin_oaspeti")
public class CheckinOaspete {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // Nula daca oaspetele nu a putut fi asociat unei rezervari existente (fluxul QR)
    @Column(name = "rezervare_id")
    private Long rezervareId;

    @Column(name = "nume_prenume", nullable = false)
    private String numePrenume;

    // Criptate (AES/GCM) inainte de a ajunge aici - vezi CriptareService
    @Column(name = "cnp_criptat", length = 500)
    private String cnpCriptat;

    @Column(name = "ci_criptat", length = 500)
    private String ciCriptat;

    @Column(columnDefinition = "TEXT")
    private String domiciliu;

    private String telefon;

    @Column(name = "data_creare")
    private LocalDateTime dataCreare = LocalDateTime.now(ZoneId.of("Europe/Bucharest"));

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }

    public Long getRezervareId() { return rezervareId; }
    public void setRezervareId(Long rezervareId) { this.rezervareId = rezervareId; }

    public String getNumePrenume() { return numePrenume; }
    public void setNumePrenume(String numePrenume) { this.numePrenume = numePrenume; }

    public String getCnpCriptat() { return cnpCriptat; }
    public void setCnpCriptat(String cnpCriptat) { this.cnpCriptat = cnpCriptat; }

    public String getCiCriptat() { return ciCriptat; }
    public void setCiCriptat(String ciCriptat) { this.ciCriptat = ciCriptat; }

    public String getDomiciliu() { return domiciliu; }
    public void setDomiciliu(String domiciliu) { this.domiciliu = domiciliu; }

    public String getTelefon() { return telefon; }
    public void setTelefon(String telefon) { this.telefon = telefon; }

    public LocalDateTime getDataCreare() { return dataCreare; }
    public void setDataCreare(LocalDateTime dataCreare) { this.dataCreare = dataCreare; }
}
