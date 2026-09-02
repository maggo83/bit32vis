// BitSquiggles — interactive Swing demo.
//
// Grug 2-Clause License
// 1. do what want
// 2. not sue grug
//
// Run from the repo root:
//   javac -d out java/core/bitsquiggles/*.java \
//     java/renderer-swing/bitsquiggles/renderer/swing/BitSquigglesRendererSwing.java \
//     && java -cp out bitsquiggles.BitSquigglesDemo

package bitsquiggles;

import bitsquiggles.renderer.swing.BitSquigglesRendererSwing;
import java.awt.*;
import java.util.ArrayList;
import java.util.List;
import javax.swing.*;
import javax.swing.event.DocumentEvent;
import javax.swing.event.DocumentListener;

public class BitSquigglesDemo {

  // ── UI palette ────────────────────────────────────────────────────────────
  private static final Color APP_BG = new Color(0xF2, 0xF2, 0xF7);
  private static final Color CARD_BG = Color.WHITE;
  private static final Color TEXT_FG = new Color(0x1C, 0x1C, 0x1E);
  private static final Color MUTED_FG = new Color(0x8E, 0x8E, 0x93);
  private static final Color BORDER_FG = new Color(0xD1, 0xD1, 0xD6);

  private static JLabel statusLabel;
  private static BitSquigglesRendererSwing.Width selectedWidth =
    BitSquigglesRendererSwing.Width.BITS_32;
  private static final List<VisView> allViews = new ArrayList<>();
  private static final List<PixelView> pixelViews = new ArrayList<>();

