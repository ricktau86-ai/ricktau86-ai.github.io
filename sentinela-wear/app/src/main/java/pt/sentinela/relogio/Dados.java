package pt.sentinela.relogio;

import android.content.ComponentName;
import android.content.Context;
import android.content.SharedPreferences;
import androidx.wear.tiles.TileService;
import androidx.wear.watchface.complications.datasource.ComplicationDataSourceUpdateRequester;
import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collections;
import java.util.Date;
import java.util.List;
import java.util.Locale;
import java.util.TimeZone;
import org.json.JSONArray;
import org.json.JSONObject;

/** Vai buscar os dados diretamente às fontes (pela ligação do relógio ou do telemóvel)
 *  e guarda-os. Cada fonte é independente: se uma falhar, as outras continuam. */
public class Dados {
    static final String PREFS = "sentinela_relogio";
    static final Locale PT = new Locale("pt", "PT");
    static final String[] LV = {"green", "yellow", "orange", "red"};
    static final String[] LVN = {"Verde", "Amarelo", "Laranja", "Vermelho"};
    static final int[] COR = {0xFF4CAF50, 0xFFFFEB3B, 0xFFFF9800, 0xFFF44336};

    /** código IPMA, nome, latitude, longitude */
    static final String[][] DISTRITOS = {
            {"VCT", "Viana do Castelo", "41.693", "-8.832"}, {"BRG", "Braga", "41.545", "-8.427"},
            {"VRL", "Vila Real", "41.300", "-7.745"}, {"BGC", "Bragança", "41.806", "-6.757"},
            {"PTO", "Porto", "41.150", "-8.611"}, {"AVR", "Aveiro", "40.640", "-8.654"},
            {"VIS", "Viseu", "40.657", "-7.913"}, {"GDA", "Guarda", "40.537", "-7.268"},
            {"CBR", "Coimbra", "40.203", "-8.410"}, {"CBO", "Castelo Branco", "39.822", "-7.491"},
            {"LRA", "Leiria", "39.744", "-8.807"}, {"STM", "Santarém", "39.236", "-8.686"},
            {"PTG", "Portalegre", "39.297", "-7.430"}, {"LSB", "Lisboa", "38.722", "-9.139"},
            {"STB", "Setúbal", "38.524", "-8.893"}, {"EVR", "Évora", "38.571", "-7.909"},
            {"BJA", "Beja", "38.015", "-7.863"}, {"FAR", "Faro", "37.019", "-7.930"},
            {"MCN", "Madeira – Costa Norte", "32.83", "-16.95"}, {"MCS", "Madeira – Costa Sul", "32.65", "-16.92"},
            {"MRM", "Madeira – Montanha", "32.75", "-16.97"}, {"MPS", "Porto Santo", "33.07", "-16.34"},
            {"AOR", "Açores – Oriental", "37.75", "-25.67"}, {"ACE", "Açores – Central", "38.65", "-27.22"},
            {"AOC", "Açores – Ocidental", "39.45", "-31.13"}
    };

    static SharedPreferences prefs(Context c) { return c.getSharedPreferences(PREFS, Context.MODE_PRIVATE); }

    static String get(String u) throws Exception {
        HttpURLConnection h = (HttpURLConnection) new URL(u).openConnection();
        h.setConnectTimeout(20000);
        h.setReadTimeout(25000);
        h.setRequestProperty("User-Agent", "Sentinela-WearOS");
        try (InputStream in = h.getInputStream(); BufferedReader r = new BufferedReader(new InputStreamReader(in, "UTF-8"))) {
            StringBuilder sb = new StringBuilder();
            String l;
            while ((l = r.readLine()) != null) sb.append(l).append('\n');
            return sb.toString();
        } finally {
            h.disconnect();
        }
    }

    static double km(double a, double b, double c, double d) {
        double r = Math.PI / 180, x = (c - a) * r, y = (d - b) * r;
        double h = Math.sin(x / 2) * Math.sin(x / 2) + Math.cos(a * r) * Math.cos(c * r) * Math.sin(y / 2) * Math.sin(y / 2);
        return 2 * 6371 * Math.asin(Math.min(1, Math.sqrt(h)));
    }

