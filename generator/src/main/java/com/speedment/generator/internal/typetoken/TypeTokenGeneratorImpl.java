package com.speedment.generator.internal.typetoken;

import com.speedment.common.codegen.model.Type;
import com.speedment.common.injector.annotation.Inject;
import com.speedment.generator.internal.util.InternalTypeTokenUtil;
import com.speedment.generator.typetoken.TypeTokenGenerator;
import com.speedment.runtime.component.TypeMapperComponent;
import com.speedment.runtime.config.Column;
import com.speedment.runtime.config.typetoken.TypeToken;

/**
 * 
 * @author  Emil Forslund
 * @author  Simon Jonasson
 * @since   3.0.0
 */
public final class TypeTokenGeneratorImpl implements TypeTokenGenerator {
    
    private @Inject TypeMapperComponent typeMappers;
    
    @Override
    public TypeToken tokenOf(Column col) {
        return typeMappers.get(col).getJavaType(col);
    }
    
    @Override
    public Type typeOf(Column col) {
        return InternalTypeTokenUtil.toType(tokenOf(col));
    }
    
    private TypeTokenGeneratorImpl() {}
    
}
