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
package io.mersel.dss.agent.api.models;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * GİB {@code basvuruSorgula} servisinin dönüş tipi.
 *
 * <p>Eski projedeki {@code GIBApplicationQueryResponse} ({@code message}, {@code status}, {@code
 * result}) ile birebir uyumlu — alan adları o servisin gerçek shape'ini yansıtır. Servis VKN/TCKN
 * bulunamadığı veya validasyon hatası verdiği durumlarda obje değil **JSON-encoded bir string**
 * dönebildiği için controller tarafında {@code TextNode} fallback'i uygulanır: o senaryoda raw
 * string {@link #message}'a yazılır, {@link #status} {@code "ERROR"} olur ve {@link #rawResponse}
 * ham gövdeyi taşır.
 *
 * <p>Bilinmeyen alanlar (servis schema'sı zaman zaman genişler) {@link JsonIgnoreProperties} ile
 * sessizce yutulur.
 */
@Schema(description = "GİB e-Fatura başvuru sorgu sonucu")
@JsonIgnoreProperties(ignoreUnknown = true)
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GibApplicationQueryResponse {

  @Schema(description = "Servis mesajı / açıklama.")
  private String message;

  @Schema(description = "Servis durumu (örn. OK, ERROR).")
  private String status;

  @Schema(description = "Servisin döndürdüğü sonuç alanı (örn. mükellef özet bilgisi).")
  private String result;

  @Schema(
      description =
          "Çağrı başarılı oldu mu? GIB HTTP başarısız döndüğünde veya gövde parse edilemediğinde"
              + " false.")
  private boolean success;

  @Schema(description = "Tanı amaçlı: GİB'den dönen ham yanıt gövdesi.")
  private String rawResponse;

  public GibApplicationQueryResponse() {}

  public String getMessage() {
    return message;
  }

  public void setMessage(String message) {
    this.message = message;
  }

  public String getStatus() {
    return status;
  }

  public void setStatus(String status) {
    this.status = status;
  }

  public String getResult() {
    return result;
  }

  public void setResult(String result) {
    this.result = result;
  }

  public boolean isSuccess() {
    return success;
  }

  public void setSuccess(boolean success) {
    this.success = success;
  }

  public String getRawResponse() {
    return rawResponse;
  }

  public void setRawResponse(String rawResponse) {
    this.rawResponse = rawResponse;
  }
}
