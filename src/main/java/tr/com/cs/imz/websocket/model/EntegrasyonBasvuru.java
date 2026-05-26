package tr.com.cs.imz.websocket.model;

import org.json.JSONException;
import org.json.JSONObject;

public class EntegrasyonBasvuru extends EntegrasyonBasvuruTemel {
    private String gbws;
    private String gbSunucuIp;
    private String gbIstemciIp;
    private String pkWs;
    private String pkSunucuIp;
    private String pkIstemciIp;

    public String getGbws() {
        return this.gbws;
    }

    public void setGbws(String gbws) {
        this.gbws = gbws;
    }

    public String getGbSunucuIp() {
        return this.gbSunucuIp;
    }

    public void setGbSunucuIp(String gpSunucuIp) {
        this.gbSunucuIp = gpSunucuIp;
    }

    public String getGbIstemciIp() {
        return this.gbIstemciIp;
    }

    public void setGbIstemciIp(String gpIstemciIp) {
        this.gbIstemciIp = gpIstemciIp;
    }

    public String getPkWs() {
        return this.pkWs;
    }

    public void setPkWs(String pkWs) {
        this.pkWs = pkWs;
    }

    public String getPkSunucuIp() {
        return this.pkSunucuIp;
    }

    public void setPkSunucuIp(String pkSunucuIp) {
        this.pkSunucuIp = pkSunucuIp;
    }

    public String getPkIstemciIp() {
        return this.pkIstemciIp;
    }

    public void setPkIstemciIp(String pkIstemciIp) {
        this.pkIstemciIp = pkIstemciIp;
    }

    public String toJSON() throws Exception {
        JSONObject obj = new JSONObject();
        try {
            obj.put("unvan", getUnvan());
            obj.put("vkn", getVkn());
            obj.put("gbws", getGbws());
            obj.put("gbSunucuIp", getGbSunucuIp());
            obj.put("gbIstemciIp", getGbIstemciIp());
            obj.put("pkWs", getPkWs());
            obj.put("pkSunucuIp", getPkSunucuIp());
            obj.put("pkIstemciIp", getPkIstemciIp());
        } catch (JSONException e) {

            e.printStackTrace();
            throw new Exception(e);
        }
        return obj.toString();
    }
}
