import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URL;
import java.text.DecimalFormat;
import java.util.*;

public class CurrencyConverter {

    // ── CONFIG ──────────────────────────────────────────────────────────────
    /** Paste your free API key from https://exchangerate-api.com here */
    private static final String API_KEY = "b3723fb7963c1891eea226a9";

    private static final String API_BASE_URL = "https://v6.exchangerate-api.com/v6/" + API_KEY + "/latest/";

    // ── STATIC FALLBACK RATES (relative to USD) ──────────────────────────
    // Updated manually — used only when the API is unavailable or key is unset
    private static final Map<String, Double> FALLBACK_RATES_USD = new LinkedHashMap<>();
    static {
        FALLBACK_RATES_USD.put("USD", 1.0);
        FALLBACK_RATES_USD.put("EUR", 0.92);
        FALLBACK_RATES_USD.put("GBP", 0.79);
        FALLBACK_RATES_USD.put("JPY", 153.50);
        FALLBACK_RATES_USD.put("INR", 83.50);
        FALLBACK_RATES_USD.put("CAD", 1.37);
        FALLBACK_RATES_USD.put("AUD", 1.54);
        FALLBACK_RATES_USD.put("CHF", 0.90);
        FALLBACK_RATES_USD.put("CNY", 7.24);
        FALLBACK_RATES_USD.put("SGD", 1.34);
        FALLBACK_RATES_USD.put("AED", 3.67);
        FALLBACK_RATES_USD.put("SAR", 3.75);
        FALLBACK_RATES_USD.put("BRL", 5.05);
        FALLBACK_RATES_USD.put("MXN", 17.15);
        FALLBACK_RATES_USD.put("KRW", 1340.0);
        FALLBACK_RATES_USD.put("HKD", 7.82);
        FALLBACK_RATES_USD.put("NOK", 10.55);
        FALLBACK_RATES_USD.put("SEK", 10.40);
        FALLBACK_RATES_USD.put("DKK", 6.88);
        FALLBACK_RATES_USD.put("NZD", 1.64);
        FALLBACK_RATES_USD.put("ZAR", 18.62);
        FALLBACK_RATES_USD.put("TRY", 32.50);
        FALLBACK_RATES_USD.put("THB", 35.10);
        FALLBACK_RATES_USD.put("MYR", 4.72);
        FALLBACK_RATES_USD.put("IDR", 15800.0);
        FALLBACK_RATES_USD.put("PHP", 57.90);
        FALLBACK_RATES_USD.put("PKR", 278.50);
        FALLBACK_RATES_USD.put("EGP", 30.90);
        FALLBACK_RATES_USD.put("NGN", 1540.0);
        FALLBACK_RATES_USD.put("BDT", 110.0);
    }

    // Currency symbols for pretty output
    private static final Map<String, String> SYMBOLS = new HashMap<>();
    static {
        SYMBOLS.put("USD", "$");
        SYMBOLS.put("EUR", "€");
        SYMBOLS.put("GBP", "£");
        SYMBOLS.put("JPY", "¥");
        SYMBOLS.put("INR", "₹");
        SYMBOLS.put("CNY", "¥");
        SYMBOLS.put("KRW", "₩");
        SYMBOLS.put("TRY", "₺");
        SYMBOLS.put("BRL", "R$");
        SYMBOLS.put("RUB", "₽");
        SYMBOLS.put("THB", "฿");
        SYMBOLS.put("NGN", "₦");
        SYMBOLS.put("PHP", "₱");
        SYMBOLS.put("PKR", "₨");
        SYMBOLS.put("IDR", "Rp");
        SYMBOLS.put("MYR", "RM");
        SYMBOLS.put("AED", "د.إ");
        SYMBOLS.put("SAR", "﷼");
    }

    // ── FIELDS ──────────────────────────────────────────────────────────────
    private final Scanner scanner = new Scanner(System.in);
    private final DecimalFormat df = new DecimalFormat("#,##0.####");
    private final DecimalFormat dfRate = new DecimalFormat("0.######");

    private Map<String, Double> liveRates = null; 
    private String liveRatesBase = null;
    private boolean usingLiveRates = false;

    // Conversion history (last 10)
    private final Deque<String> history = new ArrayDeque<>();

