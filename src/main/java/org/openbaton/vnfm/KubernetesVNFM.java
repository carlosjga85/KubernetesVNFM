package org.openbaton.vnfm;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.*;
import io.kubernetes.client.util.Config;
import org.openbaton.catalogue.mano.common.Event;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VNFRecordDependency;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.ConfigurationParameter;
import org.openbaton.catalogue.nfvo.DependencyParameters;
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
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.FileInputStream;
import java.io.IOException;
import java.util.*;

public class KubernetesVNFM extends AbstractVnfmSpringAmqp {

    @Autowired
    private ConfigurableApplicationContext context;

    private ResourceManagement resourceManagement;

    private VimDriverCaller client;

    private static Logger logger = LoggerFactory.getLogger(KubernetesVNFM.class);

    public static void main(String[] args){
        SpringApplication.run(KubernetesVNFM.class);
    }

    @Override
    public VirtualNetworkFunctionRecord instantiate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, Object scripts, Map<String, Collection<BaseVimInstance>> vimInstances) throws Exception {

        log("INSTANCIATE", "New Instantiation");
        log("VNFR::", virtualNetworkFunctionRecord);

        System.out.println("Trying to print something");

        for (VirtualDeploymentUnit virtualDeploymentUnit : virtualNetworkFunctionRecord.getVdu()) {
            log("virtualDeploymentUnit: ", virtualDeploymentUnit);
            log("VNFC: ", virtualDeploymentUnit.getVnfc());
            log("VNFCInstance: ", virtualDeploymentUnit.getVnfc_instance());
//            for (VNFCInstance vnfcInstance : virtualDeploymentUnit.getVnfc_instance()) {
//                log("VNFCInstance: ", vnfcInstance);
//            }
        }



        createDeployment(virtualNetworkFunctionRecord, vimInstances);




//        log.debug("Processing allocation of Resources for vnfr: " + virtualNetworkFunctionRecord);
//        /**
//         * Allocation of Resources
//         *  the grant operation is already done before this method
//         */
//        String userdata = "";
//        Iterator<ConfigurationParameter> configIterator =  virtualNetworkFunctionRecord.getConfigurations().getConfigurationParameters().iterator();
//        while (configIterator.hasNext()) {
//            ConfigurationParameter configurationParameter = configIterator.next();
//            log.debug("Configuration Parameter:" + configurationParameter);
//            if (configurationParameter.getConfKey().equals("GROUP_NAME")) {
//                userdata = "export GROUP_NAME=" + configurationParameter.getValue() + "\n";
//                userdata += "echo $GROUP_NAME > /home/ubuntu/group_name.txt\n";
//            }
//        }
//        userdata += getUserData();
//
//        log.debug("Processing allocation if Resources for vnfm: " + virtualNetworkFunctionRecord);
//        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
//            BaseVimInstance vimInstance = vimInstances.get(vdu.getParent_vdu()).iterator().next();
//            log.debug("Creating " + vdu.getVnfc().size() + " VMs");
//            for (VNFComponent vnfComponent : vdu.getVnfc()) {
//                Map<String, String> floatingIps = new HashMap<>();
//                for (VNFDConnectionPoint connectionPoint : vnfComponent.getConnection_point()) {
//                    if (connectionPoint.getFloatingIp() != null && !connectionPoint.getFloatingIp().equals("")) {
//                        floatingIps.put(connectionPoint.getVirtual_link_reference(), connectionPoint.getFloatingIp());
//                    }
//                }
//                try {
//                    VNFCInstance vnfcInstance = resourceManagement.allocate(vimInstance, vdu, virtualNetworkFunctionRecord, vnfComponent, userdata, floatingIps, new HashSet<>()).get();
//                    log.debug("Created VNFCInstance with id: " + vnfcInstance);
//                    vdu.getVnfc_instance().add(vnfcInstance);
//                } catch (VimException e) {
//                    log.error(e.getMessage());
//                    if (log.isDebugEnabled())
//                        log.error(e.getMessage(), e);
//                }
//            }
//        }
//        log.debug("Allocated all Resources for vnfr: " + virtualNetworkFunctionRecord);
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

        log("DEPENDENCIES", dependency.toString());
        log("TARGET", dependency.getTarget());
        log("ID TYPE", dependency.getIdType());
        log("PARAMETER", dependency.getParameters());
        log("VNFC PARAMETER", dependency.getVnfcParameters());

        log.info(
                "VirtualNetworkFunctionRecord VERSION is: " + virtualNetworkFunctionRecord.getHbVersion());
        log.info("executing modify for VNFR: " + virtualNetworkFunctionRecord.getName());

        log.info("Got dependency: " + dependency);
        log.info("Parameters are: ");
        for (Map.Entry<String, DependencyParameters> entry : dependency.getParameters().entrySet()) {
            log.info("Source type: " + entry.getKey());
            log.info("Parameters: " + entry.getValue().getParameters());
        }

        return virtualNetworkFunctionRecord;
    }

    @Override
    public void upgradeSoftware() {

    }

    @Override
    public VirtualNetworkFunctionRecord terminate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        log.info("Terminating vnfr with id " + virtualNetworkFunctionRecord.getId());

        try {
            String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tbXprdmQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjIwMTRkMzE3LTI2YmEtMTFlOC1hNzE1LTUwMjk2MDI0MDY3OCIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.YTuFvY4-u_Wz5H3hhPvLied2fk2GZ42tCbnvi72GXwTr-8eVy-tnEsgY48WEMQjFnNwuwqneBsijUxsE8zSswD6izGyLnCtJnmuH4h3hnEmcyjqmwJIZXdDFIvPU5kLEsaNEB5GGLyPDFq-BiA81gcsu-uEZvkHvEgvETaNZ0Wjvr2-JE1dLXl5_ghr6B2ueLg-hkxAT7pXsGd8tYQ3mvB-bOZ7PpGb-6Kc1HKbmIGX0lub4E3WUMUgyQBEHJGMvnB7tReNtWfNBRRykoaT9_mMDAqMHpznGSJkIl7hXtX1cLQUqWK4P8e2idZcr6gWLFhIZ9hM8AiKir7I7UjKZJA";
            String master = "https://192.168.39.253:8443";
            String str_caCert = "/home/carlos/.minikube/ca.crt";
            FileInputStream caCert = new FileInputStream(str_caCert);

            ApiClient defaultClient = new ApiClient();
            defaultClient.setBasePath(master);
            defaultClient.setSslCaCert(caCert);
            defaultClient.setApiKey("Bearer " + token);
            Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api

            AppsV1beta1Api apiInstance = new AppsV1beta1Api();

            for (VirtualDeploymentUnit virtualDeploymentUnit : virtualNetworkFunctionRecord.getVdu()) {

                String name = virtualDeploymentUnit.getName();
                String namespace = "default";
                V1DeleteOptions body = new V1DeleteOptions();
//                body.setApiVersion("extensions/v1beta1");
//                body.setKind("DeleteOptions");
//                body.setPropagationPolicy("Foreground");
//                body.setGracePeriodSeconds((Long) 0);

                String pretty = "false";
                Integer gracePeriodSeconds = null;
                Boolean orphanDependents = null;
                String propagationPolicy = null;

                log("Deleting Name:", name);
                log("Deleting namespace:", namespace);
                log("Deleting Body:", body);


                //Todo: Deletion is not working properly yet.
                V1Status response = apiInstance.deleteNamespacedDeployment(name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);
                log("Response Delete Deployment ", response);
//
//                try {
//                    V1Status response = apiInstance.deleteNamespacedDeployment(name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);
//                } catch ()
            }
        } catch (ApiException e) {
            e.printStackTrace();
        }
