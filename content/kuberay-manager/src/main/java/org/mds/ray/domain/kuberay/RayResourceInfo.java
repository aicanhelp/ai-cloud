package org.mds.ray.domain.kuberay;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;

@Getter
@Setter
@Accessors(chain = true)
public class RayResourceInfo {
    private String name;
    private String type;
    private String template;
    private String content;
}