    // ── ENTRY POINT ─────────────────────────────────────────────────────────
    public static void main(String[] args) {
        new CurrencyConverter().run();
    }

    private void run() {
        printBanner();
        mainMenu();
    }

    // ── MENUS ────────────────────────────────────────────────────────────────
    private void mainMenu() {
        while (true) {
            printDivider();
            System.out.println("  MAIN MENU");
            printDivider();
            System.out.println("  [1]  Convert currency");
            System.out.println("  [2]  View exchange rates for a base currency");
            System.out.println("  [3]  Batch convert (one amount -> many currencies)");
            System.out.println("  [4]  Conversion history");
            System.out.println("  [5]  List supported currencies");
            System.out.println("  [6]  Toggle rate source  (" +
                    (usingLiveRates ? "LIVE" : "OFFLINE") + ")");
            System.out.println("  [0]  Exit");
            printDivider();
            System.out.print("  Choice: ");

            String choice = scanner.nextLine().trim();
            switch (choice) {
                case "1" -> convertMenu();
                case "2" -> viewRatesMenu();
                case "3" -> batchConvertMenu();
                case "4" -> showHistory();
                case "5" -> listCurrencies();
                case "6" -> toggleRateSource();
                case "0" -> {
                    printGoodbye();
                    return;
                }
                default -> System.out.println("Invalid choice! Try again.");
            }
        }
    }

    // ── CONVERT ──────────────────────────────────────────────────────────────
    private void convertMenu() {
        System.out.println();
        System.out.println("  ── Single Conversion ──");

        double amount = promptAmount();
        String from = promptCurrency("From currency (e.g. USD): ").toUpperCase();
        String to = promptCurrency("To  currency (e.g. EUR): ").toUpperCase();

        performAndPrintConversion(amount, from, to, true);
    }

    private void performAndPrintConversion(double amount, String from, String to, boolean addToHistory) {
        try {
            double rate = getExchangeRate(from, to);
            double result = amount * rate;

            String srcSymbol = SYMBOLS.getOrDefault(from, "");
            String dstSymbol = SYMBOLS.getOrDefault(to, "");

            System.out.println();
            printDivider();
            System.out.printf("  %s%s %s  -->  %s%s %s%n",
                    srcSymbol, df.format(amount), from,
                    dstSymbol, df.format(result), to);
            System.out.printf("  Exchange rate: 1 %s = %s %s%n",
                    from, dfRate.format(rate), to);
            System.out.printf("  Inverse rate : 1 %s = %s %s%n",
                    to, dfRate.format(1.0 / rate), from);
            System.out.println("  Source: " + (usingLiveRates ? "Live (ExchangeRate-API)" : "Offline fallback"));
            printDivider();

            if (addToHistory) {
                String entry = String.format("%s%s %s  -->  %s%s %s  (rate: %s)",
                        srcSymbol, df.format(amount), from,
                        dstSymbol, df.format(result), to,
                        dfRate.format(rate));
                history.offerFirst(entry);
                if (history.size() > 10)
                    history.pollLast();
            }
        } catch (Exception e) {
            System.out.println("Error!: " + e.getMessage());
        }
    }

    // ── BATCH CONVERT ────────────────────────────────────────────────────────
    private void batchConvertMenu() {
        System.out.println();
        System.out.println("  ── Batch Conversion ──");
        double amount = promptAmount();
        String from = promptCurrency("  From currency (e.g. USD): ").toUpperCase();

        System.out.print("  Target currencies (comma-separated, or ENTER for all): ");
        String line = scanner.nextLine().trim();

        List<String> targets;
        if (line.isEmpty()) {
            targets = new ArrayList<>(getSupportedCurrencies());
            targets.remove(from);
        } else {
            targets = Arrays.stream(line.split(","))
                    .map(String::trim)
                    .map(String::toUpperCase)
                    .toList();
        }

        System.out.println();
        printDivider();
        System.out.printf("  %-6s  %-18s  %s%n", "CODE", "AMOUNT", "RATE (1 " + from + ")");
        printDivider();
        String srcSymbol = SYMBOLS.getOrDefault(from, "");
        for (String to : targets) {
            try {
                double rate = getExchangeRate(from, to);
                double result = amount * rate;
                String dstSymbol = SYMBOLS.getOrDefault(to, "");
                System.out.printf("  %-6s  %s%-14s  %s%n",
                        to, dstSymbol, df.format(result), dfRate.format(rate));
            } catch (Exception e) {
                System.out.printf("  %-6s  %-18s  %s%n", to, "N/A", e.getMessage());
            }
        }
        printDivider();
    }