    static String fmt1(double v) { return String.format(PT, "%.1f", v); }

    static String hora(long t) { return new SimpleDateFormat("HH:mm", PT).format(new Date(t)); }

    /** Vai buscar tudo e avisa o mosaico e a complicação. Correr fora da thread principal. */
    static synchronized void atualizar(Context c) {
        SharedPreferences p = prefs(c);
        avisos(p);
        tempo(p);
        ocorrencias(p);
        tempestade(p);
        p.edit().putLong("upd", System.currentTimeMillis()).apply();
        try {
            TileService.getUpdater(c).requestUpdate(SentinelaTile.class);
            TileService.getUpdater(c).requestUpdate(ChuvaTile.class);
            TileService.getUpdater(c).requestUpdate(HorasTile.class);
        } catch (Exception ignored) { }
        try {
            ComplicationDataSourceUpdateRequester.create(c, new ComponentName(c, SentinelaComplicacao.class)).requestUpdateAll();
        } catch (Exception ignored) { }
    }

    /* ---------- Avisos IPMA ---------- */
    static int nivel(String s) {
        for (int i = 0; i < LV.length; i++) if (LV[i].equals(s)) return i;
        return 0;
    }

    static String iconeAviso(String tipo) {
        String t = tipo.toLowerCase(PT);
        if (t.contains("vento")) return "💨";
        if (t.contains("precip")) return "🌧";
        if (t.contains("agita")) return "🌊";
        if (t.contains("trovo")) return "⛈";
        if (t.contains("nevoeiro")) return "🌫";
        if (t.contains("neve")) return "🌨";
        if (t.contains("quente")) return "🌡";
        if (t.contains("frio")) return "❄";
        return "⚠";
    }

    static void avisos(SharedPreferences p) {
        try {
            String area = p.getString("area", "LRA");
            JSONArray arr = new JSONArray(get("https://api.ipma.pt/open-data/forecast/warnings/warnings_www.json"));
            SimpleDateFormat f = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            f.setTimeZone(TimeZone.getTimeZone("Europe/Lisbon"));
            SimpleDateFormat hf = new SimpleDateFormat("EEE HH:mm", PT);
            long now = System.currentTimeMillis();
            List<Object[]> l = new ArrayList<>();
            for (int i = 0; i < arr.length(); i++) {
                JSONObject w = arr.getJSONObject(i);
                if (!area.equals(w.optString("idAreaAviso"))) continue;
                int L = nivel(w.optString("awarenessLevelID"));
                if (L <= 0) continue;
                long s = f.parse(w.getString("startTime")).getTime(), e = f.parse(w.getString("endTime")).getTime();
                if (e < now) continue;
                l.add(new Object[]{L, s, e, w.optString("awarenessTypeName", "Aviso")});
            }
            // mais grave primeiro; dentro do mesmo nível, o que está ativo / começa mais cedo
            Collections.sort(l, (a, b) -> (int) b[0] != (int) a[0] ? (int) b[0] - (int) a[0] : Long.compare((long) a[1], (long) b[1]));
            StringBuilder lista = new StringBuilder();
            for (Object[] o : l) {
                long s = (long) o[1], e = (long) o[2];
                String quando = s <= now ? "até " + hf.format(new Date(e)) : hf.format(new Date(s)) + " → " + hf.format(new Date(e));
                lista.append(o[0]).append('|').append(o[3]).append('|').append(quando).append('\n');
            }
            SharedPreferences.Editor ed = p.edit().putInt("lvl", l.isEmpty() ? 0 : (int) l.get(0)[0])
                    .putInt("a_n", l.size()).putString("a_lista", lista.toString()).putLong("a_upd", now);
            if (l.isEmpty()) {
                ed.putString("a_tipo", "Sem avisos").putString("a_sub", "Próximos 3 dias").putString("a_ico", "✓");
            } else {
                Object[] o = l.get(0);
                long s = (long) o[1], e = (long) o[2];
                String quando = s <= now ? "Até " + hf.format(new Date(e)) : "A partir de " + hf.format(new Date(s));
                if (l.size() > 1) quando += " · +" + (l.size() - 1) + (l.size() == 2 ? " aviso" : " avisos");
                ed.putString("a_tipo", o[3] + " · " + LVN[(int) o[0]]).putString("a_sub", quando).putString("a_ico", iconeAviso((String) o[3]));
            }
            ed.apply();
        } catch (Exception ignored) { }
    }

