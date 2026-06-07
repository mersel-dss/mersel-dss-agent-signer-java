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

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.Clipboard;
import java.awt.datatransfer.StringSelection;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.lang.reflect.InvocationTargetException;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JComponent;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.ScrollPaneConstants;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Başlatma (Spring Boot {@code app.run()}) başarısız olduğunda gösterilen hata penceresi. {@link
 * SplashWindow} ile aynı koyu paleti paylaşır ama danger (kırmızı) accent'le boot dizisinin
 * "başarısız" hâli olduğunu belli eder.
 *
 * <p>Neden gerekli: splash, {@code ApplicationReadyEvent} dinleyen {@link DesktopUiBootstrap}
 * tarafından kapatılır. Başlatma o event'e ulaşamadan patlarsa (örn. port zaten dinleniyorsa)
 * splash sonsuza dek ekranda kalır <b>ve</b> görünür AWT penceresi non-daemon thread'i canlı
 * tuttuğu için JVM hiç sonlanmaz. Bu pencere splash'in yerini alır, kullanıcıya anlaşılır bir neden
 * + çözüm gösterir ve kapatıldığında {@code onClose} (tipik olarak {@code System.exit(1)})
 * tetiklenir.
 *
 * <p>Headless ortamlarda {@link #show(StartupError, String, Runnable)} hiçbir şey çizmez ve {@code
 * false} döner — çağıran taraf bu durumda kendi çıkışını yapar.
 */
public final class StartupErrorWindow {

  private static final Logger LOG = LoggerFactory.getLogger(StartupErrorWindow.class);

  private static final int WIDTH = 560;
  private static final int HEIGHT = 460;

  // SplashWindow paletinin koyu zemini + danger accent (kırmızı/turuncu).
  private static final Color BG_TOP = new Color(15, 23, 42); // slate-900
  private static final Color BG_BOTTOM = new Color(2, 6, 23); // slate-950
  private static final Color TITLE = new Color(248, 250, 252); // slate-50
  private static final Color BODY = new Color(203, 213, 225); // slate-300
  private static final Color SUBTLE = new Color(148, 163, 184); // slate-400
  private static final Color FOOTER = new Color(100, 116, 139); // slate-500
  private static final Color BRAND_TINT = new Color(248, 113, 113); // red-400
  private static final Color DANGER_FROM = new Color(239, 68, 68); // red-500
  private static final Color DANGER_TO = new Color(220, 38, 38); // red-600
  private static final Color DETAIL_BG = new Color(15, 23, 42); // slate-900
  private static final Color DETAIL_BORDER = new Color(51, 65, 85); // slate-700
  private static final Color DETAIL_TEXT = new Color(148, 163, 184); // slate-400
  private static final Color BUTTON_BG = new Color(30, 41, 59); // slate-800
  private static final Color BUTTON_BORDER = new Color(71, 85, 105); // slate-600

  private static StartupErrorWindow current;

  private JFrame frame;
  private Runnable onClose;
  private boolean closeFired;

  private StartupErrorWindow() {}

  /**
   * Hata penceresini gösterir. Zaten gösteriliyorsa veya headless ortamdaysa {@code false} döner
   * (bu durumda çağıran taraf kendi çıkışını yapmalıdır). Pencere kapatıldığında ({@code Kapat}
   * butonu ya da başlık çubuğundaki X) {@code onClose} <b>bir kez</b> çağrılır.
   */
  public static synchronized boolean show(StartupError error, String version, Runnable onClose) {
    if (GraphicsEnvironment.isHeadless()) {
      LOG.debug("Başlatma hata penceresi atlandı: headless ortam.");
      return false;
    }
    if (current != null) {
      return false;
    }
    StartupErrorWindow w = new StartupErrorWindow();
    boolean shown = w.build(error, version, onClose);
    if (shown) {
      current = w;
    }
    return shown;
  }

  /** Açıksa kapatır (onClose'u TETİKLEMEZ — programatik/test kapatması). Idempotent. */
  public static synchronized void close() {
    if (current == null) {
      return;
    }
    try {
      current.disposeFrame();
    } finally {
      current = null;
    }
  }

  /** Test friendly: pencere şu an gösteriliyor mu? */
  static synchronized boolean isShowing() {
    return current != null;
  }

  /* ==================== build ==================== */

