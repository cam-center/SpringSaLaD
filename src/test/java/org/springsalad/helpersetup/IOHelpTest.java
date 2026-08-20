package org.springsalad.helpersetup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@link IOHelp} is the formatting layer for every number that reaches the model file, which the
 * LangevinNoVis01 solver parses. The DF index is a lossy truncation of the user's value as the
 * solver sees it, not a display choice -- see {@link org.springsalad.langevinsetup.SiteTypeTest}.
 */
class IOHelpTest {

    @Test
    @DisplayName("DF[n] formats to exactly n decimal places")
    void dfIndexIsDecimalPlaces() {
        for (int n = 1; n < IOHelp.DF.length; n++) {
            String formatted = IOHelp.DF[n].format(1.0);
            int decimals = formatted.length() - formatted.indexOf('.') - 1;
            assertEquals(n, decimals, "DF[" + n + "] produced '" + formatted + "'");
        }
    }

    @Test
    @DisplayName("DF rounds rather than truncates, and drops precision beyond its index")
    void dfIsLossy() {
        assertEquals("0.123", IOHelp.DF[3].format(0.123456));
        assertEquals("0.12346", IOHelp.DF[5].format(0.123456));
        // The value a 3-decimal field would have silently lost:
        assertEquals("0.001", IOHelp.DF[3].format(0.001));
        assertEquals("0.000", IOHelp.DF[3].format(0.0001), "sub-milli values vanish at DF[3]");
        assertEquals("0.00010", IOHelp.DF[5].format(0.0001));
    }

    @Test
    @DisplayName("getNameInQuotes reassembles a quoted name containing spaces")
    void getNameInQuotesHandlesSpaces() {
        Scanner sc = new Scanner("\"My Site Name\" trailing");
        assertEquals("My Site Name", IOHelp.getNameInQuotes(sc));
        assertEquals("trailing", sc.next());
    }

    @Test
    @DisplayName("getNameInQuotes handles a single-token name")
    void getNameInQuotesSingleToken() {
        assertEquals("Site0", IOHelp.getNameInQuotes(new Scanner("\"Site0\"")));
    }

    @Test
    @DisplayName("DECIMAL SEPARATOR HAZARD: the DF pattern is locale-sensitive")
    void decimalFormatPatternIsLocaleSensitive() {
        // IOHelp.DF is built from `new DecimalFormat("0.00000")` in a static initializer, so it
        // binds the JVM's default locale at class-load time. Under a comma locale that writes
        // "1,50000" into the model file, which the solver's Double.parseDouble cannot read.
        // This test documents the mechanism; it cannot exercise IOHelp.DF itself, because that
        // array is already initialized by the time any test can change the default locale.
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1,50000", new DecimalFormat("0.00000").format(1.5),
                    "if this ever changes, the hazard below is gone and this test can go with it");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("IOHelp.DF writes '.' under the locale this JVM actually booted with")
    void dfUsesDotUnderCurrentLocale() {
        // Guards the hazard above for whatever locale CI and developers actually run in.
        assertTrue(IOHelp.DF[5].format(1.5).contains("."),
                "IOHelp.DF wrote '" + IOHelp.DF[5].format(1.5) + "' under default locale "
                        + Locale.getDefault() + "; the solver cannot parse that");
    }
}
