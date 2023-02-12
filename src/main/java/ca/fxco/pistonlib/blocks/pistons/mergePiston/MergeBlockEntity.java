package ca.fxco.pistonlib.blocks.pistons.mergePiston;

import ca.fxco.pistonlib.PistonLibConfig;
import ca.fxco.pistonlib.base.ModBlockEntities;
import ca.fxco.pistonlib.base.ModBlocks;
import ca.fxco.pistonlib.base.ModPistonFamilies;
import ca.fxco.pistonlib.blocks.pistons.basePiston.BasicMovingBlockEntity;
import ca.fxco.pistonlib.helpers.Utils;
import ca.fxco.pistonlib.impl.BlockEntityMerging;
import ca.fxco.pistonlib.pistonLogic.accessible.ConfigurablePistonMerging;
import ca.fxco.pistonlib.pistonLogic.accessible.ConfigurablePistonStickiness;
import ca.fxco.pistonlib.pistonLogic.sticky.StickyType;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.HolderGetter;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.NbtUtils;
import net.minecraft.nbt.Tag;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.MoverType;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.Blocks;
import net.minecraft.world.level.block.EntityBlock;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.piston.MovingPistonBlock;
import net.minecraft.world.level.block.piston.PistonMath;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.level.material.PushReaction;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraft.world.phys.shapes.BooleanOp;
import net.minecraft.world.phys.shapes.Shapes;
import net.minecraft.world.phys.shapes.VoxelShape;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static net.minecraft.world.level.block.piston.PistonMovingBlockEntity.NOCLIP;

public class MergeBlockEntity extends BlockEntity {

    protected final Map<Direction, MergeData> mergingBlocks = new HashMap<>();
    protected BlockState initialState;
    protected @Nullable BlockEntity initialBlockEntity;

    public MergeBlockEntity(BlockPos pos, BlockState state) {
        super(ModBlockEntities.MERGE_BLOCK_ENTITY, pos, state);
    }

    public MergeBlockEntity(BlockPos pos, BlockState state, BlockState initialState) {
        this(pos, state, initialState, null);
    }

    public MergeBlockEntity(BlockPos pos, BlockState state, BlockState initialState, BlockEntity initialBlockEntity) {
        super(ModBlockEntities.MERGE_BLOCK_ENTITY, pos, state);

        this.initialState = initialState;
        this.initialBlockEntity = initialBlockEntity;
    }

    // Should always be called before calling `canMerge()`
    public boolean canMergeFromSide(Direction pushDirection) {
        return !mergingBlocks.containsKey(pushDirection);
    }

    public boolean canMerge(BlockState state, Direction dir) {
        ConfigurablePistonMerging merge = (ConfigurablePistonMerging) initialState.getBlock();
        if (merge.canMultiMerge() &&
                merge.canMultiMerge(state, level, worldPosition, initialState, dir, mergingBlocks)) {
            return initialBlockEntity == null || (!merge.getBlockEntityMergeRules().checkMerge() ||
                    initialBlockEntity instanceof BlockEntityMerging bem &&
                    bem.canMultiMerge(state, initialState, dir, mergingBlocks));
        }
        return false;
    }

    public void doMerge(BlockState state, Direction dir, float speed) {
        MergeData data = new MergeData(state);
        data.setSpeed(speed);
        mergingBlocks.put(dir, data);
    }

    public void doMerge(BlockState state, BlockEntity blockEntity, Direction dir, float speed) {
        MergeData data = new MergeData(blockEntity, state);
        data.setSpeed(speed);
        mergingBlocks.put(dir, data);
    }

    public static void tick(Level level, BlockPos blockPos, BlockState blockState, MergeBlockEntity mergeBlockEntity) {
        for (MergeData data : mergeBlockEntity.mergingBlocks.values()) {
            data.setLastProgress(data.getProgress());
            float lastProgress = data.getLastProgress();
            float f = lastProgress + 0.5F * data.getSpeed();
            mergeBlockEntity.moveCollidedEntities(f);
            //moveStuckEntities(level, blockPos, f, mergeBlockEntity);
            data.setProgress(Math.min(f, 1.0F));
        }
        mergeBlockEntity.tryFinish();
    }

    public @Nullable BlockEntity getInitialBlockEntity() {
        return this.initialBlockEntity;
    }