    // ── VIEW RATES ───────────────────────────────────────────────────────────
    private void viewRatesMenu() {
        System.out.println();
        String base = promptCurrency("Base currency (e.g. USD): ").toUpperCase();
        System.out.println();

        Set<String> currencies = getSupportedCurrencies();
        currencies.remove(base);

        printDivider();
        System.out.printf("  Exchange rates  —  base: %s%n", base);
        System.out.printf("  Source: %s%n",
                usingLiveRates ? "Live (ExchangeRate-API)" : "Offline fallback");
        printDivider();
        System.out.printf("  %-8s  %-15s  %s%n", "CODE", "1 " + base + " =", "SYMBOL");
        printDivider();

        int col = 0;
        for (String code : currencies) {
            try {
                double rate = getExchangeRate(base, code);
                System.out.printf("  %-8s  %-15s  %s%n",
                        code, dfRate.format(rate) + " " + code,
                        SYMBOLS.getOrDefault(code, "—"));
            } catch (Exception ignored) {
            }
            if (++col % 20 == 0) {
                System.out.print("Press ENTER to continue...");
                scanner.nextLine();
            }
        }
        printDivider();
    }

    // ── HISTORY ──────────────────────────────────────────────────────────────
    private void showHistory() {
        System.out.println();
        printDivider();
        System.out.println("CONVERSION HISTORY (last 10)");
        printDivider();
        if (history.isEmpty()) {
            System.out.println("No conversions yet.");
        } else {
            int i = 1;
            for (String entry : history) {
                System.out.printf("  %2d. %s%n", i++, entry);
            }
        }
        printDivider();
    }

    // ── LIST CURRENCIES ──────────────────────────────────────────────────────
    private void listCurrencies() {
        System.out.println();
        printDivider();
        System.out.println("SUPPORTED CURRENCIES");
        printDivider();
        List<String> list = new ArrayList<>(getSupportedCurrencies());
        Collections.sort(list);
        int cols = 8, col = 0;
        System.out.print("  ");
        for (String c : list) {
            System.out.printf("%-6s  ", c);
            if (++col % cols == 0)
                System.out.print("\n  ");
        }
        System.out.println();
        printDivider();
        System.out.println("  Total: " + list.size() + " currencies");
        printDivider();
    }

    // ── TOGGLE RATE SOURCE ───────────────────────────────────────────────────
    private void toggleRateSource() {
        if (!usingLiveRates) {
            if (API_KEY.equals("YOUR_API_KEY_HERE")) {
                System.out.println();
                System.out.println("-->No API key set. Edit the API_KEY field in CurrencyConverter.java");
                System.out.println("-->Get a free key at: https://exchangerate-api.com");
                return;
            }
            System.out.println("-->Fetching live rates from ExchangeRate-API...");
            try {
                fetchLiveRates("USD");
                usingLiveRates = true;
                System.out.println("Live rates loaded successfully.");
            } catch (Exception e) {
                System.out.println("Failed to fetch live rates: " + e.getMessage());
                System.out.println("Staying in offline mode.");
            }
        } else {
            usingLiveRates = false;
            System.out.println(" Switched to offline (fallback) rates.");
        }
    }

    // ── RATE LOGIC ───────────────────────────────────────────────────────────
    private double getExchangeRate(String from, String to) throws Exception {
        if (from.equals(to))
            return 1.0;

        if (usingLiveRates) {
            // Re-fetch if base doesn't match cached base
            if (!from.equals(liveRatesBase)) {
                fetchLiveRates(from);
            }
            Double rate = liveRates.get(to);
            if (rate == null)
                throw new Exception("Currency not found in live data: " + to);
            return rate;
        } else {
            return getFallbackRate(from, to);
        }
    }

    /** Cross-rate via USD from the static fallback table. */
    private double getFallbackRate(String from, String to) throws Exception {
        Double rateFrom = FALLBACK_RATES_USD.get(from);
        Double rateTo = FALLBACK_RATES_USD.get(to);
        if (rateFrom == null)
            throw new Exception("Unsupported currency (offline): " + from);
        if (rateTo == null)
            throw new Exception("Unsupported currency (offline): " + to);
        return rateTo / rateFrom;
    }

