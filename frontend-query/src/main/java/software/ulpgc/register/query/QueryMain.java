package software.ulpgc.register.query;

import software.ulpgc.register.query.adapter.http.HttpAttendanceGateway;
import software.ulpgc.register.query.adapter.swing.QueryPanel;
import software.ulpgc.register.query.application.QueryController;

import javax.swing.*;
import java.awt.GraphicsEnvironment;
import java.awt.event.WindowAdapter;
import java.awt.event.WindowEvent;
import java.net.URI;
import java.net.http.HttpClient;
import java.time.Duration;

public final class QueryMain {
    private QueryMain() { }

    public static void main(String[] args) {
        if (args.length == 1 && "--help".equals(args[0])) {
            System.out.println("Uso: java -jar control-presencia-frontend-query-0.1.0-SNAPSHOT.jar [URL_BASE]");
            System.out.println("La URL indicada prevalece sobre API_BASE_URL. Por defecto se usa http://localhost:7070.");
            return;
        }
        if (args.length > 1) {
            System.err.println("Indica una URL base o utiliza --help.");
            return;
        }
        if (GraphicsEnvironment.isHeadless()) {
            System.err.println("La consulta de asistencia necesita un escritorio gráfico.");
            return;
        }
        String url = args.length == 1 ? args[0] : System.getenv("API_BASE_URL");
        URI base;
        try {
            base = URI.create(url == null || url.isBlank() ? "http://localhost:7070" : url.strip());
        } catch (IllegalArgumentException error) {
            System.err.println("La URL del servidor no es válida.");
            return;
        }
        HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
        try {
            var gateway = new HttpAttendanceGateway(base, client);
            SwingUtilities.invokeLater(() -> {
                QueryPanel panel = new QueryPanel();
                panel.bind(new QueryController(gateway, panel, SwingUtilities::invokeLater));
                JFrame frame = new JFrame("Control de Presencia · Consulta de asistencia");
                frame.setContentPane(panel);
                frame.setDefaultCloseOperation(WindowConstants.DISPOSE_ON_CLOSE);
                frame.addWindowListener(new WindowAdapter() {
                    @Override public void windowClosed(WindowEvent event) { client.shutdownNow(); }
                });
                frame.pack();
                frame.setLocationByPlatform(true);
                frame.setVisible(true);
            });
        } catch (IllegalArgumentException error) {
            client.shutdownNow();
            System.err.println(error.getMessage());
        }
    }
}