  public static void main(String[] args) {
    SwingUtilities.invokeLater(() -> {
      JFrame frame = new JFrame("BitSquiggles");
      frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);

      JPanel root = new JPanel(new BorderLayout());
      root.setBackground(APP_BG);
      root.setBorder(BorderFactory.createEmptyBorder(20, 20, 20, 20));

      JScrollPane scroll = new JScrollPane(buildContent());
      scroll.setBorder(null);
      scroll.setBackground(APP_BG);
      scroll.getViewport().setBackground(APP_BG);
      scroll.getVerticalScrollBar().setUnitIncrement(16);
      root.add(scroll, BorderLayout.CENTER);

      frame.setContentPane(root);
      frame.setPreferredSize(new Dimension(820, 780));
      frame.pack();
      frame.setLocationRelativeTo(null);
      frame.setVisible(true);
    });
  }

  private static JPanel buildContent() {
    JPanel content = new JPanel();
    content.setLayout(new BoxLayout(content, BoxLayout.Y_AXIS));
    content.setBackground(APP_BG);

    // Input
    JPanel inputCard = makeCard(14);
    inputCard.setLayout(new BoxLayout(inputCard, BoxLayout.Y_AXIS));
    inputCard.setAlignmentX(Component.LEFT_ALIGNMENT);

    JComboBox<String> widthSelector = new JComboBox<>(
      new String[] { "32-bit", "40-bit" }
    );
    widthSelector.setAlignmentX(Component.LEFT_ALIGNMENT);
    inputCard.add(widthSelector);
    inputCard.add(vgap(8));

    JLabel inputLabel = makeCaptionLabel(inputPrompt());
    inputLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    inputCard.add(inputLabel);
    inputCard.add(vgap(5));

    JTextField field = new JTextField("89abcdef");
    field.setFont(new Font(Font.MONOSPACED, Font.PLAIN, 14));
    field.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_FG),
        BorderFactory.createEmptyBorder(6, 8, 6, 8)
      )
    );
    int fieldH = field.getPreferredSize().height * 2;
    field.setPreferredSize(
      new Dimension(field.getPreferredSize().width, fieldH)
    );
    field.setMaximumSize(new Dimension(Integer.MAX_VALUE, fieldH));
    field.setAlignmentX(Component.LEFT_ALIGNMENT);
    inputCard.add(field);
    inputCard.add(vgap(8));

    statusLabel = new JLabel(" ");
    statusLabel.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 12));
    statusLabel.setForeground(MUTED_FG);
    statusLabel.setAlignmentX(Component.LEFT_ALIGNMENT);
    inputCard.add(statusLabel);

    content.add(inputCard);
    content.add(vgap(20));

    // Styles
    content.add(sectionLabel("Styles"));
    content.add(vgap(8));
    JPanel stylesRow = hrow(
      variantCard(BitSquigglesRendererSwing.Style.STANDARD, 200, "Standard"),
      variantCard(
        BitSquigglesRendererSwing.Style.HIGH_CONTRAST,
        200,
        "High Contrast"
      ),
      variantCard(
        BitSquigglesRendererSwing.Style.MONOCHROME,
        200,
        "Monochrome"
      ),
      variantCard(
        BitSquigglesRendererSwing.Style.BLACK_AND_WHITE,
        290,
        "Black and white"
      )
    );
    content.add(stylesRow);
    content.add(vgap(20));

    // Sizes (Standard)
    content.add(sectionLabel("Sizes  (Standard)"));
    content.add(vgap(8));
    JPanel sizesCard = makeCard(12);
    sizesCard.setLayout(new BoxLayout(sizesCard, BoxLayout.X_AXIS));
    sizesCard.setAlignmentX(Component.LEFT_ALIGNMENT);
    boolean first = true;
    for (int sz : new int[] { 160, 120, 80, 56, 40, 28 }) {
      if (!first) sizesCard.add(hgap(14));
      first = false;
      JPanel sub = vstack(CARD_BG);
      VisView v = new VisView(BitSquigglesRendererSwing.Style.STANDARD, sz);
      allViews.add(v);
      v.setAlignmentX(Component.CENTER_ALIGNMENT);
      sub.add(v);
      sub.add(vgap(4));
      JLabel lbl = makeCaptionLabel(sz + " px");
      lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
      sub.add(lbl);
      sizesCard.add(sub);
    }
    sizesCard.add(Box.createHorizontalGlue());
    content.add(sizesCard);
    content.add(vgap(20));

    // Pixel grid ×6
    content.add(sectionLabel("Pixel Grid  (16 \u00d7 22,  \u00d76 zoom)"));
    content.add(vgap(8));
    content.add(pixelRow(6));
    content.add(vgap(20));

    // Pixel grid ×1
    content.add(sectionLabel("Pixel Grid  (16 \u00d7 22,  actual size)"));
    content.add(vgap(8));
    content.add(pixelRow(1));
    content.add(vgap(24));

    // Live update
    Timer debounce = new Timer(80, e -> updateAll(field.getText()));
    debounce.setRepeats(false);
    field.getDocument().addDocumentListener(
      new DocumentListener() {
        void kick() {
          debounce.restart();
        }

        public void insertUpdate(DocumentEvent e) {
          kick();
        }

        public void removeUpdate(DocumentEvent e) {
          kick();
        }

        public void changedUpdate(DocumentEvent e) {
          kick();
        }
      }
    );
    widthSelector.addActionListener(event -> {
      selectedWidth = widthSelector.getSelectedIndex() == 0
        ? BitSquigglesRendererSwing.Width.BITS_32
        : BitSquigglesRendererSwing.Width.BITS_40;
      inputLabel.setText(inputPrompt());
      field.setText(
        selectedWidth == BitSquigglesRendererSwing.Width.BITS_32
          ? "89abcdef"
          : "39527804db"
      );
      debounce.restart();
    });
    updateAll(field.getText());

    return content;
  }

  private static JPanel pixelRow(int cellPx) {
    JPanel card = makeCard(12);
    card.setLayout(new BoxLayout(card, BoxLayout.X_AXIS));
    card.setAlignmentX(Component.LEFT_ALIGNMENT);
    boolean first = true;
    for (
      BitSquigglesRendererSwing.Style style :
        BitSquigglesRendererSwing.Style.values()
    ) {
      if (!first) card.add(hgap(20));
      first = false;
      JPanel sub = vstack(CARD_BG);
      PixelView pv = new PixelView(style, cellPx);
      pixelViews.add(pv);
      pv.setAlignmentX(Component.CENTER_ALIGNMENT);
      sub.add(pv);
      sub.add(vgap(4));
      JLabel lbl = makeCaptionLabel(styleName(style));
      lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
      sub.add(lbl);
      card.add(sub);
    }
    card.add(Box.createHorizontalGlue());
    return card;
  }

  private static void updateAll(String input) {
    try {
      long bits = parseBits(input, selectedWidth);
      BitSquigglesRendererSwing.VisSpec visual =
        selectedWidth == BitSquigglesRendererSwing.Width.BITS_32
          ? BitSquigglesRendererSwing.spec32((int) bits)
          : BitSquigglesRendererSwing.spec40(bits);
      String fallback = visual.fallback() ? " → A| fallback" : "";
      statusLabel.setText(
        String.format(
          selectedWidth == BitSquigglesRendererSwing.Width.BITS_32
            ? "unsigned: %d  ·  mixed: %08x  ·  mode: %s%s"
            : "unsigned: %d  ·  mixed: %010x  ·  mode: %s%s",
          bits,
          visual.mixed(),
          visual.preferredMode().label(),
          fallback
        )
      );
      allViews.forEach(view -> view.setInput(selectedWidth, bits));
      pixelViews.forEach(view -> view.setInput(selectedWidth, bits));
    } catch (IllegalArgumentException e) {
      statusLabel.setText(e.getMessage());
    }
  }

  static long parseBits(
    String input,
    BitSquigglesRendererSwing.Width width
  ) {
    String hex = input.trim();
    if (hex.startsWith("0x") || hex.startsWith("0X")) hex = hex.substring(2);
    int digits = width == BitSquigglesRendererSwing.Width.BITS_32 ? 8 : 10;
    if (!hex.matches("[0-9a-fA-F]{" + digits + "}")) {
      throw new IllegalArgumentException(
        "Enter exactly " + digits + " hexadecimal digits."
      );
    }
    return Long.parseLong(hex, 16);
  }

  private static String inputPrompt() {
    int bits = selectedWidth == BitSquigglesRendererSwing.Width.BITS_32
      ? 32
      : 40;
    int digits = bits / 4;
    return String.format(
      "%d-BIT INPUT (%d HEX DIGITS, BIG-ENDIAN)",
      bits,
      digits
    );
  }

  // =========================================================================
  // Views
  // =========================================================================

  static class VisView extends JComponent {

    private final BitSquigglesRendererSwing.Style style;
    private final int targetWidth;
    private BitSquigglesRendererSwing.VisSpec spec;

    VisView(BitSquigglesRendererSwing.Style style, int width) {
      this.style = style;
      targetWidth = width;
      Dimension d = dimensions(
        width,
        BitSquigglesRendererSwing.Width.BITS_32
      );
      setPreferredSize(d);
      setMinimumSize(d);
      setMaximumSize(d);
    }

    void setInput(BitSquigglesRendererSwing.Width width, long bits) {
      spec = width == BitSquigglesRendererSwing.Width.BITS_32
        ? BitSquigglesRendererSwing.spec32((int) bits, style)
        : BitSquigglesRendererSwing.spec40(bits, style);
      Dimension size = dimensions(targetWidth, width);
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
      revalidate();
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (spec == null) return;
      Graphics2D g2 = (Graphics2D) g.create();
      try {
        if (spec.variant() == BitSquigglesRendererSwing.Width.BITS_32) {
          BitSquigglesRendererSwing.renderSmooth32(
            g2,
            spec,
            getWidth(),
            getHeight()
          );
        } else {
          BitSquigglesRendererSwing.renderSmooth40(
            g2,
            spec,
            getWidth(),
            getHeight()
          );
        }
      } finally {
        g2.dispose();
      }
    }
  }

  static class PixelView extends JComponent {

    private final BitSquigglesRendererSwing.Style style;
    private final int cellPx;
    private BitSquigglesRendererSwing.PixelGrid grid;

    PixelView(BitSquigglesRendererSwing.Style style, int cellPx) {
      this.style = style;
      this.cellPx = cellPx;
      Dimension d = new Dimension(16 * cellPx, 22 * cellPx);
      setPreferredSize(d);
      setMinimumSize(d);
      setMaximumSize(d);
    }

    void setInput(BitSquigglesRendererSwing.Width width, long bits) {
      grid = width == BitSquigglesRendererSwing.Width.BITS_32
        ? BitSquigglesRendererSwing.pixels32((int) bits, style)
        : BitSquigglesRendererSwing.pixels40(bits, style);
      Dimension size = new Dimension(
        grid.width() * cellPx,
        grid.height() * cellPx
      );
      setPreferredSize(size);
      setMinimumSize(size);
      setMaximumSize(size);
      revalidate();
      repaint();
    }

    @Override
    protected void paintComponent(Graphics g) {
      super.paintComponent(g);
      if (grid == null) return;
      Graphics2D g2 = (Graphics2D) g.create();
      g2.setRenderingHint(
        RenderingHints.KEY_ANTIALIASING,
        RenderingHints.VALUE_ANTIALIAS_OFF
      );
      try {
        if (grid.variant() == BitSquigglesRendererSwing.Width.BITS_32) {
          BitSquigglesRendererSwing.renderRaster32(g2, grid, cellPx);
        } else {
          BitSquigglesRendererSwing.renderRaster40(g2, grid, cellPx);
        }
      } finally {
        g2.dispose();
      }
    }
  }

  // =========================================================================
  // UI helpers
  // =========================================================================

  private static JPanel variantCard(
    BitSquigglesRendererSwing.Style style,
    int size,
    String label
  ) {
    JPanel card = makeCard(12);
    card.setLayout(new BoxLayout(card, BoxLayout.Y_AXIS));
    VisView v = new VisView(style, size);
    allViews.add(v);
    v.setAlignmentX(Component.CENTER_ALIGNMENT);
    card.add(v);
    card.add(vgap(6));
    JLabel lbl = makeCaptionLabel(label);
    lbl.setAlignmentX(Component.CENTER_ALIGNMENT);
    card.add(lbl);
    return card;
  }

  private static JPanel hrow(JPanel... cards) {
    JPanel row = new JPanel();
    row.setLayout(new BoxLayout(row, BoxLayout.X_AXIS));
    row.setBackground(APP_BG);
    row.setAlignmentX(Component.LEFT_ALIGNMENT);
    for (int index = 0; index < cards.length; index++) {
      if (index != 0) row.add(hgap(12));
      row.add(cards[index]);
    }
    Dimension pref = row.getPreferredSize();
    row.setMaximumSize(new Dimension(Integer.MAX_VALUE, pref.height));
    return row;
  }

  private static JPanel makeCard(int pad) {
    JPanel p = new JPanel();
    p.setBackground(CARD_BG);
    p.setBorder(
      BorderFactory.createCompoundBorder(
        BorderFactory.createLineBorder(BORDER_FG),
        BorderFactory.createEmptyBorder(pad, pad, pad, pad)
      )
    );
    return p;
  }

  private static JPanel vstack(Color bg) {
    JPanel p = new JPanel();
    p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
    p.setBackground(bg);
    return p;
  }

  private static JLabel sectionLabel(String text) {
    JLabel l = new JLabel(text);
    l.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 13));
    l.setForeground(TEXT_FG);
    l.setAlignmentX(Component.LEFT_ALIGNMENT);
    return l;
  }

  private static JLabel makeCaptionLabel(String text) {
    JLabel l = new JLabel(text);
    l.setFont(new Font(Font.SANS_SERIF, Font.PLAIN, 11));
    l.setForeground(MUTED_FG);
    return l;
  }

  private static Component vgap(int h) {
    return Box.createVerticalStrut(h);
  }

  private static Component hgap(int w) {
    return Box.createHorizontalStrut(w);
  }

  private static String styleName(BitSquigglesRendererSwing.Style style) {
    return switch (style) {
      case STANDARD -> "Standard";
      case HIGH_CONTRAST -> "High Contrast";
      case MONOCHROME -> "Monochrome";
      case BLACK_AND_WHITE -> "Black and white";
    };
  }

  private static Dimension dimensions(
    int width,
    BitSquigglesRendererSwing.Width variant
  ) {
    BitSquigglesRendererSwing.PixelGrid grid =
      variant == BitSquigglesRendererSwing.Width.BITS_32
      ? BitSquigglesRendererSwing.pixels32(0)
      : BitSquigglesRendererSwing.pixels40(0);
    return new Dimension(
      width,
      (int) Math.round((width * (double) grid.height()) / grid.width())
    );
  }
}
