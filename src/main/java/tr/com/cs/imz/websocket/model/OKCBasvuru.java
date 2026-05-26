package tr.com.cs.imz.websocket.model;

import org.json.JSONException;
import org.json.JSONObject;

public class OKCBasvuru extends BasvuruTemel {
    private String faaliyetBasTar;
    private String isTel;
    private String fax;
    private String adres;
    private String eposta;
    private String webadres;
    private String kanuniMerkez;
    private String donem;
    private String sorumluTckn;
    private String sorumluAdSoyad;
    private String sorumluCepTel;
    private String sorumluEPosta;

    public String toJSON() throws Exception {
        JSONObject obj = new JSONObject();
        try {
            obj.put("vkn", getVkn());
            obj.put("unvan", getUnvan());
            obj.put("tckn", getTckn());
            obj.put("ad", getAd());
            obj.put("soyad", getSoyad());
            obj.put("faaliyetBasTar", this.faaliyetBasTar);
            obj.put("isTel", this.isTel);
            obj.put("fax", this.fax);
            obj.put("adres", this.adres);
            obj.put("eposta", this.eposta);
            obj.put("webadres", this.webadres);
            obj.put("kanuniMerkezi", this.kanuniMerkez);
            obj.put("donem", this.donem);
            obj.put("sorumluTckn", this.sorumluTckn);
            obj.put("sorumluAdSoyad", this.sorumluAdSoyad);
            obj.put("sorumluEPosta", this.sorumluEPosta);
            obj.put("sorumluCepTel", this.sorumluCepTel);
            obj.put("system", getSystem());
        } catch (JSONException e) {

            e.printStackTrace();
            throw new Exception(e);
        }
        return obj.toString();
    }

    public String getFaaliyetBasTar() {
        return this.faaliyetBasTar;
    }

    public void setFaaliyetBasTar(String faaliyetBasTar) {
        this.faaliyetBasTar = faaliyetBasTar;
    }

    public String getIsTel() {
        return this.isTel;
    }

    public void setIsTel(String isTel) {
        this.isTel = isTel;
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

    public String getEposta() {
        return this.eposta;
    }

    public void setEposta(String eposta) {
        this.eposta = eposta;
    }

    public String getWebadres() {
        return this.webadres;
    }

    public void setWebadres(String webadres) {
        this.webadres = webadres;
    }

    public String getKanuniMerkez() {
        return this.kanuniMerkez;
    }

    public void setKanuniMerkez(String kanuniMerkez) {
        this.kanuniMerkez = kanuniMerkez;
    }

    public String getDonem() {
        return this.donem;
    }

    public void setDonem(String donem) {
        this.donem = donem;
    }

    public String getSorumluTckn() {
        return this.sorumluTckn;
    }

    public void setSorumluTckn(String sorumluTckn) {
        this.sorumluTckn = sorumluTckn;
    }

    public String getSorumluAdSoyad() {
        return this.sorumluAdSoyad;
    }

    public void setSorumluAdSoyad(String sorumluAdSoyad) {
        this.sorumluAdSoyad = sorumluAdSoyad;
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
}
