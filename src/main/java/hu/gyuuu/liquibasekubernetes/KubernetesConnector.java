package hu.gyuuu.liquibasekubernetes;

import io.kubernetes.client.ApiClient;
import io.kubernetes.client.ApiException;
import io.kubernetes.client.Configuration;
import io.kubernetes.client.apis.CoreV1Api;
import io.kubernetes.client.models.V1Pod;
import io.kubernetes.client.util.Config;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;

@Slf4j
public class KubernetesConnector {

    public static final String POD_PHASE_PENDING = "Pending";
    public static final String POD_PHASE_RUNNING = "Running";
    public static final int HTTP_STATUS_NOT_FOUND = 404;
    private final boolean connected;
    private final String podName;
    private final String podNamespace;

    public static final class InstanceHolder {
        private static final KubernetesConnector INSTANCE = new KubernetesConnector();
    }

    public static KubernetesConnector getInstance() {
        return InstanceHolder.INSTANCE;
    }

    private KubernetesConnector() {
        podName = System.getenv().get("POD_NAME");
        podNamespace = System.getenv().get("POD_NAMESPACE");
        connected = connect();
    }

    private boolean connect() {
        try {
            ApiClient client = Config.fromCluster();

            Configuration.setDefaultApiClient(client);

            CoreV1Api api = new CoreV1Api();
            log.trace("Reading pod status, Pod name: " + podName + " Pod namespace: " + podNamespace);
            V1Pod pod = api.readNamespacedPodStatus(podName, podNamespace, "true");
            String podPhase = pod.getStatus().getPhase();
            log.trace("Pod phase:" + podPhase);
            log.trace("Connected to Kubernetes using fromCluster configuration");
            return true;
        } catch (IOException | ApiException e) {
            log.debug("Connection fail to Kubernetes cluster using fromCluster configuration");
            log.trace("Pod status read error", e);
            return false;
        }
    }

    public boolean isConnected() {
        return connected;
    }

    public String getPodName() {
        return podName;
    }

    public String getPodNamespace() {
        return podNamespace;
    }

    public boolean isCurrentPod(String podNamespace, String podName) {
        if (podNamespace == null || podName == null) {
            return false;
        }
        return podNamespace.equals(this.podNamespace) && podName.equals(this.podName);
    }

    public Boolean isPodActive(String podNamespace, String podName) {
        try {
            CoreV1Api api = new CoreV1Api();
            log.trace("Reading pod status, Pod name: " + podName + " Pod namespace: " + podNamespace);
            V1Pod pod = null;

            pod = api.readNamespacedPodStatus(podName, podNamespace, "true");
            String podPhase = pod.getStatus().getPhase();

            if(POD_PHASE_PENDING.equals(podPhase) || POD_PHASE_RUNNING.equals(podPhase)){
                log.trace("Pod is active, phase:"+podPhase);
                return true;
            } else {
                log.trace("Pod is inactive, phase:"+podPhase);
                return false;
            }
        } catch (ApiException e) {
            if(e.getCode() == HTTP_STATUS_NOT_FOUND){
                log.trace("Can't find pod");
                return false;
            }
            log.debug("Can't read Pod status:" + podNamespace + ":" + podName);
            log.trace("Pod status read error", e);
            return null;
        }
    }
}
