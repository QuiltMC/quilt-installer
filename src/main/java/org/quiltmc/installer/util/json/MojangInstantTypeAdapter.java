package org.quiltmc.installer.util.json;

import com.google.gson.TypeAdapter;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import org.quiltmc.installer.util.Util;

import java.io.IOException;
import java.time.Instant;

public class MojangInstantTypeAdapter extends TypeAdapter<Instant> {
    @Override
    public void write(JsonWriter out, Instant value) throws IOException {
        out.value(Util.MOJANG_TIME_FORMAT.format(value));
    }

    @Override
    public Instant read(JsonReader in) throws IOException {
        return Util.MOJANG_TIME_FORMAT.parse(in.nextString(), Instant::from);
    }
}