    /* ---------- Tempo (Open-Meteo) ---------- */
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

    static String emoji(int c, boolean dia) {
        if (c == 0) return dia ? "☀" : "🌙";
        if (c <= 2) return dia ? "⛅" : "☁";
        if (c == 3) return "☁";
        if (c == 45 || c == 48) return "🌫";
        if (c >= 51 && c <= 67) return "🌧";
        if (c >= 71 && c <= 77) return "🌨";
        if (c >= 80 && c <= 82) return "🌦";
        if (c == 85 || c == 86) return "🌨";
        if (c >= 95) return "⛈";
        return "☁";
    }

    static void tempo(SharedPreferences p) {
        try {
            String lat = p.getString("lat", "39.744"), lon = p.getString("lon", "-8.807");
            JSONObject j = new JSONObject(get("https://api.open-meteo.com/v1/forecast?latitude=" + lat + "&longitude=" + lon
                    + "&current=temperature_2m,weather_code,is_day"
                    + "&minutely_15=precipitation&forecast_minutely_15=10"
                    + "&hourly=temperature_2m,weather_code,precipitation_probability,precipitation,is_day,wind_gusts_10m&forecast_hours=13"
                    + "&daily=temperature_2m_max,temperature_2m_min,wind_gusts_10m_max&forecast_days=1&timezone=Europe%2FLisbon"));
            JSONObject cur = j.getJSONObject("current"), d = j.getJSONObject("daily"), h = j.getJSONObject("hourly");
            JSONArray pp = h.getJSONArray("precipitation_probability"), pr = h.getJSONArray("precipitation"), ht = h.getJSONArray("time"),
                    tt = h.getJSONArray("temperature_2m"), wc = h.getJSONArray("weather_code"), dy = h.getJSONArray("is_day");
            int maxP = -1, maxI = 0;
            double sum = 0;
            for (int i = 0; i < pp.length(); i++) {
                int v = pp.isNull(i) ? 0 : pp.getInt(i);
                if (v > maxP) { maxP = v; maxI = i; }
                sum += pr.isNull(i) ? 0 : pr.getDouble(i);
            }
            StringBuilder horas = new StringBuilder();
            for (int i = 2; i < ht.length() && i <= 12; i += 2) {
                horas.append(ht.getString(i).substring(11, 13)).append("h|")
                        .append(emoji(wc.optInt(i), dy.optInt(i, 1) == 1)).append('|')
                        .append(Math.round(tt.optDouble(i))).append("°|")
                        .append(pp.isNull(i) ? 0 : pp.getInt(i)).append("%\n");
            }
            // hora a hora (5 horas seguidas, para o mosaico)
            StringBuilder h1 = new StringBuilder();
            long agora = System.currentTimeMillis();
            SimpleDateFormat isoL = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm", Locale.US);
            isoL.setTimeZone(TimeZone.getTimeZone("Europe/Lisbon"));
            int n1 = 0;
            double gmax = 0; String gh = "";
            JSONArray gu = h.optJSONArray("wind_gusts_10m");
            for (int i = 0; i < ht.length() && n1 < 5; i++) {
                if (isoL.parse(ht.getString(i)).getTime() + 3600000L <= agora) continue;
                h1.append(ht.getString(i).substring(11, 13)).append("h|").append(emoji(wc.optInt(i), dy.optInt(i, 1) == 1)).append('|')
                        .append(Math.round(tt.optDouble(i))).append("°|").append(pp.isNull(i) ? 0 : pp.getInt(i)).append('\n');
                if (gu != null && gu.optDouble(i, 0) > gmax) { gmax = gu.optDouble(i, 0); gh = ht.getString(i).substring(11, 13) + "h"; }
                n1++;
            }
            // chuva de 15 em 15 minutos (próximas 2 h)
            StringBuilder q = new StringBuilder();
            JSONObject m15 = j.optJSONObject("minutely_15");
            if (m15 != null) {
                JSONArray mt = m15.getJSONArray("time"), mp = m15.getJSONArray("precipitation");
                int nq = 0;
                for (int i = 0; i < mt.length() && nq < 8; i++) {
                    if (isoL.parse(mt.getString(i)).getTime() + 15 * 60000L <= agora) continue;
                    q.append(mt.getString(i).substring(11, 16)).append('|').append(mp.isNull(i) ? 0 : mp.getDouble(i)).append('\n');
                    nq++;
                }
            }
            int code = cur.getInt("weather_code");
            long gust = Math.round(d.getJSONArray("wind_gusts_10m_max").getDouble(0));
            p.edit()
                    .putString("t_temp", Math.round(cur.getDouble("temperature_2m")) + "°")
                    .putString("t_ico", emoji(code, cur.optInt("is_day", 1) == 1))
                    .putString("t_desc", wmo(code))
                    .putString("t_minmax", "mín " + Math.round(d.getJSONArray("temperature_2m_min").getDouble(0)) + "° · máx " + Math.round(d.getJSONArray("temperature_2m_max").getDouble(0)) + "°")
                    .putString("t_rain", maxP < 20 ? "Sem chuva nas próximas 12 h" : "🌧 " + maxP + "% às " + ht.getString(maxI).substring(11, 13) + "h · " + fmt1(sum) + " mm")
                    .putString("t_rain_curto", maxP < 20 ? "" : maxP + "% " + ht.getString(maxI).substring(11, 13) + "h")
                    .putString("t_gust", "💨 rajada até " + gust + " km/h")
                    .putLong("t_gustv", gust)
                    .putString("t_horas", horas.toString())
                    .putString("t_h1", h1.toString())
                    .putString("t_h1_gust", gmax > 0 ? "💨 rajada " + Math.round(gmax) + " km/h às " + gh : "")
                    .putString("t_q15", q.toString())
                    .putLong("t_upd", System.currentTimeMillis())
                    .apply();
        } catch (Exception ignored) { }
    }

