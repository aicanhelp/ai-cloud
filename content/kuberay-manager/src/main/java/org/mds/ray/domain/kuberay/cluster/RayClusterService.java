package org.mds.ray.domain.kuberay.cluster;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

import java.util.List;

@Service
@Slf4j
public class RayClusterService {
    private KubernetesClient client;

    public RayClusterService(KubernetesClient client) {
        this.client = client;
    }

    public Mono<RayCluster> create(RayCluster rayCluster) {
        return Mono.empty();
    }

    public Mono<RayCluster> create(String name, String template) {
        return Mono.empty();
    }

    private List<GenericKubernetesResource> getResources() {
        return client.genericKubernetesResources("ray.io/v1alpha1", "RayCluster")
                .list()
                .getItems();
    }

}
