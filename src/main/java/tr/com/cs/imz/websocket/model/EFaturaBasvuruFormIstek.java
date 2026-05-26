package tr.com.cs.imz.websocket.model;

import javax.activation.DataHandler;
import org.json.JSONException;
import org.json.JSONObject;

public class EFaturaBasvuruFormIstek extends BasvuruTemel {
    private String ticaretSicilNo;
    private String ticaretSicilMemurlugu;
    private String kanuniMerkezi;
    private String kurulusTarihi;
    private String bagliBulunduguOda;
    private String odaSicilNo;
    private String webSitesi;
    private String ePosta;
    private String fax;
    private String adres;
    private String telNo;
    private String sorumluTckn;
    private String sorumluAd;
    private String sorumluSoyad;
    private String sorumluCepTel;
    private String sorumluEPosta;
    private int basvuruTipi;
    private String basvuruBelgeNo;
    private DataHandler form;
    private String dosyaIsmi;
    private int maliMuhurIstedi;

    public String toJSON() throws Exception {
        JSONObject obj = new JSONObject();
        try {
            obj.put("vkn", getVkn());
            obj.put("unvan", getUnvan());
            obj.put("tckn", getTckn());
            obj.put("ad", getAd());
            obj.put("soyad", getSoyad());
            obj.put("ticaretSicilNo", this.ticaretSicilNo);
            obj.put("ticaretSicilMemurlugu", this.ticaretSicilMemurlugu);
            obj.put("kanuniMerkezi", this.kanuniMerkezi);
            obj.put("kurulusTarihi", this.kurulusTarihi);
            obj.put("bagliBulunduguOda", this.bagliBulunduguOda);
            obj.put("odaSicilNo", this.odaSicilNo);
            obj.put("wadres", this.webSitesi);
            obj.put("eposta", this.ePosta);
            obj.put("fax", this.fax);
            obj.put("adres", this.adres);
            obj.put("telNo", this.telNo);
            obj.put("sorumluTckn", this.sorumluTckn);
            obj.put("sorumluAd", this.sorumluAd);
            obj.put("sorumluSoyad", this.sorumluSoyad);
            obj.put("sorumluEPosta", this.sorumluEPosta);
            obj.put("sorumluCepTel", this.sorumluCepTel);
            obj.put("basvuruTipi", this.basvuruTipi);
            obj.put("system", getSystem());
        } catch (JSONException e) {

            e.printStackTrace();
            throw new Exception(e);
        }
        return obj.toString();
    }

    public int getMaliMuhurIstedi() {
        return this.maliMuhurIstedi;
    }

    public void setMaliMuhurIstedi(int maliMuhurIstedi) {
        this.maliMuhurIstedi = maliMuhurIstedi;
    }

    public String getTicaretSicilNo() {
        return this.ticaretSicilNo;
    }

    public void setTicaretSicilNo(String ticaretSicilNo) {
        this.ticaretSicilNo = ticaretSicilNo;
    }

    public String getTicaretSicilMemurlugu() {
        return this.ticaretSicilMemurlugu;
    }

    public void setTicaretSicilMemurlugu(String ticaretSicilMemurlugu) {
        this.ticaretSicilMemurlugu = ticaretSicilMemurlugu;
    }

    public String getKanuniMerkezi() {
        return this.kanuniMerkezi;
    }

    public void setKanuniMerkezi(String kanuniMerkezi) {
        this.kanuniMerkezi = kanuniMerkezi;
    }

    public String getKurulusTarihi() {
        return this.kurulusTarihi;
    }

    public void setKurulusTarihi(String kurulusTarihi) {
        this.kurulusTarihi = kurulusTarihi;
    }

    public String getBagliBulunduguOda() {
        return this.bagliBulunduguOda;
    }

    public void setBagliBulunduguOda(String bagliBulunduguOda) {
        this.bagliBulunduguOda = bagliBulunduguOda;
    }

    public String getOdaSicilNo() {
        return this.odaSicilNo;
    }

    public void setOdaSicilNo(String odaSicilNo) {
        this.odaSicilNo = odaSicilNo;
    }

    public String getWebSitesi() {
        return this.webSitesi;
    }

    public void setWebSitesi(String webSitesi) {
        this.webSitesi = webSitesi;
    }

    public String getTelNo() {
        return this.telNo;
    }

    public void setTelNo(String telNo) {
        this.telNo = telNo;
    }

    public String getePosta() {
        return this.ePosta;
    }

    public void setePosta(String ePosta) {
        this.ePosta = ePosta;
    }

    public String getFax() {
        return this.fax;
    }

    public void setFax(String fax) {
        this.fax = fax;
    }

    public String getAdres() {
        return this.adres;
    }

    public void setAdres(String adres) {
        this.adres = adres;
    }

    public String getSorumluTckn() {
        return this.sorumluTckn;
    }

    public void setSorumluTckn(String sorumluTckn) {
        this.sorumluTckn = sorumluTckn;
    }

    public String getSorumluAd() {
        return this.sorumluAd;
    }

    public void setSorumluAd(String sorumluAd) {
        this.sorumluAd = sorumluAd;
    }

    public String getSorumluSoyad() {
        return this.sorumluSoyad;
    }

    public void setSorumluSoyad(String sorumluSoyad) {
        this.sorumluSoyad = sorumluSoyad;
    }

    public String getSorumluCepTel() {
        return this.sorumluCepTel;
    }

    public void setSorumluCepTel(String sorumluCepTel) {
        this.sorumluCepTel = sorumluCepTel;
    }

    public String getSorumluEPosta() {
        return this.sorumluEPosta;
    }

    public void setSorumluEPosta(String sorumluEPosta) {
        this.sorumluEPosta = sorumluEPosta;
    }

    public String getDosyaIsmi() {
        return this.dosyaIsmi;
    }

    public void setDosyaIsmi(String dosyaIsmi) {
        this.dosyaIsmi = dosyaIsmi;
    }

    public int getBasvuruTipi() {
        return this.basvuruTipi;
    }

    public void setBasvuruTipi(int basvuruTipi) {
        this.basvuruTipi = basvuruTipi;
    }

    public DataHandler getForm() {
        return this.form;
    }

    public void setForm(DataHandler form) {
        this.form = form;
    }

    public String getBasvuruBelgeNo() {
        return this.basvuruBelgeNo;
    }

    public void setBasvuruBelgeNo(String basvuruBelgeNo) {
        this.basvuruBelgeNo = basvuruBelgeNo;
    }
}
