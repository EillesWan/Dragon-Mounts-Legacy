package com.github.kay9.dragonmounts.dragon;

import com.github.kay9.dragonmounts.DMLRegistry;
import com.github.kay9.dragonmounts.dragon.breed.BreedRegistry;
import com.github.kay9.dragonmounts.dragon.breed.DragonBreed;
import net.minecraft.client.Minecraft;
import net.minecraft.core.NonNullList;
import net.minecraft.core.RegistryAccess;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.TranslatableComponent;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.common.ForgeSpawnEggItem;
import net.minecraftforge.common.capabilities.ICapabilityProvider;
import net.minecraftforge.fml.loading.FMLLoader;
import net.minecraftforge.server.ServerLifecycleHooks;
import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.Random;

@SuppressWarnings("DataFlowIssue")
public class DragonSpawnEgg extends ForgeSpawnEggItem
{
    private static final String DATA_TAG = "ItemData";
    private static final String DATA_ITEM_NAME = "ItemName";
    private static final String DATA_PRIM_COLOR = "PrimaryColor";
    private static final String DATA_SEC_COLOR = "SecondaryColor";

    public DragonSpawnEgg()
    {
        super(DMLRegistry.DRAGON, 0, 0, new Item.Properties().tab(CreativeModeTab.TAB_MISC));
    }

    @Override
    public void fillItemCategory(CreativeModeTab pCategory, NonNullList<ItemStack> pItems)
    {
        if (FMLLoader.getDist().isClient() && allowdedIn(pCategory) && Minecraft.getInstance().level != null)
        {
            var reg = Minecraft.getInstance().level.registryAccess();
            for (DragonBreed breed : BreedRegistry.registry(reg))
                pItems.add(create(breed, reg));
        }
    }

    public static ItemStack create(DragonBreed breed, RegistryAccess reg)
    {
        var id = breed.id(reg);
        var root = new CompoundTag();

        // entity tag
        var entityTag = new CompoundTag();
        entityTag.putString(TameableDragon.NBT_BREED, id.toString());
        root.put(EntityType.ENTITY_TAG, entityTag);

        // name & colors
        // storing these in the stack nbt is more performant than getting the breed everytime
        var itemDataTag = new CompoundTag();
        itemDataTag.putString(DATA_ITEM_NAME, String.join(".", DMLRegistry.SPAWN_EGG.get().getDescriptionId(), id.getNamespace(), id.getPath()));
        itemDataTag.putInt(DATA_PRIM_COLOR, breed.primaryColor());
        itemDataTag.putInt(DATA_SEC_COLOR, breed.secondaryColor());
        root.put(DATA_TAG, itemDataTag);

        ItemStack stack = new ItemStack(DMLRegistry.SPAWN_EGG.get());
        stack.setTag(root);
        return stack;
    }

    @Override
    public @Nullable ICapabilityProvider initCapabilities(ItemStack stack, @Nullable CompoundTag nbt)
    {
        preconditionSpawnEgg(stack); // extremely hacky to be doing it here... but there doesn't seem to be any other options.
        return super.initCapabilities(stack, nbt);
    }

    @SuppressWarnings("DataFlowIssue")
    @Override
    public Component getName(ItemStack stack)
    {
        var tag = stack.getTagElement(DATA_TAG);
        if (tag != null && tag.contains(DATA_ITEM_NAME))
            return new TranslatableComponent(tag.getString(DATA_ITEM_NAME));

        return super.getName(stack);
    }

    @Override
    public Optional<Mob> spawnOffspringFromSpawnEgg(Player pPlayer, Mob pMob, EntityType<? extends Mob> pEntityType, ServerLevel pServerLevel, Vec3 pPos, ItemStack pStack)
    {
        var entityTag = pStack.getTagElement(EntityType.ENTITY_TAG);
        if (entityTag != null)
        {
            var breedID = entityTag.getString(TameableDragon.NBT_BREED);
            if (!breedID.isEmpty())
            {
                if (((TameableDragon) pMob).getBreed() != BreedRegistry.get(breedID, pServerLevel.registryAccess()))
                    return Optional.empty();
            }
        }

        return super.spawnOffspringFromSpawnEgg(pPlayer, pMob, pEntityType, pServerLevel, pPos, pStack);
    }

    public static int getColor(ItemStack stack, int tintIndex)
    {
        var tag = stack.getTagElement(DATA_TAG);
        if (tag != null)
            return tintIndex == 0? tag.getInt(DATA_PRIM_COLOR) : tag.getInt(DATA_SEC_COLOR);
        return 0xffffff;
    }

    @SuppressWarnings("ConstantConditions")
    private static void preconditionSpawnEgg(ItemStack stack)
    {
        if (ServerLifecycleHooks.getCurrentServer() == null) return;

        var root = stack.getOrCreateTag();
        var blockEntityData = stack.getOrCreateTagElement(EntityType.ENTITY_TAG);
        var breedId = blockEntityData.getString(TameableDragon.NBT_BREED);
        var regAcc = ServerLifecycleHooks.getCurrentServer().registryAccess();
        var reg = BreedRegistry.registry(regAcc);

        if (breedId.isEmpty() || !reg.containsKey(new ResourceLocation(breedId))) // this item doesn't contain a breed yet?
        {
            // assign one ourselves then.
            var breed = reg.getRandom(new Random()).orElseThrow();
            var updated = create(breed.value(), regAcc);
            root.merge(updated.getTag());
        }
    }
}
