/*
 * Copyright 2017-2020 original authors
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package io.micronaut.context;

import io.micronaut.core.annotation.NonNull;
import io.micronaut.context.env.Environment;
import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.core.annotation.UsedByGeneratedCode;
import io.micronaut.core.type.Argument;
import io.micronaut.core.type.ReturnType;
import io.micronaut.core.util.ArgumentUtils;
import io.micronaut.core.util.ObjectUtils;
import io.micronaut.inject.ExecutableMethod;
import io.micronaut.inject.annotation.AbstractEnvironmentAnnotationMetadata;

import io.micronaut.core.annotation.Nullable;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;

/**
 * <p>Abstract base class for generated {@link ExecutableMethod} classes to implement. The generated classes should
 * implement the {@link ExecutableMethod#invoke(Object, Object...)} method at compile time providing direct dispatch
 * of the target method</p>
 *
 * @author Graeme Rocher
 * @since 1.0
 */
@Internal
public abstract class AbstractExecutableMethod extends AbstractExecutable implements ExecutableMethod, EnvironmentConfigurable {

    private final ReturnType returnType;
    private final Argument<?> genericReturnType;
    private final int hashCode;
    private Environment environment;
    private AnnotationMetadata methodAnnotationMetadata;

    /**
     * @param declaringType     The declaring type
     * @param methodName        The method name
     * @param genericReturnType The generic return type
     * @param arguments         The arguments
     */
    @SuppressWarnings("WeakerAccess")
    protected AbstractExecutableMethod(Class<?> declaringType,
                                       String methodName,
                                       Argument genericReturnType,
                                       Argument... arguments) {
        super(declaringType, methodName, arguments);
        this.genericReturnType = genericReturnType;
        this.returnType = new ReturnTypeImpl();
        int result = ObjectUtils.hash(declaringType, methodName);
        result = 31 * result + Arrays.hashCode(argTypes);
        this.hashCode = result;
    }

    /**
     * @param declaringType     The declaring type
     * @param methodName        The method name
     * @param genericReturnType The generic return type
     */
    @SuppressWarnings("WeakerAccess")
    protected AbstractExecutableMethod(Class<?> declaringType,
                                       String methodName,
                                       Argument genericReturnType) {
        this(declaringType, methodName, genericReturnType, Argument.ZERO_ARGUMENTS);
    }

    /**
     * @param declaringType     The declaring type
     * @param methodName        The method name
     */
    @SuppressWarnings("WeakerAccess")
    @UsedByGeneratedCode
    protected AbstractExecutableMethod(Class<?> declaringType,
                                       String methodName) {
        this(declaringType, methodName, Argument.OBJECT_ARGUMENT, Argument.ZERO_ARGUMENTS);
    }

    @Override
    public boolean hasPropertyExpressions() {
        return getAnnotationMetadata().hasPropertyExpressions();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (this.methodAnnotationMetadata == null) {
            this.methodAnnotationMetadata = initializeAnnotationMetadata();
        }
        return this.methodAnnotationMetadata;

    }

    @Override
    public void configure(Environment environment) {
        this.environment = environment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        AbstractExecutableMethod that = (AbstractExecutableMethod) o;
        return Objects.equals(declaringType, that.declaringType) &&
            Objects.equals(methodName, that.methodName) &&
            Arrays.equals(argTypes, that.argTypes);
    }

    @Override
    public int hashCode() {
        return hashCode;
    }

    @Override
    public String toString() {
        String text = Argument.toString(getArguments());
        return getReturnType().getType().getSimpleName() + " " + getMethodName() + "(" + text + ")";
    }

    @Override
    public ReturnType getReturnType() {
        return returnType;
    }

    @Override
    public Class<?>[] getArgumentTypes() {
        return argTypes;
    }

    @Override
    public Class<?> getDeclaringType() {
        return declaringType;
    }

    @Override
    public String getMethodName() {
        return methodName;
    }

    @Override
    public final Object invoke(Object instance, Object... arguments) {
        ArgumentUtils.validateArguments(this, getArguments(), arguments);
        return invokeInternal(instance, arguments);
    }

    /**
     * @param instance  The instance
     * @param arguments The arguments
     * @return The result
     */
    @SuppressWarnings("WeakerAccess")
    @UsedByGeneratedCode
    protected abstract Object invokeInternal(Object instance, Object[] arguments);

    /**
     * Resolves the annotation metadata for this method. Subclasses
     *
     * @return The {@link AnnotationMetadata}
     */
    protected AnnotationMetadata resolveAnnotationMetadata() {
        return AnnotationMetadata.EMPTY_METADATA;
    }

    private AnnotationMetadata initializeAnnotationMetadata() {
        AnnotationMetadata annotationMetadata = resolveAnnotationMetadata();
        if (annotationMetadata != AnnotationMetadata.EMPTY_METADATA) {
            if (annotationMetadata.hasPropertyExpressions()) {
                // we make a copy of the result of annotation metadata which is normally a reference
                // to the class metadata
                return new MethodAnnotationMetadata(annotationMetadata);
            } else {
                return annotationMetadata;
            }
        } else {
            return AnnotationMetadata.EMPTY_METADATA;
        }
    }

    /**
     * A {@link ReturnType} implementation.
     */
    class ReturnTypeImpl implements ReturnType {

        @Override
        public Class<?> getType() {
            if (genericReturnType != null) {
                return genericReturnType.getType();
            } else {
                return void.class;
            }
        }

        @Override
        public boolean isSuspended() {
            return AbstractExecutableMethod.this.isSuspend();
        }

        @NonNull
        @Override
        public AnnotationMetadata getAnnotationMetadata() {
            return AbstractExecutableMethod.this.getAnnotationMetadata();
        }

        @Override
        public Argument[] getTypeParameters() {
            if (genericReturnType != null) {
                return genericReturnType.getTypeParameters();
            }
            return Argument.ZERO_ARGUMENTS;
        }

        @Override
        public Map<String, Argument<?>> getTypeVariables() {
            if (genericReturnType != null) {
                return genericReturnType.getTypeVariables();
            }
            return Collections.emptyMap();
        }

        @Override
        @NonNull
        public Argument asArgument() {
            Map<String, Argument<?>> typeVariables = getTypeVariables();
            Collection<Argument<?>> values = typeVariables.values();
            final AnnotationMetadata annotationMetadata = getAnnotationMetadata();
            return Argument.of(getType(), annotationMetadata, values.toArray(Argument.ZERO_ARGUMENTS));
        }
    }



    /**
     * Internal environment aware annotation metadata delegate.
     */
    private final class MethodAnnotationMetadata extends AbstractEnvironmentAnnotationMetadata {
        MethodAnnotationMetadata(AnnotationMetadata targetMetadata) {
            super(targetMetadata);
        }

        @Nullable
        @Override
        protected Environment getEnvironment() {
            return environment;
        }
    }
}
