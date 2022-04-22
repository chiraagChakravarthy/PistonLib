package ca.fxco.configurablepistons.base;

import ca.fxco.configurablepistons.ConfigurablePistons;
import net.minecraft.block.*;
import net.minecraft.block.entity.PistonBlockEntity;
import net.minecraft.block.enums.PistonType;
import net.minecraft.block.piston.PistonBehavior;
import net.minecraft.entity.Entity;
import net.minecraft.entity.MovementType;
import net.minecraft.nbt.NbtCompound;
import net.minecraft.nbt.NbtHelper;
import net.minecraft.server.network.ServerPlayerEntity;
import net.minecraft.state.property.Properties;
import net.minecraft.util.math.*;
import net.minecraft.util.shape.VoxelShape;
import net.minecraft.util.shape.VoxelShapes;
import net.minecraft.world.BlockView;
import net.minecraft.world.World;

import java.util.Iterator;
import java.util.List;

public class BasicPistonBlockEntity extends PistonBlockEntity {

    /*
     * This class overrides all the non-static methods of PistonBlockEntity, and all static methods are
     * redirected to this class.
     * This class is used for all Piston Block Entities
     */

    public BasicPistonBlockEntity(BlockPos pos, BlockState state) {
        super(pos, state);
    }

    public BasicPistonBlockEntity(BlockPos pos, BlockState state, BlockState pushedBlock, Direction facing, boolean extending, boolean source) {
        super(pos, state, pushedBlock, facing, extending, source);
    }

    public NbtCompound toInitialChunkDataNbt() {
        return this.createNbt();
    }

    @Override
    public float getAmountExtended(float progress) {
        return this.extending ? progress - 1.0F : 1.0F - progress;
    }

    @Override
    public BlockState getHeadBlockState() {
        // Removed setting the type since this is currently only used for collision shape
        return !this.isExtending() && this.isSource() && this.pushedBlock.getBlock() instanceof PistonBlock ?
                Blocks.PISTON_HEAD.getDefaultState()
                        .with(PistonHeadBlock.SHORT, this.progress > 0.25F)
                        .with(PistonHeadBlock.FACING, this.pushedBlock.get(PistonBlock.FACING)) :
                this.pushedBlock;
    }

    public static void pushEntities(World world, BlockPos pos, float f, BasicPistonBlockEntity blockEntity) {
        Direction direction = blockEntity.getMovementDirection();
        double d = f - blockEntity.progress;
        VoxelShape voxelShape = blockEntity.getHeadBlockState().getCollisionShape(world, pos);
        if (!voxelShape.isEmpty()) {
            Box box = offsetHeadBox(pos, voxelShape.getBoundingBox(), blockEntity);
            List<Entity> list = world.getOtherEntities(null, Boxes.stretch(box, direction, d).union(box));
            if (!list.isEmpty()) {
                List<Box> list2 = voxelShape.getBoundingBoxes();
                boolean bl = blockEntity.pushedBlock.isOf(Blocks.SLIME_BLOCK);
                Iterator<Entity> entityIterator = list.iterator();
                while(true) {
                    Entity entity;
                    while(true) {
                        do {
                            if (!entityIterator.hasNext()) return;
                            entity = entityIterator.next();
                        } while(entity.getPistonBehavior() == PistonBehavior.IGNORE);
                        if (!bl) break;
                        if (!(entity instanceof ServerPlayerEntity)) {
                            Vec3d vec3d = entity.getVelocity();
                            double e = vec3d.x;
                            double g = vec3d.y;
                            double h = vec3d.z;
                            switch (direction.getAxis()) {
                                case X -> e = direction.getOffsetX();
                                case Y -> g = direction.getOffsetY();
                                case Z -> h = direction.getOffsetZ();
                            }
                            entity.setVelocity(e, g, h);
                            break;
                        }
                    }
                    double i = 0.0;
                    for (Box box2 : list2) {
                        Box box3 = Boxes.stretch(offsetHeadBox(pos, box2, blockEntity), direction, d);
                        Box box4 = entity.getBoundingBox();
                        if (box3.intersects(box4)) {
                            i = Math.max(i, getIntersectionSize(box3, direction, box4));
                            if (i >= d) break;
                        }
                    }
                    if (!(i <= 0.0)) {
                        i = Math.min(i, d) + 0.01;
                        moveEntity(direction, entity, i, direction);
                        if (!blockEntity.extending && blockEntity.source) push(pos, entity, direction, d);
                    }
                }
            }
        }
    }

