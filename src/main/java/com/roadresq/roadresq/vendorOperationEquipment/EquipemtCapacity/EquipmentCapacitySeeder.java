package com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity;

import com.mongodb.bulk.BulkWriteResult;
import com.roadresq.roadresq.vendorOperation.ServiceType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityType;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.CapacityUnit;
import com.roadresq.roadresq.vendorOperationEquipment.EquipemtCapacity.EquipmentCapacity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.mongodb.core.BulkOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.data.mongodb.core.query.Update;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@ConditionalOnProperty(
        name = "roadresq.equipment-capacity-seeding.enabled",
        havingValue = "true",
        matchIfMissing = true
)
public class EquipmentCapacitySeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(EquipmentCapacitySeeder.class);
    private final MongoTemplate mongoTemplate;

    public EquipmentCapacitySeeder(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<CapacityOption> options = buildDefaultOptions();
        if (options.isEmpty()) {
            return;
        }

        LocalDateTime now = LocalDateTime.now();
        BulkOperations bulk = mongoTemplate.bulkOps(
                BulkOperations.BulkMode.UNORDERED,
                EquipmentCapacity.class
        );

        for (CapacityOption option : options) {
            // Match either the original seeded ID or an equivalent admin-created option.
            // Existing rows are never modified: names, active flags and pictures are retained.
            Criteria sameSpecification = Criteria.where("serviceType").is(option.serviceType())
                    .and("equipmentType").is(option.equipmentType())
                    .and("capacityType").is(option.capacityType())
                    .and("capacityValue").is(option.capacityValue())
                    .and("capacityUnit").is(option.capacityUnit())
                    .and("voltage").is(option.voltage());

            Query query = Query.query(new Criteria().orOperator(
                    Criteria.where("_id").is(option.id()),
                    sameSpecification
            ));

            Update insertOnly = new Update()
                    .setOnInsert("_id", option.id())
                    .setOnInsert("serviceType", option.serviceType())
                    .setOnInsert("equipmentType", option.equipmentType())
                    .setOnInsert("capacityType", option.capacityType())
                    .setOnInsert("capacityValue", option.capacityValue())
                    .setOnInsert("capacityUnit", option.capacityUnit())
                    .setOnInsert("voltage", option.voltage())
                    .setOnInsert("displayName", option.displayName())
                    .setOnInsert("pictures", Set.of())
                    .setOnInsert("active", option.active())
                    .setOnInsert("createdAt", now)
                    .setOnInsert("updatedAt", now);

            bulk.upsert(query, insertOnly);
        }

        BulkWriteResult result = bulk.execute();
        int inserted = result.getUpserts().size();
        log.info("Equipment capacity catalogue: {} new options, {} already present ({} total definitions)",
                inserted, options.size() - inserted, options.size());
    }

    private static List<CapacityOption> buildDefaultOptions() {
        List<CapacityOption> options = new ArrayList<>();

        // TOWING: payload and towing rating are different specifications.
        add(options, ServiceType.TOWING, "MOTORCYCLE_TOW_TRAILER", CapacityType.RATED_PAYLOAD,
                CapacityUnit.KG, null, 200, 300, 400, 500, 750, 1000, 1500);
        add(options, ServiceType.TOWING, "FLATBED_TOW_TRUCK", CapacityType.RATED_PAYLOAD,
                CapacityUnit.KG, null, 750, 1000, 1250, 1500, 1750, 2000, 2500, 3000,
                3500, 4000, 4500, 5000, 6000, 7500, 10000, 12000);
        add(options, ServiceType.TOWING, "WHEEL_LIFT_TOW_TRUCK", CapacityType.RATED_TOWING_CAPACITY,
                CapacityUnit.KG, null, 1000, 1500, 2000, 2500, 3000, 3500, 4000,
                5000, 6000, 7500, 10000, 12000);
        add(options, ServiceType.TOWING, "INTEGRATED_TOW_TRUCK", CapacityType.RATED_TOWING_CAPACITY,
                CapacityUnit.KG, null, 2500, 3500, 5000, 7500, 10000, 12000,
                15000, 20000, 25000, 30000);
        add(options, ServiceType.TOWING, "VEHICLE_TRANSPORT_TRAILER", CapacityType.RATED_PAYLOAD,
                CapacityUnit.KG, null, 750, 1000, 1500, 2000, 2500, 3000, 3500,
                5000, 7500, 10000, 15000, 20000);
        add(options, ServiceType.TOWING, "HEAVY_DUTY_WRECKER", CapacityType.RATED_TOWING_CAPACITY,
                CapacityUnit.KG, null, 10000, 12000, 15000, 20000, 25000, 30000,
                40000, 50000, 60000, 80000);
        add(options, ServiceType.TOWING, "HEAVY_DUTY_LOW_LOADER", CapacityType.RATED_PAYLOAD,
                CapacityUnit.KG, null, 10000, 15000, 20000, 25000, 30000, 40000,
                50000, 60000, 75000);

        // FUEL DELIVERY: these represent container volume, not fuel currently available.
        // Local transport rules and container approval must be checked independently.
        add(options, ServiceType.FUEL_DELIVERY, "PORTABLE_FUEL_CAN", CapacityType.FUEL_VOLUME,
                CapacityUnit.LITRES, null, 1, 2, 4, 5, 7.5, 9.5, 10, 15, 19, 20, 25);
        add(options, ServiceType.FUEL_DELIVERY, "FUEL_JERRYCAN", CapacityType.FUEL_VOLUME,
                CapacityUnit.LITRES, null, 5, 10, 15, 20, 25, 30);
        add(options, ServiceType.FUEL_DELIVERY, "PORTABLE_FUEL_TANK", CapacityType.FUEL_VOLUME,
                CapacityUnit.LITRES, null, 20, 25, 30, 40, 50, 60, 75, 100,
                120, 150, 200, 250, 300);
        add(options, ServiceType.FUEL_DELIVERY, "VEHICLE_MOUNTED_FUEL_TANK", CapacityType.FUEL_VOLUME,
                CapacityUnit.LITRES, null, 50, 75, 100, 150, 200, 250, 300, 400,
                500, 750, 1000, 1500, 2000);
        add(options, ServiceType.FUEL_DELIVERY, "FUEL_DELIVERY_TANKER", CapacityType.FUEL_VOLUME,
                CapacityUnit.LITRES, null, 500, 750, 1000, 1500, 2000, 3000,
                5000, 7500, 10000, 15000, 20000);

        // BATTERY JUMP-START: peak amps alone cannot establish vehicle compatibility.
        // Voltage represents one operating voltage; this schema cannot express both in one row.
        add(options, ServiceType.BATTERY_JUMP_START, "PORTABLE_LITHIUM_JUMP_STARTER",
                CapacityType.PEAK_STARTING_CURRENT, CapacityUnit.AMPS, 12,
                300, 400, 500, 600, 800, 1000, 1200, 1500, 1750, 2000,
                2500, 3000, 3500, 4000, 5000, 6000);
        add(options, ServiceType.BATTERY_JUMP_START, "PROFESSIONAL_JUMP_STARTER",
                CapacityType.PEAK_STARTING_CURRENT, CapacityUnit.AMPS, 12,
                1000, 1500, 2000, 2500, 3000, 3500, 4000, 4500,
                5000, 6000, 7000, 8000, 10000);
        add(options, ServiceType.BATTERY_JUMP_START, "HEAVY_DUTY_JUMP_STARTER",
                CapacityType.PEAK_STARTING_CURRENT, CapacityUnit.AMPS, 24,
                1000, 1500, 2000, 2500, 3000, 4000, 5000, 6250, 7000, 8000, 10000);
        add(options, ServiceType.BATTERY_JUMP_START, "DUAL_VOLTAGE_JUMP_STARTER",
                CapacityType.PEAK_STARTING_CURRENT, CapacityUnit.AMPS, 12,
                1500, 2000, 2500, 3000, 4000, 5000, 6250, 7000, 8000);
        add(options, ServiceType.BATTERY_JUMP_START, "DUAL_VOLTAGE_JUMP_STARTER",
                CapacityType.PEAK_STARTING_CURRENT, CapacityUnit.AMPS, 24,
                1500, 2000, 2500, 3000, 4000, 5000, 6250, 7000, 8000);

        return List.copyOf(options);
    }

    private static void add(List<CapacityOption> options, ServiceType serviceType,
                            String equipmentType, CapacityType capacityType,
                            CapacityUnit capacityUnit, Integer voltage, double... capacityValues) {
        for (double value : capacityValues) {
            BigDecimal capacity = BigDecimal.valueOf(value).stripTrailingZeros();
            String number = capacity.toPlainString();
            String id = "CAP_" + serviceType + "_" + equipmentType + "_"
                    + capacityType + "_" + number.replace('.', '_') + "_"
                    + capacityUnit + "_" + (voltage == null ? "NA" : voltage + "V");
            String displayName = readableName(equipmentType) + " - " + number + " "
                    + capacityUnit + " (" + readableMeasure(capacityType) + ")"
                    + (voltage == null ? "" : " - " + voltage + "V");

            options.add(new CapacityOption(id, serviceType, equipmentType, capacityType,
                    capacity, capacityUnit, voltage, displayName, true));
        }
    }

    private static String readableName(String value) {
        return Stream.of(value.toLowerCase(Locale.ROOT).split("_"))
                .map(word -> Character.toUpperCase(word.charAt(0)) + word.substring(1))
                .collect(Collectors.joining(" "));
    }

    private static String readableMeasure(CapacityType type) {
        return switch (type) {
            case RATED_PAYLOAD -> "rated payload";
            case RATED_TOWING_CAPACITY -> "rated towing capacity";
            case FUEL_VOLUME -> "container volume";
            case PEAK_STARTING_CURRENT -> "peak starting current";
        };
    }

    private record CapacityOption(
            String id,
            ServiceType serviceType,
            String equipmentType,
            CapacityType capacityType,
            BigDecimal capacityValue,
            CapacityUnit capacityUnit,
            Integer voltage,
            String displayName,
            boolean active
    ) {}
}
