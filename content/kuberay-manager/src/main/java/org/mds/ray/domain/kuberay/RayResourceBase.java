package org.mds.ray.domain.kuberay;

import io.fabric8.kubernetes.api.model.GenericKubernetesResource;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public abstract class RayResourceBase {
    private RayResourceInfo info;

    private transient GenericKubernetesResource resource;

}
