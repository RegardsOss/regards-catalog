/*
 * LICENSE_PLACEHOLDER
 */
package fr.cnes.regards.modules.search.domain;

import javax.persistence.*;
import javax.validation.constraints.NotNull;
import java.util.Set;

import org.hibernate.annotations.TypeDef;
import org.hibernate.annotations.TypeDefs;

import fr.cnes.regards.framework.jpa.json.JsonBinaryType;
import fr.cnes.regards.framework.modules.plugins.domain.PluginConfiguration;
import fr.cnes.regards.modules.search.validation.PluginServices;

/**
 * Class mapping Plugins of type IFilter, IConverter, IService to a Dataset
 *
 * @author Sylvain Vissiere-Guerinet
 *
 */
@TypeDefs({ @TypeDef(name = "jsonb", typeClass = JsonBinaryType.class) })
@Entity
@Table(name = "t_link_service_dataset",
        uniqueConstraints = @UniqueConstraint(columnNames = { "dataset_id" }, name = "uk_link_service_dataset_dataset_id"))
public class LinkPluginsDatasets {

    /**
     * Id of the dataset which is concerned by this mapping
     */
    @Id
    @SequenceGenerator(name = "linkServiceSequence", initialValue = 1, sequenceName = "seq_link_service_dataset")
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "linkServiceSequence")
    private Long linkId;

    @NotNull
    @Column(name = "dataset_id", nullable = false, length = 256)
    private String datasetId;

    /**
     * Ids of plugin configuration of type IService
     */
    @ManyToMany
    @JoinTable(name = "ta_link_service_dataset_plugins", joinColumns = @JoinColumn(name = "dataset_id", foreignKey = @ForeignKey(name = "fk_link_service_dataset_plugin")),
            inverseJoinColumns = @JoinColumn(name = "service_configuration_id", foreignKey = @ForeignKey(name = "fk_plugin_link_service_dataset")))
    private Set<PluginConfiguration> services;

    /**
     * Only there for (de)serialization purpose and hibernate
     */
    protected LinkPluginsDatasets() {
    }

    /**
     * Constructor
     *
     * @param pDatasetId
     *            Id of the dataset which is concerned by this mapping
     * @param pServices
     *            Ids of plugin configuration of type IService
     */
    public LinkPluginsDatasets(final String pDatasetId, @PluginServices final Set<PluginConfiguration> pServices) {
        super();
        datasetId = pDatasetId;
        services = pServices;
    }

    public String getDatasetId() {
        return datasetId;
    }

    public void setDatasetId(final String pDatasetId) {
        datasetId = pDatasetId;
    }

    public Set<PluginConfiguration> getServices() {
        return services;
    }

    public void setServices(@PluginServices final Set<PluginConfiguration> pServices) {
        services = pServices;
    }

    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = (prime * result) + ((datasetId == null) ? 0 : datasetId.hashCode());
        return result;
    }

    @Override
    public boolean equals(final Object obj) {
        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        final LinkPluginsDatasets other = (LinkPluginsDatasets) obj;
        if (datasetId == null) {
            if (other.datasetId != null) {
                return false;
            }
        } else
            if (!datasetId.equals(other.datasetId)) {
                return false;
            }
        return true;
    }

    @Override
    public String toString() {
        return "LinkPluginsDatasets [linkId=" + linkId + ", datasetId=" + datasetId + "]";
    }

}
