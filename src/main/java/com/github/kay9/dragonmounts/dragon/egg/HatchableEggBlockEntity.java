package com.github.kay9.dragonmounts.dragon.egg;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.github.kay9.dragonmounts.dragon.breed.DragonBreed;
import com.github.kay9.dragonmounts.habitats.Habitat;
import com.google.common.base.Suppliers;
import net.minecraft.core.BlockPos;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.network.protocol.game.ClientboundBlockEntityDataPacket;
import net.minecraft.util.RandomSource;
import net.minecraft.world.Nameable;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.block.Block;
import net.minecraft.world.level.block.entity.BlockEntity;
import net.minecraft.world.level.block.state.BlockState;
import org.jetbrains.annotations.Nullable;

import java.util.function.Supplier;

@SuppressWarnings("ConstantConditions")
public class HatchableEggBlockEntity extends BlockEntity implements Nameable
{
    public static final int MIN_HABITAT_POINTS = 2;
    public static final int BREED_TRANSITION_TIME = 200;

    private final TransitionHandler transitioner = new TransitionHandler();

    private Supplier<DragonBreed> breed = () -> null;
    private Component customName;

    public HatchableEggBlockEntity(BlockPos pPos, BlockState pBlockState)
    {
        super(DMLRegistry.EGG_BLOCK_ENTITY.get(), pPos, pBlockState);
    }

    @Override
    protected void saveAdditional(CompoundTag tag)
    {
        super.saveAdditional(tag);

        if (getBreed() != null)
            tag.putString(HatchableEggBlock.NBT_BREED, getBreed().id(getLevel().registryAccess()).toString());

        if (getCustomName() != null)
            tag.putString(HatchableEggBlock.NBT_NAME, Component.Serializer.toJson(customName));

        if (getTransition().isRunning())
        {
            var transitionTag = new CompoundTag();
            getTransition().save(transitionTag);
            tag.put(TransitionHandler.NBT_TRANSITIONER, transitionTag);
        }
    }

    @Override
    public void load(CompoundTag pTag)
    {
        super.load(pTag);

        setBreed(Suppliers.memoize(() -> BreedRegistry.get(pTag.getString(HatchableEggBlock.NBT_BREED), getLevel().registryAccess())));

        var name = pTag.getString(HatchableEggBlock.NBT_NAME);
        if (!name.isBlank()) setCustomName(Component.Serializer.fromJson(name));

        var transitioner = pTag.getCompound(TransitionHandler.NBT_TRANSITIONER);
        if (!transitioner.isEmpty()) getTransition().load(transitioner);

        if (getLevel() != null && getLevel().isClientSide()) // level is nullable
            getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
    }

    @Override
    public CompoundTag getUpdateTag()
    {
        var tag = super.getUpdateTag();
        saveAdditional(tag);
        return tag;
    }

    @Nullable
    @Override
    public Packet<ClientGamePacketListener> getUpdatePacket()
    {
        return ClientboundBlockEntityDataPacket.create(this);
    }

    public DragonBreed getBreed()
    {
        var current = breed.get();
        if (current == null) // init lazily
        {
            var newBreed = BreedRegistry.getRandom(getLevel().registryAccess(), getLevel().getRandom());
            setBreed(() -> newBreed);
            return newBreed;
        }

        return current;
    }

    public void setBreed(Supplier<DragonBreed> breed)
    {
        this.breed = breed;
    }

    @Override
    public Component getCustomName()
    {
        return customName;
    }

    @Override
    public Component getName()
    {
        return customName != null? customName :
                Component.translatable(DMLRegistry.EGG_BLOCK_ITEM.get().getDescriptionId(),
                        Component.translatable(DragonBreed.getTranslationKey(getBreed().id(getLevel().registryAccess()).toString())));
    }

    public void setCustomName(Component name)
    {
        this.customName = name;
    }

    public TransitionHandler getTransition()
    {
        return transitioner;
    }

    public void tick(Level pLevel, BlockPos pPos, BlockState pState)
    {
        getTransition().tick(getLevel().getRandom());
    }

    public void updateHabitat()
    {
        DragonBreed winner = null;
        int prevPoints = 0;
        for (var breed : BreedRegistry.registry(getLevel().registryAccess()))
        {
            int points = 0;
            for (Habitat habitat : breed.habitats()) points += habitat.getHabitatPoints(level, getBlockPos());
            if (points > MIN_HABITAT_POINTS && points > prevPoints)
            {
                winner = breed;
                prevPoints = points;
            }
        }
        if (winner != null && winner != getBreed())
        {
            getTransition().begin(winner);
            getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
        }
    }

    public boolean isModelReady()
    {
        return getLevel() != null && getBreed() != null;
    }

    public class TransitionHandler
    {
        private static final String NBT_TRANSITIONER = "TransitionerTag";
        private static final String NBT_TRANSITION_BREED = "TransitionBreed";
        private static final String NBT_TRANSITION_TIME = "TransitionTime";

        public Supplier<DragonBreed> transitioningBreed;
        public int transitionTime;

        public void tick(RandomSource random)
        {
            if (isRunning())
            {
                if (--transitionTime == 0)
                {
                    setBreed(transitioningBreed);
                    getLevel().sendBlockUpdated(getBlockPos(), getBlockState(), getBlockState(), Block.UPDATE_IMMEDIATE);
                }

                if (getLevel().isClientSide)
                {
                    for (var i = 0; i < (BREED_TRANSITION_TIME - transitionTime) * 0.25; i++)
                    {
                        var pos = getBlockPos();
                        var px = pos.getX() + random.nextDouble();
                        var py = pos.getY() + random.nextDouble();
                        var pz = pos.getZ() + random.nextDouble();
                        var particle = HatchableEggBlock.dustParticleFor(transitioningBreed.get(), random);

                        getLevel().addParticle(particle, px, py, pz, 0, 0, 0);
                    }
                }
            }
        }

        public void startFrom(Supplier<DragonBreed> transitioningBreed, int transitionTime)
        {
            this.transitioningBreed = transitioningBreed;
            this.transitionTime = transitionTime;
        }

        public void begin(DragonBreed transitioningBreed)
        {
            startFrom(() -> transitioningBreed, BREED_TRANSITION_TIME);
        }

        public boolean isRunning()
        {
            return transitionTime > 0;
        }

        public void save(CompoundTag tag)
        {
            tag.putString(NBT_TRANSITION_BREED, transitioningBreed.get().id(getLevel().registryAccess()).toString());
            tag.putInt(NBT_TRANSITION_TIME,  transitionTime);
        }

        public void load(CompoundTag tag)
        {
            startFrom(Suppliers.memoize(() -> BreedRegistry.get(tag.getString(NBT_TRANSITION_BREED), getLevel().registryAccess())), tag.getInt(NBT_TRANSITION_TIME));
        }
    }
}
