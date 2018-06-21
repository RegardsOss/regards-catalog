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
package fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.formatter.atom;

import java.sql.Date;
import java.time.LocalDate;
import java.util.List;

import org.apache.commons.compress.utils.Lists;
import org.springframework.http.MediaType;

import com.google.gson.Gson;
import com.rometools.modules.opensearch.OpenSearchModule;
import com.rometools.modules.opensearch.entity.OSQuery;
import com.rometools.modules.opensearch.impl.OpenSearchModuleImpl;
import com.rometools.rome.feed.atom.Content;
import com.rometools.rome.feed.atom.Entry;
import com.rometools.rome.feed.atom.Feed;
import com.rometools.rome.feed.atom.Link;
import com.rometools.rome.feed.module.Module;
import com.rometools.rome.feed.synd.SyndPerson;
import com.rometools.rome.feed.synd.SyndPersonImpl;

import fr.cnes.regards.modules.entities.domain.AbstractEntity;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.search.OpenSearchMediaType;
import fr.cnes.regards.modules.search.domain.plugin.SearchContext;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.Configuration;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.ParameterConfiguration;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.extension.IOpenSearchExtension;
import fr.cnes.regards.modules.search.rest.engine.plugin.opensearch.formatter.IResponseBuilder;

/**
 * Build open search responses in ATOM format through rome library handling :
 * <ul>
 * <li>Opensearch parameters extension</li>
 * <li>{@link IOpenSearchExtension}s for additional extensions like geo+time or media.</li>
 * </ul>
 *
 * @see <a href="https://rometools.github.io/rome/RssAndAtOMUtilitiEsROMEV0.5AndAboveTutorialsAndArticles/RssAndAtOMUtilitiEsROMEPluginsMechanism.html">rometools.github.io</a>
 * @author Sébastien Binda
 */
public class AtomResponseBuilder implements IResponseBuilder<Feed> {

    /**
     * ATOM format version
     */
    public static final String ATOM_VERSION = "atom_1.0";

    /**
     * List of {@link IOpenSearchExtension} to handle for ATOM format.
     */
    private final List<IOpenSearchExtension> extensions = Lists.newArrayList();

    private final Gson gson;

    private final Feed feed = new Feed(ATOM_VERSION);

    public AtomResponseBuilder(Gson gson) {
        this.gson = gson;
    }

    @Override
    public void addMetadata(String searchId, String searchTitle, String searchDescription,
            String openSearchDescriptionUrl, SearchContext context, Configuration configuration,
            FacetPage<AbstractEntity> page, List<org.springframework.hateoas.Link> links) {
        // Fee general informations
        feed.setId(searchId);
        feed.setTitle(searchTitle);

        // Feed description
        Content content = new Content();
        content.setType(Content.TEXT);
        content.setValue(searchDescription);
        feed.setSubtitle(content);

        // Create feed author.
        SyndPerson author = new SyndPersonImpl();
        author.setEmail(configuration.getContactEmail());
        author.setName(configuration.getAttribution());
        feed.getAuthors().add(author);

        // Add search date
        feed.setUpdated(Date.valueOf(LocalDate.now()));

        // Add search language
        feed.setLanguage(configuration.getLanguage());

        // Add the opensearch module, you would get information like totalResults from the
        // return results of your search
        List<Module> mods = feed.getModules();
        OpenSearchModule osm = new OpenSearchModuleImpl();
        osm.setItemsPerPage(page.getSize());
        osm.setStartIndex(page.getNumber());
        osm.setTotalResults(page.getNumberOfElements());

        // Add the query from opensearch module
        OSQuery query = new OSQuery();
        if ((context.getQueryParams() != null) && context.getQueryParams().containsKey("q")) {
            query.setSearchTerms(context.getQueryParams().get("q").get(0));
        }
        query.setStartPage(context.getPageable().getPageNumber());
        query.setRole(configuration.getUrlsRel());
        osm.addQuery(query);
        // Add opensearch description link
        Link osDescLink = new Link();
        osDescLink.setHref(openSearchDescriptionUrl);
        osDescLink.setType(OpenSearchMediaType.APPLICATION_OPENSEARCH_DESC_VALUE);
        osm.setLink(osDescLink);
        mods.add(osm);
        feed.setModules(mods);

        // Add simple link to opensearch description
        feed.getAlternateLinks().add(osDescLink);
    }

    @Override
    public void clear() {
        feed.getEntries().clear();
    }

    @Override
    public Feed build() {
        return this.feed;
    }

    @Override
    public void addExtension(IOpenSearchExtension configuration) {
        extensions.add(configuration);
    }

    @Override
    public void addEntity(AbstractEntity entity, List<ParameterConfiguration> paramConfigurations,
            List<org.springframework.hateoas.Link> entityLinks) {
        Entry entry = new Entry();
        entry.setId(entity.getIpId().toString());
        if (entity.getCreationDate() != null) {
            entry.setPublished(Date.valueOf(entity.getCreationDate().toLocalDate()));
        }
        if (entity.getLastUpdate() != null) {
            entry.setUpdated(Date.valueOf(entity.getLastUpdate().toLocalDate()));
        }
        entry.setTitle(entity.getLabel());
        List<Module> mods = entry.getModules();

        // Handle extensions
        for (IOpenSearchExtension extension : extensions) {
            if (extension.isActivated()) {
                extension.formatAtomResponseEntry(entity, paramConfigurations, entry, gson);
            }
        }
        entry.setModules(mods);

        entityLinks.forEach(link -> {
            Link feedEntityLink = new Link();
            feedEntityLink.setHref(link.getHref());
            feedEntityLink.setType(MediaType.APPLICATION_ATOM_XML_VALUE);
            if (link.getRel().equals(org.springframework.hateoas.Link.REL_SELF)) {
                feedEntityLink.setTitle(String.format("ATOM link for %s", entity.getIpId().toString()));
            }
            entry.getAlternateLinks().add(feedEntityLink);
        });

        feed.getEntries().add(entry);
    }

}
