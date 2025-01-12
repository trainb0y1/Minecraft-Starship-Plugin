package io.github.petercrawley.minecraftstarshipplugin.customMaterials

import io.github.petercrawley.minecraftstarshipplugin.MinecraftStarshipPlugin.Companion.plugin
import org.bukkit.Bukkit.*
import org.bukkit.Material
import org.bukkit.block.Block
import org.bukkit.block.BlockFace
import org.bukkit.block.data.BlockData
import org.bukkit.event.EventHandler
import org.bukkit.event.Listener
import org.bukkit.event.block.BlockPhysicsEvent
import org.bukkit.event.block.BlockPistonExtendEvent
import org.bukkit.event.block.BlockPistonRetractEvent
import org.bukkit.event.block.BlockPlaceEvent

class CustomBlocksListener : Listener {
	// For our purposes BlockPistonExtendEvent and BlockPistonRetractEvent can be handled the same way.
	private fun mushroomBlockMovedByPiston(blocks: List<Block>, direction: BlockFace) {
		val blocksToChange = mutableMapOf<Block, BlockData>()

		// Add each mushroom block to the list.
		blocks.forEach {
			if (it.type == Material.MUSHROOM_STEM) blocksToChange[it.getRelative(direction)] = it.blockData
		}

		if (blocksToChange.isNotEmpty()) {
			// Create a task to correct the blocks after the piston is done doing its thing.
			getScheduler().runTask(plugin, Runnable {
				blocksToChange.forEach { it.key.setBlockData(it.value, false) }
			})
		}
	}

	@EventHandler
	fun mushroomBlockPushedByPiston(event: BlockPistonExtendEvent) {
		mushroomBlockMovedByPiston(event.blocks, event.direction)
	}

	@EventHandler
	fun mushroomBlockPulledByPiston(event: BlockPistonRetractEvent) {
		mushroomBlockMovedByPiston(event.blocks, event.direction)
	}

	// If a mushroom block is placed force its faces to all be true.
	// This allows us to keep allowing the use of the blocks in builds.
	@EventHandler
	fun mushroomBlockPlaced(event: BlockPlaceEvent) {
		val block = event.blockPlaced

		if (block.type != Material.MUSHROOM_STEM) return // If it's not a mushroom stem, ignore it.

		block.setBlockData(getServer().createBlockData(Material.MUSHROOM_STEM), false) // A blank block data will have all sides set to true.
	}

	// Prevent the block faces from changing.
	// This has to be done on the main thread as doing it async will cause issues.
	// TODO: On the client the mushroom blocks flash with the incorrect faces very briefly, see if this can be avoided.
	@EventHandler
	fun mushroomBlockPhysicsEvent(event: BlockPhysicsEvent) {
		if (event.changedType != Material.MUSHROOM_STEM) return

		event.isCancelled = true

		val blocksToUpdate = mutableSetOf<Block>()
		val checkedBlocks = mutableSetOf<Block>()

		blocksToUpdate.add(event.block)

		// It is important to find every block that changed as these changes will be processed client side, so we need to update ALL of them to ensure they are all correct client side.
		while (blocksToUpdate.isNotEmpty()) {
			val block = blocksToUpdate.first()
			blocksToUpdate.remove(block)

			if (checkedBlocks.contains(block)) continue

			checkedBlocks.add(block)

			if (block.type != Material.MUSHROOM_STEM) continue

			getOnlinePlayers().forEach {
				if (it.world != event.block.world) return@forEach

				val minX = it.chunk.x - it.viewDistance
				val maxX = it.chunk.x + it.viewDistance
				val minZ = it.chunk.z - it.viewDistance
				val maxZ = it.chunk.z + it.viewDistance

				if (event.block.chunk.x !in minX .. maxX) return@forEach
				if (event.block.chunk.z !in minZ .. maxZ) return@forEach

				it.sendBlockChange(event.block.location, event.block.blockData)
			}

			blocksToUpdate.add(block.getRelative( 1,  0,  0))
			blocksToUpdate.add(block.getRelative(-1,  0,  0))
			blocksToUpdate.add(block.getRelative( 0,  1,  0))
			blocksToUpdate.add(block.getRelative( 0, -1,  0))
			blocksToUpdate.add(block.getRelative( 0,  0,  1))
			blocksToUpdate.add(block.getRelative( 0,  0, -1))
		}
	}
}