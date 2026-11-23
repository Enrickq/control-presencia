package software.ulpgc.register.admin;

import software.ulpgc.register.admin.adapter.http.HttpAdministrationGateway;
import software.ulpgc.register.admin.adapter.swing.AdminPanel;
import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.event.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

public final class AdminMain {
    private AdminMain() {}

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Uso: java -jar control-presencia-frontend-admin-0.1.0-SNAPSHOT.jar [URL_BASE]");
            System.out.println("La URL indicada prevalece sobre API_BASE_URL. Valor por defecto: http://localhost:7070");
            return;
        }
        if (args.length > 1) {
            System.err.println("Indica una sola URL base o utiliza --help.");
            return;
        }
        String url = args.length == 1 ? args[0] : System.getenv("API_BASE_URL");
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            var gateway = new HttpAdministrationGateway(URI.create(url == null || url.isBlank() ? "http://localhost:7070" : url.strip()), client);
            if (GraphicsEnvironment.isHeadless()) {
                client.close();
                System.err.println("La administración necesita un escritorio gráfico.");
                return;
            }
            SwingUtilities.invokeLater(() -> {
                JFrame frame = new JFrame("Control de Presencia · Administración");
                AdminPanel panel = new AdminPanel(gateway);
                frame.setContentPane(panel);
                frame.setDefaultCloseOperation(WindowConstants.DO_NOTHING_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosing(WindowEvent e) { if (panel.canClose()) frame.dispose(); }
                    @Override public void windowClosed(WindowEvent e) { client.shutdownNow(); }
                });
                frame.pack();
                frame.setLocationByPlatform(true);
                frame.setVisible(true);
            });
        } catch (IllegalArgumentException error) {
            client.close();
            System.err.println("Revisa la URL del servidor. " + error.getMessage());
        }
    }
}
