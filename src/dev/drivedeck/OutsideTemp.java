package dev.drivedeck;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Outside temperature from the car's own sensor. The vendor UI (com.ts.MainUI, CanFunc.UpdateOutTemp)
 * reads it off the CAN bus and publishes it, already formatted, in the system property
 * forfan.user.info: "14.5℃", "58.1℉", or " " when there is no reading. Its status bar shows the same
 * string, which DriveDeck covers when full screen.
 */
final class OutsideTemp {
    static final String PROPERTY = "forfan.user.info";
    private static final Pattern READING = Pattern.compile("(-?\\d+(?:[.,]\\d+)?)\\s*(℉|°?\\s*F)?");

    /** With a decimal point: "14.5". */
    final String value;
    /** "°C" or "°F". */
    final String unit;

    private OutsideTemp(String value, String unit) {
        this.value = value;
        this.unit = unit;
    }

    /** The current reading, or null when the unit has none. */
    static OutsideTemp read() {
        return parse(systemProperty(PROPERTY));
    }

    static OutsideTemp parse(String s) {
        if (s == null) return null;
        Matcher m = READING.matcher(s);
        if (!m.find()) return null;
        return new OutsideTemp(m.group(1).replace(',', '.'), m.group(2) != null ? "°F" : "°C");
    }

    private static String systemProperty(String key) {
        try {
            return (String) Class.forName("android.os.SystemProperties")
                    .getMethod("get", String.class).invoke(null, key);
        } catch (Exception e) {
            return null;
        }
    }
}
