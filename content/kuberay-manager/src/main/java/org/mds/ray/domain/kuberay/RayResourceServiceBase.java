package org.mds.ray.domain.kuberay;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import io.fabric8.kubernetes.client.KubernetesClient;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public abstract class RayResourceServiceBase<T extends RayResourceBase> {
    private KubernetesClient client;
    private RayResourceRepositoryBase<T> repository;

    private String type;

    protected RayResourceServiceBase(String type, KubernetesClient client, RayResourceRepositoryBase<T> repository) {
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
