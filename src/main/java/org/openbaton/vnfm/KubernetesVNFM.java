package org.openbaton.vnfm;

import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1beta1Api;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
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
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.*;

public class KubernetesVNFM extends AbstractVnfmSpringAmqp {

    @Autowired
    private ConfigurableApplicationContext context;

    private ResourceManagement resourceManagement;

    private VimDriverCaller client;

    private static Map<String, NetworkService> networkServiceMap;

    public KubernetesVNFM() {
        super();
        networkServiceMap = new HashMap<>();
    }



    public static String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNmx0YnQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjEzZTM1Y2I2LTI2Y2MtMTFlOC1iNWQ1LTkwZTkyNTcyNjExYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.jrj5-XLv8Qcali4ZhJWMeHKuHE8_RrsYhe-Svf6X2iccCj7j83kUp9_xTNisuhcWulV20esHxDFi48du5e5JoMCYx9r4dVL_q83fHlOMyMTZ97Un9r7QcwkrwHftvbl9WZdrhkK2LsIg02gLKUtdPrdMOMGnlnSb40aaUEL4zgHdPjKLmI31_bkbkC4opdSf7T05zCKSf5NYc_Sw7MDtmzIITjGMDsJ_LkX5cIXOMqT721FTSYtA9bwO0xvlJ5rFFmumGP8zTAavNE-spNfUIukIfP_-QCkSf_schC7KDrHz5jesFAVorx2KdEeFMA6dXreHqTC4Ue81U6s11tSgLA";
    public static String master = "https://192.168.39.231:8443";

    private static Logger logger = LoggerFactory.getLogger(KubernetesVNFM.class);

    public static void main(String[] args){
        SpringApplication.run(KubernetesVNFM.class);
    }

    @Override
    public VirtualNetworkFunctionRecord instantiate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, Object scripts, Map<String, Collection<BaseVimInstance>> vimInstances) throws Exception {

        NetworkService networkService;// = new NetworkService();

        log("INSTANCIATE", "New Instantiation");
//        log("VNFR::", virtualNetworkFunctionRecord);
        log("Scripts::", scripts);

        System.out.println("Trying to print something");
        log("VNFR Name::", virtualNetworkFunctionRecord.getName());
        log("VNFR Id::", virtualNetworkFunctionRecord.getId());

        //////////////////////////////////////////

        networkService = getNetworkService(virtualNetworkFunctionRecord.getId());
        networkService.addDeploys(virtualNetworkFunctionRecord.getName());
        networkService.addVnfr(virtualNetworkFunctionRecord);
        networkService.setVnfStatus(virtualNetworkFunctionRecord.getName(), "instantiated");

        networkServiceMap.remove(virtualNetworkFunctionRecord.getId());
        networkServiceMap.put(virtualNetworkFunctionRecord.getId(), networkService);

        for (Map.Entry<String, NetworkService> item : networkServiceMap.entrySet()) {
            log("Map Key", item.getKey());
            log("Map Value", item.getValue());
        }


//        createDeployment(virtualNetworkFunctionRecord, vimInstances);






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

        NetworkService networkService;// = new NetworkService();

        log.info(
                "Received NFVO Message: "
                        + "MODIFY**************************************************"
                        + " for VNFR "
                        + virtualNetworkFunctionRecord.getName()
                        + " and following vnfrDep: \n"
                        + dependency.toString());

//        log("DEPENDENCIES", dependency.toString());
//        log("TARGET", dependency.getTarget());
//        log("ID TYPE", dependency.getIdType());
//        log("PARAMETER", dependency.getParameters());
//        log("VNFC PARAMETER", dependency.getVnfcParameters());
//
//        log.info(
//                "VirtualNetworkFunctionRecord VERSION is: " + virtualNetworkFunctionRecord.getHbVersion());
//        log.info("executing modify for VNFR: " + virtualNetworkFunctionRecord.getName());
//
//        log.info("Got dependency: " + dependency);
//        log.info("Parameters are: ");
        for (Map.Entry<String, DependencyParameters> entry : dependency.getParameters().entrySet()) {
            log.info("Source type: " + entry.getKey());
            log.info("Parameters: " + entry.getValue().getParameters());
        }

        ////////////////////////////////
        String vnfr_id = virtualNetworkFunctionRecord.getId();
        networkService = getNetworkService(vnfr_id);


        // fill the dependency map
        for (Map.Entry<String, DependencyParameters> entry :
                dependency.getParameters().entrySet()) {
            String sourceType = entry.getKey();
            String sourceName = "";
            for (Map.Entry<String, String> nameTypeEntry : dependency.getIdType().entrySet()) {
                if (nameTypeEntry.getValue().equals(sourceType)) sourceName = nameTypeEntry.getKey();
            }
            DependencyParameters dependencyParameters = entry.getValue();
            List<String> parameters = new LinkedList<>();
            for (Map.Entry<String, String> pe : dependencyParameters.getParameters().entrySet()) {
                parameters.add(pe.getKey());
            }
            Map<String, List<String>> sourceParams = new HashMap<>();
            sourceParams.put(sourceName, parameters);
            Map<String, Map<String, List<String>>> targetSourceParams = new HashMap<>();
            targetSourceParams.put(virtualNetworkFunctionRecord.getName(), sourceParams);

            networkService.addDependency(
                    virtualNetworkFunctionRecord.getName(), sourceName, parameters);
        }

        networkService.setVnfStatus(virtualNetworkFunctionRecord.getName(), "modified");

        networkServiceMap.remove(vnfr_id);
        networkServiceMap.put(vnfr_id, networkService);

        for (Map.Entry<String, NetworkService> item : networkServiceMap.entrySet()) {
            log("Map Key", item.getKey());
            log("Map Value", item.getValue());
        }

        return virtualNetworkFunctionRecord;
    }

    @Override
    public void upgradeSoftware() {

    }

    @Override
    public VirtualNetworkFunctionRecord terminate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        log.info("Terminating vnfr with id " + virtualNetworkFunctionRecord.getId());
        NetworkService networkService = getNetworkService(virtualNetworkFunctionRecord.getId());

        String str_caCert = "/home/carlos/.minikube/ca.crt";
        FileInputStream caCert = new FileInputStream(str_caCert);

        ApiClient defaultClient = new ApiClient();
        defaultClient.setBasePath(master);
        defaultClient.setSslCaCert(caCert);
        defaultClient.setApiKey("Bearer " + token);
        log("ApiClient", defaultClient);
