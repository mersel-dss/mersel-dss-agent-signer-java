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

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import io.mersel.dss.agent.api.services.update.UpdateInfo;
import io.mersel.dss.agent.api.services.update.UpdateService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * GitHub Releases tabanlı güncelleme kontrolü REST uçları.
 *
 * <p>Asla 4xx/5xx dönmez — kontrol başarısız olursa {@code upToDate(current)} veya {@code
 * disabled()} ile 200 döner; istemci hata yutması yerine state-machine gibi okur.
 */
@RestController
@Tag(
    name = "Güncelleme",
    description = "GitHub Releases üzerinden yeni sürüm kontrolü ve indirme URL'i.")
public class UpdateController {

  private final UpdateService updateService;

  public UpdateController(UpdateService updateService) {
    this.updateService = updateService;
  }

  @Operation(
      summary = "Mevcut güncelleme durumunu döner (cache'li).",
      description =
          "ETag/conditional-GET ile GitHub'a fazla yük bindirmez. Daemon hazır olduktan sonra"
              + " arka planda zaten bir kez sorgulamış olabilir; sonuç cache'lenmiştir.")
  @GetMapping("/update/status")
  public ResponseEntity<UpdateInfo> status() {
    return ResponseEntity.ok(updateService.currentStatus(false));
  }

  @Operation(
      summary = "Güncelleme kontrolünü zorla tetikler (cache bypass).",
      description =
          "ETag/conditional-GET cache'i atlar; her zaman GitHub'a yeni istek atar. UI 'Yenile'"
              + " butonu için.")
  @PostMapping("/update/check")
  public ResponseEntity<UpdateInfo> check() {
    return ResponseEntity.ok(updateService.currentStatus(true));
  }
}
