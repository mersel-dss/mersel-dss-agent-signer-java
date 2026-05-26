package tr.com.cs.imz.websocket.model;

import org.json.JSONException;
import org.json.JSONObject;

public class EAPortalBasvuru extends BasvuruTemel {
    private String sicilNo;
    private String vd;
    private String telefon;
    private String eposta;
    private String kmerkez;
    private String ilce;
    private String il;
    private String ulke;
    private String belgeTip;
    private String uygZaman;

    public String getUygZaman() {
        return this.uygZaman;
    }

    public void setUygZaman(String uygZaman) {
        this.uygZaman = uygZaman;
    }

    public String getSicilNo() {
        return this.sicilNo;
    }

    public void setSicilNo(String sicilNo) {
        this.sicilNo = sicilNo;
    }

    public String getVd() {
        return this.vd;
    }

    public void setVd(String vd) {
        this.vd = vd;
    }

    public String getTelefon() {
        return this.telefon;
    }

    public void setTelefon(String telefon) {
        this.telefon = telefon;
    }

    public String getEposta() {
        return this.eposta;
    }

    public void setEposta(String eposta) {
        this.eposta = eposta;
    }

    public String getKmerkez() {
        return this.kmerkez;
    }

    public void setKmerkez(String kmerkez) {
        this.kmerkez = kmerkez;
    }

    public String getIlce() {
        return this.ilce;
    }

    public void setIlce(String ilce) {
        this.ilce = ilce;
    }

    public String getIl() {
        return this.il;
    }

    public void setIl(String il) {
        this.il = il;
    }

    public String getUlke() {
        return this.ulke;
    }

    public void setUlke(String ulke) {
        this.ulke = ulke;
    }

    public String getBelgeTip() {
        return this.belgeTip;
    }

    public void setBelgeTip(String belgeTip) {
        this.belgeTip = belgeTip;
    }

    public String toJSON() throws Exception {
        JSONObject obj = new JSONObject();
        try {
            obj.put("vkn", getVkn());
            obj.put("unvan", getUnvan());
            obj.put("tckn", getTckn());
            obj.put("ad", getAd());
            obj.put("soyad", getSoyad());
            obj.put("sicilNo", this.sicilNo);
            obj.put("vd", this.vd);
            obj.put("telefon", this.telefon);
            obj.put("eposta", this.eposta);
            obj.put("kmerkez", this.kmerkez);
            obj.put("ilce", this.ilce);
            obj.put("il", this.il);
            obj.put("ulke", this.ulke);
            obj.put("belgeTip", this.belgeTip);
            obj.put("uygZaman", this.uygZaman);
            obj.put("system", getSystem());
        } catch (JSONException e) {

            e.printStackTrace();
            throw new Exception(e);
        }
        return obj.toString();
    }
}