    public static void moveEntity(Direction direction, Entity entity, double d, Direction direction2) {
        field_12205.set(direction);
        entity.move(MovementType.PISTON, new Vec3d(d * (double)direction2.getOffsetX(), d * (double)direction2.getOffsetY(), d * (double)direction2.getOffsetZ()));
        field_12205.set(null);
    }

    public static void moveEntitiesInHoneyBlock(World world, BlockPos pos, float f, BasicPistonBlockEntity blockEntity) {
        if (blockEntity.isPushingHoneyBlock()) {
            Direction direction = blockEntity.getMovementDirection();
            if (direction.getAxis().isHorizontal()) {
                double d = blockEntity.pushedBlock.getCollisionShape(world, pos).getMax(Direction.Axis.Y);
                Box box = offsetHeadBox(pos, new Box(0.0, d, 0.0, 1.0, 1.5000000999999998, 1.0), blockEntity);
                double e = f - blockEntity.progress;
                List<Entity> list = world.getOtherEntities(null, box, (entityx) -> canMoveEntity(box, entityx));
                for (Entity entity : list) {
                    moveEntity(direction, entity, e, direction);
                }
            }
        }
    }

    public static boolean canMoveEntity(Box box, Entity entity) {
        return entity.getPistonBehavior() == PistonBehavior.NORMAL && entity.isOnGround() && entity.getX() >= box.minX && entity.getX() <= box.maxX && entity.getZ() >= box.minZ && entity.getZ() <= box.maxZ;
    }

    @Override
    public boolean isPushingHoneyBlock() {
        return this.pushedBlock.isOf(Blocks.HONEY_BLOCK);
    }

    @Override
    public Direction getMovementDirection() {
        return this.extending ? this.facing : this.facing.getOpposite();
    }

    public static double getIntersectionSize(Box box, Direction direction, Box box2) {
        switch (direction) {
            case EAST:
                return box.maxX - box2.minX;
            case WEST:
                return box2.maxX - box.minX;
            case UP:
            default:
                return box.maxY - box2.minY;
            case DOWN:
                return box2.maxY - box.minY;
            case SOUTH:
                return box.maxZ - box2.minZ;
            case NORTH:
                return box2.maxZ - box.minZ;
        }
    }

    public static Box offsetHeadBox(BlockPos pos, Box box, BasicPistonBlockEntity blockEntity) {
        double d = blockEntity.getAmountExtended(blockEntity.progress);
        return box.offset((double)pos.getX() + d * (double)blockEntity.facing.getOffsetX(), (double)pos.getY() + d * (double)blockEntity.facing.getOffsetY(), (double)pos.getZ() + d * (double)blockEntity.facing.getOffsetZ());
    }

    public static void push(BlockPos pos, Entity entity, Direction direction, double amount) {
        Box box = entity.getBoundingBox();
        Box box2 = VoxelShapes.fullCube().getBoundingBox().offset(pos);
        if (box.intersects(box2)) {
            Direction direction2 = direction.getOpposite();
            double d = getIntersectionSize(box2, direction2, box) + 0.01;
            double e = getIntersectionSize(box2, direction2, box.intersection(box2)) + 0.01;
            if (Math.abs(d - e) < 0.01) {
                d = Math.min(d, amount) + 0.01;
                moveEntity(direction, entity, d, direction2);
            }
        }
    }

    @Override
    public BlockState getPushedBlock() {
        return this.pushedBlock;
    }

    @Override
    public void finish() {
        if (this.world != null && (this.lastProgress < 1.0F || this.world.isClient)) {
            this.progress = 1.0F;
            this.lastProgress = this.progress;
            this.world.removeBlockEntity(this.pos);
            this.markRemoved();
            if (this.world.getBlockState(this.pos).isOf(Blocks.MOVING_PISTON)) {
                BlockState blockState;
                if (this.source) {
                    blockState = Blocks.AIR.getDefaultState();
                } else {
                    blockState = Block.postProcessState(this.pushedBlock, this.world, this.pos);
                }
                this.world.setBlockState(this.pos, blockState, Block.NOTIFY_ALL);
                this.world.updateNeighbor(this.pos, blockState.getBlock(), this.pos);
            }
        }
    }

