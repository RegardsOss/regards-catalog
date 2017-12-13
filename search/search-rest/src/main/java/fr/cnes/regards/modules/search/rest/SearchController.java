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
package fr.cnes.regards.modules.search.rest;

import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.util.Map;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.Assert;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Preconditions;
import feign.Response;
import fr.cnes.regards.framework.hateoas.IResourceService;
import fr.cnes.regards.framework.module.annotation.ModuleInfo;
import fr.cnes.regards.framework.module.rest.exception.EntityNotFoundException;
import fr.cnes.regards.framework.module.rest.exception.EntityOperationForbiddenException;
import fr.cnes.regards.framework.multitenant.IRuntimeTenantResolver;
import fr.cnes.regards.framework.oais.urn.EntityType;
import fr.cnes.regards.framework.oais.urn.UniformResourceName;
import fr.cnes.regards.framework.security.annotation.ResourceAccess;
import fr.cnes.regards.framework.security.role.DefaultRole;
import fr.cnes.regards.modules.entities.domain.AbstractEntity;
import fr.cnes.regards.modules.entities.domain.Collection;
import fr.cnes.regards.modules.entities.domain.DataObject;
import fr.cnes.regards.modules.entities.domain.Dataset;
import fr.cnes.regards.modules.entities.domain.Document;
import fr.cnes.regards.modules.indexer.dao.FacetPage;
import fr.cnes.regards.modules.indexer.domain.JoinEntitySearchKey;
import fr.cnes.regards.modules.indexer.domain.SimpleSearchKey;
import fr.cnes.regards.modules.indexer.domain.summary.DocFilesSummary;
import fr.cnes.regards.modules.indexer.service.ISearchService;
import fr.cnes.regards.modules.indexer.service.Searches;
import fr.cnes.regards.modules.opensearch.service.description.OpenSearchDescriptionBuilder;
import fr.cnes.regards.modules.search.rest.assembler.DatasetResourcesAssembler;
import fr.cnes.regards.modules.search.rest.assembler.FacettedPagedResourcesAssembler;
import fr.cnes.regards.modules.search.rest.assembler.PagedDatasetResourcesAssembler;
import fr.cnes.regards.modules.search.rest.assembler.resource.FacettedPagedResources;
import fr.cnes.regards.modules.search.rest.representation.IRepresentation;
import fr.cnes.regards.modules.search.schema.OpenSearchDescription;
import fr.cnes.regards.modules.search.service.ICatalogSearchService;
import fr.cnes.regards.modules.search.service.IFileEntityDescriptionHelper;
import fr.cnes.regards.modules.search.service.SearchException;
import fr.cnes.regards.modules.search.service.accessright.IAccessRightFilter;

/**
 * REST controller managing the research of REGARDS entities ({@link Collection}s, {@link Dataset}s, {@link DataObject}s
 * and {@link Document}s).
 * <p>
 * It :
 * <ol>
 * <li>Receives an OpenSearch format request, for example
 * <code>q=(tags=urn://laCollection)&type=collection&modele=ModelDeCollection</code>.
 * <li>Applies project filters by interpreting the OpenSearch query string and transforming them in ElasticSearch
 * criterion request. This is done with a plugin of type IFilter.
 * <li>Adds user group and data access filters. This is done with {@link IAccessRightFilter} service.
 * <li>Performs the ElasticSearch request on the project index. This is done with
 * {@link fr.cnes.regards.modules.indexer.service.IIndexerService}.
 * <li>Applies {@link IRepresentation} type plugins to the response.
 * <ol>
 * @author Xavier-Alexandre Brochard
 */
@RestController
@ModuleInfo(name = "search", version = "1.0-SNAPSHOT", author = "REGARDS", legalOwner = "CS",
        documentation = "http://test")
@RequestMapping(path = SearchController.PATH)
public class SearchController {
    /**
     * The main path
     */
    public static final String PATH = "/search";

    public static final String DATAOBJECTS_DATASETS_SEARCH = "/dataobjects/datasets";

    public static final String DOCUMENTS_SEARCH = "/documents";

    public static final String DATAOBJECTS_SEARCH_WITH_FACETS = "/dataobjects/withfacets";

