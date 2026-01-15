package org.pragmatica.jbct.slice.model;

import org.pragmatica.lang.Result;
import org.pragmatica.lang.utils.Causes;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.regex.Pattern;

public record MethodModel(String name,
                          TypeMirror returnType,
                          TypeMirror responseType,
                          TypeMirror parameterType,
                          String parameterName,
                          boolean deprecated) {
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]+$");

    public static Result<MethodModel> methodModel(ExecutableElement method) {
        var name = method.getSimpleName()
                         .toString();

        if (!METHOD_NAME_PATTERN.matcher(name).matches()) {
            return Causes.cause("Invalid slice method name '" + name +
                               "': must start with lowercase letter and contain only alphanumeric characters")
                         .result();
        }

        var returnType = method.getReturnType();
        var responseType = extractPromiseTypeArg(returnType);
        var params = method.getParameters();
        if (params.size() != 1) {
            return Causes.cause("Slice methods must have exactly one parameter: " + name)
                         .result();
        }
        var param = params.getFirst();
        var deprecated = method.getAnnotation(Deprecated.class) != null;
        return Result.success(new MethodModel(name,
                                              returnType,
                                              responseType,
                                              param.asType(),
                                              param.getSimpleName()
                                                   .toString(),
                                              deprecated));
    }

    private static TypeMirror extractPromiseTypeArg(TypeMirror returnType) {
        if (returnType instanceof DeclaredType dt) {
            var typeArgs = dt.getTypeArguments();
            if (!typeArgs.isEmpty()) {
                return typeArgs.getFirst();
            }
        }
        return returnType;
    }
}