    protected void moveCollidedEntities(float nextProgress) {
        VoxelShape initialShape = this.initialState.getCollisionShape(this.level, this.worldPosition);
        List<AABB>[] blockAabbs = new ArrayList[6];
        double[] deltaProgresses = new double[6];
        for (Map.Entry<Direction, MergeData> entry : this.mergingBlocks.entrySet()) {
            MergeData data = entry.getValue();
            Direction dir = entry.getKey();

            VoxelShape blockShape = data.getState().getCollisionShape(this.level, this.worldPosition);
            if (!blockShape.isEmpty()) {
                double maxProgress = (double)1.0F - data.progress;
                blockShape.move(
                        (double)this.worldPosition.getX() + maxProgress * (double)dir.getStepX(),
                        (double)this.worldPosition.getY() + maxProgress * (double)dir.getStepY(),
                        (double)this.worldPosition.getZ() + maxProgress * (double)dir.getStepZ()
                );
                double deltaProgress = nextProgress - data.progress;
                deltaProgresses[dir.ordinal()] = deltaProgress;
                blockAabbs[dir.ordinal()] = blockShape.toAabbs();
                initialShape = Shapes.join(initialShape, blockShape, BooleanOp.OR);
            }
        }

        AABB totalBlockBounds = initialShape.isEmpty() ? new AABB(0,0,0,1,1,1) : initialShape.bounds();

        List<Entity> entities = this.level.getEntities(null, totalBlockBounds);
        if (entities.isEmpty()) {
            return;
        }

        for (Entity entity : entities) {
            if (entity.getPistonPushReaction() == PushReaction.IGNORE) {
                continue;
            }

            AABB entityAabb = entity.getBoundingBox();

            for (Map.Entry<Direction, MergeData> entry : this.mergingBlocks.entrySet()) {
                Direction dir = entry.getKey();
                double movement = 0.0D;
                int ord = dir.ordinal();
                double delta = deltaProgresses[ord];
                for (AABB blockAabb : blockAabbs[ord]) {
                    blockAabb = PistonMath.getMovementArea(blockAabb, dir, delta);
                    if (blockAabb.intersects(entityAabb)) {
                        movement = Math.max(movement, getMovement(blockAabb, dir, entityAabb));

                        if (movement >= delta) {
                            break;
                        }
                    }
                }
                if (movement <= 0.0D) {
                    continue;
                }

                moveEntity(dir, entity, Math.min(movement, delta) + (0.01D * entry.getValue().getSpeed()), dir);
            }

            //fixEntityWithinPistonBase(entity, Direction.UP, 1, float movementMargin);
        }
    }

    /*protected void fixEntityWithinPistonBase(Entity entity, Direction moveDir, double deltaProgress, float movementMargin) {
        AABB entityAabb = entity.getBoundingBox();
        AABB baseAabb = Shapes.block().bounds().move(this.worldPosition);

        if (entityAabb.intersects(baseAabb)) {
            Direction opp = moveDir.getOpposite();
            double d = getMovement(baseAabb, opp, entityAabb) + this.movementMargin();
            double e = getMovement(baseAabb, opp, entityAabb.intersect(baseAabb)) + this.movementMargin();

            if (Math.abs(d - e) < this.movementMargin()) {
                moveEntity(moveDir, entity, Math.min(d, deltaProgress) + this.movementMargin(), opp);
            }
        }
    }*/

    protected static void moveEntity(Direction noclipDir, Entity entity, double amount, Direction moveDir) {
        NOCLIP.set(noclipDir);
        entity.move(MoverType.PISTON, new Vec3(
                amount * moveDir.getStepX(),
                amount * moveDir.getStepY(),
                amount * moveDir.getStepZ()
        ));
        NOCLIP.set(null);
    }

    private static double getMovement(AABB aABB, Direction direction, AABB aABB2) {
        switch (direction) {
            case EAST:
                return aABB.maxX - aABB2.minX;
            case WEST:
                return aABB2.maxX - aABB.minX;
            case UP:
            default:
                return aABB.maxY - aABB2.minY;
            case DOWN:
                return aABB2.maxY - aABB.minY;
            case SOUTH:
                return aABB.maxZ - aABB2.minZ;
            case NORTH:
                return aABB2.maxZ - aABB.minZ;
        }
    }

    public float getProgress(float f, float progress, float lastProgress) {
        if (f > 1.0F) {
            f = 1.0F;
        }

        return Mth.lerp(f, lastProgress, progress);
    }

    public float getXOff(Direction dir, float f, float progress, float lastProgress) {
        return (float)dir.getStepX() * (this.getProgress(f, progress, lastProgress) - 1);
    }

    public float getYOff(Direction dir, float f, float progress, float lastProgress) {
        return (float)dir.getStepY() * (this.getProgress(f, progress, lastProgress) - 1);
    }

    public float getZOff(Direction dir, float f, float progress, float lastProgress) {
        return (float)dir.getStepZ() * (this.getProgress(f, progress, lastProgress) - 1);
    }

