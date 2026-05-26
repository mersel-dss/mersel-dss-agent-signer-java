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

import java.awt.AlphaComposite;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Component;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.LinearGradientPaint;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.font.TextAttribute;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Map;

import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JComponent;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JWindow;
import javax.swing.SwingUtilities;
import javax.swing.Timer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Spring Boot başlama gecikmesini örtmek için gösterilen modern, tipografi-merkezli splash window.
 *
 * <p>Headless ortamlarda ({@code java.awt.headless=true} veya {@link
 * GraphicsEnvironment#isHeadless()}) {@link #show(String)} no-op'tur — sunucu/CI/Docker'da hata
 * vermeden geçer. Tüm görsel öğeler (gradient arka plan, brand accent çubuğu, animasyonlu marquee
 * progress bar) custom-painted; raster logo dosyalarına bağımlılık yoktur. Bu sayede klasik ImageIO
 * scale artefaktları (blur, kenar bozulması) ortadan kalkar; pencere her ekran DPI'sinde keskin
 * görünür.
 *
 * <p>Window dekorasyonsuzdur (taskbar / minimize butonu yok), ekranın ortasına gelir ve {@link
 * #close()} çağrılana kadar görünür kalır. Indeterminate marquee progress bar kendi {@link Timer}
 * ile çalışır; macOS Aqua LAF'ın ince {@code JProgressBar}'ı statik çizme bug'ından etkilenmez.
 */
public final class SplashWindow {

  private static final Logger LOG = LoggerFactory.getLogger(SplashWindow.class);
  private static final int WIDTH = 460;
  private static final int HEIGHT = 240;
  private static final int CORNER_RADIUS = 14;
  private static final int PROGRESS_BAR_HEIGHT = 6;

  // Dark, modern paleti — splash kısa süre görüldüğü için brand hissi yüksek, sofistike bir
  // arkaplan tercih edildi. Renkler Tailwind slate + blue/indigo skalasından seçildi; UI'da brand
  // bütünlüğü için MainWindow'un accent mavisi (~blue-500/600) ile aynı tonda kalır.
  private static final Color BG_TOP = new Color(15, 23, 42); // slate-900
  private static final Color BG_BOTTOM = new Color(2, 6, 23); // slate-950
  private static final Color BORDER = new Color(51, 65, 85); // slate-700
  private static final Color BORDER_INNER = new Color(255, 255, 255, 14); // 1 px highlight stroke
  private static final Color BRAND_TINT = new Color(96, 165, 250); // blue-400
  private static final Color ACCENT_FROM = new Color(59, 130, 246); // blue-500
  private static final Color ACCENT_TO = new Color(99, 102, 241); // indigo-500
  private static final Color TITLE = new Color(248, 250, 252); // slate-50
  private static final Color SUBTITLE = new Color(148, 163, 184); // slate-400
  private static final Color FOOTER = new Color(100, 116, 139); // slate-500
  private static final Color TRACK = new Color(30, 41, 59); // slate-800

  private JWindow window;
  private MarqueeBar marquee;

  /** Splash'i EDT üzerinde gösterir; çağıran thread bloklanmaz. */
  public void show(String version) {
    if (GraphicsEnvironment.isHeadless()) {
      LOG.debug("Splash atlandı: headless ortam.");
      return;
    }
    try {
      // Splash'i SENKRON kuruyoruz; aksi takdirde Spring Boot başlangıcı splash'i geçebilir ve
      // pencere hiç görünmez. invokeAndWait küçük overhead getirir (< 50 ms) ama UX kararlı.
      SwingUtilities.invokeAndWait(() -> buildAndShow(version));
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.warn("Splash gösterimi kesildi: {}", ie.getMessage());
    } catch (InvocationTargetException ite) {
      // EDT'de runtime exception olursa: log + devam et. Splash kritik değil, daemon çalışmalı.
      LOG.warn("Splash oluşturulamadı: {}", ite.getTargetException().toString());
    }
  }

  private void buildAndShow(String version) {
    window = new JWindow();
    window.setSize(new Dimension(WIDTH, HEIGHT));
    // Pencere arka planını şeffaf yapıp tüm dolguyu GradientPanel'e bıraktığımız için border'lar
    // rounded rectangle olarak çizilebiliyor. Bazı LAF/platform kombinasyonlarında alpha background
    // desteklenmeyebilir; o durumda fallback olarak en alt arka plan koyu kalır.
    try {
      window.setBackground(new Color(0, 0, 0, 0));
    } catch (UnsupportedOperationException ignored) {
      window.setBackground(BG_BOTTOM);
    }

    GradientPanel root = new GradientPanel();
    root.setLayout(new BoxLayout(root, BoxLayout.Y_AXIS));
    root.setOpaque(false);
    // Brand kapsülü içeriği nefes alsın diye geniş iç padding. Üst padding alttan biraz daha fazla;
    // optik denge için (alt footer kendi padding'ini taşıyor).
    root.setBorder(javax.swing.BorderFactory.createEmptyBorder(34, 36, 22, 36));

    // ====================================================================
    // LEGAL NOTICE — Brand Notice (LICENSE Ek 2(a))
    // ====================================================================
    // Aşağıdaki üç bileşen (brand etiketi "MERSEL DSS", AccentBar ve
    // "Agent Signer" başlığı) LICENSE dosyasındaki Mersel Brand Attribution
    // Addendum'un 2. Maddesi (a) bendinde tanımlanan "Marka Atfı / Brand
    // Notice"tır. Bu bileşenler HER kullanım biçiminde (dahili kullanım,
    // ticari satış, kapalı kaynağa entegrasyon, SaaS, fork, compound
    // adlandırma vb.) görünür, okunabilir ve işlevsel olarak eşdeğer
    // biçimde KORUNMAK ZORUNDADIR. Yeniden adlandırma yoluyla atıfları
    // silme istisnası YOKTUR. Yazılım'ı değiştirip dağıtan taraflar
    // ayrıca Addendum § 3 (Modifikasyon Bildirimi) ve § 4 (Onay Beyanı
    // Yasağı) yükümlülüklerine de tabidir. Detay: LICENSE ve TRADEMARK.md.
    // ====================================================================
    JLabel brand = new JLabel("MERSEL DSS");
    brand.setForeground(BRAND_TINT);
    brand.setFont(letterSpacedFont(Font.BOLD, 11f, 0.22f));
    brand.setAlignmentX(Component.CENTER_ALIGNMENT);
    root.add(brand);
    root.add(Box.createVerticalStrut(8));

    AccentBar accent = new AccentBar(48, 3);
    accent.setAlignmentX(Component.CENTER_ALIGNMENT);
    root.add(accent);
    root.add(Box.createVerticalStrut(16));

    JLabel title = new JLabel("Agent Signer");
    title.setForeground(TITLE);
    title.setFont(deriveFont(Font.BOLD, 22f));
    title.setAlignmentX(Component.CENTER_ALIGNMENT);
    root.add(title);
    root.add(Box.createVerticalStrut(6));
    // ==================== /LEGAL NOTICE ====================

    JLabel subtitle = new JLabel("Yerel imzalama servisi hazırlanıyor…");
    subtitle.setForeground(SUBTITLE);
    subtitle.setFont(deriveFont(Font.PLAIN, 12f));
    subtitle.setAlignmentX(Component.CENTER_ALIGNMENT);
    root.add(subtitle);
    root.add(Box.createVerticalStrut(26));

    marquee = new MarqueeBar(WIDTH - 120, PROGRESS_BAR_HEIGHT);
    marquee.setAlignmentX(Component.CENTER_ALIGNMENT);
    root.add(marquee);

    root.add(Box.createVerticalGlue());

    JLabel footer = new JLabel("v" + safe(version));
    footer.setForeground(FOOTER);
    footer.setFont(deriveFont(Font.PLAIN, 11f));
    footer.setAlignmentX(Component.CENTER_ALIGNMENT);
    root.add(footer);

    window.setContentPane(root);
    window.setLocationRelativeTo(null);
    window.setAlwaysOnTop(true);
    window.setVisible(true);
  }

  /** Splash'i kapatır + kaynakları (Timer dahil) serbest bırakır. Idempotent. */
  public void close() {
    if (window == null) {
      return;
    }
    Runnable closeOp =
        () -> {
          try {
            if (marquee != null) {
              marquee.stopAnimation();
            }
            window.setVisible(false);
            window.dispose();
          } finally {
            window = null;
            marquee = null;
          }
        };
    if (SwingUtilities.isEventDispatchThread()) {
      closeOp.run();
    } else {
      SwingUtilities.invokeLater(closeOp);
    }
  }

  /** Spinner'ı durdurmadan kullanıcıya yapısal bir hata mesajı göstermek için (opsiyonel). */
  public Toolkit toolkit() {
    return Toolkit.getDefaultToolkit();
  }

  /* ==================== custom painted components ==================== */

  /**
   * Vertical gradient + rounded rectangle clip ile çizilmiş arka plan. İç ve dış olmak üzere iki
   * çizgi stroke'u: dış (slate-700) silüet için, iç (beyaz alpha) cam-kenarı highlight için —
   * pencere üst sınırına ince bir parlama verir; dark zeminde profesyonel "device chrome" hissini
   * yakalar.
   */
  private static final class GradientPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setRenderingHint(
            RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        int w = getWidth();
        int h = getHeight();

        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, h, BG_BOTTOM));
        g2.fillRoundRect(0, 0, w, h, CORNER_RADIUS * 2, CORNER_RADIUS * 2);

        // İnce mavi parıltı: pencere tepesinden ~80 px aşağıya doğru sönen radial-ish glow.
        // GradientPaint dikey, alpha ile birlikte verildiği için radial maliyetinden kaçınıyoruz.
        g2.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, 0.10f));
        g2.setPaint(
            new GradientPaint(
                0,
                0,
                ACCENT_FROM,
                0,
                100,
                new Color(ACCENT_FROM.getRed(), ACCENT_FROM.getGreen(), ACCENT_FROM.getBlue(), 0)));
        g2.fillRoundRect(0, 0, w, 100, CORNER_RADIUS * 2, CORNER_RADIUS * 2);
        g2.setComposite(AlphaComposite.SrcOver);

        // Dış silüet
        g2.setStroke(new BasicStroke(1f));
        g2.setColor(BORDER);
        g2.drawRoundRect(0, 0, w - 1, h - 1, CORNER_RADIUS * 2, CORNER_RADIUS * 2);

        // İç highlight — üst kenarın ilk birkaç pikselinde cam hissi
        g2.setColor(BORDER_INNER);
        g2.drawRoundRect(1, 1, w - 3, h - 3, CORNER_RADIUS * 2 - 2, CORNER_RADIUS * 2 - 2);
      } finally {
        g2.dispose();
      }
    }
  }

  /** 48x3 px rounded mini line — brand accent ayraç çubuğu. */
  private static final class AccentBar extends JComponent {
    private static final long serialVersionUID = 1L;
    private final int barW;
    private final int barH;

    AccentBar(int barW, int barH) {
      this.barW = barW;
      this.barH = barH;
      Dimension d = new Dimension(barW, barH);
      setPreferredSize(d);
      setMinimumSize(d);
      setMaximumSize(d);
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, ACCENT_FROM, barW, 0, ACCENT_TO));
        g2.fillRoundRect(0, 0, barW, barH, barH, barH);
      } finally {
        g2.dispose();
      }
    }
  }

  /**
   * Custom indeterminate progress bar. Track üzerinde sürekli ileri kayan bir gradient şerit.
   *
   * <p>Java {@link javax.swing.JProgressBar}'ı tercih etmedik çünkü macOS Aqua LAF'ı 6 px gibi ince
   * yüksekliklerde indeterminate animasyonu durdurup statik gri çubuk çiziyor (mevcut splash kodu
   * bu sorunla zaten yorum satırlarında karşılaşmış). Kendi {@link Timer}'lı çizimimiz bu
   * davranıştan tamamen bağımsız ve cross-platform kararlıdır.
   */
  private static final class MarqueeBar extends JComponent {
    private static final long serialVersionUID = 1L;

    private static final int FRAME_INTERVAL_MS = 16; // ~60 fps
    private static final int STEP_PX = 3;

    private final int barW;
    private final int barH;
    private final int sliderW;
    private final Timer timer;

    private int position;

    MarqueeBar(int barW, int barH) {
      this.barW = barW;
      this.barH = barH;
      this.sliderW = Math.max(60, barW / 3);
      this.position = -sliderW;
      Dimension d = new Dimension(barW, barH);
      setPreferredSize(d);
      setMinimumSize(d);
      setMaximumSize(d);

      timer =
          new Timer(
              FRAME_INTERVAL_MS,
              e -> {
                position += STEP_PX;
                if (position > barW) {
                  position = -sliderW;
                }
                repaint();
              });
      timer.setCoalesce(true);
    }

    @Override
    public void addNotify() {
      super.addNotify();
      // Pencereye gerçekten asıldığında animasyonu başlat. removeNotify'da güvenle durdurulur;
      // close() çağrısı da ek olarak stopAnimation() ile garanti altına alır.
      timer.start();
    }

    @Override
    public void removeNotify() {
      timer.stop();
      super.removeNotify();
    }

    void stopAnimation() {
      timer.stop();
    }

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

        // Track
        g2.setColor(TRACK);
        g2.fillRoundRect(0, 0, barW, barH, barH, barH);

        // Slider — clip to track shape so gradient slider rounded ends stay inside track.
        g2.setClip(new java.awt.geom.RoundRectangle2D.Float(0, 0, barW, barH, barH, barH));

        // Soft edge: gradient'i şeffaf → opak → şeffaf yaparak track içinde yumuşak bir parıltı
        // hissi yakalıyoruz. Sert kenarlı blok yerine daha "premium" görünür.
        int x0 = position;
        int x1 = position + sliderW;
        Color transparent =
            new Color(ACCENT_FROM.getRed(), ACCENT_FROM.getGreen(), ACCENT_FROM.getBlue(), 0);
        LinearGradientPaint paint =
            new LinearGradientPaint(
                x0,
                0,
                x1,
                0,
                new float[] {0.0f, 0.5f, 1.0f},
                new Color[] {transparent, ACCENT_FROM, transparent});
        g2.setPaint(paint);
        g2.fillRect(x0, 0, sliderW, barH);

        // Slider'ın merkezine ufak bir indigo çekirdek — accent_to ile geçiş hissi ver.
        LinearGradientPaint corePaint =
            new LinearGradientPaint(
                x0 + sliderW / 4,
                0,
                x0 + (sliderW * 3) / 4,
                0,
                new float[] {0.0f, 1.0f},
                new Color[] {ACCENT_FROM, ACCENT_TO});
        g2.setPaint(corePaint);
        g2.fillRect(x0 + sliderW / 4, 0, sliderW / 2, barH);
      } finally {
        g2.dispose();
      }
    }
  }

  /* ==================== font helpers ==================== */

  private static Font deriveFont(int style, float size) {
    // Sistem default'ı; "Verdana"/"Segoe UI" hard-code edilmez — macOS/Linux'ta düşmesin diye.
    Font base = new JLabel().getFont();
    return base.deriveFont(style, size);
  }

  /**
   * Tracking (letter-spacing) uygulanmış font. {@link TextAttribute#TRACKING} 1.0 = font boyutu
   * kadar boşluk; küçük değerler (0.1 - 0.3) tipografide "small caps brand" hissi yaratır.
   */
  private static Font letterSpacedFont(int style, float size, float tracking) {
    Font base = deriveFont(style, size);
    Map<TextAttribute, Object> attrs = Collections.singletonMap(TextAttribute.TRACKING, tracking);
    return base.deriveFont(attrs);
  }

  private static String safe(String v) {
    return (v == null || v.isEmpty()) ? "0.0.0" : v;
  }
}
