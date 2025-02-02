/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.blocks.metal;

import blusunrize.immersiveengineering.api.IEProperties;
import blusunrize.immersiveengineering.api.client.IModelOffsetProvider;
import blusunrize.immersiveengineering.api.energy.MutableEnergyStorage;
import blusunrize.immersiveengineering.api.excavator.ExcavatorHandler;
import blusunrize.immersiveengineering.api.excavator.MineralWorldInfo;
import blusunrize.immersiveengineering.common.blocks.IEBaseBlockEntity;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IHasDummyBlocks;
import blusunrize.immersiveengineering.common.blocks.IEBlockInterfaces.IPlayerInteraction;
import blusunrize.immersiveengineering.common.blocks.ticking.IEClientTickableBE;
import blusunrize.immersiveengineering.common.blocks.ticking.IEServerTickableBE;
import blusunrize.immersiveengineering.common.config.IEServerConfig;
import blusunrize.immersiveengineering.common.items.CoresampleItem;
import blusunrize.immersiveengineering.common.items.CoresampleItem.VeinSampleData;
import blusunrize.immersiveengineering.common.register.IEItems.Misc;
import blusunrize.immersiveengineering.common.util.EnergyHelper;
import blusunrize.immersiveengineering.common.util.ItemNBTHelper;
import blusunrize.immersiveengineering.common.util.MultiblockCapability;
import blusunrize.immersiveengineering.common.util.Utils;
import net.minecraft.core.BlockPos;
import net.minecraft.core.Direction;
import net.minecraft.core.Vec3i;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.Tag;
import net.minecraft.world.InteractionHand;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.context.BlockPlaceContext;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.entity.BlockEntityType;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.common.capabilities.Capability;
import net.minecraftforge.common.util.LazyOptional;
import net.minecraftforge.energy.CapabilityEnergy;
import net.minecraftforge.energy.IEnergyStorage;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.List;

