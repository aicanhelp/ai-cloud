package org.mds.ray.domain.kuberay.template;

import lombok.Getter;
import lombok.Setter;
import lombok.experimental.Accessors;
import org.apache.commons.lang.StringUtils;

@Getter
@Setter
@Accessors(chain = true)
public class RayTemplate {
    private String name;
    private String content;
    private String url;

    public String getContent() {
        if (!StringUtils.isBlank(this.content)) {
            return this.content;
        }
        this.content = this.load();
        return this.content;
    }

    private String load() {
        return "";
    }
}
