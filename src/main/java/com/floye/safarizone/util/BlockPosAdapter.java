package com.floye.safarizone.util;

import com.google.gson.*;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.Type;

public class BlockPosAdapter implements JsonSerializer<BlockPos>, JsonDeserializer<BlockPos> {
    @Override
    public JsonElement serialize(BlockPos src, Type typeOfSrc, JsonSerializationContext context) {
        JsonObject json = new JsonObject();
        json.addProperty("x", src.getX());
        json.addProperty("y", src.getY());
        json.addProperty("z", src.getZ());
        return json;
    }

    @Override
    public BlockPos deserialize(JsonElement json, Type typeOfT, JsonDeserializationContext context) throws JsonParseException {
        JsonObject obj = json.getAsJsonObject();
        int x = obj.get("x").getAsInt();
        int y = obj.get("y").getAsInt();
        int z = obj.get("z").getAsInt();
        return new BlockPos(x, y, z);
    }
}