/*
 * Copyright 2026 Mersel DSS
 * SPDX-License-Identifier: Apache-2.0 WITH LicenseRef-Mersel-Brand-Attribution
 */
package io.mersel.dss.agent.api.ui;

import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Cursor;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Font;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.GraphicsEnvironment;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;
import java.awt.RenderingHints;
import java.awt.Toolkit;
import java.awt.datatransfer.StringSelection;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;
import java.awt.event.KeyEvent;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.InvocationTargetException;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import javax.swing.AbstractAction;
import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.BoxLayout;
import javax.swing.JButton;
import javax.swing.JCheckBox;
import javax.swing.JComponent;
import javax.swing.JDialog;
import javax.swing.JFileChooser;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JScrollPane;
import javax.swing.JSplitPane;
import javax.swing.JTable;
import javax.swing.JTextArea;
import javax.swing.JTextField;
import javax.swing.KeyStroke;
import javax.swing.ListSelectionModel;
import javax.swing.RowFilter;
import javax.swing.SwingUtilities;
import javax.swing.WindowConstants;
import javax.swing.border.EmptyBorder;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;
import javax.swing.event.ListSelectionEvent;
import javax.swing.event.ListSelectionListener;
import javax.swing.table.AbstractTableModel;
import javax.swing.table.DefaultTableCellRenderer;
import javax.swing.table.JTableHeader;
import javax.swing.table.TableRowSorter;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import io.mersel.dss.agent.api.exceptions.CauseChainExtractor;
import io.mersel.dss.agent.api.models.SignatureDiagnostics;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecord;
import io.mersel.dss.agent.api.services.diagnostics.TraceRecorder;

/**
 * Bellekteki son trace kayıtlarını canlı gösteren modeless Swing tanılama paneli.
 *
 * <p>Tasarım dili MainWindow ile aynı slate paletine oturur. Layout dört bölüm:
 *
 * <ul>
 *   <li><b>Header</b> — başlık + alt açıklama (dikey hiza, kesişme yok).
 *   <li><b>Toolbar</b> — surface tonlu sticky bant: arama input'u, "Yalnız hatalar" chip'i,
 *       recorder toggle, buffer temizleme.
 *   <li><b>Master/detail</b> — üstte yüksek satırlı, badge'li (method / status pill) JTable; altta
 *       JTabbedPane (Özet, Hata zinciri, İmzalama tanılaması, Ham JSON).
 *   <li><b>Footer</b> — sayaç istatistikleri solda, hızlı eylemler (Kopyala / Dışa aktar) sağda.
 * </ul>
 *
 * <p>Buffer boşsa tablonun yerine "kayıt yok" empty-state overlay'i çıkar. Yeni kayıtlar recorder
 * listener üzerinden EDT'ye invokeLater ile push edilir; recorder kilidi UI thread'ini bloklamaz.
 * Headless ortamda no-op.
 */
public final class DiagnosticsPanel {

  private static final Logger log = LoggerFactory.getLogger(DiagnosticsPanel.class);

  // MainWindow paleti — tutarlılık için.
  private static final Color BG = Color.WHITE;
  private static final Color SURFACE = new Color(248, 250, 252); // slate-50
  private static final Color SURFACE_ALT = new Color(241, 245, 249); // slate-100
  private static final Color BORDER = new Color(226, 232, 240); // slate-200
  private static final Color BORDER_STRONG = new Color(203, 213, 225); // slate-300
  private static final Color HEADLINE = new Color(15, 23, 42); // slate-900
  private static final Color TEXT_PRIMARY = new Color(30, 41, 59); // slate-800
  private static final Color TEXT_SECONDARY = new Color(71, 85, 105); // slate-600
  private static final Color TEXT_MUTED = new Color(148, 163, 184); // slate-400
  private static final Color ACCENT = new Color(37, 99, 235); // blue-600
  private static final Color ACCENT_SOFT = new Color(219, 234, 254); // blue-100
  private static final Color SUCCESS = new Color(22, 163, 74); // green-600
  private static final Color SUCCESS_SOFT = new Color(220, 252, 231); // green-100
  private static final Color WARN = new Color(234, 88, 12); // orange-600
  private static final Color WARN_SOFT = new Color(255, 237, 213); // orange-100
  private static final Color DANGER = new Color(220, 38, 38); // red-600
  private static final Color DANGER_SOFT = new Color(254, 226, 226); // red-100
  private static final Color ROW_ALT = new Color(250, 251, 252);

  private static final DateTimeFormatter TIME_FMT = DateTimeFormatter.ofPattern("HH:mm:ss");

  /** Test friendly: panel kapalıyken null. */
  private static volatile DiagnosticsPanel current;

  private final TraceRecorder recorder;
  private final ObjectMapper jsonMapper;
  private final TraceTableModel tableModel;
  private final Consumer<TraceRecord> recorderListener;

  private JDialog dialog;
  private JTable table;
  private TableRowSorter<TraceTableModel> sorter;
  private JTextField searchField;
  private JCheckBox enabledToggle;
  private JCheckBox errorOnlyChip;
  private JLabel statsLabel;
  private JLabel emptyOverlay;
  private JScrollPane tableScroll;
  private DetailPane detailPane;

  private DiagnosticsPanel(TraceRecorder recorder) {
    this.recorder = recorder;
    this.jsonMapper = buildMapper();
    this.tableModel = new TraceTableModel();
    this.recorderListener = this::onNewRecord;
  }

  private static ObjectMapper buildMapper() {
    ObjectMapper m = new ObjectMapper();
    m.findAndRegisterModules();
    m.setSerializationInclusion(JsonInclude.Include.NON_NULL);
    m.enable(SerializationFeature.INDENT_OUTPUT);
    return m;
  }

  /**
   * Paneli gösterir; zaten açıksa öne alır. Headless ortamlarda ya da {@code recorder == null} ise
   * no-op. EDT-safe.
   */
  public static synchronized void showOrFocus(TraceRecorder recorder) {
    if (recorder == null) {
      log.debug("DiagnosticsPanel atlandı: recorder null.");
      return;
    }
    if (GraphicsEnvironment.isHeadless()) {
      log.debug("DiagnosticsPanel atlandı: headless ortam.");
      return;
    }
    DiagnosticsPanel existing = current;
    if (existing != null && existing.dialog != null) {
      runOnEdt(
          () -> {
            existing.dialog.setVisible(true);
            existing.dialog.toFront();
            existing.dialog.requestFocus();
          });
      return;
    }
    DiagnosticsPanel panel = new DiagnosticsPanel(recorder);
    runOnEdt(panel::buildAndShow);
    current = panel;
  }

  public static synchronized void close() {
    DiagnosticsPanel existing = current;
    if (existing == null) {
      return;
    }
    current = null;
    runOnEdt(existing::disposeInternal);
  }

  static synchronized boolean isShowingForTest() {
    DiagnosticsPanel existing = current;
    return existing != null && existing.dialog != null && existing.dialog.isVisible();
  }

  static synchronized void resetForTest() {
    DiagnosticsPanel existing = current;
    if (existing != null) {
      existing.disposeInternal();
    }
    current = null;
  }

  /**
   * Test-only: aktif dialog'u manuel preview / offscreen screenshot için döner. Production kodu bu
   * erişimi kullanmaz.
   */
  static synchronized JDialog getDialogForTest() {
    DiagnosticsPanel existing = current;
    return existing == null ? null : existing.dialog;
  }

