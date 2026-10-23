package com.gathin.tvhome;

import android.content.Context;
import android.content.Intent;
import android.media.tv.TvInputInfo;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Switching the TV's input source on the sets whose framework does not answer the standard way.
 *
 * <p>Most TVs take ACTION_VIEW on the passthrough channel URI and that is the whole story. Xiaomi's
 * China firmware does not: it registers the ports with the framework (so the desk can list them) but
 * nothing answers that URI, and the only way in is its own player, which takes an integer naming the
 * port to switch to — {@code com.xiaomi.mitv.tvplayer.EXTSRC_PLAY} with the extra {@code input}. The
 * port numbers are the vendor's own (HDMI 1/2/3 are 23/24/25, AV is 2), not the framework's.
 *
 * <p>The mapping is worked out from the ports themselves, so it needs no configuration: the HDMI
 * ports are numbered in the order the framework reports them, which is how the vendor numbers them
 * too (the framework's own hardware ids start at HW5 for HDMI 1 on these sets, which is where the
 * vendor's numbering comes from).
 */
final class VendorInputs {

    private static final String XIAOMI_PLAYER = "com.xiaomi.mitv.tvplayer";
    private static final String XIAOMI_SOURCE_ACTION = "com.xiaomi.mitv.tvplayer.EXTSRC_PLAY";
    private static final String XIAOMI_SOURCE_ACTIVITY = "com.xiaomi.mitv.tvplayer.ExternalSourceActivity";
    private static final String XIAOMI_ATV_ACTION = "com.xiaomi.mitv.tvplayer.ATV_PLAY";
    private static final String XIAOMI_ATV_ACTIVITY = "com.xiaomi.mitv.tvplayer.AtvActivity";

    /** The vendor's port numbers: HDMI 1/2/3 and AV. */
    private static final int SOURCE_HDMI_1 = 23;
    private static final int SOURCE_AV = 2;

    private VendorInputs() {
    }

    /** Whether this set has Xiaomi's player, the one that can switch its sources. */
    static boolean hasXiaomiPlayer(Context ctx) {
        return installed(ctx, XIAOMI_PLAYER);
    }

    /**
     * Whether this set needs the vendor's port numbers: China's firmware, and only that.
     *
     * <p>China's firmware registers the ports with the framework (so the desk can list them) but
     * the standard URI is either refused or, worse, accepted and ignored — the TV then just sits
     * there. The vendor's player ({@code com.xiaomi.mitv.tvplayer}) is the only way in, and it
     * takes the port number as an int extra.
     *
     * <p>The sets sold for other markets carry a different app ({@code com.mitv.livetv}), which
     * answers the standard URI properly and switches the port. Those are left alone, so the two
     * kinds never get in each other's way.
     */
    static boolean isChinaFirmware(Context ctx) {
        return installed(ctx, XIAOMI_PLAYER) && !installed(ctx, "com.mitv.livetv");
    }

    /**
     * The vendor's number for every port of this set, keyed by the framework's input id. Empty when
     * this is not one of the sets that needs it.
     *
     * <p>The HDMI ports are numbered in the order the framework reports them: the vendor counts them
     * the same way (HDMI 1/2/3 are 23/24/25 on every Xiaomi set measured). The order is taken from
     * the framework's own hardware ids rather than from the labels, because the labels are whatever
     * the vendor felt like calling the ports on that model.
     */
    static Map<String, Integer> sourceNumbers(List<TvInputInfo> inputs) {
        Map<String, Integer> out = new HashMap<>();
        if (inputs == null || inputs.isEmpty()) return out;
        List<TvInputInfo> hdmi = new ArrayList<>();
        for (TvInputInfo info : inputs) {
            if (info.getType() == TvInputInfo.TYPE_HDMI) hdmi.add(info);
        }
        // Insertion sort by the number in the hardware id. Done by hand because there are a handful
        // of ports and the order has to be exact: "HW10" comes after "HW2", which a comparison of
        // the text would get wrong, and a port in the wrong place means the wrong HDMI.
        for (int i = 1; i < hdmi.size(); i++) {
            TvInputInfo here = hdmi.get(i);
            int j = i - 1;
            while (j >= 0 && hardwareNumber(hdmi.get(j).getId()) > hardwareNumber(here.getId())) {
                hdmi.set(j + 1, hdmi.get(j));
                j--;
            }
            hdmi.set(j + 1, here);
        }
        for (int i = 0; i < hdmi.size(); i++) {
            // Only three HDMI ports have a vendor number; a fourth id the board reports has no port.
            if (i > 2) break;
            out.put(hdmi.get(i).getId(), SOURCE_HDMI_1 + i);
        }
        for (TvInputInfo info : inputs) {
            if (info.getType() == TvInputInfo.TYPE_HDMI) continue;
            if (isComposite(info)) out.put(info.getId(), SOURCE_AV);
        }
        return out;
    }

    /** The vendor's number for one port, or null when this port has none. */
    static Integer sourceNumber(String inputId, Map<String, Integer> numbers) {
        return numbers == null ? null : numbers.get(inputId);
    }

    /**
     * The intent that switches to a port: the vendor's player, named directly, with the port number
     * as the extra "input". The bare action is the second try, for a build where the activity is not
     * exported under its own name but does answer the action.
     */
    static List<Intent> sourceIntents(int number) {
        List<Intent> out = new ArrayList<>();
        out.add(new Intent(XIAOMI_SOURCE_ACTION)
                .setClassName(XIAOMI_PLAYER, XIAOMI_SOURCE_ACTIVITY)
                .putExtra("input", number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        out.add(new Intent(XIAOMI_SOURCE_ACTION)
                .putExtra("input", number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return out;
    }

    /**
     * The intents that open the set's own analogue TV (ATV) player: the activity named directly,
     * and the bare action for a build where it answers that instead.
     */
    static List<Intent> analogueTvIntents() {
        List<Intent> out = new ArrayList<>();
        out.add(new Intent(XIAOMI_ATV_ACTION)
                .setClassName(XIAOMI_PLAYER, XIAOMI_ATV_ACTIVITY)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        out.add(new Intent(XIAOMI_ATV_ACTION)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
        return out;
    }

    /**
     * The first of the intents that something on this set can actually handle, or null when none
     * can. Asked before the row is offered, so the menu never shows a dead entry.
     */
    static Intent firstResolvable(Context ctx, List<Intent> intents) {
        for (Intent i : intents) {
            if (i.resolveActivity(ctx.getPackageManager()) != null) return i;
        }
        return null;
    }

    /**
     * Whether an input is the composite (AV) one. A tuner is not: on these sets the tuner is the
     * analogue TV, which the player reaches by its own action rather than by a port number.
     */
    private static boolean isComposite(TvInputInfo info) {
        if (info.getType() == TvInputInfo.TYPE_COMPOSITE) return true;
        if (info.getType() == TvInputInfo.TYPE_TUNER) return false;
        String cls = serviceName(info.getId()).toLowerCase(java.util.Locale.ROOT);
        return cls.contains("composite") || cls.contains("av");
    }

    /** The hardware id the framework ends an input id with: ".../HW5" -> "HW5". */
    static String hardwareId(String inputId) {
        if (inputId == null) return "";
        int slash = inputId.lastIndexOf('/');
        return slash < 0 ? inputId : inputId.substring(slash + 1);
    }

    /**
     * The number in a hardware id: "HW5" -> 5, "HW10" -> 10. The ports are ordered by this number
     * and not by the text: "HW10" sorts before "HW2" as a string, which would put the last port
     * first and switch the TV to the wrong one.
     */
    static int hardwareNumber(String inputId) {
        String hw = hardwareId(inputId);
        int i = hw.length();
        while (i > 0 && Character.isDigit(hw.charAt(i - 1))) i--;
        try {
            return Integer.parseInt(hw.substring(i));
        } catch (NumberFormatException e) {
            return Integer.MAX_VALUE;   // an id without a number goes last
        }
    }

    /** The service class an input id names: "com.x/.Svc/HW5" -> ".Svc". */
    private static String serviceName(String inputId) {
        if (inputId == null) return "";
        int slash = inputId.lastIndexOf('/');
        String head = slash < 0 ? inputId : inputId.substring(0, slash);
        int dot = head.lastIndexOf('.');
        return dot < 0 ? head : head.substring(dot + 1);
    }

    private static boolean installed(Context ctx, String pkg) {
        try {
            ctx.getPackageManager().getPackageInfo(pkg, 0);
            return true;
        } catch (Exception e) {
            return false;
        }
    }
}
