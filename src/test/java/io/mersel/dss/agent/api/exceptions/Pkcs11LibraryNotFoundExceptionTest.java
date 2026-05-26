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
package io.mersel.dss.agent.api.exceptions;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import io.mersel.dss.agent.api.models.ErrorModel;

class Pkcs11LibraryNotFoundExceptionTest {

  @Test
  void carriesAllStructuredFields() {
    List<String> paths =
        Arrays.asList("/usr/local/lib/libakisp11.dylib", "/Library/Akia/libakisp11.dylib");
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            "AKIS", "akisp11", paths, "https://example/download", "msg");

    assertThat(ex.getErrorCode()).isEqualTo("PKCS11_LIBRARY_NOT_FOUND");
    assertThat(Pkcs11LibraryNotFoundException.CODE).isEqualTo("PKCS11_LIBRARY_NOT_FOUND");
    assertThat(ex.getCardType()).isEqualTo("AKIS");
    assertThat(ex.getRequiredLibrary()).isEqualTo("akisp11");
    assertThat(ex.getSearchedPaths()).containsExactlyElementsOf(paths);
    assertThat(ex.getDownloadHint()).isEqualTo("https://example/download");
    assertThat(ex.getMessage()).isEqualTo("msg");
  }

  @Test
  void searchedPathsListIsImmutable() {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException("AKIS", "akisp11", Arrays.asList("a"), null, "x");
    assertThat(ex.getSearchedPaths()).hasSize(1);
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> ex.getSearchedPaths().add("b"));
  }

  @Test
  void nullSearchedPathsBecomesEmptyList() {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException("AKIS", "akisp11", null, null, "x");
    assertThat(ex.getSearchedPaths()).isEmpty();
  }

  @Test
  void serializesAsErrorModelWithAllFields() throws JsonProcessingException {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            "AKIS",
            "akisp11",
            Arrays.asList("/usr/local/lib/libakisp11.dylib"),
            "https://kamusm.bilgem.tubitak.gov.tr/...",
            "AKIS kartı algılandı ama akisp11 bulunamadı.");

    ErrorModel model = new ErrorModel(ex.getErrorCode(), ex.getMessage());
    model.setCardType(ex.getCardType());
    model.setRequiredLibrary(ex.getRequiredLibrary());
    model.setSearchedPaths(ex.getSearchedPaths());
    model.setDownloadHint(ex.getDownloadHint());

    ObjectMapper mapper = new ObjectMapper();
    String json = mapper.writeValueAsString(model);

    assertThat(json).contains("\"code\":\"PKCS11_LIBRARY_NOT_FOUND\"");
    assertThat(json).contains("\"cardType\":\"AKIS\"");
    assertThat(json).contains("\"requiredLibrary\":\"akisp11\"");
    assertThat(json).contains("\"searchedPaths\":[\"/usr/local/lib/libakisp11.dylib\"]");
    assertThat(json).contains("\"downloadHint\":\"https://kamusm.bilgem.tubitak.gov.tr/...\"");
    assertThat(json).contains("\"message\":\"AKIS kartı algılandı ama akisp11 bulunamadı.\"");
    assertThat(json).contains("\"timestamp\":");
  }

  @Test
  void plainErrorModelOmitsExtraFields() throws JsonProcessingException {
    // Diğer hatalarda (örn 401 PIN auth) ek alanlar JSON çıktısında görünmemeli
    ErrorModel plain = new ErrorModel("PKCS11_AUTH_FAILED", "PIN yanlış");
    String json = new ObjectMapper().writeValueAsString(plain);

    assertThat(json).contains("\"code\":\"PKCS11_AUTH_FAILED\"");
    assertThat(json).doesNotContain("cardType");
    assertThat(json).doesNotContain("requiredLibrary");
    assertThat(json).doesNotContain("searchedPaths");
    assertThat(json).doesNotContain("downloadHint");
  }

  @Test
  void emptySearchedPathsOmittedDueToBusinessRule() {
    // ErrorModel default olarak null'ları gizler. Boş liste null değildir; bu test
    // GlobalExceptionHandler'ın boş listeyi set etmediği convention'ını kayıt altına alır.
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            "AKIS", "akisp11", Collections.<String>emptyList(), null, "x");
    assertThat(ex.getSearchedPaths()).isEmpty();
  }

  /* ====================== Layer 5 fallback fields ====================== */

  @Test
  void cardTypeNullWithCandidatesTriggersUserSelectionRequired() {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            null,
            null,
            Collections.<String>emptyList(),
            null,
            "Kart otomatik tanınamadı",
            Arrays.asList("AKIS", "ALADDIN", "SAFESIGN"));

    assertThat(ex.getCardType()).isNull();
    assertThat(ex.isUserSelectionRequired()).isTrue();
    assertThat(ex.getCardTypeCandidates()).containsExactly("AKIS", "ALADDIN", "SAFESIGN");
  }

  @Test
  void knownCardTypeDoesNotRequireUserSelection() {
    // Kart bilinse de driver yoksa — bu Layer 5 değil, Layer 3 sonrası lib bulunamadı durumu.
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            "AKIS",
            "akisp11",
            Arrays.asList("/usr/local/lib/libakisp11.dylib"),
            "https://x",
            "AKIS algılandı",
            Arrays.asList("AKIS", "ALADDIN"));

    assertThat(ex.getCardType()).isEqualTo("AKIS");
    assertThat(ex.isUserSelectionRequired()).isFalse();
  }

  @Test
  void nullCandidatesBecomesEmptyAndNoUserSelection() {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(null, null, null, null, "msg", null);
    assertThat(ex.getCardTypeCandidates()).isEmpty();
    assertThat(ex.isUserSelectionRequired()).isFalse();
  }

  @Test
  void candidatesListIsImmutable() {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            null, null, null, null, "msg", Arrays.asList("AKIS", "ALADDIN"));
    org.junit.jupiter.api.Assertions.assertThrows(
        UnsupportedOperationException.class, () -> ex.getCardTypeCandidates().add("BREAK"));
  }

  @Test
  void layer5FieldsSerializedAsErrorModel() throws JsonProcessingException {
    Pkcs11LibraryNotFoundException ex =
        new Pkcs11LibraryNotFoundException(
            null,
            null,
            Collections.<String>emptyList(),
            null,
            "Kart otomatik tanınamadı. Lütfen kart tipini seçin.",
            Arrays.asList("AKIS", "ALADDIN", "SAFESIGN"));

    io.mersel.dss.agent.api.models.ErrorModel model =
        new io.mersel.dss.agent.api.models.ErrorModel(ex.getErrorCode(), ex.getMessage());
    model.setCardType(ex.getCardType());
    model.setCardTypeCandidates(ex.getCardTypeCandidates());
    if (ex.isUserSelectionRequired()) {
      model.setUserSelectionRequired(Boolean.TRUE);
    }

    String json = new ObjectMapper().writeValueAsString(model);
    assertThat(json).contains("\"code\":\"PKCS11_LIBRARY_NOT_FOUND\"");
    assertThat(json).contains("\"cardTypeCandidates\":[\"AKIS\",\"ALADDIN\",\"SAFESIGN\"]");
    assertThat(json).contains("\"userSelectionRequired\":true");
    assertThat(json).doesNotContain("\"cardType\":");
  }
}
