package tr.com.cs.imz.websocket;

import com.itextpdf.text.BaseColor;
import com.itextpdf.text.Document;
import com.itextpdf.text.DocumentException;
import com.itextpdf.text.Element;
import com.itextpdf.text.Font;
import com.itextpdf.text.List;
import com.itextpdf.text.ListItem;
import com.itextpdf.text.Paragraph;
import com.itextpdf.text.Phrase;
import com.itextpdf.text.pdf.BaseFont;
import com.itextpdf.text.pdf.PdfPCell;
import com.itextpdf.text.pdf.PdfPTable;
import com.itextpdf.text.pdf.PdfWriter;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import tr.com.cs.imz.websocket.model.EAPortalBasvuru;
import tr.com.cs.imz.websocket.model.EDefterBasvuru;
import tr.com.cs.imz.websocket.model.EFaturaBasvuruFormIstek;
import tr.com.cs.imz.websocket.model.EntegrasyonBasvuru;
import tr.com.cs.imz.websocket.model.OKCBasvuru;
import tr.com.cs.imz.websocket.model.SorumluKisi;

public class PDFUtil {
    Font smallBold = new Font(Font.FontFamily.TIMES_ROMAN, 12.0F, 1);
    private static BaseFont STF_Helvetica_Turkish;
    private static Font fontNormal;
    private static Font fontBold;
    private static Font fontSmaller;
    private static BaseColor cellHeadarBackgroundColor = new BaseColor(204, 204, 204);

