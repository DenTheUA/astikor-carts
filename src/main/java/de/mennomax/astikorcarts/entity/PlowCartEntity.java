package de.mennomax.astikorcarts.entity;

import java.util.ArrayList;

import com.google.common.collect.ImmutableList;

import de.mennomax.astikorcarts.config.AstikorCartsConfig;
import de.mennomax.astikorcarts.init.Items;
import de.mennomax.astikorcarts.inventory.container.PlowCartContainer;
import de.mennomax.astikorcarts.util.CartItemStackHandler;
import de.mennomax.astikorcarts.util.PlowBlockHandler;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityType;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.ServerPlayerEntity;
import net.minecraft.inventory.container.SimpleNamedContainerProvider;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.CompoundNBT;
import net.minecraft.network.datasync.DataParameter;
import net.minecraft.network.datasync.DataSerializers;
import net.minecraft.network.datasync.EntityDataManager;
import net.minecraft.util.Hand;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.fml.network.NetworkHooks;
import net.minecraftforge.items.ItemStackHandler;

public class PlowCartEntity extends AbstractDrawnInventoryEntity {

    private static final double BLADEOFFSET = 1.7D;
    private static final DataParameter<Boolean> PLOWING = EntityDataManager.<Boolean>createKey(PlowCartEntity.class, DataSerializers.BOOLEAN);
    private static final ImmutableList<DataParameter<ItemStack>> TOOLS = ImmutableList.of(
            EntityDataManager.createKey(PlowCartEntity.class, DataSerializers.ITEMSTACK),
            EntityDataManager.createKey(PlowCartEntity.class, DataSerializers.ITEMSTACK),
            EntityDataManager.createKey(PlowCartEntity.class, DataSerializers.ITEMSTACK));
    private final PlowBlockHandler[] plowRunners = new PlowBlockHandler[3];

    public PlowCartEntity(EntityType<? extends Entity> entityTypeIn, World worldIn) {
        super(entityTypeIn, worldIn);
        this.spacing = 2.0D;
        for (int i = 0; i < TOOLS.size(); i++) {
            this.updateRunnerForSlot(i, this.inventory.getStackInSlot(i));
        }
    }

    @Override
    protected ArrayList<String> getAllowedEntityList() {
        return AstikorCartsConfig.COMMON.PLOWCART.get();
    }

    @Override
    protected ItemStackHandler initInventory() {
        return new CartItemStackHandler<PlowCartEntity>(3, this) {
            @Override
            protected void onLoad() {
                for (int i = 0; i < TOOLS.size(); i++) {
                    CART.getDataManager().set(TOOLS.get(i), this.getStackInSlot(i));
                    CART.updateRunnerForSlot(i, this.getStackInSlot(i));
                }
            }

            @Override
            protected void onContentsChanged(int slot) {
                CART.updateSlot(slot);
            }
        };
    }

    public boolean getPlowing() {
        return this.dataManager.get(PLOWING);
    }

    @Override
    public void pulledTick() {
        super.pulledTick();
        if (!this.world.isRemote) {
            PlayerEntity player = null;
            if (this.getPulling() instanceof PlayerEntity) {
                player = (PlayerEntity) this.getPulling();
            } else if (this.getPulling().getControllingPassenger() instanceof PlayerEntity) {
                player = (PlayerEntity) this.getPulling().getControllingPassenger();
            }
            if (this.dataManager.get(PLOWING) && player != null) {
                if (this.prevPosX != this.posX || this.prevPosZ != this.posZ) {
                    for (int i = 0; i < this.inventory.getSlots(); i++) {
                        float offset = 38.0F - i * 38.0F;
                        double blockPosX = this.posX + MathHelper.sin((this.rotationYaw - offset) * 0.017453292F) * BLADEOFFSET;
                        double blockPosZ = this.posZ - MathHelper.cos((this.rotationYaw - offset) * 0.017453292F) * BLADEOFFSET;
                        BlockPos blockPos = new BlockPos(blockPosX, this.posY - 0.5D, blockPosZ);
                        plowRunners[i].tillBlock(player, blockPos);
                    }
                }
            }
        }
    }

    @Override
    public boolean processInitialInteract(PlayerEntity player, Hand hand) {
        if (!this.world.isRemote) {
            if (player.isSneaking()) {
                this.openContainer(player);
            } else {
                this.dataManager.set(PLOWING, !this.dataManager.get(PLOWING));
            }
        }
        return true;
    }

    public void updateRunnerForSlot(int slot, ItemStack stack) {
        plowRunners[slot] = new PlowBlockHandler(stack, slot, this);
    }

    public void updateSlot(int slot) {
        if (!this.world.isRemote) {
            this.updateRunnerForSlot(slot, this.inventory.getStackInSlot(slot));
            if (this.inventory.getStackInSlot(slot).isEmpty()) {
                this.dataManager.set(TOOLS.get(slot), ItemStack.EMPTY);
            } else {
                this.dataManager.set(TOOLS.get(slot), this.inventory.getStackInSlot(slot));
            }

        }
    }

    public ItemStack getStackInSlot(int i) {
        return this.dataManager.get(TOOLS.get(i));
    }

    @Override
    public Item getCartItem() {
        return Items.PLOWCART;
    }

    @Override
    protected void registerData() {
        super.registerData();
        this.dataManager.register(PLOWING, false);
        for (DataParameter<ItemStack> param : TOOLS) {
            this.dataManager.register(param, ItemStack.EMPTY);
        }
    }

    @Override
    protected void readAdditional(CompoundNBT compound) {
        super.readAdditional(compound);
        this.dataManager.set(PLOWING, compound.getBoolean("Plowing"));
    }

    @Override
    protected void writeAdditional(CompoundNBT compound) {
        super.writeAdditional(compound);
        compound.putBoolean("Plowing", this.dataManager.get(PLOWING));

    }

    public void openContainer(PlayerEntity player) {
        NetworkHooks.openGui((ServerPlayerEntity) player, new SimpleNamedContainerProvider((id, inv, plyr) -> {
            return new PlowCartContainer(id, inv, this);
        }, this.getDisplayName()), (writer) -> {
            writer.writeInt(this.getEntityId());
        });
    }

}
