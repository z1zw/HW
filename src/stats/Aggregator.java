package stats;

import config.SimulationConfig;
import model.Product;
import model.ProductCatalog;

import java.io.BufferedWriter;
import java.nio.file.Files;
import java.nio.file.Path;
import java.text.NumberFormat;
import java.time.LocalDate;
import java.util.*;
import java.util.stream.Collectors;

public final class Aggregator {

    private final ProductCatalog catalog;
    private static final int WINDOW_DAYS = 14;
    private final Map<LocalDate, Integer> dailyCustomers = new HashMap<>();
    private List<LocalDate> firstNDates(int windowDays) {
        List<LocalDate> dates = dailySkuCounts.keySet().stream().sorted().toList();
        return dates.subList(0, Math.min(windowDays, dates.size()));
    }
    private double avgCustomersPerDay(int windowDays) {
        List<LocalDate> dates = firstNDates(windowDays);
        long sum = 0;
        for (LocalDate d : dates) sum += dailyCustomers.getOrDefault(d, 0);
        return dates.isEmpty() ? 0.0 : sum / (double) dates.size();
    }
    private long totalCustomers;
    private long totalItems;
    private double totalSales;

    private final Map<Integer, Long> skuCounts;
    private final Map<LocalDate, Map<Integer, Long>> dailySkuCounts = new HashMap<>();

    public Aggregator(ProductCatalog catalog) {
        this.catalog = catalog;
        this.totalCustomers = 0L;
        this.totalItems = 0L;
        this.totalSales = 0.0;
        this.skuCounts = new HashMap<>(4096);
    }

    public void accept(
            LocalDate date,
            int storeId,
            int customerId,
            int sku,
            double salePrice
    ) {
        totalItems++;
        totalSales += salePrice;
        skuCounts.merge(sku, 1L, Long::sum);
        dailySkuCounts
                .computeIfAbsent(date, d -> new HashMap<>())
                .merge(sku, 1L, Long::sum);
    }
    public Map<LocalDate, Map<Integer, Long>> dailySkuCounts() {
        return dailySkuCounts;
    }

    public void printSummary() {
        NumberFormat intFmt = NumberFormat.getIntegerInstance();
        NumberFormat moneyFmt = NumberFormat.getCurrencyInstance(Locale.US);

        System.out.println();
        System.out.println("===== SIMULATION SUMMARY =====");
        System.out.println("Total Customers : " + intFmt.format(totalCustomers));
        System.out.println("Total Items     : " + intFmt.format(totalItems));
        System.out.println("Total Sales     : " + moneyFmt.format(round2(totalSales)));
        System.out.println();

        System.out.println("Top 10 Items (by count)");
        System.out.println("--------------------------------------------");

        List<Map.Entry<Integer, Long>> top10 = getTop10();

        int rank = 1;
        for (Map.Entry<Integer, Long> e : top10) {
            Product p = catalog.productsBySku().get(e.getKey());
            String name = p != null ? p.name() : "UNKNOWN";
            System.out.printf(
                    "%2d. SKU=%d  Count=%s  Name=%s%n",
                    rank++,
                    e.getKey(),
                    intFmt.format(e.getValue()),
                    name
            );
        }
        System.out.println();
    }

    public List<Map.Entry<Integer, Long>> getTop10() {
        return skuCounts.entrySet()
                .stream()
                .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                .limit(10)
                .collect(Collectors.toList());
    }

    public void exportD3Json(Path outputDir) throws Exception {
        exportSummaryJson(outputDir.resolve("summary.json"));
        exportTop10Json(outputDir.resolve("top10.json"));
    }