    public static void tick(World world, BlockPos pos, BlockState state, BasicPistonBlockEntity blockEntity) {
        blockEntity.savedWorldTime = world.getTime();
        blockEntity.lastProgress = blockEntity.progress;
        if (blockEntity.lastProgress >= 1.0F) {
            if (world.isClient && blockEntity.field_26705 < 5) {
                ++blockEntity.field_26705;
            } else {
                world.removeBlockEntity(pos);
                blockEntity.markRemoved();
                if (world.getBlockState(pos).isOf(Blocks.MOVING_PISTON)) {
                    BlockState blockState = Block.postProcessState(blockEntity.pushedBlock, world, pos);
                    if (blockState.isAir()) {
                        world.setBlockState(pos, blockEntity.pushedBlock, Block.NO_REDRAW | Block.FORCE_STATE | Block.MOVED);
                        Block.replace(blockEntity.pushedBlock, blockState, world, pos, 3);
                    } else {
                        if (blockState.contains(Properties.WATERLOGGED) && blockState.get(Properties.WATERLOGGED)) {
                            blockState = blockState.with(Properties.WATERLOGGED, false);
                        }
                        world.setBlockState(pos, blockState, Block.NOTIFY_ALL | Block.MOVED);
                        world.updateNeighbor(pos, blockState.getBlock(), pos);
                    }
                }
            }
        } else {
            float f = blockEntity.progress + 0.5F;
            pushEntities(world, pos, f, blockEntity);
            moveEntitiesInHoneyBlock(world, pos, f, blockEntity);
            blockEntity.progress = f;
            if (blockEntity.progress >= 1.0F) blockEntity.progress = 1.0F;
        }
    }

    @Override
    public void readNbt(NbtCompound nbt) {
        super.readNbt(nbt);
        this.pushedBlock = NbtHelper.toBlockState(nbt.getCompound("blockState"));
        this.facing = Direction.byId(nbt.getInt("facing"));
        this.progress = nbt.getFloat("progress");
        if (ConfigurablePistons.PISTON_PROGRESS_FIX) {
            this.lastProgress = nbt.contains("lastProgress") ? nbt.getFloat("lastProgress") : this.progress;
        } else {
            this.lastProgress = this.progress;
        }
        this.extending = nbt.getBoolean("extending");
        this.source = nbt.getBoolean("source");
    }

    @Override
    public void writeNbt(NbtCompound nbt) {
        super.writeNbt(nbt);
        nbt.put("blockState", NbtHelper.fromBlockState(this.pushedBlock));
        nbt.putInt("facing", this.facing.getId());
        if (ConfigurablePistons.PISTON_PROGRESS_FIX) {
            nbt.putFloat("progress", this.progress);
            nbt.putFloat("lastProgress", this.lastProgress);
        } else {
            nbt.putFloat("progress", this.lastProgress);
        }
        nbt.putBoolean("extending", this.extending);
        nbt.putBoolean("source", this.source);
    }

    @Override
    public VoxelShape getCollisionShape(BlockView world, BlockPos pos) {
        VoxelShape voxelShape;
        if (!this.extending && this.source && this.pushedBlock.getBlock() instanceof PistonBlock) {
            voxelShape = this.pushedBlock.with(PistonBlock.EXTENDED, true).getCollisionShape(world, pos);
        } else {
            voxelShape = VoxelShapes.empty();
        }
        Direction direction = field_12205.get();
        if ((double)this.progress < 1.0 && direction == this.getMovementDirection()) {
            return voxelShape;
        } else {
            BlockState blockState;
            if (this.isSource()) {
                blockState = Blocks.PISTON_HEAD.getDefaultState()
                        .with(PistonHeadBlock.FACING, this.facing)
                        .with(PistonHeadBlock.SHORT, this.extending != 1.0F - this.progress < 0.25F);
            } else {
                blockState = this.pushedBlock;
            }
            float f = this.getAmountExtended(this.progress);
            double d = (float)this.facing.getOffsetX() * f;
            double e = (float)this.facing.getOffsetY() * f;
            double g = (float)this.facing.getOffsetZ() * f;
            return VoxelShapes.union(voxelShape, blockState.getCollisionShape(world, pos).offset(d, e, g));
        }
    }

    @Override
    public long getSavedWorldTime() {
        return this.savedWorldTime;
    }
}
