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
import java.awt.Desktop;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.awt.font.TextAttribute;
import java.io.IOException;
import java.io.InputStream;
import java.lang.reflect.InvocationTargetException;
import java.net.URI;
import java.net.URISyntaxException;
import java.net.URL;
import java.util.Collections;
import java.util.Map;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.ImageIcon;
import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;

/**
 * Splash kapandıktan sonra gösterilen hafif STATUS paneli. Pencere bir "iş yapma yeri" değil; tek
 * görevi kullanıcıya servisin arka planda çalıştığını ve asıl imzalama akışına (tarayıcı uzantısı
 * veya iş uygulaması) geri dönmesi gerektiğini söylemektir.
 *
 * <p>Yapı:
 *
 * <ul>
 *   <li>Üstte küçük Mersel logo banner'ı (kimlik için, eylem değil).
 *   <li>Ortada hero status card: yeşil pulse dot + "Servis Hazır" + kullanıcıyı tarayıcıya
 *       yönlendiren açık metin + monospace URL.
 *   <li>Altta "Geliştirici araçları" rumuz başlığı altında küçük, ikinci plan link butonlar (API
 *       dokümanı, sağlık kontrolü). Son kullanıcı bunları gözardı edebilir; geliştiriciye geçişte
 *       hâlâ bir kestirme verir.
 *   <li>Alt sağ köşede outline "Uygulamayı Kapat" butonu — onay diyaloğu sonrası gerçek shutdown.
 *   <li>Footer: versiyon ve copyright.
 * </ul>
 *
 * <p>X tuşu pencereyi tray'e gizlemez — onay diyaloğu sonrası uygulamayı GERÇEKTEN kapatır.
 * Kullanıcı kararı: "ben kapattım sandım, hâlâ çalışıyor" şikâyetini önlemek için açık davranış
 * istendi. Tray menüsü "Pencereyi Aç" ile bu pencereyi tekrar öne getirir.
 *
 * <p>Headless ortamlarda ({@code java.awt.headless=true} veya {@link
 * GraphicsEnvironment#isHeadless()}) {@link #show()} no-op'tur. Logo yüklenemezse metin başlığa
 * fallback edilir; SystemTray tarafına bağımlı değildir.
 */
public final class MainWindow {

  private static final Logger LOG = LoggerFactory.getLogger(MainWindow.class);
  // Pencere genişliği sabit. Yükseklik {@code frame.pack()} ile içeriğe göre HESAPLANIR — bu
  // sayede dev tools row'u footer'ın altına sıkışıp kaybolmaz; resize false ile boyut yine
  // kullanıcı tarafından değiştirilemez.
  private static final int WIDTH = 720;
  private static final int CONTENT_PADDING_X = 36;
  private static final int CARD_PADDING_X = 28;
  private static final int STATUS_DOT_DIAMETER = 14;
  private static final String LOGO_RESOURCE = "static/assets/mersel-logo.png";
  private static final String MERSEL_HOME_URL = "https://mersel.io";

  // Light theme — banner zaten beyaz arkaplana sahip; tek bütün beyaz card hissi için pencere
  // de beyaz. Slate (Tailwind) tonları profesyonel ve nötr; mavi accent + yeşil success.
  private static final Color BG = Color.WHITE;
  private static final Color SURFACE = new Color(248, 250, 252); // slate-50
  private static final Color BORDER = new Color(226, 232, 240); // slate-200
  private static final Color BORDER_STRONG = new Color(203, 213, 225); // slate-300
  private static final Color HEADLINE = new Color(15, 23, 42); // slate-900
  private static final Color TEXT_PRIMARY = new Color(30, 41, 59); // slate-800
  private static final Color TEXT_SECONDARY = new Color(71, 85, 105); // slate-600
  private static final Color TEXT_MUTED = new Color(148, 163, 184); // slate-400
  private static final Color ACCENT = new Color(37, 99, 235); // blue-600
  private static final Color SUCCESS = new Color(22, 163, 74); // green-600
  private static final Color SUCCESS_GLOW = new Color(22, 163, 74, 50);
  private static final Color BUTTON_BG = new Color(241, 245, 249); // slate-100
  private static final Color DANGER = new Color(220, 38, 38); // red-600
  private static final Color DANGER_SOFT = new Color(254, 226, 226); // red-100

