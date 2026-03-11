package stats;


import model.Product;
import model.ProductCatalog;

import java.io.BufferedWriter;
import java.io.IOException;
import java.net.Inet4Address;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class InventoryManager {
    int IEMS_PER_CASE=12;
    private final ProductCatalog catalog;
    public InventoryManager(ProductCatalog productCatalog) {
        this.catalog = productCatalog;
        initalInventory();
    }
    private final Map<Integer,Integer> itemsLeft=new HashMap<>();
    private final Map<Integer,Integer> caseOrder=new HashMap<>();

    private void initalInventory() {
        for(int sku:catalog.allSkus()) {
            Product p=catalog.productsBySku().get(sku);
            int Items;
            if(p!=null&&p.type().equals("Milk")){
                Items=(int) Math.ceil(1.5*100);
            }else{
                Items=3*100;
            }

            itemsLeft.put(sku,Items);

            int initialCases=(int) Math.ceil(Items/(double)IEMS_PER_CASE);
            caseOrder.put(sku,initialCases);
        }

    }
    public boolean inStock(int sku) {
        return itemsLeft.getOrDefault(sku,0)>0;
    }

    public int sell(int sku){
        int Left=itemsLeft.getOrDefault(sku,0);
        if(Left>0){
            Left--;
            itemsLeft.put(sku,Left);
        }
        return Left;
    }
    public int getItemsLeft(int sku){
        return itemsLeft.getOrDefault(sku,0);
    }
    public int getCaseOrder(int sku){
        return caseOrder.getOrDefault(sku,0);
    }

    public void experJson(Path path) throws Exception {
        try(BufferedWriter w= Files.newBufferedWriter(path)){
            w.write("[\n");

            boolean first=true;
            for(int sku:catalog.allSkus()){
                if(!first){
                    w.write(",\n");
                }
                first=false;
                int inventory=itemsLeft.getOrDefault(sku,0);
                int cases=caseOrder.getOrDefault(sku,0);
                w.write(" {\n");
                w.write(" \"sku\": "+sku+",\n");
                w.write("\"inventory\": "+inventory+",\n");
                w.write("\"caseOrdered\": "+cases+"\n");
                w.write("}");
            }
            w.write("\n]");
        }
    }
    public void processDelivery(LocalDate date){
        for(int sku:catalog.allSkus()){
            Product p=catalog.productsBySku().get(sku);
            int currItems=itemsLeft.getOrDefault(sku,0);
            int targetItems;
            if(p!=null&&p.type().equals("Milk")){
                targetItems=(int)Math.ceil(1.5*100);
            }else {
                targetItems=3*100;
            }
            if(currItems<targetItems){
                int need=targetItems-currItems;
                int cases=(int) Math.ceil(need/(double)IEMS_PER_CASE);
                int itemsAdded=cases*IEMS_PER_CASE;
                itemsLeft.put(sku,currItems+itemsAdded);
                int totalCases=caseOrder.getOrDefault(sku,0);
                caseOrder.put(sku,totalCases+cases);

            }
        }
    }
}
