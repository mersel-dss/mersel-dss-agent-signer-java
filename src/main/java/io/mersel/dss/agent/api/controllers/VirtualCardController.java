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
package io.mersel.dss.agent.api.controllers;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import io.mersel.dss.agent.api.dtos.RegisterPkcs11VirtualCardDto;
import io.mersel.dss.agent.api.exceptions.SignatureOperationException;
import io.mersel.dss.agent.api.models.VirtualCardResponse;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualToken;
import io.mersel.dss.agent.api.services.virtualtoken.VirtualTokenRegistry;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Kart takılı olmasa bile imza atabilmek için sanal kart ("Dummy Card") tanımlama uçları.
 *
 * <p>Tanımlanan kaynak ({@code PKCS#11} lib veya {@code PKCS#12} PFX) {@code GET /smartcard}
 * çıktısında normal bir kart gibi görünür; sertifika listeleme ve imzalama uçları {@code
 * terminalName} olarak buradaki {@code name}'i alır.
 *
 * <p>Kayıtlar <b>bellekte</b> tutulur; agent kapanınca silinir. PKCS#12 parolası tanım anında bir
 * kez alınır, bellekte saklanır ve listeleme + imzada kullanılır.
 */
@RestController
@Tag(
    name = "Sanal Kart",
    description =
        "Kart takılı olmasa bile PKCS#11 (HSM) veya PKCS#12 (PFX) üzerinden sertifika gösterip imza"
            + " atabilmek için sanal kart tanımları.")
public class VirtualCardController {

  private final VirtualTokenRegistry registry;

  public VirtualCardController(VirtualTokenRegistry registry) {
    this.registry = registry;
  }

  @Operation(
      summary = "PKCS#11 (HSM / yüklü sürücü) sanal kart tanımlar.",
      description =
          "Verilen library yolu diskte doğrulanır ve bir 'Dummy Card' olarak kaydedilir. İmza ve"
              + " listeleme bu kütüphane üzerinden, fiziksel kart aranmadan yapılır.")
  @PostMapping(
      value = "/smartcard/virtual/pkcs11",
      consumes = MediaType.APPLICATION_JSON_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VirtualCardResponse> registerPkcs11(
      @Valid @RequestBody RegisterPkcs11VirtualCardDto body) {
    VirtualToken token = registry.registerPkcs11(body.getName(), body.getLibraryPath());
    return ResponseEntity.ok(VirtualCardResponse.from(token));
  }

  @Operation(
      summary = "PKCS#12 (PFX) sanal kart tanımlar.",
      description =
          "Yüklenen PFX dosyası parolayla açılıp doğrulanır ve bir 'Dummy Card' olarak kaydedilir."
              + " Parola bellekte saklanır; sertifika listeleme ve imzalama bu parolayı kullanır,"
              + " imza uçlarındaki `pin` alanı bu kart için yok sayılır.")
  @PostMapping(
      value = "/smartcard/virtual/pkcs12",
      consumes = MediaType.MULTIPART_FORM_DATA_VALUE,
      produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<VirtualCardResponse> registerPkcs12(
      @Parameter(description = "Sanal kartın benzersiz adı (terminalName).", required = true)
          @RequestParam("name")
          @NotBlank
          String name,
      @Parameter(description = "PFX / PKCS#12 dosyası.", required = true) @RequestParam("file")
          MultipartFile file,
      @Parameter(description = "PFX parolası.", required = true) @RequestParam("password")
          String password) {
    if (file == null || file.isEmpty()) {
      throw new IllegalArgumentException("PFX dosyası ('file') zorunludur.");
    }
    byte[] pfxBytes;
    try {
      pfxBytes = file.getBytes();
    } catch (IOException e) {
      throw new SignatureOperationException("PFX dosyası okunamadı: " + e.getMessage(), e);
    }
    char[] pw = password == null ? new char[0] : password.toCharArray();
    try {
      VirtualToken token =
          registry.registerPkcs12(name, pfxBytes, pw, file.getOriginalFilename());
      return ResponseEntity.ok(VirtualCardResponse.from(token));
    } finally {
      java.util.Arrays.fill(pw, '\0');
    }
  }

  @Operation(summary = "Tanımlı sanal kartları listeler (parola yansıtılmaz).")
  @GetMapping(value = "/smartcard/virtual", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<List<VirtualCardResponse>> list() {
    List<VirtualCardResponse> out = new ArrayList<VirtualCardResponse>();
    for (VirtualToken t : registry.list()) {
      out.add(VirtualCardResponse.from(t));
    }
    return ResponseEntity.ok(out);
  }

  @Operation(summary = "Bir sanal kartı kaldırır ve (PFX ise) parolasını bellekten siler.")
  @DeleteMapping(value = "/smartcard/virtual/{name}", produces = MediaType.APPLICATION_JSON_VALUE)
  public ResponseEntity<Void> remove(
      @Parameter(description = "Sanal kart adı.", required = true) @PathVariable("name")
          String name) {
    VirtualToken removed = registry.remove(name);
    if (removed == null) {
      return ResponseEntity.notFound().build();
    }
    return ResponseEntity.noContent().build();
  }
}
