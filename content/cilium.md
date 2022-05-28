# 云原生网络利器--Cilium 总览

## **01 背景**

在云原生相对成熟的今天，对于 Kubernetes 本身的能力需求，逐渐变得不那么迫切，因为常见的能力都已经成熟稳定了。但是在周边领域还处于不断发展的过程，特别是容器的网络方案，存储方案，安全领域等。

**重点是网络方案，**举例来说，一个企业要想成功落地容器平台，首先要解决的问题就是网络方案的选型。选型的参考没有标准化。有根据易用性的；有根据规模的；有根据性能的；有根据应用亲和的 (固定 IP)，有根据企业内部网络要求的，有根据网络安全要求的，有根据稳定性的 (相对而言) ，有根据可运维性的，等等。

在选型的时候选型的条件大多数是组合条件，这些条件对容器的网络方案就提出了很多的挑战。不同的厂商或者开源项目都在构建不同场景下的网络方案。

从容器的网络发展路线来看，包括以下几个阶段：

- 基于 Linux bridge 以及基于 ovs 实现的 overlay 的网络。
- 基于 bgp/hostgw 等基于路由能力的网络。
- 基于 macvlan，ipvlan 等偏物理网络的 underlay 的网络。
- 基于 Kernel 的 eBPF 的技术实现的网络。
- 基于 dpdk/sriov/vpp/virtio-user/offload/af-xdp 实现的用户态的网络。

第一/二/三种网络的方案有一个共同的特性就是完全使用内核成熟的技术，以及对于 netfilter，也就是 iptables 的能力，就是拿来用就可以，不会考虑 bypass 它。

第四种的网络方案中也会用到 iptables，但是它的设计思想是尽量的 bypass netfilter/iptables，以及基于内核的 eBPF 技术实现网络设备之间 redirect 的方式 bypass netfilte，让网络数据处理的路径尽量的短，进而达到高性能的目的，不是说 eBPF 不稳定，而是想要实现更好的性能，对内核的 uapi 的依赖性比较高，往往要开发 feature，并合并到内核去，然后再使用，前三种方案使用的内核技术都是非常成熟的，不会有太多的功能会依赖开发新的内核特性。

第五种网络的方案的主要场景就是希望最快的网络，不同的实现方案，会有硬件的依赖，会有用户态协议栈的技术，内存网络加速的技术，有的方案还会对应用本身有侵入性，而且此方案也是相对最复杂的。

从技术路线来看，不难看出的是，对容器的网络方案的基本诉求就是要越来越快。

从功能的角度看和使用的角度看，基本是类似的，原因是使用的接口都是标准的 CNI 标准接口。

从发展的阶段看，理论上用户态的网络能力是最快的，因为 bypass 了 Kernel，但是也意味着对硬件和应用是有一定的要求的，技术的通用性稍微显得有点不太兼容。

其余 4 种方案都是基于内核技术，通用性比较好。方案没有好坏之分，适合自己的才是好的。那接下来就要看看谁的性能是比较好的。

本文不对所有的网络方案做分析，主要针对最近比较热门的基于 eBPF 实现的 Cilium 网络技术，做一些相关的技术分享。

## **02 技术术语**

为了更好的理解 Cilium 实现的能力，首先需要对一些常见的内核技术和网络技术的相关概念有一个初步的认识之后，会更好的帮助理解原理。以下整理一些技术术语，不是包含所有的，主要列出来一些主要的点。

