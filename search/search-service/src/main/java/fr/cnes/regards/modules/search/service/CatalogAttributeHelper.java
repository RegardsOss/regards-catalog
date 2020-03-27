/*
 * Copyright 2017-2020 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
 *
 * This file is part of REGARDS.
 *
 * REGARDS is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * REGARDS is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with REGARDS. If not, see <http://www.gnu.org/licenses/>.
 */
package fr.cnes.regards.modules.search.service;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.hateoas.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;

import com.google.common.collect.Sets;

import fr.cnes.regards.framework.feign.security.FeignSecurityManager;
import fr.cnes.regards.framework.hateoas.HateoasUtils;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.dam.client.models.IAttributeModelClient;
import fr.cnes.regards.modules.dam.client.models.IModelAttrAssocClient;
import fr.cnes.regards.modules.dam.domain.models.attributes.AttributeModel;
import fr.cnes.regards.modules.dam.gson.entities.IAttributeHelper;

/**
*
* Helper class to retrieve model attributes
* @author Marc Sordi
*
*/
@Service
public class CatalogAttributeHelper implements IAttributeHelper {

    /**
     * Runtime tenant resolver
     */
    private final IRuntimeTenantResolver runtimeTenantResolver;

    /**
     * Feign client for rs-dam {@link AttributeModel} controller. Autowired by Spring.
     */
    private final IAttributeModelClient attributeModelClient;

    private final IModelAttrAssocClient attruteModelAssocClient;

    public CatalogAttributeHelper(IRuntimeTenantResolver runtimeTenantResolver,
            IAttributeModelClient attributeModelClient, IModelAttrAssocClient attruteModelAssocClient) {
        this.runtimeTenantResolver = runtimeTenantResolver;
        this.attributeModelClient = attributeModelClient;
        this.attruteModelAssocClient = attruteModelAssocClient;
    }

    @Override
    public List<AttributeModel> getAllAttributes(String pTenant) {
        try {
            runtimeTenantResolver.forceTenant(pTenant);
            FeignSecurityManager.asSystem();

            ResponseEntity<List<Resource<AttributeModel>>> resources = attributeModelClient.getAttributes(null, null);
            if (resources != null) {
                return HateoasUtils.unwrapList(resources.getBody());
            }
        } finally {
            runtimeTenantResolver.clearTenant();
            FeignSecurityManager.reset();
        }
        return Collections.emptyList();
    }

    @Override
    public Set<AttributeModel> getAllCommonAttributes(Collection<String> modelNames) throws ModuleException {
        Set<AttributeModel> commonAttributes = Sets.newHashSet();
        boolean first = true;
        for (String modelName : modelNames) {
            try {
                Set<AttributeModel> modelAttributes = attruteModelAssocClient.getModelAttrAssocs(modelName).getBody()
                        .stream().map(f -> f.getContent().getAttribute()).collect(Collectors.toSet());
                if (first) {
                    commonAttributes.addAll(modelAttributes);
                } else {
                    commonAttributes = commonAttributes.stream().filter(f -> !modelAttributes.contains(f))
                            .collect(Collectors.toSet());
                }
                first = false;
            } catch (HttpClientErrorException | HttpServerErrorException e) {
                throw new ModuleException("Error retrieving attribute models from dam microservice.", e);
            }
        }
        return commonAttributes;
    }
}
