/*
 * Copyright 2017-2018 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.formatter;

import java.util.List;

import org.springframework.hateoas.Link;

import fr.cnes.regards.modules.entities.domain.AbstractEntity;
import fr.cnes.regards.modules.entities.domain.feature.EntityFeature;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.search.domain.plugin.SearchContext;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.Configuration;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.EngineConfiguration;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.ParameterConfiguration;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.extension.IOpenSearchExtension;

/**
 * Interface to define OpenSearch response builders. Builders are made for a specific ouput format as ATOM or JSON.
 * @author Sébastien Binda
 *
 * @param <R> Search Output result format.
 */
public interface IResponseBuilder<R> {

    /**
     * Add metadatas to the global search results collection.
     * @param searchId {@link String}
     * @param engineConf {@link EngineConfiguration}
     * @param openSearchDescriptionUrl {@link String}
     * @param context {@link SearchContext}
     * @param page {@link FacetPage} results of the search
     * @param links {@link Link}s of the entities collection
     */
    void addMetadata(String searchId, EngineConfiguration engineConf, String openSearchDescriptionUrl,
            SearchContext context, Configuration configuration, FacetPage<EntityFeature> page, List<Link> links);

    /**
     * Add a new response entity to the builder. An entity is a {@link AbstractEntity} from an catalog search response.
     * @param entity {@link AbstractEntity}
     * @param paramConfigurations {@link ParameterConfiguration}s
     * @parma entityLinks {@link Link}s of the entity
     */
    void addEntity(EntityFeature entity, List<ParameterConfiguration> paramConfigurations, List<Link> entityLinks);

    /**
     * Clear all added {@link AbstractEntity}s to the current builder.
     */
    void clear();

    /**
     * Build the oupout search results collection.
     * @return {@link R}Search Output result formatted
     */
    R build();

    /**
     * Add a new {@link IOpenSearchExtension} to the current builder.
     * @param extension {@link IOpenSearchExtension}
     */
    void addExtension(IOpenSearchExtension extension);

}
