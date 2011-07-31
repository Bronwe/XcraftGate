package de.xcraft.engelier.XcraftGate.Generator;

import java.util.Random;

import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Biome;
import org.bukkit.block.Block;

public class PopulatorTundra extends PopulatorHelper {

	@Override
	public void populate(World world, Random random, Chunk chunk) {
		for (int x = 0; x < 16; x++) {
			for (int z = 0; z < 16; z++) {
				int realX = (chunk.getX() * 16) + x;
				int realZ = (chunk.getZ() * 16) + z;
				int y = world.getHighestBlockYAt(realX, realZ);

				Block block = world.getBlockAt(realX, y, realZ);
				if (block.getBiome() != Biome.TUNDRA) continue;
				
				Block blockBelow = world.getBlockAt(realX, y - 1, realZ);
				
				if (block.getType() == Material.AIR && blockBelow.getType() != Material.WATER && blockBelow.getType() != Material.LAVA) {
					block.setType(Material.SNOW);
				}
				
				if (blockBelow.getType() == Material.WATER) {
					blockBelow.setType(Material.ICE);
				}
			}
		}
	}
	
}
