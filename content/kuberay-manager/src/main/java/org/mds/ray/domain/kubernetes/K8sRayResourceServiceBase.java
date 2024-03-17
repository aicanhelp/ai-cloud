package org.mds.ray.domain.kubernetes;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.mds.ray.domain.kuberay.RayResourceBase;
import org.mds.ray.domain.kuberay.RayResourceRepositoryBase;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public abstract class K8sRayResourceServiceBase<T extends K8sRayResourceBase> {
    private KubernetesClient client;
    private K8sRayResourceRepositoryBase<T> repository;

    private String type;

    protected K8sRayResourceServiceBase(String type, KubernetesClient client, K8sRayResourceRepositoryBase<T> repository) {
        this.type = type;
        this.client = client;
        this.repository = repository;
    }

    protected Mono<GenericKubernetesResource> deploy(String name) {
        return Mono.empty();
    }

    protected Mono<Void> undeploy(String name) {
        return Mono.empty();
    }

    protected Mono<GenericKubernetesResource> state(String name) {
        return Mono.empty();
    }

}