    public static byte[] createPdf(EFaturaBasvuruFormIstek form, String fileName) throws Exception {

        boolean tuzelKisi = !(form.getVkn() == null || form.getVkn().equals(""));

        STF_Helvetica_Turkish = BaseFont.createFont("Helvetica", "CP1254", false);

        fontNormal = new Font(STF_Helvetica_Turkish, 12.0F, 0);

        fontBold = new Font(STF_Helvetica_Turkish, 12.0F, 1);

        Document document = new Document();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PdfWriter.getInstance(document, outputStream);

        document.open();

        document.addTitle("Gelir İdaresi Başkanlığı");

        document.addSubject("Gelir İdaresi Başkanlığı");

        document.addAuthor("Gelir İdaresi Başkanlığı");

        document.addCreator("Gelir İdaresi Başkanlığı");

        Paragraph preface = new Paragraph();

        Paragraph tarihP = new Paragraph("Tarih : " + nowShort(), fontNormal);

        tarihP.setAlignment(0);

        preface.add((Element) tarihP);

        Paragraph sayiP = new Paragraph("Sayı : " + fileName, fontNormal);

        sayiP.setAlignment(0);

        preface.add((Element) sayiP);

        addEmptyLine(preface, 1);

        Paragraph baslikP =
                new Paragraph("e-Fatura Uygulaması Başvuru Formu ve Taahhütnamesi", fontBold);

        baslikP.setAlignment(1);

        preface.add((Element) baslikP);

        Paragraph baslik2P = new Paragraph("Gelir İdaresi Başkanlığı", fontNormal);

        baslik2P.setAlignment(1);

        preface.add((Element) baslik2P);

        Paragraph baslik3P =
                new Paragraph("Uygulama ve Veri Yönetimi Daire Başkanlığı (III)", fontNormal);

        baslik3P.setAlignment(1);

        preface.add((Element) baslik3P);

        addEmptyLine(preface, 1);

        Paragraph introP =
                new Paragraph(
                        "e-Fatura Uygulamasından yararlanmak istiyorum. Talebimin değerlendirilmesi ve gerekli işlemlerin yapılarak kullanıcı hesabımın oluşturulması hususunda gereğini arz ederim. ",
                        fontNormal);

        preface.add((Element) introP);

        addEmptyLine(preface, 1);

        float[] colsWidth = {1.0F, 2.0F};

        PdfPTable table = new PdfPTable(colsWidth);

        table.setWidthPercentage(90.0F);

        table.setHorizontalAlignment(0);

        PdfPCell cell =
                new PdfPCell((Phrase) new Paragraph("Mükellef Kimlik/Adres Bilgileri ", fontBold));

        cell.setColspan(2);

        table.addCell(cell);

        switch (form.getBasvuruTipi()) {
            case 0:
                table.addCell((Phrase) new Paragraph("Başvuru Tipi :", fontNormal));

                table.addCell((Phrase) new Paragraph("GİB Portal", fontNormal));

                break;

            case 1:
                table.addCell((Phrase) new Paragraph("Başvuru Tipi :", fontNormal));

                table.addCell((Phrase) new Paragraph("Entegrasyon", fontNormal));

                break;

            case 2:
                table.addCell((Phrase) new Paragraph("Başvuru Tipi :", fontNormal));

                table.addCell((Phrase) new Paragraph("Özel Entegratör", fontNormal));

                break;

            case 3:
                table.addCell((Phrase) new Paragraph("Başvuru Tipi :", fontNormal));

                table.addCell((Phrase) new Paragraph("Portal - Sadece İrsaliye", fontNormal));

                break;

            case 55:
                table.addCell((Phrase) new Paragraph("Başvuru Tipi :", fontNormal));

                table.addCell(
                        (Phrase)
                                new Paragraph(
                                        "Portal 01.01." + (Calendar.getInstance().get(1) + 1),
                                        fontNormal));

                break;

            case 56:
                table.addCell((Phrase) new Paragraph("Başvuru Tipi :", fontNormal));

                table.addCell(
                        (Phrase)
                                new Paragraph(
                                        "Portal 01.07." + Calendar.getInstance().get(1),
                                        fontNormal));

                break;
        }

        if (tuzelKisi) {

            table.addCell((Phrase) new Paragraph("Vergi Kimlik Numarası :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getVkn(), fontNormal));

            table.addCell((Phrase) new Paragraph("Unvanı :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getUnvan(), fontNormal));

            table.addCell((Phrase) new Paragraph("Ticaret Sicil No :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getTicaretSicilNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Ticaret Sicil Memurluğu :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getTicaretSicilMemurlugu(), fontNormal));

            table.addCell((Phrase) new Paragraph("Kanuni Merkezi :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getKanuniMerkezi(), fontNormal));

            table.addCell((Phrase) new Paragraph("Kuruluş Tarihi :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getKurulusTarihi(), fontNormal));

        } else {

            table.addCell((Phrase) new Paragraph("T.C. Kimlik Numarası :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getTckn(), fontNormal));

            table.addCell((Phrase) new Paragraph("Ad Soyad :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getAd() + " " + form.getSoyad(), fontNormal));
        }

        table.addCell((Phrase) new Paragraph("Adresi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getAdres(), fontNormal));

        if (tuzelKisi) {

            table.addCell((Phrase) new Paragraph("Bağlı Bulunduğu Oda :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getBagliBulunduguOda(), fontNormal));

            table.addCell((Phrase) new Paragraph("Oda Sicil No :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getOdaSicilNo(), fontNormal));
        }

        table.addCell((Phrase) new Paragraph("Telefon Numarası :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getTelNo(), fontNormal));

        if (form.getFax() != null && !form.getFax().equals("")) {

            table.addCell((Phrase) new Paragraph("Fax Numarası :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getFax(), fontNormal));
        }

        table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getePosta(), fontNormal));

        if (form.getWebSitesi() != null && !form.getWebSitesi().equals("")) {

            table.addCell((Phrase) new Paragraph("Web Sitesi :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getWebSitesi(), fontNormal));
        }

        if (tuzelKisi) {

            PdfPCell cell2 =
                    new PdfPCell((Phrase) new Paragraph("Sertifika Sorumlusunun", fontBold));

            cell2.setColspan(2);

            table.addCell(cell2);

            table.addCell((Phrase) new Paragraph("T.C. Kimlik Numarası :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getSorumluTckn(), fontNormal));

            table.addCell((Phrase) new Paragraph("Adı Soyadı :", fontNormal));

            table.addCell(
                    (Phrase)
                            new Paragraph(
                                    form.getSorumluAd() + " " + form.getSorumluSoyad(),
                                    fontNormal));

            table.addCell((Phrase) new Paragraph("Cep Telefonu :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getSorumluCepTel(), fontNormal));

            table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getSorumluEPosta(), fontNormal));
        }

        preface.add((Element) table);

        addEmptyLine(preface, 1);

        preface.add(
                (Element)
                        new Paragraph(
                                "Tarafıma e-Fatura Uygulamasından yararlanma izninin verilmesi halinde;",
                                fontNormal));

        document.add((Element) preface);

        List orderedList = new List(true);

        orderedList.add(
                (Element)
                        new ListItem(
                                "Başkanlık tarafından e-Fatura Uygulaması ile ilgili yayımlanan genel tebliğ ve diğer ilgili düzenlemelerle getirilecek usul ve esasların tümüne uyacağımı, e-Fatura Uygulamasının, Mali Mühür ve Nitelikli Elektronik Sertifikanın kullanımı ile ilgili her türlü sorumluluğu kabul ettiğimi,",
                                fontNormal));

        orderedList.add(
                (Element)
                        new ListItem(
                                "Kullanıcı hesabımın yetkisiz ve ilgisiz kişilerce kullanımına izin vermeyeceğimi ve kullanıcı hesabı ile ilgili bilgilerimi kimseye devretmeyeceğimi, kiralamayacağımı, satmayacağımı ve maddi-gayrimaddi herhangi bir menfaate konu etmeyeceğimi, kullanıcı hesabım ile başkaları tarafından işlem yapıldığını öğrendiğim anda durumu Gelir İdaresi Başkanlığına bildireceğimi,",
                                fontNormal));

        orderedList.add(
                (Element)
                        new ListItem(
                                "e-Fatura Uygulamasını başka amaçlarla kullanmayacağımı, belirlenen kurallar dışında e-fatura göndermeyeceğimi ve almayacağımı, aksi durumda doğacak her türlü sorumluluğun tarafıma ait olduğunu,",
                                fontNormal));

        orderedList.add(
                (Element)
                        new ListItem(
                                "Kullanıcı hesabıma ulaştırılan fatura ile diğer tüm bilgi ve belgelerin, hesabıma ulaştırıldığı andan itibaren kurumumun bilgisi/bilgim dahiline girdiğini,",
                                fontNormal));

        orderedList.add(
                (Element)
                        new ListItem(
                                "Gerek e-Fatura Uygulaması gerekse yapılacak diğer düzenlemeler çerçevesinde kullanıcı hesabım aracılığı ile yapılan tüm işlemlerin tespit ve tevsikinde Gelir İdaresi Başkanlığına ait kayıtların esas alınacağını,",
                                fontNormal));

        document.add((Element) orderedList);

        preface = new Paragraph();

        preface.add((Element) new Paragraph("kabul ve taahhüt ediyorum.", fontNormal));

        document.add((Element) preface);

        document.close();

        outputStream.close();

        return outputStream.toByteArray();
    }

    public static byte[] createPdf(EntegrasyonBasvuru basvuru, String fileName) throws Exception {

        try {

            STF_Helvetica_Turkish = BaseFont.createFont("Helvetica", "CP1254", false);

            fontNormal = new Font(STF_Helvetica_Turkish, 12.0F, 0);

            fontBold = new Font(STF_Helvetica_Turkish, 12.0F, 1);

            Document document = new Document();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfWriter.getInstance(document, outputStream);

            document.open();

            document.addTitle("Gelir İdaresi Başkanlığı");

            document.addSubject("Gelir İdaresi Başkanlığı");

            document.addAuthor("Gelir İdaresi Başkanlığı");

            document.addCreator("Gelir İdaresi Başkanlığı");

            Paragraph preface = new Paragraph();

            Paragraph tarihP = new Paragraph(formatDate(now()));

            tarihP.setAlignment(2);

            preface.add((Element) tarihP);

            String system = "GELİŞTİRME";

            if (basvuru.getSystem().equals("PROD")) {

                system = "CANLI";

            } else if (basvuru.getSystem().equals("TEST")) {

                system = "TEST";
            }

            Paragraph baslikP =
                    new Paragraph("E-Fatura Entegrasyon " + system + " Tanım Formu", fontBold);

            baslikP.setAlignment(1);

            preface.add((Element) baslikP);

            addEmptyLine(preface, 1);

            float[] colsWidth = {1.0F, 2.0F};

            PdfPTable table = new PdfPTable(colsWidth);

            table.setWidthPercentage(100.0F);

            table.setHorizontalAlignment(0);

            PdfPCell cell = new PdfPCell((Phrase) new Paragraph("MüKELLEF BİLGİLERİ", fontBold));

            cell.setColspan(2);

            cell.setBackgroundColor(cellHeadarBackgroundColor);

            table.addCell(cell);

            table.addCell((Phrase) new Paragraph("UNVANI :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getUnvan(), fontNormal));

            table.addCell((Phrase) new Paragraph("VKN :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getVkn(), fontNormal));

            cell = new PdfPCell((Phrase) new Paragraph("GÖNDERİCİ BİRİM BİLGİLERİ", fontBold));

            cell.setBackgroundColor(cellHeadarBackgroundColor);

            cell.setColspan(2);

            table.addCell(cell);

            table.addCell((Phrase) new Paragraph("Web Servis Uç Noktası :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getGbws(), fontNormal));

            table.addCell((Phrase) new Paragraph("Sunucu IP Adresi :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getGbSunucuIp(), fontNormal));

            table.addCell((Phrase) new Paragraph("İstemci IP Adresi :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getGbIstemciIp(), fontNormal));

            cell = new PdfPCell((Phrase) new Paragraph("POSTA KUTUSU BİLGİLERİ", fontBold));

            cell.setBackgroundColor(cellHeadarBackgroundColor);

            cell.setColspan(2);

            table.addCell(cell);

            table.addCell((Phrase) new Paragraph("Web Servis Uç Noktası :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getPkWs(), fontNormal));

            table.addCell((Phrase) new Paragraph("Sunucu IP Adresi :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getPkSunucuIp(), fontNormal));

            table.addCell((Phrase) new Paragraph("İstemci IP Adresi :", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getPkIstemciIp(), fontNormal));

            preface.add((Element) table);

            float[] colsWidthKisi = {1.0F, 1.0F, 1.0F};

            PdfPTable tableKisi = new PdfPTable(colsWidthKisi);

            tableKisi.setWidthPercentage(100.0F);

            tableKisi.setHorizontalAlignment(0);

            cell =
                    new PdfPCell(
                            (Phrase)
                                    new Paragraph(
                                            "SİSTEM SORUMLULARI VE UYGULAMA GELİŞTİRİCİLERE AİT BİLGİLERİ",
                                            fontBold));

            cell.setColspan(3);

            cell.setBackgroundColor(cellHeadarBackgroundColor);

            tableKisi.addCell(cell);

            tableKisi.addCell((Phrase) new Paragraph("Adı ve Soyadı:", fontBold));

            tableKisi.addCell((Phrase) new Paragraph("e-Posta Adresi:", fontBold));

            tableKisi.addCell((Phrase) new Paragraph("Telefon No :", fontBold));

            for (int i = 0; i < basvuru.getKisiList().size(); i++) {

                tableKisi.addCell(
                        (Phrase)
                                new Paragraph(
                                        ((SorumluKisi) basvuru.getKisiList().get(i)).getAd()
                                                + " "
                                                + ((SorumluKisi) basvuru.getKisiList().get(i))
                                                        .getSoyad(),
                                        fontNormal));

                tableKisi.addCell(
                        (Phrase)
                                new Paragraph(
                                        ((SorumluKisi) basvuru.getKisiList().get(i)).getEposta(),
                                        fontNormal));

                tableKisi.addCell(
                        (Phrase)
                                new Paragraph(
                                        ((SorumluKisi) basvuru.getKisiList().get(i)).getTelefon(),
                                        fontNormal));
            }

            preface.add((Element) tableKisi);

            addEmptyLine(preface, 1);

            Paragraph footer =
                    new Paragraph(
                            "Bu belge elektronik ortamda oluşturulup, imzalanmıştır. ", fontBold);

            footer.setAlignment(0);

            preface.add((Element) footer);

            document.add((Element) preface);

            document.close();

            outputStream.close();

            return outputStream.toByteArray();

        } catch (DocumentException e) {

            e.printStackTrace();

            throw new Exception("Pdf oluşturulurken bir hata oluştu. " + e.getMessage());

        } catch (IOException e) {

            e.printStackTrace();

            throw new Exception(
                    "Input output işlemleri sırasında bir hata oluştu." + e.getMessage());

        } catch (Exception e) {

            e.printStackTrace();

            throw new Exception("Bir hata oluştu. " + e.getMessage());
        }
    }

    public static byte[] createPdf(EDefterBasvuru basvuru, String fileName) throws Exception {

        try {

            STF_Helvetica_Turkish = BaseFont.createFont("Helvetica", "CP1254", false);

            fontNormal = new Font(STF_Helvetica_Turkish, 10.0F, 0);

            fontBold = new Font(STF_Helvetica_Turkish, 11.0F, 1);

            fontSmaller = new Font(STF_Helvetica_Turkish, 9.0F, 0);

            float[] colsWidth = {1.0F, 2.0F};

            Document document = new Document();

            ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

            PdfWriter.getInstance(document, outputStream);

            document.open();

            document.addTitle("Gelir İdaresi Başkanlığı");

            document.addSubject("Gelir İdaresi Başkanlığı");

            document.addAuthor("Gelir İdaresi Başkanlığı");

            document.addCreator("Gelir İdaresi Başkanlığı");

            Paragraph anaParagraf = new Paragraph();

            Paragraph baslik =
                    new Paragraph("E-DEFTER UYGULAMASI BAŞVURU FORMU VE TAAHHüTNAMESİ", fontBold);

            baslik.setAlignment(1);

            anaParagraf.add((Element) baslik);

            baslik = new Paragraph("Gelir İdaresi Başkanlığı", fontBold);

            baslik.setAlignment(1);

            anaParagraf.add((Element) baslik);

            baslik = new Paragraph("Uygulama ve Veri Yönetimi Daire Başkanlığı (III)", fontBold);

            baslik.setAlignment(1);

            anaParagraf.add((Element) baslik);

            addEmptyLine(anaParagraf, 1);

            baslik =
                    new Paragraph(
                            "Yevmiye defterim ile büyük defterimi 1 Sıra No.lu Elektronik Defter Genel Tebliği düzenlemelerine uygun biçimde e-Defter olarak tutmak istiyorum. Talebimin değerlendirilmesi ve gerekli işlemlerin yapılması hususunda gereğini arz ederim. ",
                            fontSmaller);

            baslik.setAlignment(3);

            anaParagraf.add((Element) baslik);

            addEmptyLine(anaParagraf, 1);

            PdfPTable table = new PdfPTable(colsWidth);

            table.setWidthPercentage(100.0F);

            table.setHorizontalAlignment(0);

            PdfPCell cell = new PdfPCell((Phrase) new Paragraph("MüKELLEF BİLGİLERİ", fontBold));

            cell.setColspan(2);

            cell.setBackgroundColor(cellHeadarBackgroundColor);

            table.addCell(cell);

            if (basvuru.isTuzel()) {

                table.addCell(
                        (Phrase) new Paragraph("VKN:                              ", fontNormal));

                table.addCell((Phrase) new Paragraph(basvuru.getVkn(), fontNormal));

                table.addCell(
                        (Phrase) new Paragraph("Unvanı:                           ", fontNormal));

                table.addCell((Phrase) new Paragraph(basvuru.getUnvan(), fontNormal));

            } else {

                table.addCell(
                        (Phrase) new Paragraph("TCKN:                             ", fontNormal));

                table.addCell((Phrase) new Paragraph(basvuru.getTckn(), fontNormal));

                table.addCell(
                        (Phrase) new Paragraph("Adı:                              ", fontNormal));

                table.addCell((Phrase) new Paragraph(basvuru.getAd(), fontNormal));

                table.addCell(
                        (Phrase) new Paragraph("Soyadı:                           ", fontNormal));

                table.addCell((Phrase) new Paragraph(basvuru.getSoyad(), fontNormal));

                table.addCell(
                        (Phrase) new Paragraph("Elektronik İmza Geçerlilik Tarihi:", fontNormal));

                table.addCell(
                        (Phrase) new Paragraph(basvuru.geteImzaGecerlilikTarih(), fontNormal));
            }

            table.addCell(
                    (Phrase)
                            new Paragraph(
                                    "Uygulama Başlangıç Tarihi:                ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getUygulamaBaslangicTarih(), fontNormal));

            table.addCell((Phrase) new Paragraph("Yazılım üreticisi Adı:            ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getYazilimUreticiAd(), fontNormal));

            table.addCell((Phrase) new Paragraph("Yazılım Adı:               ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getYazilimAd(), fontNormal));

            table.addCell((Phrase) new Paragraph("Versiyon/Sürüm No:                ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getVersiyon_surum_no(), fontNormal));

            table.addCell((Phrase) new Paragraph("Ticaret Sicil No:                 ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getTicaretSicilNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Ticaret Sicil Memurluğu:          ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getTicaretSicilMemurlugu(), fontNormal));

            table.addCell((Phrase) new Paragraph("Kanuni Merkezi:                   ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getKanuniMerkez(), fontNormal));

            table.addCell((Phrase) new Paragraph("Kuruluş Tarihi:                   ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getKurulusTarih(), fontNormal));

            table.addCell((Phrase) new Paragraph("Adresi:                           ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getAdresi(), fontNormal));

            table.addCell((Phrase) new Paragraph("Bağlı Bulunduğu Oda:              ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getBagliBulunduguOda(), fontNormal));

            table.addCell((Phrase) new Paragraph("Oda Sicil No:                     ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getOdaSicilNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Telefon Numarası:                 ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getMukellefTelNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Fax Numarası:                     ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getFaxNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi:          ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getMukellefEPosta(), fontNormal));

            table.addCell((Phrase) new Paragraph("Web Sitesi:                       ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getWebSite(), fontNormal));

            cell =
                    new PdfPCell(
                            (Phrase)
                                    new Paragraph(
                                            "İRTİBAT KURULACAK KİŞİYE AİT BİLGİLER", fontBold));

            cell.setColspan(2);

            cell.setBackgroundColor(cellHeadarBackgroundColor);

            table.addCell(cell);

            table.addCell((Phrase) new Paragraph("Adı:                              ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getIrtibatAdi(), fontNormal));

            table.addCell((Phrase) new Paragraph("Soyadı:                           ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getIrtibatSoyadi(), fontNormal));

            table.addCell((Phrase) new Paragraph("Telefon Numarası:                 ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getIrtibatTelNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Cep Telefonu:                     ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getIrtibatCepNo(), fontNormal));

            table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi:          ", fontNormal));

            table.addCell((Phrase) new Paragraph(basvuru.getIrtibatEPosta(), fontNormal));

            anaParagraf.add((Element) table);

            addEmptyLine(anaParagraf, 1);

            baslik =
                    new Paragraph(
                            "Tarafıma e-Defter Uygulamasından yararlanma izninin verilmesi halinde;",
                            fontSmaller);

            baslik.setAlignment(3);

            anaParagraf.add((Element) baslik);

            List numberedList = new List(true);

            numberedList.setIndentationLeft(0.0F);

            ListItem listItem =
                    new ListItem(
                            "e-Defter Uygulaması ile ilgili yayımlanan genel tebliğ ve diğer ilgili düzenlemelerle getirilecek usul ve esasların tümüne uyacağımı, e-Defter Uygulamasının kullanımı ile ilgili her türlü sorumluluğu kabul ettiğimi, ",
                            fontSmaller);

            listItem.setAlignment(3);

            numberedList.add((Element) listItem);

            listItem =
                    new ListItem(
                            "e-Defter Uygulamasında oluşturulan kullanıcı hesabımın yetkisiz ve ilgisiz kişilerce kullanımına izin vermeyeceğimi ve kullanıcı hesabı ile ilgili bilgilerimi kimseye devretmeyeceğimi, kiralamayacağımı, satmayacağımı ve maddi-gayrimaddi herhangi bir menfaate konu etmeyeceğimi, kullanıcı hesabım ile başkaları tarafından işlem yapıldığını öğrendiğim anda durumu Gelir İdaresi Başkanlığına bildireceğimi, ",
                            fontSmaller);

            listItem.setAlignment(3);

            numberedList.add((Element) listItem);

            listItem =
                    new ListItem(
                            "e-Defter oluşturma, imzalama/mühürleme, muhafaza etme işlemlerimi sadece başvuru formu ekinde bildirdiğim yazılımla yerine getireceğimi, bunlar dışında başka yazılım, program ve araçları kullanmayacağımı, bildirdiğim yazılımı/yazılımları değiştirmek istemem halinde başvurumu yenileyeceğimi, ",
                            fontSmaller);

            listItem.setAlignment(3);

            numberedList.add((Element) listItem);

            listItem =
                    new ListItem(
                            "e-Defter Uygulamasını başka amaçlarla kullanmayacağımı, belirlenen kurallar dışında e-Defter ve/veya e-Defter beratı göndermeyeceğimi aksi durumda doğacak her türlü sorumluluğun tarafıma ait olduğunu, ",
                            fontSmaller);

            listItem.setAlignment(3);

            numberedList.add((Element) listItem);

            listItem =
                    new ListItem(
                            "Kullanıcı hesabıma ulaştırılan defter beratı ve diğer tüm bilgi ve belgelerin, hesabıma ulaştırıldığı andan itibaren bilgim dahiline girdiğini, ",
                            fontSmaller);

            listItem.setAlignment(3);

            numberedList.add((Element) listItem);

            listItem =
                    new ListItem(
                            "Gerek e-Defter Uygulaması gerekse yapılacak diğer düzenlemeler çerçevesinde kullanıcı hesabım aracılığı ile yapılan tüm işlemlerin tespit ve tevsikinde Gelir İdaresi Başkanlığına ait kayıtların esas alınacağını, kabul ve taahhüt ediyorum.",
                            fontSmaller);

            listItem.setAlignment(3);

            numberedList.add((Element) listItem);

            anaParagraf.add((Element) numberedList);

            document.add((Element) anaParagraf);

            document.close();

            outputStream.close();

            return outputStream.toByteArray();

        } catch (DocumentException e) {

            e.printStackTrace();

            throw new Exception("Pdf oluşturulurken bir hata oluştu. " + e.getMessage());

        } catch (IOException e) {

            e.printStackTrace();

            throw new Exception(
                    "Input output işlemleri sırasında bir hata oluştu." + e.getMessage());

        } catch (Exception e) {

            e.printStackTrace();

            throw new Exception("Bir hata oluştu. " + e.getMessage());
        }
    }

    private static void addEmptyLine(Paragraph paragraph, int number) {

        for (int i = 0; i < number; i++) {

            paragraph.add((Element) new Paragraph(" "));
        }
    }

    public static String nowShort() {

        Calendar cal = Calendar.getInstance();

        SimpleDateFormat sdf = new SimpleDateFormat("dd/MM/yyyy");

        return sdf.format(cal.getTime());
    }

    public static String now() {

        Calendar cal = Calendar.getInstance();

        SimpleDateFormat sdf = new SimpleDateFormat("yyyyMMddHHmmss");

        return sdf.format(cal.getTime());
    }

    public static String formatDate(String str) {

        if (str == null || "".equals(str)) {

            return "01/03/2010";
        }

        return str.substring(6, 8) + "/" + str.substring(4, 6) + "/" + str.substring(0, 4);
    }

    public static byte[] createPdf(OKCBasvuru form, String fileName) throws Exception {

        boolean tuzelKisi = !(form.getVkn() == null || form.getVkn().equals(""));

        STF_Helvetica_Turkish = BaseFont.createFont("Helvetica", "CP1254", false);

        fontNormal = new Font(STF_Helvetica_Turkish, 12.0F, 0);

        fontBold = new Font(STF_Helvetica_Turkish, 12.0F, 1);

        Document document = new Document();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PdfWriter.getInstance(document, outputStream);

        document.open();

        document.addTitle("Gelir İdaresi Başkanlığı");

        document.addSubject("Gelir İdaresi Başkanlığı");

        document.addAuthor("Gelir İdaresi Başkanlığı");

        document.addCreator("Gelir İdaresi Başkanlığı");

        Paragraph preface = new Paragraph();

        Paragraph tarihP = new Paragraph("Tarih : " + nowShort(), fontNormal);

        tarihP.setAlignment(0);

        preface.add((Element) tarihP);

        Paragraph sayiP = new Paragraph("Sayı : " + fileName, fontNormal);

        sayiP.setAlignment(0);

        preface.add((Element) sayiP);

        addEmptyLine(preface, 1);

        Paragraph baslikP =
                new Paragraph(
                        "Organize Perakende Sektöründe Faaliyet Gösteren ve Belli Büyüklüğe Sahip Mükelleflerin Eski Nesil ÖKC’lerin Mali Hafızaları Doluncaya Kadar Kullanmaya Devam Edilebilmesi İçin 483 No.lu VUK GT Uyarınca  Elektronik Ortamda Yapılacak Başvuru Formu",
                        fontBold);

        baslikP.setAlignment(1);

        preface.add((Element) baslikP);

        Paragraph baslik2P = new Paragraph("Gelir İdaresi Başkanlığı", fontNormal);

        baslik2P.setAlignment(1);

        preface.add((Element) baslik2P);

        Paragraph baslik3P =
                new Paragraph("Uygulama ve Veri Yönetimi Daire Başkanlığı (III)", fontNormal);

        baslik3P.setAlignment(1);

        preface.add((Element) baslik3P);

        addEmptyLine(preface, 1);

        addEmptyLine(preface, 1);

        float[] colsWidth = {1.0F, 2.0F};

        PdfPTable table = new PdfPTable(colsWidth);

        table.setWidthPercentage(90.0F);

        table.setHorizontalAlignment(0);

        PdfPCell cell =
                new PdfPCell((Phrase) new Paragraph("MÜKELLEF KİMLİK/ADRES BİLGİLERİ", fontBold));

        cell.setBackgroundColor(cellHeadarBackgroundColor);

        cell.setColspan(2);

        table.addCell(cell);

        if (form.getVkn().length() == 10) {

            table.addCell((Phrase) new Paragraph("Vergi Kimlik Numarası :", fontNormal));

        } else {

            table.addCell((Phrase) new Paragraph("TC Kimlik Numarası :", fontNormal));
        }

        table.addCell((Phrase) new Paragraph(form.getVkn(), fontNormal));

        if (form.getVkn().length() == 10) {

            table.addCell((Phrase) new Paragraph("Unvanı :", fontNormal));

        } else {

            table.addCell((Phrase) new Paragraph("Ad Soyad :", fontNormal));
        }

        table.addCell((Phrase) new Paragraph(form.getUnvan(), fontNormal));

        table.addCell((Phrase) new Paragraph("Faaliyet Başlama Tarihi:", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getFaaliyetBasTar(), fontNormal));

        table.addCell((Phrase) new Paragraph("Telefon Numarası :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getIsTel(), fontNormal));

        if (form.getFax() != null && !form.getFax().equals("")) {

            table.addCell((Phrase) new Paragraph("Fax Numarası :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getFax(), fontNormal));
        }

        table.addCell((Phrase) new Paragraph("Adresi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getAdres(), fontNormal));

        table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getEposta(), fontNormal));

        if (form.getWebadres() != null && !form.getWebadres().equals("")) {

            table.addCell((Phrase) new Paragraph("Web Sitesi :", fontNormal));

            table.addCell((Phrase) new Paragraph(form.getWebadres(), fontNormal));
        }

        table.addCell((Phrase) new Paragraph("Kanuni Merkezi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getKanuniMerkez(), fontNormal));

        table.addCell((Phrase) new Paragraph("Mali Rapor Dönemi :", fontNormal));

        switch (form.getDonem()) {
            case "1":
                table.addCell((Phrase) new Paragraph("GÜNLÜK", fontNormal));

                break;

            case "2":
                table.addCell((Phrase) new Paragraph("ON'AR GÜNLÜK", fontNormal));

                break;

            case "3":
                table.addCell((Phrase) new Paragraph("AYLIK", fontNormal));

                break;
        }

        PdfPCell cell2 =
                new PdfPCell((Phrase) new Paragraph("ŞİRKET SORUMLUSU BİLGİLERİ", fontBold));

        cell2.setColspan(2);

        cell2.setBackgroundColor(cellHeadarBackgroundColor);

        table.addCell(cell2);

        table.addCell((Phrase) new Paragraph("TC Kimlik Numarası :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getSorumluTckn(), fontNormal));

        table.addCell((Phrase) new Paragraph("Adı Soyadı :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getSorumluAdSoyad(), fontNormal));

        table.addCell((Phrase) new Paragraph("Cep Telefonu :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getSorumluCepTel(), fontNormal));

        table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getSorumluEPosta(), fontNormal));

        preface.add((Element) table);

        addEmptyLine(preface, 1);

        preface.add(
                (Element)
                        new Paragraph(
                                "483 Sıra No.lu Vergi Usul Kanunu Genel Tebliğinin 3'üncü maddesi kapsamında bulunduğunuzdan başvurunuz alınmıştır.",
                                fontNormal));

        addEmptyLine(preface, 1);

        preface.add(
                (Element)
                        new Paragraph(
                                "483 Sıra No.lu Tebliğde öngörüldüğü üzere ayrıca; ", fontNormal));

        List orderedList = new List(true);

        orderedList.add(
                (Element)
                        new ListItem(
                                "1/1/2018 tarihine kadar ÖKC fişlerine ait günlük mali bilgileri GİB bilgi sistemine e-Arşiv uygulaması aracılığı ile elektronik ortamda bildirebilecek teknik alt yapıyı tesis etmeniz;",
                                fontNormal));

        orderedList.add(
                (Element)
                        new ListItem(
                                "1/7/2018 tarihine kadar da perakende mal satışı ve hizmet ifalarına yönelik olarak ÖKC’lerden düzenlenen ÖKC günlük kapanış (Z) raporlarına ait ilgili bilgileri (www.efatura.gov.tr) internet adresinde yayınlanan “Perakende Mal Satışları ile Hizmet İfalarına İlişkin Mali Rapor Bildirim Kılavuzu”nda yapılan açıklamalara uygun olarak, güvenli veri depolama ve sorgulama sistemlerinde muhafaza ve GİB’in erişimine sunacak alt yapıyı tesis etmeniz  veya bir dış hizmet sağlayıcı tarafından bu niteliklere haiz olarak oluşturulan alt yapı hizmetinden yararlanmanız gerekmektedir.",
                                fontNormal));

        orderedList.add((Element) new ListItem("", fontNormal));

        preface.add((Element) orderedList);

        addEmptyLine(preface, 1);

        addEmptyLine(preface, 1);

        addEmptyLine(preface, 1);

        preface.add(
                (Element)
                        new Paragraph(
                                "Belirtilen bu şartların yerine getirilmemesi halinde; 483 No.lu Tebliğle getirilen imkandan (eski nesil ÖKC'lerinizi  ilk alış faturası tarihinden itibaren 10 yıl boyunca kullanabilme imkanından) yararlanabilmeniz mümkün olmayacak ve sahip olduğunuz eski nesil ÖKC'leri  426 Sıra No.lu Vergi Usul Kanunu Genel Tebliği (Güncel) ile belirlenen kademeli geçiş tarihlerinde Yeni Nesil ÖKC'lerle değiştirerek kullanmaya başlamanız söz konusu olacaktır.",
                                fontNormal));

        addEmptyLine(preface, 1);

        preface.add(
                (Element)
                        new Paragraph(
                                "Belirtilen şartların yerine getirilmesini müteakip; 1/7/2018 tarihinden itibaren ÖKC’lerden düzenlenen ÖKC günlük kapanış (Z) raporlarına ait ilgili bilgileri  bir mali rapor olarak (www.efatura.gov.tr) internet adresinde yayınlanan “Perakende Mal Satışları ile Hizmet İfalarına İlişkin Mali Rapor Bildirim Kılavuzu”nda yapılan açıklamalara uygun olarak e-Arşiv Uyguması üzerinden GİB'e göndermeniz gerekmektedir.",
                                fontNormal));

        document.add((Element) preface);

        document.close();

        outputStream.close();

        return outputStream.toByteArray();
    }

    public static byte[] createPdf(EAPortalBasvuru form, String fileName) throws Exception {

        boolean tuzelKisi = !(form.getVkn() == null || form.getVkn().equals(""));

        STF_Helvetica_Turkish = BaseFont.createFont("Helvetica", "CP1254", false);

        fontNormal = new Font(STF_Helvetica_Turkish, 12.0F, 0);

        fontBold = new Font(STF_Helvetica_Turkish, 12.0F, 1);

        Document document = new Document();

        ByteArrayOutputStream outputStream = new ByteArrayOutputStream();

        PdfWriter.getInstance(document, outputStream);

        document.open();

        document.addTitle("Gelir İdaresi Başkanlığı");

        document.addSubject("Gelir İdaresi Başkanlığı");

        document.addAuthor("Gelir İdaresi Başkanlığı");

        document.addCreator("Gelir İdaresi Başkanlığı");

        Paragraph preface = new Paragraph();

        Paragraph tarihP = new Paragraph("Tarih : " + nowShort(), fontNormal);

        tarihP.setAlignment(0);

        preface.add((Element) tarihP);

        Paragraph sayiP = new Paragraph("Sayı : " + fileName, fontNormal);

        sayiP.setAlignment(0);

        preface.add((Element) sayiP);

        addEmptyLine(preface, 1);

        Paragraph baslikP = new Paragraph("E-Arşiv Portal Başvuru Formu", fontBold);

        baslikP.setAlignment(1);

        preface.add((Element) baslikP);

        Paragraph baslik2P = new Paragraph("Gelir İdaresi Başkanlığı", fontNormal);

        baslik2P.setAlignment(1);

        preface.add((Element) baslik2P);

        Paragraph baslik3P =
                new Paragraph("Uygulama ve Veri Yönetimi Daire Başkanlığı (III)", fontNormal);

        baslik3P.setAlignment(1);

        preface.add((Element) baslik3P);

        addEmptyLine(preface, 1);

        addEmptyLine(preface, 1);

        float[] colsWidth = {1.0F, 2.0F};

        PdfPTable table = new PdfPTable(colsWidth);

        table.setWidthPercentage(90.0F);

        table.setHorizontalAlignment(0);

        PdfPCell cell = new PdfPCell((Phrase) new Paragraph("MÜKELLEF BİLGİLERİ", fontBold));

        cell.setBackgroundColor(cellHeadarBackgroundColor);

        cell.setColspan(2);

        table.addCell(cell);

        if (form.getVkn().length() == 10) {

            table.addCell((Phrase) new Paragraph("Vergi Kimlik Numarası :", fontNormal));

        } else {

            table.addCell((Phrase) new Paragraph("TC Kimlik Numarası :", fontNormal));
        }

        table.addCell((Phrase) new Paragraph(form.getVkn(), fontNormal));

        if (form.getVkn().length() == 10) {

            table.addCell((Phrase) new Paragraph("Unvanı :", fontNormal));

        } else {

            table.addCell((Phrase) new Paragraph("Ad Soyad :", fontNormal));
        }

        if (form.getVkn().length() == 10) {

            table.addCell((Phrase) new Paragraph(form.getUnvan(), fontNormal));

        } else {

            table.addCell((Phrase) new Paragraph(form.getAd() + " " + form.getSoyad(), fontNormal));
        }

        table.addCell((Phrase) new Paragraph("Sicil No :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getSicilNo(), fontNormal));

        table.addCell((Phrase) new Paragraph("Vergi Dairesi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getVd(), fontNormal));

        table.addCell((Phrase) new Paragraph("Telefon Numarası :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getTelefon(), fontNormal));

        table.addCell((Phrase) new Paragraph("Elektronik Posta Adresi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getEposta(), fontNormal));

        table.addCell((Phrase) new Paragraph("Kanuni Merkezi :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getKmerkez(), fontNormal));

        table.addCell((Phrase) new Paragraph("Mahalle/Semt/İlçe :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getIlce(), fontNormal));

        table.addCell((Phrase) new Paragraph("İl :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getIl(), fontNormal));

        table.addCell((Phrase) new Paragraph("Ülke :", fontNormal));

        table.addCell((Phrase) new Paragraph(form.getUlke(), fontNormal));

        table.addCell((Phrase) new Paragraph("Kesilecek Belge Türü :", fontNormal));

        switch (form.getBelgeTip()) {
            case "1":
                table.addCell((Phrase) new Paragraph("E-Arşiv Fatura", fontNormal));

                break;

            case "2":
                table.addCell((Phrase) new Paragraph("Müstahsil Makbuzu", fontNormal));

                break;

            case "3":
                table.addCell((Phrase) new Paragraph("Serbest Meslek Makbuzu", fontNormal));

                break;

            case "12":
                table.addCell(
                        (Phrase) new Paragraph("E-Arşiv Fatura ve Müstahsil Makbuzu", fontNormal));

                break;
        }

        table.addCell((Phrase) new Paragraph("Uygulama Başlama Tarihi : ", fontNormal));

        SimpleDateFormat inputFormat = new SimpleDateFormat("yyyyMMdd");

        SimpleDateFormat outputFormat = new SimpleDateFormat("dd/MM/yyyy");

        Date date = new Date();

        if (form.getUygZaman().equals("0")) {

            table.addCell((Phrase) new Paragraph(outputFormat.format(date), fontNormal));

        } else {

            table.addCell(
                    (Phrase)
                            new Paragraph(
                                    outputFormat.format(inputFormat.parse(form.getUygZaman())),
                                    fontNormal));
        }

        preface.add((Element) table);

        addEmptyLine(preface, 1);

        preface.add((Element) new Paragraph("E-Arşiv Portal başvurunuz alınmıştır.", fontNormal));

        addEmptyLine(preface, 1);

        document.add((Element) preface);

        document.close();

        outputStream.close();

        return outputStream.toByteArray();
    }
}