    public static final String DATAOBJECTS_COMPUTE_FILES_SUMMARY = "/dataobjects/computefilessummary";

    public static final String DATAOBJECTS_SEARCH = "/dataobjects";

    public static final String DATASETS_SEARCH = "/datasets";

    public static final String COLLECTIONS_SEARCH = "/collections";

    public static final String SEARCH_WITH_FACETS = "/withfacets";

    public static final String DESCRIPTOR = "/descriptor.xml";

    public static final String ENTITY_GET_MAPPING = "/entities/{urn}";

    public static final String COLLECTIONS_URN = "/collections/{urn}";

    public static final String DATASETS_URN = "/datasets/{urn}";

    public static final String DATASETS_URN_FILE = "/datasets/{urn}/file";

    public static final String DATAOBJECTS_URN = "/dataobjects/{urn}";

    public static final String DOCUMENTS_URN = "/documents/{urn}";

    /**
     * Service performing the search from the query string. Autowired by Spring.
     */
    @Autowired
    private ICatalogSearchService catalogSearchService;

    /**
     * The resource service. Autowired by Spring.
     */
    @Autowired
    private IFileEntityDescriptionHelper fileEntityDescriptionHelper;

    /**
     * The resource service. Autowired by Spring.
     */
    @Autowired
    private IResourceService resourceService;

    /**
     * Get current tenant at runtime and allows tenant forcing. Autowired.
     */
    @Autowired
    private IRuntimeTenantResolver runtimeTenantResolver;

    @Autowired
    private OpenSearchDescriptionBuilder osDescriptorBuilder;

