/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.search.rest.representation;

import java.util.ArrayList;
import java.util.List;

import org.hamcrest.Matchers;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.mockito.Mockito;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpMethod;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultMatcher;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.result.MockMvcResultMatchers;
import org.springframework.web.bind.annotation.RequestMethod;

import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.security.utils.HttpConstants;
import fr.cnes.regards.framework.test.integration.AbstractRegardsITWithoutMockedCots;
import fr.cnes.regards.framework.test.integration.RequestParamBuilder;
import fr.cnes.regards.framework.test.report.annotation.Purpose;
import fr.cnes.regards.framework.test.report.annotation.Requirement;
import fr.cnes.regards.modules.dataaccess.client.IUserClient;
import fr.cnes.regards.modules.entities.domain.Collection;
import fr.cnes.regards.modules.entities.domain.DataObject;
import fr.cnes.regards.modules.entities.domain.Dataset;
import fr.cnes.regards.modules.entities.domain.Document;
import fr.cnes.regards.modules.indexer.dao.IEsRepository;
import fr.cnes.regards.modules.models.client.IAttributeModelClient;
import fr.cnes.regards.modules.search.rest.CatalogController;
import fr.cnes.regards.modules.search.rest.CatalogControllerTestUtils;

/**
 * Integration test for {@link CatalogController}
 *
 * @author Xavier-Alexandre Brochard
 */
@TestPropertySource(locations = { "classpath:dao.properties", "classpath:test-representation.properties" })
public class CatalogControllerGeoJsonIT extends AbstractRegardsITWithoutMockedCots {

    /**
     * Class logger
     */
    private static final Logger LOG = LoggerFactory.getLogger(CatalogControllerGeoJsonIT.class);

    /**
     * The mock attribute model client
     */
    @Autowired
    private IAttributeModelClient attributeModelClient;

    /**
     * The mock user client providing access groups
     */
    @Autowired
    private IUserClient userClient;

    /**
     * ElasticSearch repository
     */
    @Autowired
    private IEsRepository esRepository;

    /**
     * Get current tenant at runtime and allows tenant forcing
     */
    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    /**
     * A dummy collection
     */
    public static final Collection COLLECTION = new Collection(null, DEFAULT_TENANT, "mycollection");

    /**
     * A dummy dataobject
     */
    public static final DataObject DATAOBJECT = new DataObject(null, DEFAULT_TENANT, "mydataobject");

    /**
     * A dummy dataset
     */
    public static final Dataset DATASET = new Dataset(null, DEFAULT_TENANT, "mydataset");

    /**
     * An other dummy dataset
     */
    public static final Dataset DATASET_1 = new Dataset(null, DEFAULT_TENANT, "mydataset");

    /**
     * A dummy document
     */
    public static final Document DOCUMENT = new Document(null, DEFAULT_TENANT, "mydocument");

    // @BeforeTransaction
    // protected void beforeTransaction() {
    // injectToken();
    // }
    //
    // private void injectToken() {
    // jwtService.injectMockToken(DEFAULT_TENANT, DEFAULT_ROLE);
    // }

    /**
     * @throws java.lang.Exception
     */
    @Before
    public void setUp() throws Exception {
        // Mock clients
        Mockito.when(attributeModelClient.getAttributes(Mockito.any(), Mockito.any()))
                .thenReturn(CatalogControllerTestUtils.ATTRIBUTE_MODEL_CLIENT_RESPONSE);

        Mockito.when(userClient.retrieveAccessGroupsOfUser(Mockito.anyString(), Mockito.anyInt(), Mockito.anyInt()))
                .thenReturn(CatalogControllerTestUtils.USER_CLIENT_RESPONSE);

        // Set groups
        COLLECTION.setGroups(CatalogControllerTestUtils.ACCESS_GROUP_NAMES_AS_SET);
        DATAOBJECT.setGroups(CatalogControllerTestUtils.ACCESS_GROUP_NAMES_AS_SET);
        DATASET.setGroups(CatalogControllerTestUtils.ACCESS_GROUP_NAMES_AS_SET);
        DATASET_1.setGroups(CatalogControllerTestUtils.ACCESS_GROUP_NAMES_AS_SET);
        DOCUMENT.setGroups(CatalogControllerTestUtils.ACCESS_GROUP_NAMES_AS_SET);

        // Populate the ElasticSearch repository
        runtimeTenantResolver.forceTenant(DEFAULT_TENANT);
        if (esRepository.indexExists(DEFAULT_TENANT)) {
            esRepository.deleteIndex(DEFAULT_TENANT);
        }
        esRepository.createIndex(DEFAULT_TENANT);
        esRepository.save(DEFAULT_TENANT, COLLECTION);
        esRepository.save(DEFAULT_TENANT, DATASET);
        esRepository.save(DEFAULT_TENANT, DATASET_1);
        esRepository.save(DEFAULT_TENANT, DATAOBJECT);
        esRepository.save(DEFAULT_TENANT, DOCUMENT);
        esRepository.refresh(DEFAULT_TENANT);
        Thread.sleep(10000);
    }