  private final String version;
  private final String openUrl;
  private final String healthUrl;
  private final Runnable onExitRequest;

  private JFrame frame;

  public MainWindow(String version, String openUrl, String healthUrl, Runnable onExitRequest) {
    this.version = safe(version);
    this.openUrl = openUrl == null ? "" : openUrl;
    this.healthUrl = healthUrl == null ? "" : healthUrl;
    // Default: System.exit(0). Bootstrap, tray cleanup'ı kendi handler'ında yapar.
    this.onExitRequest = onExitRequest != null ? onExitRequest : () -> System.exit(0);
  }

  /** Pencereyi EDT üzerinde gösterir; çağıran thread bloklanmaz. */
  public void show() {
    if (GraphicsEnvironment.isHeadless()) {
      LOG.debug("Ana pencere atlandı: headless ortam.");
      return;
    }
    try {
      // invokeAndWait ile kuruyoruz; show() döndüğünde caller pencereye referans alabilsin.
      SwingUtilities.invokeAndWait(this::buildAndShow);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
      LOG.warn("Ana pencere açılışı kesildi: {}", ie.getMessage());
    } catch (InvocationTargetException ite) {
      LOG.warn("Ana pencere oluşturulamadı: {}", ite.getTargetException().toString());
    }
  }

  /** Pencere açıksa ön plana alır; gizlenmişse tekrar gösterir. EDT-safe + idempotent. */
  public void bringToFront() {
    if (GraphicsEnvironment.isHeadless()) {
      return;
    }
    Runnable op =
        () -> {
          if (frame == null) {
            buildAndShow();
            return;
          }
          if (!frame.isVisible()) {
            frame.setVisible(true);
          }
          frame.setExtendedState(JFrame.NORMAL);
          frame.toFront();
          frame.requestFocus();
        };
    if (SwingUtilities.isEventDispatchThread()) {
      op.run();
    } else {
      SwingUtilities.invokeLater(op);
    }
  }

  /** Onay diyaloğu olmadan pencereyi kapatır + uygulamayı sonlandırır. Tray callback için. */
  public void shutdownWithoutPrompt() {
    Runnable op =
        () -> {
          disposeFrame();
          onExitRequest.run();
        };
    if (SwingUtilities.isEventDispatchThread()) {
      op.run();
    } else {
      SwingUtilities.invokeLater(op);
    }
  }

  /** Pencereyi sadece kapatır (uygulamayı sonlandırmaz). Test / shutdown hook için. */
  public void close() {
    if (frame == null) {
      return;
    }
    Runnable op = this::disposeFrame;
    if (SwingUtilities.isEventDispatchThread()) {
      op.run();
    } else {
      SwingUtilities.invokeLater(op);
    }
  }

  /* ==================== EDT-side ==================== */

