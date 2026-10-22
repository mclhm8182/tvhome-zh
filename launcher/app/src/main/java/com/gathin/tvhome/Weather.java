package com.gathin.tvhome;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * The weather for one place, from Open-Meteo: no account, no key, no location permission. Only this
 * launcher's own INTERNET permission is used, the same one the daily wallpaper already needs.
 *
 * <p>What leaves the TV: the city name the user typed (to find it), then that place's latitude and
 * longitude (for the forecast and the air quality), and — only when the user taps "Locate my city" —
 * one request to ipinfo.io for the coarse position, which is then resolved through the same geocoder.
 * Nothing runs in the background: requests happen while the desk is on screen.
 */
final class Weather {

    static final String PREFS = "weather";
    private static final String KEY_ENABLED = "enabled";
    private static final String KEY_NAME = "name";
    private static final String KEY_LAT = "lat";
    private static final String KEY_LON = "lon";

    private static final String GEO = "https://geocoding-api.open-meteo.com/v1/search";
    private static final String FORECAST = "https://api.open-meteo.com/v1/forecast";
    private static final String AIR = "https://air-quality-api.open-meteo.com/v1/air-quality";
    private static final String IP_GEO = "https://ipinfo.io/json";

    /** How often the line is refreshed while the desk is on screen. */
    static final long REFRESH_MS = 30 * 60 * 1000L;

    private final SharedPreferences prefs;
    private final String lang;

    Weather(Context ctx) {
        prefs = ctx.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        // Open-Meteo takes a two-letter language and falls back to English on its own.
        String l = Locale.getDefault().getLanguage();
        lang = (l == null || l.isEmpty()) ? "en" : l;
    }

    boolean enabled() {
        return prefs.getBoolean(KEY_ENABLED, false);
    }

    void enabled(boolean on) {
        prefs.edit().putBoolean(KEY_ENABLED, on).apply();
    }

    /** True once a place has been chosen (or located), so the desk knows it can show a line. */
    boolean hasPlace() {
        return prefs.contains(KEY_NAME);
    }

    String placeName() {
        return prefs.getString(KEY_NAME, "");
    }