    /**
     * Perform an OpenSearch request on all indexed data, regardless of the type. The return objects can be any mix of
     * collection, dataset, dataobject and document.
     * @param allParams all query parameters
     * @param pPageable the page
     * @return the page of entities matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on all indexed data, regardless of the type. The "
            + "returned objects can be any mix of collection, dataset, dataobject and document.",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<AbstractEntity>>> searchAll(
            @RequestParam final Map<String, String> allParams, final Pageable pPageable,
            FacettedPagedResourcesAssembler<AbstractEntity> pAssembler) throws SearchException {
        final SimpleSearchKey<AbstractEntity> searchKey = Searches.onAllEntities(runtimeTenantResolver.getTenant());
        final FacetPage<AbstractEntity> result = catalogSearchService.search(allParams, searchKey, null, pPageable);
        return new ResponseEntity<>(pAssembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DESCRIPTOR, method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(
            description = "endpoint allowing to get the OpenSearch descriptor for searches on every type of entities",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchAllDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescriptorBuilder.build(null, SearchController.PATH), HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on all indexed data, regardless of the type. The return objects can be any mix of
     * collection, dataset, dataobject and document. Allows usage of facets.
     * @param allParams all query parameters
     * @param pFacets the facets to apply
     * @param pPageable the page
     * @return the page of entities matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = SEARCH_WITH_FACETS, method = RequestMethod.GET)
    @ResourceAccess(role = DefaultRole.PUBLIC,
            description = "Perform an OpenSearch request on all indexed data, regardless of the type. The return objects can be any mix of collection, dataset, dataobject and document.")
    public ResponseEntity<FacettedPagedResources<Resource<AbstractEntity>>> searchAll(
            @RequestParam final Map<String, String> allParams,
            @RequestParam(value = "facets", required = false) final String[] pFacets, final Pageable pPageable,
            FacettedPagedResourcesAssembler<AbstractEntity> pAssembler) throws SearchException {
        final SimpleSearchKey<AbstractEntity> searchKey = Searches.onAllEntities(runtimeTenantResolver.getTenant());
        final FacetPage<AbstractEntity> result = catalogSearchService.search(allParams, searchKey, pFacets, pPageable);
        return new ResponseEntity<>(pAssembler.toResource(result), HttpStatus.OK);
    }

    /**
     * Return the collection of passed URN_COLLECTION.
     * @param pUrn the Uniform Resource Name of the collection
     * @return the collection
     * @throws EntityNotFoundException           if no collection with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = COLLECTIONS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the collection of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<Collection>> getCollection(
            @Valid @PathVariable("urn") final UniformResourceName pUrn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        final Collection collection = catalogSearchService.get(pUrn);
        final Resource<Collection> resource = toResource(collection);
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on collections.
     * @param allParams all query parameters
     * @param pPageable the page
     * @param pAssembler injected by Spring
     * @return the page of collections matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = COLLECTIONS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on collection.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Collection>>> searchCollections(
            @RequestParam final Map<String, String> allParams, final Pageable pPageable,
            final PagedResourcesAssembler<Collection> pAssembler) throws SearchException {
        final SimpleSearchKey<Collection> searchKey = Searches
                .onSingleEntity(runtimeTenantResolver.getTenant(), EntityType.COLLECTION);
        final FacetPage<Collection> result = catalogSearchService.search(allParams, searchKey, null, pPageable);
        return new ResponseEntity<>(toPagedResources(result, pAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = COLLECTIONS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(description = "endpoint allowing to get the OpenSearch descriptor for searches on collections",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchCollectionsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescriptorBuilder.build(EntityType.COLLECTION, PATH + COLLECTIONS_SEARCH),
                                    HttpStatus.OK);
    }

    /**
     * Return the dataset of passed URN_COLLECTION.
     * @param pUrn the Uniform Resource Name of the dataset
     * @return the dataset
     * @throws EntityNotFoundException           if no dataset with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = DATASETS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the dataset of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<Dataset>> getDataset(@Valid @PathVariable("urn") final UniformResourceName pUrn,
            DatasetResourcesAssembler assembler) throws EntityOperationForbiddenException, EntityNotFoundException {
        final Dataset dataset = catalogSearchService.get(pUrn);
        return new ResponseEntity<>(assembler.toResource(dataset), HttpStatus.OK);
    }

    /**
     * Return the dataset file of passed dataset URN.
     * @param pUrn the Uniform Resource Name of the dataset
     * @return the dataset file
     */
    @RequestMapping(path = DATASETS_URN_FILE, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the dataset of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<InputStreamResource> retrieveDatasetDescription(
            @RequestParam(name = "origin", required = false) String origin, @PathVariable("urn") String pUrn,
            HttpServletResponse response)
            throws SearchException, EntityNotFoundException, EntityOperationForbiddenException, IOException {
        final Response fileStream = fileEntityDescriptionHelper.getFile(UniformResourceName.fromString(pUrn), response);
        // Return rs-dam headers
        HttpHeaders headers = new HttpHeaders();
        headers.add(HttpHeaders.CONTENT_TYPE,
                    fileStream.headers().get(HttpHeaders.CONTENT_TYPE).stream().findFirst().get());
        headers.add(HttpHeaders.CONTENT_LENGTH,
                    fileStream.headers().get(HttpHeaders.CONTENT_LENGTH).stream().findFirst().get());
        headers.add(HttpHeaders.CONTENT_DISPOSITION,
                    fileStream.headers().get(HttpHeaders.CONTENT_DISPOSITION).stream().findFirst().get());

        // set the X-Frame-Options header value to ALLOW-FROM origin
        if (origin != null) {
            response.setHeader(com.google.common.net.HttpHeaders.X_FRAME_OPTIONS, "ALLOW-FROM " + origin);
        }
        final InputStream inputStream = fileStream.body().asInputStream();
        return new ResponseEntity<>(new InputStreamResource(inputStream), headers, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on datasets.
     * @param allParams all query parameters
     * @param pPageable the page
     * @return the page of datasets matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATASETS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on dataset.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Dataset>>> searchDatasets(
            @RequestParam final Map<String, String> allParams, final Pageable pPageable,
            PagedDatasetResourcesAssembler pAssembler) throws SearchException {
        final SimpleSearchKey<Dataset> searchKey = Searches
                .onSingleEntity(runtimeTenantResolver.getTenant(), EntityType.DATASET);
        final FacetPage<Dataset> result = catalogSearchService.search(allParams, searchKey, null, pPageable);
        return new ResponseEntity<>(pAssembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DATASETS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(description = "endpoint allowing to get the OpenSearch descriptor for searches on datasets",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDatasetsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescriptorBuilder.build(EntityType.DATASET, PATH + DATASETS_SEARCH),
                                    HttpStatus.OK);
    }

    /**
     * Return the dataobject of passed URN_COLLECTION.
     * @param pUrn the Uniform Resource Name of the dataobject
     * @return the dataobject
     * @throws EntityNotFoundException           if no dataobject with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = DATAOBJECTS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the dataobject of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<DataObject>> getDataobject(
            @Valid @PathVariable("urn") final UniformResourceName pUrn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        final DataObject dataobject = catalogSearchService.get(pUrn);
        final Resource<DataObject> resource = toResource(dataobject);
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on dataobjects. Only return required facets.
     * @param allParams all query parameters
     * @param pFacets the facets to apply
     * @param pPageable the page
     * @return the page of dataobjects matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATAOBJECTS_SEARCH_WITH_FACETS, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on dataobject. Only return required facets.",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<FacettedPagedResources<Resource<DataObject>>> searchDataobjects(
            @RequestParam final Map<String, String> allParams,
            @RequestParam(value = "facets", required = false) String[] pFacets, final Pageable pPageable,
            FacettedPagedResourcesAssembler<DataObject> pAssembler) throws SearchException {
        final SimpleSearchKey<DataObject> searchKey = Searches
                .onSingleEntity(runtimeTenantResolver.getTenant(), EntityType.DATA);
        final FacetPage<DataObject> result = catalogSearchService.search(allParams, searchKey, pFacets, pPageable);
        return new ResponseEntity<>(pAssembler.toResource(result), HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on dataobjects without facets.
     * @param allParams all query parameters
     * @param pPageable the page
     * @return the page of dataobjects matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATAOBJECTS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on dataobject without facets",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<DataObject>>> searchDataobjects(
            @RequestParam final Map<String, String> allParams, final Pageable pPageable,
            FacettedPagedResourcesAssembler<DataObject> pAssembler) throws SearchException {
        final SimpleSearchKey<DataObject> searchKey = Searches
                .onSingleEntity(runtimeTenantResolver.getTenant(), EntityType.DATA);
        final Page<DataObject> result = catalogSearchService.search(allParams, searchKey, null, pPageable);
        return new ResponseEntity<>(pAssembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DATAOBJECTS_SEARCH_WITH_FACETS + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(description = "endpoint allowing to get the OpenSearch descriptor for searches on data",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDataobjectsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescriptorBuilder.build(EntityType.DATA, PATH + DATAOBJECTS_SEARCH_WITH_FACETS),
                                    HttpStatus.OK);
    }

    /**
     * Perform an joined OpenSearch request. The search will be performed on dataobjects attributes, but will return the
     * associated datasets.
     * @param allParams all query parameters
     * @param pFacets the facets to apply
     * @param pPageable the page
     * @param pAssembler injected by Spring
     * @return the page of datasets matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATAOBJECTS_DATASETS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description =
            "Perform a joined OpenSearch request. The search will be performed on dataobjects attributes, "
                    + "but will return the associated datasets.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Dataset>>> searchDataobjectsReturnDatasets(
            @RequestParam final Map<String, String> allParams,
            @RequestParam(value = "facets", required = false) final String[] pFacets, final Pageable pPageable,
            final PagedResourcesAssembler<Dataset> pAssembler) throws SearchException {
        final JoinEntitySearchKey<DataObject, Dataset> searchKey = Searches
                .onSingleEntityReturningJoinEntity(runtimeTenantResolver.getTenant(), EntityType.DATA,
                                                   EntityType.DATASET);
        final FacetPage<Dataset> result = catalogSearchService.search(allParams, searchKey, pFacets, pPageable);
        return new ResponseEntity<>(toPagedResources(result, pAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = DATAOBJECTS_DATASETS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(
            description = "endpoint allowing to get the OpenSearch descriptor for searches on data but result returned "
                    + "are datasets", role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDataobjectsReturnDatasetsDescriptor()
            throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescriptorBuilder.build(EntityType.DATA, PATH + DATAOBJECTS_DATASETS_SEARCH),
                                    HttpStatus.OK);
    }

    /**
     * Return the document of passed URN_COLLECTION.
     * @param pUrn the Uniform Resource Name of the document
     * @return the document
     * @throws EntityNotFoundException           if no document with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = DOCUMENTS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the document of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<Document>> getDocument(@Valid @PathVariable("urn") final UniformResourceName pUrn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        final Document document = catalogSearchService.get(pUrn);
        final Resource<Document> resource = toResource(document);
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Unified entity retrieval endpoint
     * @param pUrn the entity URN
     * @return an entity
     * @throws EntityNotFoundException           if no entity with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @SuppressWarnings("unchecked")
    @RequestMapping(path = ENTITY_GET_MAPPING, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the entity of passed URN.", role = DefaultRole.PUBLIC)
    public <E extends AbstractEntity> ResponseEntity<Resource<E>> getEntity(
            @Valid @PathVariable("urn") final UniformResourceName pUrn, DatasetResourcesAssembler assembler)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        // Retrieve entity
        E indexable = catalogSearchService.get(pUrn);
        // Prepare resource according to its type
        Resource<E> resource;
        if (EntityType.DATASET.name().equals(indexable.getType())) {
            resource = (Resource<E>) assembler.toResource((Dataset) indexable);
        } else {
            resource = toResource(indexable);
        }
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on documents.
     * @param allParams all query parameters
     * @param pPageable the page
     * @param pAssembler injected by Spring
     * @return the page of documents matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DOCUMENTS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on document.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Document>>> searchDocuments(
            @RequestParam final Map<String, String> allParams, final Pageable pPageable,
            final PagedResourcesAssembler<Document> pAssembler) throws SearchException {
        final SimpleSearchKey<Document> searchKey = Searches
                .onSingleEntity(runtimeTenantResolver.getTenant(), EntityType.DOCUMENT);
        final FacetPage<Document> result = catalogSearchService.search(allParams, searchKey, null, pPageable);
        return new ResponseEntity<>(toPagedResources(result, pAssembler), HttpStatus.OK);
    }

    @RequestMapping(path = DOCUMENTS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(
            description = "endpoint allowing to get the OpenSearch descriptor for searches on data but result returned are datasets",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDocumentsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescriptorBuilder.build(EntityType.DOCUMENT, PATH + DOCUMENTS_SEARCH),
                                    HttpStatus.OK);
    }

    /**
     * Compute a DocFileSummary for current user, for specified opensearch request, for asked file types (see DataType)
     * and eventualy restricted by a given datasetIpId
     * @param allParams request params containing "q" query param (opensearch request)
     * @param datasetIpId restrict summary to this datasetIpIp (can be null)
     * @param fileTypes asked files types DataType somewhere in rs-microservice
     * @return the computed summary
     * @throws SearchException is opensearch request is mouldy
     */
    @RequestMapping(path = DATAOBJECTS_COMPUTE_FILES_SUMMARY, method = RequestMethod.GET)
    @ResourceAccess(description = "compute dataset(s) summary", role = DefaultRole.INSTANCE_ADMIN)
    public ResponseEntity<DocFilesSummary> computeDatasetsSummary(@RequestParam final Map<String, String> allParams,
            @RequestParam(value = "datasetIpId", required = false) final String datasetIpId,
            @RequestParam(value = "fileTypes") final String[] fileTypes) throws SearchException {
        final SimpleSearchKey<DataObject> searchKey = Searches
                .onSingleEntity(runtimeTenantResolver.getTenant(), EntityType.DATA);
        DocFilesSummary summary = catalogSearchService
                .computeDatasetsSummary(allParams, searchKey, datasetIpId, fileTypes);
        return new ResponseEntity<>(summary, HttpStatus.OK);
    }

    /**
     * Convert a list of elements to a list of {@link Resource}
     * @param pElements list of elements to convert
     * @param pAssembler page resources assembler
     * @return a list of {@link Resource}
     */
    private <T> PagedResources<Resource<T>> toPagedResources(final Page<T> pElements,
            final PagedResourcesAssembler<T> pAssembler) {
        Preconditions.checkNotNull(pElements);
        final PagedResources<Resource<T>> pageResources = pAssembler.toResource(pElements);
        pageResources.forEach(resource -> resource.add(toResource(resource.getContent()).getLinks()));
        return pageResources;
    }

    private <T> Resource<T> toResource(final T pElement) {
        return resourceService.toResource(pElement);
    }

}