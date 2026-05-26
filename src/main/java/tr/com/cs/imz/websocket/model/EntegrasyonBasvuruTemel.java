package tr.com.cs.imz.websocket.model;

import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public class EntegrasyonBasvuruTemel extends BasvuruTemel {
    private String kisiListJSon;
    private ArrayList<SorumluKisi> kisiList = new ArrayList<>();

    public void setKisiList(ArrayList<SorumluKisi> kisiList) {
        this.kisiList = kisiList;
    }

    public ArrayList<SorumluKisi> getKisiList() {
        return this.kisiList;
    }

    public void setKisiList(String input) throws JSONException {
        this.kisiListJSon = input;

        JSONArray jasonArray = new JSONArray(input);

        for (int i = 0; i < jasonArray.length(); i++) {
            JSONObject obj = (JSONObject) jasonArray.get(i);
            SorumluKisi kisi =
                    new SorumluKisi(
                            obj.getString("ad"),
                            obj.getString("soyad"),
                            obj.getString("eposta"),
                            obj.getString("telefon"));
            this.kisiList.add(kisi);
        }
    }

    public String getKisiListJSon() {
        return this.kisiListJSon;
    }

    public String toJSON() throws Exception {
        JSONObject obj = new JSONObject();
        try {
            obj.put("unvan", getUnvan());
            obj.put("vkn", getVkn());
        } catch (JSONException e) {

            e.printStackTrace();
            throw new Exception(e);
        }
        return obj.toString();
    }
}
