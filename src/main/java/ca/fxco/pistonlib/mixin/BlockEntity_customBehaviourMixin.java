package ca.fxco.pistonlib.mixin;

import ca.fxco.pistonlib.pistonLogic.internal.BlockEntityBaseMerging;
import net.minecraft.world.level.block.entity.BlockEntity;
import org.spongepowered.asm.mixin.Mixin;

@Mixin(BlockEntity.class)
public class BlockEntity_customBehaviourMixin implements BlockEntityBaseMerging {}
