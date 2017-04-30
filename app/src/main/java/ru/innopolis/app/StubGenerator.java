package ru.innopolis.app;

import com.android.dx.gen.Code;
import com.android.dx.gen.DexGenerator;
import com.android.dx.gen.Local;
import com.android.dx.gen.MethodId;
import com.android.dx.gen.Type;
import com.android.dx.io.DexBuffer;

import java.io.IOException;
import java.lang.reflect.Modifier;

public class StubGenerator {
    public static final String PAC_NAME = "ru/innopolis/";
    public static final String CLASS_NAME = "Stub";
    private DexGenerator generator;
    private Type<?> stub;

    public StubGenerator(String file) {
        generator = new DexGenerator();
        stub = Type.get("L" + PAC_NAME + CLASS_NAME + ";");
        generator.declare(stub, file, Modifier.PUBLIC, Type.OBJECT);
    }

    public DexBuffer save() throws IOException {
        return new DexBuffer(generator.generate());
    }

    public <T> void generateMethod(String name, Type<T> ret, Type...params) {
        MethodId methodId = stub.getMethod(ret, name, params);
        Code code = generator.declare(methodId, Modifier.STATIC | Modifier.PUBLIC);
        if (ret.equals(Type.VOID)) {
            code.returnVoid();
        } else {
            MethodId<T, Void> ctr = ret.getConstructor();
            Local<T> r = code.newLocal(ret);
            code.newInstance(r, ctr);
            code.returnValue(r);
        }
    }
}
