/*
 * Copyright 2017-2023 original authors
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
package io.micronaut.inject.ast.annotation;

import io.micronaut.core.annotation.AnnotationMetadata;
import io.micronaut.core.annotation.Internal;
import io.micronaut.inject.annotation.AnnotationMetadataHierarchy;
import io.micronaut.inject.ast.ClassElement;
import io.micronaut.inject.ast.WildcardElement;

import java.util.ArrayList;
import java.util.List;

/**
 * The element annotation metadata for wildcard element.
 *
 * @author Denis Stepanov
 * @since 4.0.0
 */
@Internal
public final class WildcardElementAnnotationMetadata extends AbstractElementAnnotationMetadata {

    private final WildcardElement wildcardElement;
    private final ClassElement representingClassElement;
    private AnnotationMetadata annotationMetadata;

    public WildcardElementAnnotationMetadata(WildcardElement wildcardElement,
                                             ClassElement representingClassElement) {
        this.wildcardElement = wildcardElement;
        this.representingClassElement = representingClassElement;
    }

    public AnnotationMetadata getReturnInstance() {
        return getAnnotationMetadata();
    }

    @Override
    protected MutableAnnotationMetadataDelegate<?> getAnnotationMetadataToWrite() {
        return wildcardElement.getGenericTypeAnnotationMetadata();
    }

    @Override
    public AnnotationMetadata getAnnotationMetadata() {
        if (annotationMetadata == null) {
            List<AnnotationMetadata> allAnnotationMetadata = new ArrayList<>();
            wildcardElement.getLowerBounds().forEach(ce -> allAnnotationMetadata.add(ce.getTypeAnnotationMetadata()));
            wildcardElement.getUpperBounds().forEach(ce -> allAnnotationMetadata.add(ce.getTypeAnnotationMetadata()));
            allAnnotationMetadata.add(representingClassElement.getTypeAnnotationMetadata());
            allAnnotationMetadata.add(wildcardElement.getGenericTypeAnnotationMetadata());
            annotationMetadata = new AnnotationMetadataHierarchy(true, allAnnotationMetadata.toArray(AnnotationMetadata[]::new));
        }
        return annotationMetadata;
    }
}
