/**
 *
 * Copyright (c) 2006-2018, Speedment, Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); You may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at:
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */
package com.speedment.common.codegen.model.trait;

import com.speedment.common.codegen.model.Javadoc;

import java.util.Optional;

/**
 * A trait for models that contain {@link Javadoc} components.
 * 
 * @author Emil Forslund
 * @param <T> The extending type
 * @since  2.0
 */
public interface HasJavadoc<T extends HasJavadoc<T>> {
    
    /**
     * Sets the {@link Javadoc} of this model.
     * 
     * @param doc  the javadoc
     * @return     a reference to this
     */
    T set(final Javadoc doc);
    
    /**
     * Returns the documentation of this model if such exists, else 
     * <code>empty</code>.
     * 
     * @return  the documentation or <code>empty</code>
     */
    Optional<Javadoc> getJavadoc();
}