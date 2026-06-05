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
package io.mersel.dss.agent.api.services.diagnostics;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import org.junit.jupiter.api.Test;

class TraceRecorderTest {

  @Test
  void recordsAndReturnsSnapshotInReverseChronologicalOrder() {
    TraceRecorder recorder = new TraceRecorder(true, 50);
    recorder.record(record("t1", 200, null, "/a"));
    recorder.record(record("t2", 200, null, "/b"));
    recorder.record(record("t3", 500, "ERR", "/c"));

    List<TraceRecord> snap = recorder.snapshot();
    assertThat(snap).hasSize(3);
    // En yeni başta
    assertThat(snap.get(0).getTraceId()).isEqualTo("t3");
    assertThat(snap.get(1).getTraceId()).isEqualTo("t2");
    assertThat(snap.get(2).getTraceId()).isEqualTo("t1");

    assertThat(recorder.getTotalRecorded()).isEqualTo(3);
    assertThat(recorder.getTotalDropped()).isEqualTo(0);
  }

  @Test
  void disabledRecorderIgnoresRecord() {
    TraceRecorder recorder = new TraceRecorder(false, 10);
    recorder.record(record("t1", 200, null, "/a"));
    assertThat(recorder.snapshot()).isEmpty();
    assertThat(recorder.getTotalRecorded()).isZero();
    assertThat(recorder.isEnabled()).isFalse();
  }

  @Test
  void toggleEnabledStartsAndStopsRecording() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(record("t1", 200, null, "/a"));

    boolean previous = recorder.setEnabled(false);
    assertThat(previous).isTrue();

    recorder.record(record("t2", 200, null, "/b"));
    assertThat(recorder.snapshot()).hasSize(1);
    assertThat(recorder.snapshot().get(0).getTraceId()).isEqualTo("t1");

    recorder.setEnabled(true);
    recorder.record(record("t3", 200, null, "/c"));
    assertThat(recorder.snapshot()).hasSize(2);
    assertThat(recorder.snapshot().get(0).getTraceId()).isEqualTo("t3");
  }

  @Test
  void ringBufferEvictsOldestWhenAtCapacity() {
    TraceRecorder recorder = new TraceRecorder(true, 3);
    recorder.record(record("t1", 200, null, "/a"));
    recorder.record(record("t2", 200, null, "/b"));
    recorder.record(record("t3", 200, null, "/c"));
    recorder.record(record("t4", 200, null, "/d"));

    List<TraceRecord> snap = recorder.snapshot();
    assertThat(snap).hasSize(3);
    assertThat(snap.get(0).getTraceId()).isEqualTo("t4");
    assertThat(snap.get(1).getTraceId()).isEqualTo("t3");
    assertThat(snap.get(2).getTraceId()).isEqualTo("t2");
    assertThat(recorder.getTotalDropped()).isEqualTo(1);
  }

  @Test
  void snapshotWithLimitAndErrorOnlyFiltersCorrectly() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(record("t1", 200, null, "/ok"));
    recorder.record(record("t2", 500, "X", "/bad"));
    recorder.record(record("t3", 200, null, "/ok2"));
    recorder.record(record("t4", 401, "AUTH", "/bad2"));

    List<TraceRecord> errorOnly = recorder.snapshot(0, true);
    assertThat(errorOnly).extracting(TraceRecord::getTraceId).containsExactly("t4", "t2");

    List<TraceRecord> limited = recorder.snapshot(2, false);
    assertThat(limited).hasSize(2);
    assertThat(limited.get(0).getTraceId()).isEqualTo("t4");
    assertThat(limited.get(1).getTraceId()).isEqualTo("t3");
  }

  @Test
  void clearWipesBufferButKeepsCounters() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.record(record("t1", 200, null, "/a"));
    recorder.record(record("t2", 200, null, "/b"));
    long total = recorder.getTotalRecorded();

    recorder.clear();
    assertThat(recorder.currentSize()).isZero();
    assertThat(recorder.snapshot()).isEmpty();
    assertThat(recorder.getTotalRecorded()).isEqualTo(total);
  }

  @Test
  void listenerIsInvokedForEachRecord() throws InterruptedException {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    AtomicInteger count = new AtomicInteger();
    CountDownLatch latch = new CountDownLatch(3);

    recorder.addListener(
        r -> {
          count.incrementAndGet();
          latch.countDown();
        });

    recorder.record(record("a", 200, null, "/"));
    recorder.record(record("b", 200, null, "/"));
    recorder.record(record("c", 200, null, "/"));

    assertThat(latch.await(2, TimeUnit.SECONDS)).isTrue();
    assertThat(count.get()).isEqualTo(3);
  }

  @Test
  void listenerExceptionDoesNotBreakRecording() {
    TraceRecorder recorder = new TraceRecorder(true, 10);
    recorder.addListener(
        r -> {
          throw new RuntimeException("boom");
        });
    AtomicInteger okCount = new AtomicInteger();
    recorder.addListener(r -> okCount.incrementAndGet());

    recorder.record(record("a", 200, null, "/"));
    recorder.record(record("b", 200, null, "/"));

    assertThat(recorder.snapshot()).hasSize(2);
    assertThat(okCount.get()).isEqualTo(2);
  }

  @Test
  void sanitiseQueryMasksSensitiveKeys() {
    String out =
        TraceRecord.Builder.sanitiseQuery(
            "terminalName=ATR&pin=1234&password=abc&pwd=zzz&apikey=k&cardType=AKIS");
    assertThat(out).contains("terminalName=ATR");
    assertThat(out).contains("cardType=AKIS");
    assertThat(out).contains("pin=***");
    assertThat(out).contains("password=***");
    assertThat(out).contains("pwd=***");
    assertThat(out).contains("apikey=***");
    assertThat(out).doesNotContain("1234");
    assertThat(out).doesNotContain("abc");
  }

  @Test
  void anonymiseIpKeepsLoopbackMasksOthers() {
    assertThat(TraceRecord.Builder.anonymiseIp("127.0.0.1")).isEqualTo("127.0.0.1");
    assertThat(TraceRecord.Builder.anonymiseIp("::1")).isEqualTo("::1");
    assertThat(TraceRecord.Builder.anonymiseIp("192.168.1.42")).isEqualTo("192.168.1.***");
    assertThat(TraceRecord.Builder.anonymiseIp("2001:db8::a")).isEqualTo("2001:db8::***");
  }

  private static TraceRecord record(String traceId, int status, String errorCode, String path) {
    return TraceRecord.builder()
        .traceId(traceId)
        .method("GET")
        .path(path)
        .statusCode(status)
        .errorCode(errorCode)
        .durationMs(12)
        .build();
  }
}
