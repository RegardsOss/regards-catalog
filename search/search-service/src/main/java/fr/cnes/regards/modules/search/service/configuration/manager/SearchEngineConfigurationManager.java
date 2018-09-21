package fr.cnes.regards.modules.search.service.configuration.manager;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.elasticsearch.common.Strings;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import fr.cnes.regards.framework.module.manager.AbstractModuleManager;
import fr.cnes.regards.framework.module.manager.ModuleConfiguration;
import fr.cnes.regards.framework.module.manager.ModuleConfigurationItem;
import fr.cnes.regards.framework.module.manager.ModuleReadinessReport;
import fr.cnes.regards.framework.module.rest.exception.ModuleException;
import fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration;
import fr.cnes.regards.modules.search.service.ISearchEngineConfigurationService;

/**
 * Search engine configuration manager.
 * We are only exporting {@link fr.cnes.regards.modules.search.domain.plugin.SearchEngineConfiguration} that are not linked to any datasets.
 *
 * @author Sylvain VISSIERE-GUERINET
 */
@Component
public class SearchEngineConfigurationManager extends AbstractModuleManager<Void> {

    @Autowired
    private ISearchEngineConfigurationService searchEngineConfigurationService;

    @Override
    protected Set<String> importConfiguration(ModuleConfiguration configuration) {
        Set<String> importErrors = new HashSet<>();
        for (ModuleConfigurationItem<?> item : configuration.getConfiguration()) {
            if (SearchEngineConfiguration.class.isAssignableFrom(item.getKey())) {
                SearchEngineConfiguration toImport = item.getTypedValue();
                if (!Strings.isNullOrEmpty(toImport.getDatasetUrn())) {
                    importErrors.add(String
                            .format("Skipping import of search engine configuration %s because it is linked to a dataset %s",
                                    toImport.getLabel(), toImport.getDatasetUrn()));
                } else {
                    try {
                        searchEngineConfigurationService.createConf(toImport);
                    } catch (ModuleException e) {
                        importErrors.add(String.format("Skipping import of search engine configuration %s: %s",
                                                       toImport.getLabel(), e.getMessage()));
                        logger.error(e.getMessage(), e);
                    }
                }
            }
        }
        return importErrors;
    }

    @Override
    public ModuleConfiguration exportConfiguration() throws ModuleException {
        List<ModuleConfigurationItem<?>> configurations = new ArrayList<>();
        for (SearchEngineConfiguration searchEngineConfiguration : searchEngineConfigurationService
                .retrieveAllConfs()) {
            // only export configuration that are not link to any dataset
            if (Strings.isNullOrEmpty(searchEngineConfiguration.getDatasetUrn())) {
                configurations.add(ModuleConfigurationItem.build(searchEngineConfiguration));
            }
        }
        return ModuleConfiguration.build(info, configurations);
    }

    @Override
    public ModuleReadinessReport<Void> isReady() {
        return new ModuleReadinessReport<Void>(true, null, null);
    }
}