    private void fetchLiveRates(String base) throws Exception {
        String urlStr = API_BASE_URL + base;
        URL url = URI.create(urlStr).toURL();
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("GET");
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(8000);

        int status = conn.getResponseCode();
        if (status != 200) {
            throw new Exception("API returned HTTP " + status);
        }

        StringBuilder sb = new StringBuilder();
        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(conn.getInputStream()))) {
            String line;
            while ((line = br.readLine()) != null)
                sb.append(line);
        }

        String json = sb.toString();

        // Verify result field
        if (!json.contains("\"result\":\"success\"")) {
            String errType = extractJsonValue(json, "\"error-type\"");
            throw new Exception("API error: " + (errType != null ? errType : "unknown"));
        }

        // Parse conversion_rates block
        int start = json.indexOf("\"conversion_rates\":");
        if (start == -1)
            throw new Exception("Unexpected API response format.");
        int braceOpen = json.indexOf('{', start);
        int braceClose = json.indexOf('}', braceOpen);
        String ratesBlock = json.substring(braceOpen + 1, braceClose);

        Map<String, Double> rates = new LinkedHashMap<>();
        for (String token : ratesBlock.split(",")) {
            token = token.trim().replace("\"", "");
            int colon = token.indexOf(':');
            if (colon < 0)
                continue;
            String code = token.substring(0, colon).trim();
            String value = token.substring(colon + 1).trim();
            try {
                rates.put(code, Double.parseDouble(value));
            } catch (NumberFormatException ignored) {
            }
        }

        liveRates = rates;
        liveRatesBase = base;
    }

    private String extractJsonValue(String json, String key) {
        int idx = json.indexOf(key);
        if (idx < 0)
            return null;
        int colon = json.indexOf(':', idx);
        int q1 = json.indexOf('"', colon + 1);
        int q2 = json.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0)
            return null;
        return json.substring(q1 + 1, q2);
    }

    // ── SUPPORTED CURRENCIES ─────────────────────────────────────────────────
    private Set<String> getSupportedCurrencies() {
        if (usingLiveRates && liveRates != null)
            return new LinkedHashSet<>(liveRates.keySet());
        return new LinkedHashSet<>(FALLBACK_RATES_USD.keySet());
    }

    // ── INPUT HELPERS ────────────────────────────────────────────────────────
    private double promptAmount() {
        while (true) {
            System.out.print("  Amount: ");
            try {
                double v = Double.parseDouble(scanner.nextLine().trim().replace(",", ""));
                if (v <= 0) {
                    System.out.println("Amount must be positive.");
                    continue;
                }
                return v;
            } catch (NumberFormatException e) {
                System.out.println("Invalid number! Try again.");
            }
        }
    }

    private String promptCurrency(String prompt) {
        while (true) {
            System.out.print(prompt);
            String code = scanner.nextLine().trim().toUpperCase();
            if (code.length() == 3 && code.matches("[A-Z]+"))
                return code;
            System.out.println("Currency codes are 3 letters (e.g. USD, EUR, INR).");
        }
    }

    // ── VISUAL HELPERS ───────────────────────────────────────────────────────
    private void printBanner() {
        System.out.println();
        System.out.println("  ╔══════════════════════════════════════════╗");
        System.out.println("  ║  ~~~~~>>> CURRENCY  CONVERTER <<<~~~~~   ║");
        System.out.println("  ║------------------------------------------║");
        System.out.println("  ║   Real-time rates via ExchangeRate-API   ║");
        System.out.println("  ╚══════════════════════════════════════════╝");
        System.out.println();
        if (API_KEY.equals("YOUR_API_KEY_HERE")) {
            System.out.println("->Running in OFFLINE mode (no API key set).");
            System.out.println("->To enable live rates: set API_KEY in the source,");
            System.out.println("->then use option [6] to switch to live mode.");
        } else {
            System.out.println("->API key found. Use option [6] to load live rates.");
        }
        System.out.println();
    }

    private void printDivider() {
        System.out.println("  ──────────────────────────────────────────");
    }

    private void printGoodbye() {
        System.out.println();
        System.out.println("  Thank you for using Currency Converter.....");
        System.out.println();
    }
}