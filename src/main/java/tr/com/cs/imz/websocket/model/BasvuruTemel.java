package tr.com.cs.imz.websocket.model;

public abstract class BasvuruTemel {
    private String vkn;
    private String unvan = "";
    private String tckn;
    private String ad = "";
    private String soyad = "";
    private String system;

    public String getSystem() {
        return this.system;
    }

    public void setSystem(String system) {
        this.system = system;
    }

    public String getVkn() {
        return this.vkn;
    }

    public void setVkn(String vkn) {
        this.vkn = vkn;
    }

    public String getUnvan() {
        return this.unvan;
    }

    public void setUnvan(String unvan) {
        this.unvan = unvan;
    }

    public String getTckn() {
        return this.tckn;
    }

    public void setTckn(String tckn) {
        this.tckn = tckn;
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

    public boolean isTuzel() {
        if (this.vkn == null || this.vkn.equals("")) return false;
        if (this.vkn.length() == 11) {
            return false;
        }
        return true;
    }

    public abstract String toJSON() throws Exception;
}