  private boolean build(StartupError error, String version, Runnable onClose) {
    this.onClose = onClose;
    try {
      SwingUtilities.invokeAndWait(() -> buildAndShow(error, version));
      return frame != null;
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.warn("Başlatma hata penceresi kesildi: {}", ie.getMessage());
      return false;
    } catch (InvocationTargetException ite) {
      LOG.warn("Başlatma hata penceresi oluşturulamadı: {}", ite.getTargetException().toString());
      return false;
    }
  }

  private void buildAndShow(StartupError error, String version) {
    frame = new JFrame("Mersel DSS Agent Signer — Başlatılamadı");
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            fireCloseAndDispose();
          }
        });

    GradientPanel root = new GradientPanel();
    root.setLayout(new BorderLayout());
    root.setBorder(BorderFactory.createEmptyBorder(26, 30, 22, 30));

    root.add(buildHeader(error), BorderLayout.NORTH);
    root.add(buildCenter(error), BorderLayout.CENTER);
    root.add(buildFooter(error, version), BorderLayout.SOUTH);

    frame.setContentPane(root);
    frame.setSize(new Dimension(WIDTH, HEIGHT));
    frame.setMinimumSize(new Dimension(WIDTH, HEIGHT));
    frame.setLocationRelativeTo(null);
    frame.setAlwaysOnTop(true);
    frame.setVisible(true);
    frame.toFront();
  }

  private JPanel buildHeader(StartupError error) {
    JPanel header = new JPanel();
    header.setOpaque(false);
    header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));

    JLabel brand = new JLabel("MERSEL DSS");
    brand.setForeground(BRAND_TINT);
    brand.setFont(deriveFont(Font.BOLD, 11f));
    brand.setAlignmentX(Component.LEFT_ALIGNMENT);
    header.add(brand);
    header.add(Box.createVerticalStrut(10));

    AccentBar accent = new AccentBar(56, 3);
    accent.setAlignmentX(Component.LEFT_ALIGNMENT);
    header.add(accent);
    header.add(Box.createVerticalStrut(14));

    JLabel title = new JLabel(safe(error.getHeadline(), "Uygulama Başlatılamadı"));
    title.setForeground(TITLE);
    title.setFont(deriveFont(Font.BOLD, 21f));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);
    header.add(title);
    header.add(Box.createVerticalStrut(14));

    return header;
  }

  private JPanel buildCenter(StartupError error) {
    JPanel center = new JPanel(new BorderLayout(0, 14));
    center.setOpaque(false);

    JTextArea message = new JTextArea(safe(error.getMessage(), ""));
    message.setEditable(false);
    message.setFocusable(false);
    message.setLineWrap(true);
    message.setWrapStyleWord(true);
    message.setOpaque(false);
    message.setForeground(BODY);
    message.setFont(deriveFont(Font.PLAIN, 13f));
    message.setBorder(null);
    center.add(message, BorderLayout.NORTH);

    String details = error.getDetails();
    if (details != null && !details.isEmpty()) {
      center.add(buildDetailsPane(details), BorderLayout.CENTER);
    }
    return center;
  }

  private JComponent buildDetailsPane(String details) {
    JPanel wrapper = new JPanel(new BorderLayout(0, 6));
    wrapper.setOpaque(false);

    JLabel label = new JLabel("Teknik ayrıntılar");
    label.setForeground(SUBTLE);
    label.setFont(deriveFont(Font.BOLD, 10f));
    wrapper.add(label, BorderLayout.NORTH);

    JTextArea area = new JTextArea(details);
    area.setEditable(false);
    area.setLineWrap(false);
    area.setForeground(DETAIL_TEXT);
    area.setBackground(DETAIL_BG);
    area.setFont(monoFont(11f));
    area.setBorder(BorderFactory.createEmptyBorder(8, 10, 8, 10));
    area.setCaretPosition(0);

    JScrollPane scroll =
        new JScrollPane(
            area,
            ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
            ScrollPaneConstants.HORIZONTAL_SCROLLBAR_AS_NEEDED);
    scroll.setBorder(BorderFactory.createLineBorder(DETAIL_BORDER, 1));
    scroll.getViewport().setBackground(DETAIL_BG);
    wrapper.add(scroll, BorderLayout.CENTER);
    return wrapper;
  }

  private JPanel buildFooter(StartupError error, String version) {
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(false);
    footer.setBorder(BorderFactory.createEmptyBorder(16, 0, 0, 0));

    JLabel versionLabel = new JLabel("v" + safe(version, "0.0.0"));
    versionLabel.setForeground(FOOTER);
    versionLabel.setFont(deriveFont(Font.PLAIN, 11f));
    footer.add(versionLabel, BorderLayout.WEST);

    JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    buttons.setOpaque(false);

    String details = error.getDetails();
    if (details != null && !details.isEmpty()) {
      JButton copy = secondaryButton("Ayrıntıları Kopyala");
      copy.addActionListener(e -> copyToClipboard(details));
      buttons.add(copy);
    }

    JButton closeButton = primaryButton("Kapat");
    closeButton.addActionListener(e -> fireCloseAndDispose());
    buttons.add(closeButton);

    footer.add(buttons, BorderLayout.EAST);
    return footer;
  }

  /* ==================== actions ==================== */

  private void fireCloseAndDispose() {
    Runnable cb = null;
    synchronized (StartupErrorWindow.class) {
      if (!closeFired) {
        closeFired = true;
        cb = onClose;
      }
      if (current == this) {
        current = null;
      }
    }
    disposeFrame();
    if (cb != null) {
      try {
        cb.run();
      } catch (RuntimeException re) {
        LOG.warn("Başlatma hata penceresi onClose hatası: {}", re.getMessage());
      }
    }
  }

  private void disposeFrame() {
    if (frame == null) {
      return;
    }
    Runnable op =
        () -> {
          try {
            frame.setVisible(false);
            frame.dispose();
          } finally {
            frame = null;
          }
        };
    if (SwingUtilities.isEventDispatchThread()) {
      op.run();
    } else {
      SwingUtilities.invokeLater(op);
    }
  }

  private void copyToClipboard(String text) {
    try {
      Clipboard clipboard = Toolkit.getDefaultToolkit().getSystemClipboard();
      clipboard.setContents(new StringSelection(text), null);
    } catch (RuntimeException re) {
      LOG.debug("Panoya kopyalama başarısız: {}", re.getMessage());
    }
  }

  /* ==================== components ==================== */

  /** SplashWindow ile aynı dikey gradient zemin. */
  private static final class GradientPanel extends JPanel {
    private static final long serialVersionUID = 1L;

    @Override
    protected void paintComponent(Graphics g) {
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g2.setPaint(new GradientPaint(0, 0, BG_TOP, 0, getHeight(), BG_BOTTOM));
        g2.fillRect(0, 0, getWidth(), getHeight());
      } finally {
        g2.dispose();
      }
    }
  }

  /** Danger renkli kısa accent çubuğu — splash'in mavi AccentBar'ının kırmızı eşi. */
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
        g2.setPaint(new GradientPaint(0, 0, DANGER_FROM, barW, 0, DANGER_TO));
        g2.fillRoundRect(0, 0, barW, barH, barH, barH);
      } finally {
        g2.dispose();
      }
    }
  }

  private JButton primaryButton(String text) {
    JButton b = new JButton(text);
    b.setForeground(Color.WHITE);
    b.setBackground(DANGER_TO);
    b.setFocusPainted(false);
    b.setOpaque(true);
    b.setContentAreaFilled(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DANGER_FROM, 1),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)));
    b.setFont(deriveFont(Font.BOLD, 12f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private JButton secondaryButton(String text) {
    JButton b = new JButton(text);
    b.setForeground(BODY);
    b.setBackground(BUTTON_BG);
    b.setFocusPainted(false);
    b.setOpaque(true);
    b.setContentAreaFilled(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BUTTON_BORDER, 1),
            BorderFactory.createEmptyBorder(8, 16, 8, 16)));
    b.setFont(deriveFont(Font.BOLD, 12f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  /* ==================== helpers ==================== */

  private static Font deriveFont(int style, float size) {
    Font base = new JLabel().getFont();
    return base.deriveFont(style, size);
  }

  private static Font monoFont(float size) {
    return new Font(Font.MONOSPACED, Font.PLAIN, (int) size);
  }

  private static String safe(String v, String fallback) {
    return (v == null || v.isEmpty()) ? fallback : v;
  }
}
