package org.pragmatica.jbct.slice.model;

import org.pragmatica.lang.Option;
import org.pragmatica.lang.Result;
import org.pragmatica.lang.utils.Causes;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.AnnotationMirror;
import javax.lang.model.element.AnnotationValue;
import javax.lang.model.element.Element;
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.RecordComponentElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

public record MethodModel(String name,
                          TypeMirror returnType,
                          TypeMirror responseType,
                          TypeMirror parameterType,
                          String parameterName,
                          boolean deprecated,
                          AspectModel aspects) {
    private static final Pattern METHOD_NAME_PATTERN = Pattern.compile("^[a-z][a-zA-Z0-9]+$");
    private static final String ASPECT_ANNOTATION = "org.pragmatica.aether.infra.aspect.Aspect";
    private static final String KEY_ANNOTATION = "org.pragmatica.aether.infra.aspect.Key";

    public static Result<MethodModel> methodModel(ExecutableElement method, ProcessingEnvironment env) {
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

        return extractAspects(method, param, env)
                   .map(aspects -> new MethodModel(name,
                                                   returnType,
                                                   responseType,
                                                   param.asType(),
                                                   param.getSimpleName()
                                                        .toString(),
                                                   deprecated,
                                                   aspects));
    }

    private static Result<AspectModel> extractAspects(ExecutableElement method,
                                                       VariableElement param,
                                                       ProcessingEnvironment env) {
        return findAnnotationMirror(method, ASPECT_ANNOTATION)
                   .fold(() -> Result.success(AspectModel.none()),
                         mirror -> {
                             var kinds = extractAspectKinds(mirror);
                             var hasCache = kinds.contains("CACHE");

                             if (!hasCache) {
                                 return Result.success(new AspectModel(kinds, Option.none()));
                             }

                             return extractKeyInfo(param.asType(), env)
                                        .map(keyInfo -> new AspectModel(kinds, keyInfo));
                         });
    }

    private static Option<AnnotationMirror> findAnnotationMirror(Element element, String annotationName) {
        for (var mirror : element.getAnnotationMirrors()) {
            var annotationType = mirror.getAnnotationType()
                                       .asElement();
            if (annotationType instanceof TypeElement te) {
                if (te.getQualifiedName()
                      .toString()
                      .equals(annotationName)) {
                    return Option.some(mirror);
                }
            }
        }
        return Option.none();
    }

    @SuppressWarnings("unchecked")
    private static List<String> extractAspectKinds(AnnotationMirror aspectMirror) {
        var kinds = new ArrayList<String>();
        for (var entry : aspectMirror.getElementValues()
                                     .entrySet()) {
            if (entry.getKey()
                     .getSimpleName()
                     .toString()
                     .equals("value")) {
                var value = entry.getValue()
                                 .getValue();
                if (value instanceof List<?> list) {
                    for (var item : list) {
                        if (item instanceof AnnotationValue av) {
                            var enumValue = av.getValue();
                            if (enumValue instanceof VariableElement ve) {
                                kinds.add(ve.getSimpleName()
                                            .toString());
                            }
                        }
                    }
                }
            }
        }
        return kinds;
    }

    private static Result<Option<KeyExtractorInfo>> extractKeyInfo(TypeMirror paramType,
                                                                    ProcessingEnvironment env) {
        if (!(paramType instanceof DeclaredType dt)) {
            return Result.success(Option.some(KeyExtractorInfo.identity(paramType.toString())));
        }

        var element = dt.asElement();
        if (element.getKind() != ElementKind.RECORD) {
            return Result.success(Option.some(KeyExtractorInfo.identity(paramType.toString())));
        }

        var typeElement = (TypeElement) element;
        var keyFields = new ArrayList<RecordComponentElement>();

        for (var enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof RecordComponentElement rce) {
                if (hasKeyAnnotation(rce)) {
                    keyFields.add(rce);
                }
            }
        }

        if (keyFields.isEmpty()) {
            return Result.success(Option.some(KeyExtractorInfo.identity(paramType.toString())));
        }

        if (keyFields.size() > 1) {
            return Causes.cause("Multiple @Key annotations found on " + typeElement.getSimpleName() +
                               ". Only one @Key field is allowed per record.")
                         .result();
        }

        var keyField = keyFields.getFirst();
        var keyType = keyField.asType()
                              .toString();
        var fieldName = keyField.getSimpleName()
                                .toString();
        var paramTypeName = typeElement.getQualifiedName()
                                       .toString();

        return Result.success(Option.some(KeyExtractorInfo.single(keyType, fieldName, paramTypeName)));
    }

    private static boolean hasKeyAnnotation(RecordComponentElement element) {
        return findAnnotationMirror(element, KEY_ANNOTATION).isPresent();
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
