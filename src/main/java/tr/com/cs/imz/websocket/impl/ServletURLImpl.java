package tr.com.cs.imz.websocket.impl;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.PrintStream;
import java.net.URL;
import java.net.URLConnection;
import java.util.LinkedList;
import tr.com.cs.basvuru.esign.ObjectHolder;
import tr.com.cs.imz.websocket.IServletConnection;
import tr.com.cs.imz.websocket.model.BasvuruTemel;
import tr.com.cs.imz.websocket.model.EDefterBasvuru;
import tr.com.cs.imz.websocket.model.EntegrasyonBasvuruTemel;

public class ServletURLImpl implements IServletConnection {
    private URLConnection srvCon = null;

    public ServletURLImpl(String urlPath) throws IOException {
        URL servletURL = new URL(urlPath);
        this.srvCon = servletURL.openConnection();
        this.srvCon.setDoOutput(true);
        this.srvCon.setUseCaches(false);
        this.srvCon.setRequestProperty("Content-Type", "application/x-java-serialized-object");
    }

    public URLConnection getURLConn() {
        return this.srvCon;
    }

    public LinkedList<String> writeObjects(
            BasvuruTemel basvuru, String fileName, byte[] imzTanimForm) throws Exception {
        LinkedList<String> retVal = new LinkedList<>();
        PrintStream out = new PrintStream(this.srvCon.getOutputStream());
        ObjectOutputStream oos = new ObjectOutputStream(out);

        if (basvuru instanceof EDefterBasvuru) {
            EDefterBasvuru basvur = (EDefterBasvuru) basvuru;
            oos.writeObject(new ObjectHolder("EDEFTER_KAYDET"));
            oos.writeObject(new ObjectHolder(basvur.getVkn()));
            oos.writeObject(new ObjectHolder(basvur.getUnvan()));
            oos.writeObject(new ObjectHolder(basvur.getTckn()));
            oos.writeObject(new ObjectHolder(basvur.getAd()));
            oos.writeObject(new ObjectHolder(basvur.getSoyad()));
            oos.writeObject(new ObjectHolder(basvur.geteImzaGecerlilikTarih()));
            oos.writeObject(new ObjectHolder(basvur.getUygulamaBaslangicTarih()));
            oos.writeObject(new ObjectHolder(basvur.getYazilimUreticiAd()));
            oos.writeObject(new ObjectHolder(basvur.getYazilimAd()));
            oos.writeObject(new ObjectHolder(basvur.getVersiyon_surum_no()));
            oos.writeObject(new ObjectHolder(basvur.getTicaretSicilNo()));
            oos.writeObject(new ObjectHolder(basvur.getTicaretSicilMemurlugu()));
            oos.writeObject(new ObjectHolder(basvur.getKanuniMerkez()));
            oos.writeObject(new ObjectHolder(basvur.getKurulusTarih()));
            oos.writeObject(new ObjectHolder(basvur.getTicaretSicilNo()));
            oos.writeObject(new ObjectHolder(basvur.getAdresi()));
            oos.writeObject(new ObjectHolder(basvur.getBagliBulunduguOda()));
            oos.writeObject(new ObjectHolder(basvur.getOdaSicilNo()));
            oos.writeObject(new ObjectHolder(basvur.getMukellefTelNo()));
            oos.writeObject(new ObjectHolder(basvur.getFaxNo()));
            oos.writeObject(new ObjectHolder(basvur.getMukellefEPosta()));
            oos.writeObject(new ObjectHolder(basvur.getWebSite()));
            oos.writeObject(new ObjectHolder(basvur.getIrtibatAdi()));
            oos.writeObject(new ObjectHolder(basvur.getIrtibatSoyadi()));
            oos.writeObject(new ObjectHolder(basvur.getIrtibatTelNo()));
            oos.writeObject(new ObjectHolder(basvur.getIrtibatCepNo()));
            oos.writeObject(new ObjectHolder(basvur.getIrtibatEPosta()));
        } else {
            if (basvuru instanceof tr.com.cs.imz.websocket.model.EntegrasyonBasvuruBis) {
                oos.writeObject(new ObjectHolder("BIS_KAYDET"));
            } else {
                oos.writeObject(new ObjectHolder("BASVURU_KAYDET"));
            }

            if (basvuru.isTuzel()) {
                oos.writeObject(new ObjectHolder(basvuru.getVkn()));
            } else {
                oos.writeObject(new ObjectHolder(basvuru.getTckn()));
            }

            if (basvuru instanceof tr.com.cs.imz.websocket.model.EFaturaBasvuruFormIstek) {
                oos.writeObject(new ObjectHolder(basvuru.toJSON()));
                oos.writeObject(new ObjectHolder(fileName));
            } else if (basvuru instanceof tr.com.cs.imz.websocket.model.EntegrasyonBasvuru) {
                oos.writeObject(new ObjectHolder(basvuru.toJSON()));
                oos.writeObject(
                        new ObjectHolder(((EntegrasyonBasvuruTemel) basvuru).getKisiListJSon()));
            } else if (basvuru instanceof EntegrasyonBasvuruTemel) {
                oos.writeObject(
                        new ObjectHolder(((EntegrasyonBasvuruTemel) basvuru).getKisiListJSon()));
            } else if (basvuru instanceof tr.com.cs.imz.websocket.model.OKCBasvuru) {
                oos.writeObject(new ObjectHolder(basvuru.toJSON()));
                oos.writeObject(new ObjectHolder(fileName));
            } else if (basvuru instanceof tr.com.cs.imz.websocket.model.EAPortalBasvuru) {
                oos.writeObject(new ObjectHolder(basvuru.toJSON()));
                oos.writeObject(new ObjectHolder(fileName));
            }

            if (basvuru.isTuzel()) {
                oos.writeObject(new ObjectHolder(basvuru.getUnvan()));
            } else {
                oos.writeObject(new ObjectHolder(basvuru.getAd() + " " + basvuru.getSoyad()));
            }
        }
        oos.writeObject(imzTanimForm);

        out.close();
        ObjectInputStream ois = new ObjectInputStream(this.srvCon.getInputStream());

        if (basvuru instanceof EDefterBasvuru) {
            retVal.add("edefter");
            retVal.add(ois.readObject().toString());
        } else {
            retVal.add("efatura");
            retVal.add(ois.readObject().toString());
            retVal.add(ois.readObject().toString());
        }

        ois.close();

        return retVal;
    }

    public LinkedList<String> writeObjects(String command, byte[] imzVeri) throws Exception {
        LinkedList<String> retVal = new LinkedList<>();
        PrintStream out = new PrintStream(this.srvCon.getOutputStream());
        ObjectOutputStream oos = new ObjectOutputStream(out);
        oos.writeObject(new ObjectHolder(command));
        oos.writeObject(imzVeri);
        out.close();
        InputStream inputStream = this.srvCon.getInputStream();

        ObjectInputStream ois = new ObjectInputStream(inputStream);
        retVal.add(ois.readObject().toString());
        return retVal;
    }
}
