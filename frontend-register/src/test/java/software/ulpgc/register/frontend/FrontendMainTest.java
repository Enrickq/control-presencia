package software.ulpgc.register.frontend;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class FrontendMainTest {
    @Test
    void defaultsToTheLocalBackendWhenNoUrlWasProvided() {
        assertThat(FrontendMain.baseUrl(new String[0], null).toString()).isEqualTo("http://localhost:7070");
        assertThat(FrontendMain.baseUrl(new String[0], " ").toString()).isEqualTo("http://localhost:7070");
    }

    @Test
    void acceptsEnvironmentConfigurationAndLetsTheArgumentOverrideIt() {
        assertThat(FrontendMain.baseUrl(new String[0], "http://localhost:8181").toString())
                .isEqualTo("http://localhost:8181");
        assertThat(FrontendMain.baseUrl(new String[]{"http://localhost:9090"}, "http://localhost:8181").toString())
                .isEqualTo("http://localhost:9090");
    }

    @Test
    void rejectsExtraArgumentsAndMalformedUris() {
        assertThatThrownBy(() -> FrontendMain.baseUrl(new String[]{"one", "two"}, null))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> FrontendMain.baseUrl(new String[]{"http://some invalid host"}, null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
