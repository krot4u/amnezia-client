#ifndef NATIVESERVERCONFIG_H
#define NATIVESERVERCONFIG_H

#include <QJsonObject>
#include <QJsonArray>
#include <QMap>
#include <optional>

#include "core/utils/containerEnum.h"
#include "core/utils/containers/containerUtils.h"
#include "core/utils/protocolEnum.h"
#include "core/models/containerConfig.h"

namespace amnezia
{

using namespace ContainerEnumNS;

struct NativeServerConfig {
    QString description;
    QString hostName;
    QMap<DockerContainer, ContainerConfig> containers;
    DockerContainer defaultContainer;
    QString dns1;
    QString dns2;

    struct XRaySubscriptionConfigs
    {
        QJsonArray configString;
        QJsonArray configName;

        QJsonObject toJson() const;
        static XRaySubscriptionConfigs fromJson(const QJsonObject &json);
    };
    std::optional<XRaySubscriptionConfigs> xraySubscriptionConfigs;
    std::optional<int> currentConfig;
    
    bool hasContainers() const;
    ContainerConfig containerConfig(DockerContainer container) const;
    QJsonObject toJson() const;
    static NativeServerConfig fromJson(const QJsonObject& json);
};

} // namespace amnezia

#endif // NATIVESERVERCONFIG_H

