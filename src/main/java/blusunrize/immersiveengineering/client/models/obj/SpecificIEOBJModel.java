/*
 * BluSunrize
 * Copyright (c) 2021
 *
 * This code is licensed under "Blu's License of Common Sense"
 * Details can be found in the license file in the root folder of this project
 *
 */

package blusunrize.immersiveengineering.client.models.obj;

import blusunrize.immersiveengineering.api.IEProperties.IEObjState;
import blusunrize.immersiveengineering.api.shader.ShaderCase;
import blusunrize.immersiveengineering.api.shader.ShaderLayer;
import blusunrize.immersiveengineering.client.models.obj.GeneralIEOBJModel.GroupKey;
import blusunrize.immersiveengineering.client.models.obj.callback.IEOBJCallback;
import blusunrize.immersiveengineering.client.models.obj.callback.item.ItemCallback;
import blusunrize.immersiveengineering.client.models.split.PolygonUtils;
import blusunrize.immersiveengineering.client.models.split.PolygonUtils.ExtraQuadData;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.Lists;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Transformation;
import com.mojang.math.Vector4f;
import malte0811.modelsplitter.model.Group;
import malte0811.modelsplitter.model.MaterialLibrary.OBJMaterial;
import malte0811.modelsplitter.model.Polygon;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.block.model.BakedQuad;
import net.minecraft.client.renderer.block.model.ItemOverrides;
import net.minecraft.client.renderer.block.model.ItemTransforms.TransformType;
import net.minecraft.client.renderer.texture.TextureAtlasSprite;
import net.minecraft.client.resources.model.BakedModel;
import net.minecraft.core.Direction;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.block.state.BlockState;
import net.minecraftforge.client.model.IModelConfiguration;
import net.minecraftforge.client.model.ModelLoaderRegistry;
import net.minecraftforge.client.model.PerspectiveMapWrapper;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.util.*;
import java.util.Map.Entry;
import java.util.function.Consumer;

public class SpecificIEOBJModel<T> implements BakedModel
{
	private final GeneralIEOBJModel<T> baseModel;
	@Nonnull
	private final IEOBJCallback<T> callback;
	private final T key;
	@Nullable
	private final ShaderCase shader;
	private final IEObjState state;
	@Nullable
	private final RenderType layer;
	private List<BakedQuad> quads;

	public SpecificIEOBJModel(
			GeneralIEOBJModel<T> baseModel, T key, @Nullable ShaderCase shader, @Nullable RenderType layer
	)
	{
		this.baseModel = baseModel;
		this.callback = baseModel.getCallback();
		this.key = key;
		this.shader = shader;
		this.state = callback.getIEOBJState(key);
		this.layer = layer;
	}

	@Nonnull
	@Override
	public List<BakedQuad> getQuads(@Nullable BlockState pState, @Nullable Direction pSide, @Nonnull Random pRand)
	{
		if(pSide!=null)
			return List.of();
		if(quads==null)
			quads = buildQuads();
		return quads;
	}

	@Override
	public boolean useAmbientOcclusion()
	{
		return baseModel.useAmbientOcclusion();
	}

	@Override
	public boolean isGui3d()
	{
		return baseModel.isGui3d();
	}

	@Override
	public boolean usesBlockLight()
	{
		return baseModel.usesBlockLight();
	}

	@Override
	public boolean isCustomRenderer()
	{
		GlobalTempData.setActiveModel(this);
		return baseModel.isCustomRenderer();
	}

	@Nonnull
	@Override
	public TextureAtlasSprite getParticleIcon()
	{
		return baseModel.getParticleIcon();
	}

	@Nonnull
	@Override
	public ItemOverrides getOverrides()
	{
		return baseModel.getOverrides();
	}

	@Override
	public boolean doesHandlePerspectives()
	{
		return true;
	}

	@Override
	public BakedModel handlePerspective(TransformType cameraTransformType, PoseStack transforms)
	{
		Transformation matrix = PerspectiveMapWrapper.getTransforms(baseModel.getOwner().getCombinedTransform())
				.getOrDefault(cameraTransformType, Transformation.identity());

		matrix.push(transforms);
		ItemCallback.castOrDefault(callback).handlePerspective(
				key, GlobalTempData.getActiveHolder(), cameraTransformType, transforms
		);
		return this;
	}

