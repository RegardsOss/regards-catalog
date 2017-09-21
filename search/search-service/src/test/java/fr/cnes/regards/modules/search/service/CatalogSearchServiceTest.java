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
package fr.cnes.regards.modules.search.service;

import java.io.UnsupportedEncodingException;
import java.net.URLEncoder;
import java.util.HashMap;
import java.util.Map;

import fr.cnes.regards.framework.oais.urn.EntityType;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;

import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.framework.module.rest.exception.SearchException;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.modules.entities.domain.DataObject;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.facet.FacetType;
import fr.cnes.regards.modules.indexer.service.ISearchService;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.opensearch.service.OpenSearchService;
import fr.cnes.regards.modules.opensearch.service.exception.OpenSearchParseException;
import fr.cnes.regards.modules.search.service.accessright.AccessRightFilterException;
import fr.cnes.regards.modules.search.service.accessright.IAccessRightFilter;
import fr.cnes.regards.modules.search.service.utils.SampleDataUtils;

/**
 * Unit test for {@link CatalogSearchService}.
 *
 * @author Xavier-Alexandre Brochard
 */
public class CatalogSearchServiceTest {

    /**
     * Class under test
     */
    private CatalogSearchService catalogSearchService;

    /**
     * The OpenSearch service building {@link ICriterion} from a request string
     */
    private OpenSearchService openSearchService;

    /**
     * Facet converter
     */
    private IFacetConverter facetConverter;

    /**
     * Adds user group and data access filters
     */
    private IAccessRightFilter accessRightFilter;

    /**
     * Service perfoming the ElasticSearch search
     */
    private ISearchService searchService;

    /**
     * Get current tenant at runtime and allows tenant forcing
     */
    private IRuntimeTenantResolver runtimeTenantResolver;

    /**
     * The resource service
     */
    private IResourceService resourceService;

    @Before
    public void setUp() throws AccessRightFilterException {
        // Declare mocks
        openSearchService = Mockito.mock(OpenSearchService.class);
        accessRightFilter = Mockito.mock(IAccessRightFilter.class);
        searchService = Mockito.mock(ISearchService.class);
        runtimeTenantResolver = Mockito.mock(IRuntimeTenantResolver.class);
        resourceService = Mockito.mock(IResourceService.class);
        facetConverter = Mockito.mock(IFacetConverter.class);

        Mockito.when(facetConverter.convert(SampleDataUtils.QUERY_FACETS)).thenReturn(SampleDataUtils.FACETS);

        // Globally mock what's mockable yet
        Mockito.when(accessRightFilter.addAccessRights(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        Mockito.when(runtimeTenantResolver.getTenant()).thenReturn(SampleDataUtils.TENANT);
        Mockito.when(resourceService.toResource(Mockito.any()))
                .thenAnswer(invocation -> new Resource<>(invocation.getArguments()[0]));

        // Instanciate the tested class
        catalogSearchService = new CatalogSearchService(searchService, openSearchService, accessRightFilter,
                                                        facetConverter);
    }

    /**
     * Test the main search method
     *
     * @throws SearchException
     * @throws OpenSearchParseException
     * @throws UnsupportedEncodingException
     */
    @SuppressWarnings("unchecked")
    @Test
    @Requirement("REGARDS_DSL_DAM_ARC_810")
    public void doSearch_shouldPerformASimpleSearch()
            throws SearchException, OpenSearchParseException, UnsupportedEncodingException {
        // Prepare test
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(SampleDataUtils.TENANT, EntityType.DATA);
        Map<String, String> q = new HashMap<>();
        q.put("q=", URLEncoder.encode(SampleDataUtils.QUERY, "UTF-8"));
        PagedResourcesAssembler<DataObject> assembler = SampleDataUtils.ASSEMBLER_DATAOBJECT;
        Pageable pageable = SampleDataUtils.PAGEABLE;

        // Define expected values
        ICriterion expectedCriterion = SampleDataUtils.SIMPLE_STRING_MATCH_CRITERION;
        FacetPage<DataObject> expectedSearchResult = SampleDataUtils.FACET_PAGE_DATAOBJECT;

        // Mock dependencies
        Mockito.when(openSearchService.parse(q)).thenReturn(expectedCriterion);
        Mockito.when(searchService.search(Mockito.any(SimpleSearchKey.class), Mockito.any(Pageable.class),
                                          Mockito.any(ICriterion.class), Mockito.any()))
                .thenReturn(expectedSearchResult);
        PagedResources<Resource<DataObject>> pageResources = SampleDataUtils.PAGED_RESOURCES_DATAOBJECT;
        Mockito.when(assembler.toResource(Mockito.any())).thenReturn(pageResources);

        // Perform the test
        catalogSearchService.search(q, searchKey, SampleDataUtils.QUERY_FACETS, pageable);

        // Check
        Mockito.verify(searchService).search(searchKey, pageable, expectedCriterion, SampleDataUtils.FACETS);
    }

    /**
     * Le système doit permettre de désactiver la gestion des facettes pour des questions de performance.
     *
     * @throws SearchException
     * @throws OpenSearchParseException
     */
    @SuppressWarnings("unchecked")
    @Test
    @Purpose("Le système doit permettre de désactiver la gestion des facettes pour des questions de performance.")
    @Requirement("REGARDS_DSL_DAM_CAT_620")
    public void doSearch_withNoFacet() throws SearchException, OpenSearchParseException {
        // Prepare test
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(SampleDataUtils.TENANT, EntityType.DATA);
        Map<String, String> q = new HashMap<>();
        q.put("q=", "whatever");
        Map<String, FacetType> facets = new HashMap<>();
        PagedResourcesAssembler<DataObject> assembler = SampleDataUtils.ASSEMBLER_DATAOBJECT;
        Pageable pageable = SampleDataUtils.PAGEABLE;

        // Define expected values
        ICriterion expectedCriterion = SampleDataUtils.SIMPLE_STRING_MATCH_CRITERION;
        FacetPage<DataObject> expectedSearchResult = SampleDataUtils.FACET_PAGE_DATAOBJECT;

        // Mock dependencies
        Mockito.when(openSearchService.parse(q)).thenReturn(expectedCriterion);
        Mockito.when(searchService.search(Mockito.any(SimpleSearchKey.class), Mockito.any(Pageable.class),
                                          Mockito.any(ICriterion.class), Mockito.any()))
                .thenReturn(expectedSearchResult);
        PagedResources<Resource<DataObject>> pageResources = SampleDataUtils.PAGED_RESOURCES_DATAOBJECT;
        Mockito.when(assembler.toResource(Mockito.any())).thenReturn(pageResources);

        // Perform the test
        catalogSearchService.search(q, searchKey, null, pageable);

        // Check
        Mockito.verify(searchService).search(searchKey, pageable, expectedCriterion, facets);
    }

}
