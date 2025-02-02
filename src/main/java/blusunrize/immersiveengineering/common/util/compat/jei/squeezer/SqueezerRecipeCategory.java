/*
 * BluSunrize
 * Copyright (c) 2017
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 */

package blusunrize.immersiveengineering.common.util.compat.jei.squeezer;

import blusunrize.immersiveengineering.api.Lib;
import blusunrize.immersiveengineering.api.crafting.SqueezerRecipe;
import blusunrize.immersiveengineering.common.register.IEBlocks;
import blusunrize.immersiveengineering.common.util.compat.jei.IERecipeCategory;
import blusunrize.immersiveengineering.common.util.compat.jei.JEIHelper;
import mezz.jei.api.constants.VanillaTypes;
import mezz.jei.api.gui.builder.IRecipeLayoutBuilder;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.gui.drawable.IDrawableStatic;
import mezz.jei.api.helpers.IGuiHelper;
import mezz.jei.api.recipe.IFocus;
import mezz.jei.api.recipe.RecipeIngredientRole;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.fluids.FluidAttributes;

import java.util.Arrays;
import java.util.List;

public class SqueezerRecipeCategory extends IERecipeCategory<SqueezerRecipe>
{
	public static final ResourceLocation UID = new ResourceLocation(Lib.MODID, "squeezer");
	private final IDrawableStatic tankOverlay;

	public SqueezerRecipeCategory(IGuiHelper helper)
	{
		super(SqueezerRecipe.class, helper, UID, "block.immersiveengineering.squeezer");
		ResourceLocation background = new ResourceLocation(Lib.MODID, "textures/gui/squeezer.png");
		setBackground(helper.createDrawable(background, 6, 12, 126, 59));
		setIcon(new ItemStack(IEBlocks.Multiblocks.SQUEEZER));
		tankOverlay = helper.createDrawable(background, 179, 33, 16, 47);
	}

	@Override
	public void setRecipe(IRecipeLayoutBuilder builder, SqueezerRecipe recipe, List<? extends IFocus<?>> focuses)
	{
		builder.addSlot(RecipeIngredientRole.INPUT, 2, 23)
				.addItemStacks(Arrays.asList(recipe.input.getMatchingStacks()));
		IRecipeSlotBuilder outputBuilder = builder.addSlot(RecipeIngredientRole.OUTPUT, 85, 41);
		if(!recipe.itemOutput.isEmpty())
			outputBuilder.addItemStack(recipe.itemOutput);
		if(recipe.fluidOutput!=null&&!recipe.fluidOutput.isEmpty())
			builder.addSlot(RecipeIngredientRole.OUTPUT, 107, 10)
					.setFluidRenderer(FluidAttributes.BUCKET_VOLUME/2, false, 16, 47)
					.setOverlay(tankOverlay, 0, 0)
					.addIngredient(VanillaTypes.FLUID, recipe.fluidOutput)
					.addTooltipCallback(JEIHelper.fluidTooltipCallback);
	}
}