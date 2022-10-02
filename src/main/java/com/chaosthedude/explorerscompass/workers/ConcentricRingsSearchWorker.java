package com.chaosthedude.explorerscompass.workers;

import java.util.List;

import com.chaosthedude.explorerscompass.ExplorersCompass;
import com.chaosthedude.explorerscompass.config.ExplorersCompassConfig;
import com.mojang.datafixers.util.Pair;

import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.item.ItemStack;
import net.minecraft.server.world.ServerWorld;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.util.math.ChunkSectionPos;
import net.minecraft.world.gen.chunk.placement.ConcentricRingsStructurePlacement;
import net.minecraft.world.gen.feature.ConfiguredStructureFeature;

public class ConcentricRingsSearchWorker extends StructureSearchWorker<ConcentricRingsStructurePlacement> {

	private List<ChunkPos> potentialChunks;
	private int chunkIndex;
	private double minDistance;
	private Pair<BlockPos, ConfiguredStructureFeature<?, ?>> closest;

	public ConcentricRingsSearchWorker(ServerWorld level, PlayerEntity player, ItemStack stack, BlockPos startPos, ConcentricRingsStructurePlacement placement, List<ConfiguredStructureFeature<?, ?>> configuredStructureSet) {
		super(level, player, stack, startPos, placement, configuredStructureSet);

		minDistance = Double.MAX_VALUE;
		chunkIndex = 0;
		potentialChunks = level.getChunkManager().getChunkGenerator().getConcentricRingsStartChunks(placement);

		finished = !level.getServer().getSaveProperties().getGeneratorOptions().shouldGenerateStructures() || potentialChunks == null || potentialChunks.isEmpty();
	}

	@Override
	public void start() {
		if (!stack.isEmpty() && stack.getItem() == ExplorersCompass.EXPLORERS_COMPASS_ITEM) {
			if (ExplorersCompassConfig.maxRadius > 0) {
				ExplorersCompass.LOGGER.info("Starting search with ConcentricRingsSearchWorker: " + ExplorersCompassConfig.maxSamples + " max samples");
				WorldWorkerManager.addWorker(this);
			} else {
				fail();
			}
		}
	}

	@Override
	public boolean hasWork() {
		// Samples for this placement are not necessarily in order of closest to
		// furthest, so disregard radius
		return !finished && samples < ExplorersCompassConfig.maxSamples && chunkIndex < potentialChunks.size();
	}

	@Override
	public boolean doWork() {
		super.doWork();
		if (hasWork()) {
			ChunkPos chunkPos = potentialChunks.get(chunkIndex);
			currentPos = new BlockPos(ChunkSectionPos.getOffsetPos(chunkPos.x, 8), 0, ChunkSectionPos.getOffsetPos(chunkPos.z, 8));
			double distance = startPos.getSquaredDistance(currentPos);

			if (closest == null || distance < minDistance) {
				Pair<BlockPos, ConfiguredStructureFeature<?, ?>> pair = getStructureGeneratingAt(chunkPos);
				if (pair != null) {
					minDistance = distance;
					closest = pair;
				}
			}

			samples++;
			chunkIndex++;
		}

		if (hasWork()) {
			return true;
		}

		if (closest != null) {
			succeed(closest.getFirst(), closest.getSecond());
		} else if (!finished) {
			fail();
		}

		return false;
	}

	// Non-optimized method to get the closest structure, for testing purposes
	private Pair<BlockPos, ConfiguredStructureFeature<?, ?>> getClosest() {
		List<ChunkPos> list = level.getChunkManager().getChunkGenerator().getConcentricRingsStartChunks(placement);
		if (list == null) {
			return null;
		} else {
			Pair<BlockPos, ConfiguredStructureFeature<?, ?>> closestPair = null;
			double minDistance = Double.MAX_VALUE;
			BlockPos.Mutable sampleBlockPos = new BlockPos.Mutable();
			for (ChunkPos chunkPos : list) {
				sampleBlockPos.set(ChunkSectionPos.getOffsetPos(chunkPos.x, 8), 32, ChunkSectionPos.getOffsetPos(chunkPos.z, 8));
				double distance = sampleBlockPos.getSquaredDistance(startPos);
				if (closestPair == null || distance < minDistance) {
					Pair<BlockPos, ConfiguredStructureFeature<?, ?>> pair = getStructureGeneratingAt(chunkPos);
					if (pair != null) {
						closestPair = pair;
						minDistance = distance;
					}
				}
			}

			return closestPair;
		}
	}

}