    double lat() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LAT, 0));
    }

    double lon() {
        return Double.longBitsToDouble(prefs.getLong(KEY_LON, 0));
    }

    void place(String name, double lat, double lon) {
        prefs.edit().putString(KEY_NAME, name)
                .putLong(KEY_LAT, Double.doubleToRawLongBits(lat))
                .putLong(KEY_LON, Double.doubleToRawLongBits(lon))
                .apply();
    }

    // ---- places -----------------------------------------------------------------------------------

    /** A candidate the geocoder returned; {@link #detail} is the province or country when there is one. */
    static final class Place {
        final String name, detail;
        final double lat, lon;

        Place(String name, String detail, double lat, double lon) {
            this.name = name;
            this.detail = detail == null ? "" : detail;
            this.lat = lat;
            this.lon = lon;
        }

        /** "南宁 · 广西壮族自治区/中国" for the picker's rows. */
        String row() {
            return detail.isEmpty() ? name : name + " · " + detail;
        }
    }

    /** Places matching what the user typed. Empty when there is no match, null when the request failed. */
    List<Place> search(String typed) throws Exception {
        String json = get(GEO + "?name=" + enc(typed) + "&count=8&language=" + enc(lang) + "&format=json");
        JSONArray results = new JSONObject(json).optJSONArray("results");
        List<Place> out = new ArrayList<>();
        if (results == null) return out;
        for (int i = 0; i < results.length(); i++) {
            JSONObject r = results.getJSONObject(i);
            String detail = r.optString("admin1", "");
            String country = r.optString("country", "");
            if (!country.isEmpty()) detail = detail.isEmpty() ? country : detail + "/" + country;
            out.add(new Place(r.optString("name"), detail, r.optDouble("latitude"), r.optDouble("longitude")));
        }
        return out;
    }

    /**
     * The coarse position of this connection, resolved into a real place so the desk shows the same
     * kind of name a search would. Null when the lookup or the resolution failed; the caller says so.
     */
    Place locate() throws Exception {
        JSONObject ip = new JSONObject(get(IP_GEO));
        String city = ip.optString("city", "");
        String country = ip.optString("country", "");
        String loc = ip.optString("loc", "");
        if (city.isEmpty() && loc.isEmpty()) return null;
        if (!city.isEmpty()) {
            // The service answers in English, and only the city name resolves ("Nanning CN" returns
            // nothing), so the geocoder is asked for the city alone. Its first hit is the one the TV's
            // own language wants — 南宁 for "Nanning", not the same-named town in Western Australia,
            // which is why the first result is taken as it stands instead of hunting for a name match.
            List<Place> found = search(city);
            if (!found.isEmpty()) return found.get(0);
        }
        if (!loc.isEmpty()) {
            String[] ll = loc.split(",");
            if (ll.length == 2) {
                return new Place(city.isEmpty() ? loc : city, country,
                        Double.parseDouble(ll[0].trim()), Double.parseDouble(ll[1].trim()));
            }
        }
        return null;
    }

    // ---- the report -------------------------------------------------------------------------------

    static final class Day {
        final String label;   // resolved by the caller: today / tomorrow / the weekday
        final int code, max, min;

        Day(String label, int code, int max, int min) {
            this.label = label;
            this.code = code;
            this.max = max;
            this.min = min;
        }
    }

    static final class Report {
        int temp, feels, code, aqi = -1;
        final List<Day> days = new ArrayList<>();
        /** The next hours: label (the hour), the code, the temperature. */
        final List<Hour> hours = new ArrayList<>();
    }

    static final class Hour {
        final String label;
        final int code, temp;

        Hour(String label, int code, int temp) {
            this.label = label;
            this.code = code;
            this.temp = temp;
        }
    }

    /** The weather now and the next three days. Null when the request failed. */
    Report fetch() throws Exception {
        String url = FORECAST + "?latitude=" + lat() + "&longitude=" + lon()
                + "&current=temperature_2m,apparent_temperature,weather_code"
                + "&hourly=temperature_2m,weather_code"
                + "&daily=weather_code,temperature_2m_max,temperature_2m_min"
                + "&timezone=auto&forecast_days=4";
        JSONObject json = new JSONObject(get(url));
        Report r = new Report();
        r.temp = Math.round((float) json.getJSONObject("current").optDouble("temperature_2m"));
        r.feels = Math.round((float) json.getJSONObject("current").optDouble("apparent_temperature"));
        r.code = json.getJSONObject("current").optInt("weather_code");
        JSONObject daily = json.getJSONObject("daily");
        JSONArray codes = daily.getJSONArray("weather_code");
        JSONArray max = daily.getJSONArray("temperature_2m_max");
        JSONArray min = daily.getJSONArray("temperature_2m_min");
        JSONArray times = daily.getJSONArray("time");
        int n = Math.min(codes.length(), 4);
        for (int i = 0; i < n; i++) {
            // The caller turns the first three dates into today/tomorrow/the day after.
            r.days.add(new Day(times.optString(i), codes.optInt(i),
                    Math.round((float) max.optDouble(i)), Math.round((float) min.optDouble(i))));
        }
        // The next twelve hours, starting from the one the TV is in now. Open-Meteo answers with a
        // full day of hours; the first one at or after the current time is where the strip begins.
        try {
            JSONObject hourly = json.getJSONObject("hourly");
            JSONArray hTimes = hourly.getJSONArray("time");
            JSONArray temps = hourly.getJSONArray("temperature_2m");
            JSONArray hCodes = hourly.getJSONArray("weather_code");
            String now = json.getJSONObject("current").optString("time");
            int from = 0;
            for (int i = 0; i < hTimes.length(); i++) {
                if (hTimes.optString(i).compareTo(now) >= 0) { from = i; break; }
            }
            int to = Math.min(hTimes.length(), from + 5);
            for (int i = from; i < to; i++) {
                // "2026-10-22T23:00" -> "23时" in the TV's own language.
                String t = hTimes.optString(i);
                int hour = 0;
                int sep = t.indexOf('T');
                if (sep > 0 && t.length() >= sep + 6) {
                    hour = Integer.parseInt(t.substring(sep + 1, sep + 3));
                }
                r.hours.add(new Hour(hour + ":00", hCodes.optInt(i),
                        Math.round((float) temps.optDouble(i))));
            }
        } catch (Exception ignored) {
            // The strip is a bonus; the line and the days are what the panel is for.
        }
        try {
            JSONObject air = new JSONObject(get(AIR + "?latitude=" + lat() + "&longitude=" + lon() + "&current=us_aqi"));
            r.aqi = air.getJSONObject("current").optInt("us_aqi", -1);
        } catch (Exception ignored) {
            // The air quality is a bonus; the forecast is what the line is for.
        }
        return r;
    }

    // ---- WMO weather codes ------------------------------------------------------------------------

    /** The sky condition bucket a WMO code falls into, for the icon and the words. */
    static int skyOf(int code) {
        if (code == 0 || code == 1) return 0;            // clear
        if (code == 2 || code == 3) return 1;            // cloudy
        if (code == 45 || code == 48) return 2;          // fog
        if (code >= 51 && code <= 57) return 3;          // drizzle
        if (code >= 61 && code <= 67) return 4;          // rain
        if (code >= 71 && code <= 77) return 5;          // snow
        if (code >= 80 && code <= 82) return 6;          // showers
        if (code == 85 || code == 86) return 7;          // snow showers
        return 8;                                        // thunder
    }

    // ---- plumbing ---------------------------------------------------------------------------------

    private static String enc(String s) throws Exception {
        return java.net.URLEncoder.encode(s, "UTF-8");
    }

    private static String get(String url) throws Exception {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        try {
            c.setConnectTimeout(8000);
            c.setReadTimeout(8000);
            c.setRequestProperty("Accept", "application/json");
            // ipinfo's free tier answers a bare client too, but a UA keeps proxies from being clever.
            c.setRequestProperty("User-Agent", "tvhome-zh");
            InputStream in = c.getResponseCode() == HttpURLConnection.HTTP_OK
                    ? c.getInputStream() : c.getErrorStream();
            if (in == null) throw new java.io.IOException("HTTP " + c.getResponseCode());
            BufferedReader r = new BufferedReader(new InputStreamReader(in, StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line);
            return sb.toString();
        } finally {
            c.disconnect();
        }
    }
}
