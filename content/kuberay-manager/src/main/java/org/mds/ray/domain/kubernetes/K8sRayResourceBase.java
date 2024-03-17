package org.mds.ray.domain.kubernetes;

import io.fabric8.kubernetes.api.model.Service;
import io.fabric8.kubernetes.api.model.apps.Deployment;
import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public abstract class K8sRayResourceBase {
    private K8sRayResourceInfo info;

    private transient Service service;
    private transient Deployment head;
    private transient Deployment worker;

}
