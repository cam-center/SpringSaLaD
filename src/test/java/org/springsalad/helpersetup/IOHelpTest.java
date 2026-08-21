package org.springsalad.helpersetup;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.text.DecimalFormat;
import java.util.Locale;
import java.util.Scanner;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
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
    @DisplayName("DF writes '.' even under a comma-decimal locale")
    void dfIsLocaleIndependent() {
        // The bug this replaced: IOHelp.DF used to be built with the bare DecimalFormat(pattern)
        // constructor, which binds the JVM's default locale. On de/fr/es/pt/ru that wrote
        // "D 1,50000" into the model file, and the solver's Double.parseDouble cannot read it --
        // so the app silently produced files it could not run.
        Locale previous = Locale.getDefault();
        try {
            for (Locale comma : new Locale[]{Locale.GERMANY, Locale.FRANCE, Locale.ITALY}) {
                Locale.setDefault(comma);
                assertEquals("1.50000", IOHelp.DF[5].format(1.5), "under locale " + comma);
                assertEquals("0.001", IOHelp.DF[3].format(0.001), "under locale " + comma);
                assertFalse(IOHelp.scientificFormat.format(0.000123).contains(","),
                        "scientificFormat used a comma under " + comma);
            }
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    @DisplayName("a bare DecimalFormat still is locale-sensitive -- pinning is what fixes it")
    void bareDecimalFormatShowsWhyPinningIsNeeded() {
        // Guards the reason the pinning exists: if this ever stops producing a comma, someone has
        // changed the platform's behaviour and the note in IOHelp can be revisited.
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.GERMANY);
            assertEquals("1,50000", new DecimalFormat("0.00000").format(1.5));
            assertEquals("1.50000", IOHelp.decimalFormat("0.00000").format(1.5),
                    "IOHelp.decimalFormat must not follow the default locale");
        } finally {
            Locale.setDefault(previous);
        }
    }
}