//        NFVORequestor nfvoRequestor = new NFVORequestor("admin", "openbaton", "default", false, "127.0.0.1", "8080", "1");
////        NfvoRequestorBuilder nfvoRequestor = NfvoRequestorBuilder.create();
//        for (VirtualDeploymentUnit vdu : virtualNetworkFunctionRecord.getVdu()) {
//            Set<VNFCInstance> vnfciToRem = new HashSet<>();
//            List<BaseVimInstance> vimInstances = new ArrayList<>();
//            BaseVimInstance vimInstance = null;
//            try {
//                vimInstances = nfvoRequestor.getVimInstanceAgent().findAll();
//            } catch (SDKException e) {
//                log.error(e.getMessage(), e);
//            }
//            for (BaseVimInstance vimInstanceFind : vimInstances) {
//                if (vdu.getVimInstanceName().contains(vimInstanceFind.getName())) {
//                    vimInstance = vimInstanceFind;
//                    break;
//                }
//            }
//            for (VNFCInstance vnfcInstance : vdu.getVnfc_instance()) {
//                log.debug("Releasing resources for vdu with id " + vdu.getId());
//                try {
//                    resourceManagement.release(vnfcInstance, vimInstance);
//                } catch (VimException e) {
//                    log.error(e.getMessage(), e);
//                    throw new RuntimeException(e.getMessage(), e);
//                }
//                vnfciToRem.add(vnfcInstance);
//                log.debug("Released resources for vdu with id " + vdu.getId());
//            }
//            vdu.getVnfc_instance().removeAll(vnfciToRem);
//        }
//        log.info("Terminated vnfr with id " + virtualNetworkFunctionRecord.getId());
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
        //resourceManagement = (ResourceManagement) context.getBean("kubernetes", 19345, "15672");
        //Using the openstack-plugin directly
        client = (VimDriverCaller) ((RabbitPluginBroker) context.getBean("rabbitPluginBroker")).getVimDriverCaller("127.0.0.1", "admin", "openbaton", 5672, "/", "docker", "kubernetes","15672", 300);
    }

    private void createPod(VirtualNetworkFunctionRecord vnfr) {
        //Creating NamespacedPod
        String pod_name, cluster_name, image_name, container_name;
        try {
            ApiClient client = Config.defaultClient();
            Configuration.setDefaultApiClient(client);

            CoreV1Api api = new CoreV1Api();

            String str_ns = "default"; // String | object name and auth scope, such as for teams and projects
            String pretty = "pretty_example"; // String | If 'true', then the output is pretty printed.
            V1Pod pod = new V1Pod();
            pod.setApiVersion("v1");
            pod.setKind("Pod");
            V1ObjectMeta metadata = new V1ObjectMeta();
            pod.setMetadata(metadata.name("iperf-client").clusterName("minikube"));
            //TODO: Check all VDU and create a container for each VDU.
            V1Container container = new V1Container().image("ubuntu-14.04-server-cloudimg").name("iperf-client");
            List<V1Container> list_ct = new ArrayList<>();
            list_ct.add(container);
            pod.setSpec(new V1PodSpec().containers(list_ct));
            try {
                V1Pod result2 = api.createNamespacedPod(str_ns, pod, pretty);
//                System.out.println(result2);
            } catch (ApiException e) {
                System.err.println("Exception when calling CoreV1Api#createNamespacedPod");
                e.printStackTrace();
            }

        } catch (IOException e) {
            System.err.println("Exception when creating Client");
            e.printStackTrace();
        }
    }

    private void createDeployment(VirtualNetworkFunctionRecord vnfr, Map<String, Collection<BaseVimInstance>> vimInstances) {
        log("Creating Kubernetes Deployment", "*****");

        String version = "apps/v1beta1";
        String kind = "Deployment";
        String name = "";
        Set<String> image = new HashSet<>();
        Object [] vm_image = null;
        Set<VNFDConnectionPoint> network = new HashSet<>();
        Integer scale_in_out = 0;

        Map<String, String> label = new HashMap<>();


        //Todo: Adapt the method for creating Deployment to this loop
        for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
            log("Creating Kubernetes Deployment************", vdu.getName());
            name = vdu.getName();
            label.put("app",name);
            image = vdu.getVm_image();
            scale_in_out = vdu.getScale_in_out();
            for (VNFComponent vnfc : vdu.getVnfc()) {
                network = vnfc.getConnection_point();
            }
        }
