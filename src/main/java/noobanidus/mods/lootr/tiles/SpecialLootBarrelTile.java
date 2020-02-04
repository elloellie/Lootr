package noobanidus.mods.lootr.tiles;

import net.minecraft.block.BarrelBlock;
import net.minecraft.block.BlockState;
import net.minecraft.block.Blocks;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.inventory.IInventory;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.play.server.SUpdateTileEntityPacket;
import net.minecraft.tileentity.*;
import net.minecraft.util.*;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Vec3i;
import net.minecraft.world.server.ServerWorld;
import net.minecraft.world.storage.loot.LootContext;
import net.minecraft.world.storage.loot.LootParameterSets;
import net.minecraft.world.storage.loot.LootParameters;
import net.minecraft.world.storage.loot.LootTable;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.Constants;
import net.minecraftforge.common.util.LazyOptional;
import noobanidus.mods.lootr.config.ConfigManager;
import noobanidus.mods.lootr.data.BooleanData;
import noobanidus.mods.lootr.init.ModBlocks;
import noobanidus.mods.lootr.init.ModTiles;

import javax.annotation.Nullable;
import java.util.Random;

public class SpecialLootBarrelTile extends BarrelTileEntity implements ITickableTileEntity, ILootTile {
  private Random random = new Random();
  private ResourceLocation savedLootTable = null;
  private long seed = -1;
  private boolean synchronised = false;

  public SpecialLootBarrelTile() {
    super(ModTiles.SPECIAL_LOOT_BARREL);
  }

  public SpecialLootBarrelTile(TileEntityType<?> tile) {
    super(tile);
  }

  @Override
  public void setLootTable(ResourceLocation lootTableIn, long seedIn) {
    super.setLootTable(lootTableIn, seedIn);
    this.setLootTable(lootTableIn, seedIn, true);
  }

  public void setLootTable(ResourceLocation lootTableIn, long seedIn, boolean doSync) {
    this.savedLootTable = lootTableIn;
    this.seed = seedIn;
    if (doSync) {
      markForSync();
    }
  }

  @Override
  public void markForSync() {
    this.synchronised = false;
  }

  @Override
  public boolean isSpecialLootChest() {
    return savedLootTable != null;
  }

  @Override
  protected boolean checkLootAndRead(CompoundNBT compound) {
    if (isSpecialLootChest()) {
      return true;
    }
    return super.checkLootAndRead(compound);
  }

  @Override
  protected boolean checkLootAndWrite(CompoundNBT compound) {
    if (isSpecialLootChest()) {
      return true;
    }
    return super.checkLootAndWrite(compound);
  }

  @Override
  public void fillWithLoot(@Nullable PlayerEntity player) {
    // TODO: Override
  }

  @Override
  @SuppressWarnings({"unused", "Duplicates"})
  public void fillWithLoot(PlayerEntity player, IInventory inventory) {
    if (this.world != null && this.savedLootTable != null && this.world.getServer() != null) {
      LootTable loottable = this.world.getServer().getLootTableManager().getLootTableFromLocation(this.savedLootTable);
      LootContext.Builder builder = (new LootContext.Builder((ServerWorld) this.world)).withParameter(LootParameters.POSITION, new BlockPos(this.pos)).withSeed(ConfigManager.RANDOMISE_SEED.get() ? random.nextLong() : this.seed);
      if (player != null) {
        builder.withLuck(player.getLuck()).withParameter(LootParameters.THIS_ENTITY, player);
      }

      loottable.fillInventory(inventory, builder.build(LootParameterSets.CHEST));
    }
  }

  @SuppressWarnings("Duplicates")
  @Override
  public void read(CompoundNBT compound) {
    super.read(compound);
    if (compound.contains("specialLootChest_table", Constants.NBT.TAG_STRING)) {
      savedLootTable = new ResourceLocation(compound.getString("specialLootChest_table"));
    }
    if (compound.contains("specialLootChest_seed", Constants.NBT.TAG_LONG)) {
      seed = compound.getLong("specialLootChest_seed");
    }
    if (savedLootTable == null && compound.contains("LootTable", Constants.NBT.TAG_STRING)) {
      savedLootTable = new ResourceLocation(compound.getString("LootTable"));
      if (compound.contains("LootTableSeed", Constants.NBT.TAG_LONG)) {
        seed = compound.getLong("LootTableSeed");
      }
      setLootTable(savedLootTable, seed);
    }
  }

