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
import java.awt.CardLayout;
import java.awt.Color;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FileDialog;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.Window;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.ButtonGroup;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JRadioButton;
import javax.swing.JScrollPane;
import javax.swing.JTable;
import javax.swing.JTextField;
import javax.swing.ListSelectionModel;
import javax.swing.filechooser.FileNameExtensionFilter;
import javax.swing.table.DefaultTableModel;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * "Sanal Kart Tanımla" modal diyaloğu. Kullanıcı kart takılı olmasa bile bir PKCS#12 (PFX) dosyası
 * veya PKCS#11 (HSM / yüklü sürücü) tanımlayıp "Dummy Card" olarak kullanabilir.
 *
 * <p>Backend ile {@link VirtualCardActions} portu üzerinden konuşur; Spring servislerine doğrudan
 * bağımlı değildir. Tüm işlem EDT'de çalışır (kayıt I/O'su küçük: PFX birkaç KB).
 */
final class VirtualCardDialog extends JDialog {

  private static final long serialVersionUID = 1L;
  private static final Logger LOG = LoggerFactory.getLogger(VirtualCardDialog.class);

  private static final Color BG = Color.WHITE;
  private static final Color SURFACE = new Color(248, 250, 252);
  private static final Color BORDER = new Color(226, 232, 240);
  private static final Color BORDER_STRONG = new Color(148, 163, 184); // slate-400
  private static final Color FIELD_BG = new Color(255, 255, 255);
  private static final Color HEADER_BG = new Color(241, 245, 249); // slate-100
  private static final Color HEADLINE = new Color(15, 23, 42);
  private static final Color TEXT_PRIMARY = new Color(30, 41, 59); // slate-800
  private static final Color TEXT_SECONDARY = new Color(71, 85, 105);
  private static final Color TEXT_MUTED = new Color(148, 163, 184);
  private static final Color ACCENT = new Color(37, 99, 235);
  private static final Color DANGER = new Color(220, 38, 38);

  private static final int FIELD_HEIGHT = 30;

  private static final String CARD_PKCS12 = "PKCS12";
  private static final String CARD_PKCS11 = "PKCS11";

  private final transient VirtualCardActions actions;

  private DefaultTableModel tableModel;
  private JTable table;

  private JRadioButton pkcs12Radio;
  private JRadioButton pkcs11Radio;
  private CardLayout formCards;
  private JPanel formCardPanel;
  private JButton registerButton;
  private JLabel editModeLabel;
  // Düzenlenmekte olan kartın orijinal adı; null ise "yeni kayıt" modundayız.
  private String editingOriginalName;

  // PKCS#12 alanları
  private JTextField pfxNameField;
  private JTextField pfxPathField;
  private JPasswordField passwordField;

  // PKCS#11 alanları
  private JTextField p11NameField;
  private JTextField libPathField;

  private VirtualCardDialog(Window owner, VirtualCardActions actions) {
    super(owner, "Sanal Kart Tanımla", ModalityType.APPLICATION_MODAL);
    this.actions = actions;
    buildUi();
    refreshTable();
  }

  /** Diyaloğu açar (modal). EDT'de çağrılmalı. */
  static void open(Window owner, VirtualCardActions actions) {
    if (actions == null) {
      return;
    }
    VirtualCardDialog dialog = new VirtualCardDialog(owner, actions);
    dialog.setLocationRelativeTo(owner);
    dialog.setVisible(true);
  }

  private void buildUi() {
    JPanel root = new JPanel(new BorderLayout(0, 16));
    root.setBackground(BG);
    root.setBorder(BorderFactory.createEmptyBorder(20, 24, 20, 24));

    root.add(buildExistingSection(), BorderLayout.NORTH);
    root.add(buildFormSection(), BorderLayout.CENTER);
    root.add(buildButtonBar(), BorderLayout.SOUTH);

    setContentPane(root);
    setDefaultCloseOperation(DISPOSE_ON_CLOSE);
    pack();
    setMinimumSize(new Dimension(580, getHeight()));
  }

