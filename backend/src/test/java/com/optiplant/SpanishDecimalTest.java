package com.optiplant;

import static org.assertj.core.api.Assertions.assertThat;

import com.optiplant.common.text.SpanishDecimal;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

class SpanishDecimalTest {
    @Test
    void messagesUseACommaForDecimalsAndADotForThousandsKeepingTheScale() {
        assertThat(SpanishDecimal.format(new BigDecimal("3.000"))).isEqualTo("3,000");
        assertThat(SpanishDecimal.format(new BigDecimal("1250.500"))).isEqualTo("1.250,500");
        assertThat(SpanishDecimal.format(new BigDecimal("1234567"))).isEqualTo("1.234.567");
        assertThat(SpanishDecimal.format(new BigDecimal("-240000.00"))).isEqualTo("−240.000,00");
        assertThat(SpanishDecimal.format(new BigDecimal("0.000"))).isEqualTo("0,000");
    }
}
