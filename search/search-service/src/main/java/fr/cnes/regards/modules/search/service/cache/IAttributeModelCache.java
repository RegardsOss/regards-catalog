/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.search.service.cache;

import java.util.List;

import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;

import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.modules.models.domain.attributes.AttributeModel;

/**
 * Provider for {@link AttributeModel}s with caching facilities.
 *
 * @author Xavier-Alexandre Brochard
 */
public interface IAttributeModelCache {

    /**
     * The call will first check the cache "attributeModels" before actually invoking the method and then caching the
     * result.
     * @param pTenant the tenant
     * @return the list of attribute models
     */
    @Cacheable(value = "attributemodels")
    List<AttributeModel> getAttributeModels(String pTenant);

    /**
     * The call will first check the cache "attributeModels" before actually invoking the method and then caching the
     * result.
     * @param pTenant the tenant
     * @return the list of attribute models
     */
    @CachePut(value = "attributemodels")
    List<AttributeModel> getAttributeModelsThenCache(String pTenant);

    /*
     * (non-Javadoc)
     *
     * @see fr.cnes.regards.modules.search.service.cache.IAttributeModelCache#findByName(java.lang.String)
     */
    AttributeModel findByName(String pName) throws EntityNotFoundException;

}