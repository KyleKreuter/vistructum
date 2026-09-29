package de.kylekreuter.vistructum.ui.web.view;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.TypeAdapterFactory;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonToken;
import com.google.gson.stream.JsonWriter;

import java.io.IOException;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.util.Optional;

public final class JsonViews {

    private JsonViews() {
    }

    public static Gson gson() {
        return new GsonBuilder().serializeNulls().disableHtmlEscaping()
                .registerTypeAdapterFactory(new OmittedWhenEmpty()).create();
    }

    private static final class OmittedWhenEmpty implements TypeAdapterFactory {

        @Override
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            if (type.getRawType() != Optional.class) {
                return null;
            }
            Type content = type.getType() instanceof ParameterizedType parameterized
                    ? parameterized.getActualTypeArguments()[0] : Object.class;
            return (TypeAdapter<T>) new OptionalAdapter<>(gson.getAdapter(TypeToken.get(content)));
        }
    }

    private static final class OptionalAdapter<V> extends TypeAdapter<Optional<V>> {

        private final TypeAdapter<V> content;

        private OptionalAdapter(TypeAdapter<V> content) {
            this.content = content;
        }

        @Override
        public void write(JsonWriter out, Optional<V> value) throws IOException {
            if (value != null && value.isPresent()) {
                content.write(out, value.get());
                return;
            }
            boolean serializeNulls = out.getSerializeNulls();
            out.setSerializeNulls(false);
            out.nullValue();
            out.setSerializeNulls(serializeNulls);
        }

        @Override
        public Optional<V> read(JsonReader in) throws IOException {
            if (in.peek() == JsonToken.NULL) {
                in.nextNull();
                return Optional.empty();
            }
            return Optional.ofNullable(content.read(in));
        }
    }
}