  /* ---------------- existing cards ---------------- */

  private JPanel buildExistingSection() {
    JPanel panel = new JPanel(new BorderLayout(0, 8));
    panel.setBackground(BG);
    panel.add(sectionTitle("Tanımlı Sanal Kartlar"), BorderLayout.NORTH);

    tableModel =
        new DefaultTableModel(new Object[] {"Ad (terminalName)", "Tip", "Kaynak"}, 0) {
          private static final long serialVersionUID = 1L;

          @Override
          public boolean isCellEditable(int row, int column) {
            return false;
          }
        };
    table = new JTable(tableModel);
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setRowHeight(26);
    table.setShowVerticalLines(false);
    table.setGridColor(BORDER);
    table.setFont(deriveFont(Font.PLAIN, 12f));
    table.setForeground(TEXT_PRIMARY);
    table.setSelectionBackground(new Color(219, 234, 254)); // blue-100
    table.setSelectionForeground(TEXT_PRIMARY);
    table.setFillsViewportHeight(true);

    javax.swing.table.JTableHeader header = table.getTableHeader();
    header.setReorderingAllowed(false);
    header.setResizingAllowed(false);
    header.setBackground(HEADER_BG);
    header.setForeground(TEXT_SECONDARY);
    header.setFont(deriveFont(Font.BOLD, 11f));
    header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));
    ((javax.swing.table.DefaultTableCellRenderer) header.getDefaultRenderer())
        .setHorizontalAlignment(javax.swing.SwingConstants.LEFT);

    JScrollPane scroll = new JScrollPane(table);
    scroll.setPreferredSize(new Dimension(520, 116));
    scroll.setBorder(BorderFactory.createLineBorder(BORDER_STRONG, 1));
    scroll.getViewport().setBackground(BG);
    panel.add(scroll, BorderLayout.CENTER);

    JPanel actionRow = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actionRow.setBackground(BG);
    JButton editButton = linkButton("Seçili kartı düzenle");
    editButton.addActionListener(e -> onEditSelected());
    JButton deleteButton = dangerButton("Seçili kartı sil");
    deleteButton.addActionListener(e -> onDeleteSelected());
    actionRow.add(editButton);
    actionRow.add(deleteButton);
    panel.add(actionRow, BorderLayout.SOUTH);

    return panel;
  }

  private void refreshTable() {
    tableModel.setRowCount(0);
    try {
      List<VirtualCardActions.View> rows = actions.list();
      for (VirtualCardActions.View v : rows) {
        tableModel.addRow(new Object[] {v.getName(), v.getCardType(), v.getSource()});
      }
    } catch (RuntimeException re) {
      LOG.debug("Sanal kart listesi alınamadı: {}", re.getMessage());
    }
  }

  private void onDeleteSelected() {
    int row = table.getSelectedRow();
    if (row < 0) {
      showInfo("Önce silinecek bir kart seçin.");
      return;
    }
    String name = String.valueOf(tableModel.getValueAt(row, 0));
    int choice =
        JOptionPane.showConfirmDialog(
            this,
            "'" + name + "' sanal kartı kaldırılsın mı?",
            "Sanal Kartı Sil",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice != JOptionPane.YES_OPTION) {
      return;
    }
    try {
      actions.remove(name);
      refreshTable();
    } catch (RuntimeException re) {
      showError("Kart silinemedi: " + re.getMessage());
    }
  }

  /* ---------------- registration form ---------------- */

  private JPanel buildFormSection() {
    JPanel panel = new JPanel(new BorderLayout(0, 10));
    panel.setBackground(BG);
    panel.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(14, 0, 0, 0)));

    panel.add(sectionTitle("Yeni Sanal Kart"), BorderLayout.NORTH);

    JPanel body = new JPanel(new BorderLayout(0, 10));
    body.setBackground(BG);

    JPanel typeRow = new JPanel(new FlowLayout(FlowLayout.LEFT, 16, 0));
    typeRow.setBackground(BG);
    pkcs12Radio = new JRadioButton("PKCS#12 (PFX dosyası)", true);
    pkcs11Radio = new JRadioButton("PKCS#11 (HSM / sürücü)");
    for (JRadioButton rb : new JRadioButton[] {pkcs12Radio, pkcs11Radio}) {
      rb.setBackground(BG);
      rb.setFont(deriveFont(Font.PLAIN, 12f));
      rb.setFocusPainted(false);
      rb.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    }
    ButtonGroup group = new ButtonGroup();
    group.add(pkcs12Radio);
    group.add(pkcs11Radio);
    pkcs12Radio.addActionListener(e -> formCards.show(formCardPanel, CARD_PKCS12));
    pkcs11Radio.addActionListener(e -> formCards.show(formCardPanel, CARD_PKCS11));
    typeRow.add(pkcs12Radio);
    typeRow.add(pkcs11Radio);
    body.add(typeRow, BorderLayout.NORTH);

    formCards = new CardLayout();
    formCardPanel = new JPanel(formCards);
    formCardPanel.setBackground(BG);
    formCardPanel.add(buildPkcs12Form(), CARD_PKCS12);
    formCardPanel.add(buildPkcs11Form(), CARD_PKCS11);
    body.add(formCardPanel, BorderLayout.CENTER);

    panel.add(body, BorderLayout.CENTER);
    return panel;
  }

  private JPanel buildPkcs12Form() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(BG);
    GridBagConstraints c = baseGbc();

    pfxNameField = new JTextField();
    addLabeledRow(p, c, 0, "Kart adı:", pfxNameField, null);

    pfxPathField = new JTextField();
    pfxPathField.setEditable(false);
    JButton browse = linkButton("Dosya Seç…");
    browse.addActionListener(e -> choosePfxFile());
    addLabeledRow(p, c, 1, "PFX dosyası:", pfxPathField, browse);

    passwordField = new JPasswordField();
    addLabeledRow(p, c, 2, "Parola:", passwordField, null);

    return p;
  }

  private JPanel buildPkcs11Form() {
    JPanel p = new JPanel(new GridBagLayout());
    p.setBackground(BG);
    GridBagConstraints c = baseGbc();

    p11NameField = new JTextField();
    addLabeledRow(p, c, 0, "Kart adı:", p11NameField, null);

    libPathField = new JTextField();
    JButton browse = linkButton("Dosya Seç…");
    browse.addActionListener(e -> chooseLibFile());
    addLabeledRow(p, c, 1, "Kütüphane yolu:", libPathField, browse);

    JLabel hint = new JLabel("Örn: /usr/local/lib/libsofthsm2.so  (.so / .dll / .dylib)");
    hint.setForeground(TEXT_MUTED);
    hint.setFont(deriveFont(Font.PLAIN, 11f));
    c.gridx = 1;
    c.gridy = 2;
    c.gridwidth = 2;
    p.add(hint, c);
    c.gridwidth = 1;

    return p;
  }

  private void choosePfxFile() {
    File f = chooseFile("PFX / PKCS#12 dosyası seç", "PKCS#12 (*.pfx, *.p12)", "pfx", "p12");
    if (f != null) {
      pfxPathField.setText(f.getAbsolutePath());
      if (pfxNameField.getText().trim().isEmpty()) {
        pfxNameField.setText("PFX - " + f.getName());
      }
    }
  }

  private void chooseLibFile() {
    File f =
        chooseFile(
            "PKCS#11 kütüphanesi seç",
            "PKCS#11 kütüphane (*.so, *.dll, *.dylib)",
            "so",
            "dll",
            "dylib");
    if (f != null) {
      libPathField.setText(f.getAbsolutePath());
      if (p11NameField.getText().trim().isEmpty()) {
        p11NameField.setText("HSM - " + f.getName());
      }
    }
  }

  /**
   * Dosya seçim diyaloğunu açar. Öncelikle native {@link FileDialog} (macOS'ta gerçek Finder
   * paneli, Windows'ta Explorer diyaloğu) denenir; herhangi bir sebeple başarısız olursa Swing
   * {@link JFileChooser}'a güvenli fallback yapılır.
   *
   * @param title diyalog başlığı
   * @param filterLabel JFileChooser fallback'i için filtre etiketi
   * @param extensions uzantılar (noktasız, örn. "pfx", "p12")
   * @return seçilen dosya ya da iptal/başarısızlıkta {@code null}
   */
  private File chooseFile(String title, String filterLabel, String... extensions) {
    try {
      FileDialog fd = new FileDialog(this, title, FileDialog.LOAD);
      fd.setFilenameFilter((dir, name) -> hasExtension(name, extensions));
      // Windows FilenameFilter'ı yok sayar; setFile pattern'i ile filtre ipucu veriyoruz.
      fd.setFile(windowsPattern(extensions));
      fd.setVisible(true);
      String fileName = fd.getFile();
      String dir = fd.getDirectory();
      if (fileName == null || dir == null) {
        return null;
      }
      return new File(dir, fileName);
    } catch (RuntimeException re) {
      LOG.debug("Native dosya diyaloğu açılamadı, JFileChooser'a düşülüyor: {}", re.getMessage());
      return chooseFileSwing(title, filterLabel, extensions);
    }
  }

  private File chooseFileSwing(String title, String filterLabel, String... extensions) {
    JFileChooser chooser = new JFileChooser();
    chooser.setDialogTitle(title);
    chooser.setFileFilter(new FileNameExtensionFilter(filterLabel, extensions));
    if (chooser.showOpenDialog(this) == JFileChooser.APPROVE_OPTION) {
      return chooser.getSelectedFile();
    }
    return null;
  }

  private static boolean hasExtension(String name, String[] extensions) {
    if (name == null) {
      return false;
    }
    String lower = name.toLowerCase(Locale.ROOT);
    for (String ext : extensions) {
      if (lower.endsWith("." + ext.toLowerCase(Locale.ROOT))) {
        return true;
      }
    }
    return false;
  }

  private static String windowsPattern(String[] extensions) {
    StringBuilder sb = new StringBuilder();
    for (String ext : extensions) {
      if (sb.length() > 0) {
        sb.append(';');
      }
      sb.append("*.").append(ext);
    }
    return sb.toString();
  }

  /* ---------------- button bar ---------------- */

  private JPanel buildButtonBar() {
    JPanel bar = new JPanel(new BorderLayout());
    bar.setBackground(BG);
    // Matte ayraç çizgisi + üstte belirgin iç boşluk: butonlar ayraç çizgisine yapışmaz.
    bar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER),
            BorderFactory.createEmptyBorder(16, 0, 4, 0)));

    editModeLabel = new JLabel(" ");
    editModeLabel.setForeground(TEXT_MUTED);
    editModeLabel.setFont(deriveFont(Font.PLAIN, 11f));
    editModeLabel.setVisible(false);

    JButton closeButton = linkButton("Kapat");
    closeButton.addActionListener(e -> dispose());

    registerButton = primaryButton("Tanımla");
    registerButton.addActionListener(e -> onRegister());

    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    right.setBackground(BG);
    right.add(closeButton);
    right.add(registerButton);

    bar.add(editModeLabel, BorderLayout.WEST);
    bar.add(right, BorderLayout.EAST);
    return bar;
  }

  private void onRegister() {
    try {
      if (editingOriginalName != null) {
        commitEdit();
        refreshTable();
        showInfo("Sanal kart güncellendi.");
      } else {
        registerInto(currentFormName());
        clearForm();
        refreshTable();
        showInfo("Sanal kart tanımlandı. Artık 'GET /smartcard' listesinde görünür.");
      }
    } catch (IllegalArgumentException iae) {
      showError(iae.getMessage());
    } catch (RuntimeException re) {
      showError("Beklenmeyen hata: " + re.getMessage());
    }
  }

  /**
   * Düzenleme commit'i. Veri kaybını önlemek için yeni tanım önce <b>geçici benzersiz</b> bir adla
   * doğrulanır (eski kayıt bu sırada bozulmadan durur); doğrulama geçerse geçici + eski kayıt
   * silinip nihai adla yeniden kaydedilir. Doğrulama (örn. yanlış PFX parolası) başarısız olursa
   * eski kart olduğu gibi kalır.
   */
  private void commitEdit() {
    String finalName = currentFormName();
    if (!finalName.equals(editingOriginalName) && nameExists(finalName)) {
      throw new IllegalArgumentException("Bu isimde başka bir sanal kart zaten var: " + finalName);
    }
    String tempName = "__edit_" + java.util.UUID.randomUUID();
    registerInto(tempName); // doğrulama; başarısız olursa eski kayıt korunur
    actions.remove(tempName);
    actions.remove(editingOriginalName);
    registerInto(finalName);
    exitEditMode();
    clearForm();
  }

  /** Aktif forma göre (PKCS#12 / PKCS#11) verilen ad altında kayıt yapar. */
  private void registerInto(String name) {
    if (pkcs12Radio.isSelected()) {
      String path = pfxPathField.getText().trim();
      if (path.isEmpty()) {
        throw new IllegalArgumentException("Lütfen bir PFX dosyası seçin.");
      }
      byte[] bytes;
      try {
        bytes = Files.readAllBytes(new File(path).toPath());
      } catch (IOException ioe) {
        throw new IllegalArgumentException("PFX dosyası okunamadı: " + ioe.getMessage(), ioe);
      }
      char[] pw = passwordField.getPassword();
      try {
        actions.registerPkcs12(name, bytes, pw, new File(path).getName());
      } finally {
        Arrays.fill(pw, '\0');
      }
    } else {
      actions.registerPkcs11(name, libPathField.getText().trim());
    }
  }

  private String currentFormName() {
    return (pkcs12Radio.isSelected() ? pfxNameField.getText() : p11NameField.getText()).trim();
  }

  private boolean nameExists(String name) {
    if (name.isEmpty()) {
      return false;
    }
    for (VirtualCardActions.View v : actions.list()) {
      if (name.equals(v.getName())) {
        return true;
      }
    }
    return false;
  }

  /** Seçili kartı forma yükleyip düzenleme moduna geçer. */
  private void onEditSelected() {
    int row = table.getSelectedRow();
    if (row < 0) {
      showInfo("Önce düzenlenecek bir kart seçin.");
      return;
    }
    String name = String.valueOf(tableModel.getValueAt(row, 0));
    String typeLabel = String.valueOf(tableModel.getValueAt(row, 1));
    String source = String.valueOf(tableModel.getValueAt(row, 2));
    boolean isPkcs12 = typeLabel.contains("PKCS#12");

    editingOriginalName = name;
    clearForm();
    if (isPkcs12) {
      pkcs12Radio.setSelected(true);
      formCards.show(formCardPanel, CARD_PKCS12);
      pfxNameField.setText(name);
      editModeLabel.setText(
          "Düzenleniyor: " + name + " — PFX dosyasını ve parolayı yeniden seçin.");
    } else {
      pkcs11Radio.setSelected(true);
      formCards.show(formCardPanel, CARD_PKCS11);
      p11NameField.setText(name);
      libPathField.setText(source);
      editModeLabel.setText("Düzenleniyor: " + name);
    }
    editModeLabel.setVisible(true);
    registerButton.setText("Güncelle");
  }

  /** Düzenleme modundan çıkar; buton metnini ve etiketleri sıfırlar. */
  private void exitEditMode() {
    editingOriginalName = null;
    if (editModeLabel != null) {
      editModeLabel.setVisible(false);
      editModeLabel.setText(" ");
    }
    if (registerButton != null) {
      registerButton.setText("Tanımla");
    }
  }

  /** Tüm form alanlarını temizler. */
  private void clearForm() {
    pfxNameField.setText("");
    pfxPathField.setText("");
    passwordField.setText("");
    p11NameField.setText("");
    libPathField.setText("");
  }

  /* ---------------- ui helpers ---------------- */

  private static GridBagConstraints baseGbc() {
    GridBagConstraints c = new GridBagConstraints();
    c.insets = new Insets(7, 0, 7, 10);
    c.anchor = GridBagConstraints.WEST;
    c.fill = GridBagConstraints.HORIZONTAL;
    return c;
  }

  /** GridBag satırı: etiket + alan (+ opsiyonel trailing buton). */
  private void addLabeledRow(
      JPanel p, GridBagConstraints c, int row, String label, JTextField field, JButton trailing) {
    c.gridy = row;
    c.gridx = 0;
    c.weightx = 0;
    c.gridwidth = 1;
    JLabel l = new JLabel(label);
    l.setForeground(TEXT_SECONDARY);
    l.setFont(deriveFont(Font.BOLD, 12f));
    p.add(l, c);

    c.gridx = 1;
    c.weightx = 1.0;
    styleField(field);
    p.add(field, c);

    c.gridx = 2;
    c.weightx = 0;
    if (trailing != null) {
      p.add(trailing, c);
    } else {
      p.add(Box.createHorizontalStrut(0), c);
    }
  }

  /**
   * Tüm metin alanlarına belirgin kenarlık + beyaz zemin verir. macOS Aqua LAF salt-okunur ya da
   * native bezel'li alanları beyaz pencerede neredeyse görünmez render ettiği için explicit border
   * şart (kullanıcı geri bildirimi: "inputlar tam belli olmuyor").
   */
  private void styleField(JTextField field) {
    field.setFont(deriveFont(Font.PLAIN, 13f));
    field.setForeground(TEXT_PRIMARY);
    field.setCaretColor(TEXT_PRIMARY);
    field.setOpaque(true);
    field.setBackground(field.isEditable() ? FIELD_BG : SURFACE);
    field.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STRONG, 1),
            BorderFactory.createEmptyBorder(5, 9, 5, 9)));
    Dimension size = new Dimension(320, FIELD_HEIGHT);
    field.setPreferredSize(size);
    field.setMinimumSize(new Dimension(180, FIELD_HEIGHT));
  }

  private JLabel sectionTitle(String text) {
    JLabel l = new JLabel(text);
    l.setForeground(HEADLINE);
    l.setFont(deriveFont(Font.BOLD, 14f));
    return l;
  }

  private JButton primaryButton(String text) {
    JButton b = new JButton(text);
    b.setBackground(ACCENT);
    b.setForeground(Color.WHITE);
    b.setFocusPainted(false);
    b.setOpaque(true);
    b.setContentAreaFilled(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(ACCENT, 1),
            BorderFactory.createEmptyBorder(8, 20, 8, 20)));
    b.setFont(deriveFont(Font.BOLD, 12f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private JButton linkButton(String text) {
    JButton b = new JButton(text);
    b.setBackground(SURFACE);
    b.setForeground(ACCENT);
    b.setFocusPainted(false);
    b.setOpaque(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1),
            BorderFactory.createEmptyBorder(7, 14, 7, 14)));
    b.setFont(deriveFont(Font.BOLD, 11f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private JButton dangerButton(String text) {
    JButton b = new JButton(text);
    b.setBackground(BG);
    b.setForeground(DANGER);
    b.setFocusPainted(false);
    b.setOpaque(true);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(new Color(254, 226, 226), 1),
            BorderFactory.createEmptyBorder(6, 14, 6, 14)));
    b.setFont(deriveFont(Font.BOLD, 11f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    return b;
  }

  private void showError(String message) {
    JOptionPane.showMessageDialog(this, message, "Sanal Kart — Hata", JOptionPane.ERROR_MESSAGE);
  }

  private void showInfo(String message) {
    JOptionPane.showMessageDialog(this, message, "Sanal Kart", JOptionPane.INFORMATION_MESSAGE);
  }

  private static Font deriveFont(int style, float size) {
    Font base = new JLabel().getFont();
    return base.deriveFont(style, size);
  }
}
