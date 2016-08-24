/*
 * Copyright 2016 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.gradle.api.internal.resolve;

import com.google.common.collect.Lists;
import org.gradle.model.ModelMap;
import org.gradle.model.internal.registry.ModelRegistry;
import org.gradle.model.internal.type.ModelType;
import org.gradle.model.internal.type.ModelTypes;
import org.gradle.nativeplatform.PrebuiltLibraries;
import org.gradle.nativeplatform.PrebuiltLibrary;
import org.gradle.platform.base.VariantComponent;

import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class PrebuiltLibraryResolver implements LocalLibraryResolver {
    private static final ModelType<ModelMap<PrebuiltLibraries>> PREBUILT_LIBRARIES_TYPE = ModelTypes.modelMap(PrebuiltLibraries.class);

    @Override
    public Collection<VariantComponent> resolveCandidates(ModelRegistry projectModel, String libraryName) {
        List<VariantComponent> librarySpecs = Lists.newArrayList();
        collectLocalComponents(projectModel, libraryName, librarySpecs);
        if (librarySpecs.isEmpty()) {
            return Collections.emptyList();
        }
        return librarySpecs;
    }

    private void collectLocalComponents(ModelRegistry projectModel, String componentName, List<VariantComponent> librarySpecs) {
        ModelMap<PrebuiltLibraries> prebuiltLibraries = projectModel.find("repositories", PREBUILT_LIBRARIES_TYPE);
        if (prebuiltLibraries != null) {
            for (PrebuiltLibraries repository : prebuiltLibraries) {
                PrebuiltLibrary prebuiltLibrary = repository.resolveLibrary(componentName);
                if (prebuiltLibrary!=null) {
                    librarySpecs.add(prebuiltLibrary);
                }
            }
        }
    }
}
