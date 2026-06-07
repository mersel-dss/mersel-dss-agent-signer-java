/*
 * Copyright 2026 Mersel DSS
 * SPDX-License-Identifier: Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution
 *
 * Bu dosya, "Mersel Marka Atıf Eki" ile genişletilmiş Apache Lisansı
 * sürüm 2.0 ("Lisans") altında lisanslanmıştır. Bu dosyayı yalnızca
 * Lisans ve Ek şartlarına uygun olarak kullanabilirsiniz. Lisans ve
 * Ek'in tam metni proje kök dizinindeki LICENSE dosyasındadır; temel
 * Apache Lisansı metnine
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * adresinden de ulaşabilirsiniz.
 *
 * Yürürlükteki hukuk aksini gerektirmedikçe veya yazılı olarak
 * anlaşılmadıkça, Lisans kapsamında dağıtılan yazılım "OLDUĞU GİBİ"
 * esasıyla, açık ya da örtük HİÇBİR GARANTİ veya KOŞUL OLMAKSIZIN
 * sunulur. Lisans kapsamındaki haklar ve sınırlamalar için Lisans
 * metnine bakınız.
 *
 * Mersel Marka Atıf Eki, uygulamanın kullanıcı arayüzünde render
 * edilen marka atıflarının (splash penceresindeki "MERSEL DSS" marka
 * işareti, ana pencerenin üst kısmındaki Mersel banner / logo ve
 * altbilgi satırındaki mersel.io credit'i) her dağıtımda korunmasını
 * zorunlu kılar. Detay için LICENSE 2. Madde ve TRADEMARK.md.
 */
package io.mersel.dss.agent.api.ui;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.net.BindException;
import java.util.List;
import java.util.Locale;

import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;

/**
 * Spring Boot başlatma hatalarını ({@code SpringApplication.run()} fırlatan {@link Throwable})
 * kullanıcıya gösterilebilir bir {@link StartupError}'a çevirir.
 *
 * <p>En kritik senaryo: yapılandırılan port işletim sisteminde zaten dinleniyor. Bu durumda hemen
 * her zaman ajanın <b>başka bir örneği</b> çalışıyordur; kullanıcıya teknik bir stack trace değil,
 * "uygulama zaten açık" mesajı verilmelidir. Diğer tüm hatalar generic kovaya düşer ama yine de
 * cause zinciri detaylarıyla birlikte gösterilir.
 *
 * <p>Saf (Swing'e bağımsız) ve yan etkisizdir; bu sayede headless ortamda da çağrılabilir ve birim
 * testlerle doğrulanabilir.
 */
public final class StartupErrorClassifier {

  private StartupErrorClassifier() {}

  /** {@code error} {@code null} ise generic bir "bilinmeyen hata" üretir. */
  public static StartupError classify(Throwable error) {
    Throwable portInUse = CauseChainExtractor.walk(error, StartupErrorClassifier::isPortInUse);
    if (portInUse != null) {
      return portInUseError(extractPort(portInUse), error);
    }
    return genericError(error);
  }

  /* ---------------- detection ---------------- */

  private static boolean isPortInUse(Throwable t) {
    if (t instanceof BindException) {
      return true;
    }
    String className = t.getClass().getName();
    // Spring Boot: org.springframework.boot.web.server.PortInUseException. Sınıfı doğrudan import
    // etmek yerine isimle eşliyoruz; böylece classifier embedded-server bağımlılığından kopuk kalır
    // ve test ortamında PortInUseException'ı taklit eden sahte exception'lar da yakalanır.
    if (className.endsWith("PortInUseException")) {
      return true;
    }
    String message = t.getMessage();
    if (message != null) {
      String lower = message.toLowerCase(Locale.ROOT);
      // İşletim sistemi / Tomcat / Netty katmanlarının ürettiği tipik metinler. "bind" gibi geniş
      // bir kelimeyi BİLEREK eklemiyoruz: Spring config "Failed to bind properties" hatasını
      // yanlışlıkla port-in-use sanmamak için spesifik kalıplara bağlı kalıyoruz.
      return lower.contains("address already in use")
          || lower.contains("already in use")
          || lower.contains("zaten kullanımda");
    }
    return false;
  }

