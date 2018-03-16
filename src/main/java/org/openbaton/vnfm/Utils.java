package org.openbaton.vnfm;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Container;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.models.V1PodList;
import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.catalogue.nfvo.viminstances.DockerVimInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public class Utils {
    private static Logger logger = LoggerFactory.getLogger(Utils.class);

    static ApiClient authenticate(DockerVimInstance vimInstance) {
        logger.info("Authenticating to VIM [{}] Type:[{}] URL:[{}]", vimInstance.getName(), vimInstance.getType(), vimInstance.getAuthUrl());

        ApiClient defaultClient = new ApiClient();

        try {
            defaultClient.setBasePath(vimInstance.getAuthUrl());
            FileInputStream caCert = new FileInputStream(vimInstance.getCa());
            String token = new String(Files.readAllBytes(Paths.get(vimInstance.getDockerKey())));
            token = Optional.ofNullable(token)
                    .filter(str -> str.length() != 0)
                    .map(str -> str.substring(0, str.length() - 1))
                    .orElse(token);
            defaultClient.setSslCaCert(caCert);
            defaultClient.setApiKey("Bearer " + token);
        } catch (IOException e) {
            System.err.println("Exception when API Client Authenticating");
            e.printStackTrace();
        }

        return defaultClient;
    }

    static Map<String, String> addEnvVariable(//Map<String, Map<String, List<String>>> dependency,
                                               NetworkService networkService) {
        String env = "";
        Map<String, String> var = new HashMap<>();
        String value;

        for (Map.Entry<String, Map<String, List<String>>> entry : networkService.getDependencies().entrySet()) {
            for (Map.Entry<String, List<String>> entry2 : entry.getValue().entrySet()) {
                for (String list : entry2.getValue()) {
                    //Todo: Change the "SERVER" for an according value like entry2.getKey(), but replacing "-" for "_".
                    //Todo:      This value has to match to variable in the container's image.
                    env = "SERVER_" + list.toUpperCase();
                    value = getPodIP(entry2.getKey(), null, networkService);
                    var.put(env, value);
                }
            }
        }

        return var;
    }

    static String getPodIP(String nameContainer, String namespace, NetworkService networkService) {
        boolean not_deployed = true;

        ApiClient defaultClient;
        if (namespace == null)
            namespace = "default";


        while (not_deployed) {
            defaultClient = authenticate(networkService.getDockerVimInstance());
            Configuration.setDefaultApiClient(defaultClient); //Setting Kubernetes client as Default one. Necessary for the CoreV1Api

            CoreV1Api api = new CoreV1Api();

            try {
                V1PodList result = api.listNamespacedPod(namespace, null, null, null, null, null, null, null, null, null);
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
        }
        return null;
    }

    private static void log(String action, Object obj) {
        logger.info("{}: {}", action, obj);
    }
}
