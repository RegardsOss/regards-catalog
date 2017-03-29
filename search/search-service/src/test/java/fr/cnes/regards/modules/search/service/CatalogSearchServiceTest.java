/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.search.service;

import java.util.Map;

import org.apache.lucene.queryparser.flexible.core.QueryNodeException;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;

import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.framework.module.rest.exception.SearchException;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.modules.entities.domain.DataObject;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.facet.FacetType;
import fr.cnes.regards.modules.indexer.service.ISearchService;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.models.domain.EntityType;
import fr.cnes.regards.modules.search.service.accessright.IAccessRightFilter;
import fr.cnes.regards.modules.search.service.queryparser.RegardsQueryParser;

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
     * The custom OpenSearch query parser building {@link ICriterion} from tu string query
     */
    private RegardsQueryParser queryParser;

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
    public void setUp() {
        // Declare mocks
        queryParser = Mockito.mock(RegardsQueryParser.class);
        accessRightFilter = Mockito.mock(IAccessRightFilter.class);
        searchService = Mockito.mock(ISearchService.class);
        runtimeTenantResolver = Mockito.mock(IRuntimeTenantResolver.class);
        resourceService = Mockito.mock(IResourceService.class);

        // Globally mock what's mockable yet
        Mockito.when(accessRightFilter.removeGroupFilter(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        Mockito.when(accessRightFilter.addGroupFilter(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        Mockito.when(accessRightFilter.addAccessRightsFilter(Mockito.any()))
                .thenAnswer(invocation -> invocation.getArguments()[0]);
        Mockito.when(runtimeTenantResolver.getTenant()).thenReturn(CatalogSearchServiceTestUtils.TENANT);
        Mockito.when(resourceService.toResource(Mockito.any()))
                .thenAnswer(invocation -> new Resource<>(invocation.getArguments()[0]));

        // Instanciate the tested class
        catalogSearchService = new CatalogSearchService(searchService, queryParser);
    }

    /**
     * Test the main search method
     *
     * @throws SearchException
     * @throws QueryNodeException
     */
    @SuppressWarnings("unchecked")
    @Test
    public void doSearch_shouldPerformASimpleSearch() throws SearchException, QueryNodeException {
        // Prepare test
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(CatalogSearchServiceTestUtils.TENANT,
                                                                        EntityType.DATA);
        String q = CatalogSearchServiceTestUtils.Q;
        Map<String, FacetType> facets = CatalogSearchServiceTestUtils.FACETS;
        PagedResourcesAssembler<DataObject> assembler = CatalogSearchServiceTestUtils.ASSEMBLER_DATAOBJECT;
        Pageable pageable = CatalogSearchServiceTestUtils.PAGEABLE;

        // Define expected values
        ICriterion expectedCriterion = CatalogSearchServiceTestUtils.SIMPLE_STRING_MATCH_CRITERION;
        Page<DataObject> expectedSearchResult = CatalogSearchServiceTestUtils.PAGE_DATAOBJECT;

        // Mock dependencies
        Mockito.when(queryParser.parse(q)).thenReturn(expectedCriterion);
        Mockito.when(searchService.search(Mockito.any(SimpleSearchKey.class), Mockito.any(Pageable.class),
                                          Mockito.any(ICriterion.class), Mockito.any(), Mockito.any()))
                .thenReturn(expectedSearchResult);
        PagedResources<Resource<DataObject>> pageResources = CatalogSearchServiceTestUtils.PAGED_RESOURCES_DATAOBJECT;
        Mockito.when(assembler.toResource(Mockito.any())).thenReturn(pageResources);

        // Perform the test
        catalogSearchService.search(q, searchKey, facets, pageable);

        // Check
        Mockito.verify(searchService).search(searchKey, pageable, expectedCriterion, facets, null);
    }

}