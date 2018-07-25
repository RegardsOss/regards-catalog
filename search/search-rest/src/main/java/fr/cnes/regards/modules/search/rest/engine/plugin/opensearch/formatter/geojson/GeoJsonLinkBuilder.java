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
package fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.formatter.geojson;

import org.springframework.hateoas.Link;

import fr.cnes.regards.framework.geojson.GeoJsonLink;

/**
 * Builder to convert a {@link Link} to a {@link GeoJsonLink}
 * @author Sébastien Binda
 */
public class GeoJsonLinkBuilder {

    /**
     * Convert a {@link Link} to a {@link GeoJsonLink}
     * @param springLink {@link Link}
     * @return {@link GeoJsonLink}
     */
    public static GeoJsonLink build(Link springLink) {
        GeoJsonLink link = new GeoJsonLink();
        link.setHref(springLink.getHref());
        link.setRel(springLink.getRel());
        return link;
    }

    /**
     * Convert a {@link Link} to a {@link GeoJsonLink}
     * @param springLink {@link Link}
     * @param type MediaType to add into the geojson link
     * @return {@link GeoJsonLink}
     */
    public static GeoJsonLink build(Link springLink, String type) {
        GeoJsonLink link = new GeoJsonLink();
        link.setHref(springLink.getHref());
        link.setRel(springLink.getRel());
        link.setType(type);
        return link;
    }

    /**
     * Convert a {@link Link} to a {@link GeoJsonLink}
     * @param springLink {@link Link}
     * @param rel use this rel instead of the one given in the spring {@link Link}
     * @param title title to add into the geojson link
     * @param type MediaType to add into the geojson link
     * @return {@link GeoJsonLink}
     */
    public static GeoJsonLink build(Link springLink, String rel, String title, String type) {
        GeoJsonLink link = GeoJsonLinkBuilder.build(springLink, type);
        link.setRel(rel);
        link.setTitle(title);
        return link;
    }

}