package software.ulpgc.register.admin.adapter.swing;

import software.ulpgc.register.admin.model.Forms.*;
import javax.swing.*;
import java.awt.*;
import java.time.DayOfWeek;
import java.time.format.TextStyle;
import java.util.*;

final class ScheduleEditor extends JPanel {
    private final JTextField id = new JTextField(14);
    private final JTextField name = new JTextField(24);
    private final JComboBox<String> type = new JComboBox<>(new String[]{"Continuo", "Partido", "Por turnos"});
    private final JTextField continuous = new JTextField("08:00-16:00");
    private final JTextField split = new JTextField("08:00-12:00, 13:00-17:00");
    private final Map<DayOfWeek, JCheckBox> days = new EnumMap<>(DayOfWeek.class);
    private final Map<DayOfWeek, JTextField> shifts = new EnumMap<>(DayOfWeek.class);
    private final CardLayout layout = new CardLayout();
    private final JPanel cards = new JPanel(layout);

    ScheduleEditor() {
        super(new BorderLayout());
        Fields fields = new Fields();
        fields.row("Código de horario", id);
        fields.row("Nombre del horario", name);
        fields.row("Tipo de horario", type);
        JPanel calendar = new JPanel(new FlowLayout(FlowLayout.LEADING, 4, 0));
        Fields weekly = new Fields();
        var es = Locale.forLanguageTag("es");
        for (DayOfWeek day : DayOfWeek.values()) {
            JCheckBox check = new JCheckBox(day.getDisplayName(TextStyle.SHORT, es), day.getValue() <= 5);
            days.put(day, check);
            calendar.add(check);
            JTextField shift = new JTextField();
            shifts.put(day, shift);
            weekly.row(day.getDisplayName(TextStyle.FULL, es), shift);
        }
        Fields regular = new Fields();
        regular.row("Días de trabajo", calendar);
        CardLayout timeLayout = new CardLayout();
        JPanel times = new JPanel(timeLayout);
        times.add(continuous, "Continuo");
        times.add(split, "Partido");
        regular.row("Tramos (HH:mm-HH:mm)", times);
        cards.add(regular, "regular");
        JPanel turnos = new JPanel(new BorderLayout());
        turnos.add(new JLabel("Un tramo por día (08:00-16:00). Deja en blanco los días libres."), BorderLayout.NORTH);
        turnos.add(weekly, BorderLayout.CENTER);
        cards.add(turnos, "turnos");
        add(fields, BorderLayout.NORTH);
        add(cards, BorderLayout.CENTER);
        type.addActionListener(e -> {
            layout.show(cards, type.getSelectedIndex() == 2 ? "turnos" : "regular");
            timeLayout.show(times, (String) type.getSelectedItem());
        });
    }

    Schedule read() {
        EnumSet<DayOfWeek> working = EnumSet.noneOf(DayOfWeek.class);
        days.forEach((day, box) -> { if (box.isSelected()) working.add(day); });
        return switch (type.getSelectedIndex()) {
            case 0 -> Schedule.continuous(id.getText(), name.getText(), Slot.parse(continuous.getText()), working);
            case 1 -> Schedule.split(id.getText(), name.getText(),
                    Arrays.stream(split.getText().split(",", -1)).map(Slot::parse).toList(), working);
            default -> {
                Map<DayOfWeek, Slot> weekly = new EnumMap<>(DayOfWeek.class);
                shifts.forEach((day, field) -> {
                    if (!field.getText().isBlank()) weekly.put(day, Slot.parse(field.getText()));
                });
                yield Schedule.shift(id.getText(), name.getText(), weekly);
            }
        };
    }
}