  private void buildAndShow() {
    frame = new JFrame("Mersel DSS Agent Signer");
    frame.setDefaultCloseOperation(JFrame.DO_NOTHING_ON_CLOSE);
    frame.getContentPane().setBackground(BG);
    frame.setIconImage(loadIconImage());

    frame.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            confirmAndExit();
          }
        });

    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(BG);
    // Sadece çerçeve — header banner'ın edge-to-edge yayılması için iç padding yok. Padding
    // sorumluluğu her alt panele (header banner kendi içine, content/footer ayrı) devredildi.
    root.setBorder(BorderFactory.createLineBorder(BORDER_STRONG, 1));

    root.add(buildHeader(), BorderLayout.NORTH);
    root.add(buildCenter(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);

    frame.setContentPane(root);
    // pack() içerik preferred size'ına göre frame'i ayarlar — banner full width (WIDTH-2),
    // content + footer kendi yüksekliklerini dikte eder; sıkışma/kayıp olmaz. Resizable=false
    // pack sonrası set ediliyor; bazı LAF'larda öncesi set'lemek pack hesabını bozabiliyor.
    frame.pack();
    frame.setResizable(false);
    frame.setLocationRelativeTo(null);
    frame.setVisible(true);
    frame.toFront();
  }

  private JPanel buildHeader() {
    // ====================================================================
    // LEGAL NOTICE — Brand Notice (LICENSE Ek 2(b))
    // ====================================================================
    // Bu header, LICENSE dosyasındaki Mersel Brand Attribution Addendum'un
    // 2. Maddesi (b) bendinde tanımlanan "Mersel banner / logo lockup"
    // Marka Atfı'nı render eder. Banner'ın kaldırılması, başka bir
    // logoyla değiştirilmesi, okunmayacak kadar küçültülmesi, pencerenin
    // görünür alanından çıkarılması veya "Hakkında" diyaloğu / gizli
    // menü gibi yerlere taşınması lisans sözleşmesinin esaslı ihlalini
    // oluşturur. Bu yükümlülük HER kullanım biçiminde kayıtsız şartsız
    // geçerlidir (dahili kullanım, ticari satış, kapalı kaynağa
    // entegrasyon, SaaS, fork, compound adlandırma vb.); yeniden
    // adlandırma yoluyla atıfları silme istisnası YOKTUR. Yazılım'ı
    // değiştirip dağıtan taraflar ayrıca Addendum § 3 (Modifikasyon
    // Bildirimi) uyarınca son kullanıcıya görünür bir disclaimer
    // göstermek ve § 4 (Onay Beyanı Yasağı) uyarınca Mersel DSS'nin
    // onayı/sponsorluğu izlenimi yaratmamakla yükümlüdür.
    // Detay: LICENSE ve TRADEMARK.md.
    // ====================================================================
    // Banner edge-to-edge: pencere çerçevesinden iç padding olmadan, beyaz şeritle dolu. Görseli
    // pencere genişliğine göre orantılı scale ediyoruz (en-boy korunur). Banner ile content
    // arasında belirgin bir ayraç çizgi (BORDER_STRONG) — banner'ın görsel olarak nereye
    // bittiğini netleştirir.
    JPanel header = new JPanel(new BorderLayout());
    header.setBackground(Color.WHITE);
    header.setOpaque(true);
    header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_STRONG));

    JLabel banner = buildBannerLabel(WIDTH - 2); // -2 = pencere line border kalınlığı
    header.add(banner, BorderLayout.CENTER);
    return header;
  }

  /**
   * Pencerenin ana mesajını taşıyan hero status card. Kullanıcıya tek bir net cümle: "servis
   * çalışıyor, asıl iş tarayıcıda/uygulamada".
   */
  private JPanel buildCenter() {
    JPanel center = new JPanel();
    center.setBackground(BG);
    center.setOpaque(true);
    center.setLayout(new BoxLayout(center, BoxLayout.Y_AXIS));
    center.setBorder(BorderFactory.createEmptyBorder(28, CONTENT_PADDING_X, 16, CONTENT_PADDING_X));

    JPanel statusCard = new JPanel();
    statusCard.setLayout(new BoxLayout(statusCard, BoxLayout.Y_AXIS));
    statusCard.setBackground(SURFACE);
    statusCard.setOpaque(true);
    statusCard.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(24, CARD_PADDING_X, 24, CARD_PADDING_X)));
    statusCard.setAlignmentX(Component.CENTER_ALIGNMENT);

    // Yeşil dot + "Servis Hazır" başlığı yan yana — tek satır.
    JPanel headlineRow = new JPanel();
    headlineRow.setOpaque(false);
    headlineRow.setLayout(new BoxLayout(headlineRow, BoxLayout.X_AXIS));
    headlineRow.setAlignmentX(Component.LEFT_ALIGNMENT);

    headlineRow.add(new StatusDot(SUCCESS, SUCCESS_GLOW, STATUS_DOT_DIAMETER));
    headlineRow.add(Box.createHorizontalStrut(12));

    JLabel headline = new JLabel("Servis Hazır");
    headline.setForeground(HEADLINE);
    headline.setFont(deriveFont(Font.BOLD, 22f));
    headlineRow.add(headline);
    headlineRow.add(Box.createHorizontalGlue());

    statusCard.add(headlineRow);
    statusCard.add(Box.createVerticalStrut(14));

    // HTML body genişliği card iç genişliğine kalibre ediliyor — pencere fixed olduğu için
    // formül net: WIDTH - 2 (line border) - 2*CONTENT_PADDING_X - 2*CARD_PADDING_X = 590.
    // 30 px tampon bırakıyoruz; JLabel'in HTML rendering'i width'e biraz pay istiyor, aksi
    // takdirde son kelimeler kırpılabiliyor (ekran görüntülerinde "üzerinden devam" kayıp olayı).
    int htmlBodyWidth = WIDTH - 2 - (2 * CONTENT_PADDING_X) - (2 * CARD_PADDING_X) - 30;
    JLabel guidance =
        new JLabel(
            "<html><body style=\"width: "
                + htmlBodyWidth
                + "px; line-height: 1.5;\">"
                + "İmzalama işlemleriniz için <b>tarayıcınız ya da iş uygulamanız</b> "
                + "üzerinden devam edebilirsiniz."
                + "<br/><br/>"
                + "Bu pencere yalnızca durum göstergesidir; servis "
                + "<b>arka planda</b> çalışmaya devam eder."
                + "</body></html>");
    guidance.setForeground(TEXT_PRIMARY);
    guidance.setFont(deriveFont(Font.PLAIN, 13f));
    guidance.setAlignmentX(Component.LEFT_ALIGNMENT);
    statusCard.add(guidance);
    statusCard.add(Box.createVerticalStrut(18));

    // URL teknik bilgi olarak — kullanıcı tıklamaz, sadece "yerel servis çalışıyor" doğrulaması.
    if (!openUrl.isEmpty()) {
      JLabel urlLabel = new JLabel("Yerel servis adresi: " + openUrl);
      urlLabel.setForeground(TEXT_SECONDARY);
      urlLabel.setFont(monoFont(11f));
      urlLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
      statusCard.add(urlLabel);
    }

    center.add(statusCard);
    center.add(Box.createVerticalStrut(20));
    center.add(buildDeveloperToolsRow());

    return center;
  }

  /**
   * "Geliştirici araçları" kümesi — alt zıt mesajla minimal punto. Son kullanıcının dikkatini
   * çekmesin diye hem başlık küçük muted, hem de butonlar primary style yerine link-style.
   */
  private JPanel buildDeveloperToolsRow() {
    JPanel container = new JPanel();
    container.setOpaque(false);
    container.setLayout(new BoxLayout(container, BoxLayout.Y_AXIS));
    container.setAlignmentX(Component.CENTER_ALIGNMENT);

    JLabel sectionLabel = new JLabel("GELİŞTİRİCİ ARAÇLARI");
    sectionLabel.setForeground(TEXT_MUTED);
    sectionLabel.setFont(deriveFont(Font.BOLD, 10f));
    sectionLabel.setAlignmentX(Component.CENTER_ALIGNMENT);
    container.add(sectionLabel);
    container.add(Box.createVerticalStrut(10));

    JPanel row = new JPanel(new FlowLayout(FlowLayout.CENTER, 8, 0));
    row.setOpaque(false);
    row.setAlignmentX(Component.CENTER_ALIGNMENT);

    JButton apiButton = linkButton("API dokümanı");
    apiButton.addActionListener(e -> openInBrowser(openUrl));
    row.add(apiButton);

    JButton healthButton = linkButton("Sağlık kontrolü");
    healthButton.addActionListener(e -> openInBrowser(healthUrl));
    row.add(healthButton);

    container.add(row);
    return container;
  }

  /**
   * Tek satırlık şık footer — üç sütun:
   *
   * <ul>
   *   <li>WEST: versiyon etiketi (muted slate-400, ikincil bilgi).
   *   <li>CENTER: kredi cümlesi. {@code mersel.io} tıklanabilir bir link gibi davranır (hover'da
   *       underline, tıklamada {@link Desktop#browse(URI)}).
   *   <li>EAST: outline "Uygulamayı Kapat" butonu. Eylem hierarchy'sine göre ana eylem değildir.
   * </ul>
   *
   * <p>Tek satıra indirme kararı: pencere bir "iş yapma yeri" değil; alt bilgiler de aynı görsel
   * öneme sahip. Dikey alan kazanmak ve dikkati statüye odaklamak için footer kompakt tutuluyor.
   */
  private JPanel buildFooter() {
    JPanel footer = new JPanel(new BorderLayout());
    footer.setBackground(SURFACE);
    footer.setOpaque(true);
    footer.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(12, CONTENT_PADDING_X, 12, CONTENT_PADDING_X)));

    JLabel versionLabel = new JLabel("v" + version);
    versionLabel.setForeground(TEXT_MUTED);
    versionLabel.setFont(deriveFont(Font.PLAIN, 11f));

    JButton exitButton = outlineDangerButton("Uygulamayı Kapat");
    exitButton.addActionListener(e -> confirmAndExit());

    footer.add(versionLabel, BorderLayout.WEST);
    footer.add(buildCreditLine(), BorderLayout.CENTER);
    footer.add(exitButton, BorderLayout.EAST);
    return footer;
  }

  private JPanel buildCreditLine() {
    // ====================================================================
    // LEGAL NOTICE — Brand Notice (LICENSE Ek 2(c))
    // ====================================================================
    // Aşağıdaki credit satırı — "Türkiye e-İmza süreçleri için mersel.io
    // tarafından açık kaynak olarak geliştirilmiştir." cümlesi + tıklanabilir
    // mersel.io köprüsü — LICENSE dosyasındaki Mersel Brand Attribution
    // Addendum'un 2. Maddesi (c) bendinde tanımlanan Marka Atfı'dır.
    // Bu satırın kaldırılması, başka bir krediyle değiştirilmesi,
    // mersel.io köprüsünün devre dışı bırakılması, başka bir adrese
    // yönlendirilmesi veya pencerenin görünür alanından çıkarılması
    // lisans sözleşmesinin esaslı ihlalini oluşturur. Bu yükümlülük
    // HER kullanım biçiminde kayıtsız şartsız geçerlidir (dahili
    // kullanım, ticari satış, kapalı kaynağa entegrasyon, SaaS, fork,
    // compound adlandırma vb.); yeniden adlandırma yoluyla atıfları
    // silme istisnası YOKTUR. Yazılım'ı değiştirip dağıtan taraflar
    // ayrıca Addendum § 3 (Modifikasyon Bildirimi) ve § 4 (Onay Beyanı
    // Yasağı) yükümlülüklerine de tabidir. Detay: LICENSE ve TRADEMARK.md.
    // ====================================================================
    JPanel row = new JPanel();
    row.setOpaque(false);
    row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));

    JLabel prefix = new JLabel("Türkiye e-İmza süreçleri için ");
    prefix.setForeground(TEXT_SECONDARY);
    prefix.setFont(deriveFont(Font.PLAIN, 11f));

    // mersel.io link — HTML wrapper KULLANMIYORUZ. JLabel'a HTML verince preferred width hesabı
    // BoxLayout içinde garip boşluklar açıyor (prefix ve suffix arasında). Düz JLabel + font
    // attribute ile underline'a geçtik.
    final JLabel link = new JLabel("mersel.io");
    link.setForeground(ACCENT);
    link.setFont(deriveFont(Font.BOLD, 11f));
    link.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

    final Font plainLinkFont = link.getFont();
    final Map<TextAttribute, Integer> underlineAttrs =
        Collections.singletonMap(TextAttribute.UNDERLINE, TextAttribute.UNDERLINE_ON);
    final Font hoverLinkFont = plainLinkFont.deriveFont(underlineAttrs);
    final Color hoverColor = new Color(29, 78, 216); // blue-700

    link.addMouseListener(
        new MouseAdapter() {
          @Override
          public void mouseClicked(MouseEvent e) {
            openInBrowser(MERSEL_HOME_URL);
          }

          @Override
          public void mouseEntered(MouseEvent e) {
            link.setFont(hoverLinkFont);
            link.setForeground(hoverColor);
          }

          @Override
          public void mouseExited(MouseEvent e) {
            link.setFont(plainLinkFont);
            link.setForeground(ACCENT);
          }
        });

    JLabel suffix = new JLabel(" tarafından açık kaynak olarak geliştirilmiştir.");
    suffix.setForeground(TEXT_SECONDARY);
    suffix.setFont(deriveFont(Font.PLAIN, 11f));

    row.add(Box.createHorizontalGlue());
    row.add(prefix);
    row.add(link);
    row.add(suffix);
    row.add(Box.createHorizontalGlue());
    return row;
  }

  /** Subtle text-button hissi — son kullanıcıyı yormayan, geliştiriciye çağrı. */
  private JButton linkButton(String text) {
    JButton b = new JButton(text);
    b.setBackground(BUTTON_BG);
    b.setForeground(ACCENT);
    b.setFocusPainted(false);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(7, 16, 7, 16)));
    b.setFont(deriveFont(Font.BOLD, 11f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setOpaque(true);
    return b;
  }

  /** Footer outline danger — birincil eylem değil; kasıtlı bir karar gerektiren tehlikeli yol. */
  private JButton outlineDangerButton(String text) {
    JButton b = new JButton(text);
    b.setBackground(BG);
    b.setForeground(DANGER);
    b.setFocusPainted(false);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DANGER_SOFT, 1),
            BorderFactory.createEmptyBorder(7, 16, 7, 16)));
    b.setFont(deriveFont(Font.BOLD, 11f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setOpaque(true);
    return b;
  }

  /** Anti-aliased dolu daire + dış glow halkası. Status indicator için. */
  private static final class StatusDot extends JPanel {
    private static final long serialVersionUID = 1L;
    private final Color core;
    private final Color glow;
    private final int diameter;

    StatusDot(Color core, Color glow, int diameter) {
      this.core = core;
      this.glow = glow;
      this.diameter = diameter;
      // Glow halkası için diameter + 8 px alan ayır; aksi takdirde halka kırpılır.
      Dimension size = new Dimension(diameter + 8, diameter + 8);
      setPreferredSize(size);
      setMaximumSize(size);
      setMinimumSize(size);
      setOpaque(false);
      setAlignmentY(Component.CENTER_ALIGNMENT);
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        int w = getWidth();
        int h = getHeight();
        int cx = w / 2;
        int cy = h / 2;
        // Outer glow
        g2.setColor(glow);
        g2.fillOval(cx - (diameter + 6) / 2, cy - (diameter + 6) / 2, diameter + 6, diameter + 6);
        // Core dot
        g2.setColor(core);
        g2.fillOval(cx - diameter / 2, cy - diameter / 2, diameter, diameter);
      } finally {
        g2.dispose();
      }
    }
  }

  private void confirmAndExit() {
    int choice =
        JOptionPane.showConfirmDialog(
            frame,
            "Uygulama kapatıldığında imzalama aracını gerektiren işlemler yapılamaz.\n"
                + "Uygulamayı kapatmak istediğinize emin misiniz?",
            "Mersel DSS Agent Signer — Çıkış",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      disposeFrame();
      onExitRequest.run();
    }
  }

  private void disposeFrame() {
    if (frame == null) {
      return;
    }
    try {
      frame.setVisible(false);
      frame.dispose();
    } finally {
      frame = null;
    }
  }

  private JLabel buildBannerLabel(int targetWidth) {
    try {
      URL logoUrl = new ClassPathResource(LOGO_RESOURCE).getURL();
      try (InputStream stream = logoUrl.openStream()) {
        Image src = ImageIO.read(stream);
        if (src != null) {
          int srcW = Math.max(1, src.getWidth(null));
          int srcH = Math.max(1, src.getHeight(null));
          // Görseli daima hedef genişliğe scale et; orijinalden büyütmek SCALE_SMOOTH ile yumuşak
          // sonuç verir, küçültmek de aynı şekilde. Yükseklik en-boy oranıyla hesaplanır.
          int targetH = Math.max(1, srcH * targetWidth / srcW);
          Image scaled = src.getScaledInstance(targetWidth, targetH, Image.SCALE_SMOOTH);
          JLabel label = new JLabel(new ImageIcon(scaled));
          label.setHorizontalAlignment(SwingConstants.CENTER);
          return label;
        }
      }
    } catch (IOException ioe) {
      LOG.debug("Ana pencere banner yüklenemedi ({}): {}", LOGO_RESOURCE, ioe.getMessage());
    } catch (RuntimeException re) {
      LOG.debug("Ana pencere banner render hatası: {}", re.getMessage());
    }
    JLabel fallback = new JLabel("MERSEL DSS Agent Signer", SwingConstants.CENTER);
    fallback.setForeground(ACCENT);
    fallback.setFont(deriveFont(Font.BOLD, 22f));
    fallback.setBorder(BorderFactory.createEmptyBorder(40, 0, 40, 0));
    return fallback;
  }

  private static Image loadIconImage() {
    try {
      URL iconUrl = new ClassPathResource("static/assets/icon.png").getURL();
      return Toolkit.getDefaultToolkit().getImage(iconUrl);
    } catch (IOException ex) {
      return null;
    }
  }

  private void openInBrowser(String url) {
    if (url == null || url.isEmpty()) {
      LOG.debug("Açılacak URL boş.");
      return;
    }
    try {
      if (!Desktop.isDesktopSupported()) {
        LOG.warn("Desktop.browse() desteklenmiyor; URL: {}", url);
        return;
      }
      Desktop desktop = Desktop.getDesktop();
      if (!desktop.isSupported(Desktop.Action.BROWSE)) {
        LOG.warn("Desktop.Action.BROWSE desteklenmiyor; URL: {}", url);
        return;
      }
      desktop.browse(new URI(url));
    } catch (IOException | URISyntaxException ex) {
      LOG.warn("URL açılamadı ({}): {}", url, ex.getMessage());
    }
  }

  private static Font deriveFont(int style, float size) {
    Font base = new JLabel().getFont();
    return base.deriveFont(style, size);
  }

  /**
   * Monospace font — URL gibi teknik metinler için. {@code Font.MONOSPACED} logical font'u her
   * platformda mevcut bir mono yüze map'lenir (Mac: Menlo, Windows: Consolas, Linux: DejaVu Sans
   * Mono). Hard-code etmiyoruz.
   */
  private static Font monoFont(float size) {
    return new Font(Font.MONOSPACED, Font.PLAIN, (int) size);
  }

  private static String safe(String v) {
    return (v == null || v.isEmpty()) ? "0.0.0" : v;
  }

  /* ==================== test friendly ==================== */

  /** Testler için: pencere şu an gösteriliyor mu? EDT-safe değil — sadece testlerde kullan. */
  boolean isShowingForTest() {
    return frame != null && frame.isVisible();
  }
}