    public BlockState getInitialState() {
        return this.initialState;
    }

    public Map<Direction, MergeData> getMergingBlocks() {
        return this.mergingBlocks;
    }

    public void load(CompoundTag compoundTag) {
        super.load(compoundTag);
        HolderGetter<Block> holderGetter = this.level != null ?
                this.level.holderLookup(Registries.BLOCK) : BuiltInRegistries.BLOCK.asLookup();
        this.initialState = NbtUtils.readBlockState(holderGetter, compoundTag.getCompound("state"));
        if (compoundTag.contains("be", Tag.TAG_COMPOUND)) {
            EntityBlock movedBlock = (EntityBlock)this.initialState.getBlock();
            this.initialBlockEntity = movedBlock.newBlockEntity(BlockPos.ZERO, this.initialState);
            this.initialBlockEntity.load(compoundTag.getCompound("be"));
        }
        for (Direction dir : Direction.values()) {
            if (compoundTag.contains("dir" + dir.ordinal(), Tag.TAG_COMPOUND)) {
                CompoundTag tag = compoundTag.getCompound("dir" + dir.ordinal());
                mergingBlocks.put(dir, MergeData.loadNbt(holderGetter, tag));
            }
        }
    }

    protected void saveAdditional(CompoundTag compoundTag) {
        super.saveAdditional(compoundTag);
        compoundTag.put("state", NbtUtils.writeBlockState(initialState));
        if (this.initialBlockEntity != null) {
            compoundTag.put("be", this.initialBlockEntity.saveWithoutMetadata());
        }
        for (Map.Entry<Direction, MergeData> entry : mergingBlocks.entrySet()) {
            compoundTag.put("dir" + entry.getKey().ordinal(), MergeData.writeNbt(entry.getValue()));
        }
    }