//
//        for (Map.Entry<String, Collection<BaseVimInstance>> entry : vimInstances.entrySet()) {
//            log("VIM EntrySet", entry);
//        }

        vm_image = image.toArray();

        try {

            //Todo: Create a method for handling authentication
            String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tbXprdmQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjIwMTRkMzE3LTI2YmEtMTFlOC1hNzE1LTUwMjk2MDI0MDY3OCIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.YTuFvY4-u_Wz5H3hhPvLied2fk2GZ42tCbnvi72GXwTr-8eVy-tnEsgY48WEMQjFnNwuwqneBsijUxsE8zSswD6izGyLnCtJnmuH4h3hnEmcyjqmwJIZXdDFIvPU5kLEsaNEB5GGLyPDFq-BiA81gcsu-uEZvkHvEgvETaNZ0Wjvr2-JE1dLXl5_ghr6B2ueLg-hkxAT7pXsGd8tYQ3mvB-bOZ7PpGb-6Kc1HKbmIGX0lub4E3WUMUgyQBEHJGMvnB7tReNtWfNBRRykoaT9_mMDAqMHpznGSJkIl7hXtX1cLQUqWK4P8e2idZcr6gWLFhIZ9hM8AiKir7I7UjKZJA";
            String master = "https://192.168.39.253:8443";
            String str_caCert = "/home/carlos/.minikube/ca.crt";
            FileInputStream caCert = new FileInputStream(str_caCert);

            ApiClient defaultClient = new ApiClient();
            defaultClient.setBasePath(master);
            defaultClient.setSslCaCert(caCert);
            defaultClient.setApiKey("Bearer " + token);
            Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api

            AppsV1beta1Api apiInstance = new AppsV1beta1Api(); //Creating obj for requesting information via API


            //Creating deployment
            AppsV1beta1Deployment deploy = new AppsV1beta1Deployment();
            deploy.setApiVersion(version);
            deploy.setKind(kind);
                V1ObjectMeta meta = new V1ObjectMeta();
                    meta.setName(name);
//                    Map<String, String> label1 = new HashMap<>();
//                    label1.put("app", "iperf-client");
                    meta.setLabels(label);
    //            meta.setClusterName("minikube");
    //            meta.setNamespace("default");
            deploy.setMetadata(meta);
                AppsV1beta1DeploymentSpec spec = new AppsV1beta1DeploymentSpec();
                    spec.setReplicas(scale_in_out);
                        V1LabelSelector selector = new V1LabelSelector();
                            selector.setMatchLabels(label);
                    spec.setSelector(selector);
    //                V1PodTemplate template = new V1PodTemplate();
                        V1PodTemplateSpec temp_spec = new V1PodTemplateSpec();
                            V1ObjectMeta temp_meta = new V1ObjectMeta();
                                temp_meta.setLabels(label);
                            temp_spec.setMetadata(temp_meta);
                            V1PodSpec pod_spec = new V1PodSpec();
                                List<V1Container> containers = new ArrayList<>();
                                    V1Container container = new V1Container();
                                        container.setName(name);
                                        container.setImagePullPolicy("Never");
                                        container.setImage(vm_image[0].toString());
//                                        List<V1ContainerPort> ports = new ArrayList<>();
//                                            V1ContainerPort port = new V1ContainerPort();
//                                            port.setContainerPort(80);
//                                            ports.add(port);
//                                        container.setPorts(ports);
                                    containers.add(container);
                                pod_spec.containers(containers);
                            temp_spec.setSpec(pod_spec);
                    spec.setTemplate(temp_spec);
            deploy.setSpec(spec);


            try {
                AppsV1beta1Deployment result = apiInstance.createNamespacedDeployment("default", deploy, "pretty");
                System.out.println("Result Deployment: " + result);
            } catch (ApiException e) {
                System.err.println("Exception when calling AppsV1beta1Api#createNamespacedDeployment");
                e.printStackTrace();
            }
        } catch (IOException e) { // Exception e
            e.printStackTrace();
            logger.error(e.getMessage(), e);

            Throwable[] suppressed = e.getSuppressed();
            if (suppressed != null) {
                for (Throwable t : suppressed) {
                    logger.error(t.getMessage(), t);
                }
            }
        }
    }

    private static void log(String action, Object obj) {
        logger.info("{}: {}", action, obj);
    }
}