    private void exportSummaryJson(Path path) throws Exception {
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write("{\n");
            w.write("  \"totalCustomers\": " + totalCustomers + ",\n");
            w.write("  \"totalItems\": " + totalItems + ",\n");
            w.write("  \"totalSales\": " + round2(totalSales) + "\n");
            w.write("}\n");
        }
    }
    private long totalItemsInWindow(int windowDays) {
        long sum = 0;
        for (LocalDate d : firstNDates(windowDays)) {
            for (long c : dailySkuCounts.getOrDefault(d, Map.of()).values()) sum += c;
        }
        return sum;
    }

    public Map<Integer, Double> computerAveragePerDay(int windowDays) {
        List<LocalDate> dates = firstNDates(windowDays);
        Map<Integer, Long> total = new HashMap<>();
        for (LocalDate d : dates) {
            Map<Integer, Long> day = dailySkuCounts.getOrDefault(d, Map.of());
            for (var e : day.entrySet()) {
                total.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }
        int n = dates.size();
        Map<Integer, Double> result = new HashMap<>();
        for (var e : total.entrySet()) {
            result.put(e.getKey(), n == 0 ? 0.0 : e.getValue() / (double) n);
        }
        return result;
    }

    public void addCustomers(LocalDate date, int count) {
        totalCustomers += count;
        dailyCustomers.merge(date, count, Integer::sum);
    }
    private Map<String, Double> computeProbabilities() {
        double pMilk = 0.70;
        double pCereal = pMilk * 0.50 + (1 - pMilk) * 0.05;
        double pBaby = 0.20;
        double pDiapers = pBaby * 0.80 + (1 - pBaby) * 0.01;
        double pPB = 0.10;
        double pJam = pPB * 0.90 + (1 - pPB) * 0.05;
        double pBread = 0.50;
        return Map.of(
                SimulationConfig.TYPE_MILK, pMilk,
                SimulationConfig.TYPE_CEREAL, pCereal,
                SimulationConfig.TYPE_BABY_FOOD, pBaby,
                SimulationConfig.TYPE_DIAPERS, pDiapers,
                SimulationConfig.TYPE_PEANUT_BUTTER, pPB,
                SimulationConfig.TYPE_BREAD, pBread,
                SimulationConfig.TYPE_JELLY_JAM, pJam
        );
    }
    public Map<Integer, Double> computerExpectedPerDay(int windowDays) {
        Map<Integer, Double> result = new HashMap<>();
        List<LocalDate> dates = firstNDates(windowDays);
        int n = dates.size();
        double avgCustomers = avgCustomersPerDay(windowDays);
        double avgItemsPerCustomer=(SimulationConfig.ITEMS_PER_CUSTOMER_LOW_INCLUSIVE+SimulationConfig.ITEMS_PER_CUSTOMER_HIGH_INCLUSIVE) / 2.0;
        Map<String, Double> prob = computeProbabilities();
        Map<String, List<Integer>> skusByType = catalog.skusByType();
        double specialTotal = 0.0;
        for (var entry : prob.entrySet()) {
            String type = entry.getKey();
            double p = entry.getValue();
            List<Integer> skus = skusByType.get(type);
            if (skus == null || skus.isEmpty()) continue;
            double expectedTotal = avgCustomers * p;
            specialTotal += expectedTotal;

            double perSku = expectedTotal / skus.size();
            for (int sku : skus) result.put(sku, perSku);
        }
        double expectedTotalItemsPerDay = avgCustomers * avgItemsPerCustomer;
        double otherExpectedTotal = Math.max(0.0, expectedTotalItemsPerDay - specialTotal);
        List<Integer> all = catalog.allSkus();
        if (!all.isEmpty()) {
            double perSku = otherExpectedTotal / all.size();

            for (int sku : all) {
                result.merge(sku, perSku, Double::sum);
            }
        }
        return result;
    }
    private static final Set<String> SPECIAL = Set.of(
            "Milk","Cereal","Baby Food","Diapers",
            "Peanut Butter","Bread","Jelly/Jam"
    );

    public Map<String, Double> averageExpectedPerDayByType(int windowDays) {
        Map<Integer, Double> avgBySku = computerExpectedPerDay(windowDays);
        Map<String, Double> result = new HashMap<>();

        for (var e : avgBySku.entrySet()) {

            int sku = e.getKey();
            double value = e.getValue();

            Product p = catalog.productsBySku().get(sku);
            if (p == null) continue;

            String type = p.type();
            if (!SPECIAL.contains(type)) type = "Other";

            result.merge(type, value, Double::sum);
        }

        return result;
    }

    public Map<String, Double> averagePerDayByType(int windowDays) {
        Map<Integer, Double> avgBySku = computerAveragePerDay(windowDays);
        Map<String, Double> result = new HashMap<>();

        for (var e : avgBySku.entrySet()) {
            int sku = e.getKey();
            double value = e.getValue();
            Product p = catalog.productsBySku().get(sku);
            if (p == null) continue;
            String type = p.type();
            if (!SPECIAL.contains(type)) type = "Other";
            result.merge(type, value, Double::sum);
        }

        return result;
    }

    private long[] minMaxNonSpecial14Days(int windowDays) {
        List<LocalDate> dates = dailySkuCounts.keySet()
                .stream()
                .sorted()
                .toList();

        int n = Math.min(windowDays, dates.size());
        Map<Integer, Long> sku14 = new HashMap<>();
        for (int i = 0; i < n; i++) {
            Map<Integer, Long> day = dailySkuCounts.get(dates.get(i));

            for (var e : day.entrySet()) {

                Product p = catalog.productsBySku().get(e.getKey());
                if (p == null) continue;

                if (SPECIAL.contains(p.type())) continue;

                sku14.merge(e.getKey(), e.getValue(), Long::sum);
            }
        }

        long min = Long.MAX_VALUE;
        long max = Long.MIN_VALUE;

        for (long v : sku14.values()) {
            min = Math.min(min, v);
            max = Math.max(max, v);
        }

        if (sku14.isEmpty()) return new long[]{0,0};

        return new long[]{min, max};
    }
    private Map<Integer, Long> skuCountsInWindow(int windowDays) {
        Map<Integer, Long> total = new HashMap<>();
        for (LocalDate d : firstNDates(windowDays)) {
            Map<Integer, Long> day = dailySkuCounts.getOrDefault(d, Map.of());
            for (var e : day.entrySet()) total.merge(e.getKey(), e.getValue(), Long::sum);
        }
        return total;
    }
    public void exportHw3Full(Path path) throws Exception {
        int days = dailySkuCounts.size();
        int windowDays = WINDOW_DAYS;
        Map<String, Double> probabilities = computeProbabilities();
        double avgCustomersPerDay = avgCustomersPerDay(windowDays);
        int n = firstNDates(windowDays).size();
        double avgSalesPerDay = n == 0 ? 0.0 : totalItemsInWindow(windowDays) / (double) n;
        Map<String, Double> actualAvg = averagePerDayByType(windowDays);
        Map<String, Double> expectedAvg = averageExpectedPerDayByType(windowDays);

        Map<Integer, Long> skuWindow = skuCountsInWindow(windowDays);
        Map<String, Long> totalByType = new HashMap<>();
        for (var e : skuWindow.entrySet()) {
            Product p = catalog.productsBySku().get(e.getKey());
            if (p == null) continue;
            String type = SPECIAL.contains(p.type()) ? p.type() : "Other";
            totalByType.merge(type, e.getValue(), Long::sum);
        }


        long[] mm = minMaxNonSpecial14Days(windowDays);
        List<String> order = List.of(
                "Milk","Cereal","Baby Food","Diapers",
                "Peanut Butter","Bread","Jelly/Jam","Other"
        );
        double actualGrand = actualAvg.values().stream().mapToDouble(Double::doubleValue).sum();
        double expectedGrand = expectedAvg.values().stream().mapToDouble(Double::doubleValue).sum();
        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write("=====================================================\n");
            w.write("                    HW3 SALES REPORT(Team 8)\n");
            w.write("=====================================================\n\n");
            w.write("Probabilities of Each Item Type\n");
            w.write("-----------------------------------------------------\n");
            w.write("Type,Probability\n");
            for (String type : order) {
                if (!probabilities.containsKey(type)) continue;
                double p = probabilities.get(type);
                w.write(type + "," + String.format("%.1f%%", p * 100) + "\n");
            }
            w.write("-----------------------------------------------------\n");
            w.write("Actual Avg Customers per day," + round2(avgCustomersPerDay) + "\n");
            w.write("Actual Avg Sales per day," + round2(avgSalesPerDay) + "\n");
            w.write("Minimum # products sold, non-special sku, 14 days," + mm[0] + "\n");
            w.write("Maximum # products sold, non-special sku, 14 days," + mm[1] + "\n\n");

            w.write("Average Sales Per Day (Actual)\n");
            w.write("-----------------------------------------------------\n");
            w.write("Type,Total Sales,Avg Sales Per Day,% Total,# Items in Type,Sales Per Item\n");

            for (String type : order) {
                long total = totalByType.getOrDefault(type, 0L);
                double avg = actualAvg.getOrDefault(type, 0.0);
                double pct = actualGrand == 0 ? 0 : avg / actualGrand * 100.0;
                int itemCount = type.equals("Other")
                        ? catalog.allSkus().size()
                        - SPECIAL.stream()
                        .mapToInt(t -> catalog.skusByType()
                                .getOrDefault(t, List.of()).size()).sum()
                        : catalog.skusByType()
                        .getOrDefault(type, List.of()).size();

                double perItem = itemCount == 0 ? 0 : avg / itemCount;
                w.write(type + ","
                        + total + ","
                        + round2(avg) + ","
                        + String.format("%.2f%%", pct) + ","
                        + itemCount + ","
                        + round2(perItem) + "\n");
            }
            w.write("\n");
            w.write("Average Predicted Sales Per Day\n");
            w.write("-----------------------------------------------------\n");
            w.write("Type,Total Sales,Avg Sales Per Day,% Total,# Items in Type,Sales Per Item\n");
            for (String type : order) {
                double avg = expectedAvg.getOrDefault(type, 0.0);
                int dayn = firstNDates(windowDays).size();
                long total = Math.round(avg * dayn);
                double pct = expectedGrand == 0 ? 0 : avg / expectedGrand * 100.0;

                int itemCount = type.equals("Other")
                        ? catalog.allSkus().size()
                        - SPECIAL.stream()
                        .mapToInt(t -> catalog.skusByType()
                                .getOrDefault(t, List.of()).size()).sum()
                        : catalog.skusByType()
                        .getOrDefault(type, List.of()).size();

                double perItem = itemCount == 0 ? 0 : avg / itemCount;

                w.write(type + ","
                        + total + ","
                        + round2(avg) + ","
                        + String.format("%.2f%%", pct) + ","
                        + itemCount + ","
                        + round2(perItem) + "\n");
            }
            w.write("\nTotal sales," + totalItemsInWindow(windowDays) + "\n");
        }
    }


    private void exportTop10Json(Path path) throws Exception {
        List<Map.Entry<Integer, Long>> top10 = getTop10();

        try (BufferedWriter w = Files.newBufferedWriter(path)) {
            w.write("[\n");
            for (int i = 0; i < top10.size(); i++) {
                Map.Entry<Integer, Long> e = top10.get(i);
                Product p = catalog.productsBySku().get(e.getKey());

                w.write("  {\n");
                w.write("    \"rank\": " + (i + 1) + ",\n");
                w.write("    \"sku\": " + e.getKey() + ",\n");
                w.write("    \"count\": " + e.getValue() + ",\n");
                w.write("    \"name\": \"" + escape(p != null ? p.name() : "UNKNOWN") + "\"\n");
                w.write("  }");
                if (i < top10.size() - 1) {
                    w.write(",");
                }
                w.write("\n");
            }
            w.write("]\n");
        }
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static String escape(String s) {
        return s.replace("\"", "\\\"");
    }
}
