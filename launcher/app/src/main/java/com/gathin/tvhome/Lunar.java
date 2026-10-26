package com.gathin.tvhome;

import java.util.Calendar;
import java.util.Locale;

/**
 * The Chinese calendar's date, for a TV that speaks Chinese.
 *
 * <p>The platform carries the calendar itself in ICU from Android 7 (24) on, so that is what is
 * asked; below it the lunar date is simply left out rather than carried in the app, which would mean
 * shipping a table of two centuries of months and hoping it agreed with everybody else's.
 *
 * <p>The names are written the way they are said: 正月 for the first month, 冬月 and 腊月 for the
 * last two, 初一 to 初十 and then 十一 on to 三十. A leap month is named 闰 followed by the month it
 * repeats, which is how the calendar itself does it.
 */
final class Lunar {

    private static final String[] MONTHS = {
            "正", "二", "三", "四", "五", "六", "七", "八", "九", "十", "冬", "腊"
    };
    private static final String[] TENS = {"初", "十", "廿", "三"};

    private Lunar() {
    }

    /** Whether this TV wants the lunar date at all. */
    static boolean wanted() {
        String l = Locale.getDefault().getLanguage();
        return "zh".equals(l) && android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N;
    }

    /** "农历九月初六", or "农历闰四月初六". Empty when the platform cannot say. */
    static String text(long millis) {
        if (android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.N) return "";
        try {
            android.icu.util.ChineseCalendar cc = new android.icu.util.ChineseCalendar();
            cc.setTimeInMillis(millis);
            int month = cc.get(Calendar.MONTH);          // 0-based, as always
            int day = cc.get(Calendar.DAY_OF_MONTH);
            boolean leap = cc.get(android.icu.util.ChineseCalendar.IS_LEAP_MONTH) == 1;
            return "农历" + (leap ? "闰" : "") + MONTHS[month] + "月" + dayName(day);
        } catch (Exception e) {
            // A device that carries ICU but not this calendar: the date is a bonus, not the point.
            return "";
        }
    }

    private static String dayName(int day) {
        if (day == 10) return "初十";
        if (day == 20) return "二十";
        if (day == 30) return "三十";
        if (day < 10) return "初" + number(day);
        if (day < 20) return "十" + number(day - 10);
        return TENS[2] + number(day - 20);
    }

    /** 1-9 in the month's own words: 一 … 九. */
    private static String number(int n) {
        switch (n) {
            case 1: return "一";
            case 2: return "二";
            case 3: return "三";
            case 4: return "四";
            case 5: return "五";
            case 6: return "六";
            case 7: return "七";
            case 8: return "八";
            case 9: return "九";
            default: return String.valueOf(n);
        }
    }
}