    /* ---------- Ocorrências da Proteção Civil (camada pública do painel de prociv.gov.pt) ---------- */
    static String iconeOc(String nat) {
        String n = nat.toLowerCase(Locale.ROOT);
        if (n.matches(".*(incênd|incend|mato|povoamento|agríc|agric|florest|queima|habitac).*")) return "🔥";
        if (n.matches(".*(acidente|rodovi|colis|despiste|atropel|capot).*")) return "🚗";
        if (n.matches(".*(árvore|arvore|queda|derrocada|movimento de massa).*")) return "🌳";
        if (n.matches(".*(inunda|cheia|alagamento).*")) return "🌊";
        if (n.matches(".*(saúde|saude|doen|assist).*")) return "🚑";
        return "🚨";
    }

    static void ocorrencias(SharedPreferences p) {
        try {
            double la = Double.parseDouble(p.getString("lat", "39.744")), lo = Double.parseDouble(p.getString("lon", "-8.807"));
            int radius = p.getInt("radius", 25);
            JSONObject j = new JSONObject(get("https://services-eu1.arcgis.com/VlrHb7fn5ewYhX6y/arcgis/rest/services/OcorrenciasSite/FeatureServer/0/query"
                    + "?where=1%3D1&outFields=Natureza,EstadoOcorrencia,Concelho,Localidade,Operacionais,MeiosAereos,DuracaoMinutos,Latitude,Longitude"
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
            StringBuilder s = new StringBuilder();
            for (int k = 0; k < idx.size() && k < 5; k++) {
                JSONObject a = f.getJSONObject((int) idx.get(k)[1]).getJSONObject("attributes");
                String nat = a.optString("Natureza", "Ocorrência").replaceFirst("^\\d+\\s*-\\s*", "");
                int ops = a.optInt("Operacionais", 0), aer = a.optInt("MeiosAereos", 0);
                String loc = a.optString("Localidade", ""), conc = a.optString("Concelho", "");
                String sub = (!loc.isEmpty() && !"null".equals(loc) ? loc + ", " : "") + conc + " · " + ops + " operac.";
                if (aer > 0) sub += " · " + aer + (aer == 1 ? " aéreo" : " aéreos");
                double d = idx.get(k)[0];
                s.append(iconeOc(nat)).append('|').append(nat.replace('|', '/')).append('|').append(sub.replace('|', '/')).append('|')
                        .append(d < 10 ? fmt1(d) : String.valueOf(Math.round(d))).append(" km|").append(ops >= 60 || aer >= 2 ? "1" : "0").append('\n');
            }
            p.edit().putInt("o_count", idx.size()).putInt("o_radius", radius).putString("o_lista", s.toString())
                    .putLong("o_upd", System.currentTimeMillis()).apply();
        } catch (Exception ignored) { }
    }

    /* ---------- Tempestades (resultado do separador Tempestades, ECMWF, calculado no GitHub) ---------- */
    static void tempestade(SharedPreferences p) {
        try {
            JSONObject d = new JSONObject(get("https://raw.githubusercontent.com/ricktau86-ai/ricktau86-ai.github.io/sentinela-tempestades/data.json"));
            JSONArray st = d.optJSONArray("tempestades");
            SimpleDateFormat iso = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss", Locale.US);
            iso.setTimeZone(TimeZone.getTimeZone("UTC"));
            SimpleDateFormat hf = new SimpleDateFormat("EEE HH:mm", PT), h2 = new SimpleDateFormat("HH:mm", PT);
            long now = System.currentTimeMillis();
            int bestRank = 0;
            String nome = "", pres = "", l1 = "", l2 = "", l3 = "", curto = "";
            for (int i = 0; st != null && i < st.length(); i++) {
                JSONObject s = st.getJSONObject(i);
                String n = s.optString("nome", "");
                String label = n.isEmpty() ? "Depressão" : "Depressão " + n;
                JSONObject lf = s.optJSONObject("entrada_em_terra");
                int rank = 0;
                String a = "", b = "", c = "", k = "";
                if (lf != null) {
                    JSONArray pts = lf.optJSONArray("pontos");
                    int inPt = 0, tot = pts == null ? 0 : pts.length();
                    for (int q = 0; q < tot; q++) {
                        JSONArray pt = pts.getJSONArray(q);
                        double la = pt.getDouble(0), lo = pt.getDouble(1);
                        if (la > 36.9 && la < 42.2 && lo < -7.38) inPt++;
                    }
                    JSONArray jan = lf.optJSONArray("janela");
                    if (jan != null) {
                        long t0 = iso.parse(jan.getString(0).substring(0, 19)).getTime(), t1 = iso.parse(jan.getString(1).substring(0, 19)).getTime();
                        if (inPt >= Math.max(5, 0.3 * lf.optInt("de", 50)) && t1 > now - 3 * 3600000L) {
                            rank = 2;
                            a = "Entrada em terra";
                            b = "perto de " + lf.optString("mais_provavel") + "\n" + hf.format(new Date(t0)) + "–" + h2.format(new Date(t1));
                            c = inPt + " de " + lf.optInt("de") + " cenários";
                            k = "terra " + hf.format(new Date(t0));
                        }
                    }
                }
                if (rank == 0 && s.optInt("dist_pt_min_km", 9999) <= 250) {
                    rank = 1;
                    a = "Aproxima-se de Portugal";
                    b = "passa a cerca de " + s.optInt("dist_pt_min_km") + " km da costa";
                    c = "Trajetória no telemóvel";
                    k = "a " + s.optInt("dist_pt_min_km") + " km";
                }
                if (rank > bestRank) {
                    bestRank = rank; nome = label; pres = Math.round(s.optDouble("pmin", 0)) + " hPa";
                    l1 = a; l2 = b; l3 = c; curto = k;
                }
            }
            p.edit().putInt("s_rank", bestRank).putString("s_nome", nome).putString("s_p", pres)
                    .putString("s_l1", l1).putString("s_l2", l2).putString("s_l3", l3).putString("s_curto", curto)
                    .putLong("s_upd", now).apply();
        } catch (Exception ignored) { }
    }

    static int corDe(int lvl) { return COR[Math.max(0, Math.min(3, lvl))]; }

    static boolean recente(long t) { return t > 0 && System.currentTimeMillis() - t < 3 * 3600000L; }

    static String dia() { return new SimpleDateFormat("EEE, d MMM", PT).format(Calendar.getInstance().getTime()); }
}
