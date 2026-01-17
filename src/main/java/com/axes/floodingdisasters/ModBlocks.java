package com.axes.floodingdisasters;

import net.minecraft.world.item.BlockItem;
import net.minecraft.world.item.Item;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.state.BlockBehaviour;
import net.neoforged.neoforge.registries.DeferredRegister;
import net.neoforged.neoforge.registries.DeferredItem;
import net.neoforged.neoforge.registries.DeferredBlock;

public class ModBlocks {
    // Registries
    public static final DeferredRegister.Blocks BLOCKS = DeferredRegister.createBlocks(FloodingDisasters.MODID);
    public static final DeferredRegister.Items ITEMS = DeferredRegister.createItems(FloodingDisasters.MODID);

    // The Surge Water Block
    // We copy properties from Water, but make it replaceable so it doesn't trap players forever
    // inside ModBlocks.java
    public static final DeferredBlock<Block> SURGE_WATER = BLOCKS.register("surge_water",
            () -> new Block(BlockBehaviour.Properties.of() // Use 'of()' for a fresh start
                    .mapColor(net.minecraft.world.level.material.MapColor.WATER)
                    .replaceable()
                    .noCollission() // You can walk through it
                    .strength(100.0f)
                    .noLootTable()
                    .noOcclusion() // Fixes rendering glitches
                    .isValidSpawn((state, level, pos, entity) -> false) // Mobs don't spawn in it
            ));

    // A simple item to place it for testing (optional)
    public static final DeferredItem<Item> SURGE_WATER_ITEM = ITEMS.register("surge_water",
            () -> new BlockItem(SURGE_WATER.get(), new Item.Properties()));
}