	private List<BakedQuad> buildQuads()
	{
		List<BakedQuad> quads = Lists.newArrayList();

		for(Entry<String, Group<OBJMaterial>> groupName : baseModel.getGroups().entrySet())
		{
			List<ShadedQuads> temp = addQuadsForGroup(groupName.getKey(), groupName.getValue(), true);
			quads.addAll(
					temp.stream()
							.map(ShadedQuads::quadsInLayer)
							.flatMap(List::stream)
							.filter(Objects::nonNull)
							.toList()
			);
		}

		quads = callback.modifyQuads(key, quads);
		return ImmutableList.copyOf(quads);
	}

	public List<ShadedQuads> addQuadsForGroup(String groupName, Group<OBJMaterial> group, boolean allowCaching)
	{
		GroupKey<T> cacheKey = new GroupKey<>(key, shader, layer, groupName);
		if(allowCaching)
		{
			List<ShadedQuads> cached = baseModel.getGroupCache().getIfPresent(cacheKey);
			if(cached!=null)
				return cached;
		}
		final int numPasses;
		if(shader!=null)
			numPasses = shader.getLayers().length;
		else
			numPasses = 1;
		List<ShadedQuads> ret = new ArrayList<>();
		Transformation optionalTransform = baseModel.getSprite().getRotation();
		optionalTransform = callback.applyTransformations(key, groupName, optionalTransform);

		final MaterialSpriteGetter<T> spriteGetter = new MaterialSpriteGetter<>(
				baseModel.getSpriteGetter(), groupName, callback, key, shader
		);
		final MaterialColorGetter<T> colorGetter = new MaterialColorGetter<>(groupName, callback, key, shader);
		final TextureCoordinateRemapper coordinateRemapper = new TextureCoordinateRemapper(shader);

		if(state.visibility().isVisible(groupName)&&callback.shouldRenderGroup(key, groupName, layer))
			for(int pass = 0; pass < numPasses; ++pass)
				if(shader==null||shader.shouldRenderGroupForPass(groupName, pass))
				{
					List<BakedQuad> quads = new ArrayList<>();
					spriteGetter.setRenderPass(pass);
					colorGetter.setRenderPass(pass);
					coordinateRemapper.setRenderPass(pass);
					addGroupQuads(
							group, baseModel.getOwner(), quads::add, spriteGetter, colorGetter,
							coordinateRemapper, state.transform().compose(optionalTransform.blockCenterToCorner())
					);
					ShaderLayer layer = shader!=null?shader.getLayers()[pass]: new ShaderLayer(new ResourceLocation("missing/no"), -1)
					{
						@Override
						public RenderType getRenderType(RenderType baseType)
						{
							return baseType;
						}
					};
					ret.add(new ShadedQuads(layer, quads));
				}
		if(allowCaching)
			baseModel.getGroupCache().put(cacheKey, ret);
		return ret;
	}

	/**
	 * Yep, this is 90% a copy of ModelObject.addQuads. We need custom hooks in there, so we copy the rest around it.
	 */
	private void addGroupQuads(Group<OBJMaterial> group, IModelConfiguration owner, Consumer<BakedQuad> out,
							   MaterialSpriteGetter<?> spriteGetter, MaterialColorGetter<?> colorGetter,
							   TextureCoordinateRemapper coordinateRemapper,
							   Transformation transform)
	{
		for(var face : group.getFaces())
		{
			OBJMaterial mat = face.getTexture();
			if(mat==null)
				continue;
			TextureAtlasSprite texture = spriteGetter.apply(
					mat.name(), ModelLoaderRegistry.resolveTexture(mat.map_Kd(), owner)
			);
			Vector4f colorTint = colorGetter.apply(mat.name(), new Vector4f(1, 1, 1, 1));

			Polygon<OBJMaterial> remappedFace = coordinateRemapper.remapCoord(face);
			if(remappedFace!=null)
				out.accept(PolygonUtils.toBakedQuad(
						remappedFace.getPoints(), new ExtraQuadData(texture, colorTint), transform, false
				));
		}
	}

	public Map<String, Group<OBJMaterial>> getGroups()
	{
		return baseModel.getGroups();
	}

	@Nonnull
	public IEOBJCallback<T> getCallback()
	{
		return callback;
	}

	public T getKey()
	{
		return key;
	}

	public record ShadedQuads(ShaderLayer layer, List<BakedQuad> quadsInLayer)
	{
	}
}