  /**
   * Port numarasını yakalamaya çalışır. Spring Boot {@code PortInUseException} bir {@code
   * getPort()} metodu sunar; reflection ile okunur. Bulunamazsa mesajdaki ilk sayı denenir, o da
   * yoksa {@code -1} döner (mesajda port belirtilmez).
   */
  private static int extractPort(Throwable t) {
    try {
      Object value = t.getClass().getMethod("getPort").invoke(t);
      if (value instanceof Integer) {
        return (Integer) value;
      }
    } catch (ReflectiveOperationException | RuntimeException ignored) {
      // getPort yok ya da erişilemiyor — mesaja düş.
    }
    String message = t.getMessage();
    if (message != null) {
      java.util.regex.Matcher m = java.util.regex.Pattern.compile("(\\d{2,5})").matcher(message);
      if (m.find()) {
        try {
          return Integer.parseInt(m.group(1));
        } catch (NumberFormatException ignored) {
          // yok say
        }
      }
    }
    return -1;
  }

  /* ---------------- builders ---------------- */

  private static StartupError portInUseError(int port, Throwable error) {
    String portText = port > 0 ? (port + " portu") : "yerel servis portu";
    String message =
        "Mersel DSS Agent Signer şu anda başlatılamadı çünkü "
            + portText
            + " işletim sisteminde zaten kullanımda.\n\n"
            + "Büyük olasılıkla uygulamanın başka bir örneği zaten çalışıyor. Lütfen sistem "
            + "tepsisindeki (system tray) Mersel simgesinden mevcut örneği açın; ya da çalışan "
            + "örneği kapatıp bu uygulamayı yeniden başlatın.";
    return new StartupError(
        StartupError.Kind.PORT_IN_USE, "Uygulama Zaten Çalışıyor", message, technicalDump(error));
  }

  private static StartupError genericError(Throwable error) {
    String root = CauseChainExtractor.rootMessage(error);
    StringBuilder message = new StringBuilder();
    message.append(
        "Mersel DSS Agent Signer başlatılırken beklenmeyen bir hata oluştu ve servis "
            + "başlatılamadı.");
    if (root != null && !root.isEmpty()) {
      message.append("\n\nHata: ").append(root);
    }
    message.append(
        "\n\nLütfen uygulamayı yeniden başlatmayı deneyin. Sorun sürerse aşağıdaki teknik "
            + "ayrıntıları destek ekibiyle paylaşın.");
    return new StartupError(
        StartupError.Kind.GENERIC,
        "Uygulama Başlatılamadı",
        message.toString(),
        technicalDump(error));
  }

  /**
   * Cause zincirini okunabilir bir özet + tam stack trace olarak birleştirir. Özet, destek
   * operatörünün tek bakışta kök nedeni görmesi için; stack trace ise derin tanı için.
   */
  private static String technicalDump(Throwable error) {
    if (error == null) {
      return "(Hata ayrıntısı yok.)";
    }
    StringBuilder sb = new StringBuilder();
    List<CauseChainExtractor.Frame> frames = CauseChainExtractor.flatten(error);
    int i = 1;
    for (CauseChainExtractor.Frame f : frames) {
      sb.append(i++).append(". ").append(f.getType());
      if (f.getMessage() != null && !f.getMessage().isEmpty()) {
        sb.append(": ").append(f.getMessage());
      }
      sb.append('\n');
    }
    sb.append('\n').append(stackTraceOf(error));
    return sb.toString();
  }

  private static String stackTraceOf(Throwable error) {
    StringWriter sw = new StringWriter();
    PrintWriter pw = new PrintWriter(sw);
    error.printStackTrace(pw);
    pw.flush();
    return sw.toString();
  }
}