  static final class TraceTableModel extends AbstractTableModel {
    private static final long serialVersionUID = 1L;
    private static final String[] COLUMNS = {
      "Saat", "Method", "Path", "Status", "Süre (ms)", "Error", "Trace ID"
    };
    private final List<TraceRecord> rows = new ArrayList<TraceRecord>();

    void setRecords(List<TraceRecord> records) {
      rows.clear();
      if (records != null) {
        rows.addAll(records);
      }
      fireTableDataChanged();
    }

    void prepend(TraceRecord r) {
      rows.add(0, r);
      fireTableRowsInserted(0, 0);
    }

    TraceRecord getRow(int row) {
      if (row < 0 || row >= rows.size()) {
        return null;
      }
      return rows.get(row);
    }

    @Override
    public int getRowCount() {
      return rows.size();
    }

    @Override
    public int getColumnCount() {
      return COLUMNS.length;
    }

    @Override
    public String getColumnName(int column) {
      return COLUMNS[column];
    }

    @Override
    public Class<?> getColumnClass(int columnIndex) {
      return String.class;
    }

    @Override
    public boolean isCellEditable(int rowIndex, int columnIndex) {
      return false;
    }

    @Override
    public Object getValueAt(int rowIndex, int columnIndex) {
      TraceRecord r = getRow(rowIndex);
      if (r == null) {
        return "";
      }
      switch (columnIndex) {
        case 0:
          return r.getStartedAt() == null ? "" : r.getStartedAt().toLocalTime().format(TIME_FMT);
        case 1:
          return safe(r.getMethod());
        case 2:
          return safe(r.getPath());
        case 3:
          return Integer.toString(r.getStatusCode());
        case 4:
          return Long.toString(r.getDurationMs());
        case 5:
          return safe(r.getErrorCode());
        case 6:
          return safe(r.getTraceId());
        default:
          return "";
      }
    }

    private static String safe(String s) {
      return s == null ? "" : s;
    }
  }

  /* ============== EDT lifecycle ============== */

