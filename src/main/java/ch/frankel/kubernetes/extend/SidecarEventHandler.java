package ch.frankel.kubernetes.extend;

import io.fabric8.kubernetes.api.model.Pod;
import io.fabric8.kubernetes.client.KubernetesClient;
import io.fabric8.kubernetes.client.informers.ResourceEventHandler;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

public class SidecarEventHandler implements ResourceEventHandler<Pod> {

    private static final Logger LOGGER = LoggerFactory.getLogger(SidecarEventHandler.class);
    private static final String SIDECAR_IMAGE_NAME = "hazelcast/hazelcast:3.12.5";
    private static final String SIDECAR_POD_NAME = "hazelcast";
    private static final String NAMESPACE = "jvmoperator";

    private final KubernetesClient client;

    public SidecarEventHandler(KubernetesClient client) {
        this.client = client;
    }

    @Override
    public void onAdd(Pod pod) {
        String namespace = pod.getMetadata().getNamespace();
        if (NAMESPACE.equals(namespace) && !isSidecar(pod)) {
            if (!alreadyHasSidecar(pod)) {
                createSidecar(pod);
            } else {
                LOGGER.info("Sidecar already existing for pod " + pod.getMetadata().getName());
            }
        }
    }

    @Override
    public void onUpdate(Pod oldPod, Pod newPod) {
        // NOTHING TO DO
    }

    @Override
    public void onDelete(Pod pod, boolean stateUnknown) {
        if (isAssignedSidecar(pod)) {
            pod.getMetadata().setResourceVersion(null);
            client.pods().inNamespace(NAMESPACE).create(pod);
        }
    }

    private boolean isSidecar(Pod pod) {
        Optional<Map<String, String>> labels = Optional.ofNullable(pod.getMetadata().getLabels());
        return "true".equals(labels
                .orElse(new HashMap<>())
                .getOrDefault("sidecar", "false"));
    }

    private void createSidecar(Pod pod) {
        String podName = pod.getMetadata().getName();
        String name = SIDECAR_POD_NAME + "-" + podName;
        client.pods().inNamespace(NAMESPACE).createNew()
                .withApiVersion("v1")
                .withKind("Pod")
                .withNewMetadata()
                    .withName(name)
                    .withNamespace(pod.getMetadata().getNamespace())
                    .addNewOwnerReference()
                        .withApiVersion("v1")
                        .withKind("Pod")
                        .withName(podName)
                        .withUid(pod.getMetadata().getUid())
                    .endOwnerReference()
                    .addToLabels("sidecar", "true")
                .endMetadata()
                .withNewSpec()
                    .addNewContainer()
                        .withName(name)
                        .withImage(SIDECAR_IMAGE_NAME)
                    .endContainer()
                .endSpec()
                .done();
    }

    private boolean alreadyHasSidecar(Pod pod) {
        return client.pods().inNamespace(NAMESPACE).list()
                .getItems()
                .stream()
                .anyMatch(it -> it.getMetadata().getName().equals(SIDECAR_POD_NAME + "-" + pod.getMetadata().getName()));
    }

    private boolean isAssignedSidecar(Pod pod) {
        return pod.getMetadata().getName().startsWith(SIDECAR_POD_NAME + "-")
                && pod.getMetadata().getOwnerReferences().stream()
                .anyMatch(it -> "Pod".equals(it.getKind()) && "v1".equals(it.getApiVersion()));
    }
}