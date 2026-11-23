package software.ulpgc.register.admin.adapter.swing;

import javax.swing.*;
import java.awt.*;

final class Fields extends JPanel {
    Fields() {
        super(new GridBagLayout());
        setBorder(BorderFactory.createEmptyBorder(8, 8, 8, 8));
    }

    void row(String title, JComponent field) {
        GridBagConstraints c = new GridBagConstraints();
        c.gridy = getComponentCount() / 2;
        c.insets = new Insets(4, 4, 4, 4);
        c.anchor = GridBagConstraints.LINE_START;
        c.gridx = 0;
        JLabel label = new JLabel(title);
        label.setLabelFor(field);
        add(label, c);
        c.gridx = 1;
        c.fill = GridBagConstraints.HORIZONTAL;
        c.weightx = 1;
        add(field, c);
    }
}