public class SampleDrillBlockEntity extends IEBaseBlockEntity implements IEServerTickableBE, IEClientTickableBE,
		IHasDummyBlocks, IPlayerInteraction, IModelOffsetProvider
{
	public MutableEnergyStorage energyStorage = new MutableEnergyStorage(8000);
	public int dummy = 0;
	public int process = 0;
	public boolean isRunning = false;
	@Nonnull
	public ItemStack sample = ItemStack.EMPTY;
	private final MultiblockCapability<IEnergyStorage> energyCap = MultiblockCapability.make(
			this, be -> be.energyCap, SampleDrillBlockEntity::master, registerEnergyInput(energyStorage)
	);

	public SampleDrillBlockEntity(BlockEntityType<SampleDrillBlockEntity> type, BlockPos pos, BlockState state)
	{
		super(type, pos, state);
	}

	@Override
	public boolean canTickAny()
	{
		return sample.isEmpty();
	}

	@Override
	public void tickClient()
	{
		if(isRunning)
			process++;
	}

	@Override
	public void tickServer()
	{
		final int consumption = IEServerConfig.MACHINES.coredrill_consumption.get();
		final int totalTime = IEServerConfig.MACHINES.coredrill_time.get();
		boolean canRun = process > 0
				&&process < totalTime
				&&energyStorage.getEnergyStored() >= consumption
				&&!isRSPowered()
				&&!level.isEmptyBlock(getBlockPos().offset(0, -1, 0));

		if(canRun&&energyStorage.extractEnergy(consumption, false)==consumption)
		{
			process++;
			if(process >= totalTime)
			{
				MineralWorldInfo info = ExcavatorHandler.getMineralWorldInfo(level, getBlockPos());
				this.sample = createCoreSample(info);
				this.process = 0;
				canRun = false;
				this.markContainingBlockForUpdate(null);
			}
			this.markChunkDirty();
		}
		if(canRun!=isRunning)
		{
			isRunning = canRun;
			this.markChunkDirty();
			this.markContainingBlockForUpdate(null);
		}
	}

	public float getSampleProgress()
	{
		return process/(float)IEServerConfig.MACHINES.coredrill_time.get();
	}

	public boolean isSamplingFinished()
	{
		return process >= IEServerConfig.MACHINES.coredrill_time.get();
	}

	@Nullable
	public List<VeinSampleData> getVein()
	{
		if(sample.isEmpty())
			return null;
		else
			return CoresampleItem.getVeins(sample);
	}

	@Nonnull
	public ItemStack createCoreSample(@Nullable MineralWorldInfo info)
	{
		ItemStack stack = new ItemStack(Misc.CORESAMPLE);
		ItemNBTHelper.putLong(stack, "timestamp", level.getGameTime());
		CoresampleItem.setDimension(stack, level.dimension());
		CoresampleItem.setCoords(stack, getBlockPos());
		CoresampleItem.setMineralInfo(stack, info, getBlockPos());
		return stack;
	}

	@Override
	public void writeCustomNBT(CompoundTag nbt, boolean descPacket)
	{
		EnergyHelper.serializeTo(energyStorage, nbt);
		nbt.putInt("dummy", dummy);
		nbt.putInt("process", process);
		nbt.putBoolean("isRunning", isRunning);
		if(!sample.isEmpty())
			nbt.put("sample", sample.save(new CompoundTag()));
	}

	@Override
	public void readCustomNBT(CompoundTag nbt, boolean descPacket)
	{
		EnergyHelper.deserializeFrom(energyStorage, nbt);
		dummy = nbt.getInt("dummy");
		process = nbt.getInt("process");
		isRunning = nbt.getBoolean("isRunning");
		if(nbt.contains("sample", Tag.TAG_COMPOUND))
			sample = ItemStack.of(nbt.getCompound("sample"));
		else
			sample = ItemStack.EMPTY;
	}

	private AABB renderAABB;

	@Override
	public AABB getRenderBoundingBox()
	{
		if(renderAABB==null)
		{
			if(dummy==0)
				renderAABB = new AABB(getBlockPos(), getBlockPos().offset(1, 3, 1));
			else
				renderAABB = new AABB(getBlockPos(), getBlockPos());
		}
		return renderAABB;
	}

	@Nonnull
	@Override
	public <T> LazyOptional<T> getCapability(@Nonnull Capability<T> cap, @Nullable Direction side)
	{
		if(cap==CapabilityEnergy.ENERGY&&(side==null||(dummy==0&&side.getAxis().isHorizontal())))
			return energyCap.getAndCast();
		return super.getCapability(cap, side);
	}

	@Override
	public boolean isDummy()
	{
		return dummy > 0;
	}

	@Nullable
	@Override
	public SampleDrillBlockEntity master()
	{
		if(!isDummy())
			return this;
		BlockPos masterPos = getBlockPos().below(dummy);
		BlockEntity te = Utils.getExistingTileEntity(level, masterPos);
		return te instanceof SampleDrillBlockEntity drill?drill: null;
	}

	@Override
	public void placeDummies(BlockPlaceContext ctx, BlockState state)
	{
		state = state.setValue(IEProperties.MULTIBLOCKSLAVE, true);
		for(int i = 1; i <= 2; i++)
		{
			level.setBlockAndUpdate(worldPosition.offset(0, i, 0), state);
			((SampleDrillBlockEntity)level.getBlockEntity(worldPosition.offset(0, i, 0))).dummy = i;
		}
	}

	@Override
	public void breakDummies(BlockPos pos, BlockState state)
	{
		for(int i = 0; i <= 2; i++)
			if(level.getBlockEntity(getBlockPos().offset(0, -dummy, 0).offset(0, i, 0)) instanceof SampleDrillBlockEntity)
				level.removeBlock(getBlockPos().offset(0, -dummy, 0).offset(0, i, 0), false);
	}

	@Override
	public boolean interact(Direction side, Player player, InteractionHand hand, ItemStack heldItem, float hitX, float hitY, float hitZ)
	{
		if(dummy!=0)
		{
			BlockEntity te = level.getBlockEntity(getBlockPos().offset(0, -dummy, 0));
			if(te instanceof SampleDrillBlockEntity)
				return ((SampleDrillBlockEntity)te).interact(side, player, hand, heldItem, hitX, hitY, hitZ);
		}

		if(!this.sample.isEmpty())
		{
			if(!level.isClientSide)
			{
				player.spawnAtLocation(this.sample.copy(), .5f);
				this.sample = ItemStack.EMPTY;
				setChanged();
				this.markContainingBlockForUpdate(null);
			}
			return true;
		}
		else if(this.process <= 0)
		{
			if(!level.isClientSide&&energyStorage.getEnergyStored() >= IEServerConfig.MACHINES.coredrill_consumption.get())
			{
				this.process = 1;
				setChanged();
				this.markContainingBlockForUpdate(null);
			}
			return true;
		}
		return false;
	}

	@Override
	public BlockPos getModelOffset(BlockState state, @Nullable Vec3i size)
	{
		return new BlockPos(0, dummy, 0);
	}
}