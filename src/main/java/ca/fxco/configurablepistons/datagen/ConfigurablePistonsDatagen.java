package ca.fxco.configurablepistons.datagen;

import ca.fxco.configurablepistons.ConfigurablePistons;
import org.slf4j.Logger;

import net.fabricmc.fabric.api.datagen.v1.DataGeneratorEntrypoint;
import net.fabricmc.fabric.api.datagen.v1.FabricDataGenerator;

public class ConfigurablePistonsDatagen implements DataGeneratorEntrypoint {
	public static final Logger LOGGER = ConfigurablePistons.LOGGER;

	@Override
	public void onInitializeDataGenerator(FabricDataGenerator dataGenerator) {
		LOGGER.info("Starting Configurable Pistons datagen...");

		dataGenerator.addProvider(ModModelProvider::new);
		dataGenerator.addProvider(ModRecipeProvider::new);
		dataGenerator.addProvider(ModBlockLootTableProvider::new);
		dataGenerator.addProvider(ModBlockTagProvider::new);
	}
}