//            ApiClient client = Config.from
        Configuration.setDefaultApiClient(defaultClient);

        ExtensionsV1beta1Api apiInstance = new ExtensionsV1beta1Api(); //Creating obj for requesting information via API

        log("Deleting Network:", networkService);
        log("Deleting Deployment:", networkService.getDeploys().get(0));

        try {
            V1Status response = apiInstance.deleteNamespacedDeployment(networkService.getDeploys().get(0)+"-1", "default", new V1DeleteOptions().gracePeriodSeconds(0L).propagationPolicy("Foreground"), null, null, null, null);
            log("Response", response);
        } catch (ApiException e) {
            System.err.println("Execution of deleteNamespacedDeployment");
        } catch (JsonSyntaxException e) {
            System.err.println("Execution of IllegalStateException");
        }

        networkServiceMap.remove(networkService);
//        try {
////            String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNmx0YnQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjEzZTM1Y2I2LTI2Y2MtMTFlOC1iNWQ1LTkwZTkyNTcyNjExYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.jrj5-XLv8Qcali4ZhJWMeHKuHE8_RrsYhe-Svf6X2iccCj7j83kUp9_xTNisuhcWulV20esHxDFi48du5e5JoMCYx9r4dVL_q83fHlOMyMTZ97Un9r7QcwkrwHftvbl9WZdrhkK2LsIg02gLKUtdPrdMOMGnlnSb40aaUEL4zgHdPjKLmI31_bkbkC4opdSf7T05zCKSf5NYc_Sw7MDtmzIITjGMDsJ_LkX5cIXOMqT721FTSYtA9bwO0xvlJ5rFFmumGP8zTAavNE-spNfUIukIfP_-QCkSf_schC7KDrHz5jesFAVorx2KdEeFMA6dXreHqTC4Ue81U6s11tSgLA";
////            String master = "https://192.168.39.231:8443";
//            String str_caCert = "/home/carlos/.minikube/ca.crt";
//            FileInputStream caCert = new FileInputStream(str_caCert);
//
//            ApiClient defaultClient = new ApiClient();
//            defaultClient.setBasePath(master);
//            defaultClient.setSslCaCert(caCert);
//            defaultClient.setApiKey("Bearer " + token);
//            Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api
//
//            AppsV1beta1Api apiInstance = new AppsV1beta1Api();
//
//            for (VirtualDeploymentUnit virtualDeploymentUnit : virtualNetworkFunctionRecord.getVdu()) {
//
//                String name = virtualDeploymentUnit.getName();
//                String namespace = "default";
//                V1DeleteOptions body = new V1DeleteOptions();
////                body.setApiVersion("extensions/v1beta1");
////                body.setKind("DeleteOptions");
////                body.setPropagationPolicy("Foreground");
////                body.setGracePeriodSeconds((Long) 0);
//
//                String pretty = "false";
//                Integer gracePeriodSeconds = null;
//                Boolean orphanDependents = null;
//                String propagationPolicy = "Background";
//
//                log("Deleting Name:", name);
//                log("Deleting namespace:", namespace);
//                log("Deleting Body:", body);
//
//
//                //Todo: Deletion is not working properly yet.
//                V1Status response = apiInstance.deleteNamespacedDeployment(name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);
//                log("Response Delete Deployment ", response);
////
////                try {
////                    V1Status response = apiInstance.deleteNamespacedDeployment(name, namespace, body, pretty, gracePeriodSeconds, orphanDependents, propagationPolicy);
////                } catch ()
//            }
//        } catch (ApiException e) {
//            e.printStackTrace();
//        }
//
        log.info("Terminated vnfr with id " + virtualNetworkFunctionRecord.getId());
        return virtualNetworkFunctionRecord;
    }

    @Override
    public void handleError(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) {

    }

    @Override
    public VirtualNetworkFunctionRecord start(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        log("START:", virtualNetworkFunctionRecord.getName());

        NetworkService temp_ns;
        String env = "";

//        Integer inst_cont = 0;
//        for (Map.Entry<String, NetworkService> item : networkServiceMap.entrySet()) {
//            for (Map.Entry<String, String> status : item.getValue().getVnfStatusMap().entrySet()) {
//                if (status.getValue().equals("instanciated")) {
//                    inst_cont++;
//                }
//            }
//        }
//
//        log("Instantiate count: ", inst_cont);
//
//


        temp_ns = getNetworkService(virtualNetworkFunctionRecord.getId());
        log("I should create INSTANTIATED Deployment::::", temp_ns.getDeploys().get(0));
        temp_ns.setVnfStatus(temp_ns.getDeploys().get(0), "started");
        networkServiceMap.remove(temp_ns);
        networkServiceMap.put(temp_ns.getId(), temp_ns);
        createDeployment(temp_ns);

//        for (Map.Entry<String, NetworkService> item : networkServiceMap.entrySet()) {
//            boolean deployed = false;
//
//            temp_ns = getNetworkService(item.getKey());
//            log("Map Key", item.getKey());
//            log("Map Value", item.getValue());
//            log("NS Status::", item.getValue().getVnfStatusMap());
//            log("temp_ns::", temp_ns);
//            for (Map.Entry<String, String> status : item.getValue().getVnfStatusMap().entrySet()) {
//                if (!status.getValue().equals("started")) {
//                    if (!status.getValue().equals("modified")) {
//                        log("I should create INSTANTIATED Deployment::::", status.getKey());
//                        temp_ns.setVnfStatus(item.getValue().getDeploys().get(0), "started");
//                        networkServiceMap.remove(temp_ns);
//                        networkServiceMap.put(item.getKey(), temp_ns);
//                        createDeployment(temp_ns);
//                        deployed = true;
//                        inst_cont --;
//                    }
//                }
//            }
//            if (deployed) {
//                break;
//            } else {
//                if (inst_cont == 0) {
//                    for (Map.Entry<String, String> status : item.getValue().getVnfStatusMap().entrySet()) {
//                        if (status.getValue().equals("modified")) {
//                            log("I should create MODIFIED Deployment::::", status.getKey());
//                            temp_ns.setVnfStatus(item.getValue().getDeploys().get(0), "started");
//                            networkServiceMap.remove(temp_ns);
//                            networkServiceMap.put(item.getKey(), temp_ns);
//                            env = generateEnvVariable(temp_ns.getDependencies());
//                            log("MOD-Env:", env);
//                            createDeployment(temp_ns);
////                            Map<String, String> ls_var = new HashMap<>();
////                            ls_var = addEnvVariable(temp_ns);
//
////                            deployed = true;
//                        }
//                    }
//                }
//            }
//        }

        for (Map.Entry<String, NetworkService> item : networkServiceMap.entrySet()) {
            log("Map Key", item.getKey());
            log("Map Value", item.getValue());
        }

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

    private String getPodIP(String nameContainer, String namespace) {
        boolean not_deployed = true;
        while (not_deployed) {
            try {
                //Todo: Create a method for handling authentication
                //String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNmx0YnQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjEzZTM1Y2I2LTI2Y2MtMTFlOC1iNWQ1LTkwZTkyNTcyNjExYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.jrj5-XLv8Qcali4ZhJWMeHKuHE8_RrsYhe-Svf6X2iccCj7j83kUp9_xTNisuhcWulV20esHxDFi48du5e5JoMCYx9r4dVL_q83fHlOMyMTZ97Un9r7QcwkrwHftvbl9WZdrhkK2LsIg02gLKUtdPrdMOMGnlnSb40aaUEL4zgHdPjKLmI31_bkbkC4opdSf7T05zCKSf5NYc_Sw7MDtmzIITjGMDsJ_LkX5cIXOMqT721FTSYtA9bwO0xvlJ5rFFmumGP8zTAavNE-spNfUIukIfP_-QCkSf_schC7KDrHz5jesFAVorx2KdEeFMA6dXreHqTC4Ue81U6s11tSgLA";
//            String master = "https://192.168.39.231:8443";
                String str_caCert = "/home/carlos/.minikube/ca.crt";
                FileInputStream caCert = new FileInputStream(str_caCert);

                ApiClient defaultClient = new ApiClient();
                defaultClient.setBasePath(master);
                defaultClient.setSslCaCert(caCert);
                defaultClient.setApiKey("Bearer " + token);
                Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api

                if (namespace == null)
                    namespace = "default";

                CoreV1Api api = new CoreV1Api();

                try {
                    V1PodList result = api.listNamespacedPod(namespace, null, null,null,null,null,null,null,null,null);
                    for (V1Pod item : result.getItems()) {
                        for (V1Container con : item.getSpec().getContainers()) {
                            if (con.getName().contains(nameContainer)) {
                                log("Name", item.getMetadata().getName());
                                log("IP", item.getStatus().getPodIP());
                                if (item.getStatus().getPodIP() != null)
                                    return item.getStatus().getPodIP();
                            }
                        }
                    }
                } catch (ApiException e) {
                    System.err.println("Exception when calling CoreV1Api#listNamespacedPod");
                    e.printStackTrace();
                }

            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }
        }
        return null;
    }

    private String generateEnvVariable(Map<String, Map<String, List<String>>> dependency) {
        String env = "";

        for (Map.Entry<String, Map<String, List<String>>> entry : dependency.entrySet()) {
            log("1st Level:", entry);
            for (Map.Entry<String, List<String>> entry2 : entry.getValue().entrySet()) {
                log("2nd Level:", entry2);
                for (String list : entry2.getValue()) {
                    log("3rd Level key", entry2.getKey());
                    log("3rd Level value", list);
                    env = "SERVER_" + list.toUpperCase();
                }
            }
        }

        return env;
    }

    private Map<String, String> addEnvVariable(Map<String, Map<String, List<String>>> dependency) {
        String env = "";
        Map<String, String> var = new HashMap<>();
        String value; //this value should be fetched previously by executing the an instruction which get the IP address from the source Pod.

        for (Map.Entry<String, Map<String, List<String>>> entry : dependency.entrySet()) {
            for (Map.Entry<String, List<String>> entry2 : entry.getValue().entrySet()) {
                for (String list : entry2.getValue()) {
                    //Todo: Change the "SERVER" for an according value like entry2.getKey(), but replacing "-" for "_".
                    //Todo:      This value has to match to variable in the container's image.
                    env = "SERVER_" + list.toUpperCase();
                    value = getPodIP(entry2.getKey(), null);
                    var.put(env, value);
                }
            }
        }
        log("**********VAR*********", var);

        return var;
    }

//    private String getPodIP(String nameContainer, String namespace) {
//        try {
//            //Todo: Create a method for handling authentication
//            //String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNmx0YnQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjEzZTM1Y2I2LTI2Y2MtMTFlOC1iNWQ1LTkwZTkyNTcyNjExYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.jrj5-XLv8Qcali4ZhJWMeHKuHE8_RrsYhe-Svf6X2iccCj7j83kUp9_xTNisuhcWulV20esHxDFi48du5e5JoMCYx9r4dVL_q83fHlOMyMTZ97Un9r7QcwkrwHftvbl9WZdrhkK2LsIg02gLKUtdPrdMOMGnlnSb40aaUEL4zgHdPjKLmI31_bkbkC4opdSf7T05zCKSf5NYc_Sw7MDtmzIITjGMDsJ_LkX5cIXOMqT721FTSYtA9bwO0xvlJ5rFFmumGP8zTAavNE-spNfUIukIfP_-QCkSf_schC7KDrHz5jesFAVorx2KdEeFMA6dXreHqTC4Ue81U6s11tSgLA";
////            String master = "https://192.168.39.231:8443";
//            String str_caCert = "/home/carlos/.minikube/ca.crt";
//            FileInputStream caCert = new FileInputStream(str_caCert);
//
//            ApiClient defaultClient = new ApiClient();
//            defaultClient.setBasePath(master);
//            defaultClient.setSslCaCert(caCert);
//            defaultClient.setApiKey("Bearer " + token);
//            Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api
//
//            if (namespace == null)
//                namespace = "default";
//
//            CoreV1Api api = new CoreV1Api();
//
//            try {
////            String result = api.connectGetNamespacedPodExec(name, namespace, command, cont, stderr, stdin, stdout, tty);
//                V1PodList result = api.listNamespacedPod(namespace, null, null,null,null,null,null,null,null,null);
//                for (V1Pod item : result.getItems()) {
//                    for (V1Container con : item.getSpec().getContainers()) {
//                        if (con.getName().contains(nameContainer)) {
//                            log("Name", item.getMetadata().getName());
//                            log("IP", item.getStatus().getPodIP());
//                            return item.getStatus().getPodIP();
//                        }
//                    }
//                }
//            } catch (ApiException e) {
//                System.err.println("Exception when calling CoreV1Api#listNamespacedPod");
//                e.printStackTrace();
//            }
//
//        } catch (FileNotFoundException e) {
//            e.printStackTrace();
//        }
//        return null;
//    }

    private void createDeployment(NetworkService networkService) {  //, Map<String, Collection<BaseVimInstance>> vimInstances) {
        log("Creating Kubernetes Deployment", "*****");
//
        String version = "apps/v1beta1";
        String kind = "Deployment";
        String name = "";
        Set<String> image = new HashSet<>();
        Object [] vm_image; // = null;
        Set<VNFDConnectionPoint> network = new HashSet<>();
        Integer scale_in_out = 0;

        Map<String, String> label = new HashMap<>();


        //Todo: Adapt the method for creating Deployment to this loop
        for (VirtualNetworkFunctionRecord vnfr : networkService.getVnfrList()) {
            for (VirtualDeploymentUnit vdu : vnfr.getVdu()) {
                log("Creating Kubernetes Deployment************", vdu.getName());
                name = vdu.getName();
                label.put("app", name);
                image = vdu.getVm_image();
                scale_in_out = vdu.getScale_in_out();
                for (VNFComponent vnfc : vdu.getVnfc()) {
                    network = vnfc.getConnection_point();
                }
            }
        }
//
//        for (Map.Entry<String, Collection<BaseVimInstance>> entry : vimInstances.entrySet()) {
//            log("VIM EntrySet", entry);
//        }

        vm_image = image.toArray();

        try {

            //Todo: Create a method for handling authentication
            //String token = "eyJhbGciOiJSUzI1NiIsInR5cCI6IkpXVCJ9.eyJpc3MiOiJrdWJlcm5ldGVzL3NlcnZpY2VhY2NvdW50Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9uYW1lc3BhY2UiOiJkZWZhdWx0Iiwia3ViZXJuZXRlcy5pby9zZXJ2aWNlYWNjb3VudC9zZWNyZXQubmFtZSI6ImRlZmF1bHQtdG9rZW4tNmx0YnQiLCJrdWJlcm5ldGVzLmlvL3NlcnZpY2VhY2NvdW50L3NlcnZpY2UtYWNjb3VudC5uYW1lIjoiZGVmYXVsdCIsImt1YmVybmV0ZXMuaW8vc2VydmljZWFjY291bnQvc2VydmljZS1hY2NvdW50LnVpZCI6IjEzZTM1Y2I2LTI2Y2MtMTFlOC1iNWQ1LTkwZTkyNTcyNjExYSIsInN1YiI6InN5c3RlbTpzZXJ2aWNlYWNjb3VudDpkZWZhdWx0OmRlZmF1bHQifQ.jrj5-XLv8Qcali4ZhJWMeHKuHE8_RrsYhe-Svf6X2iccCj7j83kUp9_xTNisuhcWulV20esHxDFi48du5e5JoMCYx9r4dVL_q83fHlOMyMTZ97Un9r7QcwkrwHftvbl9WZdrhkK2LsIg02gLKUtdPrdMOMGnlnSb40aaUEL4zgHdPjKLmI31_bkbkC4opdSf7T05zCKSf5NYc_Sw7MDtmzIITjGMDsJ_LkX5cIXOMqT721FTSYtA9bwO0xvlJ5rFFmumGP8zTAavNE-spNfUIukIfP_-QCkSf_schC7KDrHz5jesFAVorx2KdEeFMA6dXreHqTC4Ue81U6s11tSgLA";
//            String master = "https://192.168.39.231:8443";
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
                                        //Adding Environment Variable for POD_NAME and POD_IP
                                        List<V1EnvVar> list_env = new ArrayList<>();
                                        V1EnvVar var_ip = new V1EnvVar();
                                        var_ip.setName("MY_POD_NAME");
                                        V1EnvVarSource varSource = new V1EnvVarSource();
                                        V1ObjectFieldSelector fieldSelector = new V1ObjectFieldSelector();
                                        fieldSelector.setFieldPath("metadata.name");
                                        varSource.setFieldRef(fieldSelector);
                                        var_ip.setValueFrom(varSource);
                                        list_env.add(var_ip);
                                        V1EnvVar var_ip2 = new V1EnvVar();
                                        var_ip2.setName("MY_POD_IP");
                                        V1EnvVarSource varSource2 = new V1EnvVarSource();
                                        V1ObjectFieldSelector fieldSelector2 = new V1ObjectFieldSelector();
                                        fieldSelector2.setFieldPath("status.podIP");
                                        varSource2.setFieldRef(fieldSelector2);
                                        var_ip2.setValueFrom(varSource2);
                                        list_env.add(var_ip2);
                                        //Adding Environment Variable according to the Dependencies
                                        Map<String, String> var = addEnvVariable(networkService.getDependencies());
                                        for (Map.Entry<String, String> entryVar : var.entrySet()) {
                                            V1EnvVar var_extra = new V1EnvVar();
                                            var_extra.setName(entryVar.getKey());
                                            var_extra.setValue(entryVar.getValue());
                                            list_env.add(var_extra);
                                        }
                                        container.setEnv(list_env);
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

    /**
     * Get a NetworkService object from the networkServiceMap. If it does not contain the requested
     * NetworkService yet, create and add it.
     *
     * @param id
     * @return the requested NetworkService
     */
    private NetworkService getNetworkService(String id) {
        if (networkServiceMap.containsKey(id)) return networkServiceMap.get(id);
        else {
            NetworkService networkService = new NetworkService();
            networkServiceMap.put(id, networkService);
            networkService.setId(id);
            return networkService;
        }
    }

    private static void log(String action, Object obj) {
        logger.info("{}: {}", action, obj);
    }
}
