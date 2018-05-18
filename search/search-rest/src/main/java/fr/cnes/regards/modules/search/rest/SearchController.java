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
package fr.cnes.regards.modules.search.rest;

import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.io.InputStream;
import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.io.InputStreamResource;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.util.MultiValueMap;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.google.common.base.Preconditions;
import com.google.common.collect.Collections2;
import com.google.common.collect.Iterables;
import com.google.common.collect.Sets;
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
import fr.cnes.regards.modules.indexer.domain.criterion.ICriterion;
import fr.cnes.regards.modules.indexer.domain.summary.DocFilesSummary;
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
 * fr.cnes.regards.modules.indexer.service.IIndexerService.
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

    public static final String DOCUMENTS_SEARCH_WITH_FACETS = "/documents/withfacets";

    public static final String DATAOBJECTS_SEARCH_WITH_FACETS = "/dataobjects/withfacets";

    public static final String DATAOBJECTS_COMPUTE_FILES_SUMMARY = "/dataobjects/computefilessummary";

    public static final String DATAOBJECTS_SEARCH = "/dataobjects";

    public static final String DATASETS_SEARCH = "/datasets";

    public static final String COLLECTIONS_SEARCH = "/collections";

    public static final String SEARCH_WITH_FACETS = "/withfacets";

    public static final String DESCRIPTOR = "/descriptor.xml";

    public static final String ENTITY_GET_MAPPING = "/entities/{urn}";

    public static final String ENTITY_HAS_ACCESS = ENTITY_GET_MAPPING + "/access";

    public static final String ENTITIES_HAS_ACCESS = "/entities/access";

    public static final String COLLECTIONS_URN = "/collections/{urn}";

    public static final String DATASETS_URN = "/datasets/{urn}";

    public static final String DATASETS_URN_FILE = "/datasets/{urn}/file";

    public static final String DATAOBJECTS_URN = "/dataobjects/{urn}";

    public static final String DOCUMENTS_URN = "/documents/{urn}";

    public static final String DATAOBJECT_PROPERTIES_VALUES = "/dataobjects/properties/{name}/values";

    /**
     * Service performing the search from the query string.
     */
    @Autowired
    private ICatalogSearchService searchService;

    @Autowired
    private IFileEntityDescriptionHelper descHelper;

    /**
     * The resource service.
     */
    @Autowired
    private IResourceService resourceService;

    /**
     * Get current tenant at runtime and allows tenant forcing. Autowired.
     */
    @Autowired
    private IRuntimeTenantResolver tenantResolver;

    @Autowired
    private OpenSearchDescriptionBuilder osDescBuilder;

    /**
     * Perform an OpenSearch request on all indexed data, regardless of the type. The return objects can be any mix of
     * collection, dataset, dataobject and document.
     * @param allParams all query parameters
     * @param pageable the page
     * @return the page of entities matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on all indexed data, regardless of the type. The "
            + "returned objects can be any mix of collection, dataset, dataobject and document.",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<AbstractEntity>>> searchAll(
            @RequestParam Map<String, String> allParams, Pageable pageable,
            FacettedPagedResourcesAssembler<AbstractEntity> assembler) throws SearchException {
        SimpleSearchKey<AbstractEntity> searchKey = Searches.onAllEntities(tenantResolver.getTenant());
        Map<String, String> decodedParams = getDecodedParams(allParams);
        FacetPage<AbstractEntity> result = searchService.search(decodedParams, searchKey, null, pageable);
        return new ResponseEntity<>(assembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DESCRIPTOR, method = RequestMethod.GET, produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(
            description = "endpoint allowing to get the OpenSearch descriptor for searches on every type of entities",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchAllDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescBuilder.build(null, SearchController.PATH), HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on all indexed data, regardless of the type. The return objects can be any mix of
     * collection, dataset, dataobject and document. Allows usage of facets.
     * @param allParams all query parameters
     * @param pFacets the facets to apply
     * @param pageable the page
     * @return the page of entities matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = SEARCH_WITH_FACETS, method = RequestMethod.GET)
    @ResourceAccess(role = DefaultRole.PUBLIC,
            description = "Perform an OpenSearch request on all indexed data, regardless of the type. The return objects can be any mix of collection, dataset, dataobject and document.")
    public ResponseEntity<FacettedPagedResources<Resource<AbstractEntity>>> searchAll(
            @RequestParam Map<String, String> allParams,
            @RequestParam(value = "facets", required = false) String[] pFacets, Pageable pageable,
            FacettedPagedResourcesAssembler<AbstractEntity> pAssembler) throws SearchException {
        SimpleSearchKey<AbstractEntity> searchKey = Searches.onAllEntities(tenantResolver.getTenant());
        Map<String, String> decodedParams = getDecodedParams(allParams);
        FacetPage<AbstractEntity> result = searchService.search(decodedParams, searchKey, pFacets, pageable);
        return new ResponseEntity<>(pAssembler.toResource(result), HttpStatus.OK);
    }

    /**
     * Return the collection of passed URN_COLLECTION.
     * @param urn the Uniform Resource Name of the collection
     * @return the collection
     * @throws EntityNotFoundException           if no collection with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = COLLECTIONS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the collection of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<Collection>> getCollection(@Valid @PathVariable("urn") UniformResourceName urn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        Collection collection = searchService.get(urn);
        Resource<Collection> resource = toResource(collection);
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on collections.
     * @param allParams all query parameters
     * @param pageable the page
     * @param assembler injected by Spring
     * @return the page of collections matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = COLLECTIONS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on collection.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Collection>>> searchCollections(
            @RequestParam final Map<String, String> allParams, Pageable pageable,
            PagedResourcesAssembler<Collection> assembler) throws SearchException {
        SimpleSearchKey<Collection> searchKey = Searches
                .onSingleEntity(tenantResolver.getTenant(), EntityType.COLLECTION);
        Map<String, String> decodedParams = getDecodedParams(allParams);
        FacetPage<Collection> result = searchService.search(decodedParams, searchKey, null, pageable);
        return new ResponseEntity<>(toPagedResources(result, assembler), HttpStatus.OK);
    }

    @RequestMapping(path = COLLECTIONS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(description = "endpoint allowing to get the OpenSearch descriptor for searches on collections",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchCollectionsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescBuilder.build(EntityType.COLLECTION, PATH + COLLECTIONS_SEARCH),
                                    HttpStatus.OK);
    }

    /**
     * Return the dataset of passed URN_COLLECTION.
     * @param urn the Uniform Resource Name of the dataset
     * @return the dataset
     * @throws EntityNotFoundException           if no dataset with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = DATASETS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the dataset of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<Dataset>> getDataset(@Valid @PathVariable("urn") UniformResourceName urn,
            DatasetResourcesAssembler assembler) throws EntityOperationForbiddenException, EntityNotFoundException {
        Dataset dataset = searchService.get(urn);
        return new ResponseEntity<>(assembler.toResource(dataset), HttpStatus.OK);
    }

    /**
     * Return the dataset file of passed dataset URN.
     * @param urn the Uniform Resource Name of the dataset
     * @return the dataset file
     */
    @RequestMapping(path = DATASETS_URN_FILE, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the dataset description file.", role = DefaultRole.PUBLIC)
    public ResponseEntity<InputStreamResource> retrieveDatasetDescription(
            @RequestParam(name = "origin", required = false) String origin, @PathVariable("urn") String urn,
            HttpServletResponse response)
            throws SearchException, EntityNotFoundException, EntityOperationForbiddenException, IOException {
        Response fileStream = descHelper.getFile(UniformResourceName.fromString(urn), response);
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
        InputStream inputStream = fileStream.body().asInputStream();
        return new ResponseEntity<>(new InputStreamResource(inputStream), headers, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on datasets.
     * @param allParams all query parameters
     * @param pageable the page
     * @return the page of datasets matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATASETS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on dataset.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Dataset>>> searchDatasets(@RequestParam Map<String, String> allParams,
            Pageable pageable, PagedDatasetResourcesAssembler assembler) throws SearchException {
        SimpleSearchKey<Dataset> searchKey = Searches.onSingleEntity(tenantResolver.getTenant(), EntityType.DATASET);
        Map<String, String> decodedParams = getDecodedParams(allParams);
        FacetPage<Dataset> result = searchService.search(decodedParams, searchKey, null, pageable);
        return new ResponseEntity<>(assembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DATASETS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(description = "endpoint allowing to get the OpenSearch descriptor for searches on datasets",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDatasetsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescBuilder.build(EntityType.DATASET, PATH + DATASETS_SEARCH), HttpStatus.OK);
    }

    /**
     * Return the dataobject of passed URN_COLLECTION.
     * @param urn the Uniform Resource Name of the dataobject
     * @return the dataobject
     * @throws EntityNotFoundException           if no dataobject with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = DATAOBJECTS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the dataobject of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<DataObject>> getDataobject(@Valid @PathVariable("urn") UniformResourceName urn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        DataObject dataobject = searchService.get(urn);
        dataobject.containsPhysicalData();
        Resource<DataObject> resource = toResource(dataobject);
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on dataobjects. Only return required facets.
     * @param allParams all query parameters multi valued because of sort
     * @param facets the facets to apply
     * @param pageable the page
     * @return the page of dataobjects matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATAOBJECTS_SEARCH_WITH_FACETS, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on dataobject. Only return required facets.",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<FacettedPagedResources<Resource<DataObject>>> searchDataobjects(
            @RequestParam MultiValueMap<String, String> allParams,
            @RequestParam(value = "facets", required = false) String[] facets, Pageable pageable,
            FacettedPagedResourcesAssembler<DataObject> assembler) throws SearchException {
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(tenantResolver.getTenant(), EntityType.DATA);
        // There are several "sort=<name>,<ASC|DESC>" request parameters that are directly mapped into Pageable BUT
        // to be taken into account, allParams MUST BE of type MultiValueMap, not Map.
        // It is the only parameter that can be multi-occured so we only take its first value it from MultiValueMap and
        // we "transform" MultiValueMap into Map (which is expected by searchService)
        Map<String, String> params = allParams.entrySet().stream()
                .collect(Collectors.toMap(Map.Entry::getKey, e -> e.getValue().get(0)));
        Map<String, String> decodedParams = getDecodedParams(params);
        FacetPage<DataObject> result = searchService.search(decodedParams, searchKey, facets, pageable);
        result.getContent().forEach(DataObject::containsPhysicalData);
        return new ResponseEntity<>(assembler.toResource(result), HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on dataobjects without facets.
     * @param allParams all query parameters
     * @param pageable the page
     * @return the page of dataobjects matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATAOBJECTS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on dataobject without facets",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<DataObject>>> searchDataobjects(
            @RequestParam Map<String, String> allParams, Pageable pageable,
            FacettedPagedResourcesAssembler<DataObject> assembler) throws SearchException {
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(tenantResolver.getTenant(), EntityType.DATA);
        Map<String, String> decodedParams = getDecodedParams(allParams);
        Page<DataObject> result = searchService.search(decodedParams, searchKey, null, pageable);
        result.getContent().forEach(DataObject::containsPhysicalData);
        return new ResponseEntity<>(assembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DATAOBJECTS_SEARCH_WITH_FACETS + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(description = "endpoint allowing to get the OpenSearch descriptor for searches on data",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDataobjectsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescBuilder.build(EntityType.DATA, PATH + DATAOBJECTS_SEARCH_WITH_FACETS),
                                    HttpStatus.OK);
    }

    /**
     * Perform an joined OpenSearch request. The search will be performed on dataobjects attributes, but will return the
     * associated datasets.
     * @param allParams all query parameters
     * @param facets the facets to apply
     * @param pageable the page
     * @param assembler injected by Spring
     * @return the page of datasets matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DATAOBJECTS_DATASETS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description =
            "Perform a joined OpenSearch request. The search will be performed on dataobjects attributes, "
                    + "but will return the associated datasets.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Dataset>>> searchDataobjectsReturnDatasets(
            @RequestParam Map<String, String> allParams,
            @RequestParam(value = "facets", required = false) String[] facets, Pageable pageable,
            PagedResourcesAssembler<Dataset> assembler) throws SearchException {
        JoinEntitySearchKey<DataObject, Dataset> searchKey = Searches
                .onSingleEntityReturningJoinEntity(tenantResolver.getTenant(), EntityType.DATA, EntityType.DATASET);
        Map<String, String> decodedParams = getDecodedParams(allParams);
        FacetPage<Dataset> result = searchService.search(decodedParams, searchKey, facets, pageable);
        return new ResponseEntity<>(toPagedResources(result, assembler), HttpStatus.OK);
    }

    @RequestMapping(path = DATAOBJECTS_DATASETS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(
            description = "endpoint allowing to get the OpenSearch descriptor for searches on data but result returned "
                    + "are datasets", role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDataobjectsReturnDatasetsDescriptor()
            throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescBuilder.build(EntityType.DATA, PATH + DATAOBJECTS_DATASETS_SEARCH),
                                    HttpStatus.OK);
    }

    /**
     * Return the document of passed URN_COLLECTION.
     * @param urn the Uniform Resource Name of the document
     * @return the document
     * @throws EntityNotFoundException           if no document with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @RequestMapping(path = DOCUMENTS_URN, method = RequestMethod.GET)
    @ResourceAccess(description = "Return the document of passed URN_COLLECTION.", role = DefaultRole.PUBLIC)
    public ResponseEntity<Resource<Document>> getDocument(@Valid @PathVariable("urn") UniformResourceName urn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        final Document document = searchService.get(urn);
        final Resource<Document> resource = toResource(document);
        return new ResponseEntity<>(resource, HttpStatus.OK);
    }

    /**
     * Unified entity retrieval endpoint
     * @param urn the entity URN
     * @return an entity
     * @throws EntityNotFoundException           if no entity with identifier provided can be found
     * @throws EntityOperationForbiddenException if the current user does not have suffisant rights
     */
    @SuppressWarnings("unchecked")
    @RequestMapping(path = ENTITY_GET_MAPPING, method = RequestMethod.GET)
    @ResourceAccess(description = "Return given URN entity.", role = DefaultRole.PUBLIC)
    public <E extends AbstractEntity> ResponseEntity<Resource<E>> getEntity(
            @Valid @PathVariable("urn") UniformResourceName urn, DatasetResourcesAssembler assembler)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        // Retrieve entity
        E indexable = searchService.get(urn);
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
     * @param pageable the page
     * @param pAssembler injected by Spring
     * @return the page of documents matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DOCUMENTS_SEARCH, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on document.", role = DefaultRole.PUBLIC)
    public ResponseEntity<PagedResources<Resource<Document>>> searchDocuments(
            @RequestParam Map<String, String> allParams, Pageable pageable,
            PagedResourcesAssembler<Document> pAssembler) throws SearchException {
        SimpleSearchKey<Document> searchKey = Searches.onSingleEntity(tenantResolver.getTenant(), EntityType.DOCUMENT);
        Map<String, String> decodedParams = getDecodedParams(allParams);
        FacetPage<Document> result = searchService.search(decodedParams, searchKey, null, pageable);
        return new ResponseEntity<>(toPagedResources(result, pAssembler), HttpStatus.OK);
    }

    /**
     * Perform an OpenSearch request on documents. Only return required facets.
     * @param allParams all query parameters
     * @param facets the facets to apply
     * @param pageable the page
     * @return the page of documents matching the query
     * @throws SearchException when an error occurs while parsing the query
     */
    @RequestMapping(path = DOCUMENTS_SEARCH_WITH_FACETS, method = RequestMethod.GET)
    @ResourceAccess(description = "Perform an OpenSearch request on documents. Only return required facets.",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<FacettedPagedResources<Resource<Document>>> searchDocuments(
            @RequestParam final Map<String, String> allParams,
            @RequestParam(value = "facets", required = false) String[] facets, final Pageable pageable,
            FacettedPagedResourcesAssembler<Document> assembler) throws SearchException {
        final SimpleSearchKey<Document> searchKey = Searches
                .onSingleEntity(tenantResolver.getTenant(), EntityType.DOCUMENT);
        Map<String, String> decodedParams = getDecodedParams(allParams);
        final FacetPage<Document> result = searchService.search(decodedParams, searchKey, facets, pageable);
        return new ResponseEntity<>(assembler.toResource(result), HttpStatus.OK);
    }

    @RequestMapping(path = DOCUMENTS_SEARCH + DESCRIPTOR, method = RequestMethod.GET,
            produces = MediaType.APPLICATION_XML_VALUE)
    @ResourceAccess(
            description = "endpoint allowing to get the OpenSearch descriptor for searches on data but result returned are datasets",
            role = DefaultRole.PUBLIC)
    public ResponseEntity<OpenSearchDescription> searchDocumentsDescriptor() throws UnsupportedEncodingException {
        return new ResponseEntity<>(osDescBuilder.build(EntityType.DOCUMENT, PATH + DOCUMENTS_SEARCH), HttpStatus.OK);
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
    @ResourceAccess(description = "compute dataset(s) summary", role = DefaultRole.REGISTERED_USER)
    public ResponseEntity<DocFilesSummary> computeDatasetsSummary(@RequestParam Map<String, String> allParams,
            @RequestParam(value = "datasetIpId", required = false) String datasetIpId,
            @RequestParam(value = "fileTypes") String[] fileTypes) throws SearchException {
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(tenantResolver.getTenant(), EntityType.DATA);
        DocFilesSummary summary = searchService.computeDatasetsSummary(allParams, searchKey, datasetIpId, fileTypes);
        return new ResponseEntity<>(summary, HttpStatus.OK);
    }

    /**
     * Retrieve given STRING property enumerated values (limited by maxCount, partial text contains and dataobjects
     * openSearch request from allParams (as usual)).
     * @param propertyPath concerned STRING property path
     * @param allParams opensearch request
     * @param partialText text that property should contains (can be null)
     * @param maxCount maximum result count
     */
    @RequestMapping(path = DATAOBJECT_PROPERTIES_VALUES, method = RequestMethod.GET)
    @ResourceAccess(description = "Retrieve enumerated property values", role = DefaultRole.PUBLIC)
    public ResponseEntity<List<String>> retrieveEnumeratedPropertyValues(@PathVariable("name") String propertyPath,
            @RequestParam Map<String, String> allParams, @RequestParam(value = "maxCount") int maxCount,
            @RequestParam(value = "partialText", required = false) String partialText) throws SearchException {
        SimpleSearchKey<DataObject> searchKey = Searches.onSingleEntity(tenantResolver.getTenant(), EntityType.DATA);
        return ResponseEntity.ok(searchService
                                         .retrieveEnumeratedPropertyValues(allParams, searchKey, propertyPath, maxCount,
                                                                           partialText));
    }

    @RequestMapping(path = ENTITY_HAS_ACCESS, method = RequestMethod.GET)
    @ResourceAccess(description = "allows to know if the user can download an entity", role = DefaultRole.PUBLIC)
    public ResponseEntity<Boolean> hasAccess(@PathVariable("urn") UniformResourceName urn)
            throws EntityOperationForbiddenException, EntityNotFoundException {
        AbstractEntity entity = searchService.get(urn);
        if (entity instanceof DataObject) {
            return ResponseEntity.ok(((DataObject) entity).getDownloadable());
        }
        return ResponseEntity.ok(true);
    }

    @RequestMapping(path = ENTITIES_HAS_ACCESS, method = RequestMethod.POST)
    @ResourceAccess(description = "allows to know if the user can download entities", role = DefaultRole.PUBLIC)
    public ResponseEntity<Set<UniformResourceName>> hasAccess(
            @RequestBody java.util.Collection<UniformResourceName> inUrns) throws SearchException {
        if (inUrns.isEmpty()) {
            return ResponseEntity.ok(Collections.emptySet());
        }
        String index = inUrns.iterator().next().getTenant();
        Set<UniformResourceName> urnsWithAccess = new HashSet<>();
        // ElasticSearch cannot manage more than 1024 criterions clauses at once. There is one clause per IP_ID plus
        // or clauses plus some depending on user access => create partitions of 1 000
        Iterable<List<UniformResourceName>> urnLists = Iterables.partition(inUrns, 1_000);
        for (List<UniformResourceName> urns : urnLists) {
            ICriterion criterion = ICriterion.or(urns.stream().map(urn -> ICriterion.eq("ipId", urn.toString()))
                                                         .toArray(n -> new ICriterion[n]));
            FacetPage<DataObject> page = searchService
                    .search(criterion, Searches.onSingleEntity(index, EntityType.DATA), null,
                            new PageRequest(0, urns.size()));
            urnsWithAccess.addAll(page.getContent().parallelStream().filter(DataObject::getDownloadable)
                                          .map(DataObject::getIpId).collect(Collectors.toSet()));
        }

        return ResponseEntity.ok(urnsWithAccess);
    }

    /**
     * Decode params
     * @param allParams params received by the endpoint
     * @return params decoded
     */
    private Map<String, String> getDecodedParams(Map<String, String> allParams) throws SearchException {
        try {
            // Store decoded params
            Map<String, String> allParamsDecoded = new HashMap(allParams.size());
            // Parse params to build the params map decoded
            for (Map.Entry<String, String> entry : allParams.entrySet()) {
                allParamsDecoded.put(entry.getKey(), URLDecoder.decode(entry.getValue(), "UTF-8"));
            }
            return allParamsDecoded;
        } catch (UnsupportedEncodingException e) {
            String message = "Unsupported query parameters";
            StringJoiner sj = new StringJoiner("&");
            allParams.forEach((key, value) -> sj.add(key + "=" + value));
            message += sj.toString();
            throw new SearchException(message, e);
        }
    }

    /**
     * Convert a list of elements to a list of {@link Resource}
     * @param elements list of elements to convert
     * @param assembler page resources assembler
     * @return a list of {@link Resource}
     */
    private <T> PagedResources<Resource<T>> toPagedResources(Page<T> elements, PagedResourcesAssembler<T> assembler) {
        Preconditions.checkNotNull(elements);
        PagedResources<Resource<T>> pageResources = assembler.toResource(elements);
        pageResources.forEach(resource -> resource.add(toResource(resource.getContent()).getLinks()));
        return pageResources;
    }

    private <T> Resource<T> toResource(final T pElement) {
        return resourceService.toResource(pElement);
    }

}
