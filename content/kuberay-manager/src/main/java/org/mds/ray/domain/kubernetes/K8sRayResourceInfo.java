package org.mds.ray.domain.kubernetes;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class K8sRayResourceInfo {
    private String name;
    private String type;
    private String template;
    private String service;
    private String head;
    private String worker;
}
