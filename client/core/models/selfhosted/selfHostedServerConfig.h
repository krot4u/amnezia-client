#ifndef SELFHOSTEDSERVERCONFIG_H
#define SELFHOSTEDSERVERCONFIG_H

#include <QJsonObject>
#include <QJsonArray>
#include <QMap>
#include <optional>

#include "core/utils/containerEnum.h"
#include "core/utils/containers/containerUtils.h"
#include "core/utils/protocolEnum.h"
#include "core/models/containerConfig.h"
#include "core/utils/errorCodes.h"
#include "core/utils/routeModes.h"
#include "core/utils/commonStructs.h"

namespace amnezia
{

using namespace ContainerEnumNS;

struct SelfHostedServerConfig {
    QString description;
    QString hostName;
    QMap<DockerContainer, ContainerConfig> containers;
    DockerContainer defaultContainer;
    QString dns1;
    QString dns2;
    
    std::optional<QString> userName;
    std::optional<QString> password;
    std::optional<int> port;

    struct XRaySubscriptionConfigs
    {
        QJsonArray configString;
        QJsonArray configName;

        QJsonObject toJson() const;
        static XRaySubscriptionConfigs fromJson(const QJsonObject &json);
    };
    std::optional<XRaySubscriptionConfigs> xraySubscriptionConfigs;
    std::optional<int> currentConfig;
    
    bool hasCredentials() const;
    bool isReadOnly() const;
    std::optional<ServerCredentials> credentials() const;
    bool hasContainers() const;
    ContainerConfig containerConfig(DockerContainer container) const;
    QJsonObject toJson() const;
    static SelfHostedServerConfig fromJson(const QJsonObject& json);
};

} // namespace amnezia

#endif // SELFHOSTEDSERVERCONFIG_H

