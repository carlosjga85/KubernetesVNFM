package org.openbaton.vnfm;

import com.google.gson.JsonSyntaxException;
import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.AppsV1beta1Api;
import io.kubernetes.client.apis.ExtensionsV1beta1Api;
import io.kubernetes.client.models.*;
import org.openbaton.catalogue.mano.descriptor.VNFComponent;
import org.openbaton.catalogue.mano.descriptor.VNFDConnectionPoint;
import org.openbaton.catalogue.mano.descriptor.VirtualDeploymentUnit;
import org.openbaton.catalogue.mano.record.VNFCInstance;
import org.openbaton.catalogue.mano.record.VNFRecordDependency;
import org.openbaton.catalogue.mano.record.VirtualNetworkFunctionRecord;
import org.openbaton.catalogue.nfvo.Action;
import org.openbaton.catalogue.nfvo.DependencyParameters;
import org.openbaton.catalogue.nfvo.Script;
import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.catalogue.nfvo.viminstances.DockerVimInstance;
import org.openbaton.common.vnfm_sdk.amqp.AbstractVnfmSpringAmqp;
import org.openbaton.plugin.mgmt.PluginStartup;
import org.openbaton.plugin.utils.RabbitPluginBroker;
import org.openbaton.vim.drivers.VimDriverCaller;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;

import java.io.IOException;
import java.util.*;

public class KubernetesVNFM extends AbstractVnfmSpringAmqp {

    @Autowired
    private ConfigurableApplicationContext context;

    private VimDriverCaller client;

    private static Map<String, NetworkService> networkServiceMap;

    public KubernetesVNFM() {
        super();
        networkServiceMap = new HashMap<>();
    }

    private static Logger logger = LoggerFactory.getLogger(KubernetesVNFM.class);

    public static void main(String[] args){
        SpringApplication.run(KubernetesVNFM.class);
    }

    @Override
    public VirtualNetworkFunctionRecord instantiate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord, Object scripts, Map<String, Collection<BaseVimInstance>> vimInstances) throws Exception {

        log.info("Instantiating VNFR Id [{}]", virtualNetworkFunctionRecord.getId());

        NetworkService networkService;// = new NetworkService();

        networkService = getNetworkService(virtualNetworkFunctionRecord.getId());

        for (Map.Entry<String, Collection<BaseVimInstance>> entry : vimInstances.entrySet()) {
            for (BaseVimInstance item : entry.getValue()) {
                if (item.getType().equals("docker")) {
                    DockerVimInstance kubernetes = (DockerVimInstance) item;
                    networkService.setDockerVimInstance(kubernetes);
                }
            }
        }

        networkService.addDeploys(virtualNetworkFunctionRecord.getName());
        networkService.addVnfr(virtualNetworkFunctionRecord);
        networkService.setVnfStatus(virtualNetworkFunctionRecord.getName(), "instantiated");

        networkServiceMap.remove(virtualNetworkFunctionRecord.getId());
        networkServiceMap.put(virtualNetworkFunctionRecord.getId(), networkService);

        log.info("Instantiated VNFR Id [{}]", virtualNetworkFunctionRecord.getId());
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

        NetworkService networkService;

        log.info(
                "Received NFVO Message: "
                        + "MODIFY**************************************************"
                        + " for VNFR "
                        + virtualNetworkFunctionRecord.getName()
                        + " and following vnfrDep: \n"
                        + dependency.toString());

        for (Map.Entry<String, DependencyParameters> entry : dependency.getParameters().entrySet()) {
            log.info("Source type: " + entry.getKey());
            log.info("Parameters: " + entry.getValue().getParameters());
        }

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

        log.info("Modified VNF Id [{}] " + virtualNetworkFunctionRecord.getId());

        return virtualNetworkFunctionRecord;
    }

    @Override
    public void upgradeSoftware() {

    }

    @Override
    public VirtualNetworkFunctionRecord terminate(VirtualNetworkFunctionRecord virtualNetworkFunctionRecord) throws Exception {
        log.info("Terminating vnfr with id " + virtualNetworkFunctionRecord.getId());
        NetworkService networkService = getNetworkService(virtualNetworkFunctionRecord.getId());

        ApiClient defaultClient;

        defaultClient = Utils.authenticate(networkService.getDockerVimInstance());

        Configuration.setDefaultApiClient(defaultClient);

        ExtensionsV1beta1Api apiInstance = new ExtensionsV1beta1Api(); //Creating obj for requesting information via API

        //According to the Blog of the official Kubernetes java-client, currently deletion of objects always triggers an IllegalStateException
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
        log.info("Starting VNF [{}] ", virtualNetworkFunctionRecord.getName());

        NetworkService temp_ns;
        String env = "";

        temp_ns = getNetworkService(virtualNetworkFunctionRecord.getId());
        temp_ns.setVnfStatus(temp_ns.getDeploys().get(0), "started");
        networkServiceMap.remove(temp_ns);
        networkServiceMap.put(temp_ns.getId(), temp_ns);
        createDeployment(temp_ns);

        log.info("Started VNF [{}] ", virtualNetworkFunctionRecord.getName());

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

    private void createDeployment(NetworkService networkService) {  //, Map<String, Collection<BaseVimInstance>> vimInstances) {
        log.info("Creating Kubernetes Deployment");
//
        String version = "apps/v1beta1";
        String kind = "Deployment";
        String name = "";
        Set<String> image = new HashSet<>();
        Object [] vm_image; // = null;
        Set<VNFDConnectionPoint> network = new HashSet<>();
        Integer scale_in_out = 0;

        Map<String, String> label = new HashMap<>();

        //Fetching information about the VNF to be deployed
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

        vm_image = image.toArray();

        ApiClient defaultClient;

        defaultClient = Utils.authenticate(networkService.getDockerVimInstance());

        Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api

        AppsV1beta1Api apiInstance = new AppsV1beta1Api(); //Creating obj for requesting information via API

        //Creating deployment
        AppsV1beta1Deployment deploy = new AppsV1beta1Deployment();
        deploy.setApiVersion(version);
        deploy.setKind(kind);
            V1ObjectMeta meta = new V1ObjectMeta();
                meta.setName(name);
                meta.setLabels(label);
        deploy.setMetadata(meta);
            AppsV1beta1DeploymentSpec spec = new AppsV1beta1DeploymentSpec();
                spec.setReplicas(scale_in_out);
                    V1LabelSelector selector = new V1LabelSelector();
                        selector.setMatchLabels(label);
                spec.setSelector(selector);
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
                                    Map<String, String> var = Utils.addEnvVariable(networkService);
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
        }//
        log.info("Deployment {} created in Kubernetes", networkService.getDeploys().get(0));
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
