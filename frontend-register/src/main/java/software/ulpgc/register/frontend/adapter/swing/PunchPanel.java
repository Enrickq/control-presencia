package software.ulpgc.register.frontend.adapter.swing;

import software.ulpgc.register.frontend.application.PunchController;
import software.ulpgc.register.frontend.model.PunchReceipt;
import software.ulpgc.register.frontend.model.PunchType;

import javax.swing.*;
import javax.swing.border.EmptyBorder;
import java.awt.*;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;

public final class PunchPanel extends JPanel implements PunchController.View {
    private static final Color NAVY = new Color(27, 49, 75);
    private static final Color MUTED = new Color(86, 101, 119);
    private static final Color GREEN = new Color(25, 111, 84);
    private static final DateTimeFormatter TIME =
            DateTimeFormatter.ofPattern("dd/MM/uuuu HH:mm:ss 'UTC'").withZone(ZoneOffset.UTC);

    private final JTextField employeeId = field("employeeId");
    private final JTextField employeeName = field("employeeName");
    private final JTextField placeId = field("placeId");
    private final JTextField placeName = field("placeName");
    private final JButton entry = button("Registrar entrada", "entry", GREEN);
    private final JButton exit = button("Registrar salida", "exit", NAVY);
    private final JTextArea status = new JTextArea(5, 40);

    public PunchPanel() {
        setLayout(new BorderLayout(0, 20));
        setBackground(new Color(244, 247, 250));
        setBorder(new EmptyBorder(28, 32, 26, 32));

        JPanel heading = column();
        JLabel title = new JLabel("Control de presencia");
        title.setFont(font(Font.BOLD, 28));
        title.setForeground(NAVY);
        heading.add(title);
        heading.add(Box.createVerticalStrut(6));
        JLabel subtitle = new JLabel("Registra tu entrada o salida del centro de trabajo.");
        subtitle.setFont(font(Font.PLAIN, 14));
        subtitle.setForeground(MUTED);
        heading.add(subtitle);
        add(heading, BorderLayout.NORTH);

        JPanel content = column();
        content.add(section("Empleado", "Identificador del empleado", employeeId,
                "Nombre del empleado", employeeName));
        content.add(Box.createVerticalStrut(16));
        content.add(section("Centro de trabajo", "Identificador del centro", placeId,
                "Nombre del centro", placeName));
        content.add(Box.createVerticalStrut(20));
        JPanel actions = new JPanel(new GridLayout(1, 2, 12, 0));
        actions.setOpaque(false);
        actions.add(entry);
        actions.add(exit);
        actions.setAlignmentX(Component.LEFT_ALIGNMENT);
        content.add(actions);
        add(content, BorderLayout.CENTER);

        status.setName("status");
        status.setEditable(false);
        status.setLineWrap(true);
        status.setWrapStyleWord(true);
        status.setFont(font(Font.PLAIN, 14));
        status.setBackground(Color.WHITE);
        status.setForeground(MUTED);
        status.setBorder(BorderFactory.createCompoundBorder(
                BorderFactory.createLineBorder(new Color(216, 226, 235)),
                new EmptyBorder(12, 14, 12, 14)));
        status.getAccessibleContext().setAccessibleName("Resultado del fichaje");
        status.setText("Completa los cuatro campos y elige entrada o salida.\n"
                + "La hora del fichaje se asigna en el servidor.");
        JScrollPane result = new JScrollPane(status,
                ScrollPaneConstants.VERTICAL_SCROLLBAR_AS_NEEDED,
                ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER);
        result.setBorder(BorderFactory.createEmptyBorder());
        result.setPreferredSize(new Dimension(580, 128));
        add(result, BorderLayout.SOUTH);
        setPreferredSize(new Dimension(650, 650));
    }

    public void bind(PunchController controller) {
        entry.addActionListener(event -> submit(controller, PunchType.IN));
        exit.addActionListener(event -> submit(controller, PunchType.OUT));
    }

    private void submit(PunchController controller, PunchType type) {
        controller.submit(employeeId.getText(), employeeName.getText(),
                placeId.getText(), placeName.getText(), type);
    }

    @Override
    public void setBusy(boolean busy) {
        List.of(employeeId, employeeName, placeId, placeName, entry, exit)
                .forEach(component -> component.setEnabled(!busy));
        if (busy) {
            status.setForeground(MUTED);
            status.setText("Enviando el fichaje…\nEspera la confirmación antes de cerrar la ventana.");
        }
    }

    @Override
    public void showReceipt(PunchReceipt receipt) {
        status.setForeground(GREEN);
        String action = receipt.type() == PunchType.IN ? "Entrada registrada" : "Salida registrada";
        status.setText(action + " correctamente.\n"
                + "Empleado: " + receipt.employeeId() + " · Centro: " + receipt.placeId() + "\n"
                + "Hora del servidor: " + TIME.format(receipt.timestamp()));
        status.setCaretPosition(0);
    }

    @Override
    public void showError(String message) {
        status.setForeground(new Color(151, 47, 37));
        status.setText(message);
        status.setCaretPosition(0);
    }

    private static JPanel section(String title, String firstLabel, JTextField first,
                                  String secondLabel, JTextField second) {
        JPanel card = new JPanel(new GridBagLayout());
        card.setBackground(Color.WHITE);
        card.setBorder(new EmptyBorder(16, 18, 16, 18));
        card.setAlignmentX(Component.LEFT_ALIGNMENT);
        GridBagConstraints cell = new GridBagConstraints();
        cell.gridx = 0;
        cell.gridy = 0;
        cell.gridwidth = 2;
        cell.weightx = 1;
        cell.fill = GridBagConstraints.HORIZONTAL;
        JLabel heading = new JLabel(title);
        heading.setFont(font(Font.BOLD, 16));
        heading.setForeground(NAVY);
        cell.insets = new Insets(0, 0, 12, 0);
        card.add(heading, cell);
        cell.gridwidth = 1;
        cell.gridy = 1;
        cell.insets = new Insets(0, 0, 6, 12);
        card.add(label(firstLabel, first), cell);
        cell.gridx = 1;
        cell.insets = new Insets(0, 0, 6, 0);
        card.add(label(secondLabel, second), cell);
        cell.gridy = 2;
        cell.gridx = 0;
        cell.insets = new Insets(0, 0, 0, 12);
        card.add(first, cell);
        cell.gridx = 1;
        cell.insets = new Insets(0, 0, 0, 0);
        card.add(second, cell);
        return card;
    }

    private static JLabel label(String text, JTextField target) {
        JLabel label = new JLabel(text);
        label.setLabelFor(target);
        label.setFont(font(Font.PLAIN, 12));
        label.setForeground(MUTED);
        return label;
    }

    private static JTextField field(String name) {
        JTextField field = new JTextField(18);
        field.setName(name);
        field.setFont(font(Font.PLAIN, 15));
        field.setMargin(new Insets(8, 9, 8, 9));
        return field;
    }

    private static JButton button(String text, String name, Color color) {
        JButton button = new JButton(text);
        button.setName(name);
        button.setFont(font(Font.BOLD, 15));
        button.setForeground(Color.WHITE);
        button.setBackground(color);
        button.setOpaque(true);
        button.setBorderPainted(false);
        button.setMargin(new Insets(14, 16, 14, 16));
        return button;
    }

    private static JPanel column() {
        JPanel panel = new JPanel();
        panel.setLayout(new BoxLayout(panel, BoxLayout.Y_AXIS));
        panel.setOpaque(false);
        return panel;
    }

    private static Font font(int style, int size) {
        return new Font("Segoe UI", style, size);
    }
}
