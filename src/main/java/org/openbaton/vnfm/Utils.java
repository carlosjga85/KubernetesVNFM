package org.openbaton.vnfm;

import io.kubernetes.client.ApiClient;
import org.openbaton.catalogue.nfvo.viminstances.BaseVimInstance;
import org.openbaton.catalogue.nfvo.viminstances.DockerVimInstance;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.FileInputStream;
import java.io.IOException;

public class Utils {
    private static Logger logger = LoggerFactory.getLogger(Utils.class);

    static ApiClient authenticate(BaseVimInstance vimInstance) {

        DockerVimInstance kubernetes = (DockerVimInstance) vimInstance;
        ApiClient defaultClient = new ApiClient();

        try {
            defaultClient.setBasePath(vimInstance.getAuthUrl());
            FileInputStream caCert = new FileInputStream(kubernetes.getCa());
            defaultClient.setSslCaCert(caCert);
            defaultClient.setApiKey("Bearer " + kubernetes.getDockerKey());
            log("ApiClient", defaultClient);
        } catch (IOException e) {
            System.err.println("Exception when API Client Authenticating");
            e.printStackTrace();
        }

        return defaultClient;
    }

    private static void log(String action, Object obj) {
        logger.info("{}: {}", action, obj);
    }
}
