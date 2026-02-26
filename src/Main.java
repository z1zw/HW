import config.SimulationConfig;
import engine.SimulationEngine;
import io.ProductLoader;
import model.ProductCatalog;
import stats.Aggregator;
import stats.CustomerSummaryCollector;
import stats.ToSQLite;
import stats.Top10DailyExporter;

import java.nio.file.Path;
public class Main {
    public static void main(String[] args) throws Exception {
        Path productsPath = SimulationConfig.PRODUCTS_FILE_PATH;
        ProductCatalog catalog = ProductLoader.load(productsPath);
        Aggregator aggregator = new Aggregator(catalog);
        CustomerSummaryCollector summaryCollector = null;
        if (SimulationConfig.SANITY_CHECK_ENABLED) {
            summaryCollector = new CustomerSummaryCollector();
        }
        SimulationEngine engine = new SimulationEngine(
                catalog,
                aggregator,
                summaryCollector
        );
        engine.run();
        aggregator.printSummary();
        if (SimulationConfig.SANITY_CHECK_ENABLED && summaryCollector != null) {
            summaryCollector.export(SimulationConfig.SANITY_CHECK_OUTPUT_PATH);
        }

        if (SimulationConfig.EXPORT_D3_JSON) {
            aggregator.exportD3Json(SimulationConfig.D3_OUTPUT_DIR);
        }
        Top10DailyExporter.export(
                aggregator,
                catalog,
                Path.of("Dataset", "top10_daily.json")
        );
        aggregator.exportHw3Full(
                Path.of("Dataset", "hw3_comparsion.csv")
        );

    }
}
