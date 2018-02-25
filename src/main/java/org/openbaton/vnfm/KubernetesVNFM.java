package org.openbaton.vnfm;

import org.openbaton.catalogue.mano.common.Event;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VNFRecordDependency;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.ConfigurationParameter;
import org.openbaton.catalogue.nfvo.Script;
import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.common.vnfm_sdk.amqp.AbstractVnfmSpringAmqp;
import org.openbaton.common.vnfm_sdk.utils.VnfmUtils;
import org.openbaton.exceptions.VimException;
import org.openbaton.nfvo.vim_interfaces.resource_management.ResourceManagement;
import org.openbaton.plugin.mgmt.PluginStartup;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.sdk.NFVORequestor;
import org.openbaton.sdk.NfvoRequestorBuilder;
import org.openbaton.sdk.api.exception.SDKException;
import org.openbaton.vim.drivers.VimDriverCaller;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.util.*;

public class KubernetesVNFM extends AbstractVnfmSpringAmqp {

    @Autowired
    private ConfigurableApplicationContext context;

    private ResourceManagement resourceManagement;

    private VimDriverCaller client;

    public static void main(String[] args){
        SpringApplication.run(KubernetesVNFM.class);
    }

    @Override
    public VirtualNetworkFunctionRecord instantiate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, Object scripts, Map<String, Collection<BaseVimInstance>> vimInstances) throws Exception {
        log.debug("Processing allocation of Resources for vnfr: " + virtualNetworkFunctionRecord);
        /**
         * Allocation of Resources
         *  the grant operation is already done before this method
         */
        String userdata = "";
        Iterator<ConfigurationParameter> configIterator =  virtualNetworkFunctionRecord.getConfigurations().getConfigurationParameters().iterator();
        while (configIterator.hasNext()) {
            ConfigurationParameter configurationParameter = configIterator.next();
            log.debug("Configuration Parameter:" + configurationParameter);
            if (configurationParameter.getConfKey().equals("GROUP_NAME")) {
                userdata = "export GROUP_NAME=" + configurationParameter.getValue() + "\n";
                userdata += "echo $GROUP_NAME > /home/ubuntu/group_name.txt\n";
            }
        }
        userdata += getUserData();

        log.debug("Processing allocation if Resources for vnfm: " + virtualNetworkFunctionRecord);
        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
            BaseVimInstance vimInstance = vimInstances.get(vdu.getParent_vdu()).iterator().next();
            log.debug("Creating " + vdu.getVnfc().size() + " VMs");
            for (VNFComponent vnfComponent : vdu.getVnfc()) {
                Map<String, String> floatingIps = new HashMap<>();
                for (VNFDConnectionPoint connectionPoint : vnfComponent.getConnection_point()) {
                    if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals("")) {
                        floatingIps.put(connectionPoint.getVirtual_link_reference(), connectionPoint.getFloatingIp());
                    }
                }
                try {
                    VNFCInstance vnfcInstance = resourceManagement.allocate(vimInstance, vdu, virtualNetworkFunctionRecord, vnfComponent, userdata, floatingIps, new HashSet<>()).get();
                    log.debug("Created VNFCInstance with id: " + vnfcInstance);
                    vdu.getVnfc_instance().add(vnfcInstance);
                } catch (VimException e) {
                    log.error(e.getMessage());
                    if (log.isDebugEnabled())
                        log.error(e.getMessage(), e);
                }
            }
        }
        log.debug("Allocated all Resources for vnfr: " + virtualNetworkFunctionRecord);
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void query() {

    }

    @Override
    public VirtualNetworkFunctionRecord scale(Action scaleInOrOut, VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFComponent component, Object scripts, VNFRecordDependency dependency) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void checkInstantiationFeasibility() {

    }

    @Override
    public VirtualNetworkFunctionRecord heal(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFCInstance component, String cause) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord updateSoftware(Script script, VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord modify(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFRecordDependency dependency) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void upgradeSoftware() {

    }

    @Override
    public VirtualNetworkFunctionRecord terminate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        log.info("Terminating vnfr with id " + virtualNetworkFunctionRecord.getId());

        NFVORequestor nfvoRequestor = new NFVORequestor("admin", "openbaton", "default", false, "127.0.0.1", "8080", "1");
//        NfvoRequestorBuilder nfvoRequestor = NfvoRequestorBuilder.create();
        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
            Set<VNFCInstance> vnfciToRem = new HashSet<>();
            List<BaseVimInstance> vimInstances = new ArrayList<>();
            BaseVimInstance vimInstance = null;
            try {
                vimInstances = nfvoRequestor.getVimInstanceAgent().findAll();
            } catch (SDKException e) {
                log.error(e.getMessage(), e);
            }
            for (BaseVimInstance vimInstanceFind : vimInstances) {
                if (vdu.getVimInstanceName().contains(vimInstanceFind.getName())) {
                    vimInstance = vimInstanceFind;
                    break;
                }
            }
            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
                log.debug("Releasing resources for vdu with id " + vdu.getId());
                try {
                    resourceManagement.release(vnfcInstance, vimInstance);
                } catch (VimException e) {
                    log.error(e.getMessage(), e);
                    throw new RuntimeException(e.getMessage(), e);
                }
                vnfciToRem.add(vnfcInstance);
                log.debug("Released resources for vdu with id " + vdu.getId());
            }
            vdu.getVnfc_instance().removeAll(vnfciToRem);
        }
        log.info("Terminated vnfr with id " + virtualNetworkFunctionRecord.getId());
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void handleError(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {

    }

    @Override
    public VirtualNetworkFunctionRecord start(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord stop(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord startVNFCInstance(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFCInstance vnfcInstance) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord stopVNFCInstance(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFCInstance vnfcInstance) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord configure(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public VirtualNetworkFunctionRecord resume(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, VNFCInstance vnfcInstance, VNFRecordDependency dependency) throws Exception {
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void NotifyChange() {

    }

    @Override
    protected void setup() {
        super.setup();
        try {
            //Start all the plugins that are located in ./plugins
            PluginStartup.startPluginRecursive("./plugins", true, "127.0.0.1", "5672", 15, "admin", "openbaton", "kubernetes","15672","./");
        } catch (IOException e) {
            e.printStackTrace();
        }
        //Fetching the KubernetesVIM using the kubernetes-plugin
        resourceManagement = (ResourceManagement) context.getBean("kubernetes", 19345, "15672");
        //Using the openstack-plugin directly
        client = (VimDriverCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getVimDriverCaller("127.0.0.1", "admin", "openbaton", 5672, "kubernetes", "kubernetes", "kubernetes","15672", 300);
    }
}
