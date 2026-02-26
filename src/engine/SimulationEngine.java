package engine;

import config.SimulationConfig;
import model.Product;
import model.ProductCatalog;
import rng.FunctionalRNG;
import stats.Aggregator;
import stats.CustomerSummary;
import stats.CustomerSummaryCollector;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class SimulationEngine {

    private final ProductCatalog catalog;
    private final Aggregator aggregator;
    private final FunctionalRNG rng;
    private final CustomerSummaryCollector summaryCollector;

    private final Map<String, List<Integer>> skusByType;
    private final List<Integer> allSkus;

    public SimulationEngine(
            ProductCatalog catalog,
            Aggregator aggregator,
            CustomerSummaryCollector summaryCollector
    ) {
        this.catalog = catalog;
        this.aggregator = aggregator;
        this.summaryCollector = summaryCollector;
        this.rng = new FunctionalRNG(SimulationConfig.GLOBAL_SEED);
        this.skusByType = catalog.skusByType();
        this.allSkus = catalog.allSkus();
    }

    public void run() {
        int dayIndex;

        for (int storeId = 1; storeId <= SimulationConfig.STORE_COUNT; storeId++) {
            dayIndex = 0;

            for (LocalDate date = SimulationConfig.START_DATE;
                 !date.isAfter(SimulationConfig.END_DATE_INCLUSIVE);
                 date = date.plusDays(1), dayIndex++) {

                int customersToday = customersForDay(storeId, date, dayIndex);
                aggregator.addCustomers(date, customersToday);

                for (int customerId = 1; customerId <= customersToday; customerId++) {

                    CustomerSummary cs = null;
                    if (summaryCollector != null) {
                        cs = new CustomerSummary(date, storeId, customerId);
                        String key = date + "-" + storeId + "-" + customerId;
                        summaryCollector.mark(key, cs);
                    }

                    int targetItems = rng.uniformIntInclusive(
                            SimulationConfig.ITEMS_PER_CUSTOMER_LOW_INCLUSIVE,
                            SimulationConfig.ITEMS_PER_CUSTOMER_HIGH_INCLUSIVE,
                            storeId, dayIndex, customerId,
                            RuleId.ITEM_COUNT, 0
                    );

                    int itemsAdded = 0;

                    itemsAdded = applyMilkCereal(storeId, date, dayIndex, customerId, targetItems, itemsAdded, cs);
                    if (itemsAdded >= targetItems) continue;

                    itemsAdded = applyBabyFoodDiapers(storeId, date, dayIndex, customerId, targetItems, itemsAdded, cs);
                    if (itemsAdded >= targetItems) continue;

                    itemsAdded = applyBread(storeId, date, dayIndex, customerId, targetItems, itemsAdded, cs);
                    if (itemsAdded >= targetItems) continue;

                    itemsAdded = applyPeanutButterJam(storeId, date, dayIndex, customerId, targetItems, itemsAdded, cs);
                    if (itemsAdded >= targetItems) continue;

                    fillRandomItems(storeId, date, dayIndex, customerId, targetItems, itemsAdded);
                }
            }
        }
    }

    private int customersForDay(int storeId, LocalDate date, int dayIndex) {
        int base = rng.uniformIntInclusive(
                SimulationConfig.WEEKDAY_CUSTOMERS_LOW_INCLUSIVE,
                SimulationConfig.WEEKDAY_CUSTOMERS_HIGH_INCLUSIVE,
                storeId, dayIndex, 0,
                RuleId.CUSTOMERS_FOR_DAY, 0
        );

        if (isWeekend(date)) {
            base += SimulationConfig.WEEKEND_CUSTOMER_INCREASE;
        }
        return base;
    }

    private static boolean isWeekend(LocalDate date) {
        DayOfWeek d = date.getDayOfWeek();
        return d == DayOfWeek.SATURDAY || d == DayOfWeek.SUNDAY;
    }

    private int applyMilkCereal(
            int storeId, LocalDate date, int dayIndex, int customerId,
            int target, int itemsAdded, CustomerSummary cs
    ) {
        boolean buysMilk = rng.bernoulli(0.70, storeId, dayIndex, customerId, RuleId.MILK, 0);

        if (buysMilk) {
            if (cs != null) cs.boughtMilk = true;

            itemsAdded = buyOneByType(
                    SimulationConfig.TYPE_MILK,
                    storeId, date, dayIndex, customerId,
                    RuleId.MILK_PICK, itemsAdded
            );
            if (itemsAdded >= target) return itemsAdded;

            boolean buysCereal = rng.bernoulli(0.50, storeId, dayIndex, customerId, RuleId.CEREAL_GIVEN_MILK, 0);
            if (buysCereal) {
                if (cs != null) cs.boughtCereal = true;
                itemsAdded = buyOneByType(
                        SimulationConfig.TYPE_CEREAL,
                        storeId, date, dayIndex, customerId,
                        RuleId.CEREAL_PICK, itemsAdded
                );
            }
        } else {
            boolean buysCereal = rng.bernoulli(0.05, storeId, dayIndex, customerId, RuleId.CEREAL_WITHOUT_MILK, 0);
            if (buysCereal) {
                if (cs != null) cs.boughtCereal = true;
                itemsAdded = buyOneByType(
                        SimulationConfig.TYPE_CEREAL,
                        storeId, date, dayIndex, customerId,
                        RuleId.CEREAL_PICK, itemsAdded
                );
            }
        }
        return itemsAdded;
    }

    private int applyBabyFoodDiapers(
            int storeId, LocalDate date, int dayIndex, int customerId,
            int target, int itemsAdded, CustomerSummary cs
    ) {
        boolean buysBaby = rng.bernoulli(0.20, storeId, dayIndex, customerId, RuleId.BABY_FOOD, 0);

        if (buysBaby) {
            if (cs != null) cs.boughtBabyFood = true;

            itemsAdded = buyOneByType(
                    SimulationConfig.TYPE_BABY_FOOD,
                    storeId, date, dayIndex, customerId,
                    RuleId.BABY_PICK, itemsAdded
            );
            if (itemsAdded >= target) return itemsAdded;

            boolean buysDiapers = rng.bernoulli(0.80, storeId, dayIndex, customerId, RuleId.DIAPERS_GIVEN_BABY, 0);
            if (buysDiapers) {
                if (cs != null) cs.boughtDiapers = true;
                itemsAdded = buyOneByType(
                        SimulationConfig.TYPE_DIAPERS,
                        storeId, date, dayIndex, customerId,
                        RuleId.DIAPERS_PICK, itemsAdded
                );
            }
        } else {
            boolean buysDiapers = rng.bernoulli(0.01, storeId, dayIndex, customerId, RuleId.DIAPERS_WITHOUT_BABY, 0);
            if (buysDiapers) {
                if (cs != null) cs.boughtDiapers = true;
                itemsAdded = buyOneByType(
                        SimulationConfig.TYPE_DIAPERS,
                        storeId, date, dayIndex, customerId,
                        RuleId.DIAPERS_PICK, itemsAdded
                );
            }
        }
        return itemsAdded;
    }

    private int applyBread(
            int storeId, LocalDate date, int dayIndex, int customerId,
            int target, int itemsAdded, CustomerSummary cs
    ) {
        boolean buysBread = rng.bernoulli(0.50, storeId, dayIndex, customerId, RuleId.BREAD, 0);
        if (buysBread) {
            if (cs != null) cs.boughtBread = true;
            itemsAdded = buyOneByType(
                    SimulationConfig.TYPE_BREAD,
                    storeId, date, dayIndex, customerId,
                    RuleId.BREAD_PICK, itemsAdded
            );
        }
        return itemsAdded;
    }

    private int applyPeanutButterJam(
            int storeId, LocalDate date, int dayIndex, int customerId,
            int target, int itemsAdded, CustomerSummary cs
    ) {
        boolean buysPb = rng.bernoulli(0.10, storeId, dayIndex, customerId, RuleId.PEANUT_BUTTER, 0);

        if (buysPb) {
            if (cs != null) cs.boughtPB = true;

            itemsAdded = buyOneByType(
                    SimulationConfig.TYPE_PEANUT_BUTTER,
                    storeId, date, dayIndex, customerId,
                    RuleId.PB_PICK, itemsAdded
            );
            if (itemsAdded >= target) return itemsAdded;

            boolean buysJam = rng.bernoulli(0.90, storeId, dayIndex, customerId, RuleId.JAM_GIVEN_PB, 0);
            if (buysJam) {
                if (cs != null) cs.boughtJam = true;
                itemsAdded = buyOneByType(
                        SimulationConfig.TYPE_JELLY_JAM,
                        storeId, date, dayIndex, customerId,
                        RuleId.JAM_PICK, itemsAdded
                );
            }
        } else {
            boolean buysJam = rng.bernoulli(0.05, storeId, dayIndex, customerId, RuleId.JAM_WITHOUT_PB, 0);
            if (buysJam) {
                if (cs != null) cs.boughtJam = true;
                itemsAdded = buyOneByType(
                        SimulationConfig.TYPE_JELLY_JAM,
                        storeId, date, dayIndex, customerId,
                        RuleId.JAM_PICK, itemsAdded
                );
            }
        }
        return itemsAdded;
    }

    private void fillRandomItems(
            int storeId, LocalDate date, int dayIndex, int customerId,
            int target, int itemsAdded
    ) {
        for (int k = itemsAdded; k < target; k++) {
            int idx = rng.uniformIntInclusive(
                    0, allSkus.size() - 1,
                    storeId, dayIndex, customerId,
                    RuleId.RANDOM_PICK, k
            );
            emitTransaction(storeId, date, customerId, allSkus.get(idx));
        }
    }

    private int buyOneByType(
            String type,
            int storeId, LocalDate date, int dayIndex, int customerId,
            long rulePickId,
            int itemsAdded
    ) {
        List<Integer> skus = skusByType.get(type);
        if (skus == null || skus.isEmpty()) return itemsAdded;

        int idx = rng.uniformIntInclusive(
                0, skus.size() - 1,
                storeId, dayIndex, customerId,
                rulePickId, itemsAdded
        );
        emitTransaction(storeId, date, customerId, skus.get(idx));
        return itemsAdded + 1;
    }

    private void emitTransaction(int storeId, LocalDate date, int customerId, int sku) {
        Product p = catalog.productsBySku().get(sku);
        if (p == null) return;

        double salePrice = round2(p.basePrice() * SimulationConfig.PRICE_MULTIPLIER);
        aggregator.accept(date, storeId, customerId, sku, salePrice);
    }

    private static double round2(double v) {
        return Math.round(v * 100.0) / 100.0;
    }

    private static final class RuleId {
        private RuleId() {}

        static final long CUSTOMERS_FOR_DAY = 1;
        static final long ITEM_COUNT = 2;

        static final long MILK = 10;
        static final long MILK_PICK = 11;
        static final long CEREAL_GIVEN_MILK = 12;
        static final long CEREAL_WITHOUT_MILK = 13;
        static final long CEREAL_PICK = 14;

        static final long BABY_FOOD = 20;
        static final long BABY_PICK = 21;
        static final long DIAPERS_GIVEN_BABY = 22;
        static final long DIAPERS_WITHOUT_BABY = 23;
        static final long DIAPERS_PICK = 24;

        static final long BREAD = 30;
        static final long BREAD_PICK = 31;

        static final long PEANUT_BUTTER = 40;
        static final long PB_PICK = 41;
        static final long JAM_GIVEN_PB = 42;
        static final long JAM_WITHOUT_PB = 43;
        static final long JAM_PICK = 44;

        static final long RANDOM_PICK = 90;
    }
}
