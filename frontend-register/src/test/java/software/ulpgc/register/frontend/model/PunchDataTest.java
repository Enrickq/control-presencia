package software.ulpgc.register.frontend.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.*;

class PunchDataTest {
    @ParameterizedTest
    @NullAndEmptySource
    @ValueSource(strings = {" ", "EMP\t1", "EMP\n1", "EMP\r1", "\tEMP", "EMP\u007f"})
    void rejectsEmptyAndControlCharactersInEveryField(String value) {
        assertThatThrownBy(() -> new PunchData(value, "Ana", "SITE", "Centro", PunchType.IN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PunchData("EMP", value, "SITE", "Centro", PunchType.IN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PunchData("EMP", "Ana", value, "Centro", PunchType.IN)).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> new PunchData("EMP", "Ana", "SITE", value, PunchType.IN)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void stripsWhitespaceAndKeepsNamesWithUnicode() {
        assertThat(new PunchData(" EMP ", " José ", " SITE ", " Recepción ", PunchType.OUT))
                .isEqualTo(new PunchData("EMP", "José", "SITE", "Recepción", PunchType.OUT));
    }

    @Test
    void requiresPunchType() {
        assertThatThrownBy(() -> new PunchData("EMP", "Ana", "SITE", "Centro", null))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
