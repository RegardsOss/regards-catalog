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
package fr.cnes.regards.modules.catalog.services.domain.plugins;

import javax.servlet.http.HttpServletResponse;

import org.springframework.core.io.InputStreamResource;
import org.springframework.http.ResponseEntity;

/**
 * Interface to define a Catalog service plugin. This plugins applies on a single entity provided by is identifier.
 *
 * @author Sébastien Binda
 *
 */
@FunctionalInterface
public interface ISingleEntityServicePlugin extends IService {

    /**
     * Apply the current service for the given entity identifier.
     * @param pEntityId entity identifier
     */
    ResponseEntity<InputStreamResource> applyOnEntity(String pEntityId, HttpServletResponse response);

}