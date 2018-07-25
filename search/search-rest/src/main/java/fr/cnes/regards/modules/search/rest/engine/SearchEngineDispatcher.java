/*
 * Copyright 2017 CNES - CENTRE NATIONAL d'ETUDES SPATIALES
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
package fr.cnes.regards.modules.search.rest.engine;

import java.util.HashMap;
import java.util.StringJoiner;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.validation.Errors;
import org.springframework.validation.MapBindingResult;
import org.springframework.validation.Validator;

import fr.cnes.regards.framework.module.rest.exception.EntityInvalidException;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.framework.modules.plugins.service.IPluginService;
import fr.cnes.regards.modules.search.domain.plugin.ISearchEngine;
import fr.cnes.regards.modules.search.domain.plugin.SearchContext;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import fr.cnes.regards.modules.search.service.ISearchEngineConfigurationService;

/**
 * Search engine service dispatcher.<br/>
 *
 * Each methods acts as a proxy for search engine.<br/>
 * <ul>
 * <li>First, context debugging may be display</li>
 * <li>The system look for a plugin instance</li>
 * <li>The system dispatches search to plugin instance</li>
 * </ul>
 *
 * @author Marc Sordi
 *
 */
@Service
public class SearchEngineDispatcher implements ISearchEngineDispatcher {

    private static final Logger LOGGER = LoggerFactory.getLogger(SearchEngineDispatcher.class);

    @Autowired
    private Validator validator;

    @Autowired
    private IPluginService pluginService;

    @Autowired
    private ISearchEngineConfigurationService searchEngineService;

    @SuppressWarnings("unchecked")
    @Override
    public <T> ResponseEntity<T> dispatchRequest(SearchContext context) throws ModuleException {

        // Validate search context
        Errors errors = new MapBindingResult(new HashMap<>(), "searchContext");
        validator.validate(context, errors);
        if (errors.hasErrors()) {
            StringJoiner joiner = new StringJoiner(". ");
            errors.getAllErrors().forEach(error -> {
                joiner.add(error.toString());
            });
            throw new EntityInvalidException(joiner.toString());
        }

        // Debugging
        if (LOGGER.isDebugEnabled()) {
            LOGGER.debug("Handling request for engine : {}", context.getEngineType());
            LOGGER.debug("Search type : {}", context.getSearchType());
            if (context.getDatasetUrn().isPresent()) {
                LOGGER.debug("Searching data objects on dataset : {}", context.getDatasetUrn().get().toString());
            }
            if (context.getExtra().isPresent()) {
                LOGGER.debug("Handling request extra path : {}", context.getExtra().get());
            }
            if (context.getUrn().isPresent()) {
                LOGGER.debug("Getting entity with URN : {}", context.getUrn().get().toString());
            }
            if (context.getPropertyName().isPresent()) {
                LOGGER.debug("Search values for property : {}", context.getPropertyName().get());
            }
            if (context.getMaxCount().isPresent()) {
                LOGGER.debug("Maximum result count for property values : {}", context.getMaxCount().get());
            }
            if (context.getDateTypes().isPresent()) {
                context.getDateTypes().get().forEach(dataType -> LOGGER.debug("Summary data type : {}", dataType));
            }
            context.getHeaders().forEach((key, values) -> LOGGER.debug("Header : {} -> {}", key, values.toString()));
            if (context.getQueryParams() != null) {
                context.getQueryParams()
                        .forEach((key, values) -> LOGGER.debug("Query param : {} -> {}", key, values.toString()));
            }
            LOGGER.debug(context.getPageable() == null ? "No pagination" : context.getPageable().toString());
        }

        // Retrieve search engine plugin from search context
        ISearchEngine<?, ?, ?, ?> engine = getSearchEngine(context);
        if (context.getExtra().isPresent()) {
            return (ResponseEntity<T>) engine.extra(context);
        } else if (context.getUrn().isPresent()) {
            return (ResponseEntity<T>) engine.getEntity(context);
        } else if (context.getPropertyName().isPresent()) {
            return (ResponseEntity<T>) engine.getPropertyValues(context);
        } else if (context.getDateTypes().isPresent()) {
            return (ResponseEntity<T>) engine.getSummary(context);
        } else {
            return (ResponseEntity<T>) engine.search(context);
        }
    }

    private ISearchEngine<?, ?, ?, ?> getSearchEngine(SearchContext context) throws ModuleException {
        SearchEngineConfiguration conf = searchEngineService.retrieveConf(context.getDatasetUrn(),
                                                                          context.getEngineType());
        return pluginService.getPlugin(conf.getConfiguration().getId());
    }
}