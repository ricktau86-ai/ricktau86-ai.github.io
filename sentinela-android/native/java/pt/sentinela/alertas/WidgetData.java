package pt.sentinela.alertas;

import android.content.Context;
import android.content.SharedPreferences;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import org.json.JSONArray;
import org.json.JSONObject;

/** Recolhe em segundo plano os dados dos widgets novos e guarda-os nas preferências.
 *  Cada fonte é independente: se uma falhar, os outros widgets continuam a atualizar. */
public class WidgetData {

    static double km(double a, double b, double c, double d) {
        double r = Math.PI / 180, x = (c - a) * r, y = (d - b) * r;
        double h = Math.sin(x / 2) * Math.sin(x / 2) + Math.cos(a * r) * Math.cos(c * r) * Math.sin(y / 2) * Math.sin(y / 2);
        return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    static String wmo(int c) {
        if (c == 0) return "Céu limpo";
        if (c == 1) return "Pouco nublado";
        if (c == 2) return "Parcialmente nublado";
        if (c == 3) return "Nublado";
        if (c == 45 || c == 48) return "Nevoeiro";
        if (c >= 51 && c <= 57) return "Chuvisco";
        if (c >= 61 && c <= 67) return c == 65 ? "Chuva forte" : "Chuva";
        if (c >= 71 && c <= 77) return "Neve";
        if (c >= 80 && c <= 82) return c == 82 ? "Aguaceiros fortes" : "Aguaceiros";
        if (c == 85 || c == 86) return "Aguaceiros de neve";
        if (c >= 95) return "Trovoada";
        return "";
    }

    static String fmt1(double v) { return String.format(new Locale("pt", "PT"), "%.1f", v); }

    static void fetchAll(Context c, SharedPreferences p) {
        String lat = p.getString("lat", "39.7436"), lon = p.getString("lon", "-8.8071");
        weather(p, lat, lon);
        ambiente(p, lat, lon);
        ocorrencias(p, Double.parseDouble(lat), Double.parseDouble(lon));
        Previsao.fetch(p, lat, lon);
    }

    /* ---------- Tempo (Open-Meteo) ---------- */
    static void weather(SharedPreferences p, String lat, String lon) {
        try {
            JSONObject j = new JSONObject(WidgetWorker.get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code&hourly=precipitation_probability,precipitation&forecast_hours=12"
                    + "&daily=temperature_2m_max,temperature_2m_min,wind_gusts_10m_max,uv_index_max&forecast_days=1&timezone=Europe%2FLisbon"));
            JSONObject cur = j.getJSONObject("current"), d = j.getJSONObject("daily"), h = j.getJSONObject("hourly");
            long t = Math.round(cur.getDouble("temperature_2m"));
            JSONArray pp = h.getJSONArray("precipitation_probability"), pr = h.getJSONArray("precipitation"), ht = h.getJSONArray("time");
            int maxP = -1, maxI = 0; double sum = 0;
            for (int i = 0; i < pp.length(); i++) {
                int v = pp.isNull(i) ? 0 : pp.getInt(i);
                if (v > maxP) { maxP = v; maxI = i; }
                sum += pr.isNull(i) ? 0 : pr.getDouble(i);
            }
            String rain = maxP < 20 ? "Sem chuva prevista (12 h)"
                    : "🌧 " + maxP + "% às " + ht.getString(maxI).substring(11, 13) + "h · " + fmt1(sum) + " mm";
            long gust = Math.round(d.getJSONArray("wind_gusts_10m_max").getDouble(0));
            p.edit()
                    .putString("temp", t + "°")
                    .putString("w_desc", wmo(cur.getInt("weather_code")))
                    .putString("w_minmax", "mín " + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "° · máx " + Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "°")
                    .putString("w_rain", rain)
                    .putString("w_gust", "💨 rajada até " + gust + " km/h")
                    .putLong("w_gustv", gust)
                    .putString("a_uv", String.valueOf(Math.round(d.getJSONArray("uv_index_max").getDouble(0))))
                    .putLong("w_upd", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) { }
    }

    /* ---------- Ambiente: risco de incêndio (IPMA) e qualidade do ar (CAMS) ---------- */
    static void ambiente(SharedPreferences p, String lat, String lon) {
        double la = Double.parseDouble(lat), lo = Double.parseDouble(lon);
        try {
            JSONObject r = new JSONObject(WidgetWorker.get("https://api.ipma.pt/open-data/forecast/meteorology/rcm/rcm-d0.json")).getJSONObject("local");
            double best = 1e9; int v = 0;
            Iterator<String> it = r.keys();
            while (it.hasNext()) {
                JSONObject l = r.getJSONObject(it.next());
                double d = km(la, lo, l.optDouble("latitude", 0), l.optDouble("longitude", 0));
                if (d < best) { best = d; v = l.getJSONObject("data").optInt("rcm", 0); }
            }
            p.edit().putInt("a_rcm", v).apply();
        } catch (Exception ignored) { }
        try {
            JSONObject q = new JSONObject(WidgetWorker.get("https://air-quality-api.open-meteo.com/v1/air-quality?latitude=" + lat + "&longitude=" + lon + "&current=european_aqi"));
            p.edit().putInt("a_aqi", (int) Math.round(q.getJSONObject("current").getDouble("european_aqi"))).apply();
        } catch (Exception ignored) { }
        p.edit().putLong("a_upd", System.currentTimeMillis()).apply();
    }

    /* ---------- Ocorrências da Proteção Civil (camada pública do painel de prociv.gov.pt) ---------- */
    static String icon(String nat) {
        String n = nat.toLowerCase(Locale.ROOT);
        if (n.matches(".*(incênd|incend|mato|povoamento|agríc|agric|florest|queima|habitac).*")) return "🔥";
        if (n.matches(".*(acidente|rodovi|colis|despiste|atropel|capot).*")) return "🚗";
        if (n.matches(".*(árvore|arvore|queda|derrocada|movimento de massa).*")) return "🌳";
        if (n.matches(".*(inunda|cheia|alagamento).*")) return "🌊";
        if (n.matches(".*(saúde|saude|doen|assist).*")) return "🚑";
        return "🚨";
    }

    static void ocorrencias(SharedPreferences p, double la, double lo) {
        try {
            int radius = p.getInt("radius", 25);
            JSONObject j = new JSONObject(WidgetWorker.get("https://services-eu1.arcgis.com/VlrHb7fn5ewYhX6y/arcgis/rest/services/OcorrenciasSite/FeatureServer/0/query"
                    + "?where=1%3D1&outFields=Natureza,EstadoOcorrencia,Concelho,Localidade,Freguesia,Operacionais,MeiosAereos,DuracaoMinutos,Latitude,Longitude"
                    + "&returnGeometry=false&resultRecordCount=2000&f=json"));
            JSONArray f = j.getJSONArray("features");
            List<double[]> idx = new ArrayList<>();
            for (int i = 0; i < f.length(); i++) {
                JSONObject a = f.getJSONObject(i).getJSONObject("attributes");
                if (a.isNull("Latitude") || a.isNull("Longitude")) continue;
                if (a.optDouble("DuracaoMinutos", 0) > 3 * 1440) continue;
                double d = km(la, lo, a.getDouble("Latitude"), a.getDouble("Longitude"));
                if (d <= radius) idx.add(new double[]{d, i});
            }
            Collections.sort(idx, (x, y) -> Double.compare(x[0], y[0]));
            SharedPreferences.Editor e = p.edit().putInt("o_count", idx.size()).putInt("o_radius", radius).putLong("o_upd", System.currentTimeMillis());
            for (int k = 0; k < 2; k++) {
                if (k < idx.size()) {
                    JSONObject a = f.getJSONObject((int) idx.get(k)[1]).getJSONObject("attributes");
                    String nat = a.optString("Natureza", "Ocorrência").replaceFirst("^\\d+\\s*-\\s*", "");
                    int ops = a.optInt("Operacionais", 0), aer = a.optInt("MeiosAereos", 0);
                    boolean urg = ops >= 60 || aer >= 2;
                    String loc = a.optString("Localidade", "");
                    String conc = a.optString("Concelho", "");
                    StringBuilder s = new StringBuilder();
                    if (!loc.isEmpty() && !"null".equals(loc)) s.append(loc).append(", ");
                    s.append(conc).append(" · ").append(ops).append(" operacionais");
                    if (aer > 0) s.append(" · ").append(aer).append(aer == 1 ? " aéreo" : " aéreos");
                    e.putString("o_" + k + "_i", icon(nat)).putString("o_" + k + "_t", nat + (urg ? " · URGENTE" : ""))
                            .putString("o_" + k + "_s", s.toString()).putString("o_" + k + "_d", (idx.get(k)[0] < 10 ? fmt1(idx.get(k)[0]) : String.valueOf(Math.round(idx.get(k)[0]))) + " km");
                } else {
                    e.remove("o_" + k + "_t");
                }
            }
            e.apply();
        } catch (Exception ignored) { }
    }

    /* ---------- Tempestade: detalhe para o widget grande ---------- */
    static void stormDetails(SharedPreferences p, JSONObject s, int rank, Locale pt) {
        try {
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            iso.setTimeZone(java.util.TimeZone.getTimeZone("UTC"));
            SimpleDateFormat hf = new SimpleDateFormat("EEE HH:mm", pt), h2 = new SimpleDateFormat("HH:mm", pt), dh = new SimpleDateFormat("EEE HH'h'", pt);
            String nome = s.optString("nome", "");
            SharedPreferences.Editor e = p.edit().putInt("s_rank", rank)
                    .putString("s_name", nome.isEmpty() ? "Depressão" : "Depressão " + nome)
                    .putString("s_p", Math.round(s.optDouble("pmin", 0)) + " hPa");
            JSONObject lf = s.optJSONObject("entrada_em_terra");
            if (rank == 2 && lf != null) {
                JSONArray jan = lf.getJSONArray("janela"), mar = lf.getJSONArray("margem");
                e.putString("s_l1", "Entrada em terra mais provável")
                        .putString("s_l2", "perto de " + lf.optString("mais_provavel") + " · " + hf.format(iso.parse(jan.getString(0).substring(0, 19))) + "–" + h2.format(iso.parse(jan.getString(1).substring(0, 19))))
                        .putString("s_l3", lf.optInt("cenarios") + " de " + lf.optInt("de") + " cenários · margem " + h2.format(iso.parse(mar.getString(0).substring(0, 19))) + "–" + h2.format(iso.parse(mar.getString(1).substring(0, 19))));
            } else {
                e.putString("s_l1", "Aproxima-se de Portugal")
                        .putString("s_l2", "Passagem a cerca de " + s.optInt("dist_pt_min_km") + " km da costa")
                        .putString("s_l3", "Veja a trajetória no separador Tempestades");
            }
            JSONArray m = s.getJSONArray("media");
            long now = System.currentTimeMillis();
            int n = 0;
            for (int i = 0; i < m.length() && n < 3; i++) {
                JSONObject q = m.getJSONObject(i);
                Date t = iso.parse(q.getString("t").substring(0, 19));
                if (t.getTime() < now - 3600000L) continue;
                java.util.Calendar cal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC"));
                cal.setTime(t);
                if (cal.get(java.util.Calendar.HOUR_OF_DAY) % 6 != 0) continue;
                e.putString("s_pt" + n, dh.format(t) + "\n" + Math.round(q.getDouble("p")) + " hPa");
                n++;
            }
            for (; n < 3; n++) e.remove("s_pt" + n);
            e.apply();
        } catch (Exception ignored) { }
    }
}