- **CNI：** Kubernetes 的网络插件的接口规范的定义，主要能力是对接 Kubelet 完成容器网卡的创建，申请和设置 ip 地址，路由设置，网关设置。核心接口就是 cmdAdd/cmdDel ，调用接口期间，会传递 Pod 相关的信息，特别是容器网卡的 name，以及 Pod 的 network namespace 等信息。
- **IPAM：** 容器的网卡对应的 ip 分配就是由 IPAM 完成，从全称可以看出来，IP Address Management。这个组件是由 CNI 调用来完成为 Pod 申请 ip 地址。
- **Vxlan：** 用于在实现 overlay 网络中，跨主机通信的能力，基于封包和隧道的能力，实现 2 层虚拟网络。不同主机互相维护对端的隧道地址，通过主机的内核路由，实现数据包跨机器组建 overlay 网络。
- **Network Policy：** 用于定义 Kubernetes 中，容器访问控制，容器安全等能力的定义规范。可以实现能不能从容器出去，能不能访问某一个容器，能不能访问某一个服务的 port 等等，包括 L3/L4/L7 以及自定义扩展的协议的安全能力。
- **Linux Veth：** Linux 提供的一种虚拟网络技术，可以实现跨 network namespace 通信能力。一端在主机网络空间，一端在容器的网络空间。
- **Netfilter/Iptables：** 实现对网络数据包的处理能力。它也是 Linux Kernel 的一种 hook 机制，可以理解成在数据包经过内核网络协议栈处理的过程中，需要经过很多的方法处理，在特定的方法中定义了 hook，所以这些 hook 会在内核方法执行前/执行后被调用和执行。Linux 提供了 5 个这样的 hook，分别是 pre-routing、input、forword、output、post-routing。处理数据包的能力主要包括修改、跟踪、打标签、过滤等。
- **CT：** 这里的 ct 指的是连接跟踪 conntrack，主要完成接受连接和发起连接的时候，记录连接的状态，主要用在 nat/reverse nat (snat/dnat) ，iptables 连接处理操作等，作为数据包处理依据。网络连接讲究的是，连接从哪来回哪去。
- **BGP：** 是一种路由协议，用于 3 层网络的路由发布能力，让 Pod 的地址可以在物理网络设备之间被识别，从而完成跨主机的可路由的能力。
- **NAT/Reverse NAT：** 地址转换在网络连接的过程中是常见的操作。举例，在路由器上就会对地址进行转换，具体是 snat (源地址转换) /dnat (目的地址转换) 就看处理连接的阶段，如果是数据包从外部进来，就会进行 dnat 操作，作用是将数据包的目的地址，转换成真正要访问的服务的地址，如通过 NodePort 访问应用，那肯定会将目的地址转换成要访问的 Pod 的 ip 地址；如果是数据包要从主机出去的时候，一般会进行 snat 的操作，因为要让数据包的源地址是路由可达的，而一般路由可达的地址应该是主机的物理网卡的地址，所以一般会 snat 成主机的物理网络地址。有 NAT，就会有反向 NAT，主要是完成 reply 包的处理。
- **Linux路由子系统/ Neigh子系统：**  用于根据 ip 地址，在 fib 中寻找下一条的地址，以及由 Neigh 子系统来查找对应的 mac 地址。在基于二层交换机的场景下，src mac 是可以不设置的，但是 dst mac 是一定要设置的，对于三层的网络通信是一定要 src mac 和 dst mac 都要设置正确，才可以完成数据包的正确处理。
- **Linux Network Namespace：** 这是 Linux 提供的网络隔离的能力，每一个 Network Namespace 有独立的网络协议栈，如 Netfilter/Iptables。
- **eBPF：** 是一种可以不改变 Kernel 的前提下，开发 Kernel 相关能力的一种技术。开发 eBPF 的程序要使用 C 语言，因为 Kernel 是 C 语言开发的，eBPF 在开发的过程中会依赖 Kernel 的 uapi 以及 Linux 的 Hepler 方法来完成处理。Linux 是基于事件模型的系统，也支持了 eBPF 类型 hook，在一些 hook 点执行挂载的 eBPF 程序。
- **一致性Hash：** 一致性 Hash 本身是一种算法，这里的提到的作用是为了说明在新 1.11 版本实现的基于 XDP 的 LB 中，解决访问过程中，运行 Backend Pod 的主机出现故障之后，为了保证正常的访问，引入了一致性 Hash 的方式选择 Pod。具体就是在客户端访问的时候，选择后端可用 Pod 的时候，使用一致性 Hash 的算法来决定由哪一个 Pod 提供服务，这样可以保证选择的 Pod 是固定的，以及当提供服务的 Pod 机器出现故障，不会出现接手的 Pod 所在机器无法处理本次客户端的连接的问题。
- **SNAT/DSR：** 这两个概念是讲的是在完成 LB 的过程中，采用的地址处理策略，选择不同的策略会影响数据包的流向，以及网络的延迟。SNAT 的方式在数据包被代理到 Backend 之后，reply 的包还是会回到代理服务器的节点，然后再由代理服务器的节点再返回给 Client。而 DSR 是直接在 Backend 节点直接返回给 Client，性能更好。
- **Session Affinity：** 在访问 Backends 的时候，当需要同一个 Client 一直保持和之前选择的 Backend 连接的时候，就会需要 Session Affinity 的特性，也就是常说的粘性会话。
- **Egress Gateway：** 这个概念不是 Cilium 特有的，而是一个特定的场景问题。主要解决的是，一个 K8s 集群要和外部进行通信的时候，需要经过出口的代理网关，这样的好处是可以在被访问端开启特定的防火墙规则，来控制 K8s 集群中的什么样的出口地址是可以访问墙内的什么样的服务，一般会在网络安全要求比较高的场景下会使用到这个特性。
- **东西向LB：**  指的是集群内部，通过内部负载均衡实现的跨主机的容器网络通信。如 Pod 访问 ClusterIP 类型的 Service。
- **南北向LB：** 外部客户端通过 Kubernetes 暴露的支持负载均衡且主机可达的服务，访问 Kubernetes 服务的方式。如 Client 访问 NodePort 类型的 Service。
- **直接路由模式/隧道模式：** 在 Cilium 中， Pod 之间的路由方式分为直接路由和隧道的模式。直接路由推荐使用 BGP 协议完成 Pod 路由能力，隧道模式使用基于 vxlan 的技术完成 Pod 之间的路由能力。
- **Endpoint：** Cilium 网络的端点 (CiliumEndpoint) ，也可以理解成 Cilium 网络中的一个参与者，或者实体，和 K8s 里的 Endpoint 不是一个概念。其中不仅仅是 Pod 本身是一个 Endpoint；Host 也是一种 Endpoint；负责 overlay 网络模式的 cilium_vxlan 也是一个 Endpoint；而对于外部网络这样的一个虚拟不存在的参与者也是一个 Endpoint, 它叫 WORLD_ID。
- **Identity：** 用于标记参与 Cilium 网络的端点 (Endpoint) 的安全身份，用标签的方式来标记。有相同 Identity 标签的 Endpoints 是具有相同安全身份的，在处理安全策略的时候，是同样的验证逻辑。这样有一个好处是，不会随着 Pod 重启导致 Pod ip 变化，而让 policy 需要重新生成或者配置的问题。Cilium 本身不仅仅完成了容器的网络的数据包处理和传输，其中安全也是 Cilium 的重要能力，在设计和实现的时候都是统一考虑和实现的。不像其它的网络方案都是在外围来实现。
- **Service：** Cilium 中的 Service 和 K8s 里的 Service 不是一个定义，但是也是类似的概念，也是用来表述一组服务的访问入口，也有对应的 Backend。只是这个 Service 的模型是 Cilium 自己定义的。在完成 ct，nat/reverse-nat，粘性会话的时候，都会用到 Service。
- **Kube Proxy Replacement：** 使用基于 eBPF 的数据平面去替代 kube-proxy 实现的功能，进一步提升 K8s 的网络相关的性能。
- **Socket LB：** 基于 eBPF 实现的东西向的 LB，可以在 Socket 级别完成数据包的发送和接受，而不是 Per-Packet 的处理方式。可以实现跨主机的 Pod->Pod 的直接连接，直接通信，而传统的 Kube-Proxy 是实现的 Per-Packet 的，且需要基于 ipvs/iptables 的方式实现的 LB 的转发能力。对于 Per-Packet 的处理，需要对每一个需要出去的数据包都要进行 DNAT 的处理，而 Socket 级的方案只需要设置一次，那就是在建立连接和 Socket 的时候，设置一次的四元组里的目的地址就可以，这样可以提升更好的网络传输速度。
- **Host Reachable Service：**传统的 LB 工具，都会在代理请求的时候，进行 NAT 相关的操作，但是 Host Reachable Service 能够完成连接 K8s service 的时候，真正建立连接的时候是直接连接 Backend Pod ip 的能力，减少了每次数据包处理的 NAT 开销。这样网络性能就自然会有所提高。
- **Host Routing / Fast Path：** 看到名字就可以想到是快速的意思，这个就是 Kernel 在 5.10 里新增加的能力，主要包含 ctx redirect peer 和 ctx redirect neigh。在没有 Host Routing 能力之前，所有数据包要想进入到容器都需要 Kernel 的路由能力和邻居子系统，以及需要经过 cilium_host 虚拟网络设备才可能进入到容器。而在有了 Host Routing 之后，可以实现网络设备到网络设备之间的直接 redirect，进一步缩短数据包的传输路径。所以 ctx_redirect_peer 是 ctx_redirect 的升级版本，当然对 Kernel 的版本要求也更高。
- **TProxy透明代理：** 可以完成 LB 场景下，模拟 Client→Backend 的直接连接的能力，从连接状态看，和直接连接一样，但是其实中间是有一层代理在处理数据包，完成 Client 到 Backend 的代理能力。
- **Envoy/Envoy Filter：** Envoy 本身就是一个可以完成 L4/L7 等能力的代理工具。同时 Envoy 有很好的扩展性，可以对 Envoy 的能力进行扩展，完成业务的需要。这里的扩展能力，主要指的就是 Envoy 的 Filters，这里的 Filters 主要包含常见的 Listener Filters，Network Filters，以及 Http Connection Manager 的 Filters。Cilium 的开源版本就是基于 Envoy 的能力完成了 L7 的 Network Policy 和 Visibility Policy，以及一些自定义的 L4 的 Policy。
- **Hubble / Relay / Hubble-OTEL：** 这里的三个组件都是为了完成 Cilium 的可观测能力。其中 Hubble 是 Cilium Agent 内置的能力，每一个节点都会有。Relay 是可以完成对所有单机的 Hubble 的数据进行收集和整合成统一的数据视图，但是在早一点的版本中，这些观测数据都是没有持久化的，都是能查看最近的一些数据，就类似于 cAdvisor 中对容器数据的监控一样，都是保存在特定的内存中的，在新版本中，有一个 Hubble-OTEL 的组件，可以完成 Hubble 的数据对接到 OpenTelemetry 中去，由于 OpenTelemetry 是有很多后端持久化插件，这样就可以完成观测数据的历史分析能力。
- **OpenTelemetry：** 是 CNCF 的用于完成观测能力的项目，支持 Traces、Metrics、Logs 能力的观测技术。具体可以参见官方文档或参见 [云原生观测性 - OpenTelemetry](https://link.zhihu.com/?target=https%3A//mp.weixin.qq.com/s%3F__biz%3DMzA5NTUxNzE4MQ%3D%3D%26mid%3D2659273449%26idx%3D1%26sn%3Db7bae4efa14bef8e08bc4f53ffe2bb36%26chksm%3D8bcbca7bbcbc436d63d28251fa23a685448cc98be1485f6700d758315039298cd6c77d9ca3e4%26mpshare%3D1%26scene%3D21%26srcid%3D1125Y2spq4hEUD05B6Tn9xgD%26sharer_sharetime%3D1645676354386%26sharer_shareid%3Da2e9742e6aa3f44e17c5f7c71530c1a5%26version%3D4.0.0.6007%26platform%3Dwin%23wechat_redirect)

## **03 架构介绍**

**

![](https://pic1.zhimg.com/80/v2-de232bd56716f550821c3367a4f9bd84_1440w.jpg)

**

**Cilium Agent：**以 DaemonSet 的方式运行在所有的节点上，并且提供 API。负责全局 eBPF 程序的编辑和挂载，以及为 Pod 编译和挂载 Pod 对应的 eBPF 程序，当有 Pod 被创建的时候，CNI 就会在完成容器本身网卡，地址等设置之后，调用 Cilium Agent 的 EndpointCreate 方法完成 eBPF/Identity 相关数据路径以及安全策略的创建；负责相关 iptables 的初始化和维护，因为在内核版本还不是足够高的情况下，还是需要 iptables 来完成一些工作，如在支持 Network Policy 的时候，使用到基于 iptables 的 tproxy，以及基于 Envoy 实现的 Network Policy 时，需要的 iptables chains 等；负责 eBPF 程序所需要的全局的 Maps 的初始化和维护以及 Pod 相关的 Maps 的创建和维护，因为有一些 Maps 是全局性的，所以需要启动的时候初始化和恢复，有一些是随着 Pod 的创建而创建的；内置 Hubble Server，提供 Metrics 和 Trace 的能力，并且提供 API。同时 Cilium Agent 也内置实现了基于透明代理的 DNS Network Policy 能力的 DNS Proxy Server。

**Cilium Operator：**负责部分的 IPAM 的工作，主要是给主机节点分配 CIDR，接下来给 Pod 分配 ip 地址由主机上的 Cilium Agent 来完成；负责对存储 Provider 进行健康检查，现在支持 Cilium 的存储 Provider 有 K8s CRD，还有外置的 etcd，当发现存储设施不健康会触发 Cilium Agent 重启；优化 K8s 的 Endpoint 的发现机制，在早期一点的版本，Cilium Agent 要处理的 Endpoint 不是使用的 EndpointSlice 的方式，会导致数据量很大，增加 Cilium Agent 的压力，现在基于 Operator 进行转换，让 Cilium Agent 只处理 Operator 转换出来的 EndpointSlice，极大的减小了数据量，这样支持的集群的节点规模就可以增大，这里的 EndpointSlice 是 K8s 的特性，所以需要开启该特性。

**Cilium Cli：**用于和本地的 Cilium Agent 通信，提供操作 Cilium 的能力。虽然只和本地的 Cilium Agent 通信，但是可以拿到完整的整个 Cilium 网络方案内的数据。因为每一个 Cilium Agent 是会和控制平面通信。

**Cilium Proxy：**用于完成 L7/Proxylib 类型的 Network Policy 的合法检查，检查通过就会继续处理数据包，不通过就返回。实现的方式是使用 C++扩展 Envoy 实现的一整套 filters，包括 Listener Filter， Network Filter，Http Filter。可以支持 K8s Network Policy，支持 Kafka 的 Policy 等都是在这里完成的。Cilium Agent 会负责处理 Proxy 相关的控制流，包括启动一个包括定制化配置的 Envoy。这里的 Proxy 是完全只是和 Envoy 相关的，为了支持 Policy 能力的部分。对于 L3/L4 层的 Network Policy 是在 eBPF 的程序中直接完成验证的。

**Cilium DNS Proxy：**拦截和验证访问的 DNS 是不是可以访问，这里的 DNS Proxy 不是独立的组件，而是 Cilium Agent 内置实现的一个独立的 Server，在这里单独提出来，只是为了强调能力的独立性。主要的实现方式是基于 tproxy 的能力将请求转发到 DNS Proxy Server，完成 DNS 请求的验证。

**Cilium CNI：** 这个比较熟悉了，就是实现了 CNI 接口的组件，由 Kubelet 调用在 Pod 的网络命名空间中完成容器网络的管理，包括容器网卡的创建，网关的设置，路由的设置等，因为依赖 eBPF 的网络能力，这里还会调用 Cilium Agent 的接口，完成 Pod 所需要的 eBPF 程序的编译，挂载，配置，以及和 Pod 相关的 eBPF Maps 的创建和管理。

**Hubble Server：**主要负责完成 Cilium 网络方案中的观测数据的服务。在 Cilium 运行的数据路径中，会对数据包的处理过程进行记录，最后统一由 Hubble Server 来完成输出，同时提供了 API 来访问这些观测数据。

**Hubble Relay：** 因为 Hubble Server 提供了 API 接口，而 Relay 就是调用所有集群节点的 Hubble Server 的 API 接口，对观测数据进行汇总，然后由 Hubble UI 统一展示。

**Hubble UI：**用于展示 Hubble Server 收集的观测数据，这里会直接连接到 Relay 去查询数据。

**Hubble OTEL：**用于将 Hubble Server 的观测数据以 OTLP 的协议导出到 OpenTelemetry 框架中。由于 OpenTelemetry 有丰富的生态，可以很好地对 Cilium 的观测数据进行管理和分析。

**eBPF 程序：**使用 eBPF 可以开发出对应不同类型的 Linux 子系统的能力扩展。这里主要使用最多的是和网络相关的能力。主要包括如下几个场景：在 XDP 和 TC 的 hook 处，对数据包进行处理，完成对数据包的验证和转发，这里的转发主要依赖 Kernle 的 redirect (ifindex, flags)/redirect_peer (ifindex, flags) 能力；使用 cgroup 类型的 eBPF 程序完成 Socket LB 能力，提供更高网络性能的东西向通信；使用 sockmap 类型和 socket ops 类型的 eBPF 程序完成 Socket Redirect 能力，加速本地 Socket 之间的通信。

**eBPF Maps：**Linux 提供了很多种类型的 eBPF Maps 供 eBPF 程序使用，理论上传统开发语言中的 Map 只是一种数据结构，eBPF 也一样，只是 eBPF 的 Maps 的类型更偏向场景或是功能，举例来说，要想完成 socket redirect，这里常用就是 BPF_MAP_TYPE_SOCKHASH 类型的 Map，配合 msg_redirect_hash 方法，完成 socket 的数据包的 redirect。Maps 的类型有很多种，暂不列出具体的类型。

**iptables：**用于完成在内核版本不支持的某一些功能下的时候，还是需要 iptables 来支撑，举例在 Kernel 的版本低于 5.7 的时候，tproxy 还是要依赖于 iptables 来完成，但是在高的版本里就直接可以用基于 eBPF 实现的 tproxy 了。再比如，在使用 Envoy 实现 Network Policy 的能力，以及处理完成之后将数据包 reply 到 Cilium 的网络的过程中，依赖于 iptables 和 ip rule 将数据包定向传输到 cilium_host。

**Grafana：**用于展示 Cilium 暴露出来的 Prometheus 的 Metrics 数据。

**Prometheus：**用于收集 Cilium 暴露出来的 Prometheus 的 Metrics 数据。

**OpenTelemetry：**用于收集 Cilium 通过 OTLP 协议暴露出来的 Trace 的数据。

**K8s api server：**用于 Cilium 的 datastore，保存 Cilium 的数据。如 Identity 数据， Endpoint 数据。Cilium Agent 会与其通信，完成数据的发现。

**etcd：**另一种可用于 Cilium 的 datastore，保存 Cilium 的数据。如 Identity 数据，Endpoint 数据。Cilium Agent 会与其通信，完成数据的发现。

## **04 观测能力**

Cilium 默认提供了一些观测的能力，支持基于 Cilium Agent 内置的 Prometheus 来获取 Metrics 数据，配合 Grafana 实现监控数据的展示；支持基于数据包访问路径的 Trace，包括 L4/L7 的观测。这里有一个点比较特别是 L7 的观测，默认是不打开的，在需要观测 L7 的 Pod 上打上 annotation 就可以拿到这个 Pod 的 L7 的观测数据，对于 L4 是默认就实现了观测，不需要额外配置。

- **L7 Trace 实现：**

基于 Pod 的 annotation，annotation 的 name 是 **“io.cilium.proxy-visibility”**， value 是 **“<{Traffic Direction}/{L4 Port}/{L4 Protocol}/{L7 Protocol}>”**。

**举例：**kubectl annotate pod foo -n bar io.cilium.proxy-visibility="<Egress/53/UDP/DNS>,<Egress/80/TCP/HTTP>"。

**实现方式：**

**1.**Cilium Agent 会 watch Pod 的一些 annotation 的变化，其中就是包括了 ProxyVisibility，也就是“io.cilium.proxy-visibility”这个 annotation。

**2.**Cilium Agent 会为 Visibility Policy 创建 redirect 对象，在经过 proxy 处理之后，会记录下相关的 Trace 信息，最后通过 Hubble 组件暴露出来。

![](https://pic4.zhimg.com/80/v2-20e9501ed70d89915c1bd878f9ea7887_1440w.jpg)

![](https://pic1.zhimg.com/80/v2-7008dcf58450eaee3843fae01370161c_1440w.jpg)

- **Trace：**

![](https://pic3.zhimg.com/80/v2-731eafe8dca907a95385f694334f09be_1440w.jpg)

![](https://pic3.zhimg.com/80/v2-3f884180b3b922b390e4bba9a0a747de_1440w.jpg)

- **Metrics：**

![](https://pic3.zhimg.com/80/v2-010ae10a0b5ba89937c59ec192f61baa_1440w.jpg)

![](https://pic2.zhimg.com/80/v2-f6458cdbb5e1bfecc0fc6e426d5e7b61_1440w.jpg)

![](https://pic2.zhimg.com/80/v2-720e0378f7ab893390fc377cf6507121_1440w.jpg)

## **05 性能**

### TCP Throughput (TCP_STREAM)

![](https://pic2.zhimg.com/80/v2-aa9eadd4e4e81b9b71bc6cfed2ad6501_1440w.jpg)

### Request/Response Rate (TCP_RR)

![](https://pic3.zhimg.com/80/v2-7cc1aaafc68acc98d35d421288766b6a_1440w.jpg)

### Connection Rate (TCP_CRR)

![](https://pic4.zhimg.com/80/v2-83bef967d29112f73d85e8ef00bec81f_1440w.jpg)

### Test Configurations

![](https://pic3.zhimg.com/80/v2-06744ce81fdc4a7a94f782610fb93b6a_1440w.jpg)

## **06 社区发展**

除了 Cilium 关键的容器网络能力之外，还包括在发展的：

- Cilium Cluster Mesh 多集群的能力，主要包括跨集群的服务发现，负载均衡，网络策略，透明加密，Pod 路由等。
- Cilium Service Mesh 服务网格的能力，主要包括基于 No Sidecar 形式运行代理。对于 L7 的服务，使用每台机器一个 Envoy 的机制完成微服务治理的相关能力。