  @Override
  public CompoundNBT write(CompoundNBT compound) {
    compound = super.write(compound);
    if (savedLootTable != null) {
      compound.putString("specialLootBarrel_table", savedLootTable.toString());
    }
    if (seed != -1) {
      compound.putLong("specialLootBarrel_seed", seed);
    }
    return compound;
  }

  @Override
  public CompoundNBT getUpdateTag() {
    return write(super.getUpdateTag());
  }

  @Nullable
  @Override
  public SUpdateTileEntityPacket getUpdatePacket() {
    if (isSpecialLootChest()) {
      return new SUpdateTileEntityPacket(this.pos, 9, getUpdateTag());
    } else {
      return null;
    }
  }

  @Override
  public void onDataPacket(NetworkManager net, SUpdateTileEntityPacket pkt) {
    read(pkt.getNbtCompound());
  }

  // Specifically disabled to prevent weird interactions
  @Override
  public <T> LazyOptional<T> getCapability(Capability<T> cap, Direction side) {
    if (!isSpecialLootChest()) {
      return super.getCapability(cap, side);
    }

    return LazyOptional.empty();
  }

  @SuppressWarnings("Duplicates")
  @Override
  public void tick() {
    if (this.world != null && !synchronised) {
      if (!this.world.isRemote() && isSpecialLootChest()) {
        this.synchronised = true;
        BooleanData.markLootChest(world, getPos());
        BlockState state = this.world.getBlockState(getPos());
        if (state.getBlock() == Blocks.BARREL) {
          world.setBlockState(pos, ModBlocks.BARREL.getDefaultState().with(BarrelBlock.PROPERTY_FACING, state.get(BarrelBlock.PROPERTY_FACING)).with(BarrelBlock.PROPERTY_OPEN, state.get(BarrelBlock.PROPERTY_OPEN)));
          TileEntity te = world.getTileEntity(pos);
          if (te instanceof SpecialLootBarrelTile && te != this) {
            ((SpecialLootBarrelTile) te).setLootTable(savedLootTable, seed, false);
            BooleanData.markLootChest(world, getPos());
          }
        } else {
          world.notifyBlockUpdate(pos, state, state, 8);
        }
      }
    }
  }

  @Override
  public void barrelTick() {
    int lvt_1_1_ = this.pos.getX();
    int lvt_2_1_ = this.pos.getY();
    int lvt_3_1_ = this.pos.getZ();
    this.numPlayersUsing = ChestTileEntity.calculatePlayersUsing(this.world, this, lvt_1_1_, lvt_2_1_, lvt_3_1_);
    if (this.numPlayersUsing > 0) {
      this.scheduleTick();
    } else {
      BlockState lvt_4_1_ = this.getBlockState();
      if (lvt_4_1_.getBlock() != Blocks.BARREL) {
        this.remove();
        return;
      }

      boolean lvt_5_1_ = (Boolean)lvt_4_1_.get(BarrelBlock.PROPERTY_OPEN);
      if (lvt_5_1_) {
        this.playSound(lvt_4_1_, SoundEvents.BLOCK_BARREL_CLOSE);
        this.setOpenProperty(lvt_4_1_, false);
      }
    }

  }

  private void setOpenProperty(BlockState p_213963_1_, boolean p_213963_2_) {
    this.world.setBlockState(this.getPos(), (BlockState)p_213963_1_.with(BarrelBlock.PROPERTY_OPEN, p_213963_2_), 3);
  }

  private void playSound(BlockState p_213965_1_, SoundEvent p_213965_2_) {
    Vec3i lvt_3_1_ = ((Direction)p_213965_1_.get(BarrelBlock.PROPERTY_FACING)).getDirectionVec();
    double lvt_4_1_ = (double)this.pos.getX() + 0.5D + (double)lvt_3_1_.getX() / 2.0D;
    double lvt_6_1_ = (double)this.pos.getY() + 0.5D + (double)lvt_3_1_.getY() / 2.0D;
    double lvt_8_1_ = (double)this.pos.getZ() + 0.5D + (double)lvt_3_1_.getZ() / 2.0D;
    this.world.playSound((PlayerEntity)null, lvt_4_1_, lvt_6_1_, lvt_8_1_, p_213965_2_, SoundCategory.BLOCKS, 0.5F, this.world.rand.nextFloat() * 0.1F + 0.9F);
  }
}