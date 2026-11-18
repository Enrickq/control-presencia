package software.ulpgc.register.frontend;

import software.ulpgc.register.frontend.adapter.http.HttpPunchGateway;
import software.ulpgc.register.frontend.adapter.swing.PunchPanel;
import software.ulpgc.register.frontend.application.PunchController;

import javax.swing.*;
import java.awt.*;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;

public final class FrontendMain {
    private FrontendMain() { }

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Uso: java -jar control-presencia-frontend-register-0.1.0-SNAPSHOT.jar [URL_BASE]");
            System.out.println("URL_BASE prevalece sobre API_BASE_URL. Valor predeterminado: http://localhost:7070");
            return;
        }
        final HttpPunchGateway gateway;
        try {
            gateway = new HttpPunchGateway(baseUrl(args, System.getenv("API_BASE_URL")));
        } catch (IllegalArgumentException error) {
            System.err.println("Configuración no válida: " + error.getMessage());
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            gateway.close();
            System.err.println("La aplicación de registro necesita un escritorio gráfico.");
            return;
        }
        SwingUtilities.invokeLater(() -> open(gateway));
    }

    static URI baseUrl(String[] args, String environment) {
        if (args.length > 1)
            throw new IllegalArgumentException("Indica una sola URL base o utiliza --help.");
        String value = args.length == 1 ? args[0]
                : environment == null || environment.isBlank() ? "http://localhost:7070" : environment;
        return URI.create(value.strip());
    }

    private static void open(HttpPunchGateway gateway) {
        PunchPanel panel = new PunchPanel();
        PunchController controller = new PunchController(gateway, panel, SwingUtilities::invokeLater);
        panel.bind(controller);

        JFrame frame = new JFrame("Control de Presencia · Registro");
        frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
        frame.setContentPane(panel);
        frame.addWindowListener(new WindowAdapter() {
            @Override
            public void windowClosing(WindowEvent event) {
                if (controller.isBusy()) {
                    panel.showError("Hay un fichaje en curso. Espera su resultado antes de cerrar la ventana.");
                    return;
                }
                frame.dispose();
                gateway.close();
            }
        });
        frame.pack();
        frame.setMinimumSize(new Dimension(600, 650));
        frame.setLocationRelativeTo(null);
        frame.setVisible(true);
    }
}
