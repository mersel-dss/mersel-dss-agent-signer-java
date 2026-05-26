package tr.com.cs.imz.websocket.model;

public class SorumluKisi {
    private String ad;
    private String soyad;
    private String eposta;
    private String telefon;

    public SorumluKisi(String ad, String soyad, String eposta, String telefon) {
        this.ad = ad;
        this.soyad = soyad;
        this.eposta = eposta;
        this.telefon = telefon;
    }

    public String getAd() {
        return this.ad;
    }

    public void setAd(String ad) {
        this.ad = ad;
    }

    public String getSoyad() {
        return this.soyad;
    }

    public void setSoyad(String soyad) {
        this.soyad = soyad;
    }

    public String getEposta() {
        return this.eposta;
    }

    public void setEposta(String eposta) {
        this.eposta = eposta;
    }

    public String getTelefon() {
        return this.telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
    }
}