    @After
    public void cleanUp() {
        esRepository.deleteIndex(DEFAULT_TENANT);
    }

    /**
     * Le système doit permettre de réaliser une recherche par critères sur l’ensemble du catalogue.
     */
    @Test
    @Purpose("Le système doit permettre de réaliser une recherche par critères sur l’ensemble du catalogue.")
    @Requirement("REGARDS_DSL_DAM_CAT_510")
    public final void testSearchAll() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        RequestParamBuilder builder = RequestParamBuilder.build().param("q", CatalogControllerTestUtils.Q);
        performDefaultGet("/search", expectations, "Error searching all entities", builder);
    }

    /**
     * Le système doit permettre de fournir des facettes pour toute recherche dans le catalogue.
     */
    @Test
    @Purpose("Le système doit permettre de fournir des facettes pour toute recherche dans le catalogue.")
    @Requirement("REGARDS_DSL_DAM_CAT_610")
    public final void testSearchAll_withFacets() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets",
                                                        Matchers.hasItem(Matchers.hasEntry("attributeName", "label"))));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets",
                                                        Matchers.hasItem(Matchers.hasEntry("type", "STRING"))));
        RequestParamBuilder builder = RequestParamBuilder.build().param("q", CatalogControllerTestUtils.Q)
                .param("facets", CatalogControllerTestUtils.FACETS_AS_ARRAY);
        performDefaultGet("/searchwithfacets", expectations, "Error searching all entities", builder);
    }

    /**
     * Le système doit permettre de manière synchrone d’accéder aux informations d’une collection via son IP_ID.
     */
    @Test
    @Purpose("Le système doit permettre de manière synchrone d’accéder aux informations d’une collection via son IP_ID.")
    @Requirement("REGARDS_DSL_DAM_COL_310")
    public final void testGetCollection() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations
                .add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.label", Matchers.is("mycollection")));
        performDefaultGet("/collections/{urn}", expectations, "Error retrieving a collection", COLLECTION.getIpId());
    }

    /**
     * Le système doit permettre de manière synchrone de rechercher des collections à partir de critères basés sur des
     * éléments du modèle de données.
     */
    @Test
    @Purpose("Le système doit permettre de manière synchrone de rechercher des collections à partir de critères basés sur des éléments du modèle de données.")
    @Requirement("REGARDS_DSL_DAM_COL_410")
    public final void testSearchCollections_shouldFindOneWithoutFacets() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].content.label",
                                                        Matchers.is("mycollection")));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets").doesNotExist());
        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_ONE_COLLECTION);
        performDefaultGet("/collections/search", expectations, "Error searching collections", builder);
    }

    /**
     * Cette fonction permet de récupérer un jeu de données
     */
    @Test
    public final void testGetDataset() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.label", Matchers.is("mydataset")));
        performDefaultGet("/datasets/{urn}", expectations, "Error retrieving a dataset", DATASET.getIpId());
    }

    /**
     * Test method for
     * {@link fr.cnes.regards.modules.search.rest.CatalogController#searchDatasets(java.lang.String, org.springframework.data.domain.Pageable, org.springframework.data.web.PagedResourcesAssembler)}.
     */
    @Test
    public final void testSearchDatasets_shouldFindTwoWithoutFacets() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].content.label",
                                                        Matchers.is("mydataset")));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[1].content.label",
                                                        Matchers.is("mydataset")));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets").doesNotExist());
        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_TWO_DATASETS);
        performDefaultGet("/datasets/search", expectations, "Error searching datasets", builder);
    }

    /**
     * Le système doit permettre de consulter les métadonnées d’un objet de données du catalogue.
     */
    @Test
    @Purpose("Le système doit permettre de consulter les métadonnées d’un objet de données du catalogue.")
    @Requirement("REGARDS_DSL_DAM_CAT_470")
    public final void testGetDataobject() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations
                .add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.label", Matchers.is("mydataobject")));
        performDefaultGet("/dataobjects/{urn}", expectations, "Error retrieving a dataobject", DATAOBJECT.getIpId());
    }

    /**
     * Test method for
     * {@link fr.cnes.regards.modules.search.rest.CatalogController#searchDataobjects(java.lang.String, java.util.List, org.springframework.data.domain.Pageable, org.springframework.data.web.PagedResourcesAssembler)}.
     */
    @Test
    public final void testSearchDataobjects_shouldFindOneWithFacets() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].content.label",
                                                        Matchers.is("mydataobject")));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets",
                                                        Matchers.hasItem(Matchers.hasEntry("attributeName", "label"))));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".facets",
                                                        Matchers.hasItem(Matchers.hasEntry("type", "STRING"))));
        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_ONE_DATAOBJECT)
                .param("facets", CatalogControllerTestUtils.FACETS_AS_ARRAY);
        performDefaultGet("/dataobjects/search", expectations, "Error searching dataobjects", builder);
    }

    /**
     * Test method for
     * {@link fr.cnes.regards.modules.search.rest.CatalogController#searchDataobjectsReturnDatasets(java.lang.String, java.util.List, org.springframework.data.domain.Pageable, org.springframework.data.web.PagedResourcesAssembler)}.
     */
    @Test
    public final void testSearchDataobjectsReturnDatasets() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        RequestParamBuilder builder = RequestParamBuilder.build().param("q", CatalogControllerTestUtils.Q);
        performDefaultGet("/dataobjects/datasets/search", expectations, "Error searching datasets via dataobjects",
                          builder);
    }

    /**
     * Le système doit permettre de manière synchrone d’accéder aux informations d’un document via son IP_ID.
     */
    @Test
    @Purpose("Le système doit permettre de manière synchrone d’accéder aux informations d’un document via son IP_ID.")
    @Requirement("REGARDS_DSL_DAM_DOC_310")
    public final void testGetDocument() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.label", Matchers.is("mydocument")));
        performDefaultGet("/documents/{urn}", expectations, "Error retrieving a document", DOCUMENT.getIpId());
    }

    /**
     * Le système doit permettre de manière synchrone de rechercher des documents à partir de critères basés sur des
     * éléments du modèle de données.
     */
    @Test
    @Purpose("Le système doit permettre de manière synchrone de rechercher des documents à partir de critères basés sur des éléments du modèle de données.")
    @Requirement("REGARDS_DSL_DAM_DOC_510")
    public final void testSearchDocuments() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].content.label",
                                                        Matchers.is("mydocument")));
        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_ONE_DOCUMENT);
        performDefaultGet("/documents/search", expectations, "Error searching documents", builder);
    }

    /**
     * Check that the system can return a sorted page of results.
     */
    @Test
    @Purpose("Check that the system can return a sorted page of results.")
    @Requirement("REGARDS_DSL_DAM_DOC_510")
    public final void testSearch_withSort() {
        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].content.label",
                                                        Matchers.is("mydataset")));

        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_TWO_DATASETS)
                .param("sort", CatalogControllerTestUtils.SORT);
        performDefaultGet("/datasets/search", expectations, "Error searching documents", builder);
    }

    /**
     * Check that the system adds a self hateoas link on datasets pointing to the dataset/{urn} endpoint.
     */
    @Test
    @Purpose("Check that the system adds a self hateoas link on datasets pointing to the dataset/{urn} endpoint.")
    @Requirement("REGARDS_DSL_SYS_ARC_020")
    public final void testSearchDatasets_shouldHaveSelfLink() {
        // Prepare authorization
        setAuthorities("/datasets/{urn}", RequestMethod.GET, DEFAULT_ROLE);

        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].links.[0].rel",
                                                        Matchers.is("self")));
        expectations
                .add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].links.[0].href",
                                                    Matchers.startsWith("http://localhost/datasets/URN:AIP:DATASET:")));
        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_TWO_DATASETS);
        performDefaultGet("/datasets/search", expectations, "Error searching datasets", builder);
    }

    /**
     * Check that the system adds a hateoas link on datasets pointing their dataobjects via a search with the pre-filled
     * query.
     */
    @Test
    @Purpose("Check that the system adds a hateoas link on datasets pointing their dataobjects via a search with the pre-filled query.")
    @Requirement("REGARDS_DSL_SYS_ARC_020")
    public final void testSearchDatasets_shouldHaveLinkNavigatingToDataobjects() {
        // Prepare authorization
        setAuthorities("/dataobjects/search", RequestMethod.GET, DEFAULT_ROLE);

        final List<ResultMatcher> expectations = new ArrayList<>();
        expectations.add(MockMvcResultMatchers.status().isOk());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT).isNotEmpty());
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features", Matchers.notNullValue()));
        expectations.add(MockMvcResultMatchers.jsonPath(JSON_PATH_ROOT + ".content.features.[0].links.[0].rel",
                                                        Matchers.either(Matchers.is("next")).or(Matchers.is("self"))));
        expectations.add(MockMvcResultMatchers
                .jsonPath(JSON_PATH_ROOT + ".content.features.[0].links.[0].href",
                          Matchers.either(Matchers.startsWith("http://localhost/dataobjects/search?q"))
                                  .or(Matchers.startsWith("http://localhost/datasets/"))));
        RequestParamBuilder builder = RequestParamBuilder.build()
                .param("q", CatalogControllerTestUtils.Q_FINDS_TWO_DATASETS);
        performDefaultGet("/datasets/search", expectations, "Error searching datasets", builder);
    }

    /*
     * (non-Javadoc)
     * @see fr.cnes.regards.framework.test.integration.AbstractRegardsIT#getLogger()
     */
    @Override
    protected Logger getLogger() {
        return LOG;
    }

    @Override
    protected MockHttpServletRequestBuilder getRequestBuilder(final String pAuthToken, final HttpMethod pHttpMethod,
            final String pUrlTemplate, final Object... pUrlVars) {

        final MockHttpServletRequestBuilder requestBuilder = MockMvcRequestBuilders.request(pHttpMethod, pUrlTemplate,
                                                                                            pUrlVars);
        addSecurityHeader(requestBuilder, pAuthToken);

        requestBuilder.header(HttpConstants.CONTENT_TYPE, "application/json");
        requestBuilder.header(HttpConstants.ACCEPT, "application/geo+json");

        return requestBuilder;
    }

}