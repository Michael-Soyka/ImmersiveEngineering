/*
 * BluSunrize
 * Copyright (c) 2020
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 *
 */

package blusunrize.immersiveengineering.api.crafting.builders;

import blusunrize.immersiveengineering.api.crafting.ArcFurnaceRecipe;
import blusunrize.immersiveengineering.api.crafting.IngredientWithSize;
import com.google.gson.JsonArray;
import net.minecraft.tags.Tag;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.ItemLike;

public class ArcFurnaceRecipeBuilder extends IEFinishedRecipe<ArcFurnaceRecipeBuilder>
{
	private final JsonArray secondaryArray = new JsonArray();

	private ArcFurnaceRecipeBuilder()
	{
		super(ArcFurnaceRecipe.SERIALIZER.get());
		setMultipleResults(6);
		setUseInputArray(4, "additives");
		addWriter(jsonObject -> {
			if(!secondaryArray.isEmpty())
				jsonObject.add("secondaries", secondaryArray);
		});
	}

	public static ArcFurnaceRecipeBuilder builder(Item result)
	{
		return new ArcFurnaceRecipeBuilder().addResult(result);
	}

	public static ArcFurnaceRecipeBuilder builder(ItemStack result)
	{
		return new ArcFurnaceRecipeBuilder().addResult(result);
	}

	public static ArcFurnaceRecipeBuilder builder(Tag<Item> result, int count)
	{
		return new ArcFurnaceRecipeBuilder().addResult(new IngredientWithSize(result, count));
	}

	public ArcFurnaceRecipeBuilder addSlag(ItemLike itemProvider)
	{
		return addItem("slag", new ItemStack(itemProvider));
	}

	public ArcFurnaceRecipeBuilder addSlag(ItemStack itemStack)
	{
		return addItem("slag", itemStack);
	}

	public ArcFurnaceRecipeBuilder addSlag(Tag<Item> tag, int count)
	{
		return addIngredient("slag", new IngredientWithSize(tag, count));
	}

	public ArcFurnaceRecipeBuilder addSecondary(Tag<Item> tag, float chance)
	{
		secondaryArray.add(serializeStackWithChance(new IngredientWithSize(tag), chance));
		return this;
	}
}