  private void buildAndShow() {
    dialog = new JDialog((java.awt.Frame) null, "Mersel DSS — Tanılama Paneli", false);
    dialog.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
    dialog.addWindowListener(
        new WindowAdapter() {
          @Override
          public void windowClosing(WindowEvent e) {
            DiagnosticsPanel.close();
          }
        });

    JPanel root = new JPanel(new BorderLayout());
    root.setBackground(BG);

    // Üstte iki bant: header (başlık+subtitle) ve toolbar (search+chip+toggle+actions).
    JPanel topStack = new JPanel();
    topStack.setLayout(new BoxLayout(topStack, BoxLayout.Y_AXIS));
    topStack.setOpaque(false);
    topStack.add(buildHeader());
    topStack.add(buildToolbar());
    root.add(topStack, BorderLayout.NORTH);

    root.add(buildBody(), BorderLayout.CENTER);
    root.add(buildFooter(), BorderLayout.SOUTH);

    dialog.setContentPane(root);
    dialog.setSize(new Dimension(1100, 720));
    dialog.setMinimumSize(new Dimension(960, 560));
    dialog.setLocationRelativeTo(null);

    // Ctrl+W kapat — alışkın kullanıcılar için kestirme.
    @SuppressWarnings("deprecation")
    int menuMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    KeyStroke escClose = KeyStroke.getKeyStroke(KeyEvent.VK_W, menuMask);
    root.getInputMap(JComponent.WHEN_IN_FOCUSED_WINDOW).put(escClose, "diagClose");
    root.getActionMap()
        .put(
            "diagClose",
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent e) {
                DiagnosticsPanel.close();
              }
            });

    // Listener bağla, sonra mevcut snapshot'ı çek — sıralama önemli, race korumalı.
    recorder.addListener(recorderListener);
    reloadFromRecorder();
    updateEmptyOverlay();

    dialog.setVisible(true);
    dialog.toFront();
  }

  private void disposeInternal() {
    try {
      recorder.removeListener(recorderListener);
    } catch (RuntimeException re) {
      log.debug("Recorder listener kaldırılamadı: {}", re.toString());
    }
    if (dialog != null) {
      try {
        dialog.setVisible(false);
        dialog.dispose();
      } finally {
        dialog = null;
      }
    }
  }

  /* ============== EDT helpers ============== */

  private static void runOnEdt(Runnable r) {
    if (SwingUtilities.isEventDispatchThread()) {
      r.run();
      return;
    }
    try {
      SwingUtilities.invokeAndWait(r);
    } catch (InterruptedException ie) {
      Thread.currentThread().interrupt();
    } catch (InvocationTargetException ite) {
      log.debug("EDT runnable hatası: {}", ite.getTargetException().toString());
    }
  }

  private static Font deriveFont(int style, float size) {
    Font base = new JLabel().getFont();
    return base.deriveFont(style, size);
  }

  private static Font monoFont(float size) {
    return new Font(Font.MONOSPACED, Font.PLAIN, (int) size);
  }

  /* ============== Header (title + subtitle) ============== */

  private JPanel buildHeader() {
    JPanel header = new JPanel();
    header.setLayout(new BoxLayout(header, BoxLayout.Y_AXIS));
    header.setOpaque(true);
    header.setBackground(BG);
    header.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(18, 24, 14, 24)));
    header.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel title = new JLabel("Tanılama Paneli");
    title.setForeground(HEADLINE);
    title.setFont(deriveFont(Font.BOLD, 20f));
    title.setAlignmentX(Component.LEFT_ALIGNMENT);

    JLabel subtitle =
        new JLabel(
            "Bellekte tutulan son HTTP trace kayıtları. PIN ve ham gövde ASLA buraya yazılmaz;"
                + " query string'de hassas anahtarlar otomatik maskelenir.");
    subtitle.setForeground(TEXT_SECONDARY);
    subtitle.setFont(deriveFont(Font.PLAIN, 12f));
    subtitle.setAlignmentX(Component.LEFT_ALIGNMENT);
    subtitle.setBorder(new EmptyBorder(4, 0, 0, 0));

    header.add(title);
    header.add(subtitle);
    return header;
  }

  /* ============== Toolbar (search + chips + toggle + clear) ============== */

  private JPanel buildToolbar() {
    JPanel toolbar = new JPanel(new BorderLayout());
    toolbar.setOpaque(true);
    toolbar.setBackground(SURFACE);
    toolbar.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER), new EmptyBorder(10, 24, 10, 24)));
    toolbar.setAlignmentX(Component.LEFT_ALIGNMENT);

    // SOL: arama + yalnız hatalar chip
    JPanel left = new JPanel(new FlowLayout(FlowLayout.LEFT, 10, 0));
    left.setOpaque(false);

    searchField = new JTextField(28);
    searchField.setFont(deriveFont(Font.PLAIN, 12f));
    searchField.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER_STRONG, 1), new EmptyBorder(6, 10, 6, 10)));
    searchField.putClientProperty("JTextField.placeholderText", "Path / errorCode / traceId ara…");
    searchField
        .getDocument()
        .addDocumentListener(
            new DocumentListener() {
              @Override
              public void insertUpdate(DocumentEvent e) {
                applyFilters();
              }

              @Override
              public void removeUpdate(DocumentEvent e) {
                applyFilters();
              }

              @Override
              public void changedUpdate(DocumentEvent e) {
                applyFilters();
              }
            });
    JLabel searchHint = new JLabel("Ara:");
    searchHint.setForeground(TEXT_SECONDARY);
    searchHint.setFont(deriveFont(Font.BOLD, 11f));
    left.add(searchHint);
    left.add(searchField);

    errorOnlyChip = new JCheckBox("Yalnız hatalar");
    errorOnlyChip.setFocusPainted(false);
    errorOnlyChip.setOpaque(false);
    errorOnlyChip.setFont(deriveFont(Font.PLAIN, 12f));
    errorOnlyChip.setForeground(TEXT_PRIMARY);
    errorOnlyChip.addActionListener(e -> applyFilters());
    left.add(errorOnlyChip);

    // SAĞ: recorder toggle + buffer temizle
    JPanel right = new JPanel(new FlowLayout(FlowLayout.RIGHT, 10, 0));
    right.setOpaque(false);

    enabledToggle = new JCheckBox("Tanılama kaydı açık", recorder.isEnabled());
    enabledToggle.setFocusPainted(false);
    enabledToggle.setOpaque(false);
    enabledToggle.setFont(deriveFont(Font.BOLD, 12f));
    enabledToggle.setForeground(TEXT_PRIMARY);
    enabledToggle.addActionListener(
        e -> {
          recorder.setEnabled(enabledToggle.isSelected());
          updateStats();
        });
    right.add(enabledToggle);

    JButton clearBtn = ghostDangerButton("Buffer'ı temizle");
    clearBtn.addActionListener(e -> confirmAndClear());
    right.add(clearBtn);

    toolbar.add(left, BorderLayout.WEST);
    toolbar.add(right, BorderLayout.EAST);
    return toolbar;
  }

  /* ============== Body (master/detail split) ============== */

  private JComponent buildBody() {
    table = new JTable(tableModel);
    table.setRowHeight(34);
    table.setShowGrid(false);
    table.setIntercellSpacing(new Dimension(0, 0));
    table.setBackground(BG);
    table.setForeground(TEXT_PRIMARY);
    table.setSelectionBackground(ACCENT_SOFT);
    table.setSelectionForeground(HEADLINE);
    table.setFont(deriveFont(Font.PLAIN, 12f));
    table.setSelectionMode(ListSelectionModel.SINGLE_SELECTION);
    table.setFillsViewportHeight(true);
    table.setAutoCreateRowSorter(false);

    JTableHeader header = table.getTableHeader();
    header.setReorderingAllowed(false);
    header.setBackground(SURFACE);
    header.setForeground(TEXT_SECONDARY);
    header.setFont(deriveFont(Font.BOLD, 11f));
    header.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER_STRONG));
    header.setPreferredSize(new Dimension(0, 30));

    sorter = new TableRowSorter<TraceTableModel>(tableModel);
    // Status ve Süre sayısal sıralama yapsın diye comparator override.
    sorter.setComparator(3, (a, b) -> Integer.compare(parseInt(a), parseInt(b)));
    sorter.setComparator(4, (a, b) -> Long.compare(parseLong(a), parseLong(b)));
    table.setRowSorter(sorter);

    // Sütun genişlikleri — başlık/subtitle alanı için orantılı.
    setColWidth(0, 90); // Saat
    setColWidth(1, 80); // Method
    setColWidth(2, 320); // Path
    setColWidth(3, 90); // Status
    setColWidth(4, 90); // Süre
    setColWidth(5, 200); // Error
    setColWidth(6, 240); // TraceId

    // Renderer atamaları — özel hücreler badge stili, diğerleri sade.
    table.getColumnModel().getColumn(0).setCellRenderer(new PlainCellRenderer(false));
    table.getColumnModel().getColumn(1).setCellRenderer(new MethodPillRenderer());
    table.getColumnModel().getColumn(2).setCellRenderer(new PlainCellRenderer(false));
    table.getColumnModel().getColumn(3).setCellRenderer(new StatusPillRenderer());
    table.getColumnModel().getColumn(4).setCellRenderer(new PlainCellRenderer(true));
    table.getColumnModel().getColumn(5).setCellRenderer(new ErrorCellRenderer());
    table.getColumnModel().getColumn(6).setCellRenderer(new MonoCellRenderer());

    table
        .getSelectionModel()
        .addListSelectionListener(
            new ListSelectionListener() {
              @Override
              public void valueChanged(ListSelectionEvent e) {
                if (!e.getValueIsAdjusting()) {
                  refreshDetail();
                }
              }
            });

    // Ctrl+C — JSON kopyala (table'da focus iken).
    @SuppressWarnings("deprecation")
    int copyMask = Toolkit.getDefaultToolkit().getMenuShortcutKeyMask();
    KeyStroke copyKey = KeyStroke.getKeyStroke(KeyEvent.VK_C, copyMask);
    table.getInputMap(JComponent.WHEN_ANCESTOR_OF_FOCUSED_COMPONENT).put(copyKey, "copyTraceJson");
    table
        .getActionMap()
        .put(
            "copyTraceJson",
            new AbstractAction() {
              private static final long serialVersionUID = 1L;

              @Override
              public void actionPerformed(ActionEvent e) {
                copySelectedJson();
              }
            });

    tableScroll = new JScrollPane(table);
    tableScroll.setBorder(BorderFactory.createEmptyBorder());
    tableScroll.getViewport().setBackground(BG);

    // Empty state — buffer boşken (ya da filter sonrası boşken) gösterilir. CardLayout ile
    // table ⇄ empty arasında temiz geçiş.
    emptyOverlay = new JLabel(buildEmptyHtml(), JLabel.CENTER);
    emptyOverlay.setOpaque(true);
    emptyOverlay.setBackground(BG);
    emptyOverlay.setForeground(TEXT_MUTED);
    emptyOverlay.setVerticalAlignment(JLabel.CENTER);
    emptyOverlay.setBorder(new EmptyBorder(40, 24, 40, 24));

    JPanel masterStack = new JPanel(new java.awt.CardLayout());
    masterStack.setBackground(BG);
    masterStack.add(tableScroll, "table");
    masterStack.add(emptyOverlay, "empty");
    emptyOverlay.putClientProperty("cardParent", masterStack);

    detailPane = new DetailPane(jsonMapper);
    detailPane.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));

    JSplitPane split = new JSplitPane(JSplitPane.VERTICAL_SPLIT, masterStack, detailPane);
    split.setResizeWeight(0.55);
    split.setDividerLocation(320);
    split.setBorder(null);
    split.setBackground(BG);
    split.setDividerSize(6);

    return split;
  }

  private void setColWidth(int col, int px) {
    table.getColumnModel().getColumn(col).setPreferredWidth(px);
  }

  private static int parseInt(Object o) {
    try {
      return Integer.parseInt(String.valueOf(o));
    } catch (NumberFormatException e) {
      return 0;
    }
  }

  private static long parseLong(Object o) {
    try {
      return Long.parseLong(String.valueOf(o));
    } catch (NumberFormatException e) {
      return 0L;
    }
  }

  private static String buildEmptyHtml() {
    return "<html><body style='text-align:center'>"
        + "<div style='font-size:14pt; color:#475569;'><b>Henüz trace kaydı yok</b></div>"
        + "<div style='margin-top:8px; font-size:11pt; color:#94a3b8;'>"
        + "Bir API isteği gönderildiğinde otomatik olarak burada listelenir.<br/>"
        + "Health / ping gibi gürültü trafiği config gereği kaydedilmez."
        + "</div></body></html>";
  }

  /* ============== Footer (stats + actions) ============== */

  private JPanel buildFooter() {
    JPanel footer = new JPanel(new BorderLayout());
    footer.setOpaque(true);
    footer.setBackground(SURFACE);
    footer.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER), new EmptyBorder(10, 24, 10, 24)));

    statsLabel = new JLabel(" ");
    statsLabel.setForeground(TEXT_SECONDARY);
    statsLabel.setFont(deriveFont(Font.PLAIN, 11f));

    JPanel actions = new JPanel(new FlowLayout(FlowLayout.RIGHT, 8, 0));
    actions.setOpaque(false);
    actions.add(secondaryButton("JSON'u kopyala", e -> copySelectedJson()));
    actions.add(secondaryButton("Dışa aktar (.json)", e -> exportSelected()));
    actions.add(secondaryButton("Tümünü dışa aktar (.ndjson)", e -> exportAll()));

    footer.add(statsLabel, BorderLayout.WEST);
    footer.add(actions, BorderLayout.EAST);
    return footer;
  }

  /* ============== state ops ============== */

  private void reloadFromRecorder() {
    List<TraceRecord> records = recorder.snapshot();
    tableModel.setRecords(records);
    applyFilters();
    updateStats();
    updateEmptyOverlay();
    refreshDetail();
  }

  private void applyFilters() {
    if (sorter == null) {
      return;
    }
    final String q =
        searchField == null
            ? ""
            : searchField.getText() == null
                ? ""
                : searchField.getText().trim().toLowerCase(java.util.Locale.ROOT);
    final boolean errorOnly = errorOnlyChip != null && errorOnlyChip.isSelected();
    sorter.setRowFilter(
        new RowFilter<TraceTableModel, Integer>() {
          @Override
          public boolean include(Entry<? extends TraceTableModel, ? extends Integer> entry) {
            int row = entry.getIdentifier().intValue();
            TraceRecord r = tableModel.getRow(row);
            if (r == null) return false;
            if (errorOnly && !r.isError()) return false;
            if (q.isEmpty()) return true;
            String haystack =
                ((r.getPath() == null ? "" : r.getPath().toLowerCase(java.util.Locale.ROOT))
                    + " "
                    + (r.getErrorCode() == null
                        ? ""
                        : r.getErrorCode().toLowerCase(java.util.Locale.ROOT))
                    + " "
                    + (r.getTraceId() == null
                        ? ""
                        : r.getTraceId().toLowerCase(java.util.Locale.ROOT))
                    + " "
                    + (r.getMethod() == null
                        ? ""
                        : r.getMethod().toLowerCase(java.util.Locale.ROOT)));
            return haystack.contains(q);
          }
        });
    updateEmptyOverlay();
  }

  private void updateStats() {
    if (statsLabel == null) {
      return;
    }
    int visible = table == null ? tableModel.getRowCount() : table.getRowCount();
    String state = recorder.isEnabled() ? "AÇIK" : "KAPALI";
    String fmt =
        String.format(
            "%d görünür · %d toplam · kapasite %d · düşürülen %d · kayıt %s",
            visible,
            recorder.currentSize(),
            recorder.getCapacity(),
            recorder.getTotalDropped(),
            state);
    statsLabel.setText(fmt);
  }

  private void updateEmptyOverlay() {
    if (emptyOverlay == null) {
      return;
    }
    boolean empty = tableModel.getRowCount() == 0 || (table != null && table.getRowCount() == 0);
    JPanel parent = (JPanel) emptyOverlay.getClientProperty("cardParent");
    if (parent == null || !(parent.getLayout() instanceof java.awt.CardLayout)) {
      return;
    }
    java.awt.CardLayout cl = (java.awt.CardLayout) parent.getLayout();
    cl.show(parent, empty ? "empty" : "table");
  }

  /** Recorder listener — yeni kayıt geldiğinde EDT'de tabloya prepend. */
  private void onNewRecord(TraceRecord record) {
    if (record == null) {
      return;
    }
    SwingUtilities.invokeLater(
        () -> {
          tableModel.prepend(record);
          // Sorter aktifken filter dolayısıyla görünüm güncellenir; ek bir invalidate gerekmez.
          updateStats();
          updateEmptyOverlay();
        });
  }

  private void refreshDetail() {
    if (detailPane == null) {
      return;
    }
    detailPane.show(selectedRecord());
  }

  private TraceRecord selectedRecord() {
    if (table == null) {
      return null;
    }
    int viewRow = table.getSelectedRow();
    if (viewRow < 0) {
      return null;
    }
    int modelRow = table.convertRowIndexToModel(viewRow);
    return tableModel.getRow(modelRow);
  }

  /* ============== actions ============== */

  private void copySelectedJson() {
    TraceRecord r = selectedRecord();
    if (r == null) {
      info("Kopyalamak için listeden bir kayıt seçin.");
      return;
    }
    String json = toJson(r);
    try {
      Toolkit.getDefaultToolkit().getSystemClipboard().setContents(new StringSelection(json), null);
      info("Trace JSON pano'ya kopyalandı (" + json.length() + " karakter).");
    } catch (RuntimeException re) {
      log.warn("Pano'ya kopyalanamadı: {}", re.getMessage());
      error("Pano'ya kopyalanamadı: " + re.getMessage());
    }
  }

  private void exportSelected() {
    TraceRecord r = selectedRecord();
    if (r == null) {
      info("Dışa aktarmak için listeden bir kayıt seçin.");
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("trace-" + safeForFile(r.getTraceId()) + ".json"));
    if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try (FileOutputStream fos = new FileOutputStream(chooser.getSelectedFile())) {
      fos.write(toJson(r).getBytes(java.nio.charset.StandardCharsets.UTF_8));
      info("Kayıt dışa aktarıldı: " + chooser.getSelectedFile().getAbsolutePath());
    } catch (IOException ioe) {
      log.warn("Trace dışa aktarılamadı: {}", ioe.getMessage());
      error("Dışa aktarma başarısız: " + ioe.getMessage());
    }
  }

  private void exportAll() {
    List<TraceRecord> all = new ArrayList<TraceRecord>(recorder.snapshot());
    if (all.isEmpty()) {
      info("Buffer boş; dışa aktarılacak kayıt yok.");
      return;
    }
    JFileChooser chooser = new JFileChooser();
    chooser.setSelectedFile(new File("mersel-traces-" + System.currentTimeMillis() + ".ndjson"));
    if (chooser.showSaveDialog(dialog) != JFileChooser.APPROVE_OPTION) {
      return;
    }
    try (FileOutputStream fos = new FileOutputStream(chooser.getSelectedFile())) {
      ObjectMapper compact = new ObjectMapper();
      compact.findAndRegisterModules();
      compact.setSerializationInclusion(JsonInclude.Include.NON_NULL);
      for (TraceRecord r : all) {
        fos.write(compact.writeValueAsBytes(r));
        fos.write('\n');
      }
      info("Toplam " + all.size() + " kayıt dışa aktarıldı.");
    } catch (IOException ioe) {
      log.warn("Toplu trace dışa aktarılamadı: {}", ioe.getMessage());
      error("Dışa aktarma başarısız: " + ioe.getMessage());
    }
  }

  private void confirmAndClear() {
    int choice =
        JOptionPane.showConfirmDialog(
            dialog,
            "Bellekteki " + recorder.currentSize() + " trace kaydı silinecek. Devam edilsin mi?",
            "Buffer'ı temizle",
            JOptionPane.YES_NO_OPTION,
            JOptionPane.WARNING_MESSAGE);
    if (choice == JOptionPane.YES_OPTION) {
      recorder.clear();
      reloadFromRecorder();
    }
  }

  private String toJson(TraceRecord r) {
    try {
      return jsonMapper.writeValueAsString(r);
    } catch (RuntimeException | com.fasterxml.jackson.core.JsonProcessingException ex) {
      return "{\"error\":\"JSON serialization failed: " + ex.getMessage() + "\"}";
    }
  }

  private static String safeForFile(String s) {
    if (s == null || s.isEmpty()) {
      return String.valueOf(System.currentTimeMillis());
    }
    StringBuilder sb = new StringBuilder(s.length());
    for (int i = 0; i < s.length(); i++) {
      char c = s.charAt(i);
      boolean ok = (c >= '0' && c <= '9') || (c >= 'A' && c <= 'Z') || (c >= 'a' && c <= 'z');
      sb.append(ok ? c : '-');
    }
    return sb.toString();
  }

  private void info(String msg) {
    JOptionPane.showMessageDialog(dialog, msg, "Tanılama Paneli", JOptionPane.INFORMATION_MESSAGE);
  }

  private void error(String msg) {
    JOptionPane.showMessageDialog(dialog, msg, "Tanılama Paneli", JOptionPane.ERROR_MESSAGE);
  }

  /* ============== buttons ============== */

  private JButton secondaryButton(String text, ActionListener l) {
    JButton b = new JButton(text);
    b.setBackground(SURFACE_ALT);
    b.setForeground(ACCENT);
    b.setFocusPainted(false);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(BORDER, 1), new EmptyBorder(6, 12, 6, 12)));
    b.setFont(deriveFont(Font.BOLD, 11f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setOpaque(true);
    b.addActionListener(l);
    return b;
  }

  private JButton ghostDangerButton(String text) {
    JButton b = new JButton(text);
    b.setBackground(BG);
    b.setForeground(DANGER);
    b.setFocusPainted(false);
    b.setBorder(
        BorderFactory.createCompoundBorder(
            BorderFactory.createLineBorder(DANGER_SOFT, 1), new EmptyBorder(6, 12, 6, 12)));
    b.setFont(deriveFont(Font.BOLD, 11f));
    b.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
    b.setOpaque(true);
    return b;
  }

  /* ============== custom renderers (pills) ============== */

  /**
   * Yatay alanı doğru orantılı kullanan, tablo hücresinde rounded badge gibi görünen
   * label-rendererı. Tablo selection rengini override etmeden satırın üstüne çiziyoruz.
   */
  static final class Pill extends JLabel {
    private static final long serialVersionUID = 1L;
    private Color pillBg;
    private Color pillFg;

    Pill() {
      setOpaque(false);
      setHorizontalAlignment(CENTER);
      setBorder(new EmptyBorder(0, 10, 0, 10));
      setFont(new JLabel().getFont().deriveFont(Font.BOLD, 11f));
    }

    void setPillColors(Color bg, Color fg) {
      this.pillBg = bg;
      this.pillFg = fg;
      setForeground(fg);
    }

    @Override
    protected void paintComponent(Graphics g) {
      if (pillBg != null) {
        Graphics2D g2 = (Graphics2D) g.create();
        try {
          g2.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
          int w = getWidth();
          int h = getHeight();
          int padY = Math.max(2, (h - 22) / 2);
          int arc = 14;
          g2.setColor(pillBg);
          g2.fillRoundRect(2, padY, w - 4, h - 2 * padY, arc, arc);
        } finally {
          g2.dispose();
        }
      }
      super.paintComponent(g);
    }
  }

  /** Hücre yüzeyini ayarlar (selection ya da alternate stripe), pill'i ortalar. */
  abstract static class CellHost extends JPanel {
    private static final long serialVersionUID = 1L;

    CellHost() {
      super(new java.awt.GridBagLayout());
      setOpaque(true);
    }

    void applyRowBackground(JTable t, boolean isSelected, int row) {
      if (isSelected) {
        setBackground(t.getSelectionBackground());
      } else {
        setBackground(row % 2 == 0 ? BG : ROW_ALT);
      }
    }
  }

  /** GET / POST / PUT / DELETE rozeti — yöntem rengi sektör konvansiyonuna yakın. */
  static final class MethodPillRenderer extends CellHost
      implements javax.swing.table.TableCellRenderer {
    private static final long serialVersionUID = 1L;
    private final Pill pill = new Pill();

    MethodPillRenderer() {
      super();
      GridBagConstraints gc = new GridBagConstraints();
      gc.fill = GridBagConstraints.NONE;
      add(pill, gc);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      applyRowBackground(t, isSelected, row);
      String m = String.valueOf(value == null ? "" : value).toUpperCase(java.util.Locale.ROOT);
      pill.setText(m);
      Color bg;
      Color fg;
      switch (m) {
        case "GET":
          bg = ACCENT_SOFT;
          fg = ACCENT;
          break;
        case "POST":
          bg = SUCCESS_SOFT;
          fg = SUCCESS;
          break;
        case "PUT":
        case "PATCH":
          bg = WARN_SOFT;
          fg = WARN;
          break;
        case "DELETE":
          bg = DANGER_SOFT;
          fg = DANGER;
          break;
        default:
          bg = SURFACE_ALT;
          fg = TEXT_SECONDARY;
      }
      pill.setPillColors(bg, fg);
      return this;
    }
  }

  /** 2xx yeşil, 4xx turuncu, 5xx kırmızı; pill arkaplanı + bold rakam. */
  static final class StatusPillRenderer extends CellHost
      implements javax.swing.table.TableCellRenderer {
    private static final long serialVersionUID = 1L;
    private final Pill pill = new Pill();

    StatusPillRenderer() {
      super();
      GridBagConstraints gc = new GridBagConstraints();
      gc.fill = GridBagConstraints.NONE;
      add(pill, gc);
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      applyRowBackground(t, isSelected, row);
      String s = String.valueOf(value == null ? "" : value);
      int code;
      try {
        code = Integer.parseInt(s);
      } catch (NumberFormatException nfe) {
        code = 0;
      }
      pill.setText(s);
      Color bg = SURFACE_ALT;
      Color fg = TEXT_SECONDARY;
      if (code >= 500) {
        bg = DANGER_SOFT;
        fg = DANGER;
      } else if (code >= 400) {
        bg = WARN_SOFT;
        fg = WARN;
      } else if (code >= 200 && code < 300) {
        bg = SUCCESS_SOFT;
        fg = SUCCESS;
      } else if (code >= 300 && code < 400) {
        bg = ACCENT_SOFT;
        fg = ACCENT;
      }
      pill.setPillColors(bg, fg);
      return this;
    }
  }

  /** Düz hücre — alternate stripe + dikey orta hizalama + sol/sağ padding. */
  static final class PlainCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;
    private final boolean rightAlign;

    PlainCellRenderer(boolean rightAlign) {
      this.rightAlign = rightAlign;
      setBorder(new EmptyBorder(0, 12, 0, 12));
    }

    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
      JLabel label = (JLabel) c;
      label.setHorizontalAlignment(rightAlign ? JLabel.RIGHT : JLabel.LEFT);
      label.setBorder(new EmptyBorder(0, 12, 0, 12));
      if (!isSelected) {
        c.setBackground(row % 2 == 0 ? BG : ROW_ALT);
        c.setForeground(TEXT_PRIMARY);
      }
      return c;
    }
  }

  /** Hata kodu hücresi — boş değilse kırmızı vurgu. */
  static final class ErrorCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
      JLabel label = (JLabel) c;
      label.setBorder(new EmptyBorder(0, 12, 0, 12));
      label.setHorizontalAlignment(JLabel.LEFT);
      String s = String.valueOf(value == null ? "" : value);
      if (!isSelected) {
        c.setBackground(row % 2 == 0 ? BG : ROW_ALT);
        c.setForeground(s.isEmpty() ? TEXT_MUTED : DANGER);
      }
      label.setFont(deriveFont(s.isEmpty() ? Font.PLAIN : Font.BOLD, 11.5f));
      return c;
    }
  }

  /** TraceId hücresi — monospace, küçük punto. */
  static final class MonoCellRenderer extends DefaultTableCellRenderer {
    private static final long serialVersionUID = 1L;

    @Override
    public Component getTableCellRendererComponent(
        JTable t, Object value, boolean isSelected, boolean hasFocus, int row, int column) {
      Component c =
          super.getTableCellRendererComponent(t, value, isSelected, hasFocus, row, column);
      JLabel label = (JLabel) c;
      label.setBorder(new EmptyBorder(0, 12, 0, 12));
      label.setFont(monoFont(11f));
      if (!isSelected) {
        c.setBackground(row % 2 == 0 ? BG : ROW_ALT);
        c.setForeground(TEXT_SECONDARY);
      }
      return c;
    }
  }

  /* ============== Detail (tabbed) ============== */

  /**
   * Seçili kayıt için 4 sekmeli detay paneli: Özet (label/value), Hata zinciri, İmzalama
   * tanılaması, Ham JSON. İçerikler {@link #show(TraceRecord)} ile yenilenir; null kayıt boş mesaj
   * gösterir.
   */
  static final class DetailPane extends JPanel {
    private static final long serialVersionUID = 1L;
    private final ObjectMapper mapper;
    private final JTextArea jsonArea;

    // Custom tab bar (Aqua LAF kontrolü dışında, kendi pixel'imizi kontrol ediyoruz).
    private final JPanel tabBar;
    private final JPanel tabContent;
    private final java.awt.CardLayout tabContentLayout;
    private final java.util.List<JLabel> tabLabels = new java.util.ArrayList<JLabel>();
    private static final String[] TAB_KEYS = {"summary", "cause", "sig", "json"};
    private static final String[] TAB_TITLES = {
      "Özet", "Hata zinciri", "İmzalama tanılaması", "Ham JSON"
    };
    private int activeTab = 0;

    // Header bar bileşenleri (POST /xades/sign · 500 · 5262 ms · trace-...)
    private final JPanel headerBar;
    private final JLabel placeholderLabel;
    private final Pill methodPill;
    private final JLabel pathLabel;
    private final Pill statusPill;
    private final Pill durationPill;
    private final JLabel traceIdLabel;

    DetailPane(ObjectMapper mapper) {
      super(new BorderLayout());
      this.mapper = mapper;
      setBackground(BG);
      setOpaque(true);

      // ----- Header bar (master/detail divider'ın hemen altı) ---------------
      // Tek satır JLabel yerine, üst kısma pill'ler + monospace path + traceId
      // koyuyoruz. Detay üzerinde 'göz adresi' işlevi görür.
      headerBar = new JPanel();
      headerBar.setLayout(new BoxLayout(headerBar, BoxLayout.X_AXIS));
      headerBar.setOpaque(true);
      headerBar.setBackground(SURFACE);
      headerBar.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER),
              new EmptyBorder(10, 18, 10, 18)));

      placeholderLabel = new JLabel("Bir kayıt seçin — detayı burada görünür.");
      placeholderLabel.setForeground(TEXT_SECONDARY);
      placeholderLabel.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12f));

      methodPill = new Pill();
      methodPill.setPreferredSize(new Dimension(64, 22));
      methodPill.setMinimumSize(new Dimension(64, 22));
      methodPill.setMaximumSize(new Dimension(64, 22));
      methodPill.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 11f));

      pathLabel = new JLabel(" ");
      pathLabel.setFont(new Font(Font.MONOSPACED, Font.BOLD, 13));
      pathLabel.setForeground(HEADLINE);
      pathLabel.setBorder(new EmptyBorder(0, 10, 0, 10));

      statusPill = new Pill();
      statusPill.setPreferredSize(new Dimension(56, 22));
      statusPill.setMinimumSize(new Dimension(56, 22));
      statusPill.setMaximumSize(new Dimension(56, 22));
      statusPill.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 11f));

      durationPill = new Pill();
      durationPill.setPreferredSize(new Dimension(78, 22));
      durationPill.setMinimumSize(new Dimension(78, 22));
      durationPill.setMaximumSize(new Dimension(78, 22));
      durationPill.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 11f));

      traceIdLabel = new JLabel(" ");
      traceIdLabel.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 11));
      traceIdLabel.setForeground(TEXT_SECONDARY);

      add(headerBar, BorderLayout.NORTH);
      // İlk durumda placeholder göster.
      renderHeaderEmpty();

      // ----- Custom tab bar + CardLayout content -----------------------------
      // Aqua LAF JTabbedPane'in border'ını override ettiği için kendi tab şeridimizi
      // yapıyoruz: BoxLayout X içinde tıklanabilir JLabel'lar (sola hizalı), seçili
      // olanın altında 2px accent çizgisi (compound border ile).
      tabBar = new JPanel();
      tabBar.setLayout(new BoxLayout(tabBar, BoxLayout.X_AXIS));
      tabBar.setOpaque(true);
      tabBar.setBackground(BG);
      tabBar.setBorder(BorderFactory.createMatteBorder(0, 0, 1, 0, BORDER));

      for (int i = 0; i < TAB_TITLES.length; i++) {
        final int idx = i;
        JLabel l = makeTabLabel(TAB_TITLES[i]);
        l.addMouseListener(
            new java.awt.event.MouseAdapter() {
              @Override
              public void mouseClicked(java.awt.event.MouseEvent e) {
                setActiveTab(idx);
              }

              @Override
              public void mouseEntered(java.awt.event.MouseEvent e) {
                if (idx != activeTab) {
                  l.setForeground(HEADLINE);
                }
                l.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
              }

              @Override
              public void mouseExited(java.awt.event.MouseEvent e) {
                if (idx != activeTab) {
                  l.setForeground(TEXT_SECONDARY);
                }
              }
            });
        tabLabels.add(l);
        tabBar.add(l);
      }
      tabBar.add(Box.createHorizontalGlue());

      tabContentLayout = new java.awt.CardLayout();
      tabContent = new JPanel(tabContentLayout);
      tabContent.setBackground(BG);
      tabContent.setOpaque(true);

      tabContent.add(
          scrollWrap(buildEmptySection("Bir kayıt seçildiğinde özet bilgileri burada listelenir.")),
          TAB_KEYS[0]);
      tabContent.add(scrollWrap(buildEmptySection("Hata zinciri yok.")), TAB_KEYS[1]);
      tabContent.add(
          scrollWrap(buildEmptySection("İmzalama tanılaması (signatureDiagnostics) yok.")),
          TAB_KEYS[2]);

      jsonArea = new JTextArea();
      jsonArea.setEditable(false);
      jsonArea.setLineWrap(false);
      jsonArea.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      jsonArea.setBackground(SURFACE);
      jsonArea.setForeground(TEXT_PRIMARY);
      jsonArea.setBorder(new EmptyBorder(10, 12, 10, 12));
      JScrollPane jsonScroll = new JScrollPane(jsonArea);
      jsonScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, BORDER));
      tabContent.add(jsonScroll, TAB_KEYS[3]);

      JPanel center = new JPanel(new BorderLayout());
      center.setBackground(BG);
      center.setOpaque(true);
      center.add(tabBar, BorderLayout.NORTH);
      center.add(tabContent, BorderLayout.CENTER);
      add(center, BorderLayout.CENTER);

      setActiveTab(0);
    }

    private void setActiveTab(int idx) {
      if (idx < 0 || idx >= tabLabels.size()) return;
      activeTab = idx;
      for (int i = 0; i < tabLabels.size(); i++) {
        boolean active = (i == idx);
        JLabel l = tabLabels.get(i);
        l.setForeground(active ? ACCENT : TEXT_SECONDARY);
        l.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createMatteBorder(0, 0, 3, 0, active ? ACCENT : BG),
                new EmptyBorder(8, 16, 5, 16)));
      }
      tabContentLayout.show(tabContent, TAB_KEYS[idx]);
      tabBar.repaint();
    }

    /**
     * Sade ama vurgulu tab başlığı: padding + bold + initial muted renk; tıklama davranışı dışarıda
     * eklenir. Aqua LAF override'ından kaçınmak için tab strip'imiz JTabbedPane yerine custom
     * JPanel kullanır.
     */
    private JLabel makeTabLabel(String text) {
      JLabel l = new JLabel(text);
      l.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 12f));
      l.setForeground(TEXT_SECONDARY);
      l.setOpaque(true);
      l.setBackground(BG);
      l.setBorder(
          BorderFactory.createCompoundBorder(
              BorderFactory.createMatteBorder(0, 0, 3, 0, BG), new EmptyBorder(8, 16, 5, 16)));
      return l;
    }

    /** Header bar'ı placeholder mesajına çevirir (hiç kayıt seçilmediği durum). */
    private void renderHeaderEmpty() {
      headerBar.removeAll();
      headerBar.add(placeholderLabel);
      headerBar.add(Box.createHorizontalGlue());
      headerBar.revalidate();
      headerBar.repaint();
    }

    /** Header bar'ı verilen kayıt için pill bar olarak doldurur. */
    private void renderHeaderForRecord(TraceRecord r) {
      headerBar.removeAll();

      String method = r.getMethod() == null ? "" : r.getMethod().toUpperCase(java.util.Locale.ROOT);
      methodPill.setText(method);
      Color[] mc = methodPalette(method);
      methodPill.setPillColors(mc[0], mc[1]);
      headerBar.add(methodPill);

      pathLabel.setText(r.getPath() == null ? "—" : r.getPath());
      headerBar.add(pathLabel);

      headerBar.add(Box.createHorizontalGlue());

      int code = r.getStatusCode();
      statusPill.setText(Integer.toString(code));
      Color[] sc = statusPalette(code);
      statusPill.setPillColors(sc[0], sc[1]);
      headerBar.add(statusPill);
      headerBar.add(Box.createHorizontalStrut(8));

      durationPill.setText(r.getDurationMs() + " ms");
      Color[] dc = durationPalette(r.getDurationMs());
      durationPill.setPillColors(dc[0], dc[1]);
      headerBar.add(durationPill);
      headerBar.add(Box.createHorizontalStrut(12));

      traceIdLabel.setText(r.getTraceId() == null ? "" : r.getTraceId());
      headerBar.add(traceIdLabel);

      headerBar.revalidate();
      headerBar.repaint();
    }

    private static Color[] methodPalette(String m) {
      switch (m) {
        case "GET":
          return new Color[] {ACCENT_SOFT, ACCENT};
        case "POST":
          return new Color[] {SUCCESS_SOFT, SUCCESS};
        case "PUT":
        case "PATCH":
          return new Color[] {WARN_SOFT, WARN};
        case "DELETE":
          return new Color[] {DANGER_SOFT, DANGER};
        default:
          return new Color[] {SURFACE_ALT, TEXT_SECONDARY};
      }
    }

    private static Color[] statusPalette(int code) {
      if (code >= 500) return new Color[] {DANGER_SOFT, DANGER};
      if (code >= 400) return new Color[] {WARN_SOFT, WARN};
      if (code >= 300) return new Color[] {ACCENT_SOFT, ACCENT};
      if (code >= 200) return new Color[] {SUCCESS_SOFT, SUCCESS};
      return new Color[] {SURFACE_ALT, TEXT_SECONDARY};
    }

    private static Color[] durationPalette(long ms) {
      // < 250 ms hızlı (yeşil değil çünkü status renklerini sömürmek istemiyoruz; nötr)
      // 250-1500 hafif uyarı; > 1500 belirgin uyarı.
      if (ms > 1500) return new Color[] {WARN_SOFT, WARN};
      return new Color[] {SURFACE_ALT, TEXT_SECONDARY};
    }

    void show(TraceRecord r) {
      if (r == null) {
        renderHeaderEmpty();
        jsonArea.setText("");
        rebuildContents(
            buildEmptySection("Bir kayıt seçildiğinde özet bilgileri burada listelenir."),
            buildEmptySection("Hata zinciri yok."),
            buildEmptySection("İmzalama tanılaması yok."));
        return;
      }
      renderHeaderForRecord(r);
      jsonArea.setText(toJson(r));
      jsonArea.setCaretPosition(0);
      rebuildContents(
          buildSummary(r),
          buildCauseChain(r.getCauseChain()),
          buildSignatureDiagnostics(r.getSignatureDiagnostics()));
    }

    /** show()'dan çağrılır: 4 card'ı (summary, cause, sig, json) yeniden inşa eder. */
    private void rebuildContents(JComponent summary, JComponent cause, JComponent sig) {
      tabContent.removeAll();
      tabContent.add(scrollWrap(summary), TAB_KEYS[0]);
      tabContent.add(scrollWrap(cause), TAB_KEYS[1]);
      tabContent.add(scrollWrap(sig), TAB_KEYS[2]);
      JScrollPane jsonScroll = new JScrollPane(jsonArea);
      jsonScroll.setBorder(BorderFactory.createMatteBorder(0, 0, 0, 0, BORDER));
      tabContent.add(jsonScroll, TAB_KEYS[3]);
      tabContent.revalidate();
      tabContent.repaint();
      // Aktif tab'ı koru.
      tabContentLayout.show(tabContent, TAB_KEYS[activeTab]);
    }

    private static JScrollPane scrollWrap(JComponent inner) {
      JScrollPane sp = new JScrollPane(inner);
      sp.setBorder(BorderFactory.createMatteBorder(1, 0, 0, 0, BORDER));
      sp.getViewport().setBackground(BG);
      sp.getVerticalScrollBar().setUnitIncrement(16);
      return sp;
    }

    private static JComponent buildEmptySection(String text) {
      JPanel p = new JPanel(new BorderLayout());
      p.setBackground(BG);
      p.setBorder(new EmptyBorder(28, 24, 28, 24));
      JLabel l = new JLabel(text, JLabel.CENTER);
      l.setForeground(TEXT_MUTED);
      l.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12f));
      p.add(l, BorderLayout.CENTER);
      return p;
    }

    private JComponent buildSummary(TraceRecord r) {
      JPanel grid = new JPanel(new GridBagLayout());
      grid.setBackground(BG);
      grid.setBorder(new EmptyBorder(20, 24, 20, 24));

      // Key sütunu için fixed genişlik strut — tüm satırlar aynı x'te başlar.
      GridBagConstraints strut = new GridBagConstraints();
      strut.gridx = 0;
      strut.gridy = 0;
      strut.gridheight = 1;
      grid.add(Box.createHorizontalStrut(170), strut);

      int row = 1;
      row = addKv(grid, row, "Trace ID", r.getTraceId());
      row =
          addKv(
              grid,
              row,
              "Başlangıç",
              r.getStartedAt() == null ? null : r.getStartedAt().toString());
      row = addKv(grid, row, "Method", r.getMethod());
      row = addKv(grid, row, "Path", r.getPath());
      row = addKv(grid, row, "Query (sanitised)", r.getQuerySanitised());
      row = addKv(grid, row, "Status", String.valueOf(r.getStatusCode()));
      row = addKv(grid, row, "Süre", r.getDurationMs() + " ms");
      row = addKv(grid, row, "Uzak adres", r.getRemoteAddr());

      if (r.getErrorCode() != null || r.getErrorMessage() != null) {
        // Sayısal değil, görsel ayrım için ince bir separator + section heading ekleyelim.
        GridBagConstraints sepC = new GridBagConstraints();
        sepC.gridx = 0;
        sepC.gridy = row;
        sepC.gridwidth = 2;
        sepC.fill = GridBagConstraints.HORIZONTAL;
        sepC.insets = new Insets(8, 0, 12, 0);
        JPanel sep = new JPanel();
        sep.setBackground(BORDER);
        sep.setPreferredSize(new Dimension(1, 1));
        grid.add(sep, sepC);
        row++;

        GridBagConstraints headC = new GridBagConstraints();
        headC.gridx = 0;
        headC.gridy = row;
        headC.gridwidth = 2;
        headC.anchor = GridBagConstraints.WEST;
        headC.insets = new Insets(0, 0, 8, 0);
        JLabel head = new JLabel("HATA");
        head.setForeground(DANGER);
        head.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 11f));
        grid.add(head, headC);
        row++;

        row = addKv(grid, row, "Error code", r.getErrorCode());
        row = addKv(grid, row, "Error message", r.getErrorMessage());
        row = addKv(grid, row, "Exception", r.getExceptionType());
      }

      // Geri kalan boşluğu push et.
      GridBagConstraints filler = new GridBagConstraints();
      filler.gridx = 0;
      filler.gridy = row;
      filler.gridwidth = 2;
      filler.weighty = 1.0;
      filler.fill = GridBagConstraints.VERTICAL;
      grid.add(Box.createVerticalGlue(), filler);
      return grid;
    }

    private static int addKv(JPanel grid, int row, String key, String value) {
      // Key sütunu sağa hizalı + sabit minimum genişlik → tüm satırlar aynı kolondan başlar.
      // Value monospace; uzun string'ler wrap edebilsin diye HTML body width hint kullanılır.
      JLabel k = new JLabel(key.toUpperCase(java.util.Locale.ROOT));
      k.setForeground(TEXT_MUTED);
      k.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 10.5f));
      k.setHorizontalAlignment(JLabel.LEFT);

      boolean empty = value == null || value.isEmpty();
      JLabel v;
      if (empty) {
        v = new JLabel("—");
        v.setForeground(TEXT_MUTED);
        v.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12f));
      } else {
        v =
            new JLabel(
                "<html><body style='width:520px; font-family:Menlo,Consolas,monospace'>"
                    + escapeHtml(value)
                    + "</body></html>");
        v.setForeground(TEXT_PRIMARY);
        // HTML body üzerinde font-family override yapıldı; setFont yine baseline.
        v.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      }

      GridBagConstraints gk = new GridBagConstraints();
      gk.gridx = 0;
      gk.gridy = row;
      gk.anchor = GridBagConstraints.NORTHWEST;
      gk.insets = new Insets(8, 0, 8, 18);
      gk.ipadx = 0;
      gk.weightx = 0.0;
      grid.add(k, gk);

      GridBagConstraints gv = new GridBagConstraints();
      gv.gridx = 1;
      gv.gridy = row;
      gv.anchor = GridBagConstraints.NORTHWEST;
      gv.fill = GridBagConstraints.HORIZONTAL;
      gv.weightx = 1.0;
      gv.insets = new Insets(8, 0, 8, 0);
      grid.add(v, gv);
      return row + 1;
    }

    private JComponent buildCauseChain(List<CauseChainExtractor.Frame> chain) {
      if (chain == null || chain.isEmpty()) {
        return buildEmptySection("Hata zinciri yok.");
      }
      JPanel list = new JPanel();
      list.setLayout(new BoxLayout(list, BoxLayout.Y_AXIS));
      list.setBackground(BG);
      list.setBorder(new EmptyBorder(14, 18, 14, 18));
      int idx = 0;
      for (CauseChainExtractor.Frame f : chain) {
        idx++;
        JPanel card = new JPanel(new BorderLayout());
        card.setBackground(SURFACE);
        card.setBorder(
            BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(BORDER, 1), new EmptyBorder(8, 12, 8, 12)));
        JLabel num = new JLabel("#" + idx);
        num.setForeground(TEXT_MUTED);
        num.setFont(new JLabel().getFont().deriveFont(Font.BOLD, 10f));
        JLabel type = new JLabel(f.getType() == null ? "<unknown>" : f.getType());
        type.setForeground(DANGER);
        type.setFont(new Font(Font.MONOSPACED, Font.BOLD, 12));
        JLabel msg =
            new JLabel(
                "<html><body style='width:540px'>"
                    + (f.getMessage() == null ? "—" : escapeHtml(f.getMessage()))
                    + "</body></html>");
        msg.setForeground(TEXT_PRIMARY);
        msg.setFont(new JLabel().getFont().deriveFont(Font.PLAIN, 12f));
        msg.setBorder(new EmptyBorder(4, 0, 0, 0));

        JPanel head = new JPanel(new BorderLayout());
        head.setOpaque(false);
        head.add(num, BorderLayout.WEST);
        head.add(type, BorderLayout.CENTER);

        JPanel content = new JPanel(new BorderLayout());
        content.setOpaque(false);
        content.add(head, BorderLayout.NORTH);
        content.add(msg, BorderLayout.CENTER);
        card.add(content, BorderLayout.CENTER);
        card.setAlignmentX(Component.LEFT_ALIGNMENT);

        if (idx > 1) {
          list.add(Box.createVerticalStrut(8));
        }
        list.add(card);
      }
      // Boşluk push.
      list.add(Box.createVerticalGlue());
      return list;
    }

    private JComponent buildSignatureDiagnostics(SignatureDiagnostics d) {
      if (d == null) {
        return buildEmptySection("İmzalama tanılaması (signatureDiagnostics) yok.");
      }
      // Tek noktadan basitlik: signatureDiagnostics'ı pretty JSON olarak da göstermek
      // okunaklı; gelecekte kart ipuçları için özel kart bileşeni eklenebilir.
      JTextArea area = new JTextArea();
      area.setEditable(false);
      area.setLineWrap(false);
      area.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 12));
      area.setBackground(SURFACE);
      area.setForeground(TEXT_PRIMARY);
      area.setBorder(new EmptyBorder(12, 14, 12, 14));
      try {
        area.setText(mapper.writeValueAsString(d));
      } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        area.setText("(JSON serialization failed: " + ex.getMessage() + ")");
      }
      area.setCaretPosition(0);
      return area;
    }

    private String toJson(TraceRecord r) {
      try {
        return mapper.writeValueAsString(r);
      } catch (com.fasterxml.jackson.core.JsonProcessingException ex) {
        return "{\"error\":\"JSON serialization failed: " + ex.getMessage() + "\"}";
      }
    }

    private static String escapeHtml(String s) {
      if (s == null) return "";
      StringBuilder sb = new StringBuilder(s.length());
      for (int i = 0; i < s.length(); i++) {
        char c = s.charAt(i);
        switch (c) {
          case '<':
            sb.append("&lt;");
            break;
          case '>':
            sb.append("&gt;");
            break;
          case '&':
            sb.append("&amp;");
            break;
          case '"':
            sb.append("&quot;");
            break;
          default:
            sb.append(c);
        }
      }
      return sb.toString();
    }
  }
}
