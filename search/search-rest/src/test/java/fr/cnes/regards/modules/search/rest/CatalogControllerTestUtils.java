/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.search.rest;

import java.util.ArrayList;
import java.util.List;

import org.mockito.Mockito;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PagedResourcesAssembler;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.PagedResources;
import org.springframework.hateoas.Resource;

import com.google.common.collect.Lists;

import fr.cnes.regards.modules.crawler.domain.criterion.ICriterion;
import fr.cnes.regards.modules.entities.domain.Collection;
import fr.cnes.regards.modules.entities.domain.DataObject;
import fr.cnes.regards.modules.entities.domain.Dataset;
import fr.cnes.regards.modules.entities.domain.Document;
import fr.cnes.regards.modules.entities.urn.UniformResourceName;
import fr.cnes.regards.modules.search.rest.CatalogController.SearchType;

/**
 *
 * @author Xavier-Alexandre Brochard
 */
@SuppressWarnings("unchecked")
public class CatalogControllerTestUtils {

    /**
     * A dummy assembler for collections
     */
    public static final PagedResourcesAssembler<Collection> ASSEMBLER_COLLECTION = Mockito
            .mock(PagedResourcesAssembler.class);

    /**
     * A dummy assembler for dataobjects
     */
    public static final PagedResourcesAssembler<DataObject> ASSEMBLER_DATAOBJECT = Mockito
            .mock(PagedResourcesAssembler.class);

    /**
     * A dummy assembler for datasets
     */
    public static final PagedResourcesAssembler<Dataset> ASSEMBLER_DATASET = Mockito
            .mock(PagedResourcesAssembler.class);

    /**
     * A dummy assembler for documents
     */
    public static final PagedResourcesAssembler<Document> ASSEMBLER_DOCUMENT = Mockito
            .mock(PagedResourcesAssembler.class);

    /**
     * A dummy collection
     */
    public static final Collection COLLECTION = new Collection();

    /**
     * A dummy dataobject
     */
    public static final DataObject DATAOBJECT = new DataObject();

    /**
     * A dummy dataset
     */
    public static final Dataset DATASET = new Dataset();

    /**
     * A dummy document
     */
    public static final Document DOCUMENT = new Document();

    /**
     * A dummy list of facets
     */
    public static final List<String> FACETS = Lists.newArrayList("faceA", "faceB");

    /**
     * A dummy page of dataobjects
     */
    public static final Page<DataObject> PAGE_DATAOBJECT = new PageImpl<>(
            Lists.newArrayList(CatalogControllerTestUtils.DATAOBJECT));

    /**
     * A dummy page of dataobjects
     */
    public static final Page<Dataset> PAGE_DATASET = new PageImpl<>(
            Lists.newArrayList(CatalogControllerTestUtils.DATASET));

    /**
     * A mock pageable
     */
    public static final Pageable PAGEABLE = Mockito.mock(Pageable.class);

    /**
     * A dummy paged resources of dataobjects
     */
    public static final PagedResources<Resource<DataObject>> PAGED_RESOURCES_DATAOBJECT = new PagedResources<Resource<DataObject>>(
            new ArrayList<>(), null, new Link("href"));

    /**
     * A dummy paged resources of dataset
     */
    public static final PagedResources<Resource<Dataset>> PAGED_RESOURCES_DATASET = new PagedResources<Resource<Dataset>>(
            new ArrayList<>(), null, new Link("href"));;

    /**
     * Dummy OpenSearch request
     */
    public static final String Q = "this:(is AND the) OR querys:string";

    /**
     * A criterion string match
     */
    public static final ICriterion SIMPLE_STRING_MATCH_CRITERION = ICriterion.equals("field", "value");

    /**
     * Define a criterion with a nested criterion of name "target" (this must be detected and properly handled)
     */
    public static final ICriterion CRITERION_WITH_NESTED_TARGET_FIELD = ICriterion
            .or(ICriterion.equals("target", SearchType.DATASET.toString()), ICriterion.equals("field", "value"));

    /**
     * Define a criterion with a nested criterion of name "dataset" (this must be detected and properly handled)
     */
    public static final ICriterion CRITERION_WITH_NESTED_DATASET_FIELD = ICriterion
            .or(ICriterion.equals("dataset", "whatever"), ICriterion.equals("field", "value"));

    /**
     * Define a criterion with a nested criterion of name "datasets" (this must be detected and properly handled)
     */
    public static final ICriterion CRITERION_WITH_NESTED_DATASETS_FIELD = ICriterion
            .or(ICriterion.equals("datasets", "whatever"), ICriterion.equals("field", "value"));

    /**
     * A dummy sort
     */
    public static final Sort SORT = new Sort("date");

    /**
     * A dummy tenant
     */
    public static final String TENANT = "tenant";

    /**
     * A dummy urn of a collection
     */
    public static final UniformResourceName URN_COLLECTION = new UniformResourceName();

    /**
     * A dummy urn for a dataobject
     */
    public static final UniformResourceName URN_DATAOBJECT = new UniformResourceName();

    /**
     * A dummy urn for a dataset
     */
    public static final UniformResourceName URN_DATASET = new UniformResourceName();

    /**
     * A dummy urn for a document
     */
    public static final UniformResourceName URN_DOCUMENT = new UniformResourceName();

}