    public void tryFinish(){
        int count = 0;
        for(MergeData data : mergingBlocks.values()){
            if(data.lastProgress<1){
                return;
            }
            count++;
        }
        BlockPos blockPos = worldPosition;
        level.removeBlockEntity(blockPos);
        setRemoved();

        BlockState initialState = this.initialState;
        if (initialState == null) return;
        ConfigurablePistonMerging merge = (ConfigurablePistonMerging) initialState.getBlock();
        BlockState newState = null;
        if (count > 1) {
            Map<Direction, BlockState> states = new HashMap<>();
            for (Map.Entry<Direction, MergeData> entry : mergingBlocks.entrySet()) {
                states.put(entry.getKey(), entry.getValue().getState());
            }
            newState = merge.doMultiMerge(level, blockPos, states, initialState);
        } else {
            for (Map.Entry<Direction, MergeData> entry : mergingBlocks.entrySet()) {
                newState = merge.doMerge(entry.getValue().getState(), level, blockPos, initialState, entry.getKey());
                break;
            }
        }
        if (newState == null) {
            newState = Blocks.AIR.defaultBlockState();
        }
        BlockState blockState2 = Block.updateFromNeighbourShapes(newState, level, blockPos);
        if (blockState2.isAir()) {
            level.setBlock(blockPos, newState, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_KNOWN_SHAPE | Block.UPDATE_CLIENTS);
            Block.updateOrDestroy(newState, blockState2, level, blockPos, Block.UPDATE_ALL);
        } else {
            if (initialBlockEntity != null) {
                BlockEntityMerging initialBem = (BlockEntityMerging)initialBlockEntity;
                initialBlockEntity.setLevel(level);
                initialBlockEntity.setBlockState(blockState2);
                initialBem.beforeInitialFinalMerge(blockState2, mergingBlocks);
                for (MergeData data : mergingBlocks.values()) {
                    if (data.hasBlockEntity()) {
                        ((BlockEntityMerging)data.getBlockEntity()).onAdvancedFinalMerge(initialBlockEntity);
                    }
                }
                initialBem.afterInitialFinalMerge(blockState2, mergingBlocks);
                Utils.setBlockWithEntity(level, blockPos, blockState2, initialBlockEntity, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
            } else {
                level.setBlock(blockPos, blockState2, Block.UPDATE_MOVE_BY_PISTON | Block.UPDATE_ALL);
            }
            level.neighborChanged(blockPos, blockState2.getBlock(), blockPos);
        }
    }

    public void finalTick(Direction facing) {
        finalTick(facing, false);
    }

    public void finalTick(Direction facing, boolean skipStickiness) {
        if(this.level == null)
            return;
        if(!mergingBlocks.containsKey(facing))
            return;

        MergeData data = mergingBlocks.get(facing);
        float progressO = data.lastProgress;
        data.setAllProgress(1);
        tryFinish();

        if (!skipStickiness && PistonLibConfig.strongBlockDropping) {
            BlockState movedState = data.state;
            ConfigurablePistonStickiness stick = (ConfigurablePistonStickiness) movedState.getBlock();

            if (stick.usesConfigurablePistonStickiness() && stick.isSticky(movedState)) {
                this.finalTickStuckNeighbors(stick.stickySides(movedState), facing, progressO);
            }
        }
    }

    public void finalTickStuckNeighbors(Map<Direction, StickyType> stickyTypes, Direction movementDirection, float progressO) {
        for (Map.Entry<Direction, StickyType> entry : stickyTypes.entrySet()) {
            StickyType stickyType = entry.getValue();

            if (stickyType.ordinal() < StickyType.STRONG.ordinal()) { // only strong or fused
                continue;
            }

            Direction dir = entry.getKey();
            BlockPos neighborPos = this.worldPosition.relative(dir);
            BlockState neighborState = this.level.getBlockState(neighborPos);

            if (neighborState.getBlock() instanceof MovingPistonBlock mpb) {
                BlockEntity blockEntity = this.level.getBlockEntity(neighborPos);

                if (blockEntity instanceof BasicMovingBlockEntity mbe) {
                    if (movementDirection == mbe.getMovementDirection() && progressO == mbe.progress) {
                        // Maybe do a stick test?
                        mbe.finalTick();
                    }
                }
            }
            if(neighborState.is(ModBlocks.MERGE_BLOCK)){
                BlockEntity blockEntity = this.level.getBlockEntity(neighborPos);
                if(blockEntity instanceof MergeBlockEntity mbe){
                    mbe.finalTick(movementDirection);
                }
            }
        }
    }

    public static class MergeData {

        private final BlockState state;
        private final BlockEntity be;
        private float progress;
        private float lastProgress;
        private float speed = 1F;
        public MergeData(BlockState state) {
            this(null, state);
        }

        public MergeData(@Nullable BlockEntity blockEntity, BlockState state) {
            this.state = state;
            this.be = blockEntity;
        }

        public boolean hasBlockEntity() {
            return be != null;
        }

        public BlockEntity getBlockEntity() {
            return be;
        }

        public BlockState getState() {
            return state;
        }

        public float getProgress() {
            return progress;
        }

        public float getLastProgress() {
            return lastProgress;
        }

        public float getSpeed() {
            return speed;
        }

        public void setProgress(float progress) {
            this.progress = progress;
        }

        public void setLastProgress(float lastProgress) {
            this.lastProgress = lastProgress;
        }

        public void setAllProgress(float progress) {
            this.progress = this.lastProgress = progress;
        }

        public void setSpeed(float speed) {
            this.speed = speed;
        }

        public static CompoundTag writeNbt(MergeData data) {
            CompoundTag compoundTag = new CompoundTag();
            compoundTag.put("state", NbtUtils.writeBlockState(data.getState()));
            if (data.hasBlockEntity()) {
                compoundTag.put("be", data.getBlockEntity().saveWithoutMetadata());
            }
            if (data.getProgress() == data.getLastProgress()) {
                compoundTag.putFloat("progress", data.getProgress());
            } else {
                compoundTag.putFloat("progress", data.getProgress());
                compoundTag.putFloat("lastProgress", data.getLastProgress());
            }
            if (data.getSpeed() != 1F) {
                compoundTag.putFloat("speed", data.getSpeed());
            }
            return compoundTag;
        }

        public static MergeData loadNbt(HolderGetter<Block> holderGetter, CompoundTag compoundTag) {
            BlockState state = NbtUtils.readBlockState(holderGetter, compoundTag.getCompound("state"));
            BlockEntity entity;
            if (compoundTag.contains("be", Tag.TAG_COMPOUND)) {
                EntityBlock movedBlock = (EntityBlock)state.getBlock();
                entity = movedBlock.newBlockEntity(BlockPos.ZERO, state);
                entity.load(compoundTag.getCompound("be"));
            } else {
                entity = null;
            }
            MergeData data = new MergeData(entity, state);
            if (compoundTag.contains("lastProgress", Tag.TAG_FLOAT)) {
                data.setProgress(compoundTag.getFloat("progress"));
                data.setLastProgress(compoundTag.getFloat("lastProgress"));
            } else {
                data.setAllProgress(compoundTag.getFloat("float"));
            }
            if (compoundTag.contains("speed")) {
                data.setSpeed(compoundTag.getFloat("speed"));
            }
            return data;
        }
    }
}
