package ca.fxco.pistonlib.blocks.pistons.translocationPiston;

import java.util.List;

import ca.fxco.pistonlib.blocks.pistons.basePiston.BasicMovingBlockEntity;
import ca.fxco.pistonlib.pistonLogic.families.PistonFamily;

import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.VoxelShape;

public class TranslocationMovingBlockEntity extends BasicMovingBlockEntity {

    public TranslocationMovingBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public TranslocationMovingBlockEntity(PistonFamily family, BlockPos pos, BlockState state, BlockState movedState,
                                          BlockEntity movedBlockEntity, Direction facing, boolean extending,
                                          boolean isSourcePiston) {
        super(family, pos, state, movedState, movedBlockEntity, facing, extending, isSourcePiston);
    }

    @Override
    public void moveCollidedEntities(float f) {
        VoxelShape vs = this.getMovedState().getCollisionShape(this.level, this.worldPosition);
        if (vs.isEmpty()) return;
        AABB box = vs.bounds().move(this.worldPosition).inflate(0.01D); // Cheating ;)
        List<Entity> list = this.level.getEntities(null, box);
        if (list.isEmpty()) return;
        boolean isSlime = this.getMovedState().is(Blocks.SLIME_BLOCK);
        Direction dir = this.getMovementDirection();
        for (Entity entity : list) {
            if (entity.getPistonPushReaction() != PushReaction.IGNORE) {
                if (isSlime) { //No player check, just like before
                    Vec3 vec3d = entity.getDeltaMovement();
                    switch (dir.getAxis()) {
                        case X -> entity.setDeltaMovement(dir.getStepX(), vec3d.y, vec3d.z);
                        case Y -> entity.setDeltaMovement(vec3d.x, dir.getStepY(), vec3d.z);
                        case Z -> entity.setDeltaMovement(vec3d.x, vec3d.y, dir.getStepZ());
                    }
                }
                double x = 0.0D, y = 0.0D, z = 0.0D;
                AABB entityBox = entity.getBoundingBox();
                boolean positive = dir.getAxisDirection() == Direction.AxisDirection.POSITIVE;
                switch (dir.getAxis()) {
                    case X -> x = (positive ? box.maxX - entityBox.minX : entityBox.maxX - box.minX) + 0.1D;
                    case Y -> y = (positive ? box.maxY - entityBox.minY : entityBox.maxY - box.minY) + 0.1D;
                    case Z -> z = (positive ? box.maxZ - entityBox.minZ : entityBox.maxZ - box.minZ) + 0.1D;
                }
                entity.move(MoverType.SELF, new Vec3(
                        x * (double) dir.getStepX(),
                        y * (double) dir.getStepY(),
                        z * (double) dir.getStepZ()
                ));
            }
        }
    }
}
